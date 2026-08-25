use std::collections::VecDeque;
use std::time::{Duration, Instant};

use raw_window_handle::{HasWindowHandle, RawWindowHandle};
use sdl2::pixels::Color;
use sdl2::rect::Rect;
use sdl2::render::WindowCanvas;
use sdl2::video::{Window, WindowPos};
use windows_sys::Win32::Foundation::{HWND, POINT, RECT};
use windows_sys::Win32::Graphics::Gdi::ClientToScreen;
use windows_sys::Win32::UI::WindowsAndMessaging::{
    GWL_EXSTYLE, GWLP_HWNDPARENT, GetClientRect, GetWindowLongPtrW, SetWindowLongPtrW,
    WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW, WS_EX_TRANSPARENT,
};

use crate::media::MediaStreamConfig;

const FULL_SIZE: (u32, u32) = (480, 470);
const MINIMAL_SIZE: (u32, u32) = (180, 40);
const EDGE_MARGIN: i32 = 24;
const SAMPLE_INTERVAL: Duration = Duration::from_millis(250);

const BACKGROUND: Color = Color::RGB(3, 9, 7);
const BORDER: Color = Color::RGB(28, 55, 40);
const GREEN: Color = Color::RGB(47, 229, 119);
const MUTED: Color = Color::RGB(102, 116, 108);
const TEXT: Color = Color::RGB(216, 226, 220);
const AMBER: Color = Color::RGB(255, 184, 0);
const GRAPH: Color = Color::RGB(48, 111, 76);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum OverlayMode {
    Hidden,
    Minimal,
    Full,
}

impl OverlayMode {
    fn next(self) -> Self {
        match self {
            Self::Hidden => Self::Minimal,
            Self::Minimal => Self::Full,
            Self::Full => Self::Hidden,
        }
    }
}

pub(crate) struct NativeDebugOverlay {
    canvas: WindowCanvas,
    parent: HWND,
    mode: OverlayMode,
    stream: MediaStreamConfig,
    decoder: &'static str,
    last_presented: u64,
    last_sample: Instant,
    measured_fps: f32,
    fps_history: VecDeque<f32>,
    dropped_frames: u64,
    relative_mouse: bool,
}

impl NativeDebugOverlay {
    pub(crate) fn new(
        video: &sdl2::VideoSubsystem,
        parent: isize,
        stream: MediaStreamConfig,
        decoder: &'static str,
    ) -> Result<Self, String> {
        let window = video
            .window("OpenNOW Stream Stats", MINIMAL_SIZE.0, MINIMAL_SIZE.1)
            .position(0, 0)
            .borderless()
            .hidden()
            .build()
            .map_err(|error| format!("native stats overlay creation failed: {error}"))?;
        let overlay_handle = hwnd(&window)?;
        let parent = parent as HWND;
        unsafe {
            SetWindowLongPtrW(overlay_handle, GWLP_HWNDPARENT, parent as isize);
            let extended = GetWindowLongPtrW(overlay_handle, GWL_EXSTYLE) as u32;
            SetWindowLongPtrW(
                overlay_handle,
                GWL_EXSTYLE,
                (extended | WS_EX_NOACTIVATE | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW) as isize,
            );
        }
        let mut canvas = window
            .into_canvas()
            .software()
            .build()
            .map_err(|error| format!("native stats overlay renderer failed: {error}"))?;
        let _ = canvas.window_mut().set_opacity(0.96);
        Ok(Self {
            canvas,
            parent,
            mode: OverlayMode::Hidden,
            stream,
            decoder,
            last_presented: 0,
            last_sample: Instant::now(),
            measured_fps: 0.0,
            fps_history: VecDeque::with_capacity(18),
            dropped_frames: 0,
            relative_mouse: false,
        })
    }

    pub(crate) fn toggle(&mut self) {
        self.mode = self.mode.next();
        if self.mode == OverlayMode::Hidden {
            self.canvas.window_mut().hide();
        } else {
            self.refresh_position();
            self.draw();
            self.canvas.window_mut().show();
            self.canvas.window_mut().raise();
        }
        eprintln!("Native F3 stream stats overlay: {:?}", self.mode);
    }

    pub(crate) fn hide(&mut self) {
        self.canvas.window_mut().hide();
    }

    pub(crate) fn show_if_enabled(&mut self) {
        if self.mode != OverlayMode::Hidden {
            self.refresh_position();
            self.draw();
            self.canvas.window_mut().show();
        }
    }

