//! Dedicated child native surface for the internal (single-window) renderer.
//!
//! GStreamer paints into a streamer-owned child surface that is parented into
//! the Electron window. The Electron BrowserWindow handle is never used as a
//! GstVideoOverlay target.

use crate::protocol::{NativeRenderRect, NativeRenderSurface};
use gstreamer as gst;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Mutex;

#[cfg(any(target_os = "windows", target_os = "linux"))]
fn parse_native_handle(value: &str) -> Result<usize, String> {
    let trimmed = value.trim();
    let hex = trimmed
        .strip_prefix("0x")
        .or_else(|| trimmed.strip_prefix("0X"));
    let parsed = if let Some(hex) = hex {
        usize::from_str_radix(hex, 16)
    } else {
        trimmed.parse::<usize>()
    }
    .map_err(|error| format!("Invalid native parent window handle {value:?}: {error}"))?;

    if parsed == 0 {
        return Err("Native parent window handle is zero.".to_owned());
    }

    Ok(parsed)
}

fn normalized_rect(rect: Option<&NativeRenderRect>) -> NativeRenderRect {
    let Some(rect) = rect else {
        return NativeRenderRect {
            x: 0,
            y: 0,
            width: 2,
            height: 2,
        };
    };

    NativeRenderRect {
        x: rect.x.max(0),
        y: rect.y.max(0),
        width: rect.width.max(2),
        height: rect.height.max(2),
    }
}

/// Platform child surface that GStreamer presents into.
#[derive(Debug)]
pub(crate) struct InternalRenderer {
    inner: Mutex<InternalRendererState>,
    /// Child window handle for prepare-window-handle (avoids locking during sink setup).
    child_handle: AtomicUsize,
    child_width: std::sync::atomic::AtomicI32,
    child_height: std::sync::atomic::AtomicI32,
}

// Child window handles are owned exclusively by this process and only mutated
// under the mutex; GStreamer callbacks require Send + Sync on render state.
unsafe impl Send for InternalRenderer {}
unsafe impl Sync for InternalRenderer {}

#[derive(Debug)]
struct InternalRendererState {
    surface: Option<PlatformChildSurface>,
    last_parent: Option<usize>,
    last_bounds: Option<NativeRenderRect>,
    last_visible: bool,
    video_sink: Option<gst::Element>,
}

impl InternalRenderer {
    pub(crate) fn new() -> Self {
        Self {
            inner: Mutex::new(InternalRendererState {
                surface: None,
                last_parent: None,
                last_bounds: None,
                last_visible: false,
                video_sink: None,
            }),
            child_handle: AtomicUsize::new(0),
            child_width: std::sync::atomic::AtomicI32::new(2),
            child_height: std::sync::atomic::AtomicI32::new(2),
        }
    }

    fn publish_child_handle(&self, handle: usize, bounds: &NativeRenderRect) {
        self.child_handle.store(handle, Ordering::SeqCst);
        self.child_width.store(bounds.width, Ordering::SeqCst);
        self.child_height.store(bounds.height, Ordering::SeqCst);
    }

    fn clear_child_handle(&self) {
        self.child_handle.store(0, Ordering::SeqCst);
        self.child_width.store(2, Ordering::SeqCst);
        self.child_height.store(2, Ordering::SeqCst);
    }

    #[cfg(target_os = "windows")]
    pub(crate) fn child_handle(&self) -> usize {
        self.child_handle.load(Ordering::SeqCst)
    }

    pub(crate) fn set_video_sink(&self, sink: gst::Element) -> Result<(), String> {
        let mut state = self
            .inner
            .lock()
            .map_err(|_| "Internal renderer lock poisoned.".to_owned())?;
        state.video_sink = Some(sink);
        Self::rebind_overlay_locked(self, &mut state)
    }

    pub(crate) fn apply_surface(&self, surface: &NativeRenderSurface) -> Result<(), String> {
        let mut state = self
            .inner
            .lock()
            .map_err(|_| "Internal renderer lock poisoned.".to_owned())?;

        let parent = match surface.window_handle.as_deref() {
            Some(handle) => Some(parse_parent_handle(handle)?),
            None => None,
        };

        if !surface.visible || parent.is_none() || surface.rect.is_none() {
            if let Some(child) = state.surface.as_mut() {
                child.set_visible(false)?;
            }
            state.last_visible = false;
            return Ok(());
        }

        let parent = parent.expect("checked above");
        let bounds = normalized_rect(surface.rect.as_ref());

        let needs_recreate = match state.last_parent {
            Some(existing) => existing != parent || state.surface.is_none(),
            None => true,
        };

        if needs_recreate {
            if let Some(mut previous) = state.surface.take() {
                previous.destroy();
            }
            let child = PlatformChildSurface::create(parent, &bounds)?;
            self.publish_child_handle(child.handle(), &bounds);
            state.surface = Some(child);
            state.last_parent = Some(parent);
            state.last_bounds = Some(bounds.clone());
            state.last_visible = true;
            Self::rebind_overlay_locked(self, &mut state)?;
        } else {
            let bounds_changed = state.last_bounds.as_ref() != Some(&bounds);
            let was_visible = state.last_visible;
            if let Some(child) = state.surface.as_mut() {
                if bounds_changed {
                    child.set_bounds(&bounds)?;
                }
                if !was_visible {
                    child.set_visible(true)?;
                }
                self.publish_child_handle(child.handle(), &bounds);
            }
            state.last_bounds = Some(bounds);
            state.last_visible = true;
            if bounds_changed || !was_visible {
                Self::rebind_overlay_locked(self, &mut state)?;
            }
        }

        Ok(())
    }

    pub(crate) fn destroy(&self) {
        if let Ok(mut state) = self.inner.lock() {
            if let Some(mut surface) = state.surface.take() {
                surface.destroy();
            }
            state.last_parent = None;
            state.last_bounds = None;
            state.last_visible = false;
            state.video_sink = None;
        }
        self.clear_child_handle();
    }

    fn rebind_overlay_locked(
        renderer: &InternalRenderer,
        state: &mut InternalRendererState,
    ) -> Result<(), String> {
        let (Some(child), Some(sink)) = (state.surface.as_ref(), state.video_sink.as_ref()) else {
            return Ok(());
        };
        let bounds = state.last_bounds.clone().unwrap_or_else(|| NativeRenderRect {
            x: 0,
            y: 0,
            width: renderer.child_width.load(Ordering::SeqCst).max(2),
            height: renderer.child_height.load(Ordering::SeqCst).max(2),
        });
        bind_overlay_to_child(sink, child.handle(), Some(&bounds))
    }
}

