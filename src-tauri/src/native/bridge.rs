//! Bridge layer between Tauri and native streaming client
//!
//! This module provides the integration layer that allows the native HDR streaming
//! client to be launched from Tauri commands. It runs the native client in a separate
//! thread to avoid event loop conflicts between Tauri and winit.

use std::sync::Arc;
use std::thread::{self, JoinHandle};
use anyhow::{Result, Context as AnyhowContext};
use log::{info, warn, error};
use parking_lot::Mutex;
use tokio::sync::mpsc;

use super::ffmpeg_decoder::VideoCodec;
use super::hdr_detection;
use super::input;
use super::{Args, GfnApp, SharedState, run_streaming};

/// Streaming status updates sent from native client to Tauri
#[derive(Debug, Clone)]
pub enum StreamingStatus {
    /// Native client is starting up
    Starting,

    /// Connected to signaling server
    SignalingConnected,

    /// WebRTC connection established
    WebRtcConnected,

    /// Actively streaming video
    Streaming {
        fps: f32,
        codec: String,
        hdr_active: bool,
    },

    /// Error occurred
    Error(String),

    /// Streaming stopped (clean shutdown)
    Stopped,
}

/// Configuration for native streaming session
#[derive(Debug, Clone)]
pub struct StreamingConfig {
    /// GFN server IP address
    pub server: String,

    /// Session ID from GFN CloudMatch
    pub session_id: String,

    /// Video resolution width
    pub width: u32,

    /// Video resolution height
    pub height: u32,

    /// Enable HDR mode
    pub hdr_enabled: bool,

    /// Preferred video codec
    pub codec: VideoCodec,
}

/// Bridge between Tauri and native streaming client
///
/// This struct manages the lifecycle of the native streaming client:
/// - Spawns native client in separate thread
/// - Forwards status updates via channels
/// - Handles graceful shutdown
pub struct NativeStreamingBridge {
    /// Handle to the native streaming thread
    thread_handle: Option<JoinHandle<()>>,

    /// Channel to send shutdown signal
    shutdown_tx: mpsc::UnboundedSender<()>,

    /// Configuration used for this session
    config: StreamingConfig,
}

impl NativeStreamingBridge {
    /// Start a new native streaming session
    ///
    /// This spawns the native client in a separate thread with its own:
    /// - Tokio runtime (not shared with Tauri)
    /// - Winit event loop (runs in the spawned thread)
    /// - Input/video processing tasks
    ///
    /// # Arguments
    /// * `config` - Streaming configuration
    /// * `status_tx` - Channel to send status updates to Tauri
    ///
    /// # Returns
    /// * `Ok(Self)` - Bridge successfully started
    /// * `Err` - Failed to start native client
    pub fn start(
        config: StreamingConfig,
        status_tx: mpsc::UnboundedSender<StreamingStatus>,
    ) -> Result<Self> {
        info!("Starting native streaming bridge");
        info!("  Server: {}", config.server);
        info!("  Session: {}", config.session_id);
        info!("  Resolution: {}x{}", config.width, config.height);
        info!("  HDR: {}", config.hdr_enabled);
        info!("  Codec: {:?}", config.codec);

        // Create shutdown channel
        let (shutdown_tx, shutdown_rx) = mpsc::unbounded_channel();

        // Clone config for thread
        let config_clone = config.clone();

        // Spawn native streaming in separate thread
        let thread_handle = thread::Builder::new()
            .name("native-streaming".to_string())
            .spawn(move || {
                info!("Native streaming thread started");

                // Send starting status
                let _ = status_tx.send(StreamingStatus::Starting);

                // Run the native client (blocking)
                if let Err(e) = run_native_streaming_blocking(
                    config_clone,
                    status_tx.clone(),
                    shutdown_rx,
                ) {
                    error!("Native streaming error: {}", e);
                    let _ = status_tx.send(StreamingStatus::Error(e.to_string()));
                }

                // Send stopped status
                let _ = status_tx.send(StreamingStatus::Stopped);
                info!("Native streaming thread stopped");
            })
            .context("Failed to spawn native streaming thread")?;

        Ok(Self {
            thread_handle: Some(thread_handle),
            shutdown_tx,
            config,
        })
    }

    /// Request graceful shutdown of native streaming
    ///
    /// Sends shutdown signal to native client and waits for thread to exit.
    ///
    /// # Timeout
    /// Waits up to 5 seconds for clean shutdown, then returns
    pub fn shutdown(mut self) -> Result<()> {
        info!("Shutting down native streaming");

        // Send shutdown signal
        if let Err(e) = self.shutdown_tx.send(()) {
            warn!("Failed to send shutdown signal: {}", e);
        }

        // Wait for thread to exit (with timeout)
        if let Some(handle) = self.thread_handle.take() {
            // Try to join with timeout
            match handle.join() {
                Ok(()) => {
                    info!("Native streaming thread exited cleanly");
                    Ok(())
                }
                Err(e) => {
                    error!("Native streaming thread panicked: {:?}", e);
                    Err(anyhow::anyhow!("Thread panicked during shutdown"))
                }
            }
        } else {
            Ok(())
        }
    }

    /// Get the configuration for this session
    pub fn config(&self) -> &StreamingConfig {
        &self.config
    }
}