    pub(crate) fn update(
        &mut self,
        presented_frames: u64,
        dropped_frames: u64,
        relative_mouse: bool,
    ) {
        self.dropped_frames = dropped_frames;
        self.relative_mouse = relative_mouse;
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_sample);
        if elapsed < SAMPLE_INTERVAL {
            return;
        }
        self.measured_fps = presented_frames.saturating_sub(self.last_presented) as f32
            / elapsed.as_secs_f32().max(0.001);
        self.last_presented = presented_frames;
        self.last_sample = now;
        if self.fps_history.len() == 18 {
            self.fps_history.pop_front();
        }
        self.fps_history.push_back(self.measured_fps);
        if self.mode != OverlayMode::Hidden {
            self.refresh_position();
            self.draw();
        }
    }

    fn refresh_position(&mut self) {
        let Some((client_x, client_y, client_width, _)) = client_bounds(self.parent) else {
            return;
        };
        let (width, height, x, y) = match self.mode {
            OverlayMode::Hidden => return,
            OverlayMode::Minimal => (
                MINIMAL_SIZE.0,
                MINIMAL_SIZE.1,
                client_x + client_width as i32 - MINIMAL_SIZE.0 as i32 - EDGE_MARGIN,
                client_y + EDGE_MARGIN,
            ),
            OverlayMode::Full => (
                FULL_SIZE
                    .0
                    .min(client_width.saturating_sub(EDGE_MARGIN as u32 * 2).max(320)),
                FULL_SIZE.1,
                client_x + EDGE_MARGIN,
                client_y + EDGE_MARGIN,
            ),
        };
        let window = self.canvas.window_mut();
        let _ = window.set_size(width, height);
        window.set_position(WindowPos::Positioned(x), WindowPos::Positioned(y));
    }

    fn draw(&mut self) {
        self.canvas.set_draw_color(BACKGROUND);
        self.canvas.clear();
        let size = self.canvas.output_size().unwrap_or(MINIMAL_SIZE);
        self.canvas.set_draw_color(BORDER);
        let _ = self.canvas.draw_rect(Rect::new(
            0,
            0,
            size.0.saturating_sub(1),
            size.1.saturating_sub(1),
        ));
        match self.mode {
            OverlayMode::Hidden => {}
            OverlayMode::Minimal => self.draw_minimal(),
            OverlayMode::Full => self.draw_full(),
        }
        self.canvas.present();
    }

    fn draw_minimal(&mut self) {
        let fps = display_fps(self.measured_fps, self.stream.fps);
        draw_text(&mut self.canvas, 14, 10, 3, GREEN, &fps.to_string());
        draw_text(&mut self.canvas, 76, 15, 1, MUTED, "FPS");
        draw_text(&mut self.canvas, 105, 14, 2, MUTED, "|");
        let frame_time = if self.measured_fps > 1.0 {
            format!("{:.1} MS/F", 1000.0 / self.measured_fps)
        } else {
            "-- MS/F".to_owned()
        };
        draw_text(&mut self.canvas, 120, 15, 1, TEXT, &frame_time);
    }

    fn draw_full(&mut self) {
        draw_text(&mut self.canvas, 20, 22, 2, GREEN, "NVST DEBUG");
        tag(&mut self.canvas, 328, 18, 64, "NTFK ON", GREEN);
        tag(&mut self.canvas, 400, 18, 62, "F3 OFF", MUTED);

        let fps = display_fps(self.measured_fps, self.stream.fps);
        let fps_text = fps.to_string();
        let fps_x = 20;
        let fps_scale = 7;
        draw_text(&mut self.canvas, fps_x, 58, fps_scale, GREEN, &fps_text);
        let fps_label_x = fps_x + text_width(&fps_text, fps_scale) as i32 + 10;
        draw_text(&mut self.canvas, fps_label_x, 77, 2, TEXT, "FPS");
        let frame_ms = if self.measured_fps > 1.0 {
            format!("{:.1} MS / FRAME", 1000.0 / self.measured_fps)
        } else {
            "-- MS / FRAME".to_owned()
        };
        draw_text(&mut self.canvas, fps_label_x, 99, 1, MUTED, &frame_ms);
        self.draw_graph(220, 58, 242, 52);

        section(&mut self.canvas, 20, 132, "VIDEO");
        row(
            &mut self.canvas,
            20,
            154,
            "CODEC",
            460,
            &self.stream.codec.label().to_ascii_uppercase(),
            TEXT,
        );
        row(
            &mut self.canvas,
            20,
            178,
            "DECODER",
            460,
            self.decoder,
            TEXT,
        );
        row(
            &mut self.canvas,
            20,
            202,
            "STREAM",
            460,
            &format!(
                "{}X{} @ {}",
                self.stream.width, self.stream.height, self.stream.fps
            ),
            TEXT,
        );
        row(
            &mut self.canvas,
            20,
            226,
            "FRAME PERIOD",
            460,
            &frame_ms,
            GREEN,
        );
        row(
            &mut self.canvas,
            20,
            250,
            "PRESENT DROPS",
            460,
            &format!("{} TOTAL", self.dropped_frames),
            if self.dropped_frames == 0 {
                TEXT
            } else {
                AMBER
            },
        );
        row(
            &mut self.canvas,
            20,
            274,
            "CURSOR / INPUT",
            460,
            if self.relative_mouse {
                "LOCKED / RAW"
            } else {
                "ABSOLUTE / RAW"
            },
            GREEN,
        );
        divider(&mut self.canvas, 20, 299, 440);

        section(&mut self.canvas, 20, 317, "NETWORK");
        row(
            &mut self.canvas,
            20,
            339,
            "TRANSPORT",
            460,
            "NVST / UDP",
            GREEN,
        );
        row(
            &mut self.canvas,
            20,
            363,
            "ROUND TRIP / JITTER",
            460,
            "-- / --",
            MUTED,
        );
        row(&mut self.canvas, 20, 387, "PACKET LOSS", 460, "--", MUTED);
        row(
            &mut self.canvas,
            20,
            411,
            "BITRATE TARGET",
            460,
            &format!("{:.1} MBPS", self.stream.bitrate_bps as f32 / 1_000_000.0),
            TEXT,
        );
        draw_text(
            &mut self.canvas,
            20,
            448,
            1,
            MUTED,
            "F3: MINIMAL  /  FULL  /  OFF",
        );
    }

    fn draw_graph(&mut self, x: i32, y: i32, width: u32, height: u32) {
        self.canvas.set_draw_color(BORDER);
        let _ = self.canvas.draw_line(
            (x, y + height as i32),
            (x + width as i32, y + height as i32),
        );
        let target = self.stream.fps.max(1) as f32;
        for (index, sample) in self.fps_history.iter().enumerate() {
            let bar_height = ((*sample / target).clamp(0.05, 1.2) * height as f32) as u32;
            self.canvas
                .set_draw_color(if *sample < target * 0.8 { AMBER } else { GRAPH });
            let _ = self.canvas.fill_rect(Rect::new(
                x + index as i32 * 14,
                y + height as i32 - bar_height as i32,
                10,
                bar_height,
            ));
        }
    }
}

