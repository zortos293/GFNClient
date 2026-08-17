mod media;
mod native_surface;
mod output;
mod queue;
mod runtime;

pub use media::{EncodedFrame, MediaCodec, MediaFeedback, MediaSession, MediaSink, PushOutcome};
pub use runtime::{MainThreadHost, MediaRuntime, create_runtime};

use opennow_streamer_protocol::{CodecCapability, VideoBackendCapability};

pub fn video_backends() -> Vec<VideoBackendCapability> {
    vec![hardware_backend(), software_backend()]
}

pub const fn supports_audio_decode() -> bool {
    true
}

pub const fn supports_audio_output() -> bool {
    true
}

#[cfg(target_os = "windows")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "d3d11",
        "windows",
        "D3D11 hardware decode is not built into this binary",
    )
}

#[cfg(target_os = "macos")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "videotoolbox",
        "macos",
        "VideoToolbox hardware decode is not built into this binary",
    )
}

#[cfg(target_os = "linux")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "vaapi",
        "linux",
        "VA-API/V4L2 hardware decode is not built into this binary",
    )
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend("unsupported", "other", "Unsupported operating system")
}

fn software_backend() -> VideoBackendCapability {
    VideoBackendCapability {
        backend: "software",
        platform: "cross-platform",
        codecs: vec![
            CodecCapability {
                codec: "h264",
                available: true,
                reason: None,
            },
            CodecCapability {
                codec: "h265",
                available: false,
                reason: Some("H.265 decoder is not built into this binary"),
            },
            CodecCapability {
                codec: "av1",
                available: false,
                reason: Some("AV1 decoder is not built into this binary"),
            },
        ],
        zero_copy_modes: Vec::new(),
        available: true,
        reason: None,
    }
}

fn unavailable_backend(
    backend: &'static str,
    platform: &'static str,
    reason: &'static str,
) -> VideoBackendCapability {
    VideoBackendCapability {
        backend,
        platform,
        codecs: ["h264", "h265", "av1"]
            .into_iter()
            .map(|codec| CodecCapability {
                codec,
                available: false,
                reason: Some(reason),
            })
            .collect(),
        zero_copy_modes: Vec::new(),
        available: false,
        reason: Some(reason),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn advertises_only_the_linked_software_codec() {
        let backends = video_backends();
        let software = backends
            .iter()
            .find(|backend| backend.backend == "software")
            .expect("software backend");
        assert!(software.available);
        assert!(
            software
                .codecs
                .iter()
                .find(|codec| codec.codec == "h264")
                .expect("h264")
                .available
        );
        assert!(
            software
                .codecs
                .iter()
                .filter(|codec| codec.codec != "h264")
                .all(|codec| !codec.available)
        );
        assert!(
            backends
                .iter()
                .filter(|backend| backend.backend != "software")
                .all(|backend| !backend.available)
        );
    }
}
