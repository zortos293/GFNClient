use std::collections::VecDeque;
use std::sync::OnceLock;
use std::time::{Duration, Instant};

use fontdue::{Font, FontSettings};

use crate::media::{MediaStreamConfig, StatsOverlayPosition};

// Exact component bounds from Paper: Desktop 09a / 09b.
const FULL_SIZE: (u32, u32) = (292, 358);
const MINIMAL_SIZE: (u32, u32) = (244, 34);
pub(crate) const MENU_SIZE: (u32, u32) = (760, 410);
pub(crate) const STOP_CONFIRM_SIZE: (u32, u32) = (520, 236);
#[cfg(any(target_os = "linux", test))]
const EDGE_MARGIN: u32 = 24;
const SAMPLE_INTERVAL: Duration = Duration::from_millis(250);
#[cfg(any(target_os = "linux", test))]
const OVERLAY_ALPHA: u16 = 235;

type Rgb = [u8; 3];

const BACKGROUND: Rgb = [0x0b, 0x0f, 0x1a];
const BORDER: Rgb = [0x2d, 0x30, 0x39];
const TEXT: Rgb = [0xff, 0xff, 0xff];
const MUTED: Rgb = [0x8a, 0x8a, 0x8a];
const FAINT: Rgb = [0x66, 0x66, 0x66];
const MINT: Rgb = [0x6e, 0xe7, 0xb7];
const SKY: Rgb = [0x7f, 0xd4, 0xff];
const VIOLET: Rgb = [0xa7, 0x8b, 0xfa];
const YELLOW: Rgb = [0xff, 0xd1, 0x66];
const LED_GREEN: Rgb = [0x1d, 0xb9, 0x54];
const MINT_DIM: Rgb = [0x2f, 0x66, 0x58];
const SKY_DIM: Rgb = [0x35, 0x5f, 0x78];
const CARD: Rgb = [0x14, 0x19, 0x24];
const CARD_HOVER: Rgb = [0x1b, 0x21, 0x2e];
const PAPER_WHITE: Rgb = [0xf2, 0xf4, 0xf8];
const SHELL: Rgb = [0x0b, 0x0f, 0x1a];
const DANGER: Rgb = [0xff, 0xb4, 0xae];
const DANGER_DIM: Rgb = [0x52, 0x2d, 0x33];

const MONO_MEDIUM_BYTES: &[u8] =
    include_bytes!("../../../../../opennow-qt/res/fonts/IBMPlexMono-Medium.ttf");
const MONO_BOLD_BYTES: &[u8] =
    include_bytes!("../../../../../opennow-qt/res/fonts/IBMPlexMono-Bold.ttf");
const LABEL_BYTES: &[u8] =
    include_bytes!("../../../../../opennow-qt/res/fonts/Nunito-Variable.ttf");

static MONO_MEDIUM: OnceLock<Font> = OnceLock::new();
static MONO_BOLD: OnceLock<Font> = OnceLock::new();
static LABEL: OnceLock<Font> = OnceLock::new();

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

    pub(crate) const fn size(self) -> Option<(u32, u32)> {
        match self {
            Self::Hidden => None,
            Self::Minimal => Some(MINIMAL_SIZE),
            Self::Full => Some(FULL_SIZE),
        }
    }
}

#[derive(Debug, Clone)]
pub(crate) struct OverlayFrame {
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) rgb: Vec<u8>,
}

struct MinimalLayout {
    width: u32,
    region: String,
    codec: String,
    codec_divider_x: i32,
    codec_x: i32,
    bitrate_divider_x: i32,
    bitrate_x: i32,
}

pub(crate) struct NativeStatsOverlay {
    mode: OverlayMode,
    stream: MediaStreamConfig,
    decoder: &'static str,
    last_presented: u64,
    last_sample: Instant,
    started_at: Instant,
    measured_fps: f32,
    last_received_video_bytes: u64,
    measured_bitrate_bps: f32,
    frame_time_history: VecDeque<f32>,
    bitrate_history: VecDeque<f32>,
    peak_bitrate_bps: f32,
    dropped_frames: u64,
    presentation_skips: u64,
    relative_mouse: bool,
    rendered: Option<OverlayFrame>,
}

impl NativeStatsOverlay {
    pub(crate) fn new(stream: MediaStreamConfig, decoder: &'static str) -> Self {
        let mut overlay = Self {
            mode: if stream.show_stats {
                OverlayMode::Minimal
            } else {
                OverlayMode::Hidden
            },
            stream,
            decoder,
            last_presented: 0,
            last_sample: Instant::now(),
            started_at: Instant::now(),
            measured_fps: 0.0,
            last_received_video_bytes: 0,
            measured_bitrate_bps: 0.0,
            frame_time_history: VecDeque::with_capacity(24),
            bitrate_history: VecDeque::with_capacity(24),
            peak_bitrate_bps: 0.0,
            dropped_frames: 0,
            presentation_skips: 0,
            relative_mouse: false,
            rendered: None,
        };
        if stream.show_stats {
            overlay.render();
        }
        overlay
    }

    pub(crate) fn toggle(&mut self) {
        self.mode = self.mode.next();
        self.render();
        eprintln!("Native F3 stream stats overlay: {:?}", self.mode);
    }

    #[cfg(any(target_os = "windows", target_os = "macos"))]
    pub(crate) const fn mode(&self) -> OverlayMode {
        self.mode
    }

    #[cfg(any(target_os = "windows", target_os = "macos"))]
    pub(crate) const fn position(&self) -> StatsOverlayPosition {
        self.stream.stats_position
    }

    #[cfg(target_os = "windows")]
    pub(crate) fn logical_size(&self) -> Option<(u32, u32)> {
        self.rendered
            .as_ref()
            .map(|frame| (frame.width, frame.height))
    }

