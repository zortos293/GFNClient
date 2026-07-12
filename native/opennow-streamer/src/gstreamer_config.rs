// Always compiled so present-policy unit tests run without the optional
// `gstreamer` feature; production callers live behind that feature.
#![allow(dead_code)]

pub(crate) const EXTERNAL_RENDERER_ENV: &str = "OPENNOW_NATIVE_EXTERNAL_RENDERER";
pub(crate) const NATIVE_VIDEO_API_ENV: &str = "OPENNOW_NATIVE_VIDEO_API";
pub(crate) const NATIVE_VIDEO_BACKEND_ENV: &str = "OPENNOW_NATIVE_VIDEO_BACKEND";
pub(crate) const NATIVE_ZERO_COPY_ENV: &str = "OPENNOW_NATIVE_ZERO_COPY";
pub(crate) const NATIVE_PRESENT_MAX_FPS_ENV: &str = "OPENNOW_NATIVE_PRESENT_MAX_FPS";
pub(crate) const NATIVE_D3D_FULLSCREEN_ENV: &str = "OPENNOW_NATIVE_D3D_FULLSCREEN";
pub(crate) const PRESENT_LIMITER_AUTO_SENTINEL: u32 = u32::MAX;

pub(crate) fn use_external_renderer_window() -> bool {
    std::env::var(EXTERNAL_RENDERER_ENV)
        .map(|value| {
            !matches!(
                value.trim().to_ascii_lowercase().as_str(),
                "0" | "false" | "no" | "off"
            )
        })
        // Default to the internal child-surface renderer (single Electron window).
        .unwrap_or(false)
}

pub(crate) fn use_internal_renderer() -> bool {
    !use_external_renderer_window()
}

pub(crate) fn requested_video_backend() -> String {
    std::env::var(NATIVE_VIDEO_BACKEND_ENV)
        .or_else(|_| std::env::var(NATIVE_VIDEO_API_ENV))
        .unwrap_or_else(|_| "auto".to_owned())
        .to_ascii_lowercase()
}

pub(crate) fn zero_copy_requested() -> bool {
    matches!(
        std::env::var(NATIVE_ZERO_COPY_ENV)
            .unwrap_or_else(|_| "auto".to_owned())
            .to_ascii_lowercase()
            .as_str(),
        "1" | "true" | "yes" | "forced"
    )
}

pub(crate) fn resolve_present_max_fps(requested_fps: u32) -> u32 {
    if let Ok(value) = std::env::var(NATIVE_PRESENT_MAX_FPS_ENV) {
        let value = value.trim().to_ascii_lowercase();
        if value == "0" || value == "off" || value == "false" || value == "unlimited" {
            return 0;
        }
        if value == "auto" {
            return PRESENT_LIMITER_AUTO_SENTINEL;
        }
        if let Ok(fps) = value.parse::<u32>() {
            return fps;
        }
    }
    let _ = requested_fps;
    PRESENT_LIMITER_AUTO_SENTINEL
}

pub(crate) fn automatic_present_max_fps(requested_fps: u32, display_hz: Option<u32>) -> u32 {
    display_hz
        .filter(|display_hz| *display_hz >= 30 && *display_hz < requested_fps)
        .unwrap_or(0)
}

pub(crate) fn resolve_d3d_fullscreen_sink(cloud_gsync_enabled: bool) -> bool {
    resolve_d3d_fullscreen_sink_for(
        use_internal_renderer(),
        cloud_gsync_enabled,
        std::env::var(NATIVE_D3D_FULLSCREEN_ENV).ok(),
    )
}

/// Pure policy for exclusive D3D fullscreen present.
///
/// Internal (child HWND) always stays windowed — exclusive fullscreen fights
/// Electron parenting. External may enable it for Cloud G-Sync/VRR, or via
/// `OPENNOW_NATIVE_D3D_FULLSCREEN`.
pub(crate) fn resolve_d3d_fullscreen_sink_for(
    internal_renderer: bool,
    cloud_gsync_enabled: bool,
    env_override: Option<String>,
) -> bool {
    if internal_renderer {
        return false;
    }

    if let Some(value) = env_override {
        let value = value.trim().to_ascii_lowercase();
        if matches!(value.as_str(), "1" | "on" | "true" | "yes") {
            return true;
        }
        if matches!(value.as_str(), "0" | "off" | "false" | "no") {
            return false;
        }
    }

    cloud_gsync_enabled
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn automatic_present_limiter_uses_display_refresh_below_requested_fps() {
        assert_eq!(automatic_present_max_fps(240, Some(165)), 165);
        assert_eq!(automatic_present_max_fps(240, Some(240)), 0);
        assert_eq!(automatic_present_max_fps(240, Some(1)), 0);
        assert_eq!(automatic_present_max_fps(240, None), 0);
    }

    #[test]
    fn internal_renderer_never_enables_exclusive_d3d_fullscreen() {
        assert!(!resolve_d3d_fullscreen_sink_for(true, true, None));
        assert!(!resolve_d3d_fullscreen_sink_for(
            true,
            true,
            Some("1".to_owned())
        ));
        assert!(!resolve_d3d_fullscreen_sink_for(
            true,
            false,
            Some("on".to_owned())
        ));
    }

    #[test]
    fn external_renderer_follows_cloud_gsync_and_env_for_d3d_fullscreen() {
        assert!(resolve_d3d_fullscreen_sink_for(false, true, None));
        assert!(!resolve_d3d_fullscreen_sink_for(false, false, None));
        assert!(resolve_d3d_fullscreen_sink_for(
            false,
            false,
            Some("1".to_owned())
        ));
        assert!(!resolve_d3d_fullscreen_sink_for(
            false,
            true,
            Some("0".to_owned())
        ));
    }
}
