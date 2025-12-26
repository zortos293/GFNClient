//! Native streaming commands for Tauri
//!
//! This module provides Tauri commands that allow the frontend to control
//! the native HDR streaming client. Commands are exposed to JavaScript/TypeScript
//! via Tauri's invoke() API.

use std::sync::Arc;
use parking_lot::Mutex;
use tauri::{AppHandle, Emitter, Manager};
use tokio::sync::mpsc;

#[cfg(feature = "native-client")]
use crate::native::bridge::{NativeStreamingBridge, StreamingConfig, StreamingStatus};
#[cfg(feature = "native-client")]
use crate::native::ffmpeg_decoder::VideoCodec;

/// Global bridge instance
///
/// Only one streaming session can be active at a time.
/// Using Option<Arc<>> allows us to check if streaming is active
/// and prevents multiple concurrent sessions.
#[cfg(feature = "native-client")]
static BRIDGE: Mutex<Option<Arc<NativeStreamingBridge>>> = Mutex::new(None);

/// Start native streaming with HDR support
///
/// This command launches the native streaming client in a separate window.
/// The Tauri app window will be hidden while streaming is active.
///
/// # Arguments
/// * `app` - Tauri app handle (injected)
/// * `server` - GFN server IP address
/// * `session_id` - Session ID from CloudMatch
/// * `width` - Video resolution width
/// * `height` - Video resolution height
/// * `codec` - Preferred codec ("H264", "H265", or "AV1")
/// * `colorspace` - Color space mode ("sdr", "hdr10", or "auto")
///
/// # Returns
/// * `Ok(())` - Streaming started successfully
/// * `Err(String)` - Error message
///
/// # Events Emitted
/// * `native-streaming:starting` - Native client is initializing
/// * `native-streaming:connected` - WebRTC connection established
/// * `native-streaming:streaming` - Video streaming active (with FPS/codec)
/// * `native-streaming:error` - Error occurred
/// * `native-streaming:stopped` - Streaming ended
#[cfg(feature = "native-client")]
#[tauri::command]
pub async fn start_native_streaming(
    app: AppHandle,
    server: String,
    session_id: String,
    width: u32,
    height: u32,
    codec: String,
    colorspace: String,
) -> Result<(), String> {
    use log::{info, warn};

    info!("Native streaming command received");
    info!("  Server: {}", server);
    info!("  Session: {}", session_id);
    info!("  Resolution: {}x{}", width, height);
    info!("  Codec: {}", codec);
    info!("  Colorspace: {}", colorspace);

    // Check if already running
    let mut bridge_guard = BRIDGE.lock();
    if bridge_guard.is_some() {
        warn!("Streaming already active, rejecting new session");
        return Err("Streaming session already active".to_string());
    }

    // Parse codec
    let video_codec = match codec.to_uppercase().as_str() {
        "H265" | "HEVC" => VideoCodec::H265,
        "AV1" => VideoCodec::AV1,
        "H264" | "AVC" => VideoCodec::H264,
        _ => {
            warn!("Unknown codec '{}', defaulting to H.264", codec);
            VideoCodec::H264
        }
    };

    // Determine if HDR should be enabled
    let hdr_enabled = match colorspace.to_lowercase().as_str() {
        "hdr10" | "hdr" => {
            info!("HDR explicitly enabled");
            true
        }
        "auto" => {
            // Auto-detect HDR capability
            info!("Auto-detecting HDR capability...");
            #[cfg(feature = "native-client")]
            {
                use crate::native::hdr_detection;
                match hdr_detection::detect_hdr_capabilities() {
                    Ok(caps) if caps.is_supported() => {
                        info!("HDR auto-detected: supported (max {} nits)", caps.max_luminance());
                        true
                    }
                    Ok(_) => {
                        info!("HDR auto-detected: not supported");
                        false
                    }
                    Err(e) => {
                        warn!("HDR detection failed: {}, assuming SDR", e);
                        false
                    }
                }
            }
            #[cfg(not(feature = "native-client"))]
            false
        }
        _ => {
            info!("SDR mode");
            false
        }
    };

    // Create streaming config
    let config = StreamingConfig {
        server,
        session_id,
        width,
        height,
        hdr_enabled,
        codec: video_codec,
    };

    // Create status channel
    let (status_tx, mut status_rx) = mpsc::unbounded_channel();

    // Start bridge
    info!("Starting native streaming bridge...");
    let bridge = Arc::new(
        NativeStreamingBridge::start(config, status_tx)
            .map_err(|e| {
                warn!("Failed to start bridge: {}", e);
                format!("Failed to start native streaming: {}", e)
            })?
    );

    info!("Native streaming bridge started successfully");

    // Store bridge in global state
    *bridge_guard = Some(bridge.clone());
    drop(bridge_guard); // Release lock

    // Spawn task to forward status updates to frontend
    let app_clone = app.clone();
    tokio::spawn(async move {
        info!("Status forwarding task started");

        while let Some(status) = status_rx.recv().await {
            match &status {
                StreamingStatus::Starting => {
                    info!("Status: Starting");
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:starting", ());
                    }
                }
                StreamingStatus::SignalingConnected => {
                    info!("Status: Signaling connected");
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:signaling-connected", ());
                    }
                }
                StreamingStatus::WebRtcConnected => {
                    info!("Status: WebRTC connected");
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:connected", ());
                    }
                }
                StreamingStatus::Streaming { fps, codec, hdr_active } => {
                    info!("Status: Streaming (FPS: {}, Codec: {}, HDR: {})", fps, codec, hdr_active);
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:streaming", serde_json::json!({
                            "fps": fps,
                            "codec": codec,
                            "hdr_active": hdr_active,
                        }));
                    }
                }
                StreamingStatus::Error(e) => {
                    warn!("Status: Error - {}", e);
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:error", e.clone());
                    }
                }
                StreamingStatus::Stopped => {
                    info!("Status: Stopped");
                    if let Some(window) = app_clone.get_webview_window("main") {
                        let _ = window.emit("native-streaming:stopped", ());
                    }

                    // Clean up bridge from global state
                    let mut bridge_guard = BRIDGE.lock();
                    *bridge_guard = None;

                    break; // Exit forwarding loop
                }
            }
        }

        info!("Status forwarding task ended");
    });

    Ok(())
}

