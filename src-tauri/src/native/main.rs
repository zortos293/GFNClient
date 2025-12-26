//! GFN Native Streaming Client
//!
//! A full-featured native client for GeForce NOW streaming.
//! Handles signaling, WebRTC, video decoding, audio playback, and input.

#[path = "input.rs"]
pub mod input;
#[path = "signaling.rs"]
mod signaling;
#[path = "webrtc_client.rs"]
mod webrtc_client;
#[path = "gpu_renderer.rs"]
pub mod gpu_renderer;
#[path = "ffmpeg_decoder.rs"]
pub mod ffmpeg_decoder;
#[path = "hdr_detection.rs"]
pub mod hdr_detection;
#[path = "hdr_signaling.rs"]
mod hdr_signaling;
#[path = "test_mode.rs"]
pub mod test_mode;
#[path = "bridge.rs"]
pub mod bridge;

use std::sync::Arc;
use std::time::Instant;

use anyhow::Result;
use clap::Parser;
use log::{info, warn, error, debug};
use parking_lot::Mutex;
use tokio::sync::mpsc;
use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::{DeviceEvent, ElementState, WindowEvent, MouseButton};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{CursorGrabMode, Window, WindowId};

use input::{InputEncoder, InputEvent};
use signaling::{GfnSignaling, SignalingEvent};
use webrtc_client::{WebRtcClient, WebRtcEvent};
use webrtc::ice_transport::ice_server::RTCIceServer;
use gpu_renderer::{GpuRenderer, RendererConfig, YuvFrame};
use ffmpeg_decoder::{FfmpegDecoder, VideoCodec, RtpReassembler};

/// GFN Native Streaming Client
#[derive(Parser, Debug, Clone)]
#[command(author, version, about, long_about = None)]
pub struct Args {
    /// GFN streaming server IP address
    #[arg(short, long)]
    pub server: Option<String>,

    /// Session ID from GFN
    #[arg(short = 'i', long)]
    pub session_id: Option<String>,

    /// Window width
    #[arg(long, default_value = "1920")]
    pub width: u32,

    /// Window height
    #[arg(long, default_value = "1080")]
    pub height: u32,

    /// Enable debug logging
    #[arg(short, long)]
    pub debug: bool,

    /// Run in test mode (no GFN session required)
    #[arg(short, long)]
    pub test: bool,

    /// Test duration in seconds
    #[arg(long, default_value = "10")]
    pub test_duration: u64,
}

/// Shared state between async tasks and window
pub struct SharedState {
    pub video_frame: Option<YuvFrame>,
    pub connected: bool,
    pub signaling_connected: bool,
    pub webrtc_connected: bool,
    pub input_ready: bool,
    pub stats: StreamingStats,
    pub status_message: String,
    pub negotiated_codec: Option<VideoCodec>,
    pub last_fps_update: std::time::Instant,
    pub frame_count: u32,
    pub current_fps: f32,
}

impl Default for SharedState {
    fn default() -> Self {
        Self {
            video_frame: None,
            connected: false,
            signaling_connected: false,
            webrtc_connected: false,
            input_ready: false,
            stats: StreamingStats::default(),
            status_message: "Initializing...".to_string(),
            negotiated_codec: None,
            last_fps_update: std::time::Instant::now(),
            frame_count: 0,
            current_fps: 0.0,
        }
    }
}

#[derive(Default)]
struct StreamingStats {
    frames_received: u64,
    frames_decoded: u64,
    bytes_received: u64,
    audio_packets: u64,
}

/// Main application state
pub struct GfnApp {
    window: Option<Arc<Window>>,
    renderer: Option<GpuRenderer>,
    shared_state: Arc<Mutex<SharedState>>,
    input_tx: mpsc::Sender<InputEvent>,
    mouse_captured: bool,
    start_time: Instant,
    args: Args,
    hdr_caps: hdr_detection::HdrCapabilities,
}

impl GfnApp {
    pub fn new(args: Args, input_tx: mpsc::Sender<InputEvent>, shared_state: Arc<Mutex<SharedState>>, hdr_caps: hdr_detection::HdrCapabilities) -> Self {
        Self {
            window: None,
            renderer: None,
            shared_state,
            input_tx,
            mouse_captured: false,
            start_time: Instant::now(),
            args,
            hdr_caps,
        }
    }

    fn get_timestamp_us(&self) -> u64 {
        self.start_time.elapsed().as_micros() as u64
    }