impl Drop for NativeStreamingBridge {
    fn drop(&mut self) {
        // Ensure shutdown is called
        let _ = self.shutdown_tx.send(());

        if let Some(handle) = self.thread_handle.take() {
            // Don't block on join in drop - just detach
            let _ = handle.join();
        }
    }
}

/// Run native streaming client in blocking mode
///
/// This is the main entry point for the native client when called from the bridge.
/// It runs in a separate thread and blocks until streaming ends or shutdown is requested.
///
/// # Architecture
/// - Creates separate Tokio runtime (not shared with Tauri)
/// - Detects HDR capabilities
/// - Creates winit event loop (blocking)
/// - Spawns async streaming tasks
/// - Runs event loop until shutdown or error
fn run_native_streaming_blocking(
    config: StreamingConfig,
    status_tx: mpsc::UnboundedSender<StreamingStatus>,
    mut shutdown_rx: mpsc::UnboundedReceiver<()>,
) -> Result<()> {
    // Initialize logging for this thread (inherit from main)
    // (Already initialized by Tauri, so this is a no-op)

    // Create separate Tokio runtime for this thread
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(4)
        .thread_name("native-streaming-worker")
        .enable_all()
        .build()
        .context("Failed to create Tokio runtime")?;

    // Detect HDR capabilities
    info!("Detecting HDR capabilities...");
    let hdr_caps = runtime.block_on(async {
        tokio::task::spawn_blocking(|| {
            hdr_detection::detect_hdr_capabilities()
        }).await
    }).context("HDR detection task panicked")?.unwrap_or_else(|e| {
        warn!("Failed to detect HDR capabilities: {}, using defaults", e);
        hdr_detection::HdrCapabilities::default()
    });

    info!("HDR capabilities: supported={}, max_luminance={:.1} nits",
          hdr_caps.is_supported(), hdr_caps.max_luminance());

    // If HDR was requested but not supported, warn
    if config.hdr_enabled && !hdr_caps.is_supported() {
        warn!("HDR was requested but display doesn't support it - falling back to SDR");
    }

    // Create Args struct compatible with native client
    let args = Args {
        server: Some(config.server.clone()),
        session_id: Some(config.session_id.clone()),
        width: config.width,
        height: config.height,
        debug: false,  // Inherit logging from Tauri
        test: false,
        test_duration: 0,
    };

    // Create input channel
    let (input_tx, input_rx) = mpsc::channel::<input::InputEvent>(256);

    // Create shared state
    let shared_state = Arc::new(Mutex::new(SharedState::default()));

    // Spawn streaming task
    let server = config.server.clone();
    let session_id = config.session_id.clone();
    let state_clone = shared_state.clone();
    let hdr_caps_clone = hdr_caps.clone();
    let status_tx_clone = status_tx.clone();

    runtime.spawn(async move {
        // Send signaling connected status when connected
        let _ = status_tx_clone.send(StreamingStatus::SignalingConnected);

        if let Err(e) = run_streaming(
            server,
            session_id,
            state_clone,
            input_rx,
            hdr_caps_clone,
        ).await {
            error!("Streaming task error: {}", e);
            let _ = status_tx_clone.send(StreamingStatus::Error(e.to_string()));
        }
    });

    // Monitor shared state and send status updates
    let state_monitor = shared_state.clone();
    let status_tx_clone2 = status_tx.clone();
    let hdr_caps_clone2 = hdr_caps.clone();
    let config_clone = config.clone();
    runtime.spawn(async move {
        let mut last_fps = 0.0f32;
        let mut last_codec: Option<VideoCodec> = None;
        let mut was_connected = false;

        loop {
            tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;

            let state = state_monitor.lock();

            // Send WebRTC connected status
            if state.webrtc_connected && !was_connected {
                let _ = status_tx_clone2.send(StreamingStatus::WebRtcConnected);
                was_connected = true;
            }

            // Send streaming status updates
            if state.webrtc_connected && (
                (state.current_fps - last_fps).abs() > 0.5 ||
                state.negotiated_codec != last_codec
            ) {
                last_fps = state.current_fps;
                last_codec = state.negotiated_codec;

                let codec_str = match state.negotiated_codec {
                    Some(VideoCodec::H264) => "H.264",
                    Some(VideoCodec::H265) => "H.265",
                    Some(VideoCodec::AV1) => "AV1",
                    None => "Unknown",
                };

                let _ = status_tx_clone2.send(StreamingStatus::Streaming {
                    fps: state.current_fps,
                    codec: codec_str.to_string(),
                    hdr_active: hdr_caps_clone2.is_supported() && config_clone.hdr_enabled,
                });
            }
        }
    });

    // Create winit event loop (will block in this thread)
    let event_loop = winit::event_loop::EventLoop::new()
        .context("Failed to create event loop")?;
    event_loop.set_control_flow(winit::event_loop::ControlFlow::Poll);

    // Create application
    let mut app = GfnApp::new(args, input_tx, shared_state, hdr_caps);

    // Spawn shutdown monitor in runtime
    runtime.spawn(async move {
        shutdown_rx.recv().await;
        info!("Shutdown requested");
        // TODO: Implement graceful shutdown signal to app
        // For now, app will exit when window closes
    });

    // Run event loop (blocking until window closes)
    info!("Starting native window event loop");
    event_loop.run_app(&mut app)
        .context("Event loop error")?;

    info!("Event loop exited");

    // Cleanup
    drop(runtime);

    Ok(())
}
