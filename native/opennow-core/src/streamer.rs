use rand::RngCore as _;
use serde_json::{Value, json};
use std::collections::HashMap;
use std::fs;
use std::io::{BufRead, BufReader, ErrorKind, Write};
use std::net::{IpAddr, TcpStream};
use std::ops::{Deref, DerefMut};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tungstenite::client::IntoClientRequest;
use tungstenite::http::HeaderValue;
use tungstenite::http::header::{ORIGIN, SEC_WEBSOCKET_PROTOCOL, USER_AGENT};
use tungstenite::stream::MaybeTlsStream;
use tungstenite::{Message, WebSocket, connect};
use url::Url;

const STREAMER_PROTOCOL_VERSION: u64 = 4;
const CHILD_MESSAGE_LIMIT: usize = 1024 * 1024;
const CHILD_START_TIMEOUT: Duration = Duration::from_secs(45);
const CAPABILITY_PROBE_TIMEOUT: Duration = Duration::from_secs(10);
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(5);

#[derive(Clone, Debug)]
pub struct StreamerError {
    pub code: &'static str,
    pub message: String,
}

#[derive(Clone)]
struct Snapshot {
    status: String,
    message: String,
    session_id: Option<String>,
    process_id: Option<u64>,
    transport: Option<String>,
    capabilities: Value,
    executable: Option<PathBuf>,
    error_code: Option<String>,
    overlay_request_generation: u64,
    screenshot_request_generation: u64,
    recording_toggle_request_generation: u64,
    shortcut_action_generation: u64,
    shortcut_action: Option<String>,
    microphone_state: String,
    microphone_enabled: bool,
    microphone_message: Option<String>,
    started_at: Option<Instant>,
    session_started_at_ms: Option<u128>,
    first_frame_latency_ms: Option<u64>,
    media_backend: Option<String>,
    backend_fallback_count: u64,
    decoder_error_count: u64,
    output_error_count: u64,
    device_loss_count: u64,
    device_recovery_count: u64,
    queue_drop_count: u64,
    input_ready: bool,
    input_unavailable_reason: Option<String>,
    input_pause_count: u64,
    input_resume_count: u64,
    surface_update_count: u64,
    fullscreen_toggle_count: u64,
    stats_toggle_count: u64,
    recording_start_count: u64,
    recording_stop_count: u64,
}

impl Default for Snapshot {
    fn default() -> Self {
        Self {
            status: "stopped".to_owned(),
            message: "Native streamer is not running".to_owned(),
            session_id: None,
            process_id: None,
            transport: None,
            capabilities: Value::Null,
            executable: None,
            error_code: None,
            overlay_request_generation: 0,
            screenshot_request_generation: 0,
            recording_toggle_request_generation: 0,
            shortcut_action_generation: 0,
            shortcut_action: None,
            microphone_state: "disabled".to_owned(),
            microphone_enabled: false,
            microphone_message: None,
            started_at: None,
            session_started_at_ms: None,
            first_frame_latency_ms: None,
            media_backend: None,
            backend_fallback_count: 0,
            decoder_error_count: 0,
            output_error_count: 0,
            device_loss_count: 0,
            device_recovery_count: 0,
            queue_drop_count: 0,
            input_ready: false,
            input_unavailable_reason: None,
            input_pause_count: 0,
            input_resume_count: 0,
            surface_update_count: 0,
            fullscreen_toggle_count: 0,
            stats_toggle_count: 0,
            recording_start_count: 0,
            recording_stop_count: 0,
        }
    }
}

impl Snapshot {
    fn value(&self) -> Value {
        json!({"streamer":{
            "status":self.status,
            "message":self.message,
            "sessionId":self.session_id,
            "processId":self.process_id,
            "transport":self.transport,
            "capabilities":self.capabilities,
            "executable":self.executable.as_ref().map(|path| path.to_string_lossy().into_owned()),
            "errorCode":self.error_code,
            "overlayRequestGeneration":self.overlay_request_generation,
            "screenshotRequestGeneration":self.screenshot_request_generation,
            "recordingToggleRequestGeneration":self.recording_toggle_request_generation,
            "shortcutActionGeneration":self.shortcut_action_generation,
            "shortcutAction":self.shortcut_action,
            "microphoneState":self.microphone_state,
            "microphoneEnabled":self.microphone_enabled,
            "microphoneMessage":self.microphone_message,
            "sessionUptimeMs":self.started_at.map(|started| elapsed_millis(started.elapsed())),
            "sessionStartedAtMs":self.session_started_at_ms.map(|value| value.to_string()),
            "firstFrameLatencyMs":self.first_frame_latency_ms,
            "mediaBackend":self.media_backend,
            "backendFallbackCount":self.backend_fallback_count,
            "decoderErrorCount":self.decoder_error_count,
            "outputErrorCount":self.output_error_count,
            "deviceLossCount":self.device_loss_count,
            "deviceRecoveryCount":self.device_recovery_count,
            "queueDropCount":self.queue_drop_count,
            "inputReady":self.input_ready,
            "inputUnavailableReason":self.input_unavailable_reason,
            "inputPauseCount":self.input_pause_count,
            "inputResumeCount":self.input_resume_count,
            "surfaceUpdateCount":self.surface_update_count,
            "fullscreenToggleCount":self.fullscreen_toggle_count,
            "statsToggleCount":self.stats_toggle_count,
            "recordingStartCount":self.recording_start_count,
            "recordingStopCount":self.recording_stop_count
        }})
    }

    fn acceptance_value(&self) -> Value {
        let available_video_backends = self.capabilities["videoBackends"]
            .as_array()
            .into_iter()
            .flatten()
            .filter(|backend| backend["available"].as_bool().unwrap_or(false))
            .filter_map(|backend| backend["backend"].as_str())
            .collect::<Vec<_>>();
        json!({
            "schemaVersion": 1,
            "kind": "opennow.stream.acceptance",
            "status": self.status,
            "transport": self.transport,
            "errorCode": self.error_code,
            "sessionUptimeMs": self.started_at.map(|started| elapsed_millis(started.elapsed())),
            "sessionStartedAtMs": self.session_started_at_ms.map(|value| value.to_string()),
            "firstFrameLatencyMs": self.first_frame_latency_ms,
            "mediaBackend": self.media_backend,
            "backendFallbackCount": self.backend_fallback_count,
            "decoderErrorCount": self.decoder_error_count,
            "outputErrorCount": self.output_error_count,
            "deviceLossCount": self.device_loss_count,
            "deviceRecoveryCount": self.device_recovery_count,
            "queueDropCount": self.queue_drop_count,
            "inputReady": self.input_ready,
            "inputUnavailableReason": self.input_unavailable_reason,
            "microphoneState": self.microphone_state,
            "microphoneEnabled": self.microphone_enabled,
            "inputPauseCount": self.input_pause_count,
            "inputResumeCount": self.input_resume_count,
            "surfaceUpdateCount": self.surface_update_count,
            "fullscreenToggleCount": self.fullscreen_toggle_count,
            "statsToggleCount": self.stats_toggle_count,
            "recordingStartCount": self.recording_start_count,
            "recordingStopCount": self.recording_stop_count,
            "availableVideoBackends": available_video_backends,
            "availableCodecs": available_codecs(&self.capabilities)
        })
    }
}

struct Worker {
    control: Sender<WorkerCommand>,
    join: JoinHandle<()>,
}

struct KillOnDrop(Child);

impl Deref for KillOnDrop {
    type Target = Child;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for KillOnDrop {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl Drop for KillOnDrop {
    fn drop(&mut self) {
        if self.0.try_wait().ok().flatten().is_none() {
            let _ = self.0.kill();
            let _ = self.0.wait();
        }
    }
}

enum WorkerCommand {
    Stop(String),
    InputPaused(bool),
    Control(String),
    Surface(Value),
    Recording {
        enabled: bool,
        output_path: Option<PathBuf>,
        reply: mpsc::SyncSender<Result<Value, StreamerError>>,
    },
}

struct SignalingContext<'a> {
    session_id: &'a str,
    signaling_url: &'a str,
    streamer_context: &'a Value,
}

pub struct StreamerService {
    state: Arc<Mutex<Snapshot>>,
    worker: Mutex<Option<Worker>>,
}

impl StreamerService {
    pub fn new() -> Self {
        Self {
            state: Arc::new(Mutex::new(Snapshot::default())),
            worker: Mutex::new(None),
        }
    }

    pub fn detect(&self, settings: &Value) -> Result<Value, StreamerError> {
        let executable = resolve_executable(settings)?;
        let metadata = fs::metadata(&executable).map_err(|error| StreamerError {
            code: "streamer_not_found",
            message: format!(
                "Native streamer was not found at {}: {error}",
                executable.display()
            ),
        })?;
        if !metadata.is_file() {
            return Err(StreamerError {
                code: "streamer_not_found",
                message: format!(
                    "Native streamer path is not a file: {}",
                    executable.display()
                ),
            });
        }
        let capabilities = probe_capabilities(&executable, settings)?;
        let available_codecs = available_codecs(&capabilities);
        Ok(json!({
            "available":true,
            "protocolVersion":STREAMER_PROTOCOL_VERSION,
            "path":executable.to_string_lossy(),
            "sizeBytes":metadata.len(),
            "capabilities":capabilities,
            "availableCodecs":available_codecs
        }))
    }

    pub fn validate_codec(&self, settings: &Value) -> Result<(), StreamerError> {
        let requested = settings["codec"]
            .as_str()
            .unwrap_or("auto")
            .trim()
            .to_ascii_lowercase();
        // Auto currently requests the portable H.264 baseline from CloudMatch. Probe it too:
        // an explicit hardware-only decoder policy can make even that baseline unavailable.
        let codec = match requested.as_str() {
            "" | "auto" => "h264",
            _ => normalize_codec_name(&requested).ok_or_else(|| invalid("Unknown video codec"))?,
        };
        let detection = self.detect(settings)?;
        ensure_codec_available(&detection["capabilities"], codec)
    }

