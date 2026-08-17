use opennow_streamer_protocol::{CodecCapability, VideoBackendCapability};

pub fn video_backends() -> Vec<VideoBackendCapability> {
    vec![hardware_backend(), software_backend()]
}

#[cfg(target_os = "windows")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "d3d11",
        "windows",
        "D3D11 hardware decode and presentation are not implemented yet",
    )
}

#[cfg(target_os = "macos")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "videotoolbox",
        "macos",
        "VideoToolbox and Metal presentation are not implemented yet",
    )
}

#[cfg(target_os = "linux")]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend(
        "vaapi",
        "linux",
        "VA-API/V4L2 decode and native presentation are not implemented yet",
    )
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
fn hardware_backend() -> VideoBackendCapability {
    unavailable_backend("unsupported", "other", "Unsupported operating system")
}

fn software_backend() -> VideoBackendCapability {
    unavailable_backend(
        "software",
        "cross-platform",
        "No software decoder is linked in the transport-only milestone",
    )
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
    fn never_advertises_an_unimplemented_backend() {
        assert!(video_backends().iter().all(|backend| !backend.available));
    }
}
