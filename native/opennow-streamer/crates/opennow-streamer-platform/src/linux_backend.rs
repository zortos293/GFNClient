use std::sync::OnceLock;

use opennow_streamer_platform_linux::{
    BackendCapability, DecoderPreference, PresentationCapability, probe_video_capabilities,
};
use opennow_streamer_protocol::{CodecCapability, VideoBackendCapability};

const VIDEO_BACKEND_ENV: &str = "OPENNOW_NATIVE_VIDEO_BACKEND";
const WINDOW_SYSTEM_ENV: &str = "OPENNOW_NATIVE_WINDOW_SYSTEM";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum LinuxVideoPath {
    Hardware(DecoderPreference),
    Software,
}

#[derive(Debug, Clone)]
pub(crate) struct LinuxVideoSelection {
    pub(crate) path: LinuxVideoPath,
    pub(crate) fallback_reason: Option<String>,
}

#[derive(Debug, Clone)]
struct LinuxCapabilitySnapshot {
    vaapi: BackendCapability,
    v4l2: BackendCapability,
    presentation: PresentationCapability,
}

impl LinuxCapabilitySnapshot {
    fn probe() -> Self {
        let (decoders, presentation) = probe_video_capabilities();
        let decoder = |name| {
            decoders
                .iter()
                .find(|capability| capability.name == name)
                .cloned()
                .unwrap_or(BackendCapability {
                    name,
                    available: false,
                    detail: "capability probe did not return this decoder".to_owned(),
                })
        };
        Self {
            vaapi: decoder("vaapi-h264"),
            v4l2: decoder("v4l2-h264"),
            presentation,
        }
    }

    fn hardware_available(&self, decoder: &BackendCapability, window_system: &str) -> bool {
        decoder.available && presentation_available(&self.presentation, window_system)
    }

    fn unavailable_reason(&self, decoder: &BackendCapability, window_system: &str) -> String {
        if !decoder.available {
            return decoder.detail.clone();
        }
        if !presentation_available(&self.presentation, window_system) {
            return presentation_unavailable_reason(&self.presentation, window_system);
        }
        "Linux hardware video path is unavailable".to_owned()
    }
}

fn presentation_available(presentation: &PresentationCapability, window_system: &str) -> bool {
    presentation.available && presentation.window_systems.contains(&window_system)
}

fn presentation_unavailable_reason(
    presentation: &PresentationCapability,
    window_system: &str,
) -> String {
    if !presentation.available {
        return presentation.detail.clone();
    }
    format!(
        "Vulkan presentation lacks {window_system} WSI required by the active Linux window system"
    )
}

fn presentation_status(presentation: &PresentationCapability, window_system: &str) -> String {
    if presentation_available(presentation, window_system) {
        format!("available for {window_system}: {}", presentation.detail)
    } else {
        presentation_unavailable_reason(presentation, window_system)
    }
}

fn selected_window_system() -> &'static str {
    selected_window_system_for(
        std::env::var(WINDOW_SYSTEM_ENV).ok().as_deref(),
        std::env::var("SDL_VIDEODRIVER").ok().as_deref(),
        std::env::var_os("WAYLAND_DISPLAY").is_some(),
        std::env::var_os("DISPLAY").is_some(),
    )
}

fn selected_window_system_for(
    requested: Option<&str>,
    sdl_driver: Option<&str>,
    has_wayland_display: bool,
    has_x11_display: bool,
) -> &'static str {
    for value in [requested, sdl_driver].into_iter().flatten() {
        match value.trim().to_ascii_lowercase().as_str() {
            "wayland" => return "wayland",
            "x11" => return "x11",
            _ => {}
        }
    }
    if has_wayland_display && !has_x11_display {
        "wayland"
    } else {
        "x11"
    }
}

fn capabilities() -> &'static LinuxCapabilitySnapshot {
    static CAPABILITIES: OnceLock<LinuxCapabilitySnapshot> = OnceLock::new();
    CAPABILITIES.get_or_init(LinuxCapabilitySnapshot::probe)
}

