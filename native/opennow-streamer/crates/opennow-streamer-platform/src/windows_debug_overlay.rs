use raw_window_handle::{HasWindowHandle, RawWindowHandle};
use sdl2::pixels::PixelFormatEnum;
use sdl2::render::WindowCanvas;
use sdl2::video::{Window, WindowPos};
use windows_sys::Win32::Foundation::{HWND, POINT, RECT};
use windows_sys::Win32::Graphics::Gdi::{
    ClientToScreen, CreateRoundRectRgn, DeleteObject, SetWindowRgn,
};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    GWL_EXSTYLE, GWLP_HWNDPARENT, GetClientRect, GetWindowLongPtrW, HWND_TOPMOST, LWA_ALPHA,
    LWA_COLORKEY, SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOSIZE, SWP_SHOWWINDOW,
    SetLayeredWindowAttributes, SetWindowLongPtrW, SetWindowPos, WS_EX_LAYERED, WS_EX_NOACTIVATE,
    WS_EX_TOOLWINDOW, WS_EX_TRANSPARENT,
};

use crate::media::{MediaStreamConfig, StatsOverlayPosition};
use crate::native_stats_overlay::{NativeStatsOverlay, OverlayFrame, OverlayMode};

const EDGE_MARGIN: i32 = 24;
const COLOR_KEY: [u8; 3] = [255, 0, 255];
const COLOR_KEY_REF: u32 = 0x00ff_00ff;
const STATS_OPACITY: u8 = 245;
const DIM_SAMPLES: u8 = 28;
const BAYER_8X8: [[u8; 8]; 8] = [
    [0, 32, 8, 40, 2, 34, 10, 42],
    [48, 16, 56, 24, 50, 18, 58, 26],
    [12, 44, 4, 36, 14, 46, 6, 38],
    [60, 28, 52, 20, 62, 30, 54, 22],
    [3, 35, 11, 43, 1, 33, 9, 41],
    [51, 19, 59, 27, 49, 17, 57, 25],
    [15, 47, 7, 39, 13, 45, 5, 37],
    [63, 31, 55, 23, 61, 29, 53, 21],
];

/// One persistent native HUD surface owns F3, Ctrl+G, and Ctrl+Shift+Q.
///
/// Reusing the proven F3 surface is important: creating a second owned SDL
/// window for a guide caused Win32/SDL focus churn and made the guide disappear
/// immediately. Modal views now expand this same HUD over the stream, with a
/// color-keyed ordered-dither backdrop so video continues presenting beneath it.
pub(crate) struct NativeDebugOverlay {
    canvas: WindowCanvas,
    parent: HWND,
    stats: NativeStatsOverlay,
    menu_visible: bool,
    stop_confirmation_visible: bool,
    stats_render_scale: f32,
    menu_render_scale: f32,
}

impl NativeDebugOverlay {
    pub(crate) fn new(
        video: &sdl2::VideoSubsystem,
        parent: isize,
        stream: MediaStreamConfig,
        decoder: &'static str,
    ) -> Result<Self, String> {
        let (width, height) = OverlayMode::Minimal
            .size()
            .expect("minimal overlay has a size");
        let parent = parent as HWND;
        let canvas = overlay_canvas(video, parent, "OpenNOW Stream Overlay", width, height)?;
        Ok(Self {
            canvas,
            parent,
            stats: NativeStatsOverlay::new(stream, decoder),
            menu_visible: false,
            stop_confirmation_visible: false,
            stats_render_scale: 1.0,
            menu_render_scale: 1.0,
        })
    }

    pub(crate) fn toggle(&mut self) {
        self.stats.toggle();
        if !self.modal_visible() {
            self.restore_stats_or_hide();
        }
    }

    pub(crate) fn toggle_menu(&mut self) {
        if self.stop_confirmation_visible {
            self.cancel_stop_confirmation();
            return;
        }
        self.menu_visible = !self.menu_visible;
        if self.menu_visible {
            self.show_menu();
            eprintln!("Native Ctrl+G stream overlay opened without pausing the stream");
        } else {
            self.restore_stats_or_hide();
            eprintln!("Native Ctrl+G stream overlay closed");
        }
    }

    pub(crate) const fn stop_confirmation_visible(&self) -> bool {
        self.stop_confirmation_visible
    }

    pub(crate) fn owns_window(&self, handle: isize) -> bool {
        handle != 0
            && (self.parent as isize == handle
                || hwnd(self.canvas.window()).is_ok_and(|window| window as isize == handle))
    }