    pub fn start(&self, params: &Value, settings: &Value) -> Result<Value, StreamerError> {
        self.reap_finished();
        let mut worker = self.worker.lock().expect("streamer worker poisoned");
        if worker.is_some() {
            return Err(StreamerError {
                code: "streamer_busy",
                message: "A native streamer session is already running".to_owned(),
            });
        }
        let executable = resolve_executable(settings)?;
        if !executable.is_file() {
            return Err(StreamerError {
                code: "streamer_not_found",
                message: format!(
                    "Native streamer is not installed at {}",
                    executable.display()
                ),
            });
        }
        let session = params["session"]
            .as_object()
            .map(|_| params["session"].clone())
            .ok_or_else(|| invalid("streamer.start requires a ready session"))?;
        let status = session["status"].as_i64().unwrap_or_default();
        if !matches!(status, 2 | 3) {
            return Err(invalid(
                "CloudMatch session is not ready for media attachment",
            ));
        }
        let session_id = required_string(&session, "sessionId")?.to_owned();
        let transport = settings["transportMode"].as_str().unwrap_or("webrtc");
        let microphone_mode = settings["microphoneMode"].as_str().unwrap_or("disabled");
        let signaling_url = session["signalingUrl"]
            .as_str()
            .unwrap_or_default()
            .to_owned();
        if !transport.eq_ignore_ascii_case("nvst") && !signaling_url.starts_with("wss://") {
            return Err(invalid("Session signaling endpoint is not secure"));
        }
        let mut context = streamer_context(session, settings);
        let surface = normalize_surface(params.get("surface"), context_resolution(&context))?;
        context["surface"] = surface;
        let (control_tx, control_rx) = mpsc::channel();
        let state = Arc::clone(&self.state);
        {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.status = "starting".to_owned();
            snapshot.message = "Launching the native media runtime…".to_owned();
            snapshot.session_id = Some(session_id.clone());
            snapshot.process_id = None;
            snapshot.transport = None;
            snapshot.executable = Some(executable.clone());
            snapshot.error_code = None;
            snapshot.started_at = Some(Instant::now());
            snapshot.session_started_at_ms = Some(unix_time_millis());
            snapshot.first_frame_latency_ms = None;
            snapshot.media_backend = None;
            snapshot.backend_fallback_count = 0;
            snapshot.decoder_error_count = 0;
            snapshot.output_error_count = 0;
            snapshot.device_loss_count = 0;
            snapshot.device_recovery_count = 0;
            snapshot.queue_drop_count = 0;
            snapshot.input_ready = false;
            snapshot.input_unavailable_reason = None;
            snapshot.input_pause_count = 0;
            snapshot.input_resume_count = 0;
            snapshot.surface_update_count = 0;
            snapshot.fullscreen_toggle_count = 0;
            snapshot.stats_toggle_count = 0;
            snapshot.recording_start_count = 0;
            snapshot.recording_stop_count = 0;
            match (microphone_mode, transport.eq_ignore_ascii_case("nvst")) {
                ("voice-activity", false) => {
                    snapshot.microphone_state = "starting".to_owned();
                    snapshot.microphone_enabled = false;
                    snapshot.microphone_message =
                        Some("Microphone capture will start after WebRTC negotiation".to_owned());
                }
                ("voice-activity", true) => {
                    snapshot.microphone_state = "unavailable".to_owned();
                    snapshot.microphone_enabled = false;
                    snapshot.microphone_message = Some(
                        "Microphone upstream is available only with WebRTC transport".to_owned(),
                    );
                }
                ("push-to-talk", _) => {
                    snapshot.microphone_state = "unavailable".to_owned();
                    snapshot.microphone_enabled = false;
                    snapshot.microphone_message =
                        Some("Push-to-talk is not supported by the native runtime".to_owned());
                }
                _ => {
                    snapshot.microphone_state = "disabled".to_owned();
                    snapshot.microphone_enabled = false;
                    snapshot.microphone_message = None;
                }
            }
        }
        let join = thread::Builder::new()
            .name("opennow-streamer-coordinator".to_owned())
            .spawn(move || {
                if let Err(error) = run_worker(
                    &executable,
                    &session_id,
                    &signaling_url,
                    context,
                    control_rx,
                    Arc::clone(&state),
                ) {
                    set_error(&state, error.code, error.message);
                }
            })
            .map_err(|error| StreamerError {
                code: "streamer_spawn_failed",
                message: error.to_string(),
            })?;
        *worker = Some(Worker {
            control: control_tx,
            join,
        });
        drop(worker);
        Ok(self.status())
    }

    pub fn status(&self) -> Value {
        self.reap_finished();
        self.state.lock().expect("streamer state poisoned").value()
    }

    pub fn acceptance_snapshot(&self) -> Value {
        self.reap_finished();
        self.state
            .lock()
            .expect("streamer state poisoned")
            .acceptance_value()
    }

    pub fn stop(&self, reason: &str) -> Result<Value, StreamerError> {
        let item = self.worker.lock().expect("streamer worker poisoned").take();
        if let Some(worker) = item {
            let _ = worker.control.send(WorkerCommand::Stop(reason.to_owned()));
            worker.join.join().map_err(|_| StreamerError {
                code: "streamer_stop_failed",
                message: "Native streamer coordinator panicked during shutdown".to_owned(),
            })?;
        }
        let mut snapshot = self.state.lock().expect("streamer state poisoned");
        snapshot.status = "stopped".to_owned();
        snapshot.message = reason.to_owned();
        snapshot.session_id = None;
        snapshot.process_id = None;
        snapshot.transport = None;
        snapshot.error_code = None;
        snapshot.microphone_state = "disabled".to_owned();
        snapshot.microphone_enabled = false;
        snapshot.microphone_message = None;
        snapshot.started_at = None;
        Ok(snapshot.value())
    }

    pub fn set_input_paused(&self, paused: bool) -> Result<Value, StreamerError> {
        self.reap_finished();
        let worker = self.worker.lock().expect("streamer worker poisoned");
        let Some(worker) = worker.as_ref() else {
            return Ok(json!({"paused":paused,"streamerRunning":false}));
        };
        worker
            .control
            .send(WorkerCommand::InputPaused(paused))
            .map_err(|_| StreamerError {
                code: "streamer_control_failed",
                message: "Native streamer control channel is closed".to_owned(),
            })?;
        let mut snapshot = self.state.lock().expect("streamer state poisoned");
        if paused {
            snapshot.input_pause_count = snapshot.input_pause_count.saturating_add(1);
        } else {
            snapshot.input_resume_count = snapshot.input_resume_count.saturating_add(1);
        }
        Ok(json!({"paused":paused,"streamerRunning":true}))
    }

    pub fn control(&self, action: &str) -> Result<Value, StreamerError> {
        let child_command = match action {
            "toggle-stats" => "stats-toggle",
            "toggle-fullscreen" => "fullscreen-toggle",
            "toggle-microphone" => "microphone-toggle",
            "anti-afk-pulse" => "anti-afk-pulse",
            _ => return Err(invalid("Unknown native streamer control action")),
        };
        self.reap_finished();
        let worker = self.worker.lock().expect("streamer worker poisoned");
        let Some(worker) = worker.as_ref() else {
            return Err(StreamerError {
                code: "streamer_not_running",
                message: "Native streamer is not running".to_owned(),
            });
        };
        worker
            .control
            .send(WorkerCommand::Control(child_command.to_owned()))
            .map_err(|_| StreamerError {
                code: "streamer_control_failed",
                message: "Native streamer control channel is closed".to_owned(),
            })?;
        let mut snapshot = self.state.lock().expect("streamer state poisoned");
        if action == "toggle-fullscreen" {
            snapshot.fullscreen_toggle_count = snapshot.fullscreen_toggle_count.saturating_add(1);
        } else if action == "toggle-stats" {
            snapshot.stats_toggle_count = snapshot.stats_toggle_count.saturating_add(1);
        }
        Ok(json!({"action":action,"streamerRunning":true}))
    }

    pub fn update_surface(&self, params: &Value) -> Result<Value, StreamerError> {
        let surface = normalize_surface(params.get("surface"), (1280, 720))?;
        self.reap_finished();
        let worker = self.worker.lock().expect("streamer worker poisoned");
        let Some(worker) = worker.as_ref() else {
            return Ok(json!({"applied":false,"streamerRunning":false}));
        };
        worker
            .control
            .send(WorkerCommand::Surface(surface))
            .map_err(|_| StreamerError {
                code: "streamer_control_failed",
                message: "Native streamer control channel is closed".to_owned(),
            })?;
        let mut snapshot = self.state.lock().expect("streamer state poisoned");
        snapshot.surface_update_count = snapshot.surface_update_count.saturating_add(1);
        Ok(json!({"applied":true,"streamerRunning":true}))
    }

    pub fn recording(&self, params: &Value, enabled: bool) -> Result<Value, StreamerError> {
        self.reap_finished();
        let output_path = if enabled {
            let path = PathBuf::from(
                params["outputPath"]
                    .as_str()
                    .filter(|value| !value.is_empty())
                    .ok_or_else(|| invalid("Native recording requires an outputPath"))?,
            );
            if !path.is_absolute()
                || path.extension().and_then(|value| value.to_str()) != Some("mkv")
            {
                return Err(invalid(
                    "Native recording requires an absolute .mkv outputPath",
                ));
            }
            Some(path)
        } else {
            None
        };
        let (reply, response) = mpsc::sync_channel(1);
        {
            let worker = self.worker.lock().expect("streamer worker poisoned");
            let Some(worker) = worker.as_ref() else {
                return Err(StreamerError {
                    code: "streamer_not_running",
                    message: "Native streamer is not running".to_owned(),
                });
            };
            worker
                .control
                .send(WorkerCommand::Recording {
                    enabled,
                    output_path,
                    reply,
                })
                .map_err(|_| StreamerError {
                    code: "streamer_control_failed",
                    message: "Native streamer control channel is closed".to_owned(),
                })?;
        }
        let result = response
            .recv_timeout(Duration::from_secs(15))
            .map_err(|_| StreamerError {
                code: "streamer_timeout",
                message: "Native streamer timed out finalizing the recording".to_owned(),
            })??;
        let mut snapshot = self.state.lock().expect("streamer state poisoned");
        if enabled {
            snapshot.recording_start_count = snapshot.recording_start_count.saturating_add(1);
        } else {
            snapshot.recording_stop_count = snapshot.recording_stop_count.saturating_add(1);
        }
        Ok(result)
    }

    fn reap_finished(&self) {
        let finished = self
            .worker
            .lock()
            .expect("streamer worker poisoned")
            .as_ref()
            .is_some_and(|worker| worker.join.is_finished());
        if finished
            && let Some(worker) = self.worker.lock().expect("streamer worker poisoned").take()
        {
            let _ = worker.join.join();
        }
    }
}

impl Drop for StreamerService {
    fn drop(&mut self) {
        if let Some(worker) = self
            .worker
            .get_mut()
            .expect("streamer worker poisoned")
            .take()
        {
            let _ = worker
                .control
                .send(WorkerCommand::Stop("OpenNOW core shutdown".to_owned()));
            let _ = worker.join.join();
        }
    }
}

fn streamer_command(executable: &Path, settings: &Value) -> Command {
    let requested_backend = settings["nativeVideoBackend"]
        .as_str()
        .unwrap_or("auto")
        .trim()
        .to_ascii_lowercase();
    let decoder_preference = settings["decoderPreference"]
        .as_str()
        .unwrap_or("auto")
        .trim()
        .to_ascii_lowercase();
    let decoder_backend = match requested_backend.as_str() {
        "auto" | "" => match decoder_preference.as_str() {
            "hardware" => "hardware",
            "software" => "software",
            _ => "auto",
        },
        explicit => explicit,
    };
    let cursor_overlay = if settings["nativeCursorOverlay"].as_bool().unwrap_or(true) {
        "1"
    } else {
        "0"
    };
    let mouse_sensitivity = settings["mouseSensitivity"]
        .as_f64()
        .unwrap_or(1.0)
        .clamp(0.1, 3.0)
        .to_string();
    let mouse_acceleration = settings["mouseAcceleration"]
        .as_f64()
        .unwrap_or(1.0)
        .clamp(1.0, 150.0)
        .to_string();
    let mut command = Command::new(executable);
    command
        .env("OPENNOW_NATIVE_EXTERNAL_RENDERER", "1")
        .env("OPENNOW_NATIVE_VIDEO_BACKEND", decoder_backend)
        .env("OPENNOW_NATIVE_CURSOR_OVERLAY", cursor_overlay)
        .env("OPENNOW_MOUSE_SENSITIVITY", mouse_sensitivity)
        .env("OPENNOW_MOUSE_ACCELERATION", mouse_acceleration)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    command
}

fn probe_capabilities(executable: &Path, settings: &Value) -> Result<Value, StreamerError> {
    let mut child = KillOnDrop(streamer_command(executable, settings).spawn().map_err(
        |error| StreamerError {
            code: "streamer_spawn_failed",
            message: format!("Could not probe {}: {error}", executable.display()),
        },
    )?);
    let mut stdin = child
        .stdin
        .take()
        .ok_or_else(|| internal("Streamer probe stdin is unavailable"))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| internal("Streamer probe stdout is unavailable"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| internal("Streamer probe stderr is unavailable"))?;
    let (child_tx, child_rx) = mpsc::channel::<Value>();
    let reader = spawn_stdout_reader(stdout, child_tx)?;
    let stderr_reader = thread::Builder::new()
        .name("opennow-streamer-probe-stderr".to_owned())
        .spawn(move || {
            for line in BufReader::new(stderr).lines().map_while(Result::ok) {
                eprintln!("native-streamer probe: {line}");
            }
        })
        .map_err(|error| StreamerError {
            code: "streamer_spawn_failed",
            message: error.to_string(),
        })?;
    let state = Arc::new(Mutex::new(Snapshot::default()));
    write_child(
        &mut stdin,
        &json!({"id":"hello","type":"hello","protocolVersion":STREAMER_PROTOCOL_VERSION}),
    )?;
    let hello = wait_for_child(&child_rx, "hello", CAPABILITY_PROBE_TIMEOUT, &state)?;
    if hello["type"] != "ready" {
        return Err(child_error(
            &hello,
            "Native streamer capability probe failed",
        ));
    }
    let capabilities = hello["capabilities"].clone();
    if !capabilities.is_object()
        || capabilities["protocolVersion"].as_u64() != Some(STREAMER_PROTOCOL_VERSION)
    {
        return Err(StreamerError {
            code: "streamer_protocol_mismatch",
            message: format!(
                "Native streamer did not report protocol {STREAMER_PROTOCOL_VERSION} capabilities"
            ),
        });
    }
    let _ = write_child(
        &mut stdin,
        &json!({"id":"shutdown","type":"shutdown","reason":"capability probe complete"}),
    );
    wait_or_kill(&mut child);
    drop(stdin);
    let _ = reader.join();
    let _ = stderr_reader.join();
    Ok(capabilities)
}

fn normalize_codec_name(value: &str) -> Option<&'static str> {
    match value.trim().to_ascii_lowercase().as_str() {
        "h264" | "avc" | "auto" | "" => Some("h264"),
        "h265" | "hevc" => Some("h265"),
        "av1" => Some("av1"),
        _ => None,
    }
}

