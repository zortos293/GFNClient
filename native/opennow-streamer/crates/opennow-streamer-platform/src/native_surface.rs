use opennow_streamer_protocol::RenderSurfaceRect;
use sdl2::video::Window;

pub(crate) struct NativeSurface {
    inner: platform::Surface,
}

impl NativeSurface {
    pub(crate) fn new(window: &Window) -> Result<Self, String> {
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            platform::Surface::new(window)
        }))
        .map_err(|_| "SDL could not expose a native presentation handle".to_owned())?
        .map(|inner| Self { inner })
    }

    pub(crate) fn attach_and_show(
        &mut self,
        parent_handle: &str,
        rect: RenderSurfaceRect,
        scale: f32,
    ) -> Result<(), String> {
        self.inner.attach_and_show(parent_handle, rect, scale)
    }

    pub(crate) fn hide(&mut self) {
        self.inner.hide();
    }
}

fn parse_handle(value: &str) -> Result<usize, String> {
    let trimmed = value.trim();
    let parsed = if let Some(hex) = trimmed.strip_prefix("0x") {
        usize::from_str_radix(hex, 16)
    } else {
        trimmed.parse()
    };
    parsed
        .ok()
        .filter(|handle| *handle != 0)
        .ok_or_else(|| format!("invalid Electron native window handle: {value}"))
}

fn physical_rect(rect: RenderSurfaceRect, scale: f32) -> (i32, i32, u32, u32) {
    let scale = if scale.is_finite() {
        scale.clamp(0.25, 8.0)
    } else {
        1.0
    };
    (
        (rect.x as f32 * scale).round() as i32,
        (rect.y as f32 * scale).round() as i32,
        ((rect.width as f32 * scale).round() as u32).max(2),
        ((rect.height as f32 * scale).round() as u32).max(2),
    )
}

#[cfg(target_os = "windows")]
mod platform {
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};
    use windows_sys::Win32::Foundation::{GetLastError, SetLastError};
    use windows_sys::Win32::UI::WindowsAndMessaging::{
        GWL_EXSTYLE, GWL_STYLE, GetWindowLongPtrW, HWND_BOTTOM, SW_HIDE, SWP_NOACTIVATE,
        SWP_SHOWWINDOW, SetParent, SetWindowLongPtrW, SetWindowPos, ShowWindow, WS_CHILD,
        WS_DISABLED, WS_EX_NOACTIVATE, WS_EX_TRANSPARENT, WS_VISIBLE,
    };

    use super::*;

    pub(crate) struct Surface {
        child: windows_sys::Win32::Foundation::HWND,
        parent: windows_sys::Win32::Foundation::HWND,
    }

    impl Surface {
        pub(crate) fn new(window: &Window) -> Result<Self, String> {
            let child = match window
                .window_handle()
                .map_err(|error| format!("SDL Win32 handle unavailable: {error}"))?
                .as_raw()
            {
                RawWindowHandle::Win32(handle) => handle.hwnd.get() as _,
                _ => return Err("SDL did not create a Win32 presentation window".to_owned()),
            };
            Ok(Self {
                child,
                parent: std::ptr::null_mut(),
            })
        }

        pub(crate) fn attach_and_show(
            &mut self,
            parent_handle: &str,
            rect: RenderSurfaceRect,
            scale: f32,
        ) -> Result<(), String> {
            let parent = parse_handle(parent_handle)? as _;
            let (x, y, width, height) = physical_rect(rect, scale);
            unsafe {
                if self.parent != parent {
                    SetLastError(0);
                    if SetParent(self.child, parent).is_null() && GetLastError() != 0 {
                        return Err("failed to parent SDL video surface to Electron".to_owned());
                    }
                    self.parent = parent;
                    let style = GetWindowLongPtrW(self.child, GWL_STYLE) as u32;
                    SetWindowLongPtrW(
                        self.child,
                        GWL_STYLE,
                        ((style & !WS_VISIBLE) | WS_CHILD | WS_DISABLED) as isize,
                    );
                    let extended = GetWindowLongPtrW(self.child, GWL_EXSTYLE) as u32;
                    SetWindowLongPtrW(
                        self.child,
                        GWL_EXSTYLE,
                        (extended | WS_EX_NOACTIVATE | WS_EX_TRANSPARENT) as isize,
                    );
                }
                if SetWindowPos(
                    self.child,
                    HWND_BOTTOM,
                    x,
                    y,
                    width as i32,
                    height as i32,
                    SWP_NOACTIVATE | SWP_SHOWWINDOW,
                ) == 0
                {
                    return Err("failed to position Electron child video surface".to_owned());
                }
            }
            Ok(())
        }

        pub(crate) fn hide(&mut self) {
            unsafe {
                ShowWindow(self.child, SW_HIDE);
            }
        }
    }
}

#[cfg(target_os = "linux")]
mod platform {
    use raw_window_handle::{HasDisplayHandle, HasWindowHandle, RawDisplayHandle, RawWindowHandle};
    use x11_dl::xlib;

    use super::*;

    pub(crate) struct Surface {
        xlib: xlib::Xlib,
        display: *mut xlib::Display,
        child: xlib::Window,
        parent: xlib::Window,
    }