    pub(crate) fn update(
        &mut self,
        presented_frames: u64,
        dropped_frames: u64,
        relative_mouse: bool,
        received_video_bytes: u64,
    ) -> bool {
        self.dropped_frames = dropped_frames;
        self.relative_mouse = relative_mouse;
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_sample);
        if elapsed < SAMPLE_INTERVAL {
            return false;
        }
        self.measured_fps = presented_frames.saturating_sub(self.last_presented) as f32
            / elapsed.as_secs_f32().max(0.001);
        let bitrate_bps = measured_bitrate_bps(
            received_video_bytes.saturating_sub(self.last_received_video_bytes),
            elapsed,
        );
        self.measured_bitrate_bps = if self.measured_bitrate_bps <= 0.0 {
            bitrate_bps
        } else {
            self.measured_bitrate_bps * 0.65 + bitrate_bps * 0.35
        };
        self.peak_bitrate_bps = self.peak_bitrate_bps.max(self.measured_bitrate_bps);
        self.last_presented = presented_frames;
        self.last_received_video_bytes = received_video_bytes;
        self.last_sample = now;
        if self.frame_time_history.len() == 24 {
            self.frame_time_history.pop_front();
        }
        self.frame_time_history
            .push_back(if self.measured_fps > 1.0 {
                1_000.0 / self.measured_fps
            } else {
                1_000.0 / self.stream.fps.max(1) as f32
            });
        if self.bitrate_history.len() == 24 {
            self.bitrate_history.pop_front();
        }
        self.bitrate_history
            .push_back(self.measured_bitrate_bps / 1_000_000.0);
        self.render();
        true
    }

    #[cfg(target_os = "linux")]
    pub(crate) fn set_presentation_skips(&mut self, presentation_skips: u64) {
        self.presentation_skips = presentation_skips;
    }

    #[cfg(any(target_os = "linux", target_os = "macos", test))]
    pub(crate) fn frame(&self) -> Option<&OverlayFrame> {
        self.rendered.as_ref()
    }

    #[cfg(any(target_os = "windows", test))]
    pub(crate) fn scaled_frame(&self, scale: f32) -> Option<OverlayFrame> {
        self.render_frame(scale)
    }

    pub(crate) fn menu_frame(&self, scale: f32) -> OverlayFrame {
        let (width, height) = MENU_SIZE;
        let mut canvas = Raster::new_scaled(width, height, scale, BACKGROUND);
        canvas.rounded_border(0, 0, width, height, 20, BORDER);

        canvas.fill_rounded_rect(20, 18, 50, 56, 9, CARD_HOVER);
        canvas.rounded_border(20, 18, 50, 56, 9, BORDER);
        canvas.text(31, 35, TextStyle::mono_bold(16.0, 20.0, 0.02), MINT, "ON");
        canvas.text(
            86,
            19,
            TextStyle::label_black(20.0, 26.0, 0.01),
            TEXT,
            "OPENNOW STREAM",
        );
        canvas.fill_circle(89, 59, 3, LED_GREEN);
        canvas.text(
            99,
            53,
            TextStyle::mono_medium(9.0, 12.0, 0.08),
            MUTED,
            &format!("LIVE · {}", elapsed_text(self.started_at.elapsed())),
        );

        self.menu_header_metric(
            &mut canvas,
            474,
            "FPS",
            &display_fps(self.measured_fps, self.stream.fps).to_string(),
        );
        self.menu_header_metric(
            &mut canvas,
            570,
            "BITRATE",
            &format!(
                "{} Mb",
                display_mbps(self.measured_bitrate_bps, self.stream.bitrate_bps)
            ),
        );
        self.menu_header_metric(
            &mut canvas,
            676,
            "OUTPUT",
            &format!("{}p", self.stream.height),
        );
        canvas.divider(20, 91, 720);

        self.menu_action(
            &mut canvas,
            20,
            110,
            352,
            54,
            "BACK TO GAME",
            "CTRL+G",
            true,
            false,
        );
        self.menu_action(
            &mut canvas,
            20,
            172,
            352,
            52,
            "STREAM STATS",
            "F3",
            false,
            false,
        );
        self.menu_action(
            &mut canvas,
            20,
            232,
            352,
            52,
            "FULLSCREEN",
            "F11",
            false,
            false,
        );
        self.menu_action(
            &mut canvas,
            20,
            292,
            352,
            52,
            "END SESSION",
            "CTRL+SHIFT+Q",
            false,
            true,
        );

        canvas.fill_rounded_rect(390, 110, 350, 164, 12, CARD);
        canvas.rounded_border(390, 110, 350, 164, 12, BORDER);
        canvas.text(
            406,
            124,
            TextStyle::mono_bold(9.0, 12.0, 0.09),
            FAINT,
            "CONNECTION",
        );
        canvas.fill_rounded_rect(648, 120, 76, 22, 11, [0x12, 0x2b, 0x26]);
        canvas.rounded_border(648, 120, 76, 22, 11, MINT_DIM);
        canvas.text(
            660,
            126,
            TextStyle::mono_bold(8.0, 10.0, 0.07),
            MINT,
            "STREAMING",
        );
        let region = elide_label(self.stream.server_region.as_str(), 31);
        self.menu_connection_row(&mut canvas, 406, 156, "REGION", &region, MINT);
        self.menu_connection_row(
            &mut canvas,
            406,
            181,
            "CODEC",
            &self.stream.codec.label().to_ascii_uppercase(),
            SKY,
        );
        self.menu_connection_row(
            &mut canvas,
            406,
            206,
            "DECODER",
            decoder_label(self.decoder),
            VIOLET,
        );
        self.menu_connection_row(
            &mut canvas,
            406,
            231,
            "OUTPUT",
            &format!("{}p · {} Hz", self.stream.height, self.stream.fps),
            TEXT,
        );

        canvas.fill_rounded_rect(390, 286, 350, 92, 12, CARD);
        canvas.rounded_border(390, 286, 350, 92, 12, BORDER);
        canvas.text(
            406,
            301,
            TextStyle::mono_bold(9.0, 12.0, 0.09),
            FAINT,
            "STREAM OVERLAY",
        );
        canvas.text(
            406,
            325,
            TextStyle::label_bold(12.0, 17.0, 0.01),
            TEXT,
            "Live video and input stay active.",
        );
        canvas.text(
            406,
            349,
            TextStyle::mono_medium(9.0, 12.0, 0.05),
            MUTED,
            "F3 STATS  ·  F11 FULLSCREEN",
        );

        canvas.text(
            20,
            385,
            TextStyle::mono_medium(9.0, 12.0, 0.07),
            FAINT,
            "NATIVE STREAM CONTINUES UNDER THIS MENU",
        );
        canvas.text_right(
            740,
            385,
            TextStyle::mono_bold(9.0, 12.0, 0.07),
            MUTED,
            "CTRL+G  CLOSE",
        );

        OverlayFrame {
            width: canvas.width,
            height: canvas.height,
            rgb: canvas.pixels,
        }
    }

    pub(crate) fn stop_confirmation_frame(&self, scale: f32) -> OverlayFrame {
        let (width, height) = STOP_CONFIRM_SIZE;
        let mut canvas = Raster::new_scaled(width, height, scale, BACKGROUND);
        canvas.rounded_border(0, 0, width, height, 20, BORDER);
        canvas.fill_rounded_rect(22, 20, 44, 44, 10, DANGER_DIM);
        canvas.rounded_border(22, 20, 44, 44, 10, [0x84, 0x3e, 0x46]);
        canvas.text(37, 31, TextStyle::mono_bold(15.0, 20.0, 0.0), DANGER, "X");
        canvas.text(
            82,
            19,
            TextStyle::label_black(20.0, 27.0, 0.01),
            TEXT,
            "END STREAM?",
        );
        canvas.text(
            82,
            48,
            TextStyle::mono_medium(9.0, 12.0, 0.06),
            MUTED,
            &format!("LIVE · {}", self.stream.server_region.as_str()),
        );
        canvas.divider(22, 82, 476);
        canvas.text(
            22,
            99,
            TextStyle::label_bold(13.0, 18.0, 0.01),
            TEXT,
            "Do you want to stop your stream?",
        );
        canvas.text(
            22,
            122,
            TextStyle::label_bold(11.0, 16.0, 0.01),
            MUTED,
            "The live GeForce NOW session will be disconnected.",
        );

        canvas.fill_rounded_rect(22, 154, 228, 50, 10, [0x35, 0x1d, 0x23]);
        canvas.rounded_border(22, 154, 228, 50, 10, [0x84, 0x3e, 0x46]);
        canvas.text(
            38,
            169,
            TextStyle::label_bold(12.0, 17.0, 0.02),
            DANGER,
            "YES, STOP",
        );
        canvas.text_right(
            234,
            171,
            TextStyle::mono_bold(9.0, 13.0, 0.05),
            DANGER,
            "ENTER / A",
        );

        canvas.fill_rounded_rect(270, 154, 228, 50, 10, CARD);
        canvas.rounded_border(270, 154, 228, 50, 10, BORDER);
        canvas.text(
            286,
            169,
            TextStyle::label_bold(12.0, 17.0, 0.02),
            TEXT,
            "NO, GO BACK",
        );
        canvas.text_right(
            482,
            171,
            TextStyle::mono_bold(9.0, 13.0, 0.05),
            MUTED,
            "ESC / B",
        );
        canvas.text(
            22,
            216,
            TextStyle::mono_medium(8.0, 11.0, 0.06),
            FAINT,
            "VIDEO CONTINUES UNTIL YOU CONFIRM",
        );

        OverlayFrame {
            width: canvas.width,
            height: canvas.height,
            rgb: canvas.pixels,
        }
    }

    fn menu_header_metric(&self, canvas: &mut Raster, center: i32, label: &str, value: &str) {
        let label_style = TextStyle::mono_bold(8.0, 10.0, 0.1);
        let value_style = TextStyle::mono_bold(12.0, 15.0, 0.02);
        canvas.text(
            center - canvas.measure(label_style, label).ceil() as i32 / 2,
            25,
            label_style,
            FAINT,
            label,
        );
        canvas.text(
            center - canvas.measure(value_style, value).ceil() as i32 / 2,
            45,
            value_style,
            TEXT,
            value,
        );
    }

    #[allow(clippy::too_many_arguments)]
    fn menu_action(
        &self,
        canvas: &mut Raster,
        x: i32,
        y: i32,
        width: u32,
        height: u32,
        title: &str,
        shortcut: &str,
        primary: bool,
        danger: bool,
    ) {
        let background = if primary { PAPER_WHITE } else { CARD };
        canvas.fill_rounded_rect(x, y, width, height, 10, background);
        if !primary {
            canvas.rounded_border(
                x,
                y,
                width,
                height,
                10,
                if danger { DANGER_DIM } else { BORDER },
            );
        }
        canvas.fill_rounded_rect(
            x + 12,
            y + (height as i32 - 28) / 2,
            28,
            28,
            7,
            if primary {
                [0xd9, 0xdc, 0xe2]
            } else {
                CARD_HOVER
            },
        );
        canvas.text(
            x + 20,
            y + (height as i32 - 14) / 2,
            TextStyle::mono_bold(10.0, 14.0, 0.0),
            if primary {
                SHELL
            } else if danger {
                DANGER
            } else {
                MINT
            },
            if primary {
                ">"
            } else if danger {
                "X"
            } else if title == "STREAM STATS" {
                "S"
            } else {
                "F"
            },
        );
        canvas.text(
            x + 52,
            y + (height as i32 - 17) / 2,
            TextStyle::label_bold(12.0, 17.0, 0.03),
            if primary {
                SHELL
            } else if danger {
                DANGER
            } else {
                TEXT
            },
            title,
        );
        canvas.text_right(
            x + width as i32 - 14,
            y + (height as i32 - 12) / 2,
            TextStyle::mono_bold(8.0, 12.0, 0.07),
            if primary {
                [0x55, 0x59, 0x62]
            } else if danger {
                DANGER
            } else {
                MUTED
            },
            shortcut,
        );
    }

    fn menu_connection_row(
        &self,
        canvas: &mut Raster,
        x: i32,
        y: i32,
        label: &str,
        value: &str,
        value_color: Rgb,
    ) {
        canvas.text(x, y, TextStyle::mono_bold(8.0, 12.0, 0.08), FAINT, label);
        canvas.text_right(
            724,
            y,
            TextStyle::mono_bold(10.0, 12.0, 0.03),
            value_color,
            value,
        );
    }

    #[cfg(any(target_os = "linux", test))]
    pub(crate) fn composite_rgb24(
        &self,
        destination: &mut [u8],
        width: u32,
        height: u32,
        stride: usize,
    ) {
        let Some(source) = self.frame() else {
            return;
        };
        let Some((origin_x, origin_y)) =
            overlay_origin(self.mode, self.stream.stats_position, width, height, source)
        else {
            return;
        };
        let copy_width = source.width.min(width.saturating_sub(origin_x));
        let copy_height = source.height.min(height.saturating_sub(origin_y));
        for row in 0..copy_height as usize {
            let source_start = row * source.width as usize * 3;
            let destination_start = (origin_y as usize + row) * stride + origin_x as usize * 3;
            let bytes = copy_width as usize * 3;
            let Some(source_row) = source.rgb.get(source_start..source_start + bytes) else {
                return;
            };
            let Some(destination_row) =
                destination.get_mut(destination_start..destination_start + bytes)
            else {
                return;
            };
            for (destination, source) in destination_row.iter_mut().zip(source_row) {
                *destination = blend(*destination, *source);
            }
        }
    }

    #[cfg(target_os = "linux")]
    pub(crate) fn composite_linux_frame(
        &self,
        frame: &mut opennow_streamer_platform_linux::DecodedVideoFrame,
    ) {
        use opennow_streamer_platform_linux::PixelFormat;

        if frame.format.pixel_format != PixelFormat::Nv12
            || (frame.vulkan.is_none() && frame.dmabuf.is_none() && frame.planes.len() != 2)
        {
            return;
        }
        let Some(source) = self.frame() else {
            return;
        };
        let Some((origin_x, origin_y)) = overlay_origin(
            self.mode,
            self.stream.stats_position,
            frame.format.width,
            frame.format.height,
            source,
        ) else {
            return;
        };
        let origin_x = origin_x & !1;
        let origin_y = origin_y & !1;
        let copy_width = source
            .width
            .min(frame.format.width.saturating_sub(origin_x))
            & !1;
        let copy_height = source
            .height
            .min(frame.format.height.saturating_sub(origin_y))
            & !1;
        if frame.vulkan.is_some() || frame.dmabuf.is_some() {
            let mut luma = vec![0_u8; (copy_width * copy_height) as usize];
            let mut chroma = vec![0_u8; (copy_width * copy_height / 2) as usize];
            for row in 0..copy_height as usize {
                for column in 0..copy_width as usize {
                    let source_offset = (row * source.width as usize + column) * 3;
                    let rgb = &source.rgb[source_offset..source_offset + 3];
                    let (y, _, _) = rgb_to_limited_bt709(rgb[0], rgb[1], rgb[2]);
                    luma[row * copy_width as usize + column] = y;
                }
            }
            for row in (0..copy_height as usize).step_by(2) {
                for column in (0..copy_width as usize).step_by(2) {
                    let mut rgb = [0_u32; 3];
                    for sample_y in row..row + 2 {
                        for sample_x in column..column + 2 {
                            let offset = (sample_y * source.width as usize + sample_x) * 3;
                            rgb[0] += u32::from(source.rgb[offset]);
                            rgb[1] += u32::from(source.rgb[offset + 1]);
                            rgb[2] += u32::from(source.rgb[offset + 2]);
                        }
                    }
                    let (_, u, v) = rgb_to_limited_bt709(
                        (rgb[0] / 4) as u8,
                        (rgb[1] / 4) as u8,
                        (rgb[2] / 4) as u8,
                    );
                    let offset = row / 2 * copy_width as usize + column;
                    chroma[offset] = u;
                    chroma[offset + 1] = v;
                }
            }
            frame.overlay = Some(opennow_streamer_platform_linux::FrameOverlay {
                origin_x,
                origin_y,
                width: copy_width,
                height: copy_height,
                luma: opennow_streamer_platform_linux::FramePlane {
                    data: std::sync::Arc::from(luma),
                    stride: copy_width as usize,
                    rows: copy_height as usize,
                },
                chroma: opennow_streamer_platform_linux::FramePlane {
                    data: std::sync::Arc::from(chroma),
                    stride: copy_width as usize,
                    rows: copy_height as usize / 2,
                },
            });
            return;
        }
        let (y_planes, uv_planes) = frame.planes.split_at_mut(1);
        let y_stride = y_planes[0].stride;
        let uv_stride = uv_planes[0].stride;
        let y_plane = std::sync::Arc::make_mut(&mut y_planes[0].data);
        let uv_plane = std::sync::Arc::make_mut(&mut uv_planes[0].data);

        for row in 0..copy_height as usize {
            for column in 0..copy_width as usize {
                let source_offset = (row * source.width as usize + column) * 3;
                let Some(rgb) = source.rgb.get(source_offset..source_offset + 3) else {
                    return;
                };
                let (y, _, _) = rgb_to_limited_bt709(rgb[0], rgb[1], rgb[2]);
                let destination_offset =
                    (origin_y as usize + row) * y_stride + origin_x as usize + column;
                let Some(destination) = y_plane.get_mut(destination_offset) else {
                    return;
                };
                *destination = blend(*destination, y);
            }
        }

        for row in (0..copy_height as usize).step_by(2) {
            for column in (0..copy_width as usize).step_by(2) {
                let mut red = 0_u32;
                let mut green = 0_u32;
                let mut blue = 0_u32;
                let mut samples = 0_u32;
                for sample_y in row..(row + 2).min(copy_height as usize) {
                    for sample_x in column..(column + 2).min(copy_width as usize) {
                        let offset = (sample_y * source.width as usize + sample_x) * 3;
                        let Some(rgb) = source.rgb.get(offset..offset + 3) else {
                            return;
                        };
                        red += u32::from(rgb[0]);
                        green += u32::from(rgb[1]);
                        blue += u32::from(rgb[2]);
                        samples += 1;
                    }
                }
                let samples = samples.max(1);
                let (_, u, v) = rgb_to_limited_bt709(
                    (red / samples) as u8,
                    (green / samples) as u8,
                    (blue / samples) as u8,
                );
                let destination_offset =
                    (origin_y as usize / 2 + row / 2) * uv_stride + origin_x as usize + column;
                let Some(destination) =
                    uv_plane.get_mut(destination_offset..destination_offset + 2)
                else {
                    return;
                };
                destination[0] = blend(destination[0], u);
                destination[1] = blend(destination[1], v);
            }
        }
    }

    fn render(&mut self) {
        self.rendered = self.render_frame(1.0);
    }

    fn render_frame(&self, scale: f32) -> Option<OverlayFrame> {
        let Some((base_width, height)) = self.mode.size() else {
            return None;
        };
        let width = if self.mode == OverlayMode::Minimal {
            self.minimal_layout().width
        } else {
            base_width
        };
        let mut canvas = Raster::new_scaled(width, height, scale, BACKGROUND);
        canvas.rounded_border(
            0,
            0,
            width,
            height,
            if self.mode == OverlayMode::Minimal {
                17
            } else {
                18
            },
            BORDER,
        );
        match self.mode {
            OverlayMode::Hidden => {}
            OverlayMode::Minimal => self.draw_minimal(&mut canvas),
            OverlayMode::Full => self.draw_full(&mut canvas),
        }
        Some(OverlayFrame {
            width: canvas.width,
            height: canvas.height,
            rgb: canvas.pixels,
        })
    }

    fn draw_minimal(&self, canvas: &mut Raster) {
        let layout = self.minimal_layout();
        canvas.fill_circle(15, 17, 3, LED_GREEN);
        draw_value(canvas, 28, 8, "--", "ms", MINT);
        canvas.fill_rect(61, 10, 1, 14, BORDER);
        canvas.text(
            72,
            10,
            TextStyle::mono_medium(11.0, 14.0, 0.06),
            TEXT,
            &layout.region,
        );
        canvas.fill_rect(layout.codec_divider_x, 10, 1, 14, BORDER);
        canvas.text(
            layout.codec_x,
            10,
            TextStyle::mono_medium(11.0, 14.0, 0.06),
            SKY,
            &layout.codec,
        );
        canvas.fill_rect(layout.bitrate_divider_x, 10, 1, 14, BORDER);
        let bitrate = display_mbps(self.measured_bitrate_bps, self.stream.bitrate_bps);
        draw_value(canvas, layout.bitrate_x, 8, &bitrate, "Mbps", TEXT);
    }

    fn minimal_layout(&self) -> MinimalLayout {
        let region = self
            .stream
            .server_region
            .as_str()
            .trim()
            .to_ascii_uppercase();
        let codec = self.stream.codec.label().to_ascii_uppercase();
        let region_style = TextStyle::mono_medium(11.0, 14.0, 0.06);
        let codec_style = TextStyle::mono_medium(11.0, 14.0, 0.06);
        let codec_divider_x = 72 + measure_text(region_style, &region).ceil() as i32 + 11;
        let codec_x = codec_divider_x + 11;
        let bitrate_divider_x = codec_x + measure_text(codec_style, &codec).ceil() as i32 + 11;
        let bitrate_x = bitrate_divider_x + 11;
        let bitrate = display_mbps(self.measured_bitrate_bps, self.stream.bitrate_bps);
        let bitrate_width = measure_text(TextStyle::mono_bold(13.0, 16.0, 0.01), &bitrate)
            + 3.0
            + measure_text(TextStyle::mono_medium(9.0, 11.0, 0.04), "Mbps");
        let width =
            (bitrate_x + bitrate_width.ceil() as i32 + 13).max(MINIMAL_SIZE.0 as i32) as u32;
        MinimalLayout {
            width,
            region,
            codec,
            codec_divider_x,
            codec_x,
            bitrate_divider_x,
            bitrate_x,
        }
    }

    fn draw_full(&self, canvas: &mut Raster) {
        let fps = display_fps(self.measured_fps, self.stream.fps);
        canvas.fill_circle(16, 17, 3, LED_GREEN);
        canvas.text(
            26,
            11,
            TextStyle::mono_medium(10.0, 12.0, 0.08),
            TEXT,
            &format!(
                "{} · {}",
                compact_region_label(self.stream.server_region.as_str()),
                decoder_label(self.decoder)
            ),
        );
        canvas.text_right(
            279,
            11,
            TextStyle::mono_medium(10.0, 12.0, 0.04),
            MUTED,
            &elapsed_text(self.started_at.elapsed()),
        );

        canvas.divider(13, 32, 266);
        self.metric_row(
            canvas,
            42,
            "FPS",
            MINT,
            &fps.to_string(),
            "stream",
            &self.stream.fps.to_string(),
            "game",
        );
        self.metric_row(canvas, 62, "LATENCY", MINT, "--", "ms", "--", "ms p99");
        self.metric_row(canvas, 82, "PING", SKY, "--", "ms", "--", "ms jitter");
        let bitrate = display_mbps(self.measured_bitrate_bps, self.stream.bitrate_bps);
        let peak = display_mbps(self.peak_bitrate_bps, self.stream.bitrate_bps);
        self.metric_row(
            canvas,
            102,
            "BITRATE",
            SKY,
            &bitrate,
            "Mbps",
            &peak,
            "Mbps peak",
        );
        self.metric_row(
            canvas,
            122,
            "LOSS",
            SKY,
            "--",
            "%",
            &self
                .dropped_frames
                .saturating_add(self.presentation_skips)
                .to_string(),
            "dropped",
        );

        canvas.divider(13, 148, 266);
        let codec = self.stream.codec.label().to_ascii_uppercase();
        let bit_depth = format!("{}-bit", self.stream.color_quality.bit_depth());
        self.metric_row(
            canvas,
            158,
            "CODEC",
            VIOLET,
            &codec,
            &bit_depth,
            decoder_label(self.decoder),
            "hw",
        );
        self.metric_row(
            canvas,
            178,
            "OUTPUT",
            VIOLET,
            &self.stream.height.to_string(),
            "p",
            &self.stream.fps.to_string(),
            if self.stream.cloud_gsync {
                "Hz · VRR"
            } else {
                "Hz"
            },
        );

        self.draw_graph(
            canvas,
            204,
            "FRAMETIME",
            MINT,
            MINT_DIM,
            &self.frame_time_history,
            "ms",
        );
        // NVST does not currently expose a trustworthy RTT sample. Preserve the
        // Paper panel's latency lane, but show it as unavailable instead of
        // inventing a number from render pacing.
        self.draw_graph(canvas, 263, "LATENCY", SKY, SKY_DIM, &VecDeque::new(), "ms");

        canvas.divider(13, 322, 266);
        canvas.text(
            13,
            332,
            TextStyle::mono_medium(9.0, 11.0, 0.06),
            FAINT,
            "F3 · PS · XB — CYCLE",
        );
        canvas.text_right(
            279,
            332,
            TextStyle::mono_medium(9.0, 11.0, 0.06),
            FAINT,
            "SHIFT+F3 COPY",
        );
    }

    #[allow(clippy::too_many_arguments)]
    fn metric_row(
        &self,
        canvas: &mut Raster,
        y: i32,
        label: &str,
        label_color: Rgb,
        main: &str,
        main_unit: &str,
        secondary: &str,
        secondary_unit: &str,
    ) {
        canvas.text(
            13,
            y,
            TextStyle::label_bold(11.0, 16.0, 0.06),
            label_color,
            label,
        );
        draw_value_right(
            canvas,
            183,
            y,
            main,
            main_unit,
            if label == "LATENCY" { MINT } else { TEXT },
        );
        draw_value_right(canvas, 279, y, secondary, secondary_unit, TEXT);
    }

    fn draw_graph(
        &self,
        canvas: &mut Raster,
        top: i32,
        label: &str,
        label_color: Rgb,
        bar_color: Rgb,
        samples: &VecDeque<f32>,
        unit: &str,
    ) {
        canvas.divider(13, top, 266);
        canvas.text(
            13,
            top + 10,
            TextStyle::mono_medium(9.0, 11.0, 0.08),
            label_color,
            label,
        );
        let range = sample_range(samples)
            .map(|(minimum, maximum)| format!("min {minimum:.1} · max {maximum:.1} {unit}"))
            .unwrap_or_else(|| "unavailable".to_owned());
        canvas.text_right(
            279,
            top + 10,
            TextStyle::mono_medium(9.0, 11.0, 0.04),
            MUTED,
            &range,
        );
        let maximum = samples.iter().copied().fold(0.0_f32, f32::max).max(1.0);
        let missing = 24_usize.saturating_sub(samples.len());
        for (index, sample) in samples.iter().enumerate() {
            let height = ((*sample / maximum) * 20.0).round().clamp(2.0, 24.0) as u32;
            let display_index = missing + index;
            canvas.fill_rect(
                13 + display_index as i32 * 11,
                top + 50 - height as i32,
                9,
                height,
                if display_index == 7 || display_index == 11 {
                    YELLOW
                } else {
                    bar_color
                },
            );
        }
    }
}