fn available_codecs(capabilities: &Value) -> Vec<&'static str> {
    ["h264", "h265", "av1"]
        .into_iter()
        .filter(|codec| codec_available(capabilities, codec))
        .collect()
}

fn codec_available(capabilities: &Value, codec: &str) -> bool {
    capabilities["videoBackends"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|backend| backend["available"].as_bool().unwrap_or(false))
        .flat_map(|backend| backend["codecs"].as_array().into_iter().flatten())
        .any(|entry| {
            entry["codec"]
                .as_str()
                .is_some_and(|value| value.eq_ignore_ascii_case(codec))
                && entry["available"].as_bool().unwrap_or(false)
        })
}

fn ensure_codec_available(capabilities: &Value, codec: &str) -> Result<(), StreamerError> {
    if codec_available(capabilities, codec) {
        return Ok(());
    }
    let reasons = capabilities["videoBackends"]
        .as_array()
        .into_iter()
        .flatten()
        .flat_map(|backend| backend["codecs"].as_array().into_iter().flatten())
        .filter(|entry| {
            entry["codec"]
                .as_str()
                .is_some_and(|value| value.eq_ignore_ascii_case(codec))
        })
        .filter_map(|entry| entry["reason"].as_str())
        .collect::<Vec<_>>();
    let detail = reasons
        .first()
        .copied()
        .unwrap_or("no selected native backend advertises this codec");
    Err(StreamerError {
        code: "streamer_codec_unavailable",
        message: format!("{} is unavailable: {detail}", codec.to_ascii_uppercase()),
    })
}

fn run_worker(
    executable: &Path,
    session_id: &str,
    signaling_url: &str,
    mut context: Value,
    control: Receiver<WorkerCommand>,
    state: Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    let mut child = KillOnDrop(
        streamer_command(executable, &context["settings"])
            .spawn()
            .map_err(|error| StreamerError {
                code: "streamer_spawn_failed",
                message: format!("Could not launch {}: {error}", executable.display()),
            })?,
    );
    let process_id = u64::from(child.id());
    let mut stdin = child
        .stdin
        .take()
        .ok_or_else(|| internal("Streamer stdin is unavailable"))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| internal("Streamer stdout is unavailable"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| internal("Streamer stderr is unavailable"))?;
    let (child_tx, child_rx) = mpsc::channel::<Value>();
    let reader = spawn_stdout_reader(stdout, child_tx)?;
    let stderr_reader = thread::Builder::new()
        .name("opennow-streamer-stderr".to_owned())
        .spawn(move || {
            for line in BufReader::new(stderr).lines().map_while(Result::ok) {
                eprintln!("native-streamer: {line}");
            }
        })
        .map_err(|error| StreamerError {
            code: "streamer_spawn_failed",
            message: error.to_string(),
        })?;

    write_child(
        &mut stdin,
        &json!({"id":"hello","type":"hello","protocolVersion":STREAMER_PROTOCOL_VERSION}),
    )?;
    let hello = wait_for_child(&child_rx, "hello", CHILD_START_TIMEOUT, &state)?;
    if hello["type"] != "ready" {
        return Err(child_error(&hello, "Native streamer handshake failed"));
    }
    let negotiated_codec = context["settings"]["codec"]
        .as_str()
        .and_then(normalize_codec_name)
        .unwrap_or("h264");
    ensure_codec_available(&hello["capabilities"], negotiated_codec)?;
    {
        let mut snapshot = state.lock().expect("streamer state poisoned");
        snapshot.process_id = hello["processId"].as_u64().or(Some(process_id));
        snapshot.capabilities = hello["capabilities"].clone();
        snapshot.status = "starting".to_owned();
        snapshot.message = "Preparing native video and audio output…".to_owned();
    }
    let nvst = context["settings"]["transportMode"]
        .as_str()
        .is_some_and(|value| value.eq_ignore_ascii_case("nvst"));
    if nvst {
        let result = run_nvst(
            session_id,
            &mut context,
            &mut stdin,
            &mut child,
            &child_rx,
            &control,
            &state,
        );
        let _ = write_child(
            &mut stdin,
            &json!({"id":"shutdown","type":"shutdown","reason":"OpenNOW session ended"}),
        );
        wait_or_kill(&mut child);
        drop(stdin);
        let _ = reader.join();
        let _ = stderr_reader.join();
        return result;
    }
    start_child(&mut stdin, &child_rx, &context, &state)?;
    ensure_child_running(&mut child)?;
    {
        let mut snapshot = state.lock().expect("streamer state poisoned");
        snapshot.status = "connecting".to_owned();
        snapshot.message = "Attaching secure GeForce NOW signaling…".to_owned();
    }
    write_surface(&mut stdin, &context["surface"])?;

    let result = run_signaling(
        SignalingContext {
            session_id,
            signaling_url,
            streamer_context: &context,
        },
        &mut stdin,
        &mut child,
        &child_rx,
        &control,
        &state,
    );
    let _ = write_child(
        &mut stdin,
        &json!({"id":"shutdown","type":"shutdown","reason":"OpenNOW session ended"}),
    );
    wait_or_kill(&mut child);
    drop(stdin);
    let _ = reader.join();
    let _ = stderr_reader.join();
    result
}

fn start_child(
    stdin: &mut ChildStdin,
    child_rx: &Receiver<Value>,
    context: &Value,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    write_child(
        stdin,
        &json!({"id":"start","type":"start","context":context}),
    )?;
    let started = wait_for_child(child_rx, "start", CHILD_START_TIMEOUT, state)?;
    if started["type"] != "ok" {
        return Err(child_error(&started, "Native media startup failed"));
    }
    state.lock().expect("streamer state poisoned").transport =
        started["transport"].as_str().map(ToOwned::to_owned);
    Ok(())
}

struct RtspResponse {
    status: u16,
    status_text: String,
    headers: HashMap<String, String>,
    body: String,
}

struct RtspClient {
    socket: WebSocket<MaybeTlsStream<TcpStream>>,
    cseq: u64,
    buffer: String,
}

impl RtspClient {
    fn connect(endpoint: &str, session_id: &str) -> Result<(Self, String), StreamerError> {
        let translated = endpoint
            .replacen("rtsps://", "https://", 1)
            .replacen("rtsp://", "http://", 1);
        let parsed = Url::parse(&translated).map_err(|_| invalid("Invalid RTSPS endpoint"))?;
        let host = parsed
            .host_str()
            .ok_or_else(|| invalid("RTSPS endpoint has no host"))?;
        if !trusted_nvst_host(host) {
            return Err(invalid("Untrusted RTSPS endpoint"));
        }
        let port = parsed.port().unwrap_or(322);
        let mut wss = Url::parse(&format!("wss://{host}:{port}/rtsp"))
            .map_err(|_| invalid("Invalid RTSPS WebSocket URL"))?;
        if host.contains(':') {
            wss.set_host(Some(host))
                .map_err(|_| invalid("Invalid RTSPS IPv6 host"))?;
        }
        let mut request = wss
            .as_str()
            .into_client_request()
            .map_err(|error| StreamerError {
                code: "nvst_connect_failed",
                message: error.to_string(),
            })?;
        request.headers_mut().insert(
            "x-nv-sessionid",
            HeaderValue::from_str(session_id)
                .map_err(|_| invalid("Invalid NVST session identity"))?,
        );
        request
            .headers_mut()
            .insert("content-length", HeaderValue::from_static("0"));
        let (mut socket, _) = connect(request).map_err(|error| StreamerError {
            code: "nvst_connect_failed",
            message: format!("Could not open RTSPS control channel: {error}"),
        })?;
        set_read_timeout(&mut socket, Duration::from_secs(20));
        Ok((
            Self {
                socket,
                cseq: 0,
                buffer: String::new(),
            },
            format!("rtsps://{host}:{port}"),
        ))
    }

    fn request(
        &mut self,
        method: &str,
        uri: &str,
        headers: &[(&str, String)],
        body: &str,
    ) -> Result<RtspResponse, StreamerError> {
        self.cseq += 1;
        let mut request = format!("{method} {uri} RTSP/1.0\r\nCSeq: {}\r\n", self.cseq);
        for (name, value) in headers {
            request.push_str(&format!("{name}: {value}\r\n"));
        }
        if !body.is_empty() {
            request.push_str(&format!("Content-Length: {}\r\n", body.len()));
        }
        request.push_str("\r\n");
        request.push_str(body);
        self.socket
            .send(Message::Text(request.into()))
            .map_err(|error| StreamerError {
                code: "nvst_rtsp_failed",
                message: error.to_string(),
            })?;
        let deadline = Instant::now() + Duration::from_secs(20);
        loop {
            if let Some(response) = take_rtsp_response(&mut self.buffer, self.cseq)? {
                return Ok(response);
            }
            if Instant::now() >= deadline {
                return Err(StreamerError {
                    code: "nvst_rtsp_timeout",
                    message: format!("RTSPS {method} timed out"),
                });
            }
            match self.socket.read() {
                Ok(Message::Text(text)) => self.buffer.push_str(text.as_str()),
                Ok(Message::Binary(bytes)) => {
                    self.buffer.push_str(&String::from_utf8_lossy(&bytes))
                }
                Ok(Message::Ping(bytes)) => {
                    let _ = self.socket.send(Message::Pong(bytes));
                }
                Ok(Message::Close(_)) => {
                    return Err(StreamerError {
                        code: "nvst_rtsp_failed",
                        message: "RTSPS control channel closed".to_owned(),
                    });
                }
                Ok(_) => {}
                Err(tungstenite::Error::Io(error))
                    if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {}
                Err(error) => {
                    return Err(StreamerError {
                        code: "nvst_rtsp_failed",
                        message: error.to_string(),
                    });
                }
            }
        }
    }
}