    impl Surface {
        pub(crate) fn new(window: &Window) -> Result<Self, String> {
            let child = match window
                .window_handle()
                .map_err(|error| format!("SDL X11 window handle unavailable: {error}"))?
                .as_raw()
            {
                RawWindowHandle::Xlib(handle) => handle.window,
                RawWindowHandle::Wayland(_) => {
                    return Err(
                        "native Electron surface embedding requires an X11/XWayland session"
                            .to_owned(),
                    );
                }
                _ => return Err("SDL did not create an X11 presentation window".to_owned()),
            };
            let display = match window
                .display_handle()
                .map_err(|error| format!("SDL X11 display handle unavailable: {error}"))?
                .as_raw()
            {
                RawDisplayHandle::Xlib(handle) => handle
                    .display
                    .map(|display| display.as_ptr())
                    .unwrap_or(std::ptr::null_mut()),
                _ => std::ptr::null_mut(),
            };
            if display.is_null() {
                return Err("SDL X11 display pointer is null".to_owned());
            }
            Ok(Self {
                xlib: xlib::Xlib::open()
                    .map_err(|error| format!("failed to load X11 embedding API: {error}"))?,
                display: display.cast(),
                child,
                parent: 0,
            })
        }

        pub(crate) fn attach_and_show(
            &mut self,
            parent_handle: &str,
            rect: RenderSurfaceRect,
            scale: f32,
        ) -> Result<(), String> {
            let parent = parse_handle(parent_handle)? as xlib::Window;
            let (x, y, width, height) = physical_rect(rect, scale);
            unsafe {
                if self.parent != parent {
                    (self.xlib.XUnmapWindow)(self.display, self.child);
                    (self.xlib.XReparentWindow)(self.display, self.child, parent, x, y);
                    (self.xlib.XSelectInput)(self.display, self.child, xlib::StructureNotifyMask);
                    self.parent = parent;
                }
                (self.xlib.XMoveResizeWindow)(self.display, self.child, x, y, width, height);
                (self.xlib.XLowerWindow)(self.display, self.child);
                (self.xlib.XMapWindow)(self.display, self.child);
                (self.xlib.XFlush)(self.display);
            }
            Ok(())
        }

        pub(crate) fn hide(&mut self) {
            unsafe {
                (self.xlib.XUnmapWindow)(self.display, self.child);
                (self.xlib.XFlush)(self.display);
            }
        }
    }
}

#[cfg(target_os = "macos")]
mod platform {
    use objc2::rc::Retained;
    use objc2_app_kit::{NSView, NSWindow, NSWindowOrderingMode};
    use objc2_foundation::{NSPoint, NSRect, NSSize};
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};

    use super::*;

    pub(crate) struct Surface {
        child: Retained<NSWindow>,
        parent: Option<Retained<NSWindow>>,
    }

    impl Surface {
        pub(crate) fn new(window: &Window) -> Result<Self, String> {
            let child_view = match window
                .window_handle()
                .map_err(|error| format!("SDL AppKit handle unavailable: {error}"))?
                .as_raw()
            {
                RawWindowHandle::AppKit(handle) => unsafe {
                    &*handle.ns_view.as_ptr().cast::<NSView>()
                },
                _ => return Err("SDL did not create an AppKit presentation window".to_owned()),
            };
            let child = child_view
                .window()
                .ok_or_else(|| "SDL AppKit view has no window".to_owned())?;
            child.setIgnoresMouseEvents(true);
            Ok(Self {
                child,
                parent: None,
            })
        }

        pub(crate) fn attach_and_show(
            &mut self,
            parent_handle: &str,
            rect: RenderSurfaceRect,
            _scale: f32,
        ) -> Result<(), String> {
            let parent_pointer = parse_handle(parent_handle)? as *const NSView;
            let parent_view = unsafe { &*parent_pointer };
            let parent_window = parent_view
                .window()
                .ok_or_else(|| "Electron AppKit view has no window".to_owned())?;
            if self
                .parent
                .as_ref()
                .is_none_or(|current| current != &parent_window)
            {
                if let Some(current) = self.parent.take() {
                    current.removeChildWindow(&self.child);
                }
                unsafe {
                    parent_window.addChildWindow_ordered(&self.child, NSWindowOrderingMode::Above);
                }
                self.parent = Some(parent_window.clone());
            }
            let bounds = parent_view.bounds();
            let local = NSRect::new(
                NSPoint::new(
                    rect.x as f64,
                    bounds.size.height - rect.y as f64 - rect.height as f64,
                ),
                NSSize::new(rect.width as f64, rect.height as f64),
            );
            let parent_window_rect = parent_view.convertRect_toView(local, None);
            let screen_rect = parent_window.convertRectToScreen(parent_window_rect);
            self.child.setFrame_display(screen_rect, true);
            self.child.orderFront(None);
            Ok(())
        }

        pub(crate) fn hide(&mut self) {
            self.child.orderOut(None);
        }
    }

    impl Drop for Surface {
        fn drop(&mut self) {
            if let Some(parent) = self.parent.take() {
                parent.removeChildWindow(&self.child);
            }
        }
    }
}

#[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
mod platform {
    use super::*;

    pub(crate) struct Surface;

    impl Surface {
        pub(crate) fn new(_window: &Window) -> Result<Self, String> {
            Err("native presentation is unsupported on this operating system".to_owned())
        }

        pub(crate) fn attach_and_show(
            &mut self,
            _parent_handle: &str,
            _rect: RenderSurfaceRect,
            _scale: f32,
        ) -> Result<(), String> {
            Err("native presentation is unsupported on this operating system".to_owned())
        }

        pub(crate) fn hide(&mut self) {}
    }
}