impl Drop for InternalRenderer {
    fn drop(&mut self) {
        self.destroy();
    }
}

fn parse_parent_handle(value: &str) -> Result<usize, String> {
    #[cfg(any(target_os = "windows", target_os = "linux"))]
    {
        parse_native_handle(value)
    }
    #[cfg(target_os = "macos")]
    {
        parse_native_handle_macos(value)
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        let _ = value;
        Err("Internal renderer is not supported on this platform.".to_owned())
    }
}

#[cfg(target_os = "macos")]
fn parse_native_handle_macos(value: &str) -> Result<usize, String> {
    let trimmed = value.trim();
    let hex = trimmed
        .strip_prefix("0x")
        .or_else(|| trimmed.strip_prefix("0X"));
    let parsed = if let Some(hex) = hex {
        usize::from_str_radix(hex, 16)
    } else {
        trimmed.parse::<usize>()
    }
    .map_err(|error| format!("Invalid native parent view handle {value:?}: {error}"))?;

    if parsed == 0 {
        return Err("Native parent view handle is zero.".to_owned());
    }

    Ok(parsed)
}

fn bind_overlay_to_child(
    sink: &gst::Element,
    child_handle: usize,
    bounds: Option<&NativeRenderRect>,
) -> Result<(), String> {
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        use gst_video::prelude::*;
        use gstreamer_video as gst_video;

        let overlay = sink
            .clone()
            .dynamic_cast::<gst_video::VideoOverlay>()
            .map_err(|_| {
                format!(
                    "Native render sink {} does not implement GstVideoOverlay.",
                    sink.name()
                )
            })?;

        // SAFETY: child_handle is a platform window/view created by this process
        // (or an X11 window id we own) and remains valid while the surface lives.
        // Win32 vulkansink: our patched gstvkwindow presents on the GSTVULKAN child
        // hwnd while parenting that child under this overlay handle (no floating window).
        unsafe {
            overlay.set_window_handle(child_handle);
        }
        overlay.handle_events(false);
        // Child surface is already sized to the StreamView rect; present into the
        // full client area so D3D sinks do not wait on an empty render rectangle.
        let rect = normalized_rect(bounds);
        let _ = overlay.set_render_rectangle(0, 0, rect.width, rect.height);
        overlay.expose();
        Ok(())
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        let _ = (sink, child_handle, bounds);
        Err("Internal renderer overlay binding is not supported on this platform.".to_owned())
    }
}

#[derive(Debug)]
struct PlatformChildSurface {
    #[cfg(target_os = "windows")]
    win: windows_child::ChildWindow,
    #[cfg(target_os = "macos")]
    view: usize,
    #[cfg(target_os = "linux")]
    window: linux_child::XWindow,
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    _unused: (),
}

impl PlatformChildSurface {
    fn create(parent: usize, bounds: &NativeRenderRect) -> Result<Self, String> {
        #[cfg(target_os = "windows")]
        {
            Ok(Self {
                win: windows_child::ChildWindow::create(parent, bounds)?,
            })
        }
        #[cfg(target_os = "macos")]
        {
            let view = macos_child::create_child(parent, bounds)?;
            Ok(Self {
                view: view as usize,
            })
        }
        #[cfg(target_os = "linux")]
        {
            let window = linux_child::create_child(parent, bounds)?;
            Ok(Self { window })
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            let _ = (parent, bounds);
            Err("Internal renderer child surfaces are not supported on this platform.".to_owned())
        }
    }

    fn handle(&self) -> usize {
        #[cfg(target_os = "windows")]
        {
            self.win.hwnd()
        }
        #[cfg(target_os = "macos")]
        {
            self.view
        }
        #[cfg(target_os = "linux")]
        {
            self.window.window as usize
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            0
        }
    }

    fn set_bounds(&mut self, bounds: &NativeRenderRect) -> Result<(), String> {
        #[cfg(target_os = "windows")]
        {
            self.win.set_bounds(bounds)
        }
        #[cfg(target_os = "macos")]
        {
            macos_child::set_bounds(self.view as macos_child::NsViewPtr, bounds)
        }
        #[cfg(target_os = "linux")]
        {
            linux_child::set_bounds(&self.window, bounds)
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            let _ = bounds;
            Ok(())
        }
    }

    fn set_visible(&mut self, visible: bool) -> Result<(), String> {
        #[cfg(target_os = "windows")]
        {
            self.win.set_visible(visible)
        }
        #[cfg(target_os = "macos")]
        {
            macos_child::set_visible(self.view as macos_child::NsViewPtr, visible)
        }
        #[cfg(target_os = "linux")]
        {
            linux_child::set_visible(&self.window, visible)
        }
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        {
            let _ = visible;
            Ok(())
        }
    }

    fn destroy(&mut self) {
        #[cfg(target_os = "windows")]
        {
            self.win.destroy();
        }
        #[cfg(target_os = "macos")]
        {
            macos_child::destroy(self.view as macos_child::NsViewPtr);
            self.view = 0;
        }
        #[cfg(target_os = "linux")]
        {
            linux_child::destroy(&mut self.window);
        }
    }
}

#[cfg(target_os = "windows")]
mod windows_child {
    use crate::protocol::NativeRenderRect;
    use std::ffi::c_void;
    use std::ptr::{null, null_mut};
    use std::sync::atomic::{AtomicBool, Ordering};

    pub(super) type Hwnd = *mut c_void;

    type Atom = u16;
    type Bool = i32;
    type Hinstance = *mut c_void;
    type Hmenu = *mut c_void;
    type Lparam = isize;
    type Lresult = isize;
    type Wparam = usize;

