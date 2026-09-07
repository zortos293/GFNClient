use crate::proxy::normalize_proxy_url;
use serde_json::{Map, Value, json};
use std::env;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

const NATIVE_TRANSPORT: &str = "nvst";
const CONSOLE_POLICY_VERSION: &str = "qtConsoleModePolicyVersion";

pub struct SettingsStore {
    path: PathBuf,
    values: Map<String, Value>,
    // Preserve fields owned by an older/newer Electron build so trying the Qt
    // client and then rolling back never destroys settings it does not know.
    // Unknown values are not exposed over the shell/core contract.
    passthrough: Map<String, Value>,
}

impl SettingsStore {
    pub fn load(data_dir: Option<PathBuf>) -> io::Result<Self> {
        let path = data_dir
            .unwrap_or_else(default_data_dir)
            .join("settings.json");
        let defaults = defaults();
        let mut values = defaults.clone();
        let mut passthrough = Map::new();
        if path.exists() {
            match fs::read_to_string(&path)
                .ok()
                .and_then(|text| serde_json::from_str::<Map<String, Value>>(&text).ok())
            {
                Some(persisted) => {
                    for (key, value) in persisted {
                        if defaults.contains_key(&key) {
                            let value = if key == "mouseAcceleration" {
                                value.as_bool().map_or(value.clone(), |enabled| {
                                    Value::Number((if enabled { 100 } else { 1 }).into())
                                })
                            } else {
                                value
                            };
                            values.insert(key, value);
                        } else {
                            if key == "sessionTimeRemainingDisplay"
                                && matches!(value.as_str(), Some("stats" | "both"))
                            {
                                values.insert(
                                    "showSessionTimeRemainingInStatsOverlay".to_owned(),
                                    Value::Bool(true),
                                );
                            }
                            passthrough.insert(key, value);
                        }
                    }
                }
                None => {
                    let corrupt_path = path.with_extension("json.corrupt");
                    let _ = fs::rename(&path, corrupt_path);
                }
            }
        }
        let mut store = Self {
            path,
            values,
            passthrough,
        };
        store.migrate_native_fullscreen_shortcut();
        // Old builds enabled automatic switching by default, so an existing
        // true value is not reliable evidence of opt-in. Reset that policy once;
        // subsequent explicit opt-ins survive every restart.
        let migrate_console_policy =
            store.passthrough.get(CONSOLE_POLICY_VERSION) != Some(&json!(1));
        if migrate_console_policy {
            store
                .values
                .insert("switchToConsoleOnPad".to_owned(), json!(false));
            store
                .passthrough
                .insert(CONSOLE_POLICY_VERSION.to_owned(), json!(1));
        }
        store.normalize();
        if migrate_console_policy && store.path.exists() {
            store.save()?;
        }
        Ok(store)
    }

    pub fn all(&self) -> Value {
        Value::Object(self.values.clone())
    }

    pub fn set(&mut self, key: &str, mut value: Value) -> Result<Value, String> {
        if !defaults().contains_key(key) {
            return Err(format!("Unknown setting: {key}"));
        }
        if key == "audioOutputDevice" {
            let device = value
                .as_str()
                .ok_or_else(|| "audioOutputDevice must be a string".to_owned())?;
            if device.len() > 1024 || device.contains('\0') {
                return Err(
                    "audioOutputDevice must be at most 1024 bytes without NUL characters"
                        .to_owned(),
                );
            }
        }
        if key == "sessionProxyUrl" {
            let raw = value
                .as_str()
                .ok_or_else(|| "sessionProxyUrl must be a string".to_owned())?
                .trim();
            value = if raw.is_empty() {
                Value::String(String::new())
            } else {
                Value::String(normalize_proxy_url(raw)?.normalized_url)
            };
        }
        if key == "sessionProxyEnabled" && value.as_bool() == Some(true) {
            let raw = self.values["sessionProxyUrl"].as_str().unwrap_or("");
            normalize_proxy_url(raw)?;
        }
        let previous_values = self.values.clone();
        self.values.insert(key.to_owned(), value);
        self.normalize();
        if key == "launchInConsoleMode" && self.values.get(key) == Some(&json!(false)) {
            self.values
                .insert("switchToConsoleOnPad".to_owned(), json!(false));
        }
        if let Err(error) = self.save() {
            self.values = previous_values;
            return Err(format!("Could not save settings: {error}"));
        }
        Ok(self.values.get(key).cloned().unwrap_or(Value::Null))
    }

    pub fn reset(&mut self) -> Result<Value, String> {
        self.values = defaults();
        self.normalize();
        self.save()
            .map_err(|error| format!("Could not reset settings: {error}"))?;
        Ok(self.all())
    }

