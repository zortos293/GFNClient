//! Native client module
//!
//! Re-exports native client components for use in the Tauri library

// Re-export main module as public
pub mod main;

// Re-export commonly used types for convenience
#[cfg(feature = "native-client")]
pub use main::bridge;

#[cfg(feature = "native-client")]
pub use main::ffmpeg_decoder;

#[cfg(feature = "native-client")]
pub use main::hdr_detection;

#[cfg(feature = "native-client")]
pub use main::input;
#[cfg(feature = "native-client")]
pub use main::gpu_renderer;

#[cfg(feature = "native-client")]
pub use main::test_mode;
