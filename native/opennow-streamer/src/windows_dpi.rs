//! Windows DPI policy for native renderer windows.
//!
//! Electron publishes native render-surface bounds in physical pixels. The
//! streamer must use the same coordinate space or Windows DPI virtualization
//! can offset or shrink the video window on scaled and mixed-DPI displays.

use std::ffi::c_void;

type DpiAwarenessContext = *mut c_void;

// DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 from winuser.h.
const PER_MONITOR_AWARE_V2: DpiAwarenessContext = -4_isize as DpiAwarenessContext;

#[link(name = "user32")]
extern "system" {
    fn SetProcessDpiAwarenessContext(value: DpiAwarenessContext) -> i32;
    fn SetProcessDPIAware() -> i32;
}

/// Opt the streamer into physical-pixel coordinates before GStreamer or any
/// renderer thread can create a window. The legacy fallback keeps coordinates
/// unvirtualized on Windows versions that reject the per-monitor-v2 context.
pub(crate) fn enable_per_monitor_awareness() {
    unsafe {
        if SetProcessDpiAwarenessContext(PER_MONITOR_AWARE_V2) == 0 {
            let _ = SetProcessDPIAware();
        }
    }
}