fn hwnd(window: &Window) -> Result<HWND, String> {
    match window
        .window_handle()
        .map_err(|error| format!("stats overlay Win32 handle unavailable: {error}"))?
        .as_raw()
    {
        RawWindowHandle::Win32(handle) => Ok(handle.hwnd.get() as HWND),
        _ => Err("stats overlay did not create a Win32 window".to_owned()),
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

fn display_fps(measured: f32, target: u32) -> u32 {
    if measured > 1.0 {
        measured.round() as u32
    } else {
        target
    }
}

fn section(canvas: &mut WindowCanvas, x: i32, y: i32, label: &str) {
    draw_text(canvas, x, y, 1, MUTED, label);
}

fn divider(canvas: &mut WindowCanvas, x: i32, y: i32, width: u32) {
    canvas.set_draw_color(BORDER);
    let _ = canvas.draw_line((x, y), (x + width as i32, y));
}

fn row(
    canvas: &mut WindowCanvas,
    x: i32,
    y: i32,
    label: &str,
    right: i32,
    value: &str,
    color: Color,
) {
    draw_text(canvas, x, y, 2, MUTED, label);
    let width = text_width(value, 2);
    draw_text(canvas, right - width as i32, y, 2, color, value);
}

fn tag(canvas: &mut WindowCanvas, x: i32, y: i32, width: u32, label: &str, color: Color) {
    canvas.set_draw_color(BORDER);
    let _ = canvas.draw_rect(Rect::new(x, y, width, 25));
    draw_text(canvas, x + 8, y + 8, 1, color, label);
}

fn text_width(text: &str, scale: u32) -> u32 {
    text.chars().count() as u32 * 6 * scale
}

fn draw_text(canvas: &mut WindowCanvas, x: i32, y: i32, scale: u32, color: Color, text: &str) {
    canvas.set_draw_color(color);
    let mut cursor_x = x;
    for character in text.to_ascii_uppercase().chars() {
        for (row, bits) in glyph(character).iter().enumerate() {
            for column in 0..5 {
                if bits & (1 << (4 - column)) != 0 {
                    let _ = canvas.fill_rect(Rect::new(
                        cursor_x + column * scale as i32,
                        y + row as i32 * scale as i32,
                        scale,
                        scale,
                    ));
                }
            }
        }
        cursor_x += 6 * scale as i32;
    }
}

fn glyph(character: char) -> [u8; 7] {
    match character {
        'A' => [14, 17, 17, 31, 17, 17, 17],
        'B' => [30, 17, 17, 30, 17, 17, 30],
        'C' => [14, 17, 16, 16, 16, 17, 14],
        'D' => [30, 17, 17, 17, 17, 17, 30],
        'E' => [31, 16, 16, 30, 16, 16, 31],
        'F' => [31, 16, 16, 30, 16, 16, 16],
        'G' => [14, 17, 16, 23, 17, 17, 15],
        'H' => [17, 17, 17, 31, 17, 17, 17],
        'I' => [14, 4, 4, 4, 4, 4, 14],
        'J' => [7, 2, 2, 2, 18, 18, 12],
        'K' => [17, 18, 20, 24, 20, 18, 17],
        'L' => [16, 16, 16, 16, 16, 16, 31],
        'M' => [17, 27, 21, 21, 17, 17, 17],
        'N' => [17, 25, 21, 19, 17, 17, 17],
        'O' => [14, 17, 17, 17, 17, 17, 14],
        'P' => [30, 17, 17, 30, 16, 16, 16],
        'Q' => [14, 17, 17, 17, 21, 18, 13],
        'R' => [30, 17, 17, 30, 20, 18, 17],
        'S' => [15, 16, 16, 14, 1, 1, 30],
        'T' => [31, 4, 4, 4, 4, 4, 4],
        'U' => [17, 17, 17, 17, 17, 17, 14],
        'V' => [17, 17, 17, 17, 17, 10, 4],
        'W' => [17, 17, 17, 21, 21, 21, 10],
        'X' => [17, 17, 10, 4, 10, 17, 17],
        'Y' => [17, 17, 10, 4, 4, 4, 4],
        'Z' => [31, 1, 2, 4, 8, 16, 31],
        '0' => [14, 17, 19, 21, 25, 17, 14],
        '1' => [4, 12, 4, 4, 4, 4, 14],
        '2' => [14, 17, 1, 2, 4, 8, 31],
        '3' => [30, 1, 1, 14, 1, 1, 30],
        '4' => [2, 6, 10, 18, 31, 2, 2],
        '5' => [31, 16, 16, 30, 1, 1, 30],
        '6' => [14, 16, 16, 30, 17, 17, 14],
        '7' => [31, 1, 2, 4, 8, 8, 8],
        '8' => [14, 17, 17, 14, 17, 17, 14],
        '9' => [14, 17, 17, 15, 1, 1, 14],
        '.' => [0, 0, 0, 0, 0, 12, 12],
        ':' => [0, 12, 12, 0, 12, 12, 0],
        '/' => [1, 2, 2, 4, 8, 8, 16],
        '-' => [0, 0, 0, 31, 0, 0, 0],
        '|' => [4, 4, 4, 4, 4, 4, 4],
        '@' => [14, 17, 23, 21, 23, 16, 14],
        '%' => [17, 2, 4, 8, 16, 17, 0],
        _ => [0; 7],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn overlay_modes_cycle_predictably() {
        assert_eq!(OverlayMode::Hidden.next(), OverlayMode::Minimal);
        assert_eq!(OverlayMode::Minimal.next(), OverlayMode::Full);
        assert_eq!(OverlayMode::Full.next(), OverlayMode::Hidden);
    }

    #[test]
    fn fps_uses_target_until_live_sample_exists() {
        assert_eq!(display_fps(0.0, 120), 120);
        assert_eq!(display_fps(117.4, 120), 117);
    }
}