fn draw_value(canvas: &mut Raster, x: i32, y: i32, value: &str, unit: &str, value_color: Rgb) {
    let value_style = TextStyle::mono_bold(13.0, 16.0, 0.01);
    canvas.text(x, y, value_style, value_color, value);
    let unit_x = x + canvas.measure(value_style, value).ceil() as i32 + 3;
    canvas.text(
        unit_x,
        y + 4,
        TextStyle::mono_medium(9.0, 11.0, 0.04),
        MUTED,
        unit,
    );
}

fn draw_value_right(
    canvas: &mut Raster,
    right: i32,
    y: i32,
    value: &str,
    unit: &str,
    value_color: Rgb,
) {
    let value_style = TextStyle::mono_bold(13.0, 16.0, 0.01);
    let unit_style = TextStyle::mono_medium(9.0, 11.0, 0.04);
    let value_width = canvas.measure(value_style, value);
    let unit_width = canvas.measure(unit_style, unit);
    let x = right - (value_width + unit_width + 3.0).ceil() as i32;
    canvas.text(x, y, value_style, value_color, value);
    canvas.text(
        x + value_width.ceil() as i32 + 3,
        y + 4,
        unit_style,
        MUTED,
        unit,
    );
}

fn display_mbps(measured: f32, configured: u32) -> String {
    let value = if measured > 0.0 {
        measured
    } else {
        configured as f32
    } / 1_000_000.0;
    format!("{:.0}", value.max(0.0))
}