fn run_nvst(
    session_id: &str,
    context: &mut Value,
    stdin: &mut ChildStdin,
    child: &mut Child,
    child_rx: &Receiver<Value>,
    control: &Receiver<WorkerCommand>,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    let endpoint = context["session"]["rtspsEndpoints"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .find(|value| value.starts_with("rtsps://") || value.starts_with("rtsp://"))
        .ok_or_else(|| StreamerError {
            code: "missing_rtsps_endpoint",
            message: "CloudMatch did not provide an RTSPS endpoint for NVST".to_owned(),
        })?;
    {
        let mut snapshot = state.lock().expect("streamer state poisoned");
        snapshot.status = "negotiating".to_owned();
        snapshot.message = "Negotiating classic NVST media transport…".to_owned();
    }
    write_child(stdin, &json!({"id":"nvst-bind","type":"nvst-bind"}))?;
    let binding = wait_for_child(child_rx, "nvst-bind", CHILD_START_TIMEOUT, state)?;
    if binding["type"] != "nvst-bound" {
        return Err(child_error(
            &binding,
            "Native NVST socket reservation failed",
        ));
    }
    let client_port = binding["port"]
        .as_u64()
        .and_then(|value| u16::try_from(value).ok())
        .filter(|value| *value > 0)
        .ok_or_else(|| internal("Native NVST reservation returned no port"))?;
    let mjolnir_port = binding["mjolnirPort"]
        .as_u64()
        .and_then(|value| u16::try_from(value).ok());
    let local_address = binding["localAddress"].as_str().unwrap_or("0.0.0.0");
    let local_ufrag = required_child_string(&binding, "iceUsernameFragment")?;
    let local_password = required_child_string(&binding, "icePassword")?;
    let local_fingerprint = required_child_string(&binding, "dtlsFingerprint")?;
    ensure_child_running(child)?;

    let (mut rtsp, rtsp_target) = RtspClient::connect(endpoint, session_id)?;
    let host = rtsp_target
        .strip_prefix("rtsps://")
        .or_else(|| rtsp_target.strip_prefix("rtsp://"))
        .unwrap_or(&rtsp_target)
        .to_owned();
    let common = [
        ("X-GS-Version", "14.2".to_owned()),
        ("Host", host),
        ("x-nv-sessionid", session_id.to_owned()),
    ];
    let options = rtsp.request("OPTIONS", &rtsp_target, &common, "")?;
    ensure_rtsp_ok("OPTIONS", &options)?;
    let mut describe_headers = common.to_vec();
    describe_headers.push(("Accept", "application/sdp".to_owned()));
    describe_headers.push(("x-nv-abtesting", "2".to_owned()));
    let describe = rtsp.request("DESCRIBE", &rtsp_target, &describe_headers, "")?;
    ensure_rtsp_ok("DESCRIBE", &describe)?;
    let rtsp_session = header_value(&describe, "session")
        .and_then(|value| value.split(';').next())
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| StreamerError {
            code: "nvst_rtsp_failed",
            message: "DESCRIBE did not include a session".to_owned(),
        })?
        .to_owned();
    let video_control = media_control(&describe.body, "video").ok_or_else(|| StreamerError {
        code: "nvst_rtsp_failed",
        message: "DESCRIBE did not include a video control stream".to_owned(),
    })?;
    let video_setup = if video_control
        .to_ascii_lowercase()
        .starts_with("streamid=video/")
        && video_control.matches('/').count() == 1
    {
        format!("{video_control}/0")
    } else {
        video_control.clone()
    };
    let video_uri = resolve_rtsp_uri(&rtsp_target, &video_setup);
    let ping_version = sdp_attribute(&describe.body, "general.pingVersion")
        .and_then(|value| value.parse::<u8>().ok())
        .unwrap_or(6);
    let remote_ufrag = sdp_attribute(&describe.body, "general.iceUserNameFragmentV2")
        .or_else(|| sdp_attribute(&describe.body, "general.iceUsernameFragment"));
    let remote_password = sdp_attribute(&describe.body, "general.icePasswordV2")
        .or_else(|| sdp_attribute(&describe.body, "general.iceUsernamePwd"));
    let remote_fingerprint = sdp_attribute(&describe.body, "general.dtlsFingerprintV2")
        .or_else(|| sdp_attribute(&describe.body, "general.dtlsFingerprint"));
    let disable_play = sdp_attribute(&describe.body, "general.disablePlay");
    let native_bundle = sdp_attribute(&describe.body, "general.nativeRtcOnBundlePort");
    if native_bundle.as_deref() != Some("1") {
        return Err(StreamerError {
            code: "nvst_legacy_transport_unsupported",
            message: "This streaming seat requires the retired multi-socket NVST transport; choose WebRTC or another region"
                .to_owned(),
        });
    }
    let rtcp_on_sctp = sdp_attribute(&describe.body, "general.rtcpOnSctp").as_deref() == Some("1");
    let mut setup_headers = common.to_vec();
    setup_headers.push(("Session", rtsp_session.clone()));
    setup_headers.push(("x-nv-ping", ping_version.to_string()));
    setup_headers.push(("Transport", String::new()));
    let setup = rtsp.request("SETUP", &video_uri, &setup_headers, "")?;
    ensure_rtsp_ok("SETUP", &setup)?;
    let transport = header_value(&setup, "transport").unwrap_or_default();
    let (video_peer_ip, video_peer_port) =
        parse_video_peer(transport).ok_or_else(|| StreamerError {
            code: "nvst_rtsp_failed",
            message: "SETUP did not return the NVST video peer".to_owned(),
        })?;
    let ping_payload = header_value(&setup, "x-nv-ping-payload")
        .unwrap_or("PING")
        .to_owned();
    let effective_ping_version = header_value(&setup, "x-nv-ping")
        .and_then(|value| value.parse().ok())
        .unwrap_or(ping_version);
    let remote_ufrag = if effective_ping_version == 6 {
        increment_hex(&ping_payload)
            .or_else(|| remote_ufrag.clone())
            .unwrap_or(ping_payload.clone())
    } else {
        ping_payload.clone()
    };
    let remote_password = remote_password.ok_or_else(|| StreamerError {
        code: "nvst_rtsp_failed",
        message: "DESCRIBE did not return NVST ICE credentials".to_owned(),
    })?;
    let (aes_key, key_id) = runtime_key(&describe.body).unwrap_or_else(random_runtime_key);
    let salt = format!("{key_id:024X}");
    let codec = context["settings"]["codec"]
        .as_str()
        .unwrap_or("H264")
        .to_ascii_uppercase();
    let mut handoff = json!({
        "clientUdpPort":client_port,"packetSize":1280,"mjolnirUdpPort":mjolnir_port,
        "videoPeerIp":video_peer_ip,"videoPeerPort":video_peer_port,
        "srtpAesKeyHex":aes_key,"srtpKeyId":key_id,"srtpSaltHex":salt,"srtpProfile":"AEAD_AES_256_GCM_8",
        "pingPayload":ping_payload,"pingVersion":effective_ping_version,
        "localIceUsernameFragment":local_ufrag,"localIcePassword":local_password,
        "remoteIceUsernameFragment":remote_ufrag,"remoteIcePassword":remote_password,
        "localDtlsFingerprint":local_fingerprint,"remoteDtlsFingerprint":remote_fingerprint,
        "rtcpOnSctp":rtcp_on_sctp,"codec":codec,
        "audioTrack":{"payloadType":111,"codec":"opus","clockRateHz":48000,"channels":2,"mid":"0"},"timeoutMs":60000
    });
    if let Some(media) = context["session"]["mediaConnectionInfo"].as_object()
        && let (Some(ip), Some(port)) = (
            media.get("ip").and_then(Value::as_str),
            media.get("port").and_then(Value::as_u64),
        )
    {
        handoff["bundlePeerIp"] = json!(ip);
        handoff["bundlePeerPort"] = json!(port);
    }
    context["nvstVideo"] = handoff;
    start_child(stdin, child_rx, context, state)?;
    write_surface(stdin, &context["surface"])?;
    let announce_body = build_nvst_announce(
        context,
        NvstAnnounceParams {
            key: &aes_key,
            key_id,
            port: client_port,
            address: local_address,
            ufrag: local_ufrag,
            password: local_password,
            fingerprint: local_fingerprint,
            video_port: video_peer_port,
            rtcp_on_sctp,
        },
    );
    let mut announce_headers = common.to_vec();
    announce_headers.push(("Session", rtsp_session.clone()));
    announce_headers.push(("Content-Type", "application/sdp".to_owned()));
    let announce = rtsp.request("ANNOUNCE", &rtsp_target, &announce_headers, &announce_body)?;
    ensure_rtsp_ok("ANNOUNCE", &announce)?;
    if disable_play.as_deref() == Some("0") {
        let mut play_headers = common.to_vec();
        play_headers.push(("Session", rtsp_session.clone()));
        let play = rtsp.request("PLAY", &rtsp_target, &play_headers, "")?;
        if play.status != 200 && play.status != 455 {
            return Err(StreamerError {
                code: "nvst_rtsp_failed",
                message: format!("PLAY failed: {} {}", play.status, play.status_text),
            });
        }
    }
    {
        let mut snapshot = state.lock().expect("streamer state poisoned");
        snapshot.transport = Some("nvst".to_owned());
        snapshot.status = "streaming".to_owned();
        snapshot.message = "Classic NVST media transport is active".to_owned();
    }
    let mut last_ping = Instant::now();
    loop {
        if let Ok(command) = control.try_recv() {
            match command {
                WorkerCommand::Stop(reason) => {
                    let _ = write_child(stdin, &json!({"id":"stop","type":"stop","reason":reason}));
                    let mut teardown = common.to_vec();
                    teardown.push(("Session", rtsp_session.clone()));
                    let _ = rtsp.request("TEARDOWN", &rtsp_target, &teardown, "");
                    return Ok(());
                }
                WorkerCommand::InputPaused(paused) => {
                    write_child(
                        stdin,
                        &json!({"id":"input-paused","type":"input-paused","paused":paused}),
                    )?;
                }
                WorkerCommand::Control(command_type) => {
                    write_child(stdin, &json!({"id":"runtime-control","type":command_type}))?;
                }
                WorkerCommand::Surface(surface) => write_surface(stdin, &surface)?,
                WorkerCommand::Recording {
                    enabled,
                    output_path,
                    reply,
                } => {
                    let result =
                        execute_recording(stdin, child_rx, state, enabled, output_path.as_deref());
                    let _ = reply.send(result);
                }
            }
        }
        if let Some(status) = child
            .try_wait()
            .map_err(|error| internal(error.to_string()))?
        {
            return Err(StreamerError {
                code: "streamer_exited",
                message: format!("Native streamer exited unexpectedly ({status})"),
            });
        }
        while let Ok(message) = child_rx.try_recv() {
            handle_nvst_child_event(&message, state)?;
        }
        if last_ping.elapsed() >= Duration::from_secs(2) {
            let _ = rtsp.socket.send(Message::Ping(Vec::new().into()));
            last_ping = Instant::now();
        }
        thread::sleep(Duration::from_millis(20));
    }
}

fn ensure_rtsp_ok(step: &str, response: &RtspResponse) -> Result<(), StreamerError> {
    if response.status == 200 {
        Ok(())
    } else {
        Err(StreamerError {
            code: "nvst_rtsp_failed",
            message: format!(
                "{step} failed: {} {}",
                response.status, response.status_text
            ),
        })
    }
}
fn header_value<'a>(response: &'a RtspResponse, name: &str) -> Option<&'a str> {
    response
        .headers
        .get(&name.to_ascii_lowercase())
        .map(String::as_str)
}
fn required_child_string<'a>(value: &'a Value, key: &str) -> Result<&'a str, StreamerError> {
    value[key]
        .as_str()
        .filter(|value| !value.is_empty())
        .ok_or_else(|| internal(format!("Native NVST binding omitted {key}")))
}

fn take_rtsp_response(
    buffer: &mut String,
    expected_cseq: u64,
) -> Result<Option<RtspResponse>, StreamerError> {
    let Some(header_end) = buffer.find("\r\n\r\n").or_else(|| buffer.find("\n\n")) else {
        return Ok(None);
    };
    let separator = if buffer[header_end..].starts_with("\r\n\r\n") {
        4
    } else {
        2
    };
    let header_text = &buffer[..header_end];
    let content_length = header_text
        .lines()
        .find_map(|line| {
            line.split_once(':')
                .filter(|(name, _)| name.eq_ignore_ascii_case("content-length"))
                .and_then(|(_, value)| value.trim().parse::<usize>().ok())
        })
        .unwrap_or(0);
    let total = header_end + separator + content_length;
    if buffer.len() < total {
        return Ok(None);
    }
    let raw = buffer[..total].to_owned();
    buffer.drain(..total);
    let (head, body) = raw.split_at(header_end + separator);
    let mut lines = head.lines();
    let status_line = lines.next().unwrap_or_default();
    let mut parts = status_line.splitn(3, ' ');
    let _ = parts.next();
    let status = parts
        .next()
        .and_then(|value| value.parse().ok())
        .ok_or_else(|| internal("Invalid RTSPS status line"))?;
    let status_text = parts.next().unwrap_or_default().trim().to_owned();
    let mut headers = HashMap::new();
    for line in lines {
        if let Some((name, value)) = line.split_once(':') {
            headers.insert(name.trim().to_ascii_lowercase(), value.trim().to_owned());
        }
    }
    if headers
        .get("cseq")
        .and_then(|value| value.parse::<u64>().ok())
        != Some(expected_cseq)
    {
        return Err(internal("RTSPS response CSeq mismatch"));
    }
    Ok(Some(RtspResponse {
        status,
        status_text,
        headers,
        body: body.to_owned(),
    }))
}