    fn capture_mouse(&mut self) {
        if let Some(window) = &self.window {
            // Try confined first, then locked
            if window.set_cursor_grab(CursorGrabMode::Confined).is_err() {
                let _ = window.set_cursor_grab(CursorGrabMode::Locked);
            }
            window.set_cursor_visible(false);
            self.mouse_captured = true;
            info!("Mouse captured");
        }
    }

    fn release_mouse(&mut self) {
        if let Some(window) = &self.window {
            let _ = window.set_cursor_grab(CursorGrabMode::None);
            window.set_cursor_visible(true);
            self.mouse_captured = false;
            info!("Mouse released");
        }
    }

    fn render_frame(&mut self) {
        let Some(renderer) = &mut self.renderer else { return };

        // Get video frame if available
        let mut state = self.shared_state.lock();

        if let Some(frame) = state.video_frame.take() {
            // Render the YUV frame using GPU
            if let Err(e) = renderer.render_frame(&frame) {
                warn!("Failed to render frame: {}", e);
            }
            // Put the frame back (we took it with take())
            state.video_frame = Some(frame);

            // Update FPS counter
            state.frame_count += 1;
            let elapsed = state.last_fps_update.elapsed().as_secs_f32();
            if elapsed >= 1.0 {
                state.current_fps = state.frame_count as f32 / elapsed;
                state.frame_count = 0;
                state.last_fps_update = std::time::Instant::now();

                // Update window title with status
                self.update_window_title();
            }
        }
    }

    fn update_window_title(&self) {
        let Some(window) = &self.window else { return };
        let state = self.shared_state.lock();

        let hdr_status = if self.hdr_caps.is_supported() {
            format!("HDR ({:.0} nits)", self.hdr_caps.max_luminance())
        } else {
            "SDR".to_string()
        };

        let codec = match state.negotiated_codec {
            Some(VideoCodec::H265) => "H.265",
            Some(VideoCodec::H264) => "H.264",
            _ => "N/A",
        };

        let title = format!(
            "GFN Client - {} | {} | {:.1} FPS | {}x{}",
            hdr_status,
            codec,
            state.current_fps,
            self.args.width,
            self.args.height
        );

        window.set_title(&title);
    }

    fn handle_keyboard(&mut self, key: PhysicalKey, state: ElementState) {
        let PhysicalKey::Code(keycode) = key else { return };

        // ESC releases mouse or closes window
        if keycode == KeyCode::Escape && state == ElementState::Pressed {
            if self.mouse_captured {
                self.release_mouse();
            }
            return;
        }

        if !self.mouse_captured {
            return;
        }

        // Convert to Windows VK code and scancode
        let vk = keycode_to_vk(keycode);
        let scan = keycode_to_scan(keycode);

        let event = match state {
            ElementState::Pressed => InputEvent::KeyDown {
                keycode: vk,
                scancode: scan,
                modifiers: 0,
                timestamp_us: self.get_timestamp_us(),
            },
            ElementState::Released => InputEvent::KeyUp {
                keycode: vk,
                scancode: scan,
                modifiers: 0,
                timestamp_us: self.get_timestamp_us(),
            },
        };

        let _ = self.input_tx.try_send(event);
    }

    fn handle_mouse_button(&mut self, button: MouseButton, state: ElementState) {
        // Capture on click if not captured
        if !self.mouse_captured && state == ElementState::Pressed {
            self.capture_mouse();
            return;
        }

        if !self.mouse_captured {
            return;
        }

        let btn = match button {
            MouseButton::Left => 0,
            MouseButton::Right => 1,
            MouseButton::Middle => 2,
            MouseButton::Back => 3,
            MouseButton::Forward => 4,
            MouseButton::Other(n) => n as u8,
        };

        let event = match state {
            ElementState::Pressed => InputEvent::MouseButtonDown {
                button: btn,
                timestamp_us: self.get_timestamp_us(),
            },
            ElementState::Released => InputEvent::MouseButtonUp {
                button: btn,
                timestamp_us: self.get_timestamp_us(),
            },
        };

        let _ = self.input_tx.try_send(event);
    }

    fn handle_mouse_wheel(&mut self, delta: f32) {
        if !self.mouse_captured {
            return;
        }

        let event = InputEvent::MouseWheel {
            delta: (delta * 120.0) as i16,
            timestamp_us: self.get_timestamp_us(),
        };

        let _ = self.input_tx.try_send(event);
    }
}