/// Stop active native streaming session
///
/// Sends shutdown signal to the native client and waits for clean exit.
/// The Tauri app window will be restored after streaming stops.
///
/// # Returns
/// * `Ok(())` - Streaming stopped successfully
/// * `Err(String)` - Error message
#[cfg(feature = "native-client")]
#[tauri::command]
pub async fn stop_native_streaming() -> Result<(), String> {
    use log::{info, warn};

    info!("Stop native streaming command received");

    let mut bridge_guard = BRIDGE.lock();

    if let Some(bridge_arc) = bridge_guard.take() {
        info!("Shutting down active streaming session...");
        drop(bridge_guard); // Release lock before blocking operation

        // Try to unwrap Arc to get ownership
        match Arc::try_unwrap(bridge_arc) {
            Ok(bridge) => {
                // We have exclusive ownership, can shutdown
                bridge.shutdown()
                    .map_err(|e| {
                        warn!("Error during shutdown: {}", e);
                        format!("Error stopping streaming: {}", e)
                    })?;

                info!("Native streaming stopped successfully");
                Ok(())
            }
            Err(arc) => {
                // There are still other references (shouldn't happen normally)
                warn!("Cannot shutdown - bridge still has {} references", Arc::strong_count(&arc));

                // Just send shutdown signal via drop
                drop(arc);

                info!("Shutdown signal sent");
                Ok(())
            }
        }
    } else {
        warn!("No active streaming session to stop");
        Err("No active streaming session".to_string())
    }
}

/// Check if native streaming is currently active
///
/// # Returns
/// * `true` - Streaming session is active
/// * `false` - No active streaming session
#[cfg(feature = "native-client")]
#[tauri::command]
pub fn is_native_streaming_active() -> bool {
    BRIDGE.lock().is_some()
}

/// Detect HDR capabilities of the display
///
/// This can be called by the frontend to show HDR status in the UI
/// before starting a streaming session.
///
/// # Returns
/// * `Ok(capabilities)` - HDR detection results
/// * `Err(String)` - Error message
#[cfg(feature = "native-client")]
#[tauri::command]
pub async fn detect_hdr_capabilities() -> Result<serde_json::Value, String> {
    use log::{info, warn};
    use crate::native::hdr_detection;

    info!("HDR detection command received");

    tokio::task::spawn_blocking(|| {
        hdr_detection::detect_hdr_capabilities()
    })
    .await
    .map_err(|e| format!("HDR detection task failed: {}", e))?
    .map(|caps| {
        info!("HDR detected: supported={}, max_luminance={:.1} nits",
              caps.is_supported(), caps.max_luminance());

        serde_json::json!({
            "hdr_supported": caps.is_supported(),
            "max_luminance": caps.max_luminance(),
            "min_luminance": caps.min_luminance(),
            "max_frame_average_luminance": caps.max_frame_average_luminance(),
        })
    })
    .map_err(|e| {
        warn!("HDR detection failed: {}", e);
        format!("Failed to detect HDR: {}", e)
    })
}

// Stub implementations when native-client feature is disabled
#[cfg(not(feature = "native-client"))]
#[tauri::command]
pub async fn start_native_streaming(
    _app: AppHandle,
    _server: String,
    _session_id: String,
    _width: u32,
    _height: u32,
    _codec: String,
    _colorspace: String,
) -> Result<(), String> {
    Err("Native streaming not available (native-client feature disabled)".to_string())
}

#[cfg(not(feature = "native-client"))]
#[tauri::command]
pub async fn stop_native_streaming() -> Result<(), String> {
    Err("Native streaming not available".to_string())
}

#[cfg(not(feature = "native-client"))]
#[tauri::command]
pub fn is_native_streaming_active() -> bool {
    false
}

#[cfg(not(feature = "native-client"))]
#[tauri::command]
pub async fn detect_hdr_capabilities() -> Result<serde_json::Value, String> {
    Ok(serde_json::json!({
        "hdr_supported": false,
        "max_luminance": 80.0,
        "min_luminance": 0.0,
        "max_frame_average_luminance": 80.0,
    }))
}