    const CS_HREDRAW: u32 = 0x0002;
    const CS_OWNDC: u32 = 0x0020;
    const CS_VREDRAW: u32 = 0x0001;
    // Sibling of Chromium Intermediate D3D: sit on top for present, force
    // Intermediate D3D WS_CLIPSIBLINGS so it punches a paint hole. Input is
    // owned by RawInput on this HWND (not Electron click-through).
    const HWND_TOP: Hwnd = std::ptr::null_mut();
    const GWL_STYLE: i32 = -16;
    const GWL_EXSTYLE: i32 = -20;
    const SWP_NOSIZE: u32 = 0x0001;
    const SWP_NOMOVE: u32 = 0x0002;
    const SWP_NOACTIVATE: u32 = 0x0010;
    const SWP_FRAMECHANGED: u32 = 0x0020;
    const SWP_SHOWWINDOW: u32 = 0x0040;
    const SWP_HIDEWINDOW: u32 = 0x0080;
    const SWP_NOCOPYBITS: u32 = 0x0100;
    const SWP_NOZORDER: u32 = 0x0004;
    const SWP_NOSENDCHANGING: u32 = 0x0400;
    const SW_HIDE: i32 = 0;
    const SW_SHOWNOACTIVATE: i32 = 4;
    const WM_DESTROY: u32 = 0x0002;
    const WM_ERASEBKGND: u32 = 0x0014;
    const WM_MOUSEACTIVATE: u32 = 0x0021;
    const WM_PAINT: u32 = 0x000F;
    const WS_CHILD: u32 = 0x4000_0000;
    const WS_CLIPCHILDREN: u32 = 0x0200_0000;
    const WS_CLIPSIBLINGS: u32 = 0x0400_0000;
    const WS_VISIBLE: u32 = 0x1000_0000;
    const WS_POPUP: u32 = 0x8000_0000;
    const WS_CAPTION: u32 = 0x00C0_0000;
    const WS_THICKFRAME: u32 = 0x0004_0000;
    const WS_MINIMIZEBOX: u32 = 0x0002_0000;
    const WS_MAXIMIZEBOX: u32 = 0x0001_0000;
    const WS_SYSMENU: u32 = 0x0008_0000;
    const WS_BORDER: u32 = 0x0080_0000;
    const WS_EX_APPWINDOW: u32 = 0x0004_0000;
    const WS_EX_TOOLWINDOW: u32 = 0x0000_0080;
    const WS_EX_NOACTIVATE: u32 = 0x0800_0000;
    const MA_ACTIVATE: isize = 1;
    const BLACK_BRUSH: i32 = 4;

    #[repr(C)]
    struct WndClassExW {
        cb_size: u32,
        style: u32,
        lpfn_wnd_proc: Option<unsafe extern "system" fn(Hwnd, u32, Wparam, Lparam) -> Lresult>,
        cb_cls_extra: i32,
        cb_wnd_extra: i32,
        h_instance: Hinstance,
        h_icon: *mut c_void,
        h_cursor: *mut c_void,
        h_br_background: *mut c_void,
        lpsz_menu_name: *const u16,
        lpsz_class_name: *const u16,
        h_icon_sm: *mut c_void,
    }

    #[link(name = "user32")]
    extern "system" {
        fn CreateWindowExW(
            dw_ex_style: u32,
            lp_class_name: *const u16,
            lp_window_name: *const u16,
            dw_style: u32,
            x: i32,
            y: i32,
            n_width: i32,
            n_height: i32,
            h_wnd_parent: Hwnd,
            h_menu: Hmenu,
            h_instance: Hinstance,
            lp_param: *mut c_void,
        ) -> Hwnd;
        fn DefWindowProcW(h_wnd: Hwnd, msg: u32, w_param: Wparam, l_param: Lparam) -> Lresult;
        fn DestroyWindow(h_wnd: Hwnd) -> Bool;
        fn EnumChildWindows(
            h_wnd_parent: Hwnd,
            lp_enum_func: Option<unsafe extern "system" fn(Hwnd, Lparam) -> Bool>,
            l_param: Lparam,
        ) -> Bool;
        fn EnumWindows(
            lp_enum_func: Option<unsafe extern "system" fn(Hwnd, Lparam) -> Bool>,
            l_param: Lparam,
        ) -> Bool;
        fn GetClassNameW(h_wnd: Hwnd, lp_class_name: *mut u16, n_max_count: i32) -> i32;
        fn GetModuleHandleW(lp_module_name: *const u16) -> Hinstance;
        fn GetParent(h_wnd: Hwnd) -> Hwnd;
        fn GetWindowLongPtrW(h_wnd: Hwnd, n_index: i32) -> isize;
        fn GetWindowThreadProcessId(h_wnd: Hwnd, process_id: *mut u32) -> u32;
        fn RegisterClassExW(class: *const WndClassExW) -> Atom;
        fn SetParent(h_wnd_child: Hwnd, h_wnd_new_parent: Hwnd) -> Hwnd;
        fn SetWindowLongPtrW(h_wnd: Hwnd, n_index: i32, dw_new_long: isize) -> isize;
        fn SetWindowPos(
            h_wnd: Hwnd,
            h_wnd_insert_after: Hwnd,
            x: i32,
            y: i32,
            cx: i32,
            cy: i32,
            flags: u32,
        ) -> Bool;
        fn ShowWindow(h_wnd: Hwnd, n_cmd_show: i32) -> Bool;
        fn ValidateRect(h_wnd: Hwnd, lp_rect: *const c_void) -> Bool;
        fn GetMessageW(lp_msg: *mut Msg, h_wnd: Hwnd, w_msg_filter_min: u32, w_msg_filter_max: u32) -> Bool;
        fn TranslateMessage(lp_msg: *const Msg) -> Bool;
        fn DispatchMessageW(lp_msg: *const Msg) -> Lresult;
        fn PostMessageW(h_wnd: Hwnd, msg: u32, w_param: Wparam, l_param: Lparam) -> Bool;
        fn PostThreadMessageW(id_thread: u32, msg: u32, w_param: Wparam, l_param: Lparam) -> Bool;
        fn GetCurrentThreadId() -> u32;
    }

    #[link(name = "kernel32")]
    extern "system" {
        fn GetCurrentProcessId() -> u32;
    }

    #[repr(C)]
    struct Msg {
        hwnd: Hwnd,
        message: u32,
        w_param: Wparam,
        l_param: Lparam,
        time: u32,
        pt_x: i32,
        pt_y: i32,
    }

    const WM_QUIT: u32 = 0x0012;
    const WM_USER_SET_BOUNDS: u32 = 0x0400;
    const WM_USER_SET_VISIBLE: u32 = 0x0401;

    #[link(name = "gdi32")]
    extern "system" {
        fn GetStockObject(index: i32) -> *mut c_void;
    }