impl ApplicationHandler for GfnApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() {
            return;
        }

        let window_attrs = Window::default_attributes()
            .with_title(format!("GFN Native Client - {}", self.args.server.as_deref().unwrap_or("Test Mode")))
            .with_inner_size(LogicalSize::new(self.args.width, self.args.height));

        let window = Arc::new(event_loop.create_window(window_attrs).unwrap());

        // Create GPU renderer with HDR or SDR configuration based on display capabilities
        let renderer_config = if self.hdr_caps.is_supported() {
            info!("Creating HDR renderer (max_luminance={:.1} nits)", self.hdr_caps.max_luminance());
            RendererConfig {
                hdr_enabled: true,
                max_luminance: self.hdr_caps.max_luminance(),
                min_luminance: self.hdr_caps.min_luminance(),
                content_max_luminance: 4000.0, // HDR10 content max
                content_min_luminance: 0.0001,
                color_space: gpu_renderer::ColorSpace::Rec2020,
            }
        } else {
            info!("Creating SDR renderer (HDR not supported)");
            RendererConfig {
                hdr_enabled: false,
                max_luminance: 80.0, // SDR standard
                min_luminance: 0.0,
                content_max_luminance: 80.0,
                content_min_luminance: 0.0,
                color_space: gpu_renderer::ColorSpace::Rec709,
            }
        };

        let renderer = match GpuRenderer::new(window.clone(), renderer_config) {
            Ok(r) => r,
            Err(e) => {
                error!("Failed to create GPU renderer: {}", e);
                panic!("Cannot continue without renderer");
            }
        };

        info!("Window created: {}x{}", self.args.width, self.args.height);
        info!("GPU renderer initialized");

        // Print HDR status banner
        info!("╔════════════════════════════════════════════════╗");
        if self.hdr_caps.is_supported() {
            info!("║  🌟 HDR MODE ENABLED                           ║");
            info!("║  Max Luminance: {:<6.1} nits                    ║", self.hdr_caps.max_luminance());
            info!("║  Min Luminance: {:<8.4} nits                  ║", self.hdr_caps.min_luminance());
            info!("║  Color Space:   Rec. 2020 (Wide Gamut)        ║");
            info!("║  Transfer:      PQ (SMPTE ST 2084)            ║");
        } else {
            info!("║  📺 SDR MODE (HDR not supported)               ║");
            info!("║  Max Luminance: 80.0 nits                      ║");
            info!("║  Color Space:   Rec. 709 (Standard)           ║");
            info!("║  Transfer:      sRGB gamma 2.2                ║");
        }
        info!("╚════════════════════════════════════════════════╝");

        info!("Server: {:?}", self.args.server);
        info!("Session: {:?}", self.args.session_id);
        info!("Press ESC to release mouse, click to capture");

        self.renderer = Some(renderer);
        self.window = Some(window);

        // Set initial window title
        self.update_window_title();
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        match event {
            WindowEvent::CloseRequested => {
                info!("Window closed");
                event_loop.exit();
            }
            WindowEvent::RedrawRequested => {
                self.render_frame();
            }
            WindowEvent::KeyboardInput { event, .. } => {
                self.handle_keyboard(event.physical_key, event.state);
            }
            WindowEvent::MouseInput { button, state, .. } => {
                self.handle_mouse_button(button, state);
            }
            WindowEvent::MouseWheel { delta, .. } => {
                let y = match delta {
                    winit::event::MouseScrollDelta::LineDelta(_, y) => y,
                    winit::event::MouseScrollDelta::PixelDelta(pos) => pos.y as f32 / 120.0,
                };
                self.handle_mouse_wheel(y);
            }
            WindowEvent::Resized(size) => {
                if let Some(renderer) = &mut self.renderer {
                    renderer.resize(size.width, size.height);
                }
            }
            _ => {}
        }
    }

    fn device_event(&mut self, _event_loop: &ActiveEventLoop, _device_id: winit::event::DeviceId, event: DeviceEvent) {
        if !self.mouse_captured {
            return;
        }

        if let DeviceEvent::MouseMotion { delta } = event {
            let (dx, dy) = delta;
            let event = InputEvent::MouseMove {
                dx: dx as i16,
                dy: dy as i16,
                timestamp_us: self.get_timestamp_us(),
            };
            let _ = self.input_tx.try_send(event);
        }
    }

    fn about_to_wait(&mut self, _event_loop: &ActiveEventLoop) {
        if let Some(window) = &self.window {
            window.request_redraw();
        }
    }
}