fn trusted_nvst_host(host: &str) -> bool {
    let host = host.trim_end_matches('.').to_ascii_lowercase();
    if host == "nvidiagrid.net" || host.ends_with(".nvidiagrid.net") {
        return true;
    }
    host.parse::<IpAddr>().is_ok_and(|ip| match ip {
        IpAddr::V4(ip) => {
            !ip.is_private() && !ip.is_loopback() && !ip.is_link_local() && !ip.is_unspecified()
        }
        IpAddr::V6(ip) => !ip.is_loopback() && !ip.is_unicast_link_local() && !ip.is_unspecified(),
    })
}
fn media_control(sdp: &str, kind: &str) -> Option<String> {
    let mut current = "";
    for line in sdp.lines().map(str::trim) {
        if let Some(media) = line.strip_prefix("m=") {
            current = media.split_whitespace().next().unwrap_or("");
        } else if current.eq_ignore_ascii_case(kind)
            && let Some(value) = line.strip_prefix("a=control:")
            && value != "*"
            && !value.is_empty()
        {
            return Some(value.to_owned());
        }
    }
    None
}
fn sdp_attribute(sdp: &str, name: &str) -> Option<String> {
    let candidates = [
        format!("a=x-nv-{name}:").to_ascii_lowercase(),
        format!("a={name}:").to_ascii_lowercase(),
    ];
    sdp.lines().map(str::trim).find_map(|line| {
        let lower = line.to_ascii_lowercase();
        candidates.iter().find_map(|prefix| {
            lower.strip_prefix(prefix).and_then(|_| {
                line.get(prefix.len()..)
                    .map(str::trim)
                    .filter(|value| !value.is_empty())
                    .map(ToOwned::to_owned)
            })
        })
    })
}
fn resolve_rtsp_uri(base: &str, control: &str) -> String {
    if control.starts_with("rtsps://") || control.starts_with("rtsp://") {
        control.to_owned()
    } else {
        format!(
            "{}/{}",
            base.trim_end_matches('/'),
            control.trim_start_matches('/')
        )
    }
}
fn parse_video_peer(transport: &str) -> Option<(String, u16)> {
    let mut ip = None;
    let mut port = None;
    for part in transport.split([';', ',']) {
        let Some((name, value)) = part.trim().split_once('=') else {
            continue;
        };
        if name.eq_ignore_ascii_case("source") {
            ip = Some(value.trim().to_owned())
        } else if name.eq_ignore_ascii_case("X-GS-ServerPort") {
            port = value.trim().split('-').next()?.parse().ok()
        }
    }
    Some((ip?, port?))
}
fn increment_hex(value: &str) -> Option<String> {
    if value.is_empty() || !value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return None;
    }
    let mut bytes = value.as_bytes().to_vec();
    let mut carry = true;
    for byte in bytes.iter_mut().rev() {
        if !carry {
            break;
        }
        let digit = (*byte as char).to_digit(16)?;
        if digit == 15 {
            *byte = b'0'
        } else {
            *byte = char::from_digit(digit + 1, 16)?.to_ascii_lowercase() as u8;
            carry = false
        }
    }
    if carry {
        bytes.insert(0, b'1')
    }
    String::from_utf8(bytes).ok()
}
fn runtime_key(sdp: &str) -> Option<(String, u32)> {
    let key = sdp_attribute(sdp, "runtime.encryptionKey")?;
    if key.len() != 64 || !key.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return None;
    }
    let raw = sdp_attribute(sdp, "runtime.encryptionKeyId")?
        .parse::<i64>()
        .ok()?;
    Some((key.to_ascii_uppercase(), raw as u32))
}
fn random_runtime_key() -> (String, u32) {
    let mut key = [0_u8; 32];
    rand::rng().fill_bytes(&mut key);
    let mut id = [0_u8; 4];
    rand::rng().fill_bytes(&mut id);
    (
        key.iter().map(|byte| format!("{byte:02X}")).collect(),
        u32::from_be_bytes(id),
    )
}

struct NvstAnnounceParams<'a> {
    key: &'a str,
    key_id: u32,
    port: u16,
    address: &'a str,
    ufrag: &'a str,
    password: &'a str,
    fingerprint: &'a str,
    video_port: u16,
    rtcp_on_sctp: bool,
}

fn build_nvst_announce(context: &Value, params: NvstAnnounceParams<'_>) -> String {
    let (width, height) = context_resolution(context);
    let fps = context["settings"]["fps"]
        .as_u64()
        .unwrap_or(60)
        .clamp(30, 240);
    let bitrate = context["settings"]["maxBitrateMbps"]
        .as_u64()
        .unwrap_or(75)
        .clamp(1, 150)
        * 1000;
    let codec = context["settings"]["codec"].as_str().unwrap_or("H264");
    let format = if codec.eq_ignore_ascii_case("AV1") {
        2
    } else if codec.eq_ignore_ascii_case("H265") || codec.eq_ignore_ascii_case("HEVC") {
        1
    } else {
        0
    };
    let lines = vec![
        "v=0".to_owned(),
        "o=unknown 0 14 IN IPv4 127.0.0.1".to_owned(),
        "s=NVIDIA Streaming Client".to_owned(),
        format!("a=x-nv-video[0].clientViewportWd:{width}"),
        format!("a=x-nv-video[0].clientViewportHt:{height}"),
        format!("a=x-nv-video[0].maxFPS:{fps}"),
        "a=x-nv-video[0].packetSize:1280".to_owned(),
        "a=x-nv-video[0].enableRtpNack:1".to_owned(),
        format!("a=x-nv-video[0].initialBitrateKbps:{bitrate}"),
        format!("a=x-nv-vqos[0].bitStreamFormat:{format}"),
        format!("a=x-nv-vqos[0].bw.maximumBitrateKbps:{bitrate}"),
        "a=x-nv-vqos[0].fec.enable:1".to_owned(),
        "a=x-nv-vqos[0].fec.repairPercent:20".to_owned(),
        "a=x-nv-general.rtspWebSocketPerConnection:1".to_owned(),
        "a=x-nv-general.clientPorts.video:0".to_owned(),
        "a=x-nv-general.clientPorts.audio:0".to_owned(),
        "a=x-nv-general.clientPorts.mic:0".to_owned(),
        "a=x-nv-general.clientPorts.control:0".to_owned(),
        "a=x-nv-general.clientPorts.bundle:0".to_owned(),
        "a=x-nv-general.clientPorts.session:0".to_owned(),
        format!("a=x-nv-general.clientPorts.localAddress:{}", params.address),
        "a=x-nv-general.clientPorts.useReserved:1".to_owned(),
        "a=x-nv-general.clientPorts.fallbackDynamic:1".to_owned(),
        format!("a=x-nv-general.clientBundlePort:{}", params.port),
        "a=x-nv-general.nativeRtcOnBundlePort:1".to_owned(),
        "a=x-nv-general.rtcVideoOnNativeBundle:0".to_owned(),
        "a=x-nv-general.rtcAudioOnNativeBundle:1".to_owned(),
        "a=x-nv-general.rtcMicOnNativeBundle:1".to_owned(),
        "a=x-nv-general.rtcDataChannelOnNativeBundle:1".to_owned(),
        "a=x-nv-general.enableUnifiedSocket:0".to_owned(),
        format!(
            "a=x-nv-general.rtcpOnSctp:{}",
            if params.rtcp_on_sctp { 1 } else { 0 }
        ),
        format!("a=x-nv-general.iceUserNameFragmentV2:{}", params.ufrag),
        format!("a=x-nv-general.icePasswordV2:{}", params.password),
        format!("a=x-nv-general.dtlsFingerprintV2:{}", params.fingerprint),
        "a=x-nv-runtime.videoSrtp:1".to_owned(),
        format!("a=x-nv-runtime.encryptionKey:{}", params.key),
        format!("a=x-nv-runtime.encryptionKeyId:{}", params.key_id),
        "a=x-nv-runtime.audioSrtp:0".to_owned(),
        "a=x-nv-runtime.micSrtp:0".to_owned(),
        "a=ice-options:trickle".to_owned(),
        format!("a=ice-ufrag:{}", params.ufrag),
        format!("a=ice-pwd:{}", params.password),
        format!("a=fingerprint:sha-256 {}", params.fingerprint),
        "a=setup:actpass".to_owned(),
        format!(
            "a=candidate:1 1 udp 2122260223 {} {} typ host",
            params.address, params.port
        ),
        "t=0 0".to_owned(),
        format!("m=video {}", params.video_port),
        "c=IN IP4 0.0.0.0".to_owned(),
        "i=DeviceString, DeviceName".to_owned(),
        String::new(),
    ];
    lines.join("\r\n")
}

fn handle_nvst_child_event(
    message: &Value,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    apply_child_telemetry(message, state);
    match message["type"].as_str().unwrap_or_default() {
        "status" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            if let Some(value) = message["status"].as_str() {
                snapshot.status = value.to_owned()
            }
            if let Some(value) = message["message"].as_str() {
                snapshot.message = value.to_owned()
            }
            Ok(())
        }
        "error" => Err(StreamerError {
            code: "native_stream_error",
            message: message["message"]
                .as_str()
                .unwrap_or("Native NVST media error")
                .to_owned(),
        }),
        "overlay-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.overlay_request_generation =
                snapshot.overlay_request_generation.wrapping_add(1);
            Ok(())
        }
        "screenshot-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.screenshot_request_generation =
                snapshot.screenshot_request_generation.wrapping_add(1);
            Ok(())
        }
        "recording-toggle-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.recording_toggle_request_generation =
                snapshot.recording_toggle_request_generation.wrapping_add(1);
            Ok(())
        }
        "shortcut-action" => {
            let action = message["action"]
                .as_str()
                .filter(|action| {
                    matches!(
                        *action,
                        "stop-stream"
                            | "toggle-anti-afk"
                            | "toggle-microphone"
                            | "screenshot"
                            | "toggle-recording"
                    )
                })
                .ok_or_else(|| StreamerError {
                    code: "streamer_protocol_error",
                    message: "Native streamer emitted an invalid shortcut action".to_owned(),
                })?;
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.shortcut_action_generation =
                snapshot.shortcut_action_generation.wrapping_add(1);
            snapshot.shortcut_action = Some(action.to_owned());
            Ok(())
        }
        "microphone-state" => {
            apply_microphone_state(message, state);
            Ok(())
        }
        _ => Ok(()),
    }
}