    static CLASS_REGISTERED: AtomicBool = AtomicBool::new(false);
    const CLASS_NAME: &[u16] = &[
        b'O' as u16, b'p' as u16, b'e' as u16, b'n' as u16, b'N' as u16, b'O' as u16, b'W' as u16,
        b'I' as u16, b'n' as u16, b't' as u16, b'e' as u16, b'r' as u16, b'n' as u16, b'a' as u16,
        b'l' as u16, b'V' as u16, b'i' as u16, b'd' as u16, b'e' as u16, b'o' as u16, 0,
    ];

    unsafe extern "system" fn wnd_proc(
        hwnd: Hwnd,
        msg: u32,
        w_param: Wparam,
        l_param: Lparam,
    ) -> Lresult {
        match msg {
            WM_USER_SET_BOUNDS => {
                let x = (l_param & 0xFFFF) as i16 as i32;
                let y = ((l_param >> 16) & 0xFFFF) as i16 as i32;
                let w = (w_param & 0xFFFF) as i32;
                let h = ((w_param >> 16) & 0xFFFF) as i32;
                let parent = GetParent(hwnd);
                if !parent.is_null() {
                    enable_clip_styles(parent, find_chromium_content_hwnd(parent));
                }
                SetWindowPos(
                    hwnd,
                    HWND_TOP,
                    x,
                    y,
                    w.max(2),
                    h.max(2),
                    SWP_NOACTIVATE | SWP_SHOWWINDOW | SWP_NOCOPYBITS | SWP_NOSENDCHANGING,
                );
                0
            }
            WM_USER_SET_VISIBLE => {
                let visible = w_param != 0;
                ShowWindow(hwnd, if visible { SW_SHOWNOACTIVATE } else { SW_HIDE });
                SetWindowPos(
                    hwnd,
                    HWND_TOP,
                    0,
                    0,
                    0,
                    0,
                    SWP_NOACTIVATE
                        | SWP_NOZORDER
                        | SWP_NOSIZE
                        | SWP_NOMOVE
                        | if visible {
                            SWP_SHOWWINDOW
                        } else {
                            SWP_HIDEWINDOW
                        },
                );
                0
            }
            // Activation / RawInput capture is handled by the chained platform
            // wndproc installed after create (see arm_internal_child_input).
            WM_MOUSEACTIVATE => MA_ACTIVATE,
            WM_ERASEBKGND => 1,
            WM_PAINT => {
                ValidateRect(hwnd, null());
                0
            }
            WM_DESTROY => 0,
            _ => DefWindowProcW(hwnd, msg, w_param, l_param),
        }
    }

    unsafe extern "system" fn collect_chromium_child(hwnd: Hwnd, l_param: Lparam) -> Bool {
        let out = &mut *(l_param as *mut Hwnd);
        if !out.is_null() {
            return 1;
        }
        let mut class_name = [0u16; 64];
        let len = GetClassNameW(hwnd, class_name.as_mut_ptr(), class_name.len() as i32);
        if len <= 0 {
            return 1;
        }
        // Chromium's Intermediate D3D / content HWND class names.
        let name: String = String::from_utf16_lossy(&class_name[..len as usize]);
        if name.contains("Intermediate D3D")
            || name.contains("Chrome_RenderWidgetHostHWND")
            || name.contains("Chrome_WidgetWin_")
        {
            *out = hwnd;
            return 0;
        }
        1
    }

    unsafe fn find_chromium_content_hwnd(parent: Hwnd) -> Option<Hwnd> {
        let mut found: Hwnd = null_mut();
        EnumChildWindows(
            parent,
            Some(collect_chromium_child),
            &mut found as *mut Hwnd as Lparam,
        );
        if found.is_null() {
            None
        } else {
            Some(found)
        }
    }

    unsafe extern "system" fn collect_gst_vulkan_child(hwnd: Hwnd, l_param: Lparam) -> Bool {
        let out = &mut *(l_param as *mut Hwnd);
        if !out.is_null() {
            return 1;
        }
        if class_name_is_gst_vulkan(hwnd) {
            *out = hwnd;
            return 0;
        }
        1
    }

    unsafe extern "system" fn collect_gst_vulkan_top_level(hwnd: Hwnd, l_param: Lparam) -> Bool {
        let state = &mut *(l_param as *mut (u32, Hwnd));
        if !state.1.is_null() {
            return 1;
        }
        let mut process_id = 0u32;
        GetWindowThreadProcessId(hwnd, &mut process_id);
        if process_id != state.0 {
            return 1;
        }
        if class_name_is_gst_vulkan(hwnd) {
            state.1 = hwnd;
            return 0;
        }
        1
    }

    unsafe fn class_name_is_gst_vulkan(hwnd: Hwnd) -> bool {
        let mut class_name = [0u16; 64];
        let len = GetClassNameW(hwnd, class_name.as_mut_ptr(), class_name.len() as i32);
        if len <= 0 {
            return false;
        }
        String::from_utf16_lossy(&class_name[..len as usize]) == "GSTVULKAN"
    }

    unsafe fn find_gst_vulkan_under_parent(parent: Hwnd) -> Option<Hwnd> {
        let mut found: Hwnd = null_mut();
        EnumChildWindows(
            parent,
            Some(collect_gst_vulkan_child),
            &mut found as *mut Hwnd as Lparam,
        );
        if found.is_null() {
            None
        } else {
            Some(found)
        }
    }

    unsafe fn find_process_gst_vulkan_window() -> Option<Hwnd> {
        let mut state = (GetCurrentProcessId(), null_mut::<c_void>());
        EnumWindows(
            Some(collect_gst_vulkan_top_level),
            &mut state as *mut (u32, Hwnd) as Lparam,
        );
        if state.1.is_null() {
            None
        } else {
            Some(state.1)
        }
    }

    /// Hide any top-level GSTVULKAN windows so they never float over Electron.
    pub(super) fn suppress_top_level_gst_vulkan_windows() {
        unsafe {
            let mut state = (GetCurrentProcessId(), 0u32);
            EnumWindows(
                Some(suppress_top_level_gst_vulkan),
                &mut state as *mut (u32, u32) as Lparam,
            );
        }
    }