fn compact_region_label(region: &str) -> String {
    let trimmed = region.trim();
    if let Some(start) = trimmed.rfind('(')
        && let Some(end) = trimmed[start + 1..].find(')')
    {
        let code = trimmed[start + 1..start + 1 + end].trim();
        if !code.is_empty() {
            return elide_label(&code.to_ascii_uppercase(), 8);
        }
    }
    elide_label(&trimmed.to_ascii_uppercase(), 8)
}

fn elide_label(value: &str, maximum_characters: usize) -> String {
    let value = value.trim();
    if value.chars().count() <= maximum_characters {
        return value.to_owned();
    }
    if maximum_characters <= 3 {
        return value.chars().take(maximum_characters).collect();
    }
    let mut elided = value
        .chars()
        .take(maximum_characters.saturating_sub(3))
        .collect::<String>();
    elided.push_str("...");
    elided
}

fn decoder_label(decoder: &str) -> &str {
    if decoder.contains("NVDEC") {
        "NVDEC"
    } else if decoder.contains("D3D11") || decoder.contains("DXVA") {
        "D3D11"
    } else if decoder.contains("VIDEOTOOLBOX") {
        "VTB"
    } else if decoder.contains("VAAPI") {
        "VAAPI"
    } else {
        decoder
            .split(['/', ' '])
            .find(|part| !part.is_empty())
            .unwrap_or("NATIVE")
    }
}