    fn normalize(&mut self) {
        normalize_types(&mut self.values);
        if self.values["audioOutputDevice"]
            .as_str()
            .is_some_and(|device| device.len() > 1024 || device.contains('\0'))
        {
            self.values
                .insert("audioOutputDevice".to_owned(), json!(""));
        }
        normalize_resolution(&mut self.values);
        normalize_choice(
            &mut self.values,
            "desktopBackground",
            &["art", "gradient", "solid"],
            "art",
        );
        normalize_choice(
            &mut self.values,
            "aspectRatio",
            &["16:9", "16:10", "21:9", "32:9", "4:3"],
            "16:9",
        );
        normalize_choice(
            &mut self.values,
            "recordingResolution",
            &["720p", "1080p", "1440p"],
            "720p",
        );
        normalize_choice(&mut self.values, "streamClientMode", &["native"], "native");
        normalize_choice(
            &mut self.values,
            "nativeVideoBackend",
            &[
                "auto",
                "d3d11",
                "d3d12",
                "nvdec",
                "cuda",
                "vaapi",
                "v4l2",
                "vulkan",
                "videotoolbox",
                "software",
            ],
            "auto",
        );
        for key in ["nativeCloudGsyncMode", "nativeD3dFullscreenMode"] {
            normalize_choice(
                &mut self.values,
                key,
                &["auto", "disabled", "forced"],
                "auto",
            );
        }
        self.values.insert(
            "transportMode".to_owned(),
            Value::String(NATIVE_TRANSPORT.to_owned()),
        );
        normalize_choice(
            &mut self.values,
            "codec",
            &["auto", "av1", "h264", "h265"],
            "auto",
        );
        normalize_choice(
            &mut self.values,
            "fallbackCodec",
            &["auto", "h264", "h265"],
            "auto",
        );
        normalize_choice(
            &mut self.values,
            "colorQuality",
            &["8bit_420", "10bit_420", "8bit_444", "10bit_444"],
            "8bit_420",
        );
        normalize_choice(&mut self.values, "frameGeneration", &["off", "2x"], "off");
        for key in ["decoderPreference", "encoderPreference"] {
            normalize_choice(
                &mut self.values,
                key,
                &["auto", "hardware", "software"],
                "auto",
            );
        }
        normalize_choice(
            &mut self.values,
            "microphoneMode",
            &["disabled", "push-to-talk", "voice-activity"],
            "disabled",
        );
        normalize_choice(
            &mut self.values,
            "statsOverlayPosition",
            &["bottom-left", "bottom-right", "top-left", "top-right"],
            "bottom-left",
        );
        normalize_choice(
            &mut self.values,
            "appTheme",
            &["light", "dark", "auto"],
            "auto",
        );
        normalize_choice(
            &mut self.values,
            "appLanguage",
            &[
                "system", "de", "en", "es", "fr", "ja", "ko", "nl", "pl", "ro", "ru", "tr", "zh",
            ],
            "system",
        );
        normalize_choice(
            &mut self.values,
            "themePack",
            &[
                "default", "nocturne", "aurora", "kraft", "phosphor", "bone", "cobalt", "hibiscus",
                "chapel",
            ],
            "nocturne",
        );
        normalize_choice(
            &mut self.values,
            "appAccentColor",
            &["green", "blue", "violet", "amber", "rose", "coral", "white"],
            "green",
        );
        normalize_choice(
            &mut self.values,
            "updateChannel",
            &["stable", "nightly"],
            "stable",
        );
        clamp_integer(&mut self.values, "mouseAcceleration", 1, 150, 1);
        clamp_integer(&mut self.values, "fps", 30, 240, 60);
        clamp_integer(&mut self.values, "maxBitrateMbps", 1, 200, 75);
        clamp_integer(&mut self.values, "windowWidth", 960, 7680, 1400);
        clamp_integer(&mut self.values, "windowHeight", 540, 4320, 900);
        clamp_integer(&mut self.values, "recordingFps", 30, 60, 30);
        clamp_integer(&mut self.values, "antiAfkReminderEveryMinutes", 1, 120, 15);
        clamp_integer(&mut self.values, "antiAfkReminderDurationSeconds", 1, 60, 5);
        clamp_integer(&mut self.values, "sessionClockShowEveryMinutes", 1, 240, 60);
        clamp_integer(
            &mut self.values,
            "sessionClockShowDurationSeconds",
            1,
            120,
            30,
        );
        clamp_number(&mut self.values, "posterSizeScale", 0.75, 1.5, 1.05);
        clamp_number(&mut self.values, "mouseSensitivity", 0.1, 3.0, 1.0);
        clamp_number(&mut self.values, "desktopUiScale", 0.85, 1.25, 1.0);
        clamp_number(&mut self.values, "statsOverlayScale", 0.85, 1.5, 1.0);
        clamp_number(&mut self.values, "statsOverlayOpacity", 40.0, 100.0, 85.0);
        normalize_optional_integer(&mut self.values, "recordingBitrateMbps", 1, 12);
        normalize_bounded_strings(&mut self.values);
        normalize_nested_settings(&mut self.values);
    }