pub(crate) fn select_video_path() -> LinuxVideoSelection {
    select_video_path_for(
        std::env::var(VIDEO_BACKEND_ENV).ok().as_deref(),
        capabilities(),
        selected_window_system(),
    )
}

fn select_video_path_for(
    requested: Option<&str>,
    capabilities: &LinuxCapabilitySnapshot,
    window_system: &str,
) -> LinuxVideoSelection {
    let requested = requested
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("auto")
        .to_ascii_lowercase();
    let vaapi = capabilities.hardware_available(&capabilities.vaapi, window_system);
    let v4l2 = capabilities.hardware_available(&capabilities.v4l2, window_system);
    let (path, fallback_reason) = match requested.as_str() {
        "software" => (LinuxVideoPath::Software, None),
        "vaapi" if vaapi => (LinuxVideoPath::Hardware(DecoderPreference::VaApiOnly), None),
        "v4l2" if v4l2 => (LinuxVideoPath::Hardware(DecoderPreference::V4l2Only), None),
        "auto" | "vulkan" if vaapi || v4l2 => {
            let preference = if vaapi {
                DecoderPreference::VaApiThenV4l2
            } else {
                DecoderPreference::V4l2ThenVaApi
            };
            (LinuxVideoPath::Hardware(preference), None)
        }
        "vaapi" => (
            LinuxVideoPath::Software,
            Some(format!(
                "VA-API was requested but is unavailable: {}",
                capabilities.unavailable_reason(&capabilities.vaapi, window_system)
            )),
        ),
        "v4l2" => (
            LinuxVideoPath::Software,
            Some(format!(
                "V4L2 was requested but is unavailable: {}",
                capabilities.unavailable_reason(&capabilities.v4l2, window_system)
            )),
        ),
        "auto" | "vulkan" => (
            LinuxVideoPath::Software,
            Some(format!(
                "Linux hardware video is unavailable (VA-API: {}; V4L2: {}; Vulkan: {})",
                capabilities.vaapi.detail,
                capabilities.v4l2.detail,
                presentation_status(&capabilities.presentation, window_system),
            )),
        ),
        other => (
            LinuxVideoPath::Software,
            Some(format!(
                "Video backend {other:?} is not supported on Linux; using software decode"
            )),
        ),
    };
    LinuxVideoSelection {
        path,
        fallback_reason,
    }
}

pub(crate) fn video_backends() -> Vec<VideoBackendCapability> {
    let capabilities = capabilities();
    let window_system = selected_window_system();
    vec![
        hardware_capability(
            "vaapi",
            &capabilities.vaapi,
            &capabilities.presentation,
            window_system,
        ),
        hardware_capability(
            "v4l2",
            &capabilities.v4l2,
            &capabilities.presentation,
            window_system,
        ),
    ]
}

fn hardware_capability(
    backend: &'static str,
    decoder: &BackendCapability,
    presentation: &PresentationCapability,
    window_system: &str,
) -> VideoBackendCapability {
    let available = decoder.available && presentation_available(presentation, window_system);
    let reason = if !decoder.available {
        Some(decoder.detail.clone())
    } else if !presentation_available(presentation, window_system) {
        Some(presentation_unavailable_reason(presentation, window_system))
    } else {
        None
    };
    let reason = reason.map(static_reason);
    VideoBackendCapability {
        backend,
        platform: "linux",
        codecs: vec![
            CodecCapability {
                codec: "h264",
                available,
                reason,
            },
            CodecCapability {
                codec: "h265",
                available: false,
                reason: Some("H.265 hardware decode is not implemented"),
            },
            CodecCapability {
                codec: "av1",
                available: false,
                reason: Some("AV1 hardware decode is not implemented"),
            },
        ],
        zero_copy_modes: Vec::new(),
        available,
        reason,
    }
}