fn elapsed_text(elapsed: Duration) -> String {
    let total = elapsed.as_secs();
    format!("{}:{:02}:{:02}", total / 3_600, total / 60 % 60, total % 60)
}

fn sample_range(samples: &VecDeque<f32>) -> Option<(f32, f32)> {
    let mut values = samples.iter().copied().filter(|value| value.is_finite());
    let first = values.next()?;
    Some(values.fold((first, first), |(minimum, maximum), value| {
        (minimum.min(value), maximum.max(value))
    }))
}

fn measured_bitrate_bps(bytes: u64, elapsed: Duration) -> f32 {
    bytes as f32 * 8.0 / elapsed.as_secs_f32().max(0.001)
}

#[cfg(any(target_os = "linux", test))]
fn overlay_origin(
    mode: OverlayMode,
    position: StatsOverlayPosition,
    width: u32,
    height: u32,
    frame: &OverlayFrame,
) -> Option<(u32, u32)> {
    mode.size()?;
    let max_x = width.saturating_sub(frame.width);
    let max_y = height.saturating_sub(frame.height);
    Some(match position {
        StatsOverlayPosition::TopLeft => (EDGE_MARGIN.min(max_x), EDGE_MARGIN.min(max_y)),
        StatsOverlayPosition::TopRight => {
            (max_x.saturating_sub(EDGE_MARGIN), EDGE_MARGIN.min(max_y))
        }
        StatsOverlayPosition::BottomLeft => {
            (EDGE_MARGIN.min(max_x), max_y.saturating_sub(EDGE_MARGIN))
        }
        StatsOverlayPosition::BottomRight => (
            max_x.saturating_sub(EDGE_MARGIN),
            max_y.saturating_sub(EDGE_MARGIN),
        ),
    })
}