fn run_signaling(
    config: SignalingContext<'_>,
    stdin: &mut ChildStdin,
    child: &mut Child,
    child_rx: &Receiver<Value>,
    control: &Receiver<WorkerCommand>,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    ensure_child_running(child)?;
    let peer_name = format!("peer-{}", random_u64() % 10_000_000_000);
    let sign_in = sign_in_url(config.signaling_url, config.session_id, &peer_name)?;
    let mut request = sign_in
        .as_str()
        .into_client_request()
        .map_err(|error| StreamerError {
            code: "signaling_connect_failed",
            message: error.to_string(),
        })?;
    request.headers_mut().insert(
        SEC_WEBSOCKET_PROTOCOL,
        HeaderValue::from_str(&format!("x-nv-sessionid.{}", config.session_id))
            .map_err(|_| invalid("Session ID cannot be used as a signaling protocol"))?,
    );
    request.headers_mut().insert(
        ORIGIN,
        HeaderValue::from_static("https://play.geforcenow.com"),
    );
    request.headers_mut().insert(
        USER_AGENT,
        HeaderValue::from_static("Mozilla/5.0 OpenNOW/0.1 GFN-PC/2.0.87.131"),
    );
    let (mut websocket, _) = connect(request).map_err(|error| StreamerError {
        code: "signaling_connect_failed",
        message: format!("Could not connect to GeForce NOW signaling: {error}"),
    })?;
    set_read_timeout(&mut websocket, Duration::from_millis(100));
    let mut peer_id = 0_i64;
    let mut remote_peer_id = 1_i64;
    let mut ack_id = 1_u64;
    send_ws_json(
        &mut websocket,
        &json!({"ackid":ack_id,"peer_info":{
            "browser":"Chrome","browserVersion":"131","connected":true,
            "id":peer_id,"name":peer_name,"peerRole":0,"resolution":"1920x1080","version":2
        }}),
    )?;
    ack_id += 1;
    {
        let mut snapshot = state.lock().expect("streamer state poisoned");
        snapshot.status = "negotiating".to_owned();
        snapshot.message = "Negotiating ICE and encrypted media…".to_owned();
    }
    let mut last_heartbeat = Instant::now();
    let mut offer_counter = 0_u64;

    loop {
        if let Ok(command) = control.try_recv() {
            match command {
                WorkerCommand::Stop(reason) => {
                    let _ = write_child(stdin, &json!({"id":"stop","type":"stop","reason":reason}));
                    let _ = websocket.close(None);
                    return Ok(());
                }
                WorkerCommand::InputPaused(paused) => {
                    write_child(
                        stdin,
                        &json!({"id":"input-paused","type":"input-paused","paused":paused}),
                    )?;
                }
                WorkerCommand::Control(command_type) => {
                    write_child(stdin, &json!({"id":"runtime-control","type":command_type}))?;
                }
                WorkerCommand::Surface(surface) => write_surface(stdin, &surface)?,
                WorkerCommand::Recording {
                    enabled,
                    output_path,
                    reply,
                } => {
                    let result =
                        execute_recording(stdin, child_rx, state, enabled, output_path.as_deref());
                    let _ = reply.send(result);
                }
            }
        }
        if let Some(status) = child
            .try_wait()
            .map_err(|error| internal(error.to_string()))?
        {
            return Err(StreamerError {
                code: "streamer_exited",
                message: format!("Native streamer exited unexpectedly ({status})"),
            });
        }
        while let Ok(message) = child_rx.try_recv() {
            handle_child_event(
                &message,
                &mut websocket,
                &mut peer_id,
                remote_peer_id,
                &mut ack_id,
                state,
            )?;
        }
        if last_heartbeat.elapsed() >= HEARTBEAT_INTERVAL {
            send_ws_json(&mut websocket, &json!({"hb":1}))?;
            last_heartbeat = Instant::now();
        }
        match websocket.read() {
            Ok(Message::Text(text)) => {
                let payload: Value = match serde_json::from_str(text.as_str()) {
                    Ok(value) => value,
                    Err(_) => continue,
                };
                if let Some(info) = payload["peer_info"].as_object() {
                    if info.get("name").and_then(Value::as_str) == Some(peer_name.as_str()) {
                        peer_id = info.get("id").and_then(Value::as_i64).unwrap_or(peer_id);
                    }
                }
                if let Some(incoming_ack) = payload["ackid"].as_u64() {
                    let is_ours = payload["peer_info"]["id"].as_i64() == Some(peer_id);
                    if !is_ours {
                        send_ws_json(&mut websocket, &json!({"ack":incoming_ack}))?;
                    }
                }
                if payload["hb"].as_i64().is_some() {
                    send_ws_json(&mut websocket, &json!({"hb":1}))?;
                    continue;
                }
                if payload["error"].as_str() == Some("peerRemoved") {
                    return Err(StreamerError {
                        code: "signaling_disconnected",
                        message: "The remote GeForce NOW peer ended the session".to_owned(),
                    });
                }
                let Some(peer_message) = payload["peer_msg"]["msg"].as_str() else {
                    continue;
                };
                remote_peer_id = payload["peer_msg"]["from"]
                    .as_i64()
                    .unwrap_or(remote_peer_id);
                if peer_message.trim() == "BYE" {
                    return Err(StreamerError {
                        code: "signaling_disconnected",
                        message: "The remote GeForce NOW peer closed the stream".to_owned(),
                    });
                }
                let Ok(peer_payload) = serde_json::from_str::<Value>(peer_message) else {
                    continue;
                };
                if peer_payload["type"].as_str() == Some("offer")
                    && let Some(sdp) = peer_payload["sdp"].as_str()
                {
                    offer_counter += 1;
                    write_child(
                        stdin,
                        &json!({
                            "id":format!("offer-{offer_counter}"),"type":"offer",
                            "sdp":sdp,"context":config.streamer_context
                        }),
                    )?;
                } else if let Some(candidate) = peer_payload["candidate"].as_str() {
                    write_child(
                        stdin,
                        &json!({
                            "id":format!("remote-ice-{}",random_u64()),"type":"remote-ice",
                            "candidate":{
                                "candidate":candidate,
                                "sdpMid":peer_payload["sdpMid"],
                                "sdpMLineIndex":peer_payload["sdpMLineIndex"],
                                "usernameFragment":peer_payload["usernameFragment"]
                            }
                        }),
                    )?;
                }
            }
            Ok(Message::Close(_)) => {
                return Err(StreamerError {
                    code: "signaling_disconnected",
                    message: "GeForce NOW signaling closed".to_owned(),
                });
            }
            Ok(Message::Ping(payload)) => {
                websocket
                    .send(Message::Pong(payload))
                    .map_err(signaling_error)?;
            }
            Ok(_) => {}
            Err(tungstenite::Error::Io(error))
                if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {}
            Err(tungstenite::Error::ConnectionClosed | tungstenite::Error::AlreadyClosed) => {
                return Err(StreamerError {
                    code: "signaling_disconnected",
                    message: "GeForce NOW signaling disconnected".to_owned(),
                });
            }
            Err(error) => return Err(signaling_error(error)),
        }
    }
}

fn handle_child_event(
    message: &Value,
    websocket: &mut WebSocket<MaybeTlsStream<TcpStream>>,
    peer_id: &mut i64,
    remote_peer_id: i64,
    ack_id: &mut u64,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<(), StreamerError> {
    apply_child_telemetry(message, state);
    match message["type"].as_str().unwrap_or_default() {
        "answer" => {
            let Some(sdp) = message["answer"]["sdp"].as_str() else {
                return Err(internal("Native streamer returned an empty SDP answer"));
            };
            send_peer_message(
                websocket,
                *peer_id,
                remote_peer_id,
                ack_id,
                &json!({"type":"answer","sdp":sdp}),
            )?;
        }
        "local-ice" => {
            let candidate = &message["candidate"];
            if !candidate["candidate"]
                .as_str()
                .is_some_and(|value| value.to_ascii_lowercase().contains(" tcp "))
            {
                send_peer_message(websocket, *peer_id, remote_peer_id, ack_id, candidate)?;
            }
        }
        "status" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            if let Some(status) = message["status"].as_str() {
                snapshot.status = status.to_owned();
            }
            if let Some(text) = message["message"].as_str() {
                snapshot.message = text.to_owned();
            }
        }
        "overlay-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.overlay_request_generation =
                snapshot.overlay_request_generation.wrapping_add(1);
        }
        "screenshot-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.screenshot_request_generation =
                snapshot.screenshot_request_generation.wrapping_add(1);
        }
        "recording-toggle-request" => {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.recording_toggle_request_generation =
                snapshot.recording_toggle_request_generation.wrapping_add(1);
        }
        "shortcut-action" => {
            if let Some(action) = message["action"].as_str().filter(|action| {
                matches!(
                    *action,
                    "stop-stream"
                        | "toggle-anti-afk"
                        | "toggle-microphone"
                        | "screenshot"
                        | "toggle-recording"
                )
            }) {
                let mut snapshot = state.lock().expect("streamer state poisoned");
                snapshot.shortcut_action_generation =
                    snapshot.shortcut_action_generation.wrapping_add(1);
                snapshot.shortcut_action = Some(action.to_owned());
            }
        }
        "microphone-state" => apply_microphone_state(message, state),
        "error" => {
            return Err(StreamerError {
                code: "native_stream_error",
                message: message["message"]
                    .as_str()
                    .unwrap_or("Native streamer reported a media error")
                    .to_owned(),
            });
        }
        _ => {}
    }
    Ok(())
}

fn apply_microphone_state(message: &Value, state: &Arc<Mutex<Snapshot>>) {
    let mut snapshot = state.lock().expect("streamer state poisoned");
    snapshot.microphone_state = message["state"]
        .as_str()
        .unwrap_or("unavailable")
        .to_owned();
    snapshot.microphone_enabled = message["enabled"].as_bool().unwrap_or(false);
    snapshot.microphone_message = message["message"].as_str().map(ToOwned::to_owned);
}

fn apply_child_telemetry(message: &Value, state: &Arc<Mutex<Snapshot>>) {
    let mut snapshot = state.lock().expect("streamer state poisoned");
    match message["type"].as_str().unwrap_or_default() {
        "input-ready" => {
            snapshot.input_ready = true;
            snapshot.input_unavailable_reason = None;
        }
        "input-unavailable" => {
            snapshot.input_ready = false;
            snapshot.input_unavailable_reason = message["reason"].as_str().map(ToOwned::to_owned);
        }
        _ => {}
    }

    match message["event"].as_str().unwrap_or_default() {
        "first-frame" => {
            if snapshot.first_frame_latency_ms.is_none() {
                snapshot.first_frame_latency_ms = snapshot
                    .started_at
                    .map(|started| elapsed_millis(started.elapsed()));
            }
            snapshot.media_backend = message["backend"].as_str().map(ToOwned::to_owned);
        }
        "backend-fallback" => {
            snapshot.backend_fallback_count = snapshot.backend_fallback_count.saturating_add(1);
        }
        "decoder-error" => {
            snapshot.decoder_error_count = snapshot.decoder_error_count.saturating_add(1);
        }
        "output-error" => {
            snapshot.output_error_count = snapshot.output_error_count.saturating_add(1);
        }
        "device-state" => {
            if message["recovered"].as_bool().unwrap_or(false) {
                snapshot.device_recovery_count = snapshot.device_recovery_count.saturating_add(1);
            } else {
                snapshot.device_loss_count = snapshot.device_loss_count.saturating_add(1);
            }
        }
        "queue-dropped" => {
            snapshot.queue_drop_count = snapshot
                .queue_drop_count
                .saturating_add(message["count"].as_u64().unwrap_or_default());
        }
        _ => {}
    }
}

fn send_peer_message(
    websocket: &mut WebSocket<MaybeTlsStream<TcpStream>>,
    peer_id: i64,
    remote_peer_id: i64,
    ack_id: &mut u64,
    payload: &Value,
) -> Result<(), StreamerError> {
    send_ws_json(
        websocket,
        &json!({
            "peer_msg":{"from":peer_id,"to":remote_peer_id,"msg":payload.to_string()},
            "ackid":*ack_id
        }),
    )?;
    *ack_id += 1;
    Ok(())
}

fn send_ws_json(
    websocket: &mut WebSocket<MaybeTlsStream<TcpStream>>,
    value: &Value,
) -> Result<(), StreamerError> {
    websocket
        .send(Message::Text(value.to_string().into()))
        .map_err(signaling_error)
}

fn set_read_timeout(websocket: &mut WebSocket<MaybeTlsStream<TcpStream>>, timeout: Duration) {
    let result = match websocket.get_mut() {
        MaybeTlsStream::Plain(stream) => stream.set_read_timeout(Some(timeout)),
        MaybeTlsStream::Rustls(stream) => stream.sock.set_read_timeout(Some(timeout)),
        _ => Ok(()),
    };
    let _ = result;
}