    pub(crate) fn show_stop_confirmation(&mut self) {
        self.stop_confirmation_visible = true;
        self.show_confirmation();
        eprintln!("Native stop-stream confirmation overlay opened");
    }

    pub(crate) fn cancel_stop_confirmation(&mut self) {
        self.stop_confirmation_visible = false;
        if self.menu_visible {
            self.show_menu();
        } else {
            self.restore_stats_or_hide();
        }
        eprintln!("Native stop-stream confirmation cancelled");
    }

    pub(crate) fn confirm_stop(&mut self) {
        self.stop_confirmation_visible = false;
        self.menu_visible = false;
        self.canvas.window_mut().hide();
        eprintln!("Native stop-stream confirmation accepted");
    }

    pub(crate) fn hide(&mut self) {
        self.canvas.window_mut().hide();
    }

    pub(crate) fn show_if_enabled(&mut self) {
        if self.stop_confirmation_visible {
            self.show_confirmation();
        } else if self.menu_visible {
            self.show_menu();
        } else {
            self.restore_stats_or_hide();
        }
    }

    pub(crate) fn update(
        &mut self,
        presented_frames: u64,
        dropped_frames: u64,
        relative_mouse: bool,
        received_video_bytes: u64,
    ) {
        let changed = self.stats.update(
            presented_frames,
            dropped_frames,
            relative_mouse,
            received_video_bytes,
        );
        if changed && !self.modal_visible() && self.stats.mode() != OverlayMode::Hidden {
            self.refresh_stats_position();
            self.draw_stats();
        }
        // Modal frames stay immutable while visible. The D3D video swap chain
        // continues independently beneath this input-transparent HUD.
    }

    fn modal_visible(&self) -> bool {
        self.menu_visible || self.stop_confirmation_visible
    }

    fn restore_stats_or_hide(&mut self) {
        if self.stats.mode() == OverlayMode::Hidden {
            self.canvas.window_mut().hide();
            return;
        }
        configure_color_key(self.canvas.window(), false);
        self.refresh_stats_position();
        self.draw_stats();
        show_overlay_topmost(&mut self.canvas);
    }

    fn show_menu(&mut self) {
        self.refresh_modal_surface();
        let card = self.stats.menu_frame(self.menu_render_scale);
        self.draw_modal(&card, (20.0 * self.menu_render_scale).round() as u32);
        show_overlay_topmost(&mut self.canvas);
    }

    fn show_confirmation(&mut self) {
        self.refresh_modal_surface();
        let card = self.stats.stop_confirmation_frame(self.menu_render_scale);
        self.draw_modal(&card, (20.0 * self.menu_render_scale).round() as u32);
        show_overlay_topmost(&mut self.canvas);
    }

    fn refresh_stats_position(&mut self) {
        let Some((client_x, client_y, client_width, client_height)) = client_bounds(self.parent)
        else {
            return;
        };
        let Some((requested_width, requested_height)) = self.stats.logical_size() else {
            return;
        };
        let scale = stats_scale(client_width, client_height);
        self.stats_render_scale = scale;
        let edge_margin = (EDGE_MARGIN as f32 * scale).round() as i32;
        let available_width = client_width.saturating_sub(edge_margin.max(0) as u32 * 2);
        let available_height = client_height.saturating_sub(edge_margin.max(0) as u32 * 2);
        let width = ((requested_width as f32 * scale).round() as u32)
            .min(available_width.max(requested_width.min(client_width)));
        let height = ((requested_height as f32 * scale).round() as u32)
            .min(available_height.max(requested_height.min(client_height)));
        let right = matches!(
            self.stats.position(),
            StatsOverlayPosition::TopRight | StatsOverlayPosition::BottomRight
        );
        let bottom = matches!(
            self.stats.position(),
            StatsOverlayPosition::BottomLeft | StatsOverlayPosition::BottomRight
        );
        let x = if right {
            client_x + client_width as i32 - width as i32 - edge_margin
        } else {
            client_x + edge_margin
        };
        let y = if bottom {
            client_y + client_height as i32 - height as i32 - edge_margin
        } else {
            client_y + edge_margin
        };
        let window = self.canvas.window_mut();
        let _ = window.set_size(width, height);
        apply_round_window_region(
            window,
            width,
            height,
            ((if self.stats.mode() == OverlayMode::Minimal {
                17
            } else {
                18
            }) as f32
                * scale)
                .round() as i32,
        );
        window.set_position(WindowPos::Positioned(x), WindowPos::Positioned(y));
    }