#[cfg(any(target_os = "linux", test))]
fn blend(destination: u8, source: u8) -> u8 {
    let inverse = 255 - OVERLAY_ALPHA;
    ((u16::from(source) * OVERLAY_ALPHA + u16::from(destination) * inverse + 127) / 255) as u8
}

#[cfg(target_os = "linux")]
fn rgb_to_limited_bt709(red: u8, green: u8, blue: u8) -> (u8, u8, u8) {
    let red = i32::from(red);
    let green = i32::from(green);
    let blue = i32::from(blue);
    let y = ((47 * red + 157 * green + 16 * blue + 128) >> 8) + 16;
    let u = ((-26 * red - 87 * green + 112 * blue + 128) >> 8) + 128;
    let v = ((112 * red - 102 * green - 10 * blue + 128) >> 8) + 128;
    (clamp_byte(y), clamp_byte(u), clamp_byte(v))
}

#[cfg(target_os = "linux")]
fn clamp_byte(value: i32) -> u8 {
    value.clamp(0, 255) as u8
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

struct Raster {
    width: u32,
    height: u32,
    pixels: Vec<u8>,
    scale: f32,
}

impl Raster {
    #[cfg(test)]
    fn new(width: u32, height: u32, color: Rgb) -> Self {
        Self::new_scaled(width, height, 1.0, color)
    }

    fn new_scaled(width: u32, height: u32, scale: f32, color: Rgb) -> Self {
        let scale = scale.clamp(0.5, 2.0);
        let width = (width as f32 * scale).round().max(1.0) as u32;
        let height = (height as f32 * scale).round().max(1.0) as u32;
        let mut pixels = vec![0_u8; width as usize * height as usize * 3];
        for pixel in pixels.chunks_exact_mut(3) {
            pixel.copy_from_slice(&color);
        }
        Self {
            width,
            height,
            pixels,
            scale,
        }
    }

    fn fill_rect(&mut self, x: i32, y: i32, width: u32, height: u32, color: Rgb) {
        let x = (x as f32 * self.scale).round() as i32;
        let y = (y as f32 * self.scale).round() as i32;
        let width = (width as f32 * self.scale).round().max(1.0) as u32;
        let height = (height as f32 * self.scale).round().max(1.0) as u32;
        self.fill_rect_physical(x, y, width, height, color);
    }

    fn fill_rect_physical(&mut self, x: i32, y: i32, width: u32, height: u32, color: Rgb) {
        let start_x = x.max(0) as u32;
        let start_y = y.max(0) as u32;
        let end_x = x.saturating_add_unsigned(width).max(0) as u32;
        let end_y = y.saturating_add_unsigned(height).max(0) as u32;
        for row in start_y.min(self.height)..end_y.min(self.height) {
            for column in start_x.min(self.width)..end_x.min(self.width) {
                let offset = (row as usize * self.width as usize + column as usize) * 3;
                self.pixels[offset..offset + 3].copy_from_slice(&color);
            }
        }
    }

    fn fill_rounded_rect(
        &mut self,
        x: i32,
        y: i32,
        width: u32,
        height: u32,
        radius: i32,
        color: Rgb,
    ) {
        let x = (x as f32 * self.scale).round() as i32;
        let y = (y as f32 * self.scale).round() as i32;
        let width = (width as f32 * self.scale).round().max(1.0) as u32;
        let height = (height as f32 * self.scale).round().max(1.0) as u32;
        let radius = (radius.max(0) as f32 * self.scale).round() as u32;
        for local_y in 0..height {
            for local_x in 0..width {
                if inside_rounded_rect(local_x, local_y, width, height, radius) {
                    self.set_pixel(x + local_x as i32, y + local_y as i32, color);
                }
            }
        }
    }

    fn fill_circle(&mut self, center_x: i32, center_y: i32, radius: i32, color: Rgb) {
        let center_x = (center_x as f32 * self.scale).round() as i32;
        let center_y = (center_y as f32 * self.scale).round() as i32;
        let radius = (radius.max(0) as f32 * self.scale).round() as i32;
        for offset_y in -radius..=radius {
            for offset_x in -radius..=radius {
                if offset_x * offset_x + offset_y * offset_y <= radius * radius {
                    self.set_pixel(center_x + offset_x, center_y + offset_y, color);
                }
            }
        }
    }

    fn rounded_border(&mut self, x: i32, y: i32, width: u32, height: u32, radius: i32, color: Rgb) {
        let x = (x as f32 * self.scale).round() as i32;
        let y = (y as f32 * self.scale).round() as i32;
        let width = (width as f32 * self.scale).round().max(1.0) as u32;
        let height = (height as f32 * self.scale).round().max(1.0) as u32;
        let radius = (radius.max(0) as f32 * self.scale).round() as u32;
        let thickness = self.scale.round().max(1.0) as u32;
        for local_y in 0..height {
            for local_x in 0..width {
                if !inside_rounded_rect(local_x, local_y, width, height, radius) {
                    continue;
                }
                let inside_inner = local_x >= thickness
                    && local_y >= thickness
                    && local_x < width.saturating_sub(thickness)
                    && local_y < height.saturating_sub(thickness)
                    && inside_rounded_rect(
                        local_x - thickness,
                        local_y - thickness,
                        width.saturating_sub(thickness * 2),
                        height.saturating_sub(thickness * 2),
                        radius.saturating_sub(thickness),
                    );
                if !inside_inner {
                    self.set_pixel(x + local_x as i32, y + local_y as i32, color);
                }
            }
        }
    }

    fn set_pixel(&mut self, x: i32, y: i32, color: Rgb) {
        if x < 0 || y < 0 || x >= self.width as i32 || y >= self.height as i32 {
            return;
        }
        let offset = (y as usize * self.width as usize + x as usize) * 3;
        self.pixels[offset..offset + 3].copy_from_slice(&color);
    }

    fn divider(&mut self, x: i32, y: i32, width: u32) {
        self.fill_rect(x, y, width, 1, BORDER);
    }

    fn measure(&self, style: TextStyle, text: &str) -> f32 {
        measure_text(style.scaled(self.scale), text) / self.scale
    }

    fn text_right(&mut self, right: i32, y: i32, style: TextStyle, color: Rgb, text: &str) {
        self.text(
            right - self.measure(style, text).ceil() as i32,
            y,
            style,
            color,
            text,
        );
    }

    fn text(&mut self, x: i32, y: i32, style: TextStyle, color: Rgb, text: &str) {
        let x = (x as f32 * self.scale).round() as i32;
        let y = (y as f32 * self.scale).round() as i32;
        let style = style.scaled(self.scale);
        let font = style.font();
        let line = font.horizontal_line_metrics(style.size);
        let (ascent, descent) = line
            .map(|metrics| (metrics.ascent, metrics.descent))
            .unwrap_or((style.size, 0.0));
        let content_height = ascent - descent;
        let baseline = y as f32 + ((style.line_height - content_height) * 0.5).max(0.0) + ascent;
        let mut cursor_x = x as f32;
        let characters = text.chars().count();
        for (index, character) in text.chars().enumerate() {
            let (metrics, bitmap) = font.rasterize(character, style.size);
            let bitmap_x = cursor_x.round() as i32 + metrics.xmin;
            let bitmap_y = (baseline - metrics.ymin as f32 - metrics.height as f32).round() as i32;
            for row in 0..metrics.height {
                for column in 0..metrics.width {
                    let alpha = bitmap[row * metrics.width + column];
                    if alpha != 0 {
                        self.blend_pixel(
                            bitmap_x + column as i32,
                            bitmap_y + row as i32,
                            color,
                            alpha,
                        );
                        for bold_offset in 1..=style.faux_bold_pixels {
                            self.blend_pixel(
                                bitmap_x + column as i32 + i32::from(bold_offset),
                                bitmap_y + row as i32,
                                color,
                                if bold_offset == style.faux_bold_pixels {
                                    alpha / 2
                                } else {
                                    alpha
                                },
                            );
                        }
                    }
                }
            }
            cursor_x += metrics.advance_width;
            if index + 1 < characters {
                cursor_x += style.letter_spacing_px();
            }
        }
    }

    fn blend_pixel(&mut self, x: i32, y: i32, color: Rgb, alpha: u8) {
        if x < 0 || y < 0 || x >= self.width as i32 || y >= self.height as i32 {
            return;
        }
        let offset = (y as usize * self.width as usize + x as usize) * 3;
        for channel in 0..3 {
            let destination = u16::from(self.pixels[offset + channel]);
            let source = u16::from(color[channel]);
            let alpha = u16::from(alpha);
            self.pixels[offset + channel] =
                ((source * alpha + destination * (255 - alpha) + 127) / 255) as u8;
        }
    }
}

fn display_fps(measured: f32, target: u32) -> u32 {
    if measured > 1.0 {
        measured.round() as u32
    } else {
        target
    }
}

#[derive(Clone, Copy)]
enum FontFace {
    MonoMedium,
    MonoBold,
    Label,
}

#[derive(Clone, Copy)]
struct TextStyle {
    face: FontFace,
    size: f32,
    line_height: f32,
    letter_spacing_em: f32,
    faux_bold_pixels: u8,
}

impl TextStyle {
    const fn mono_medium(size: f32, line_height: f32, letter_spacing_em: f32) -> Self {
        Self {
            face: FontFace::MonoMedium,
            size,
            line_height,
            letter_spacing_em,
            faux_bold_pixels: 0,
        }
    }

    const fn mono_bold(size: f32, line_height: f32, letter_spacing_em: f32) -> Self {
        Self {
            face: FontFace::MonoBold,
            size,
            line_height,
            letter_spacing_em,
            faux_bold_pixels: 0,
        }
    }

    const fn label_bold(size: f32, line_height: f32, letter_spacing_em: f32) -> Self {
        Self {
            face: FontFace::Label,
            size,
            line_height,
            letter_spacing_em,
            faux_bold_pixels: 1,
        }
    }

    const fn label_black(size: f32, line_height: f32, letter_spacing_em: f32) -> Self {
        Self {
            face: FontFace::Label,
            size,
            line_height,
            letter_spacing_em,
            faux_bold_pixels: 2,
        }
    }

    fn font(self) -> &'static Font {
        match self.face {
            FontFace::MonoMedium => font(&MONO_MEDIUM, MONO_MEDIUM_BYTES),
            FontFace::MonoBold => font(&MONO_BOLD, MONO_BOLD_BYTES),
            FontFace::Label => font(&LABEL, LABEL_BYTES),
        }
    }

    fn letter_spacing_px(self) -> f32 {
        self.size * self.letter_spacing_em
    }

    fn scaled(self, scale: f32) -> Self {
        Self {
            size: self.size * scale,
            line_height: self.line_height * scale,
            ..self
        }
    }
}