    fn migrate_native_fullscreen_shortcut(&mut self) {
        let fullscreen = self
            .values
            .get("shortcutToggleFullscreen")
            .and_then(Value::as_str);
        let screenshot = self
            .values
            .get("shortcutScreenshot")
            .and_then(Value::as_str);
        if fullscreen == Some("F10") && screenshot == Some("F11") {
            self.values.insert(
                "shortcutToggleFullscreen".to_owned(),
                Value::String("F11".to_owned()),
            );
            self.values.insert(
                "shortcutScreenshot".to_owned(),
                Value::String("Ctrl+F11".to_owned()),
            );
            if self
                .values
                .get("statsOverlayPosition")
                .and_then(Value::as_str)
                == Some("bottom-left")
            {
                self.values.insert(
                    "statsOverlayPosition".to_owned(),
                    Value::String("top-right".to_owned()),
                );
            }
        }
    }

    fn save(&self) -> io::Result<()> {
        let parent = self.path.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let temporary = self.path.with_extension("json.tmp");
        let backup = self.path.with_extension("json.bak");
        let mut persisted = self.passthrough.clone();
        persisted.extend(self.values.clone());
        let data = serde_json::to_vec_pretty(&persisted).map_err(io::Error::other)?;
        fs::write(&temporary, data)?;
        if self.path.exists() {
            let _ = fs::remove_file(&backup);
            fs::rename(&self.path, &backup)?;
        }
        if let Err(error) = fs::rename(&temporary, &self.path) {
            let _ = fs::rename(&backup, &self.path);
            return Err(error);
        }
        Ok(())
    }
}

fn normalize_resolution(values: &mut Map<String, Value>) {
    let valid = values["resolution"]
        .as_str()
        .and_then(|value| value.split_once('x'))
        .and_then(|(width, height)| Some((width.parse::<u32>().ok()?, height.parse::<u32>().ok()?)))
        .is_some_and(|(width, height)| {
            (640..=7680).contains(&width)
                && (480..=4320).contains(&height)
                && width % 2 == 0
                && height % 2 == 0
        });
    if !valid {
        values.insert("resolution".to_owned(), json!("1920x1080"));
    }
}

pub fn resolve_data_dir(data_dir: Option<PathBuf>) -> PathBuf {
    data_dir.unwrap_or_else(|| {
        let primary = default_data_dir();
        select_existing_data_dir(primary.clone(), legacy_data_dirs(&primary))
    })
}

fn select_existing_data_dir(
    primary: PathBuf,
    legacy_candidates: impl IntoIterator<Item = PathBuf>,
) -> PathBuf {
    if primary.exists() {
        return primary;
    }
    legacy_candidates
        .into_iter()
        .find(|candidate| candidate.exists())
        .unwrap_or(primary)
}

fn normalize_choice(values: &mut Map<String, Value>, key: &str, choices: &[&str], fallback: &str) {
    let valid = values
        .get(key)
        .and_then(Value::as_str)
        .is_some_and(|value| choices.contains(&value));
    if !valid {
        values.insert(key.to_owned(), Value::String(fallback.to_owned()));
    }
}

fn clamp_integer(
    values: &mut Map<String, Value>,
    key: &str,
    minimum: i64,
    maximum: i64,
    fallback: i64,
) {
    let value = values
        .get(key)
        .and_then(Value::as_i64)
        .unwrap_or(fallback)
        .clamp(minimum, maximum);
    values.insert(key.to_owned(), Value::Number(value.into()));
}

fn clamp_number(
    values: &mut Map<String, Value>,
    key: &str,
    minimum: f64,
    maximum: f64,
    fallback: f64,
) {
    let value = values
        .get(key)
        .and_then(Value::as_f64)
        .filter(|value| value.is_finite())
        .unwrap_or(fallback)
        .clamp(minimum, maximum);
    values.insert(
        key.to_owned(),
        serde_json::Number::from_f64(value).map_or(Value::from(fallback), Value::Number),
    );
}