    fn refresh_modal_surface(&mut self) {
        let Some((client_x, client_y, client_width, client_height)) = client_bounds(self.parent)
        else {
            return;
        };
        self.menu_render_scale = menu_scale(client_width, client_height);
        let window = self.canvas.window_mut();
        let _ = window.set_size(client_width, client_height);
        clear_window_region(window);
        window.set_position(
            WindowPos::Positioned(client_x),
            WindowPos::Positioned(client_y),
        );
        configure_color_key(window, true);
    }

    fn draw_stats(&mut self) {
        let Some(frame) = self.stats.scaled_frame(self.stats_render_scale) else {
            return;
        };
        draw_frame(&mut self.canvas, &frame);
    }

    fn draw_modal(&mut self, card: &OverlayFrame, radius: u32) {
        let (width, height) = self.canvas.window().size();
        let frame = modal_overlay_frame(width, height, card, radius);
        draw_frame(&mut self.canvas, &frame);
    }
}

fn overlay_canvas(
    video: &sdl2::VideoSubsystem,
    parent: HWND,
    title: &str,
    width: u32,
    height: u32,
) -> Result<WindowCanvas, String> {
    let window = video
        .window(title, width, height)
        .position(0, 0)
        .borderless()
        .hidden()
        .build()
        .map_err(|error| format!("native overlay creation failed: {error}"))?;
    let overlay_handle = hwnd(&window)?;
    unsafe {
        SetWindowLongPtrW(overlay_handle, GWLP_HWNDPARENT, parent as isize);
        let extended = GetWindowLongPtrW(overlay_handle, GWL_EXSTYLE) as u32;
        SetWindowLongPtrW(
            overlay_handle,
            GWL_EXSTYLE,
            (extended | WS_EX_LAYERED | WS_EX_NOACTIVATE | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW)
                as isize,
        );
    }
    configure_color_key(&window, false);
    window
        .into_canvas()
        .software()
        .build()
        .map_err(|error| format!("native overlay renderer failed: {error}"))
}

fn modal_overlay_frame(width: u32, height: u32, card: &OverlayFrame, radius: u32) -> OverlayFrame {
    let mut rgb = vec![0_u8; width as usize * height as usize * 3];
    for y in 0..height {
        for x in 0..width {
            let offset = (y as usize * width as usize + x as usize) * 3;
            if BAYER_8X8[y as usize % 8][x as usize % 8] < DIM_SAMPLES {
                rgb[offset..offset + 3].copy_from_slice(&[0, 0, 0]);
            } else {
                rgb[offset..offset + 3].copy_from_slice(&COLOR_KEY);
            }
        }
    }

    let origin_x = width.saturating_sub(card.width) / 2;
    let origin_y = height.saturating_sub(card.height) / 2;
    let copy_width = card.width.min(width);
    let copy_height = card.height.min(height);
    for y in 0..copy_height {
        for x in 0..copy_width {
            if !inside_rounded_rect(x, y, card.width, card.height, radius) {
                continue;
            }
            let source = (y as usize * card.width as usize + x as usize) * 3;
            let destination =
                ((origin_y + y) as usize * width as usize + (origin_x + x) as usize) * 3;
            if let (Some(source), Some(destination)) = (
                card.rgb.get(source..source + 3),
                rgb.get_mut(destination..destination + 3),
            ) {
                destination.copy_from_slice(source);
            }
        }
    }
    OverlayFrame { width, height, rgb }
}

fn draw_frame(canvas: &mut WindowCanvas, frame: &OverlayFrame) {
    let texture_creator = canvas.texture_creator();
    let Ok(mut texture) =
        texture_creator.create_texture_streaming(PixelFormatEnum::RGB24, frame.width, frame.height)
    else {
        return;
    };
    if texture
        .update(None, &frame.rgb, frame.width as usize * 3)
        .is_err()
    {
        return;
    }
    let _ = canvas.copy(&texture, None, None);
    canvas.present();
}

fn show_overlay_topmost(canvas: &mut WindowCanvas) {
    let window = canvas.window_mut();
    window.show();
    let Ok(window_handle) = hwnd(window) else {
        return;
    };
    unsafe {
        if SetWindowPos(
            window_handle,
            HWND_TOPMOST,
            0,
            0,
            0,
            0,
            SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW,
        ) == 0
        {
            eprintln!("Native overlay z-order update failed");
        }
    }
}