fn sign_in_url(raw: &str, session_id: &str, peer_name: &str) -> Result<Url, StreamerError> {
    let mut url = Url::parse(raw).map_err(|_| invalid("Invalid signaling URL"))?;
    let host = url
        .host_str()
        .unwrap_or_default()
        .trim_end_matches('.')
        .to_ascii_lowercase();
    if url.scheme() != "wss" || !(host == "nvidiagrid.net" || host.ends_with(".nvidiagrid.net")) {
        return Err(invalid("Untrusted GeForce NOW signaling URL"));
    }
    let path = format!("{}/sign_in", url.path().trim_end_matches('/'));
    url.set_path(&path);
    url.query_pairs_mut()
        .append_pair("peer_id", peer_name)
        .append_pair("version", "2")
        .append_pair("peer_role", "1")
        .append_pair("pairing_id", session_id);
    Ok(url)
}

fn streamer_context(mut session: Value, settings: &Value) -> Value {
    let mut normalized = settings.clone();
    let negotiated_codec = session["negotiatedStreamProfile"]["codec"].as_str();
    let requested_codec = normalized["codec"].as_str().unwrap_or("auto").to_owned();
    let codec = negotiated_codec.map(ToOwned::to_owned).unwrap_or_else(|| {
        if requested_codec.eq_ignore_ascii_case("auto") {
            "H264".to_owned()
        } else {
            requested_codec
        }
    });
    normalized["codec"] = Value::String(codec.to_ascii_uppercase());
    let transport = settings["transportMode"].as_str().unwrap_or("webrtc");
    normalized["transportMode"] = Value::String(transport.to_ascii_lowercase());
    if session["negotiatedStreamProfile"].is_null() {
        session["negotiatedStreamProfile"] = json!({"codec":codec.to_ascii_uppercase()});
    }
    json!({
        "session":session,
        "settings":normalized,
        "shortcuts":{
            "toggleStats":settings["shortcutToggleStats"],
            "togglePointerLock":settings["shortcutTogglePointerLock"],
            "toggleFullscreen":settings["shortcutToggleFullscreen"],
            "stopStream":settings["shortcutStopStream"],
            "toggleAntiAfk":settings["shortcutToggleAntiAfk"],
            "toggleMicrophone":settings["shortcutToggleMicrophone"],
            "screenshot":settings["shortcutScreenshot"],
            "toggleRecording":settings["shortcutToggleRecording"]
        }
    })
}

fn context_resolution(context: &Value) -> (u64, u64) {
    context["settings"]["resolution"]
        .as_str()
        .and_then(|value| value.split_once('x'))
        .and_then(|(width, height)| Some((width.parse().ok()?, height.parse().ok()?)))
        .filter(|(width, height)| *width > 0 && *height > 0)
        .unwrap_or((1280, 720))
}

fn normalize_surface(
    raw: Option<&Value>,
    fallback_size: (u64, u64),
) -> Result<Value, StreamerError> {
    let fallback = json!({
        "rect":{"x":0,"y":0,"width":fallback_size.0,"height":fallback_size.1},
        "screenRect":{"x":0,"y":0,"width":fallback_size.0,"height":fallback_size.1},
        "visible":true,
        "deviceScaleFactor":1.0
    });
    let value = raw.unwrap_or(&fallback);
    let object = value
        .as_object()
        .ok_or_else(|| invalid("streamer surface must be an object"))?;
    let visible = object
        .get("visible")
        .and_then(Value::as_bool)
        .unwrap_or(true);
    let screen = object
        .get("screenRect")
        .and_then(Value::as_object)
        .or_else(|| object.get("rect").and_then(Value::as_object))
        .ok_or_else(|| invalid("streamer surface requires screenRect"))?;
    let width = screen
        .get("width")
        .and_then(Value::as_u64)
        .filter(|value| (64..=16_384).contains(value))
        .ok_or_else(|| invalid("streamer surface width is out of range"))?;
    let height = screen
        .get("height")
        .and_then(Value::as_u64)
        .filter(|value| (64..=16_384).contains(value))
        .ok_or_else(|| invalid("streamer surface height is out of range"))?;
    let x = screen.get("x").and_then(Value::as_i64).unwrap_or(0);
    let y = screen.get("y").and_then(Value::as_i64).unwrap_or(0);
    if !(-100_000..=100_000).contains(&x) || !(-100_000..=100_000).contains(&y) {
        return Err(invalid("streamer surface position is out of range"));
    }
    let scale = object
        .get("deviceScaleFactor")
        .and_then(Value::as_f64)
        .unwrap_or(1.0);
    if !scale.is_finite() || !(0.5..=8.0).contains(&scale) {
        return Err(invalid("streamer surface scale is out of range"));
    }
    let window_handle = object
        .get("windowHandle")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty() && value.len() <= 512);
    let mut normalized = json!({
        "rect":{"x":0,"y":0,"width":width,"height":height},
        "screenRect":{"x":x,"y":y,"width":width,"height":height},
        "visible":visible,
        "deviceScaleFactor":scale
    });
    if let Some(window_handle) = window_handle {
        normalized["windowHandle"] = Value::String(window_handle.to_owned());
    }
    Ok(normalized)
}

fn write_surface(stdin: &mut ChildStdin, surface: &Value) -> Result<(), StreamerError> {
    write_child(
        stdin,
        &json!({"id":"surface","type":"surface","surface":surface}),
    )
}

fn execute_recording(
    stdin: &mut ChildStdin,
    child_rx: &Receiver<Value>,
    state: &Arc<Mutex<Snapshot>>,
    enabled: bool,
    output_path: Option<&Path>,
) -> Result<Value, StreamerError> {
    const ID: &str = "recording-control";
    write_child(
        stdin,
        &json!({
            "id":ID,
            "type":if enabled { "recording-start" } else { "recording-stop" },
            "outputPath":output_path.map(|path| path.to_string_lossy().into_owned()),
        }),
    )?;
    let response = wait_for_child(child_rx, ID, Duration::from_secs(12), state)?;
    if response["type"] == "error" {
        return Err(StreamerError {
            code: "recording_failed",
            message: response["message"]
                .as_str()
                .unwrap_or("Native recording failed")
                .to_owned(),
        });
    }
    Ok(json!({
        "enabled":enabled,
        "path":response["path"],
        "videoPackets":response["videoPackets"],
        "audioPackets":response["audioPackets"],
        "streamerRunning":true,
    }))
}

fn spawn_stdout_reader(
    stdout: impl std::io::Read + Send + 'static,
    messages: Sender<Value>,
) -> Result<JoinHandle<()>, StreamerError> {
    thread::Builder::new()
        .name("opennow-streamer-stdout".to_owned())
        .spawn(move || {
            for line in BufReader::new(stdout).lines().map_while(Result::ok) {
                if line.len() > CHILD_MESSAGE_LIMIT {
                    break;
                }
                if let Ok(message) = serde_json::from_str(&line) {
                    let _ = messages.send(message);
                }
            }
        })
        .map_err(|error| StreamerError {
            code: "streamer_spawn_failed",
            message: error.to_string(),
        })
}

fn wait_for_child(
    receiver: &Receiver<Value>,
    id: &str,
    timeout: Duration,
    state: &Arc<Mutex<Snapshot>>,
) -> Result<Value, StreamerError> {
    let deadline = Instant::now() + timeout;
    loop {
        let remaining = deadline.saturating_duration_since(Instant::now());
        if remaining.is_zero() {
            return Err(StreamerError {
                code: "streamer_timeout",
                message: format!("Native streamer timed out waiting for {id}"),
            });
        }
        let message = match receiver.recv_timeout(remaining) {
            Ok(message) => message,
            Err(mpsc::RecvTimeoutError::Timeout) => {
                return Err(StreamerError {
                    code: "streamer_timeout",
                    message: format!("Native streamer timed out waiting for {id}"),
                });
            }
            Err(mpsc::RecvTimeoutError::Disconnected) => {
                return Err(StreamerError {
                    code: "streamer_exited",
                    message: format!(
                        "Native streamer closed its protocol output while waiting for {id}"
                    ),
                });
            }
        };
        if message["id"].as_str() == Some(id) {
            return Ok(message);
        }
        if message["type"].as_str() == Some("status") {
            let mut snapshot = state.lock().expect("streamer state poisoned");
            snapshot.status = message["status"].as_str().unwrap_or("starting").to_owned();
            snapshot.message = message["message"].as_str().unwrap_or("").to_owned();
        }
    }
}

fn write_child(stdin: &mut ChildStdin, message: &Value) -> Result<(), StreamerError> {
    serde_json::to_writer(&mut *stdin, message).map_err(|error| internal(error.to_string()))?;
    stdin.write_all(b"\n").map_err(|error| StreamerError {
        code: "streamer_io_failed",
        message: error.to_string(),
    })?;
    stdin.flush().map_err(|error| StreamerError {
        code: "streamer_io_failed",
        message: error.to_string(),
    })
}

fn wait_or_kill(child: &mut Child) {
    let deadline = Instant::now() + Duration::from_secs(2);
    while Instant::now() < deadline {
        if child.try_wait().ok().flatten().is_some() {
            return;
        }
        thread::sleep(Duration::from_millis(20));
    }
    let _ = child.kill();
    let _ = child.wait();
}

fn ensure_child_running(child: &mut Child) -> Result<(), StreamerError> {
    match child.try_wait() {
        Ok(None) => Ok(()),
        Ok(Some(status)) => Err(StreamerError {
            code: "streamer_exited",
            message: format!("Native streamer exited unexpectedly ({status})"),
        }),
        Err(error) => Err(internal(format!(
            "Could not inspect native streamer state: {error}"
        ))),
    }
}

fn resolve_executable(settings: &Value) -> Result<PathBuf, StreamerError> {
    if let Some(configured) = settings["nativeStreamerExecutablePath"]
        .as_str()
        .filter(|value| !value.trim().is_empty())
    {
        return Ok(PathBuf::from(configured));
    }
    let current = std::env::current_exe().map_err(|error| StreamerError {
        code: "streamer_not_found",
        message: error.to_string(),
    })?;
    Ok(current
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join(if cfg!(target_os = "windows") {
            "opennow-streamer.exe"
        } else {
            "opennow-streamer"
        }))
}

fn required_string<'a>(value: &'a Value, key: &str) -> Result<&'a str, StreamerError> {
    value[key]
        .as_str()
        .filter(|value| !value.is_empty())
        .ok_or_else(|| invalid(format!("Missing session {key}")))
}

fn child_error(value: &Value, fallback: &str) -> StreamerError {
    StreamerError {
        code: "native_stream_error",
        message: value["message"].as_str().unwrap_or(fallback).to_owned(),
    }
}

fn set_error(state: &Arc<Mutex<Snapshot>>, code: &str, message: String) {
    let mut snapshot = state.lock().expect("streamer state poisoned");
    snapshot.status = "error".to_owned();
    snapshot.message = message;
    snapshot.error_code = Some(code.to_owned());
    snapshot.process_id = None;
    snapshot.microphone_enabled = false;
    if snapshot.microphone_state != "disabled" {
        snapshot.microphone_state = "unavailable".to_owned();
    }
}

fn elapsed_millis(duration: Duration) -> u64 {
    u64::try_from(duration.as_millis()).unwrap_or(u64::MAX)
}

fn unix_time_millis() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
}

fn random_u64() -> u64 {
    let mut bytes = [0_u8; 8];
    rand::rng().fill_bytes(&mut bytes);
    u64::from_le_bytes(bytes)
}

fn signaling_error(error: tungstenite::Error) -> StreamerError {
    StreamerError {
        code: "signaling_error",
        message: error.to_string(),
    }
}

fn invalid(message: impl Into<String>) -> StreamerError {
    StreamerError {
        code: "invalid_params",
        message: message.into(),
    }
}