fn measure_text(style: TextStyle, text: &str) -> f32 {
    let font = style.font();
    let characters = text.chars().count();
    text.chars()
        .enumerate()
        .map(|(index, character)| {
            font.metrics(character, style.size).advance_width
                + if index + 1 < characters {
                    style.letter_spacing_px()
                } else {
                    0.0
                }
        })
        .sum()
}

fn font(slot: &'static OnceLock<Font>, bytes: &'static [u8]) -> &'static Font {
    slot.get_or_init(|| {
        Font::from_bytes(bytes, FontSettings::default())
            .expect("bundled OpenNOW overlay font must be valid")
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn overlay_modes_cycle_predictably() {
        assert_eq!(OverlayMode::Hidden.next(), OverlayMode::Minimal);
        assert_eq!(OverlayMode::Minimal.next(), OverlayMode::Full);
        assert_eq!(OverlayMode::Full.next(), OverlayMode::Hidden);
        assert_eq!(OverlayMode::Minimal.size(), Some((244, 34)));
        assert_eq!(OverlayMode::Full.size(), Some((292, 358)));
    }

    #[test]
    fn paper_typography_is_rendered_from_the_bundled_fonts() {
        let mut canvas = Raster::new(244, 34, BACKGROUND);
        let style = TextStyle::mono_bold(13.0, 16.0, 0.01);
        assert!(canvas.measure(style, "72") > 14.0);
        canvas.text(10, 8, style, TEXT, "72 Mbps");
        assert!(canvas.pixels.chunks_exact(3).any(|pixel| pixel == TEXT));
    }

    #[test]
    fn native_menu_uses_the_paper_dimensions_and_live_region() {
        let stream = MediaStreamConfig {
            server_region: crate::media::StreamRegionLabel::new("Amsterdam (AMS-03)"),
            ..MediaStreamConfig::default()
        };
        let overlay = NativeStatsOverlay::new(stream, "D3D11 / NVDEC");
        let frame = overlay.menu_frame(1.0);
        assert_eq!((frame.width, frame.height), MENU_SIZE);
        assert_eq!(
            compact_region_label(stream.server_region.as_str()),
            "AMS-03"
        );
        assert!(frame.rgb.chunks_exact(3).any(|pixel| pixel == MINT));

        let scaled = overlay.menu_frame(1.5);
        assert_eq!((scaled.width, scaled.height), (1140, 615));
        let confirmation = overlay.stop_confirmation_frame(1.25);
        assert_eq!((confirmation.width, confirmation.height), (650, 295));
        assert!(
            confirmation
                .rgb
                .chunks_exact(3)
                .any(|pixel| pixel == DANGER)
        );
    }

    #[test]
    fn compact_hud_expands_to_preserve_the_complete_region_label() {
        let stream = MediaStreamConfig {
            show_stats: true,
            server_region: crate::media::StreamRegionLabel::new("EU-NETHERLANDS-SOUTH"),
            ..MediaStreamConfig::default()
        };
        let overlay = NativeStatsOverlay::new(stream, "D3D11");
        let frame = overlay.frame().expect("compact overlay frame");
        assert!(frame.width > MINIMAL_SIZE.0);
        assert_eq!(overlay.minimal_layout().region, "EU-NETHERLANDS-SOUTH");
    }

    #[test]
    fn expanded_header_region_labels_use_the_server_code() {
        assert_eq!(compact_region_label("Tokyo (TYO-01)"), "TYO-01");
        assert_eq!(compact_region_label("EU Northwest"), "EU NO...");
    }

    #[test]
    #[ignore = "writes a visual QA snapshot when requested"]
    fn write_paper_overlay_snapshot() {
        let path = std::env::var_os("OPENNOW_OVERLAY_SNAPSHOT")
            .expect("OPENNOW_OVERLAY_SNAPSHOT must name the output PNG");
        let mut overlay = NativeStatsOverlay::new(MediaStreamConfig::default(), "D3D11 / NVDEC");
        overlay.toggle();
        overlay.toggle();
        let frame = overlay.frame().expect("expanded overlay frame");
        image::save_buffer(
            path,
            &frame.rgb,
            frame.width,
            frame.height,
            image::ColorType::Rgb8,
        )
        .expect("overlay snapshot");
    }

    #[test]
    #[ignore = "writes a visual QA snapshot when requested"]
    fn write_native_menu_snapshot() {
        let path = std::env::var_os("OPENNOW_MENU_SNAPSHOT")
            .expect("OPENNOW_MENU_SNAPSHOT must name the output PNG");
        let scale = std::env::var("OPENNOW_MENU_SCALE")
            .ok()
            .and_then(|value| value.parse::<f32>().ok())
            .unwrap_or(1.0);
        let stream = MediaStreamConfig {
            codec: crate::media::MediaVideoCodec::H265,
            width: 2560,
            height: 1440,
            fps: 120,
            bitrate_bps: 75_000_000,
            server_region: crate::media::StreamRegionLabel::new("Amsterdam (AMS-03)"),
            ..MediaStreamConfig::default()
        };
        let frame = NativeStatsOverlay::new(stream, "D3D11 / NVDEC").menu_frame(scale);
        image::save_buffer(
            path,
            &frame.rgb,
            frame.width,
            frame.height,
            image::ColorType::Rgb8,
        )
        .expect("menu snapshot");
    }

    #[test]
    #[ignore = "writes a visual QA snapshot when requested"]
    fn write_compact_region_snapshot() {
        let path = std::env::var_os("OPENNOW_COMPACT_SNAPSHOT")
            .expect("OPENNOW_COMPACT_SNAPSHOT must name the output PNG");
        let stream = MediaStreamConfig {
            show_stats: true,
            codec: crate::media::MediaVideoCodec::H264,
            bitrate_bps: 6_000_000,
            server_region: crate::media::StreamRegionLabel::new("EU-NETHERLANDS-SOUTH"),
            ..MediaStreamConfig::default()
        };
        let overlay = NativeStatsOverlay::new(stream, "D3D11");
        let frame = overlay.scaled_frame(1.2).expect("compact frame");
        image::save_buffer(
            path,
            &frame.rgb,
            frame.width,
            frame.height,
            image::ColorType::Rgb8,
        )
        .expect("compact snapshot");
    }

    #[test]
    #[ignore = "writes a visual QA snapshot when requested"]
    fn write_stop_confirmation_snapshot() {
        let path = std::env::var_os("OPENNOW_STOP_CONFIRM_SNAPSHOT")
            .expect("OPENNOW_STOP_CONFIRM_SNAPSHOT must name the output PNG");
        let stream = MediaStreamConfig {
            server_region: crate::media::StreamRegionLabel::new("EU-NETHERLANDS-SOUTH"),
            ..MediaStreamConfig::default()
        };
        let overlay = NativeStatsOverlay::new(stream, "D3D11");
        let frame = overlay.stop_confirmation_frame(1.18);
        image::save_buffer(
            path,
            &frame.rgb,
            frame.width,
            frame.height,
            image::ColorType::Rgb8,
        )
        .expect("stop confirmation snapshot");
    }

    #[test]
    fn fps_uses_target_until_live_sample_exists() {
        assert_eq!(display_fps(0.0, 120), 120);
        assert_eq!(display_fps(117.4, 120), 117);
    }

    #[test]
    fn measured_bitrate_uses_encoded_video_bytes_over_sample_time() {
        assert_eq!(
            measured_bitrate_bps(1_000_000, Duration::from_secs(1)),
            8_000_000.0,
        );
        assert_eq!(
            measured_bitrate_bps(500_000, Duration::from_millis(250)),
            16_000_000.0,
        );
    }

    #[test]
    fn rgb_overlay_is_composited_in_the_expected_corner() {
        let mut overlay = NativeStatsOverlay::new(MediaStreamConfig::default(), "TEST");
        overlay.toggle();
        let mut destination = vec![0_u8; 640 * 360 * 3];
        overlay.composite_rgb24(&mut destination, 640, 360, 640 * 3);
        let origin = (EDGE_MARGIN * 640 + 640 - MINIMAL_SIZE.0 - EDGE_MARGIN) as usize * 3;
        assert_ne!(&destination[origin..origin + 3], &[0, 0, 0]);
        assert_eq!(&destination[..3], &[0, 0, 0]);
    }

    #[cfg(target_os = "linux")]
    #[test]
    fn nv12_overlay_updates_luma_and_chroma_without_touching_other_pixels() {
        use std::sync::Arc;

        use opennow_streamer_platform_linux::{DecodedVideoFrame, FramePlane, StreamFormat};

        let mut overlay = NativeStatsOverlay::new(MediaStreamConfig::default(), "TEST");
        overlay.toggle();
        let mut frame = DecodedVideoFrame {
            format: StreamFormat::h264_default(640, 360).unwrap(),
            planes: vec![
                FramePlane {
                    data: Arc::from(vec![16_u8; 640 * 360]),
                    stride: 640,
                    rows: 360,
                },
                FramePlane {
                    data: Arc::from(vec![128_u8; 640 * 180]),
                    stride: 640,
                    rows: 180,
                },
            ],
            dmabuf: None,
            vulkan: None,
            overlay: None,
            timestamp_us: 0,
        };

        overlay.composite_linux_frame(&mut frame);

        let origin_x = EDGE_MARGIN as usize;
        let origin_y = 360 - MINIMAL_SIZE.1 as usize - EDGE_MARGIN as usize;
        let luma_origin = origin_y * 640 + origin_x;
        let chroma_origin = origin_y / 2 * 640 + origin_x;
        assert_ne!(frame.planes[0].data[luma_origin], 16);
        assert_ne!(
            &frame.planes[1].data[chroma_origin..chroma_origin + 2],
            &[128, 128]
        );
        assert_eq!(frame.planes[0].data[0], 16);
        assert_eq!(&frame.planes[1].data[..2], &[128, 128]);
    }
}
