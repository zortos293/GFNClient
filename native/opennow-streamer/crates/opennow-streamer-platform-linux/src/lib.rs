#![cfg_attr(not(target_os = "linux"), allow(dead_code))]

#[cfg(not(target_os = "linux"))]
compile_error!("opennow-streamer-platform-linux only supports Linux");

#[cfg(not(any(target_arch = "x86_64", target_arch = "aarch64")))]
compile_error!("the Linux backend currently supports x86_64 and aarch64");

mod audio;
mod capability;
mod error;
mod format;
#[cfg(feature = "vulkan")]
mod presentation;
mod queue;
mod session;
mod video;

pub use audio::{AudioBackend, AudioBackendPreference, AudioConfig, AudioPacket};
pub use capability::{
    BackendCapability, CapabilityReport, PresentationCapability, probe_capabilities,
    probe_video_capabilities,
};
pub use error::{Error, Result, Subsystem};
pub use format::{
    ChromaLocation, ColorMatrix, ColorRange, DecodedVideoFrame, EncodedVideoFrame, FramePlane,
    PixelFormat, StreamFormat,
};
#[cfg(feature = "vulkan")]
pub use presentation::{NativeSurface, VulkanPresenter};
pub use session::{
    BackendEvent, DecoderBackend, DecoderPreference, LifecycleState, LinuxSession, PushOutcome,
    SessionConfig,
};