fn internal(message: impl Into<String>) -> StreamerError {
    StreamerError {
        code: "streamer_internal_error",
        message: message.into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crashed_child_is_reported_as_a_typed_streamer_failure() {
        #[cfg(target_os = "windows")]
        let mut child = Command::new("cmd")
            .args(["/C", "exit", "23"])
            .spawn()
            .expect("crash fixture");
        #[cfg(not(target_os = "windows"))]
        let mut child = Command::new("sh")
            .args(["-c", "exit 23"])
            .spawn()
            .expect("crash fixture");
        child.wait().expect("crash fixture exit");

        let failure = ensure_child_running(&mut child).expect_err("exited child must fail");
        assert_eq!(failure.code, "streamer_exited");
        assert!(failure.message.contains("23"));
    }

    #[test]
    fn closed_child_protocol_is_not_misreported_as_a_timeout() {
        let (sender, receiver) = mpsc::channel();
        drop(sender);
        let state = Arc::new(Mutex::new(Snapshot::default()));
        let failure = wait_for_child(&receiver, "hello", Duration::from_secs(1), &state)
            .expect_err("closed protocol must fail");
        assert_eq!(failure.code, "streamer_exited");
    }

    #[cfg(unix)]
    #[test]
    fn streamer_service_contains_a_crashing_child_and_publishes_error_state() {
        use std::os::unix::fs::PermissionsExt as _;

        let fixture = std::env::temp_dir().join(format!(
            "opennow-crashing-streamer-{}-{}",
            std::process::id(),
            random_u64()
        ));
        fs::write(
            &fixture,
            "#!/bin/sh\nread -r _line\nprintf '%s\\n' '{\"id\":\"hello\",\"type\":\"ready\",\"processId\":1,\"capabilities\":{\"protocolVersion\":4,\"videoBackends\":[{\"available\":true,\"codecs\":[{\"codec\":\"h264\",\"available\":true}]}]}}'\nexit 23\n",
        )
        .expect("write crash fixture");
        fs::set_permissions(&fixture, fs::Permissions::from_mode(0o700))
            .expect("make crash fixture executable");

        let service = StreamerService::new();
        service
            .start(
                &json!({
                    "session":{
                        "status":2,
                        "sessionId":"crash-fixture",
                        "rtspsEndpoints":["rtsps://seat.nvidiagrid.net:322/session"]
                    }
                }),
                &json!({
                    "nativeStreamerExecutablePath":fixture,
                    "transportMode":"nvst",
                    "codec":"H264",
                    "resolution":"1920x1080"
                }),
            )
            .expect("coordinator starts asynchronously");

        let deadline = Instant::now() + Duration::from_secs(2);
        let state = loop {
            let state = service.status();
            if state["streamer"]["status"] == "error" || Instant::now() >= deadline {
                break state;
            }
            thread::sleep(Duration::from_millis(10));
        };
        assert_eq!(state["streamer"]["status"], "error");
        assert_eq!(state["streamer"]["errorCode"], "streamer_exited");
        assert_eq!(state["streamer"]["processId"], Value::Null);

        fs::remove_file(fixture).expect("remove crash fixture");
    }

    #[test]
    fn codec_capabilities_require_an_available_backend_and_codec() {
        let capabilities = json!({
            "protocolVersion":4,
            "videoBackends":[
                {"backend":"hardware","available":true,"codecs":[
                    {"codec":"h264","available":true},
                    {"codec":"h265","available":false,"reason":"not installed"}
                ]},
                {"backend":"software","available":false,"codecs":[
                    {"codec":"av1","available":true}
                ]}
            ]
        });
        assert_eq!(available_codecs(&capabilities), vec!["h264"]);
        assert!(ensure_codec_available(&capabilities, "h264").is_ok());
        let hevc = ensure_codec_available(&capabilities, "h265").expect_err("HEVC must fail");
        assert_eq!(hevc.code, "streamer_codec_unavailable");
        assert!(hevc.message.contains("not installed"));
        assert!(ensure_codec_available(&capabilities, "av1").is_err());
    }

    #[cfg(unix)]
    #[test]
    fn detect_performs_a_protocol_handshake_and_reports_real_codecs() {
        use std::os::unix::fs::PermissionsExt as _;

        let fixture = std::env::temp_dir().join(format!(
            "opennow-capability-streamer-{}-{}",
            std::process::id(),
            random_u64()
        ));
        fs::write(
            &fixture,
            "#!/bin/sh\nread -r _hello\nprintf '%s\\n' '{\"id\":\"hello\",\"type\":\"ready\",\"processId\":1,\"capabilities\":{\"protocolVersion\":4,\"videoBackends\":[{\"backend\":\"software\",\"available\":true,\"codecs\":[{\"codec\":\"h264\",\"available\":true},{\"codec\":\"av1\",\"available\":false,\"reason\":\"not built\"}]}]}}'\nread -r _shutdown\nprintf '%s\\n' '{\"id\":\"shutdown\",\"type\":\"ok\"}'\n",
        )
        .expect("write capability fixture");
        fs::set_permissions(&fixture, fs::Permissions::from_mode(0o700))
            .expect("make capability fixture executable");

        let service = StreamerService::new();
        let detected = service
            .detect(&json!({"nativeStreamerExecutablePath":fixture}))
            .expect("capability probe");
        assert_eq!(detected["protocolVersion"], 4);
        assert_eq!(detected["availableCodecs"], json!(["h264"]));
        assert_eq!(
            detected["capabilities"]["videoBackends"][0]["backend"],
            "software"
        );

        fs::remove_file(fixture).expect("remove capability fixture");
    }

    #[test]
    fn context_resolves_auto_to_concrete_h264() {
        let context = streamer_context(
            json!({"sessionId":"one","status":2,"signalingUrl":"wss://server.nvidiagrid.net/nvst/"}),
            &json!({"codec":"auto","transportMode":"webrtc","resolution":"1920x1080"}),
        );
        assert_eq!(context["settings"]["codec"], "H264");
        assert_eq!(context_resolution(&context), (1920, 1080));
    }

    #[test]
    fn microphone_child_state_is_exposed_without_losing_the_message() {
        let state = Arc::new(Mutex::new(Snapshot::default()));
        apply_microphone_state(
            &json!({
                "type":"microphone-state",
                "state":"ready",
                "enabled":true,
                "message":"Microphone is streaming"
            }),
            &state,
        );

        let value = state.lock().expect("snapshot").value();
        assert_eq!(value["streamer"]["microphoneState"], "ready");
        assert_eq!(value["streamer"]["microphoneEnabled"], true);
        assert_eq!(
            value["streamer"]["microphoneMessage"],
            "Microphone is streaming"
        );
    }

    #[test]
    fn native_shortcut_actions_are_validated_and_exposed_once() {
        let state = Arc::new(Mutex::new(Snapshot::default()));
        handle_nvst_child_event(
            &json!({"type":"shortcut-action","action":"toggle-recording"}),
            &state,
        )
        .expect("supported shortcut action");
        let value = state.lock().expect("snapshot").value();
        assert_eq!(value["streamer"]["shortcutAction"], "toggle-recording");
        assert_eq!(value["streamer"]["shortcutActionGeneration"], 1);

        let failure = handle_nvst_child_event(
            &json!({"type":"shortcut-action","action":"launch-command"}),
            &state,
        )
        .expect_err("unsupported shortcut action must fail closed");
        assert_eq!(failure.code, "streamer_protocol_error");
        assert_eq!(
            state.lock().expect("snapshot").shortcut_action_generation,
            1
        );
    }

    #[test]
    fn structured_child_events_accumulate_redacted_acceptance_evidence() {
        let state = Arc::new(Mutex::new(Snapshot {
            started_at: Instant::now().checked_sub(Duration::from_millis(25)),
            ..Snapshot::default()
        }));
        for event in [
            json!({"type":"input-ready","protocolVersion":1}),
            json!({"type":"log","event":"backend-fallback","fromBackend":"hardware","toBackend":"software"}),
            json!({"type":"log","event":"queue-dropped","media":"video","count":3}),
            json!({"type":"log","event":"device-state","subsystem":"video","recovered":false}),
            json!({"type":"log","event":"device-state","subsystem":"video","recovered":true}),
            json!({"type":"status","event":"first-frame","backend":"ffmpeg","status":"streaming"}),
        ] {
            apply_child_telemetry(&event, &state);
        }

        let value = state.lock().expect("snapshot").acceptance_value();
        assert_eq!(value["kind"], "opennow.stream.acceptance");
        assert_eq!(value["mediaBackend"], "ffmpeg");
        assert!(value["firstFrameLatencyMs"].as_u64().unwrap_or_default() >= 25);
        assert_eq!(value["backendFallbackCount"], 1);
        assert_eq!(value["queueDropCount"], 3);
        assert_eq!(value["deviceLossCount"], 1);
        assert_eq!(value["deviceRecoveryCount"], 1);
        assert_eq!(value["inputReady"], true);
        assert!(value.get("sessionId").is_none());
        assert!(value.get("executable").is_none());
        assert!(value.get("processId").is_none());
    }

    #[test]
    fn dynamic_surface_is_bounded_and_normalized() {
        let surface = normalize_surface(
            Some(&json!({
                "screenRect":{"x":-120,"y":48,"width":1600,"height":900},
                "visible":true,
                "deviceScaleFactor":1.5
            })),
            (1280, 720),
        )
        .unwrap();
        assert_eq!(
            surface["rect"],
            json!({"x":0,"y":0,"width":1600,"height":900})
        );
        assert_eq!(surface["screenRect"]["x"], -120);
        assert_eq!(surface["deviceScaleFactor"], 1.5);
        assert!(
            normalize_surface(
                Some(&json!({"screenRect":{"width":32,"height":900}})),
                (1280, 720)
            )
            .is_err()
        );
    }

    #[test]
    fn signaling_url_is_scoped_to_nvidia_and_session() {
        let url = sign_in_url(
            "wss://80-1-2-3.nvidiagrid.net/nvst/",
            "session-one",
            "peer-one",
        )
        .unwrap();
        assert_eq!(url.path(), "/nvst/sign_in");
        assert!(url.query().unwrap().contains("pairing_id=session-one"));
        assert!(sign_in_url("wss://example.com/nvst/", "session", "peer").is_err());
    }

    #[test]
    fn nvst_context_preserves_explicit_transport() {
        let context = streamer_context(
            json!({"sessionId":"one","status":2,"rtspsEndpoints":["rtsps://203.0.113.20:322/session"]}),
            &json!({"codec":"H264","transportMode":"nvst","resolution":"1920x1080"}),
        );
        assert_eq!(context["settings"]["transportMode"], "nvst");
    }

    #[test]
    fn rtsp_parser_waits_for_body_and_validates_cseq() {
        let mut buffer = "RTSP/1.0 200 OK\r\nCSeq: 3\r\nContent-Length: 4\r\n\r\ntest".to_owned();
        let response = take_rtsp_response(&mut buffer, 3).unwrap().unwrap();
        assert_eq!(response.status, 200);
        assert_eq!(response.body, "test");
        assert!(buffer.is_empty());
    }

    #[test]
    fn nvst_transport_peer_ignores_flag_tokens() {
        let peer = parse_video_peer(
            "RTP/AVP/UDP;unicast;source=198.51.100.20;X-GS-ServerPort=49000-49001",
        )
        .unwrap();
        assert_eq!(peer, ("198.51.100.20".to_owned(), 49000));
    }

    #[test]
    fn nvst_sdp_attributes_are_case_insensitive_and_preserve_values() {
        let sdp = "A=X-NV-General.NativeRtcOnBundlePort:1\r\na=X-NV-runtime.encryptionKey:AbCd";
        assert_eq!(
            sdp_attribute(sdp, "general.nativeRtcOnBundlePort").as_deref(),
            Some("1")
        );
        assert_eq!(
            sdp_attribute(sdp, "runtime.encryptionKey").as_deref(),
            Some("AbCd")
        );
    }

    #[test]
    fn nvst_host_policy_rejects_local_and_private_addresses() {
        assert!(trusted_nvst_host("seat.nvidiagrid.net"));
        assert!(trusted_nvst_host("8.8.8.8"));
        assert!(!trusted_nvst_host("localhost"));
        assert!(!trusted_nvst_host("127.0.0.1"));
        assert!(!trusted_nvst_host("10.0.0.8"));
        assert!(!trusted_nvst_host("::1"));
    }

    #[test]
    fn nvst_hex_identity_increment_handles_carry() {
        assert_eq!(increment_hex("00ff").as_deref(), Some("0100"));
        assert_eq!(increment_hex("ffff").as_deref(), Some("10000"));
        assert_eq!(increment_hex("PING"), None);
    }
}