fn configure_color_key(window: &Window, enabled: bool) {
    let Ok(window_handle) = hwnd(window) else {
        return;
    };
    unsafe {
        let (key, alpha, flags) = if enabled {
            (COLOR_KEY_REF, 255, LWA_COLORKEY | LWA_ALPHA)
        } else {
            (0, STATS_OPACITY, LWA_ALPHA)
        };
        if SetLayeredWindowAttributes(window_handle, key, alpha, flags) == 0 {
            eprintln!("Native overlay transparency update failed");
        }
    }
}

fn stats_scale(client_width: u32, client_height: u32) -> f32 {
    (client_width as f32 / 1920.0)
        .min(client_height as f32 / 1080.0)
        .clamp(1.2, 1.75)
}

fn menu_scale(client_width: u32, client_height: u32) -> f32 {
    (client_width as f32 / 1440.0)
        .min(client_height as f32 / 900.0)
        .clamp(0.72, 1.6)
}

fn apply_round_window_region(window: &Window, width: u32, height: u32, radius: i32) {
    let Ok(window_handle) = hwnd(window) else {
        return;
    };
    unsafe {
        let region = CreateRoundRectRgn(
            0,
            0,
            width.saturating_add(1) as i32,
            height.saturating_add(1) as i32,
            radius.saturating_mul(2),
            radius.saturating_mul(2),
        );
        if !region.is_null() && SetWindowRgn(window_handle, region, 1) == 0 {
            let _ = DeleteObject(region);
        }
    }
}

fn clear_window_region(window: &Window) {
    let Ok(window_handle) = hwnd(window) else {
        return;
    };
    unsafe {
        SetWindowRgn(window_handle, std::ptr::null_mut(), 1);
    }
}

fn hwnd(window: &Window) -> Result<HWND, String> {
    match window
        .window_handle()
        .map_err(|error| format!("stream overlay Win32 handle unavailable: {error}"))?
        .as_raw()
    {
        RawWindowHandle::Win32(handle) => Ok(handle.hwnd.get() as HWND),
        _ => Err("stream overlay did not create a Win32 window".to_owned()),
    }
}

fn client_bounds(parent: HWND) -> Option<(i32, i32, u32, u32)> {
    let mut rect = RECT::default();
    let mut origin = POINT { x: 0, y: 0 };
    unsafe {
        if GetClientRect(parent, &mut rect) == 0 || ClientToScreen(parent, &mut origin) == 0 {
            return None;
        }
    }
    Some((
        origin.x,
        origin.y,
        (rect.right - rect.left).max(1) as u32,
        (rect.bottom - rect.top).max(1) as u32,
    ))
}

fn inside_rounded_rect(x: u32, y: u32, width: u32, height: u32, radius: u32) -> bool {
    if width == 0 || height == 0 {
        return false;
    }
    let radius = radius.min(width / 2).min(height / 2);
    if radius == 0 {
        return true;
    }
    let sample_x = x as f32 + 0.5;
    let sample_y = y as f32 + 0.5;
    let radius = radius as f32;
    let nearest_x = sample_x.clamp(radius, width as f32 - radius);
    let nearest_y = sample_y.clamp(radius, height as f32 - radius);
    let delta_x = sample_x - nearest_x;
    let delta_y = sample_y - nearest_y;
    delta_x * delta_x + delta_y * delta_y <= radius * radius
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn overlays_scale_up_for_1440p_without_becoming_oversized() {
        assert_eq!(stats_scale(1920, 1080), 1.2);
        assert!((stats_scale(2560, 1440) - 4.0 / 3.0).abs() < 0.001);
        assert_eq!(stats_scale(3840, 2160), 1.75);
        assert!((menu_scale(2560, 1440) - 1.6).abs() < 0.001);
    }

    #[test]
    fn modal_overlay_uses_one_full_surface_and_preserves_the_card() {
        let card = OverlayFrame {
            width: 4,
            height: 4,
            rgb: vec![12; 4 * 4 * 3],
        };
        let frame = modal_overlay_frame(20, 12, &card, 0);
        assert_eq!((frame.width, frame.height), (20, 12));
        let card_pixel = ((4 * frame.width + 8) * 3) as usize;
        assert_eq!(&frame.rgb[card_pixel..card_pixel + 3], &[12, 12, 12]);
        assert!(frame.rgb.chunks_exact(3).any(|pixel| pixel == COLOR_KEY));
        assert!(frame.rgb.chunks_exact(3).any(|pixel| pixel == [0, 0, 0]));
    }
}