fn static_reason(reason: String) -> &'static str {
    Box::leak(reason.into_boxed_str())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn snapshot(
        vaapi: bool,
        v4l2: bool,
        vulkan: bool,
        window_systems: Vec<&'static str>,
    ) -> LinuxCapabilitySnapshot {
        LinuxCapabilitySnapshot {
            vaapi: BackendCapability {
                name: "vaapi-h264",
                available: vaapi,
                detail: "vaapi probe".to_owned(),
            },
            v4l2: BackendCapability {
                name: "v4l2-h264",
                available: v4l2,
                detail: "v4l2 probe".to_owned(),
            },
            presentation: PresentationCapability {
                available: vulkan,
                api: "vulkan",
                window_systems,
                detail: "vulkan probe".to_owned(),
            },
        }
    }

    #[test]
    fn honors_explicit_linux_backend_preference() {
        let available = snapshot(true, true, true, vec!["x11"]);
        assert_eq!(
            select_video_path_for(Some("vaapi"), &available, "x11").path,
            LinuxVideoPath::Hardware(DecoderPreference::VaApiOnly)
        );
        assert_eq!(
            select_video_path_for(Some("v4l2"), &available, "x11").path,
            LinuxVideoPath::Hardware(DecoderPreference::V4l2Only)
        );
        assert_eq!(
            select_video_path_for(Some("software"), &available, "x11").path,
            LinuxVideoPath::Software
        );
    }

    #[test]
    fn auto_prefers_vaapi_then_v4l2_and_requires_presentation() {
        assert_eq!(
            select_video_path_for(
                Some("auto"),
                &snapshot(true, true, true, vec!["x11"]),
                "x11",
            )
            .path,
            LinuxVideoPath::Hardware(DecoderPreference::VaApiThenV4l2)
        );
        assert_eq!(
            select_video_path_for(None, &snapshot(false, true, true, vec!["x11"]), "x11").path,
            LinuxVideoPath::Hardware(DecoderPreference::V4l2ThenVaApi)
        );
        assert_eq!(
            select_video_path_for(None, &snapshot(true, true, false, Vec::new()), "x11").path,
            LinuxVideoPath::Software
        );
    }

    #[test]
    fn wayland_only_vulkan_snapshot_supports_wayland_and_rejects_x11() {
        let capabilities = snapshot(true, true, true, vec!["wayland"]);
        let selected = select_video_path_for(None, &capabilities, "wayland");
        assert_eq!(
            selected.path,
            LinuxVideoPath::Hardware(DecoderPreference::VaApiThenV4l2)
        );
        assert!(selected.fallback_reason.is_none());

        let selected = select_video_path_for(None, &capabilities, "x11");
        assert_eq!(selected.path, LinuxVideoPath::Software);
        assert!(
            selected
                .fallback_reason
                .as_deref()
                .is_some_and(|reason| reason.contains("lacks x11 WSI"))
        );

        let backend = hardware_capability(
            "vaapi",
            &capabilities.vaapi,
            &capabilities.presentation,
            "x11",
        );
        assert!(!backend.available);
        assert_eq!(
            backend.reason,
            Some("Vulkan presentation lacks x11 WSI required by the active Linux window system")
        );
    }

    #[test]
    fn window_system_selection_prefers_explicit_runtime_contract() {
        assert_eq!(
            selected_window_system_for(Some("wayland"), Some("x11"), false, true),
            "wayland"
        );
        assert_eq!(
            selected_window_system_for(None, Some("wayland"), true, true),
            "wayland"
        );
        assert_eq!(
            selected_window_system_for(None, None, true, false),
            "wayland"
        );
        assert_eq!(selected_window_system_for(None, None, true, true), "x11");
    }

    #[test]
    fn unsupported_or_unavailable_explicit_backend_falls_back_honestly() {
        for requested in ["vaapi", "v4l2", "nvdec", "d3d11"] {
            let selected = select_video_path_for(
                Some(requested),
                &snapshot(false, false, true, vec!["x11"]),
                "x11",
            );
            assert_eq!(selected.path, LinuxVideoPath::Software);
            assert!(selected.fallback_reason.is_some());
        }
    }

    #[test]
    fn software_fallback_does_not_report_available_wayland_wsi_as_missing() {
        let selected = select_video_path_for(
            None,
            &snapshot(false, false, true, vec!["wayland"]),
            "wayland",
        );
        let reason = selected.fallback_reason.expect("fallback reason");
        assert!(reason.contains("Vulkan: available for wayland"));
        assert!(!reason.contains("lacks wayland WSI"));
    }
}