fn normalize_types(values: &mut Map<String, Value>) {
    let expected = defaults();
    for (key, default) in expected {
        let valid = values.get(&key).is_some_and(|value| match &default {
            Value::Bool(_) => value.is_boolean(),
            Value::String(_) => value.is_string(),
            Value::Number(_) => value.is_number(),
            Value::Array(_) => value.is_array(),
            Value::Object(_) => value.is_object(),
            Value::Null => value.is_null() || value.is_number(),
        });
        if !valid {
            values.insert(key, default);
        }
    }
}

fn normalize_optional_integer(
    values: &mut Map<String, Value>,
    key: &str,
    minimum: i64,
    maximum: i64,
) {
    let Some(value) = values.get(key) else {
        return;
    };
    if value.is_null() {
        return;
    }
    let normalized = value.as_i64().map(|value| value.clamp(minimum, maximum));
    values.insert(
        key.to_owned(),
        normalized.map_or(Value::Null, |value| Value::Number(value.into())),
    );
}

fn normalize_bounded_strings(values: &mut Map<String, Value>) {
    for (key, maximum) in [
        ("region", 256_usize),
        ("sessionProxyUrl", 2_048),
        ("nativeStreamerExecutablePath", 2_048),
        ("microphoneDeviceId", 512),
        ("telemetryInstallId", 128),
        ("lastSeenReleaseHighlightsVersion", 128),
    ] {
        let value = values
            .get(key)
            .and_then(Value::as_str)
            .unwrap_or_default()
            .chars()
            .take(maximum)
            .collect::<String>();
        values.insert(key.to_owned(), Value::String(value));
    }
    for key in [
        "shortcutToggleStats",
        "shortcutTogglePointerLock",
        "shortcutToggleFullscreen",
        "shortcutStopStream",
        "shortcutToggleAntiAfk",
        "shortcutToggleMicrophone",
        "shortcutScreenshot",
        "shortcutToggleRecording",
    ] {
        let value = values
            .get(key)
            .and_then(Value::as_str)
            .unwrap_or_default()
            .chars()
            .take(80)
            .collect::<String>();
        values.insert(key.to_owned(), Value::String(value));
    }
    let favorites = values["favoriteGameIds"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(|value| value.chars().take(128).collect::<String>())
        .filter(|value| !value.is_empty())
        .take(500)
        .map(Value::String)
        .collect();
    values.insert("favoriteGameIds".to_owned(), Value::Array(favorites));

    let hidden_games = values["hiddenGameIds"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(|value| value.chars().take(128).collect::<String>())
        .filter(|value| !value.is_empty())
        .take(500)
        .map(Value::String)
        .collect();
    values.insert("hiddenGameIds".to_owned(), Value::Array(hidden_games));

    let tile_sizes = values["homeTileSizes"]
        .as_object()
        .into_iter()
        .flat_map(|entries| entries.iter())
        .filter_map(|(id, value)| {
            let id = id.chars().take(128).collect::<String>();
            let size = value.as_str()?;
            (!id.is_empty() && matches!(size, "square" | "wide"))
                .then(|| (id, Value::String(size.to_owned())))
        })
        .take(500)
        .collect();
    values.insert("homeTileSizes".to_owned(), Value::Object(tile_sizes));
}

fn normalize_nested_settings(values: &mut Map<String, Value>) {
    let mut interpolation = values["frameInterpolation"]
        .as_object()
        .cloned()
        .unwrap_or_default();
    let enabled = interpolation
        .get("enabled")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    let factor = interpolation
        .get("factor")
        .and_then(Value::as_i64)
        .unwrap_or(2)
        .clamp(2, 4);
    let quality = interpolation
        .get("quality")
        .and_then(Value::as_i64)
        .unwrap_or(480)
        .clamp(120, 1_080);
    interpolation.clear();
    interpolation.insert("enabled".to_owned(), Value::Bool(enabled));
    interpolation.insert("factor".to_owned(), Value::Number(factor.into()));
    interpolation.insert("quality".to_owned(), Value::Number(quality.into()));
    values.insert(
        "frameInterpolation".to_owned(),
        Value::Object(interpolation),
    );

    let mut shader = values["videoShader"]
        .as_object()
        .cloned()
        .unwrap_or_default();
    let mut normalized = Map::new();
    normalized.insert(
        "enabled".to_owned(),
        Value::Bool(
            shader
                .remove("enabled")
                .and_then(|value| value.as_bool())
                .unwrap_or(false),
        ),
    );
    for (key, fallback, minimum, maximum) in [
        ("sharpen", 40, 0, 100),
        ("saturation", 100, 0, 200),
        ("contrast", 100, 0, 200),
        ("brightness", 100, 0, 200),
        ("vibrance", 0, -100, 100),
        ("filmGrain", 0, 0, 100),
    ] {
        let value = shader
            .remove(key)
            .and_then(|value| value.as_i64())
            .unwrap_or(fallback)
            .clamp(minimum, maximum);
        normalized.insert(key.to_owned(), Value::Number(value.into()));
    }
    values.insert("videoShader".to_owned(), Value::Object(normalized));
}

fn default_data_dir() -> PathBuf {
    if let Some(path) = env::var_os("OPENNOW_DATA_DIR") {
        return PathBuf::from(path);
    }
    #[cfg(target_os = "windows")]
    if let Some(path) = env::var_os("APPDATA") {
        return PathBuf::from(path).join("OpenNOW");
    }
    #[cfg(target_os = "macos")]
    if let Some(path) = env::var_os("HOME") {
        return PathBuf::from(path).join("Library/Application Support/OpenNOW");
    }
    if let Some(path) = env::var_os("XDG_CONFIG_HOME") {
        return PathBuf::from(path).join("OpenNOW");
    }
    env::var_os("HOME")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."))
        .join(".config/OpenNOW")
}

fn legacy_data_dirs(primary: &Path) -> Vec<PathBuf> {
    #[cfg(target_os = "linux")]
    {
        let mut candidates = Vec::new();
        if let Some(parent) = primary.parent() {
            candidates.push(parent.join("opennow"));
        }
        candidates
    }
    #[cfg(not(target_os = "linux"))]
    {
        let _ = primary;
        Vec::new()
    }
}

fn defaults() -> Map<String, Value> {
    json!({
        "resolution":"1920x1080", "aspectRatio":"16:9", "posterSizeScale":1.05,
        "fps":60, "frameGeneration":"off", "maxBitrateMbps":75, "recordingBitrateMbps":null,
        "recordingResolution":"720p", "recordingFps":30, "streamClientMode":"native",
        "nativeVideoBackend":"auto", "nativeStreamerExecutablePath":"", "audioOutputDevice":"",
        "nativeCloudGsyncMode":"auto", "nativeD3dFullscreenMode":"auto",
        "nativeExternalRenderer":false, "transportMode":"nvst", "showNativeStreamerStats":false,
        "codec":"auto", "fallbackCodec":"auto", "decoderPreference":"auto",
        "encoderPreference":"auto", "colorQuality":"8bit_420", "region":"",
        "sessionProxyEnabled":false, "sessionProxyUrl":"", "clipboardPaste":false,
        "enableGyroscopeControls":false, "steamControllerCompatibilityMode":false,
        "nativeCursorOverlay":true, "mouseSensitivity":1, "mouseAcceleration":1,
        "shortcutToggleStats":"Ctrl+N", "shortcutTogglePointerLock":"F8",
        "shortcutToggleFullscreen":"F11", "shortcutStopStream":"Ctrl+Shift+Q",
        "shortcutToggleAntiAfk":"Ctrl+Shift+K", "shortcutToggleMicrophone":"Ctrl+Shift+M",
        "shortcutScreenshot":"Ctrl+F11", "shortcutToggleRecording":"F12",
        "microphoneMode":"disabled", "microphoneDeviceId":"", "hideStreamButtons":false,
        "showAntiAfkIndicator":true, "antiAfkReminderEveryMinutes":15,
        "antiAfkReminderDurationSeconds":5, "showStatsOnLaunch":false,
        "statsOverlayPosition":"top-right", "hideServerSelector":false,
        "desktopUiScale":1.0, "statsOverlayScale":1.0, "statsOverlayOpacity":85,
        "themeAccentOverride":false,
        "statsShowFps":true, "statsShowRegion":true, "statsShowPing":true,
        "statsShowBitrate":true, "statsShowJitter":true, "statsShowDrops":true,
        "statsShowPacketLoss":true, "statsShowDecode":true, "statsShowLatency":true,
        "statsShowVideo":true, "statsShowClock":true, "statsShowGraphs":true,
        "appAccentColor":"green", "appTheme":"auto", "appLanguage":"system", "themePack":"nocturne", "translucentUI":false,
        "showTileLabels":true,
        "controllerMode":true, "controllerModePromptDismissed":false,
        "reducedMotion":false,
        "launchInConsoleMode":false, "consoleProfilePickerOnLaunch":true,
        "desktopRailCollapsed":true, "desktopSidebarHover":true, "desktopBackground":"art",
        "switchToConsoleOnPad":false, "leaveConsoleOnPointer":true,
        "autoFullScreen":false, "favoriteGameIds":[], "hiddenGameIds":[], "homeTileSizes":{}, "sessionCounterEnabled":false,
        "showSessionReport":true, "showSessionTimeRemainingInStatsOverlay":false,
        "sessionClockShowEveryMinutes":60, "sessionClockShowDurationSeconds":30,
        "windowWidth":1400, "windowHeight":900, "keyboardLayout":"en-US",
        "gameLanguage":"en_US", "enablePersistingInGameSettings":false, "enableL4S":false,
        "identifyAsSteamDeck":false, "enableCloudGsync":false, "discordRichPresence":false,
        "autoCheckForUpdates":true, "updateChannel":"stable",
        "allowEscapeToExitFullscreen":false, "lastSeenReleaseHighlightsVersion":"",
        "videoShader":{"enabled":false,"sharpen":40,"saturation":100,"contrast":100,"brightness":100,"vibrance":0,"filmGrain":0},
        "frameInterpolation":{"enabled":false,"factor":2,"quality":480},
        "errorReportingConsent":"unset", "telemetryInstallId":""
    })
    .as_object()
    .cloned()
    .expect("settings defaults are an object")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn audio_output_device_is_persisted_and_validated() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-audio-settings-{unique}"));
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["audioOutputDevice"], json!(""));
        let name = "USB Headphones – Audio";
        assert_eq!(
            store.set("audioOutputDevice", json!(name)).unwrap(),
            json!(name)
        );
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["audioOutputDevice"], json!(name));
        for invalid in [
            json!(42),
            json!(null),
            json!("speaker\0other"),
            json!("é".repeat(513)),
        ] {
            assert!(store.set("audioOutputDevice", invalid).is_err());
            assert_eq!(store.all()["audioOutputDevice"], json!(name));
        }
        store.set("audioOutputDevice", json!("")).unwrap();
        assert_eq!(
            SettingsStore::load(Some(directory.clone())).unwrap().all()["audioOutputDevice"],
            json!("")
        );
        let mut persisted: Value =
            serde_json::from_slice(&fs::read(directory.join("settings.json")).unwrap()).unwrap();
        persisted["audioOutputDevice"] = json!("bad\0device");
        fs::write(
            directory.join("settings.json"),
            serde_json::to_vec(&persisted).unwrap(),
        )
        .unwrap();
        assert_eq!(
            SettingsStore::load(Some(directory.clone())).unwrap().all()["audioOutputDevice"],
            json!("")
        );
        fs::remove_dir_all(directory).unwrap();
    }
    #[test]
    fn console_switching_is_opt_in_and_manual_desktop_survives_reload() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-console-policy-{unique}"));
        fs::create_dir_all(&directory).unwrap();
        fs::write(
            directory.join("settings.json"),
            serde_json::to_vec(&json!({
                "launchInConsoleMode": true,
                "switchToConsoleOnPad": true,
                "unrelatedLegacySetting": "preserve"
            }))
            .unwrap(),
        )
        .unwrap();
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["switchToConsoleOnPad"], json!(false));
        assert_eq!(store.all()["launchInConsoleMode"], json!(true));
        store.set("switchToConsoleOnPad", json!(true)).unwrap();
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(
            store.all()["switchToConsoleOnPad"],
            json!(true),
            "do not migrate a new opt-in twice"
        );
        // A failed atomic save must not change the in-memory policy either.
        let blocked_temporary = directory.join("settings.json.tmp");
        fs::create_dir(&blocked_temporary).unwrap();
        assert!(store.set("launchInConsoleMode", json!(false)).is_err());
        assert_eq!(store.all()["launchInConsoleMode"], json!(true));
        assert_eq!(store.all()["switchToConsoleOnPad"], json!(true));
        fs::remove_dir(blocked_temporary).unwrap();
        store.set("launchInConsoleMode", json!(false)).unwrap();
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["launchInConsoleMode"], json!(false));
        assert_eq!(store.all()["switchToConsoleOnPad"], json!(false));
        store.set("launchInConsoleMode", json!(true)).unwrap();
        assert_eq!(
            store.all()["switchToConsoleOnPad"],
            json!(false),
            "manual console is not automatic opt-in"
        );
        let persisted: Value =
            serde_json::from_slice(&fs::read(directory.join("settings.json")).unwrap()).unwrap();
        assert_eq!(persisted["unrelatedLegacySetting"], json!("preserve"));
        fs::remove_dir_all(directory).unwrap();
    }

    #[test]
    fn persists_and_normalizes_settings() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-core-settings-{unique}"));
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["launchInConsoleMode"], json!(false));
        assert_eq!(store.all()["transportMode"], json!("nvst"));
        assert_eq!(store.all()["desktopRailCollapsed"], json!(true));
        assert_eq!(store.all()["desktopSidebarHover"], json!(true));
        assert_eq!(store.all()["desktopBackground"], json!("art"));
        assert_eq!(
            store.set("desktopBackground", json!("gradient")).unwrap(),
            json!("gradient")
        );
        assert_eq!(
            store.set("desktopBackground", json!("invalid")).unwrap(),
            json!("art")
        );
        assert_eq!(
            store.set("desktopSidebarHover", json!(false)).unwrap(),
            json!(false)
        );
        assert_eq!(store.all()["switchToConsoleOnPad"], json!(false));
        assert_eq!(store.all()["leaveConsoleOnPointer"], json!(true));
        assert_eq!(store.all()["shortcutToggleFullscreen"], json!("F11"));
        assert_eq!(store.all()["shortcutScreenshot"], json!("Ctrl+F11"));
        assert_eq!(store.all()["statsOverlayPosition"], json!("top-right"));
        for key in [
            "statsShowFps",
            "statsShowRegion",
            "statsShowPing",
            "statsShowBitrate",
            "statsShowJitter",
            "statsShowDrops",
            "statsShowPacketLoss",
            "statsShowDecode",
            "statsShowLatency",
            "statsShowVideo",
            "statsShowClock",
            "statsShowGraphs",
        ] {
            assert_eq!(store.all()[key], json!(true));
            assert_eq!(store.set(key, json!(false)).unwrap(), json!(false));
        }
        assert_eq!(
            store.set("statsOverlayScale", json!(99)).unwrap(),
            json!(1.5)
        );
        assert_eq!(
            store.set("statsOverlayOpacity", json!(0)).unwrap(),
            json!(40.0)
        );
        assert_eq!(store.set("desktopUiScale", json!(0)).unwrap(), json!(0.85));
        let preferences = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(preferences.all()["desktopSidebarHover"], json!(false));
        assert_eq!(preferences.all()["statsShowFps"], json!(false));
        assert_eq!(preferences.all()["statsShowRegion"], json!(false));
        assert_eq!(preferences.all()["statsOverlayScale"], json!(1.5));
        assert_eq!(store.set("fps", json!(999)).unwrap(), json!(240));
        assert_eq!(store.set("maxBitrateMbps", json!(200)).unwrap(), json!(200));
        assert_eq!(
            store.set("launchInConsoleMode", json!(false)).unwrap(),
            json!(false)
        );
        assert_eq!(
            store.set("reducedMotion", json!(true)).unwrap(),
            json!(true)
        );
        let loaded = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(loaded.all()["fps"], json!(240));
        assert_eq!(loaded.all()["maxBitrateMbps"], json!(200));
        assert_eq!(loaded.all()["launchInConsoleMode"], json!(false));
        assert_eq!(loaded.all()["reducedMotion"], json!(true));
        assert!(store.set("notASetting", json!(true)).is_err());
        assert_eq!(store.set("codec", json!("invalid")).unwrap(), json!("auto"));
        assert_eq!(
            store.set("resolution", json!("3440x1440")).unwrap(),
            json!("3440x1440")
        );
        assert_eq!(
            store.set("resolution", json!("99999x1")).unwrap(),
            json!("1920x1080")
        );
        assert_eq!(
            store.set("controllerMode", json!("yes")).unwrap(),
            json!(true)
        );
        assert_eq!(
            store.set("mouseSensitivity", json!(99)).unwrap(),
            json!(3.0)
        );
        assert_eq!(
            store
                .set("frameInterpolation", json!({"enabled":true,"factor":99}))
                .unwrap(),
            json!({"enabled":true,"factor":4,"quality":480})
        );
        assert_eq!(
            store.set("themePack", json!("chapel")).unwrap(),
            json!("chapel")
        );
        assert_eq!(
            store.set("themePack", json!("unknown")).unwrap(),
            json!("nocturne")
        );
        assert_eq!(store.set("appLanguage", json!("de")).unwrap(), json!("de"));
        assert_eq!(
            store.set("appLanguage", json!("unsupported")).unwrap(),
            json!("system")
        );
        assert!(store.set("sessionProxyEnabled", json!(true)).is_err());
        assert_eq!(
            store
                .set("sessionProxyUrl", json!("proxy.example:8080"))
                .unwrap(),
            json!("http://proxy.example:8080/")
        );
        assert_eq!(
            store.set("sessionProxyEnabled", json!(true)).unwrap(),
            json!(true)
        );
        assert_eq!(
            store
                .set(
                    "homeTileSizes",
                    json!({"game-a":"wide","game-b":"giant","":"square"}),
                )
                .unwrap(),
            json!({"game-a":"wide"})
        );
        assert_eq!(
            store
                .set("hiddenGameIds", json!(["game-a", "", "game-b"]))
                .unwrap(),
            json!(["game-a", "game-b"])
        );
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn frame_generation_is_limited_to_off_or_2x() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-frame-generation-{unique}"));
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();

        assert_eq!(store.all()["frameGeneration"], json!("off"));
        assert_eq!(
            store.set("frameGeneration", json!("2x")).unwrap(),
            json!("2x")
        );
        assert_eq!(
            store.set("frameGeneration", json!("3x")).unwrap(),
            json!("off")
        );
        assert_eq!(
            store.set("frameGeneration", json!(true)).unwrap(),
            json!("off")
        );

        fs::write(
            directory.join("settings.json"),
            serde_json::to_vec(&json!({"frameGeneration": "invalid"})).unwrap(),
        )
        .unwrap();
        let store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["frameGeneration"], json!("off"));
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn legacy_f10_f11_pair_migrates_to_native_f11_fullscreen() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-core-f11-migration-{unique}"));
        fs::create_dir_all(&directory).unwrap();
        fs::write(
            directory.join("settings.json"),
            r#"{"shortcutToggleFullscreen":"F10","shortcutScreenshot":"F11"}"#,
        )
        .unwrap();

        let store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["shortcutToggleFullscreen"], json!("F11"));
        assert_eq!(store.all()["shortcutScreenshot"], json!("Ctrl+F11"));
        assert_eq!(store.all()["statsOverlayPosition"], json!("top-right"));

        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn existing_legacy_profile_wins_only_when_primary_is_absent() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let root = env::temp_dir().join(format!("opennow-core-profile-{unique}"));
        let primary = root.join("OpenNOW");
        let legacy = root.join("opennow");

        fs::create_dir_all(&legacy).unwrap();
        #[cfg(not(windows))]
        assert_eq!(
            select_existing_data_dir(primary.clone(), [legacy.clone()]),
            legacy
        );
        // Windows resolves these two historical spellings to the same directory,
        // so the preferred spelling is already considered present.
        #[cfg(windows)]
        assert_eq!(
            select_existing_data_dir(primary.clone(), [legacy.clone()]),
            primary
        );

        fs::create_dir_all(&primary).unwrap();
        assert_eq!(
            select_existing_data_dir(primary.clone(), [root.join("opennow")]),
            primary
        );
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn electron_settings_migrate_without_destroying_rollback_fields() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-core-legacy-settings-{unique}"));
        fs::create_dir_all(&directory).unwrap();
        fs::write(
            directory.join("settings.json"),
            serde_json::to_vec_pretty(&json!({
                "fps": 120,
                "mouseAcceleration": true,
                "transportMode": "webrtc",
                "sessionTimeRemainingDisplay": "both",
                "futureElectronSetting": {"enabled": true}
            }))
            .unwrap(),
        )
        .unwrap();

        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();
        assert_eq!(store.all()["fps"], json!(120));
        assert_eq!(store.all()["mouseAcceleration"], json!(100));
        assert_eq!(store.all()["transportMode"], json!("nvst"));
        assert_eq!(
            store.all()["showSessionTimeRemainingInStatsOverlay"],
            json!(true)
        );
        assert!(store.all().get("futureElectronSetting").is_none());
        store.set("codec", json!("h264")).unwrap();

        let persisted: Value =
            serde_json::from_slice(&fs::read(directory.join("settings.json")).unwrap()).unwrap();
        assert_eq!(persisted["futureElectronSetting"], json!({"enabled": true}));
        assert_eq!(persisted["transportMode"], json!("nvst"));
        assert_eq!(persisted["sessionTimeRemainingDisplay"], json!("both"));
        assert_eq!(persisted["mouseAcceleration"], json!(100));
        assert_eq!(persisted["codec"], json!("h264"));
        fs::remove_dir_all(directory).unwrap();
    }

    #[test]
    fn every_legacy_transport_selector_normalizes_and_persists_as_nvst() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = env::temp_dir().join(format!("opennow-core-transport-{unique}"));
        let mut store = SettingsStore::load(Some(directory.clone())).unwrap();

        for legacy in [
            json!("webrtc"),
            json!("browser"),
            json!("auto"),
            json!(null),
        ] {
            assert_eq!(store.set("transportMode", legacy).unwrap(), json!("nvst"));
        }

        let persisted: Value =
            serde_json::from_slice(&fs::read(directory.join("settings.json")).unwrap()).unwrap();
        assert_eq!(persisted["transportMode"], json!("nvst"));
        fs::remove_dir_all(directory).unwrap();
    }
}