    unsafe extern "system" fn suppress_top_level_gst_vulkan(hwnd: Hwnd, l_param: Lparam) -> Bool {
        let state = &mut *(l_param as *mut (u32, u32));
        let mut process_id = 0u32;
        GetWindowThreadProcessId(hwnd, &mut process_id);
        if process_id != state.0 || !class_name_is_gst_vulkan(hwnd) {
            return 1;
        }
        // Already embedded under some parent — leave alone.
        if !GetParent(hwnd).is_null() {
            return 1;
        }
        ShowWindow(hwnd, SW_HIDE);
        let ex = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
        let desired_ex = (ex | WS_EX_TOOLWINDOW as isize | WS_EX_NOACTIVATE as isize)
            & !(WS_EX_APPWINDOW as isize);
        if desired_ex != ex {
            SetWindowLongPtrW(hwnd, GWL_EXSTYLE, desired_ex);
        }
        SetWindowPos(
            hwnd,
            HWND_TOP,
            -32_000,
            -32_000,
            2,
            2,
            SWP_NOACTIVATE | SWP_HIDEWINDOW | SWP_NOSENDCHANGING,
        );
        state.1 = state.1.saturating_add(1);
        1
    }

    /// Reparent vulkansink's GSTVULKAN hwnd into our Internal child and size it.
    /// Keeps the window hidden while top-level so nothing floats over Electron.
    pub(super) fn embed_gst_vulkan_window(parent: Hwnd, width: i32, height: i32) -> bool {
        if parent.is_null() {
            return false;
        }
        let width = width.max(2);
        let height = height.max(2);
        unsafe {
            suppress_top_level_gst_vulkan_windows();

            let Some(vulkan) = find_gst_vulkan_under_parent(parent)
                .or_else(|| find_process_gst_vulkan_window())
            else {
                return false;
            };

            // Never show as a top-level window — hide first, then reparent, then show.
            if GetParent(vulkan) != parent {
                ShowWindow(vulkan, SW_HIDE);
                SetParent(vulkan, parent);
            }

            let style = GetWindowLongPtrW(vulkan, GWL_STYLE);
            let cleared = (WS_POPUP
                | WS_CAPTION
                | WS_THICKFRAME
                | WS_MINIMIZEBOX
                | WS_MAXIMIZEBOX
                | WS_SYSMENU
                | WS_BORDER) as isize;
            let desired = (style & !cleared)
                | (WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS | WS_CLIPCHILDREN) as isize;
            if desired != style {
                SetWindowLongPtrW(vulkan, GWL_STYLE, desired);
            }

            let ex = GetWindowLongPtrW(vulkan, GWL_EXSTYLE);
            let desired_ex = ex & !(WS_EX_APPWINDOW as isize | WS_EX_TOOLWINDOW as isize);
            if desired_ex != ex {
                SetWindowLongPtrW(vulkan, GWL_EXSTYLE, desired_ex);
            }

            SetWindowPos(
                vulkan,
                HWND_TOP,
                0,
                0,
                width,
                height,
                SWP_NOACTIVATE | SWP_SHOWWINDOW | SWP_FRAMECHANGED | SWP_NOCOPYBITS,
            );
            ShowWindow(vulkan, SW_SHOWNOACTIVATE);
            true
        }
    }

    unsafe fn enable_clip_styles(parent: Hwnd, chromium: Option<Hwnd>) {
        let parent_style = GetWindowLongPtrW(parent, GWL_STYLE);
        let desired_parent = parent_style | (WS_CLIPCHILDREN as isize);
        if desired_parent != parent_style {
            SetWindowLongPtrW(parent, GWL_STYLE, desired_parent);
        }
        if let Some(chrome) = chromium {
            let chrome_style = GetWindowLongPtrW(chrome, GWL_STYLE);
            let desired_chrome = chrome_style | (WS_CLIPSIBLINGS as isize);
            if desired_chrome != chrome_style {
                SetWindowLongPtrW(chrome, GWL_STYLE, desired_chrome);
            }
        }
    }

    fn ensure_class() -> Result<(), String> {
        if CLASS_REGISTERED.load(Ordering::SeqCst) {
            return Ok(());
        }

        unsafe {
            let instance = GetModuleHandleW(null());
            if instance.is_null() {
                return Err("GetModuleHandleW failed for internal renderer class.".to_owned());
            }

            let class = WndClassExW {
                cb_size: std::mem::size_of::<WndClassExW>() as u32,
                style: CS_HREDRAW | CS_VREDRAW | CS_OWNDC,
                lpfn_wnd_proc: Some(wnd_proc),
                cb_cls_extra: 0,
                cb_wnd_extra: 0,
                h_instance: instance,
                h_icon: null_mut(),
                h_cursor: null_mut(),
                h_br_background: GetStockObject(BLACK_BRUSH),
                lpsz_menu_name: null(),
                lpsz_class_name: CLASS_NAME.as_ptr(),
                h_icon_sm: null_mut(),
            };

            let atom = RegisterClassExW(&class);
            if atom == 0 {
                // Another thread may have won the race; treat as success if already registered.
                if !CLASS_REGISTERED.load(Ordering::SeqCst) {
                    // ERROR_CLASS_ALREADY_EXISTS == 1410
                    // Still mark registered so we do not loop.
                }
            }
            CLASS_REGISTERED.store(true, Ordering::SeqCst);
        }

        Ok(())
    }

    pub(super) struct ChildWindow {
        hwnd: usize,
        thread_id: u32,
        join: Option<std::thread::JoinHandle<()>>,
    }