/// Run the streaming connection
pub async fn run_streaming(
    server: String,
    session_id: String,
    shared_state: Arc<Mutex<SharedState>>,
    mut input_rx: mpsc::Receiver<InputEvent>,
    hdr_caps: hdr_detection::HdrCapabilities,
) -> Result<()> {
    info!("Starting streaming connection to {}", server);

    {
        let mut state = shared_state.lock();
        state.status_message = "Connecting to signaling...".to_string();
    }

    // Create channels for signaling events
    let (sig_tx, mut sig_rx) = mpsc::channel::<SignalingEvent>(64);

    // Create signaling client
    let mut signaling = GfnSignaling::new(server.clone(), session_id.clone(), sig_tx);

    // Connect to signaling server
    match signaling.connect().await {
        Ok(_) => {
            info!("Connected to signaling server");
            let mut state = shared_state.lock();
            state.signaling_connected = true;
            state.status_message = "Signaling connected, waiting for offer...".to_string();
        }
        Err(e) => {
            error!("Failed to connect to signaling: {}", e);
            let mut state = shared_state.lock();
            state.status_message = format!("Signaling failed: {}", e);
            return Err(e);
        }
    }

    // Create WebRTC event channel
    let (webrtc_tx, mut webrtc_rx) = mpsc::channel::<WebRtcEvent>(64);
    let mut webrtc_client = WebRtcClient::new(webrtc_tx);

    // Input encoder
    let mut input_encoder = InputEncoder::new();

    // Decoder and RTP reassembler will be created after codec negotiation
    let mut decoder: Option<FfmpegDecoder> = None;
    let mut rtp_reassembler: Option<RtpReassembler> = None;

    // Main event loop
    loop {
        tokio::select! {
            // Handle signaling events
            Some(event) = sig_rx.recv() => {
                match event {
                    SignalingEvent::Connected => {
                        info!("Signaling connected");
                    }
                    SignalingEvent::SdpOffer(sdp) => {
                        info!("Received SDP offer ({} bytes)", sdp.len());

                        {
                            let mut state = shared_state.lock();
                            state.status_message = "Received offer, creating answer...".to_string();
                        }

                        // Modify SDP to prefer HDR codec if supported
                        let hdr_config = create_hdr_config(&hdr_caps);
                        let modified_sdp = if hdr_caps.is_supported() {
                            info!("HDR supported, modifying SDP to prefer H.265 Main10");
                            match hdr_signaling::modify_sdp_for_hdr(&sdp, &hdr_config) {
                                Ok(s) => {
                                    info!("SDP modified for HDR");
                                    s
                                }
                                Err(e) => {
                                    warn!("Failed to modify SDP for HDR: {}, using original", e);
                                    sdp.clone()
                                }
                            }
                        } else {
                            info!("SDR display, using original SDP (H.264)");
                            sdp.clone()
                        };

                        // Parse ICE servers - use STUN server
                        let ice_servers = vec![
                            RTCIceServer {
                                urls: vec!["stun:stun.l.google.com:19302".to_string()],
                                ..Default::default()
                            },
                        ];

                        match webrtc_client.handle_offer(&modified_sdp, ice_servers).await {
                            Ok(answer) => {
                                info!("Created SDP answer, sending to server");

                                // Detect negotiated codec from answer
                                let negotiated_codec = detect_codec_from_sdp(&answer);
                                {
                                    let mut state = shared_state.lock();
                                    state.negotiated_codec = Some(negotiated_codec);
                                    state.status_message = format!("Codec negotiated: {:?}", negotiated_codec);
                                }

                                if let Err(e) = signaling.send_answer(&answer, None).await {
                                    error!("Failed to send SDP answer: {}", e);
                                }

                                // Create input channel
                                if let Err(e) = webrtc_client.create_input_channel().await {
                                    warn!("Failed to create input channel: {}", e);
                                }
                            }
                            Err(e) => {
                                error!("Failed to handle SDP offer: {}", e);
                                let mut state = shared_state.lock();
                                state.status_message = format!("WebRTC error: {}", e);
                            }
                        }
                    }
                    SignalingEvent::IceCandidate(candidate) => {
                        debug!("Received ICE candidate: {}", &candidate.candidate[..candidate.candidate.len().min(50)]);
                        if let Err(e) = webrtc_client.add_ice_candidate(
                            &candidate.candidate,
                            candidate.sdp_mid.as_deref(),
                            candidate.sdp_mline_index.map(|i| i as u16),
                        ).await {
                            warn!("Failed to add ICE candidate: {}", e);
                        }
                    }
                    SignalingEvent::Disconnected(reason) => {
                        warn!("Signaling disconnected: {}", reason);
                        let mut state = shared_state.lock();
                        state.signaling_connected = false;
                        state.status_message = format!("Disconnected: {}", reason);
                        break;
                    }
                    SignalingEvent::Error(e) => {
                        error!("Signaling error: {}", e);
                    }
                }
            }

            // Handle WebRTC events
            Some(event) = webrtc_rx.recv() => {
                match event {
                    WebRtcEvent::Connected => {
                        info!("WebRTC connected!");

                        // Create decoder based on negotiated codec
                        let codec = {
                            let state = shared_state.lock();
                            state.negotiated_codec.unwrap_or(VideoCodec::H264)
                        };

                        info!("Creating decoder for codec: {:?}", codec);

                        decoder = match FfmpegDecoder::new(codec) {
                            Ok(d) => {
                                info!("FFmpeg {:?} decoder initialized", codec);
                                if d.is_hwaccel_enabled() {
                                    info!("Hardware acceleration is enabled");
                                } else {
                                    warn!("Hardware acceleration not available, using software decoding");
                                }
                                Some(d)
                            }
                            Err(e) => {
                                error!("Failed to create FFmpeg decoder: {}. Video will not be displayed.", e);
                                None
                            }
                        };

                        // Create RTP reassembler for the negotiated codec
                        rtp_reassembler = Some(RtpReassembler::new(codec));

                        // Print codec status banner
                        info!("╔════════════════════════════════════════════════╗");
                        match codec {
                            VideoCodec::H265 => {
                                info!("║  🎬 CODEC: H.265 Main10 (10-bit HDR)          ║");
                                info!("║  Quality: High (HDR streaming active)         ║");
                            }
                            VideoCodec::H264 => {
                                info!("║  🎬 CODEC: H.264 (8-bit SDR)                   ║");
                                info!("║  Quality: Standard (SDR streaming)            ║");
                            }
                            _ => {
                                info!("║  🎬 CODEC: {:?}                                 ║", codec);
                            }
                        }
                        info!("╚════════════════════════════════════════════════╝");

                        let mut state = shared_state.lock();
                        state.webrtc_connected = true;
                        state.connected = true;
                        state.status_message = format!("WebRTC connected, using {:?} codec", codec);
                    }
                    WebRtcEvent::Disconnected => {
                        warn!("WebRTC disconnected");
                        let mut state = shared_state.lock();
                        state.webrtc_connected = false;
                        state.connected = false;
                        state.status_message = "WebRTC disconnected".to_string();
                    }
                    WebRtcEvent::VideoFrame(rtp_payload) => {
                        // Update stats
                        {
                            let mut state = shared_state.lock();
                            state.stats.frames_received += 1;
                            state.stats.bytes_received += rtp_payload.len() as u64;
                        }

                        // Process RTP payload
                        if !rtp_payload.is_empty() {
                            if let (Some(ref mut dec), Some(ref mut reassembler)) = (&mut decoder, &mut rtp_reassembler) {
                                // Reassemble RTP packet into NAL unit
                                if let Some(nal_data) = reassembler.process_packet(&rtp_payload) {
                                    // Decode the NAL unit
                                    match dec.decode(&nal_data) {
                                        Ok(Some(decoded_frame)) => {
                                            // Convert FFmpeg frame to YuvFrame
                                            let yuv_frame = YuvFrame {
                                                y_plane: decoded_frame.y_plane,
                                                u_plane: decoded_frame.u_plane,
                                                v_plane: decoded_frame.v_plane,
                                                width: decoded_frame.width,
                                                height: decoded_frame.height,
                                                y_stride: decoded_frame.y_stride,
                                                u_stride: decoded_frame.u_stride,
                                                v_stride: decoded_frame.v_stride,
                                            };

                                            // Update shared state with new frame
                                            let mut state = shared_state.lock();
                                            state.video_frame = Some(yuv_frame);
                                            state.stats.frames_decoded += 1;
                                            state.status_message = format!("Streaming - {}x{}",
                                                decoded_frame.width, decoded_frame.height);
                                        }
                                        Ok(None) => {
                                            // Decoder needs more data
                                        }
                                        Err(e) => {
                                            // Decode error - skip this packet
                                            debug!("Decode error: {}", e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    WebRtcEvent::AudioFrame(_data) => {
                        let mut state = shared_state.lock();
                        state.stats.audio_packets += 1;
                        // TODO: Play audio with cpal
                    }
                    WebRtcEvent::DataChannelOpen(name) => {
                        info!("Data channel opened: {}", name);
                        if name.contains("input") {
                            let mut state = shared_state.lock();
                            state.input_ready = true;
                            state.status_message = "Input channel ready".to_string();
                        }
                    }
                    WebRtcEvent::DataChannelMessage(name, data) => {
                        debug!("Data channel '{}' message: {} bytes", name, data.len());

                        // Handle input handshake
                        if data.len() == 4 && data[0] == 0x0e {
                            info!("Received input handshake: v{}.{} flags={}", data[1], data[2], data[3]);
                            if let Err(e) = webrtc_client.send_handshake_response(data[1], data[2], data[3]).await {
                                warn!("Failed to send handshake response: {}", e);
                            } else {
                                info!("Handshake complete - input ready!");
                            }
                        }
                    }
                    WebRtcEvent::IceCandidate(candidate, sdp_mid, sdp_mline_index) => {
                        debug!("Local ICE candidate generated");
                        if let Err(e) = signaling.send_ice_candidate(
                            &candidate,
                            sdp_mid.as_deref(),
                            sdp_mline_index.map(|i| i as u32),
                        ).await {
                            warn!("Failed to send ICE candidate: {}", e);
                        }
                    }
                    WebRtcEvent::Error(e) => {
                        error!("WebRTC error: {}", e);
                    }
                }
            }

            // Handle input events
            Some(event) = input_rx.recv() => {
                let ready = {
                    let state = shared_state.lock();
                    state.input_ready && webrtc_client.is_handshake_complete()
                };

                if ready {
                    let encoded = input_encoder.encode(&event);
                    if let Err(e) = webrtc_client.send_input(&encoded).await {
                        debug!("Failed to send input: {}", e);
                    }
                }
            }

            else => break,
        }
    }

    info!("Streaming ended");
    Ok(())
}

/// Convert winit keycode to Windows virtual key code
fn keycode_to_vk(keycode: KeyCode) -> u16 {
    match keycode {
        KeyCode::Backquote => 0xC0,
        KeyCode::Backslash => 0xDC,
        KeyCode::BracketLeft => 0xDB,
        KeyCode::BracketRight => 0xDD,
        KeyCode::Comma => 0xBC,
        KeyCode::Digit0 => 0x30,
        KeyCode::Digit1 => 0x31,
        KeyCode::Digit2 => 0x32,
        KeyCode::Digit3 => 0x33,
        KeyCode::Digit4 => 0x34,
        KeyCode::Digit5 => 0x35,
        KeyCode::Digit6 => 0x36,
        KeyCode::Digit7 => 0x37,
        KeyCode::Digit8 => 0x38,
        KeyCode::Digit9 => 0x39,
        KeyCode::Equal => 0xBB,
        KeyCode::KeyA => 0x41,
        KeyCode::KeyB => 0x42,
        KeyCode::KeyC => 0x43,
        KeyCode::KeyD => 0x44,
        KeyCode::KeyE => 0x45,
        KeyCode::KeyF => 0x46,
        KeyCode::KeyG => 0x47,
        KeyCode::KeyH => 0x48,
        KeyCode::KeyI => 0x49,
        KeyCode::KeyJ => 0x4A,
        KeyCode::KeyK => 0x4B,
        KeyCode::KeyL => 0x4C,
        KeyCode::KeyM => 0x4D,
        KeyCode::KeyN => 0x4E,
        KeyCode::KeyO => 0x4F,
        KeyCode::KeyP => 0x50,
        KeyCode::KeyQ => 0x51,
        KeyCode::KeyR => 0x52,
        KeyCode::KeyS => 0x53,
        KeyCode::KeyT => 0x54,
        KeyCode::KeyU => 0x55,
        KeyCode::KeyV => 0x56,
        KeyCode::KeyW => 0x57,
        KeyCode::KeyX => 0x58,
        KeyCode::KeyY => 0x59,
        KeyCode::KeyZ => 0x5A,
        KeyCode::Minus => 0xBD,
        KeyCode::Period => 0xBE,
        KeyCode::Quote => 0xDE,
        KeyCode::Semicolon => 0xBA,
        KeyCode::Slash => 0xBF,
        KeyCode::Backspace => 0x08,
        KeyCode::CapsLock => 0x14,
        KeyCode::Enter => 0x0D,
        KeyCode::Space => 0x20,
        KeyCode::Tab => 0x09,
        KeyCode::Delete => 0x2E,
        KeyCode::End => 0x23,
        KeyCode::Home => 0x24,
        KeyCode::Insert => 0x2D,
        KeyCode::PageDown => 0x22,
        KeyCode::PageUp => 0x21,
        KeyCode::ArrowDown => 0x28,
        KeyCode::ArrowLeft => 0x25,
        KeyCode::ArrowRight => 0x27,
        KeyCode::ArrowUp => 0x26,
        KeyCode::Escape => 0x1B,
        KeyCode::F1 => 0x70,
        KeyCode::F2 => 0x71,
        KeyCode::F3 => 0x72,
        KeyCode::F4 => 0x73,
        KeyCode::F5 => 0x74,
        KeyCode::F6 => 0x75,
        KeyCode::F7 => 0x76,
        KeyCode::F8 => 0x77,
        KeyCode::F9 => 0x78,
        KeyCode::F10 => 0x79,
        KeyCode::F11 => 0x7A,
        KeyCode::F12 => 0x7B,
        KeyCode::Numpad0 => 0x60,
        KeyCode::Numpad1 => 0x61,
        KeyCode::Numpad2 => 0x62,
        KeyCode::Numpad3 => 0x63,
        KeyCode::Numpad4 => 0x64,
        KeyCode::Numpad5 => 0x65,
        KeyCode::Numpad6 => 0x66,
        KeyCode::Numpad7 => 0x67,
        KeyCode::Numpad8 => 0x68,
        KeyCode::Numpad9 => 0x69,
        KeyCode::NumpadAdd => 0x6B,
        KeyCode::NumpadDecimal => 0x6E,
        KeyCode::NumpadDivide => 0x6F,
        KeyCode::NumpadEnter => 0x0D,
        KeyCode::NumpadMultiply => 0x6A,
        KeyCode::NumpadSubtract => 0x6D,
        KeyCode::ShiftLeft | KeyCode::ShiftRight => 0x10,
        KeyCode::ControlLeft | KeyCode::ControlRight => 0x11,
        KeyCode::AltLeft | KeyCode::AltRight => 0x12,
        KeyCode::SuperLeft => 0x5B,
        KeyCode::SuperRight => 0x5C,
        _ => 0,
    }
}

/// Convert winit keycode to scan code
fn keycode_to_scan(keycode: KeyCode) -> u16 {
    match keycode {
        KeyCode::Escape => 0x01,
        KeyCode::Digit1 => 0x02,
        KeyCode::Digit2 => 0x03,
        KeyCode::Digit3 => 0x04,
        KeyCode::Digit4 => 0x05,
        KeyCode::Digit5 => 0x06,
        KeyCode::Digit6 => 0x07,
        KeyCode::Digit7 => 0x08,
        KeyCode::Digit8 => 0x09,
        KeyCode::Digit9 => 0x0A,
        KeyCode::Digit0 => 0x0B,
        KeyCode::Minus => 0x0C,
        KeyCode::Equal => 0x0D,
        KeyCode::Backspace => 0x0E,
        KeyCode::Tab => 0x0F,
        KeyCode::KeyQ => 0x10,
        KeyCode::KeyW => 0x11,
        KeyCode::KeyE => 0x12,
        KeyCode::KeyR => 0x13,
        KeyCode::KeyT => 0x14,
        KeyCode::KeyY => 0x15,
        KeyCode::KeyU => 0x16,
        KeyCode::KeyI => 0x17,
        KeyCode::KeyO => 0x18,
        KeyCode::KeyP => 0x19,
        KeyCode::BracketLeft => 0x1A,
        KeyCode::BracketRight => 0x1B,
        KeyCode::Enter => 0x1C,
        KeyCode::ControlLeft => 0x1D,
        KeyCode::KeyA => 0x1E,
        KeyCode::KeyS => 0x1F,
        KeyCode::KeyD => 0x20,
        KeyCode::KeyF => 0x21,
        KeyCode::KeyG => 0x22,
        KeyCode::KeyH => 0x23,
        KeyCode::KeyJ => 0x24,
        KeyCode::KeyK => 0x25,
        KeyCode::KeyL => 0x26,
        KeyCode::Semicolon => 0x27,
        KeyCode::Quote => 0x28,
        KeyCode::Backquote => 0x29,
        KeyCode::ShiftLeft => 0x2A,
        KeyCode::Backslash => 0x2B,
        KeyCode::KeyZ => 0x2C,
        KeyCode::KeyX => 0x2D,
        KeyCode::KeyC => 0x2E,
        KeyCode::KeyV => 0x2F,
        KeyCode::KeyB => 0x30,
        KeyCode::KeyN => 0x31,
        KeyCode::KeyM => 0x32,
        KeyCode::Comma => 0x33,
        KeyCode::Period => 0x34,
        KeyCode::Slash => 0x35,
        KeyCode::ShiftRight => 0x36,
        KeyCode::Space => 0x39,
        KeyCode::CapsLock => 0x3A,
        KeyCode::F1 => 0x3B,
        KeyCode::F2 => 0x3C,
        KeyCode::F3 => 0x3D,
        KeyCode::F4 => 0x3E,
        KeyCode::F5 => 0x3F,
        KeyCode::F6 => 0x40,
        KeyCode::F7 => 0x41,
        KeyCode::F8 => 0x42,
        KeyCode::F9 => 0x43,
        KeyCode::F10 => 0x44,
        KeyCode::F11 => 0x57,
        KeyCode::F12 => 0x58,
        KeyCode::ArrowUp => 0x48,
        KeyCode::ArrowLeft => 0x4B,
        KeyCode::ArrowRight => 0x4D,
        KeyCode::ArrowDown => 0x50,
        _ => 0,
    }
}

/// Create HDR streaming config from detected capabilities
fn create_hdr_config(hdr_caps: &hdr_detection::HdrCapabilities) -> hdr_signaling::HdrStreamingConfig {
    if hdr_caps.is_supported() {
        hdr_signaling::HdrStreamingConfig {
            enable_hdr: true,
            codec: hdr_signaling::HdrCodec::H265Main10,
            max_bitrate_mbps: 50,
            max_luminance: hdr_caps.max_luminance(),
            min_luminance: hdr_caps.min_luminance(),
            max_frame_avg_luminance: hdr_caps.max_frame_average_luminance(),
            color_space: "rec2020".to_string(),
        }
    } else {
        hdr_signaling::HdrStreamingConfig::default()
    }
}

/// Detect video codec from SDP (simple version - checks for H.265/H265 in SDP)
fn detect_codec_from_sdp(sdp: &str) -> VideoCodec {
    // Check if SDP contains H.265/HEVC indicators
    let sdp_upper = sdp.to_uppercase();
    if sdp_upper.contains("H265") || sdp_upper.contains("HEVC") {
        info!("Detected H.265 codec in SDP");
        VideoCodec::H265
    } else {
        info!("Detected H.264 codec in SDP (default)");
        VideoCodec::H264
    }
}

fn main() -> Result<()> {
    // Parse arguments
    let args = Args::parse();

    // Initialize logging
    if args.debug {
        env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("debug")).init();
    } else {
        env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
    }

    // Detect HDR display capabilities
    info!("Detecting HDR display capabilities...");
    let hdr_caps = hdr_detection::detect_hdr_capabilities().unwrap_or_else(|e| {
        warn!("Failed to detect HDR capabilities: {}, using defaults", e);
        hdr_detection::HdrCapabilities::default()
    });
    info!("HDR capabilities: supported={}, max_luminance={:.1} nits, min_luminance={:.4} nits",
          hdr_caps.is_supported(), hdr_caps.max_luminance(), hdr_caps.min_luminance());

    // Test mode - no GFN session required
    if args.test {
        info!("=== GPU Renderer Test Mode ===");
        info!("Resolution: {}x{}", args.width, args.height);
        info!("Duration: {} seconds", args.test_duration);
        info!("Press ESC to exit early");
        test_mode::run_renderer_test(args.width, args.height, args.test_duration);
        return Ok(());
    }

    // Normal mode - requires server and session ID
    let server = args.server.as_ref().ok_or_else(|| anyhow::anyhow!("--server is required (or use --test for test mode)"))?;
    let session_id = args.session_id.as_ref().ok_or_else(|| anyhow::anyhow!("--session-id is required (or use --test for test mode)"))?;

    info!("GFN Native Client v{}", env!("CARGO_PKG_VERSION"));
    info!("Server: {}", server);
    info!("Session: {}", session_id);

    // Create input channel
    let (input_tx, input_rx) = mpsc::channel::<InputEvent>(256);

    // Create shared state
    let shared_state = Arc::new(Mutex::new(SharedState::default()));

    // Start tokio runtime for async tasks
    let runtime = tokio::runtime::Runtime::new()?;

    // Spawn streaming task
    let server_clone = server.clone();
    let session_id_clone = session_id.clone();
    let state_clone = shared_state.clone();
    let hdr_caps_clone = hdr_caps.clone();

    runtime.spawn(async move {
        if let Err(e) = run_streaming(server_clone, session_id_clone, state_clone, input_rx, hdr_caps_clone).await {
            error!("Streaming error: {}", e);
        }
    });

    // Create event loop
    let event_loop = EventLoop::new()?;
    event_loop.set_control_flow(ControlFlow::Poll);

    // Create application
    let mut app = GfnApp::new(args, input_tx, shared_state, hdr_caps);

    // Run event loop (blocking)
    event_loop.run_app(&mut app)?;

    // Cleanup
    info!("Shutting down...");
    drop(runtime);

    Ok(())
}
