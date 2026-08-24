use std::sync::OnceLock;

use opennow_streamer_platform_linux::{
    BackendCapability, DecoderPreference, PresentationCapability, probe_video_capabilities,
};
use opennow_streamer_protocol::{CodecCapability, VideoBackendCapability};

const VIDEO_BACKEND_ENV: &str = "OPENNOW_NATIVE_VIDEO_BACKEND";
const REQUIRED_WINDOW_SYSTEM: &str = "x11";

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

    fn hardware_available(&self, decoder: &BackendCapability) -> bool {
        decoder.available && presentation_available(&self.presentation)
    }

    fn unavailable_reason(&self, decoder: &BackendCapability) -> String {
        if !decoder.available {
            return decoder.detail.clone();
        }
        if !presentation_available(&self.presentation) {
            return presentation_unavailable_reason(&self.presentation);
        }
        "Linux hardware video path is unavailable".to_owned()
    }
}

fn presentation_available(presentation: &PresentationCapability) -> bool {
    presentation.available
        && presentation
            .window_systems
            .contains(&REQUIRED_WINDOW_SYSTEM)
}

fn presentation_unavailable_reason(presentation: &PresentationCapability) -> String {
    if !presentation.available {
        return presentation.detail.clone();
    }
    format!(
        "Vulkan presentation lacks {REQUIRED_WINDOW_SYSTEM} WSI required by the Electron native window path"
    )
}

fn capabilities() -> &'static LinuxCapabilitySnapshot {
    static CAPABILITIES: OnceLock<LinuxCapabilitySnapshot> = OnceLock::new();
    CAPABILITIES.get_or_init(LinuxCapabilitySnapshot::probe)
}

pub(crate) fn select_video_path() -> LinuxVideoSelection {
    select_video_path_for(
        std::env::var(VIDEO_BACKEND_ENV).ok().as_deref(),
        capabilities(),
    )
}

fn select_video_path_for(
    requested: Option<&str>,
    capabilities: &LinuxCapabilitySnapshot,
) -> LinuxVideoSelection {
    let requested = requested
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("auto")
        .to_ascii_lowercase();
    let vaapi = capabilities.hardware_available(&capabilities.vaapi);
    let v4l2 = capabilities.hardware_available(&capabilities.v4l2);
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
                capabilities.unavailable_reason(&capabilities.vaapi)
            )),
        ),
        "v4l2" => (
            LinuxVideoPath::Software,
            Some(format!(
                "V4L2 was requested but is unavailable: {}",
                capabilities.unavailable_reason(&capabilities.v4l2)
            )),
        ),
        "auto" | "vulkan" => (
            LinuxVideoPath::Software,
            Some(format!(
                "Linux hardware video is unavailable (VA-API: {}; V4L2: {}; Vulkan: {})",
                capabilities.vaapi.detail,
                capabilities.v4l2.detail,
                presentation_unavailable_reason(&capabilities.presentation),
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
    vec![
        hardware_capability("vaapi", &capabilities.vaapi, &capabilities.presentation),
        hardware_capability("v4l2", &capabilities.v4l2, &capabilities.presentation),
    ]
}

fn hardware_capability(
    backend: &'static str,
    decoder: &BackendCapability,
    presentation: &PresentationCapability,
) -> VideoBackendCapability {
    let available = decoder.available && presentation_available(presentation);
    let reason = if !decoder.available {
        Some(decoder.detail.clone())
    } else if !presentation_available(presentation) {
        Some(presentation_unavailable_reason(presentation))
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
            select_video_path_for(Some("vaapi"), &available).path,
            LinuxVideoPath::Hardware(DecoderPreference::VaApiOnly)
        );
        assert_eq!(
            select_video_path_for(Some("v4l2"), &available).path,
            LinuxVideoPath::Hardware(DecoderPreference::V4l2Only)
        );
        assert_eq!(
            select_video_path_for(Some("software"), &available).path,
            LinuxVideoPath::Software
        );
    }

    #[test]
    fn auto_prefers_vaapi_then_v4l2_and_requires_presentation() {
        assert_eq!(
            select_video_path_for(Some("auto"), &snapshot(true, true, true, vec!["x11"])).path,
            LinuxVideoPath::Hardware(DecoderPreference::VaApiThenV4l2)
        );
        assert_eq!(
            select_video_path_for(None, &snapshot(false, true, true, vec!["x11"])).path,
            LinuxVideoPath::Hardware(DecoderPreference::V4l2ThenVaApi)
        );
        assert_eq!(
            select_video_path_for(None, &snapshot(true, true, false, Vec::new())).path,
            LinuxVideoPath::Software
        );
    }

    #[test]
    fn wayland_only_vulkan_snapshot_falls_back_and_reports_x11_requirement() {
        let capabilities = snapshot(true, true, true, vec!["wayland"]);
        let selected = select_video_path_for(None, &capabilities);
        assert_eq!(selected.path, LinuxVideoPath::Software);
        assert!(
            selected
                .fallback_reason
                .as_deref()
                .is_some_and(|reason| reason.contains("lacks x11 WSI"))
        );

        let backend = hardware_capability("vaapi", &capabilities.vaapi, &capabilities.presentation);
        assert!(!backend.available);
        assert_eq!(
            backend.reason,
            Some("Vulkan presentation lacks x11 WSI required by the Electron native window path")
        );
    }

    #[test]
    fn unsupported_or_unavailable_explicit_backend_falls_back_honestly() {
        for requested in ["vaapi", "v4l2", "nvdec", "d3d11"] {
            let selected =
                select_video_path_for(Some(requested), &snapshot(false, false, true, vec!["x11"]));
            assert_eq!(selected.path, LinuxVideoPath::Software);
            assert!(selected.fallback_reason.is_some());
        }
    }
}