    impl std::fmt::Debug for ChildWindow {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            f.debug_struct("ChildWindow")
                .field("hwnd", &self.hwnd)
                .field("thread_id", &self.thread_id)
                .finish()
        }
    }

    impl ChildWindow {
        pub(super) fn create(parent: usize, bounds: &NativeRenderRect) -> Result<Self, String> {
            ensure_class()?;
            let parent_hwnd = parent as Hwnd;
            if parent_hwnd.is_null() {
                return Err("Internal renderer parent HWND is null.".to_owned());
            }

            let (tx, rx) = std::sync::mpsc::channel::<Result<(usize, u32), String>>();
            let bounds = bounds.clone();
            let parent_handle = parent;
            let join = std::thread::Builder::new()
                .name("opennow-internal-video".to_owned())
                .spawn(move || {
                    let created =
                        unsafe { create_child_on_thread(parent_handle as Hwnd, &bounds) };
                    match created {
                        Ok(hwnd) => {
                            let thread_id = unsafe { GetCurrentThreadId() };
                            let _ = tx.send(Ok((hwnd as usize, thread_id)));
                            run_message_loop(hwnd);
                        }
                        Err(error) => {
                            let _ = tx.send(Err(error));
                        }
                    }
                })
                .map_err(|error| format!("Failed to spawn internal renderer UI thread: {error}"))?;

            let (hwnd, thread_id) = rx
                .recv()
                .map_err(|_| "Internal renderer UI thread exited before creating HWND.".to_owned())?
                ?;

            Ok(Self {
                hwnd,
                thread_id,
                join: Some(join),
            })
        }

        pub(super) fn hwnd(&self) -> usize {
            self.hwnd
        }

        pub(super) fn set_bounds(&self, bounds: &NativeRenderRect) -> Result<(), String> {
            if self.hwnd == 0 {
                return Ok(());
            }
            // Pack x/y into lParam (signed 16-bit each) and w/h into wParam.
            let x = bounds.x.clamp(i16::MIN as i32, i16::MAX as i32) as u16 as u32;
            let y = bounds.y.clamp(i16::MIN as i32, i16::MAX as i32) as u16 as u32;
            let l_param = ((y as Lparam) << 16) | (x as Lparam);
            let w = bounds.width.max(2).min(u16::MAX as i32) as u16 as usize;
            let h = bounds.height.max(2).min(u16::MAX as i32) as u16 as usize;
            let w_param = (h << 16) | w;
            unsafe {
                if PostMessageW(
                    self.hwnd as Hwnd,
                    WM_USER_SET_BOUNDS,
                    w_param,
                    l_param,
                ) == 0
                {
                    return Err("PostMessageW(SET_BOUNDS) failed for internal renderer.".to_owned());
                }
            }
            Ok(())
        }

        pub(super) fn set_visible(&self, visible: bool) -> Result<(), String> {
            if self.hwnd == 0 {
                return Ok(());
            }
            unsafe {
                if PostMessageW(
                    self.hwnd as Hwnd,
                    WM_USER_SET_VISIBLE,
                    if visible { 1 } else { 0 },
                    0,
                ) == 0
                {
                    return Err("PostMessageW(SET_VISIBLE) failed for internal renderer.".to_owned());
                }
            }
            Ok(())
        }

        pub(super) fn destroy(&mut self) {
            if self.hwnd != 0 {
                unsafe {
                    // DestroyWindow posts WM_DESTROY; then quit the UI thread loop.
                    DestroyWindow(self.hwnd as Hwnd);
                    PostThreadMessageW(self.thread_id, WM_QUIT, 0, 0);
                }
                self.hwnd = 0;
            }
            if let Some(join) = self.join.take() {
                let _ = join.join();
            }
        }
    }

    impl Drop for ChildWindow {
        fn drop(&mut self) {
            self.destroy();
        }
    }

    unsafe fn create_child_on_thread(parent_hwnd: Hwnd, bounds: &NativeRenderRect) -> Result<Hwnd, String> {
        let instance = GetModuleHandleW(null());
        let chromium = find_chromium_content_hwnd(parent_hwnd);
        enable_clip_styles(parent_hwnd, chromium);

        // Sibling above Intermediate D3D for present. Clip styles punch a hole
        // so Chromium does not paint over us. Input uses RawInput on this HWND
        // (Electron click-through is unreliable across process boundaries).
        // Do not use WS_EX_NOACTIVATE: the first click must activate so RawInput
        // capture can arm (same as the floating external renderer window).
        let hwnd = CreateWindowExW(
            0,
            CLASS_NAME.as_ptr(),
            null(),
            WS_CHILD | WS_CLIPSIBLINGS | WS_VISIBLE,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            parent_hwnd,
            null_mut(),
            instance,
            null_mut(),
        );

        if hwnd.is_null() {
            return Err("CreateWindowExW failed for internal renderer child HWND.".to_owned());
        }

        SetWindowPos(
            hwnd,
            HWND_TOP,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            SWP_NOACTIVATE | SWP_SHOWWINDOW | SWP_NOCOPYBITS | SWP_NOSENDCHANGING,
        );

        Ok(hwnd)
    }

    fn run_message_loop(_hwnd: Hwnd) {
        unsafe {
            let mut msg = Msg {
                hwnd: null_mut(),
                message: 0,
                w_param: 0,
                l_param: 0,
                time: 0,
                pt_x: 0,
                pt_y: 0,
            };
            // D3D11 videosink present requires a live message pump on the
            // thread that owns the child HWND. Without this, sink stays at 0 fps.
            while GetMessageW(&mut msg, null_mut(), 0, 0) > 0 {
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }
        }
    }

    // ChildWindow owns create/bounds/visible/destroy + UI thread lifecycle.
}

#[cfg(target_os = "macos")]
mod macos_child {
    use crate::protocol::NativeRenderRect;
    use std::ffi::c_void;
    use std::sync::OnceLock;

    pub(super) type NsViewPtr = *mut c_void;

    #[link(name = "objc")]
    extern "C" {
        fn sel_registerName(name: *const i8) -> *const c_void;
        fn objc_getClass(name: *const i8) -> *mut c_void;
        fn objc_msgSend();
    }

    // objc_msgSend is variadic; call via transmute per selector signature.
    type MsgSend0 = unsafe extern "C" fn(*mut c_void, *const c_void) -> *mut c_void;
    type MsgSend1Ptr = unsafe extern "C" fn(*mut c_void, *const c_void, *mut c_void) -> *mut c_void;
    type MsgSendRect = unsafe extern "C" fn(*mut c_void, *const c_void, NsRect) -> *mut c_void;
    type MsgSendBool = unsafe extern "C" fn(*mut c_void, *const c_void, bool);
    type MsgSendVoidPtr = unsafe extern "C" fn(*mut c_void, *const c_void, *mut c_void);

    #[repr(C)]
    #[derive(Clone, Copy)]
    struct NsPoint {
        x: f64,
        y: f64,
    }

    #[repr(C)]
    #[derive(Clone, Copy)]
    struct NsSize {
        width: f64,
        height: f64,
    }

    #[repr(C)]
    #[derive(Clone, Copy)]
    struct NsRect {
        origin: NsPoint,
        size: NsSize,
    }

    struct Selectors {
        alloc: *const c_void,
        init_with_frame: *const c_void,
        add_subview: *const c_void,
        set_frame: *const c_void,
        set_hidden: *const c_void,
        set_wants_layer: *const c_void,
        set_autoresizes_subviews: *const c_void,
        release: *const c_void,
        bounds: *const c_void,
    }

    // Objective-C selectors are process-global, immutable runtime tokens returned by
    // sel_registerName. Sharing these non-null tokens does not share pointed-to data.
    unsafe impl Send for Selectors {}
    unsafe impl Sync for Selectors {}

    fn selectors() -> &'static Selectors {
        static SELECTORS: OnceLock<Selectors> = OnceLock::new();
        SELECTORS.get_or_init(|| unsafe {
            Selectors {
                alloc: sel_registerName(b"alloc\0".as_ptr().cast()),
                init_with_frame: sel_registerName(b"initWithFrame:\0".as_ptr().cast()),
                add_subview: sel_registerName(b"addSubview:\0".as_ptr().cast()),
                set_frame: sel_registerName(b"setFrame:\0".as_ptr().cast()),
                set_hidden: sel_registerName(b"setHidden:\0".as_ptr().cast()),
                set_wants_layer: sel_registerName(b"setWantsLayer:\0".as_ptr().cast()),
                set_autoresizes_subviews: sel_registerName(
                    b"setAutoresizesSubviews:\0".as_ptr().cast(),
                ),
                release: sel_registerName(b"release\0".as_ptr().cast()),
                bounds: sel_registerName(b"bounds\0".as_ptr().cast()),
            }
        })
    }

    fn msg_send_0(obj: *mut c_void, sel: *const c_void) -> *mut c_void {
        unsafe {
            let f: MsgSend0 = std::mem::transmute(objc_msgSend as *const ());
            f(obj, sel)
        }
    }

    fn parent_bounds(parent: NsViewPtr) -> NsRect {
        // bounds returns NSRect by value; on x86_64/arm64 macOS this is returned in registers /
        // as a struct. Use a dedicated trampoline via objc_msgSend_stret is obsolete on arm64.
        type MsgSendBounds = unsafe extern "C" fn(*mut c_void, *const c_void) -> NsRect;
        unsafe {
            let f: MsgSendBounds = std::mem::transmute(objc_msgSend as *const ());
            f(parent, selectors().bounds)
        }
    }

    fn to_ns_rect(bounds: &NativeRenderRect, parent: NsViewPtr) -> NsRect {
        // Electron reports top-left client coords in device pixels. NSView is bottom-left
        // in points; approximate by treating incoming coords as points relative to parent
        // bounds (Electron already DPI-scales the rect we receive).
        let parent_bounds = parent_bounds(parent);
        let height = bounds.height as f64;
        let y = parent_bounds.size.height - (bounds.y as f64) - height;
        NsRect {
            origin: NsPoint {
                x: bounds.x as f64,
                y: y.max(0.0),
            },
            size: NsSize {
                width: bounds.width as f64,
                height,
            },
        }
    }

    pub(super) fn create_child(parent: usize, bounds: &NativeRenderRect) -> Result<NsViewPtr, String> {
        let parent_view = parent as NsViewPtr;
        if parent_view.is_null() {
            return Err("Internal renderer parent NSView is null.".to_owned());
        }

        unsafe {
            let class = objc_getClass(b"NSView\0".as_ptr().cast());
            if class.is_null() {
                return Err("objc_getClass(NSView) failed.".to_owned());
            }

            let sels = selectors();
            let alloc = msg_send_0(class, sels.alloc);
            if alloc.is_null() {
                return Err("NSView alloc failed.".to_owned());
            }

            let frame = to_ns_rect(bounds, parent_view);
            let init: MsgSendRect = std::mem::transmute(objc_msgSend as *const ());
            let view = init(alloc, sels.init_with_frame, frame);
            if view.is_null() {
                return Err("NSView initWithFrame failed.".to_owned());
            }

            let set_bool: MsgSendBool = std::mem::transmute(objc_msgSend as *const ());
            set_bool(view, sels.set_wants_layer, true);
            set_bool(view, sels.set_autoresizes_subviews, false);
            set_bool(view, sels.set_hidden, false);

            let add: MsgSend1Ptr = std::mem::transmute(objc_msgSend as *const ());
            add(parent_view, sels.add_subview, view);

            Ok(view)
        }
    }

    pub(super) fn set_bounds(view: NsViewPtr, bounds: &NativeRenderRect) -> Result<(), String> {
        if view.is_null() {
            return Ok(());
        }
        // Parent is needed for Y-flip; store is not available here — use frame in parent
        // coordinates assuming the same parent. Read superview.
        unsafe {
            let sels = selectors();
            let superview_sel = sel_registerName(b"superview\0".as_ptr().cast());
            let parent = msg_send_0(view, superview_sel);
            let frame = if parent.is_null() {
                NsRect {
                    origin: NsPoint {
                        x: bounds.x as f64,
                        y: bounds.y as f64,
                    },
                    size: NsSize {
                        width: bounds.width as f64,
                        height: bounds.height as f64,
                    },
                }
            } else {
                to_ns_rect(bounds, parent)
            };
            let set_frame: MsgSendRect = std::mem::transmute(objc_msgSend as *const ());
            set_frame(view, sels.set_frame, frame);
        }
        Ok(())
    }

    pub(super) fn set_visible(view: NsViewPtr, visible: bool) -> Result<(), String> {
        if view.is_null() {
            return Ok(());
        }
        unsafe {
            let set_bool: MsgSendBool = std::mem::transmute(objc_msgSend as *const ());
            set_bool(view, selectors().set_hidden, !visible);
        }
        Ok(())
    }

    pub(super) fn destroy(view: NsViewPtr) {
        if view.is_null() {
            return;
        }
        unsafe {
            let sels = selectors();
            let remove_sel = sel_registerName(b"removeFromSuperview\0".as_ptr().cast());
            let _ = msg_send_0(view, remove_sel);
            let _ = msg_send_0(view, sels.release);
        }
    }
}

#[cfg(target_os = "linux")]
mod linux_child {
    use crate::protocol::NativeRenderRect;
    use std::ffi::c_void;
    use std::ptr::null_mut;

    #[derive(Debug)]
    pub(super) struct XWindow {
        pub(super) display: usize,
        pub(super) window: u64,
        parent: u64,
    }

    // Minimal X11 FFI — enough to create a child window for GstVideoOverlay.
    #[repr(C)]
    struct XSetWindowAttributes {
        background_pixmap: u64,
        background_pixel: u64,
        border_pixmap: u64,
        border_pixel: u64,
        bit_gravity: i32,
        win_gravity: i32,
        backing_store: i32,
        backing_planes: u64,
        backing_pixel: u64,
        save_under: i32,
        event_mask: i64,
        do_not_propagate_mask: i64,
        override_redirect: i32,
        colormap: u64,
        cursor: u64,
    }

    #[link(name = "X11")]
    extern "C" {
        fn XOpenDisplay(display_name: *const i8) -> *mut c_void;
        fn XDefaultScreen(display: *mut c_void) -> i32;
        fn XDefaultVisual(display: *mut c_void, screen: i32) -> *mut c_void;
        fn XDefaultDepth(display: *mut c_void, screen: i32) -> i32;
        fn XDefaultColormap(display: *mut c_void, screen: i32) -> u64;
        fn XBlackPixel(display: *mut c_void, screen: i32) -> u64;
        fn XCreateWindow(
            display: *mut c_void,
            parent: u64,
            x: i32,
            y: i32,
            width: u32,
            height: u32,
            border_width: u32,
            depth: i32,
            class: u32,
            visual: *mut c_void,
            valuemask: u64,
            attributes: *mut XSetWindowAttributes,
        ) -> u64;
        fn XMapWindow(display: *mut c_void, window: u64) -> i32;
        fn XUnmapWindow(display: *mut c_void, window: u64) -> i32;
        fn XMoveResizeWindow(
            display: *mut c_void,
            window: u64,
            x: i32,
            y: i32,
            width: u32,
            height: u32,
        ) -> i32;
        fn XRaiseWindow(display: *mut c_void, window: u64) -> i32;
        fn XClearWindow(display: *mut c_void, window: u64) -> i32;
        fn XDestroyWindow(display: *mut c_void, window: u64) -> i32;
        fn XFlush(display: *mut c_void) -> i32;
        fn XSync(display: *mut c_void, discard: i32) -> i32;
        fn XSelectInput(display: *mut c_void, window: u64, event_mask: i64) -> i32;
    }

    const INPUT_OUTPUT: u32 = 1;
    const CW_BACK_PIXEL: u64 = 0x0002;
    const CW_EVENT_MASK: u64 = 0x0800;
    const CW_COLORMAP: u64 = 0x2000;
    // Exposure + StructureNotify so GstVideoOverlay can redraw after map/resize.
    // No pointer/keyboard masks — Electron owns input in internal mode.
    const EXPOSURE_MASK: i64 = 0x0000_8000;
    const STRUCTURE_NOTIFY_MASK: i64 = 0x0002_0000;
    const EVENT_MASK: i64 = EXPOSURE_MASK | STRUCTURE_NOTIFY_MASK;

    fn wayland_session_active() -> bool {
        std::env::var_os("WAYLAND_DISPLAY")
            .filter(|value| !value.is_empty())
            .is_some()
    }

    fn x11_unavailable_message() -> String {
        if wayland_session_active() {
            "Native internal renderer requires X11 or XWayland. Pure Wayland embedding is not supported yet — launch under X11, or set GDK_BACKEND=x11 / use an XWayland session."
                .to_owned()
        } else {
            "XOpenDisplay failed; native internal renderer requires a working X11 display (DISPLAY unset or unreachable)."
                .to_owned()
        }
    }

    pub(super) fn create_child(parent: usize, bounds: &NativeRenderRect) -> Result<XWindow, String> {
        unsafe {
            let display = XOpenDisplay(null_mut());
            if display.is_null() {
                return Err(x11_unavailable_message());
            }

            let screen = XDefaultScreen(display);
            let visual = XDefaultVisual(display, screen);
            let depth = XDefaultDepth(display, screen);
            let mut attrs = XSetWindowAttributes {
                background_pixmap: 0,
                background_pixel: XBlackPixel(display, screen),
                border_pixmap: 0,
                border_pixel: 0,
                bit_gravity: 0,
                win_gravity: 0,
                backing_store: 0,
                backing_planes: 0,
                backing_pixel: 0,
                save_under: 0,
                event_mask: EVENT_MASK,
                do_not_propagate_mask: 0,
                override_redirect: 0,
                colormap: XDefaultColormap(display, screen),
                cursor: 0,
            };

            let window = XCreateWindow(
                display,
                parent as u64,
                bounds.x,
                bounds.y,
                bounds.width.max(2) as u32,
                bounds.height.max(2) as u32,
                0,
                depth,
                INPUT_OUTPUT,
                visual,
                CW_BACK_PIXEL | CW_EVENT_MASK | CW_COLORMAP,
                &mut attrs,
            );

            if window == 0 {
                return Err("XCreateWindow failed for internal renderer child.".to_owned());
            }

            XSelectInput(display, window, EVENT_MASK);
            XMapWindow(display, window);
            XRaiseWindow(display, window);
            // Ensure the child is mapped and sized before GstVideoOverlay binds.
            XSync(display, 0);
            XClearWindow(display, window);
            XFlush(display);

            Ok(XWindow {
                display: display as usize,
                window,
                parent: parent as u64,
            })
        }
    }

    pub(super) fn set_bounds(window: &XWindow, bounds: &NativeRenderRect) -> Result<(), String> {
        if window.display == 0 || window.window == 0 {
            return Ok(());
        }
        let display = window.display as *mut c_void;
        unsafe {
            XMoveResizeWindow(
                display,
                window.window,
                bounds.x,
                bounds.y,
                bounds.width.max(2) as u32,
                bounds.height.max(2) as u32,
            );
            XRaiseWindow(display, window.window);
            XSync(display, 0);
            XFlush(display);
        }
        let _ = window.parent;
        Ok(())
    }

    pub(super) fn set_visible(window: &XWindow, visible: bool) -> Result<(), String> {
        if window.display == 0 || window.window == 0 {
            return Ok(());
        }
        let display = window.display as *mut c_void;
        unsafe {
            if visible {
                XMapWindow(display, window.window);
                XRaiseWindow(display, window.window);
                XClearWindow(display, window.window);
            } else {
                XUnmapWindow(display, window.window);
            }
            XSync(display, 0);
            XFlush(display);
        }
        Ok(())
    }

    pub(super) fn destroy(window: &mut XWindow) {
        if window.display == 0 {
            return;
        }
        let display = window.display as *mut c_void;
        unsafe {
            if window.window != 0 {
                XDestroyWindow(display, window.window);
                window.window = 0;
            }
            // Intentionally keep the display open for process lifetime; closing here
            // can race with other X users in the same process.
            XFlush(display);
        }
    }
}
