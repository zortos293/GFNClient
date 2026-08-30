use std::collections::HashMap;
use std::net::UdpSocket;
use std::sync::mpsc::{Receiver, Sender};
use std::sync::{Arc, Mutex, MutexGuard, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64;
use opennow_streamer_platform::{
    CapturedInput, CapturedInputQueue, CapturedInputSample, EncodedFrame, EncodedMicrophoneQueue,
    MediaCodec, MediaColorQuality, MediaControl, MediaFeedback, MediaRuntime, MediaRuntimeControl,
    MediaSession, MediaSink, MediaStreamConfig, MediaVideoCodec, MicrophoneCapture, PushOutcome,
    RecordingSummary, StreamShortcutAction, StreamShortcutBindings, record_matroska,
    supports_audio_decode, supports_audio_output, video_backends,
};
use opennow_streamer_protocol::{
    Capabilities, Command, PROTOCOL_VERSION, SessionContext, error, event, response,
};
use opennow_streamer_transport::{
    EncodedMicrophoneFrame, NegotiatedVideoCodec, NvstDropReason, NvstReceiveEvent,
    NvstReceiverState, NvstRecovery, NvstUdpReceiverControl, NvstUdpReceiverSession,
    PreferredVideoTransport, ReservedNvstBundle, SharedNvstFeedback, TransportControl,
    TransportEvent, TransportSession, negotiate, reserve_nvst_mjolnir_udp_socket,
    select_preferred_video_transport, spawn_nvst_mjolnir_receiver,
    spawn_nvst_udp_receiver_with_socket,
};
use serde_json::{Value, json};

mod nvst_rtsp;

use nvst_rtsp::{ActiveNvstRtspSession, prepare_owned_nvst};

pub use opennow_streamer_transport::{EncodedMediaFrame, MediaConsumer};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum State {
    Idle,
    Prepared,
    Negotiating,
    Connected,
}

const ENCODED_MEDIA_QUEUE_CAPACITY: usize = 8;
const NVST_RECOVERY_ATTEMPT_LIMIT: usize = 1;
const NATIVE_INPUT_POLL_INTERVAL: Duration = Duration::from_micros(250);

trait NvstSessionResources {
    fn request_keyframe(&self);
    fn acknowledge_video_frame(&self, bytes: u32);
    fn send_captured_input(&self, bytes: Vec<u8>) -> Result<(), String>;
    fn apply_cursor(&self, bytes: Vec<u8>);
    fn recover(&self) -> Result<(), String>;
    fn stop(&self);
}

struct ActiveNvstResources {
    bundle: NvstUdpReceiverControl,
    mjolnir: Option<NvstUdpReceiverControl>,
    feedback: SharedNvstFeedback,
    media: Option<MediaControl>,
}

impl NvstSessionResources for ActiveNvstResources {
    fn request_keyframe(&self) {
        self.feedback.request_keyframe();
    }

    fn acknowledge_video_frame(&self, bytes: u32) {
        self.feedback.publish_accepted_frame(bytes, Instant::now());
    }

    fn send_captured_input(&self, bytes: Vec<u8>) -> Result<(), String> {
        self.bundle
            .queue_input(bytes, false)
            .map_err(|error| error.to_string())
    }

    fn apply_cursor(&self, bytes: Vec<u8>) {
        if let Some(media) = self.media.as_ref() {
            media.update_cursor(bytes);
        }
    }

    fn recover(&self) -> Result<(), String> {
        self.bundle
            .recover()
            .map_err(|error| format!("bundle recovery failed: {error}"))?;
        if let Some(mjolnir) = self.mjolnir.as_ref() {
            mjolnir
                .recover()
                .map_err(|error| format!("Mjolnir recovery failed: {error}"))?;
        }
        Ok(())
    }

    fn stop(&self) {
        let _ = self.bundle.stop();
        if let Some(mjolnir) = self.mjolnir.as_ref() {
            let _ = mjolnir.stop();
        }
        if let Some(media) = self.media.as_ref() {
            media.stop();
        }
    }
}

pub struct Engine {
    lifecycle: Arc<Mutex<Lifecycle>>,
    transport: Option<TransportSession>,
    nvst_transport: Option<NvstUdpReceiverSession>,
    nvst_mjolnir_transport: Option<NvstUdpReceiverSession>,
    reserved_nvst_bundle: Option<ReservedNvstBundle>,
    nvst_hole_punch_socket: Option<UdpSocket>,
    nvst_rtsp: Option<ActiveNvstRtspSession>,
    events: Sender<Value>,
    media_consumer: Option<MediaConsumer>,
    media_runtime: Option<MediaRuntime>,
    media_session: Option<MediaSession>,
    media_worker: Option<JoinHandle<()>>,
    media_feedback: Option<Receiver<MediaFeedback>>,
    feedback_worker: Option<JoinHandle<()>>,
    microphone_capture: Option<MicrophoneCapture>,
    microphone_enabled: bool,
    recording_worker: Option<JoinHandle<Result<RecordingSummary, String>>>,
}

#[derive(Debug)]
struct Lifecycle {
    state: State,
    context: Option<SessionContext>,
    generation: u64,
}

impl Engine {
    pub fn new(events: Sender<Value>) -> Self {
        Self {
            lifecycle: Arc::new(Mutex::new(Lifecycle {
                state: State::Idle,
                context: None,
                generation: 0,
            })),
            transport: None,
            nvst_transport: None,
            nvst_mjolnir_transport: None,
            reserved_nvst_bundle: None,
            nvst_hole_punch_socket: None,
            nvst_rtsp: None,
            events,
            media_consumer: None,
            media_runtime: None,
            media_session: None,
            media_worker: None,
            media_feedback: None,
            feedback_worker: None,
            microphone_capture: None,
            microphone_enabled: false,
            recording_worker: None,
        }
    }

    pub fn with_media_consumer(events: Sender<Value>, media_consumer: MediaConsumer) -> Self {
        Self {
            lifecycle: Arc::new(Mutex::new(Lifecycle {
                state: State::Idle,
                context: None,
                generation: 0,
            })),
            transport: None,
            nvst_transport: None,
            nvst_mjolnir_transport: None,
            reserved_nvst_bundle: None,
            nvst_hole_punch_socket: None,
            nvst_rtsp: None,
            events,
            media_consumer: Some(media_consumer),
            media_runtime: None,
            media_session: None,
            media_worker: None,
            media_feedback: None,
            feedback_worker: None,
            microphone_capture: None,
            microphone_enabled: false,
            recording_worker: None,
        }
    }

    pub fn with_media_runtime(events: Sender<Value>, media_runtime: MediaRuntime) -> Self {
        Self {
            lifecycle: Arc::new(Mutex::new(Lifecycle {
                state: State::Idle,
                context: None,
                generation: 0,
            })),
            transport: None,
            nvst_transport: None,
            nvst_mjolnir_transport: None,
            reserved_nvst_bundle: None,
            nvst_hole_punch_socket: None,
            nvst_rtsp: None,
            events,
            media_consumer: None,
            media_runtime: Some(media_runtime),
            media_session: None,
            media_worker: None,
            media_feedback: None,
            feedback_worker: None,
            microphone_capture: None,
            microphone_enabled: false,
            recording_worker: None,
        }
    }

    pub fn handle(&mut self, command: Command) -> (Vec<Value>, bool) {
        let id = command.id.clone();
        let result = match command.kind.as_str() {
            "hello" => self.hello(&command),
            "nvst-bind" => self.nvst_bind(command),
            "nvst-unbind" => self.nvst_unbind(command),
            "nvst-send" => self.nvst_send(command),
            "start" => self.start(command),
            "offer" => self.offer(command),
            "remote-ice" => self.remote_ice(command),
            "input" => self.input(command),
            "input-paused" => self.set_paused(command),
            "surface" => self.update_surface(command),
            "stats-toggle" => Ok(vec![
                response(id, "ok"),
                event(
                    "shortcut-action",
                    json!({"action":"toggle-stats", "source":"command"}),
                ),
            ]),
            "fullscreen-toggle" => Ok(vec![
                response(id, "ok"),
                event(
                    "shortcut-action",
                    json!({"action":"toggle-fullscreen", "source":"command"}),
                ),
            ]),
            "microphone-toggle" => self.toggle_microphone(command),
            "anti-afk-pulse" => self.anti_afk_pulse(command),
            "recording-start" => self.start_recording(command),
            "recording-stop" => self.stop_recording(command),
            "bitrate" | "update-shortcuts" => Err(error(
                Some(&id),
                "unsupported-command",
                format!("Native streamer cannot apply the {} command", command.kind),
            )),
            "stop" => {
                self.stop(command.reason.as_deref().unwrap_or("stopped"));
                Ok(vec![response(id, "ok")])
            }
            "shutdown" => {
                self.stop(command.reason.as_deref().unwrap_or("shutdown"));
                return (vec![response(id, "ok")], false);
            }
            other => Err(error(
                Some(&id),
                "unknown-command",
                format!("Unknown command: {other}"),
            )),
        };

        match result {
            Ok(values) => (values, true),
            Err(value) => (vec![value], true),
        }
    }

    fn hello(&self, command: &Command) -> Result<Vec<Value>, Value> {
        if command.protocol_version != Some(PROTOCOL_VERSION) {
            return Err(error(
                Some(&command.id),
                "protocol-version-mismatch",
                format!("Native streamer requires protocol {PROTOCOL_VERSION}"),
            ));
        }
        let backends = video_backends();
        let media_ready = self.media_runtime.is_some();
        let video_ready = media_ready && backends.iter().any(|backend| backend.available);
        let capabilities = Capabilities {
            protocol_version: PROTOCOL_VERSION,
            backend: "native",
            fallback_reason: (!media_ready)
                .then_some("Native streamer requires an in-process decoded media runtime"),
            supports_offer_answer: media_ready,
            supports_remote_ice: media_ready,
            supports_local_ice: media_ready,
            supports_input: media_ready,
            supports_video_decode: video_ready,
            supports_video_present: video_ready,
            supports_audio_decode: media_ready && supports_audio_decode(),
            supports_audio_output: media_ready && supports_audio_output(),
            supports_owned_nvst_negotiation: media_ready,
            video_backends: backends,
        };
        let mut ready = json!({
            "id": command.id,
            "type": "ready",
            "processId": std::process::id(),
            "capabilities": capabilities,
        });
        ready["capabilities"]["microphoneDevices"] =
            json!(MicrophoneCapture::device_names().unwrap_or_default());
        Ok(vec![ready])
    }

    fn nvst_bind(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        if self.reserved_nvst_bundle.is_none() {
            let bundle = ReservedNvstBundle::reserve().map_err(|bind_error| {
                error(
                    Some(&command.id),
                    "nvst-bind-failed",
                    format!("failed to reserve NVST UDP socket: {bind_error}"),
                )
            })?;
            eprintln!(
                "NVST reserved video UDP socket on {} (Mjolnir on {})",
                bundle
                    .local_addr()
                    .map(|addr| addr.to_string())
                    .unwrap_or_else(|_| "unknown".to_owned()),
                bundle
                    .mjolnir_local_addr()
                    .map(|addr| addr.to_string())
                    .unwrap_or_else(|_| "unknown".to_owned()),
            );
            self.reserved_nvst_bundle = Some(bundle);
        }
        let bundle = self.reserved_nvst_bundle.as_mut().ok_or_else(|| {
            error(
                Some(&command.id),
                "nvst-bind-failed",
                "reserved NVST UDP socket has no local port",
            )
        })?;
        let local_addr = bundle.local_addr().map_err(|_| {
            error(
                Some(&command.id),
                "nvst-bind-failed",
                "reserved NVST UDP socket has no local port",
            )
        })?;
        let mjolnir_addr = bundle.mjolnir_local_addr().map_err(|_| {
            error(
                Some(&command.id),
                "nvst-bind-failed",
                "reserved NVST Mjolnir UDP socket has no local port",
            )
        })?;
        let port = local_addr.port();
        let local_address = bundle.advertised_local_address();
        let identity = bundle.identity();
        Ok(vec![json!({
            "id": command.id,
            "type": "nvst-bound",
            "port": port,
            "mjolnirPort": mjolnir_addr.port(),
            "localAddress": local_address,
            "iceUsernameFragment": identity.ice_username_fragment,
            "icePassword": identity.ice_password,
            "dtlsFingerprint": identity.dtls_fingerprint,
        })])
    }

    fn nvst_send(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        let host = command.host.ok_or_else(|| {
            error(
                Some(&command.id),
                "nvst-send-failed",
                "nvst-send requires host",
            )
        })?;
        let port = command.port.ok_or_else(|| {
            error(
                Some(&command.id),
                "nvst-send-failed",
                "nvst-send requires port",
            )
        })?;
        let payload = BASE64
            .decode(command.payload_base64.unwrap_or_default())
            .map_err(|decode_error| {
                error(
                    Some(&command.id),
                    "nvst-send-failed",
                    format!("nvst-send payload is not valid base64: {decode_error}"),
                )
            })?;
        let send_result = if let Some(bundle) = self.reserved_nvst_bundle.as_ref() {
            bundle.send_to(&payload, host.as_str(), port)
        } else if let Some(socket) = self.nvst_hole_punch_socket.as_ref() {
            socket.send_to(&payload, (host.as_str(), port))
        } else {
            return Err(error(
                Some(&command.id),
                "nvst-send-failed",
                "NVST UDP socket has not been reserved",
            ));
        };
        send_result.map_err(|send_error| {
            error(
                Some(&command.id),
                "nvst-send-failed",
                format!("failed to send NVST UDP datagram: {send_error}"),
            )
        })?;
        Ok(vec![response(command.id, "ok")])
    }

    fn nvst_unbind(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        let lifecycle = lock_lifecycle(&self.lifecycle);
        if lifecycle.state != State::Idle
            || self.nvst_transport.is_some()
            || self.nvst_mjolnir_transport.is_some()
        {
            return Err(error(
                Some(&command.id),
                "nvst-unbind-in-use",
                "Cannot release an NVST UDP reservation after session start",
            ));
        }
        drop(lifecycle);
        self.reserved_nvst_bundle = None;
        self.nvst_hole_punch_socket = None;
        Ok(vec![response(command.id, "ok")])
    }

    fn start(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        let mut context = parse_context(command.context, &command.id)?;
        validate_context(&context, &command.id)?;
        {
            let lifecycle = lock_lifecycle(&self.lifecycle);
            if lifecycle.state != State::Idle {
                return Err(invalid_state(&command.id, "start", lifecycle.state, "Idle"));
            }
        }
        let wants_owned_nvst = context
            .settings
            .get("transportMode")
            .and_then(Value::as_str)
            .is_some_and(|mode| mode.eq_ignore_ascii_case("nvst"))
            && context.nvst_video.is_none();
        let mut prepared_nvst = if wants_owned_nvst {
            if self.reserved_nvst_bundle.is_none() {
                self.reserved_nvst_bundle =
                    Some(ReservedNvstBundle::reserve().map_err(|error_value| {
                        error(
                            Some(&command.id),
                            "nvst-bind-failed",
                            format!(
                                "Native streamer could not reserve its NVST sockets: {error_value}"
                            ),
                        )
                    })?);
            }
            let prepared_result = {
                let bundle = self
                    .reserved_nvst_bundle
                    .as_mut()
                    .expect("NVST reservation created above");
                prepare_owned_nvst(&context, bundle)
            };
            let prepared = match prepared_result {
                Ok(prepared) => prepared,
                Err(negotiation_error) => {
                    self.reserved_nvst_bundle = None;
                    return Err(error(
                        Some(&command.id),
                        negotiation_error.code,
                        negotiation_error.message,
                    ));
                }
            };
            context.nvst_video = Some(prepared.handoff.clone());
            Some(prepared)
        } else {
            None
        };
        let transport_context = serde_json::to_value(&context).map_err(|context_error| {
            error(
                Some(&command.id),
                "invalid-context",
                format!("Session context is not serializable: {context_error}"),
            )
        })?;
        let explicit_nvst = transport_context
            .pointer("/settings/transportMode")
            .and_then(Value::as_str)
            .is_some_and(|mode| mode.eq_ignore_ascii_case("nvst"))
            || transport_context.get("nvstVideo").is_some()
            || transport_context.get("nvstTransport").is_some();
        let (nvst_config, fallback_note) =
            match select_preferred_video_transport(&transport_context) {
                PreferredVideoTransport::Nvst(config) => (Some(*config), None),
                PreferredVideoTransport::WebRtcFallback(reason) if explicit_nvst => {
                    return Err(error(
                        Some(&command.id),
                        "invalid-nvst-handoff",
                        format!("Explicit NVST transport is invalid: {reason:?}"),
                    ));
                }
                PreferredVideoTransport::WebRtcFallback(reason) => (
                    None,
                    Some(format!(
                        "NVST unavailable; using WebRTC fallback: {reason:?}"
                    )),
                ),
            };
        let nvst_bundle_available = nvst_config
            .as_ref()
            .is_some_and(|config| config.remote_dtls_fingerprint().is_some());
        let nvst_audio_negotiated = nvst_config
            .as_ref()
            .is_some_and(|config| config.audio_track().is_some());

        if let Some(transport) = self.transport.take() {
            transport.stop();
        }
        if let Some(transport) = self.nvst_transport.take() {
            transport.stop();
        }
        if let Some(transport) = self.nvst_mjolnir_transport.take() {
            transport.stop();
        }
        if let Some(mut rtsp) = self.nvst_rtsp.take() {
            rtsp.shutdown();
        }
        self.stop_media_resources();
        if let Some(runtime) = self.media_runtime.clone() {
            let (feedback_sender, feedback_receiver) = std::sync::mpsc::channel();
            let stream_config = media_stream_config(&context);
            let session = runtime
                .start(feedback_sender, stream_config)
                .map_err(|message| error(Some(&command.id), "media-output-unavailable", message))?;
            let sink = session.sink();
            let (media_consumer, media_receiver) =
                std::sync::mpsc::sync_channel(ENCODED_MEDIA_QUEUE_CAPACITY);
            let output = self.events.clone();
            let media_worker = match thread::Builder::new()
                .name("opennow-media-consumer".to_owned())
                .spawn(move || consume_encoded_media(&output, media_receiver, sink))
            {
                Ok(worker) => worker,
                Err(spawn_error) => {
                    session.stop();
                    return Err(error(
                        Some(&command.id),
                        "media-worker-failed",
                        spawn_error.to_string(),
                    ));
                }
            };
            self.media_consumer = Some(media_consumer);
            self.media_session = Some(session);
            self.media_worker = Some(media_worker);
            self.media_feedback = Some(feedback_receiver);
        }

        if let Some(prepared) = prepared_nvst.as_mut()
            && let Err(negotiation_error) = prepared.announce()
        {
            self.stop_media_resources();
            self.reserved_nvst_bundle = None;
            return Err(error(
                Some(&command.id),
                negotiation_error.code,
                negotiation_error.message,
            ));
        }

        let mut nvst_events = None;
        let mut nvst_resources = None;
        if let Some(config) = nvst_config {
            let Some(media_consumer) = self.media_consumer.clone() else {
                self.stop_media_resources();
                return Err(error(
                    Some(&command.id),
                    "media-consumer-unavailable",
                    "NVST video requires an in-process encoded media consumer",
                ));
            };
            let (event_sender, event_receiver) = std::sync::mpsc::channel();
            let (reserved_socket, reserved_rtc, reserved_mjolnir) =
                match self.reserved_nvst_bundle.take() {
                    Some(bundle) => {
                        self.nvst_hole_punch_socket = bundle.try_clone_socket().ok();
                        let (socket, rtc, mjolnir_socket) = bundle.into_parts();
                        (Some(socket), Some(rtc), Some(mjolnir_socket))
                    }
                    None => (None, None, None),
                };
            let mjolnir_udp_port = config.mjolnir_udp_port();
            let feedback = config.feedback();
            let transport = match spawn_nvst_udp_receiver_with_socket(
                config.clone(),
                media_consumer.clone(),
                event_sender.clone(),
                reserved_socket,
                reserved_rtc,
            ) {
                Ok(transport) => transport,
                Err(transport_error) => {
                    drop(media_consumer);
                    self.stop_media_resources();
                    return Err(error(
                        Some(&command.id),
                        "nvst-start-failed",
                        transport_error.to_string(),
                    ));
                }
            };
            let bundle_control = transport.control();
            self.nvst_transport = Some(transport);
            let mut mjolnir_control = None;
            if let Some(expected_port) = mjolnir_udp_port {
                // Official two-socket model: video RTP/SRTP arrives on the
                // dedicated NATT-only Mjolnir socket, not on the ICE/DTLS bundle.
                let mjolnir_socket = match reserved_mjolnir {
                    Some(socket) => {
                        let actual_port = socket.local_addr().map(|addr| addr.port()).unwrap_or(0);
                        if actual_port != expected_port {
                            eprintln!(
                                "NVST Mjolnir socket port mismatch: reserved {actual_port}, handoff expects {expected_port}; NATT keepalive determines routing"
                            );
                        }
                        socket
                    }
                    None => {
                        eprintln!(
                            "NVST Mjolnir reservation missing at start; binding a fresh video UDP socket"
                        );
                        reserve_nvst_mjolnir_udp_socket().map_err(|bind_error| {
                            if let Some(transport) = self.nvst_transport.take() {
                                transport.stop();
                            }
                            self.stop_media_resources();
                            error(
                                Some(&command.id),
                                "nvst-start-failed",
                                format!("failed to reserve NVST Mjolnir UDP socket: {bind_error}"),
                            )
                        })?
                    }
                };
                let mjolnir = spawn_nvst_mjolnir_receiver(
                    mjolnir_socket,
                    config,
                    media_consumer,
                    event_sender,
                )
                .map_err(|mjolnir_error| {
                    if let Some(transport) = self.nvst_transport.take() {
                        transport.stop();
                    }
                    self.stop_media_resources();
                    error(
                        Some(&command.id),
                        "nvst-start-failed",
                        mjolnir_error.to_string(),
                    )
                })?;
                mjolnir_control = Some(mjolnir.control());
                self.nvst_mjolnir_transport = Some(mjolnir);
            }
            nvst_resources = Some(ActiveNvstResources {
                bundle: bundle_control,
                mjolnir: mjolnir_control,
                feedback,
                media: self.media_session.as_ref().map(MediaSession::control),
            });
            nvst_events = Some(event_receiver);
        } else {
            self.reserved_nvst_bundle = None;
            self.nvst_hole_punch_socket = None;
        }

        let generation = {
            let mut lifecycle = lock_lifecycle(&self.lifecycle);
            lifecycle.generation = lifecycle.generation.wrapping_add(1);
            lifecycle.context = Some(context);
            lifecycle.state = if nvst_events.is_some() {
                State::Connected
            } else {
                State::Prepared
            };
            lifecycle.generation
        };
        if let Some(nvst_events) = nvst_events {
            let output = self.events.clone();
            let lifecycle = self.lifecycle.clone();
            let media_feedback = self.media_feedback.take();
            let captured_input = self
                .media_session
                .as_ref()
                .map(MediaSession::captured_input);
            let shortcut_runtime = self.media_runtime.clone();
            let nvst_resources = nvst_resources.expect("NVST events require active resources");
            self.feedback_worker = thread::Builder::new()
                .name("opennow-nvst-events".to_owned())
                .spawn(move || {
                    forward_nvst_session_events(
                        &output,
                        &lifecycle,
                        generation,
                        NvstSessionEventResources {
                            nvst_events,
                            media_feedback,
                            captured_input,
                            shortcut_runtime,
                            transport: nvst_resources,
                        },
                    );
                })
                .ok();
            if self.feedback_worker.is_none() {
                if let Some(transport) = self.nvst_transport.take() {
                    transport.stop();
                }
                if let Some(transport) = self.nvst_mjolnir_transport.take() {
                    transport.stop();
                }
                self.stop_media_resources();
                let mut lifecycle = lock_lifecycle(&self.lifecycle);
                if lifecycle.generation == generation {
                    lifecycle.context = None;
                    lifecycle.state = State::Idle;
                }
                return Err(error(
                    Some(&command.id),
                    "media-worker-failed",
                    "Failed to start NVST lifecycle worker",
                ));
            }
        }
        if let Some(prepared) = prepared_nvst {
            match prepared.finish() {
                Ok(active) => self.nvst_rtsp = Some(active),
                Err(negotiation_error) => {
                    self.stop("Native-owned NVST negotiation failed");
                    return Err(error(
                        Some(&command.id),
                        negotiation_error.code,
                        negotiation_error.message,
                    ));
                }
            }
        }
        let _ = self.events.send(event(
            "status",
            json!({
                "status": "ready",
                "message": if self.nvst_transport.is_some() {
                    "NVST authenticated H.264 receive path initialized"
                } else if self.media_runtime.is_some() {
                    "H.264 video and Opus audio media path initialized"
                } else {
                    "Native WebRTC session prepared"
                }
            }),
        ));
        if let Some(note) = fallback_note {
            let _ = self
                .events
                .send(event("log", json!({ "level": "debug", "message": note })));
        }
        let mut start_response = response(command.id, "ok");
        let using_nvst = self.nvst_transport.is_some();
        start_response["transport"] =
            Value::String(if using_nvst { "nvst" } else { "webrtc" }.to_owned());
        start_response["capabilities"] = if using_nvst {
            json!({
                "supportsOfferAnswer": false,
                "supportsRemoteIce": false,
                "supportsLocalIce": false,
                "supportsInput": nvst_bundle_available,
                "supportsAudioDecode": nvst_audio_negotiated && supports_audio_decode(),
                "supportsAudioOutput": nvst_audio_negotiated && supports_audio_output(),
            })
        } else {
            json!({
                "supportsOfferAnswer": self.media_runtime.is_some(),
                "supportsRemoteIce": self.media_runtime.is_some(),
                "supportsLocalIce": self.media_runtime.is_some(),
                "supportsInput": self.media_runtime.is_some(),
                "supportsAudioDecode": self.media_runtime.is_some() && supports_audio_decode(),
                "supportsAudioOutput": self.media_runtime.is_some() && supports_audio_output(),
            })
        };
        Ok(vec![start_response])
    }

    fn offer(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        if self.nvst_transport.is_some() {
            return Err(error(
                Some(&command.id),
                "nvst-video-active",
                "NVST video is active; do not negotiate a WebRTC media offer for this session",
            ));
        }
        let offer_sdp = command.sdp.as_deref().ok_or_else(|| {
            error(
                Some(&command.id),
                "missing-sdp",
                "Offer command does not include SDP",
            )
        })?;
        let offered_context = command
            .context
            .map(|context| parse_context(Some(context), &command.id))
            .transpose()?;
        if let Some(context) = &offered_context {
            validate_context(context, &command.id)?;
        }
        let (context, generation) = {
            let mut lifecycle = lock_lifecycle(&self.lifecycle);
            if lifecycle.state == State::Idle {
                return Err(error(
                    Some(&command.id),
                    "not-started",
                    "Start must be sent before offer",
                ));
            }
            if lifecycle.state != State::Prepared {
                return Err(invalid_state(
                    &command.id,
                    "offer",
                    lifecycle.state,
                    "Prepared",
                ));
            }
            let Some(stored_context) = lifecycle.context.as_ref() else {
                lifecycle.state = State::Idle;
                return Err(error(
                    Some(&command.id),
                    "invalid-state",
                    "Prepared lifecycle is missing its session context",
                ));
            };
            let stored_session_id = stored_context.session.session_id.clone();
            if let Some(context) = offered_context {
                if context.session.session_id != stored_session_id {
                    return Err(error(
                        Some(&command.id),
                        "session-mismatch",
                        "Offer context does not match the prepared session",
                    ));
                }
                lifecycle.context = Some(context);
            }
            let Some(context) = lifecycle.context.clone() else {
                lifecycle.state = State::Idle;
                return Err(error(
                    Some(&command.id),
                    "invalid-state",
                    "Prepared lifecycle is missing its session context",
                ));
            };
            lifecycle.state = State::Negotiating;
            (context, lifecycle.generation)
        };
        let Some(media_consumer) = self.media_consumer.clone() else {
            let mut lifecycle = lock_lifecycle(&self.lifecycle);
            if lifecycle.generation == generation && lifecycle.state == State::Negotiating {
                lifecycle.state = State::Prepared;
            }
            return Err(error(
                Some(&command.id),
                "media-consumer-unavailable",
                "No in-process encoded media consumer is configured",
            ));
        };
        let threshold = partial_reliable_threshold(offer_sdp).unwrap_or(300);
        let video_codec = match media_stream_config(&context).codec {
            MediaVideoCodec::H264 => NegotiatedVideoCodec::H264,
            MediaVideoCodec::H265 => NegotiatedVideoCodec::H265,
            MediaVideoCodec::Av1 => NegotiatedVideoCodec::Av1,
        };
        let (transport_events, receiver) = std::sync::mpsc::channel();
        let negotiated = negotiate(
            offer_sdp,
            &context.session,
            video_codec,
            threshold,
            transport_events,
            media_consumer,
        )
        .map_err(|transport_error| {
            let mut lifecycle = lock_lifecycle(&self.lifecycle);
            if lifecycle.generation == generation && lifecycle.state == State::Negotiating {
                lifecycle.state = State::Prepared;
            }
            error(
                Some(&command.id),
                transport_error.code(),
                transport_error.to_string(),
            )
        })?;
        let output = self.events.clone();
        let lifecycle = self.lifecycle.clone();
        let transport_control = negotiated.session.control();
        let media_feedback = self.media_feedback.take();
        let captured_input = self
            .media_session
            .as_ref()
            .map(MediaSession::captured_input);
        let microphone_packets = match context
            .settings
            .get("microphoneMode")
            .and_then(Value::as_str)
            .unwrap_or("disabled")
        {
            "voice-activity" if self.media_runtime.is_some() => {
                let configured_device = context
                    .settings
                    .get("microphoneDeviceId")
                    .and_then(Value::as_str)
                    .filter(|value| !value.trim().is_empty());
                let capture = MicrophoneCapture::start(configured_device).or_else(|configured_error| {
                    if configured_device.is_none() {
                        return Err(configured_error);
                    }
                    let _ = self.events.send(event(
                        "log",
                        json!({"level":"warn","message":"Configured microphone is unavailable; using the system default"}),
                    ));
                    MicrophoneCapture::start(None).map_err(|fallback_error| {
                        format!("configured microphone failed ({configured_error}); default microphone failed ({fallback_error})")
                    })
                });
                match capture {
                    Ok(capture) => {
                        let packets = capture.packets();
                        self.microphone_capture = Some(capture);
                        self.microphone_enabled = true;
                        Some(packets)
                    }
                    Err(message) => {
                        let _ = self.events.send(event(
                            "microphone-state",
                            json!({"state":"unavailable","enabled":false,"message":message}),
                        ));
                        None
                    }
                }
            }
            "push-to-talk" => {
                let _ = self.events.send(event(
                    "microphone-state",
                    json!({
                        "state":"unavailable",
                        "enabled":false,
                        "message":"Push-to-talk requires dynamic shortcut negotiation and remains disabled"
                    }),
                ));
                None
            }
            _ => None,
        };
        let shortcut_runtime = self.media_runtime.clone();
        let feedback_worker = thread::Builder::new()
            .name("opennow-media-events".to_owned())
            .spawn(move || {
                forward_session_events(
                    &output,
                    &lifecycle,
                    generation,
                    WebrtcSessionResources {
                        transport_events: receiver,
                        media_feedback,
                        captured_input,
                        microphone_packets,
                        shortcut_runtime,
                        transport: transport_control,
                    },
                );
            });
        self.feedback_worker = Some(match feedback_worker {
            Ok(worker) => worker,
            Err(spawn_error) => {
                negotiated.session.stop();
                let mut lifecycle = lock_lifecycle(&self.lifecycle);
                if lifecycle.generation == generation && lifecycle.state == State::Negotiating {
                    lifecycle.state = State::Prepared;
                }
                drop(lifecycle);
                self.stop_media_resources();
                return Err(error(
                    Some(&command.id),
                    "media-worker-failed",
                    spawn_error.to_string(),
                ));
            }
        });
        self.transport = Some(negotiated.session);
        let _ = self.events.send(event(
            "local-ice",
            json!({ "candidate": negotiated.local_candidate }),
        ));
        Ok(vec![json!({
            "id": command.id,
            "type": "answer",
            "answer": { "sdp": negotiated.answer_sdp },
        })])
    }

    fn remote_ice(&self, command: Command) -> Result<Vec<Value>, Value> {
        if self.nvst_transport.is_some() {
            return Err(error(
                Some(&command.id),
                "nvst-remote-ice-unsupported",
                "NVST owns its negotiated ICE bundle and does not accept remote-ice commands",
            ));
        }
        let state = lock_lifecycle(&self.lifecycle).state;
        if !matches!(state, State::Negotiating | State::Connected) {
            return Err(invalid_state(
                &command.id,
                "remote-ice",
                state,
                "Negotiating or Connected",
            ));
        }
        let transport = self.transport.as_ref().ok_or_else(|| {
            error(
                Some(&command.id),
                "transport-not-ready",
                "No active WebRTC transport",
            )
        })?;
        let candidate = command.candidate.as_ref().ok_or_else(|| {
            error(
                Some(&command.id),
                "missing-candidate",
                "Remote ICE command is empty",
            )
        })?;
        transport
            .add_remote_candidate(candidate)
            .map_err(|transport_error| {
                error(
                    Some(&command.id),
                    transport_error.code(),
                    transport_error.to_string(),
                )
            })?;
        Ok(vec![response(command.id, "ok")])
    }

    fn input(&self, command: Command) -> Result<Vec<Value>, Value> {
        let state = lock_lifecycle(&self.lifecycle).state;
        if state != State::Connected {
            return Err(invalid_state(
                &command.id,
                "input",
                state,
                "Connected with an initialized input channel",
            ));
        }
        let input = command
            .input
            .as_ref()
            .ok_or_else(|| error(Some(&command.id), "missing-input", "Input command is empty"))?;
        let bytes = BASE64
            .decode(&input.payload_base64)
            .map_err(|decode_error| {
                error(Some(&command.id), "invalid-input", decode_error.to_string())
            })?;
        let send_result = if let Some(transport) = self.nvst_transport.as_ref() {
            transport.send_input(bytes, input.partially_reliable)
        } else if let Some(transport) = self.transport.as_ref() {
            transport.send_input(bytes, input.partially_reliable)
        } else {
            return Err(error(
                Some(&command.id),
                "transport-not-ready",
                "No active media transport",
            ));
        };
        send_result.map_err(|transport_error| {
            error(
                Some(&command.id),
                transport_error.code(),
                transport_error.to_string(),
            )
        })?;
        Ok(vec![response(command.id, "ok")])
    }

    fn set_paused(&self, command: Command) -> Result<Vec<Value>, Value> {
        let Some(runtime) = self.media_runtime.as_ref() else {
            return Err(error(
                Some(&command.id),
                "unsupported-command",
                "Native streamer has no media runtime for input-paused",
            ));
        };
        let paused = command.paused.ok_or_else(|| {
            error(
                Some(&command.id),
                "missing-paused",
                "Pause command does not include paused state",
            )
        })?;
        runtime
            .set_paused(paused)
            .map_err(|message| error(Some(&command.id), "media-host-unavailable", message))?;
        if let Some(media) = self.media_session.as_ref() {
            media.set_paused(paused);
        }
        if let Some(transport) = self.nvst_transport.as_ref() {
            let result = if paused {
                transport.pause()
            } else {
                transport.resume()
            };
            result.map_err(|transport_error| {
                error(
                    Some(&command.id),
                    "nvst-control-failed",
                    transport_error.to_string(),
                )
            })?;
        }
        if let Some(transport) = self.nvst_mjolnir_transport.as_ref() {
            let result = if paused {
                transport.pause()
            } else {
                transport.resume()
            };
            result.map_err(|transport_error| {
                error(
                    Some(&command.id),
                    "nvst-control-failed",
                    transport_error.to_string(),
                )
            })?;
        }
        Ok(vec![response(command.id, "ok")])
    }

    fn update_surface(&self, command: Command) -> Result<Vec<Value>, Value> {
        let Some(runtime) = self.media_runtime.as_ref() else {
            return Err(error(
                Some(&command.id),
                "unsupported-command",
                "Native streamer has no media runtime for surface",
            ));
        };
        let surface = command.surface.ok_or_else(|| {
            error(
                Some(&command.id),
                "missing-surface",
                "Surface command does not include a render surface",
            )
        })?;
        runtime
            .update_surface(surface)
            .map_err(|message| error(Some(&command.id), "media-host-unavailable", message))?;
        Ok(vec![response(command.id, "ok")])
    }

    fn stop(&mut self, reason: &str) {
        let was_active = {
            let mut lifecycle = lock_lifecycle(&self.lifecycle);
            let was_active = lifecycle.state != State::Idle;
            lifecycle.generation = lifecycle.generation.wrapping_add(1);
            lifecycle.context = None;
            lifecycle.state = State::Idle;
            was_active
        };
        if let Some(transport) = self.transport.take() {
            transport.stop();
        }
        if let Some(transport) = self.nvst_transport.take() {
            transport.stop();
        }
        if let Some(transport) = self.nvst_mjolnir_transport.take() {
            transport.stop();
        }
        if let Some(mut rtsp) = self.nvst_rtsp.take() {
            rtsp.shutdown();
        }
        self.reserved_nvst_bundle = None;
        self.nvst_hole_punch_socket = None;
        self.stop_media_resources();
        if was_active {
            let _ = self.events.send(event(
                "status",
                json!({ "status": "stopped", "message": reason }),
            ));
        }
    }

    fn stop_media_resources(&mut self) {
        let _ = self.stop_recording_inner();
        self.microphone_capture = None;
        self.microphone_enabled = false;
        if self.media_runtime.is_some() {
            self.media_consumer = None;
            if let Some(session) = self.media_session.take() {
                session.stop();
            }
            if let Some(worker) = self.media_worker.take() {
                let _ = worker.join();
            }
            self.media_feedback = None;
        }
        if let Some(worker) = self.feedback_worker.take() {
            let _ = worker.join();
        }
    }

    fn start_recording(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        if self.recording_worker.is_some() {
            return Err(error(
                Some(&command.id),
                "recording-already-active",
                "A native stream recording is already active",
            ));
        }
        let output_path = command
            .output_path
            .filter(|path| !path.is_empty())
            .ok_or_else(|| {
                error(
                    Some(&command.id),
                    "invalid-recording-output",
                    "Native recording requires an absolute .mkv output path",
                )
            })?;
        let path = std::path::PathBuf::from(&output_path);
        if !path.is_absolute() || path.extension().and_then(|value| value.to_str()) != Some("mkv") {
            return Err(error(
                Some(&command.id),
                "invalid-recording-output",
                "Native recording requires an absolute .mkv output path",
            ));
        }
        let control = self
            .media_session
            .as_ref()
            .map(MediaSession::control)
            .ok_or_else(|| {
                error(
                    Some(&command.id),
                    "media-output-unavailable",
                    "Native recording requires an active media session",
                )
            })?;
        let (stream, receiver) = control
            .subscribe_recording()
            .map_err(|message| error(Some(&command.id), "recording-start-failed", message))?;
        let events = self.events.clone();
        let worker_path = path.clone();
        let worker = thread::Builder::new()
            .name("opennow-matroska-recording".to_owned())
            .spawn(move || {
                let result = record_matroska(&worker_path, stream, receiver);
                let payload = match &result {
                    Ok(summary) => json!({
                        "state":"saved",
                        "path":summary.path,
                        "videoPackets":summary.video_packets,
                        "audioPackets":summary.audio_packets,
                    }),
                    Err(message) => json!({"state":"failed","message":message}),
                };
                let _ = events.send(event("recording-state", payload));
                result
            })
            .map_err(|spawn_error| {
                control.unsubscribe_recording();
                error(
                    Some(&command.id),
                    "recording-worker-failed",
                    spawn_error.to_string(),
                )
            })?;
        self.recording_worker = Some(worker);
        Ok(vec![json!({
            "id":command.id,
            "type":"recording-started",
            "path":path,
        })])
    }

    fn stop_recording(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        match self.stop_recording_inner() {
            Ok(Some(summary)) => Ok(vec![json!({
                "id":command.id,
                "type":"recording-stopped",
                "path":summary.path,
                "videoPackets":summary.video_packets,
                "audioPackets":summary.audio_packets,
            })]),
            Ok(None) => Ok(vec![response(command.id, "recording-not-active")]),
            Err(message) => Err(error(Some(&command.id), "recording-failed", message)),
        }
    }

    fn toggle_microphone(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        let capture = self.microphone_capture.as_ref().ok_or_else(|| {
            error(
                Some(&command.id),
                "microphone-unavailable",
                "Microphone was not armed when this WebRTC session started",
            )
        })?;
        self.microphone_enabled = !self.microphone_enabled;
        capture.set_enabled(self.microphone_enabled);
        let message = if self.microphone_enabled {
            "Microphone is streaming"
        } else {
            "Microphone is muted"
        };
        let _ = self.events.send(event(
            "microphone-state",
            json!({
                "state":if self.microphone_enabled { "ready" } else { "disabled" },
                "enabled":self.microphone_enabled,
                "message":message
            }),
        ));
        Ok(vec![json!({
            "id":command.id,
            "type":"microphone-state",
            "enabled":self.microphone_enabled,
        })])
    }

    fn anti_afk_pulse(&self, command: Command) -> Result<Vec<Value>, Value> {
        let state = lock_lifecycle(&self.lifecycle).state;
        if state != State::Connected {
            return Err(invalid_state(
                &command.id,
                "anti-afk-pulse",
                state,
                "Connected with an initialized input channel",
            ));
        }
        let send = |input| {
            let bytes = captured_input_packet(input, 0);
            if let Some(transport) = self.nvst_transport.as_ref() {
                transport.send_input(bytes, false)
            } else if let Some(transport) = self.transport.as_ref() {
                transport.send_input(bytes, false)
            } else {
                Err(opennow_streamer_transport::TransportError::Closed)
            }
        };
        send(CapturedInput::Key {
            virtual_key: 0x7c,
            modifiers: 0,
            pressed: true,
        })
        .and_then(|_| {
            send(CapturedInput::Key {
                virtual_key: 0x7c,
                modifiers: 0,
                pressed: false,
            })
        })
        .map_err(|transport_error| {
            error(
                Some(&command.id),
                transport_error.code(),
                transport_error.to_string(),
            )
        })?;
        Ok(vec![response(command.id, "ok")])
    }

    fn stop_recording_inner(&mut self) -> Result<Option<RecordingSummary>, String> {
        let Some(worker) = self.recording_worker.take() else {
            return Ok(None);
        };
        if let Some(session) = self.media_session.as_ref() {
            session.control().unsubscribe_recording();
        }
        worker
            .join()
            .map_err(|_| "native recording worker panicked".to_owned())?
            .map(Some)
    }
}

impl Drop for Engine {
    fn drop(&mut self) {
        self.stop("process closed");
    }
}

fn partial_reliable_threshold(sdp: &str) -> Option<u16> {
    sdp.lines().find_map(|line| {
        line.trim()
            .strip_prefix("a=ri.partialReliableThresholdMs:")
            .and_then(|value| value.trim().parse().ok())
    })
}

fn parse_context(context: Option<Value>, id: &str) -> Result<SessionContext, Value> {
    let context = context.ok_or_else(|| {
        error(
            Some(id),
            "missing-context",
            "Command requires session context",
        )
    })?;
    serde_json::from_value(context).map_err(|context_error| {
        error(
            Some(id),
            "invalid-context",
            format!("Invalid session context: {context_error}"),
        )
    })
}

fn validate_context(context: &SessionContext, id: &str) -> Result<(), Value> {
    if context.session.session_id.trim().is_empty() {
        return Err(error(
            Some(id),
            "invalid-context",
            "Session context requires a non-empty sessionId",
        ));
    }
    if context.session.server_ip.trim().is_empty() {
        return Err(error(
            Some(id),
            "invalid-context",
            "Session context requires a non-empty serverIp endpoint",
        ));
    }
    if !context.settings.is_object() || !context.shortcuts.is_object() {
        return Err(error(
            Some(id),
            "invalid-context",
            "Session context settings and shortcuts must be objects",
        ));
    }
    if context
        .session
        .ice_servers
        .iter()
        .any(|server| server.urls.is_empty() || server.urls.iter().any(|url| url.trim().is_empty()))
    {
        return Err(error(
            Some(id),
            "invalid-context",
            "Every ICE server requires at least one non-empty URL",
        ));
    }
    if let Some(endpoint) = &context.session.media_connection_info {
        if endpoint.ip.trim().is_empty() || endpoint.port == 0 || endpoint.port > u16::MAX.into() {
            return Err(error(
                Some(id),
                "invalid-context",
                "mediaConnectionInfo requires a hostname and a port in 1..=65535",
            ));
        }
    }
    if context
        .session
        .connection_info
        .as_ref()
        .is_some_and(|connections| {
            connections.iter().any(|connection| {
                connection.port == 0
                    || connection.port > u16::MAX.into()
                    || connection
                        .ip
                        .as_ref()
                        .is_some_and(|ip| ip.trim().is_empty())
            })
        })
    {
        return Err(error(
            Some(id),
            "invalid-context",
            "connectionInfo requires ports in 1..=65535 and non-empty hostnames when present",
        ));
    }
    serde_json::to_value(context).map_err(|context_error| {
        error(
            Some(id),
            "invalid-context",
            format!("Session context is not serializable: {context_error}"),
        )
    })?;
    Ok(())
}

fn invalid_state(id: &str, command: &str, state: State, required: &str) -> Value {
    error(
        Some(id),
        "invalid-state",
        format!("Cannot apply {command} while lifecycle is {state:?}; required state: {required}"),
    )
}

fn lock_lifecycle(lifecycle: &Mutex<Lifecycle>) -> MutexGuard<'_, Lifecycle> {
    lifecycle
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn forward_transport_event(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    transport_event: TransportEvent,
) {
    {
        let mut lifecycle = lock_lifecycle(lifecycle);
        if lifecycle.generation != generation {
            return;
        }
        match &transport_event {
            TransportEvent::Connected => lifecycle.state = State::Connected,
            TransportEvent::Disconnected(_) => {
                lifecycle.context = None;
                lifecycle.state = State::Idle;
            }
            _ => {}
        }
    }
    let value = match transport_event {
        TransportEvent::Connected => event(
            "status",
            json!({ "status": "streaming", "message": "ICE, DTLS-SRTP, and RTP connected" }),
        ),
        TransportEvent::Disconnected(message) => {
            event("status", json!({ "status": "stopped", "message": message }))
        }
        TransportEvent::InputReady(protocol_version) => event(
            "input-ready",
            json!({ "protocolVersion": protocol_version }),
        ),
        TransportEvent::InputUnavailable(reason) => {
            event("input-unavailable", json!({ "reason": reason }))
        }
        TransportEvent::MicrophoneReady => event(
            "microphone-state",
            json!({"state":"ready","enabled":true,"message":"Microphone is streaming"}),
        ),
        TransportEvent::MicrophoneUnavailable(reason) => event(
            "microphone-state",
            json!({"state":"unavailable","enabled":false,"message":reason}),
        ),
        TransportEvent::Log(message) => {
            event("log", json!({ "level": "warn", "message": message }))
        }
    };
    let _ = output.send(value);
}

fn forward_shortcut_action(
    output: &Sender<Value>,
    runtime: Option<&MediaRuntime>,
    action: StreamShortcutAction,
) {
    let control = match action {
        StreamShortcutAction::ToggleStats => None,
        StreamShortcutAction::ToggleFullscreen => None,
        StreamShortcutAction::TogglePointerLock => Some(MediaRuntimeControl::PointerLock),
        _ => None,
    };
    if let Some(control) = control {
        let result = runtime
            .ok_or_else(|| "native media runtime is unavailable".to_owned())
            .and_then(|runtime| runtime.control(control));
        if let Err(message) = result {
            let _ = output.send(event(
                "log",
                json!({"level":"warn", "message":format!("Shortcut {} failed: {message}", action.protocol_name())}),
            ));
        }
        return;
    }
    let _ = output.send(event(
        "shortcut-action",
        json!({"action":action.protocol_name(), "source":"keyboard"}),
    ));
}

struct NvstSessionEventResources<R> {
    nvst_events: Receiver<NvstReceiveEvent>,
    media_feedback: Option<Receiver<MediaFeedback>>,
    captured_input: Option<Arc<CapturedInputQueue>>,
    shortcut_runtime: Option<MediaRuntime>,
    transport: R,
}

fn forward_nvst_session_events<R: NvstSessionResources>(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    event_resources: NvstSessionEventResources<R>,
) {
    let NvstSessionEventResources {
        nvst_events,
        media_feedback,
        captured_input,
        shortcut_runtime,
        transport: resources,
    } = event_resources;
    let mut feedback_state = NvstMediaFeedbackState::new(false);
    loop {
        if let Some(feedback) = media_feedback.as_ref() {
            while let Ok(feedback) = feedback.try_recv() {
                forward_nvst_media_feedback(
                    output,
                    lifecycle,
                    generation,
                    &resources,
                    feedback,
                    &mut feedback_state,
                );
            }
        }
        if let Some(captured_input) = captured_input.as_ref() {
            if !feedback_state.input_available {
                captured_input.clear();
            } else if captured_input.take_overflowed() {
                let _ = emit_nvst_terminal(
                    output,
                    lifecycle,
                    generation,
                    &resources,
                    "native-input-capture-overflow",
                    "Native input capture queue overflowed; stopping to prevent stuck input"
                        .to_owned(),
                );
                return;
            } else {
                // Preserve high-polling-rate RawInput/SDL samples rather than
                // turning several reports into one uneven movement burst.
                for _ in 0..32 {
                    let Some(input) = captured_input.take_sample() else {
                        break;
                    };
                    if matches!(input.input, CapturedInput::Guide) {
                        let _ = output.send(event("overlay-request", json!({"source":"gamepad"})));
                        continue;
                    }
                    if matches!(input.input, CapturedInput::Screenshot) {
                        let _ =
                            output.send(event("screenshot-request", json!({"source":"keyboard"})));
                        continue;
                    }
                    if matches!(input.input, CapturedInput::RecordingToggle) {
                        let _ = output.send(event(
                            "recording-toggle-request",
                            json!({"source":"keyboard"}),
                        ));
                        continue;
                    }
                    if let CapturedInput::Shortcut(action) = &input.input {
                        forward_shortcut_action(output, shortcut_runtime.as_ref(), *action);
                        continue;
                    }
                    if let Err(error) =
                        forward_nvst_captured_sample(&resources, input, &feedback_state)
                    {
                        let _ = emit_nvst_terminal(
                            output,
                            lifecycle,
                            generation,
                            &resources,
                            "native-input-capture-failed",
                            format!("Native window input capture failed: {error}"),
                        );
                        return;
                    }
                }
            }
        }
        match nvst_events.recv_timeout(NATIVE_INPUT_POLL_INTERVAL) {
            Ok(nvst_event) => {
                match &nvst_event {
                    NvstReceiveEvent::InputReady(_) => feedback_state.input_available = true,
                    NvstReceiveEvent::InputUnavailable(_) => {
                        feedback_state.input_available = false;
                    }
                    _ => {}
                }
                let terminal = forward_nvst_event(
                    output,
                    lifecycle,
                    generation,
                    &resources,
                    &mut feedback_state.recovery_attempts,
                    nvst_event,
                );
                if terminal {
                    return;
                }
            }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                let _ = emit_nvst_terminal(
                    output,
                    lifecycle,
                    generation,
                    &resources,
                    "nvst-event-channel-closed",
                    "NVST receiver event channel closed unexpectedly".to_owned(),
                );
                return;
            }
        }
    }
}

fn forward_nvst_event<R: NvstSessionResources>(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    resources: &R,
    recovery_attempts: &mut usize,
    nvst_event: NvstReceiveEvent,
) -> bool {
    if lock_lifecycle(lifecycle).generation != generation {
        return true;
    }

    match nvst_event {
        NvstReceiveEvent::RecoveryNeeded(NvstRecovery::PacketGap {
            first_missing_index,
            last_missing_index,
        }) => {
            // Packet loss is expected on the UDP media leg. The reorder buffer
            // has already skipped the unrecoverable range and reset the frame
            // assembler, so request a clean decoder reference without spending
            // the terminal transport-recovery budget. Several gaps can arrive
            // before the requested keyframe reaches us at high bitrates.
            resources.request_keyframe();
            let _ = output.send(event(
                "log",
                json!({
                    "level": "warn",
                    "message": format!(
                        "Recovering NVST packet gap with a fresh keyframe: {first_missing_index}..={last_missing_index}"
                    )
                }),
            ));
            false
        }
        NvstReceiveEvent::RecoveryNeeded(recovery) => attempt_nvst_recovery(
            output,
            lifecycle,
            generation,
            resources,
            recovery_attempts,
            format!("{recovery:?}"),
        ),
        NvstReceiveEvent::Lifecycle(NvstReceiverState::RecoveryRequired) => attempt_nvst_recovery(
            output,
            lifecycle,
            generation,
            resources,
            recovery_attempts,
            "authenticated media timeout".to_owned(),
        ),
        NvstReceiveEvent::Lifecycle(NvstReceiverState::Stopped) => emit_nvst_terminal(
            output,
            lifecycle,
            generation,
            resources,
            "nvst-transport-stopped",
            "NVST receiver stopped unexpectedly".to_owned(),
        ),
        NvstReceiveEvent::Dropped(NvstDropReason::MediaConsumerBackpressured) => {
            // A bounded decode queue protects latency. A momentary full queue
            // means this access unit is stale, not that the network session is
            // dead. Keep receiving and ask for a clean decoder reference.
            resources.request_keyframe();
            let _ = output.send(event(
                "log",
                json!({
                    "level": "warn",
                    "message": "Dropped a backpressured NVST video frame and requested a fresh keyframe"
                }),
            ));
            false
        }
        NvstReceiveEvent::Dropped(NvstDropReason::MediaConsumerClosed) => emit_nvst_terminal(
            output,
            lifecycle,
            generation,
            resources,
            "media-consumer-closed",
            "NVST receiver stopped because the decoded media path closed".to_owned(),
        ),
        NvstReceiveEvent::Lifecycle(NvstReceiverState::Running) => {
            lock_lifecycle(lifecycle).state = State::Connected;
            let _ = output.send(event(
                "status",
                json!({ "status": "streaming", "message": "NVST SRTP video receiver is running" }),
            ));
            false
        }
        NvstReceiveEvent::Lifecycle(NvstReceiverState::Paused) => {
            let _ = output.send(event(
                "status",
                json!({ "status": "paused", "message": "NVST SRTP video receiver is paused" }),
            ));
            false
        }
        NvstReceiveEvent::TransportReady(phase) => {
            let _ = output.send(event("nvst-transport-ready", json!({ "phase": phase })));
            false
        }
        NvstReceiveEvent::InputReady(protocol_version) => {
            let _ = output.send(event(
                "input-ready",
                json!({ "protocolVersion": protocol_version }),
            ));
            false
        }
        NvstReceiveEvent::InputUnavailable(reason) => {
            let _ = output.send(event("input-unavailable", json!({ "reason": reason })));
            false
        }
        NvstReceiveEvent::Cursor(bytes) => {
            resources.apply_cursor(bytes);
            false
        }
        NvstReceiveEvent::Dropped(
            NvstDropReason::AwaitingStartOfFrame
            | NvstDropReason::StaleRtpPacket { .. }
            | NvstDropReason::DuplicateRtpPacket { .. },
        ) => {
            // These are expected while a packet-gap recovery waits for the
            // requested keyframe. Logging every following datagram can flood
            // stdout and steal time from the receive/decode threads.
            false
        }
        NvstReceiveEvent::Dropped(reason) => {
            let _ = output.send(event(
                "log",
                json!({ "level": "debug", "message": format!("Dropped NVST datagram: {reason:?}") }),
            ));
            false
        }
        NvstReceiveEvent::Frame(_) => false,
    }
}

fn attempt_nvst_recovery<R: NvstSessionResources>(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    resources: &R,
    recovery_attempts: &mut usize,
    reason: String,
) -> bool {
    if *recovery_attempts >= NVST_RECOVERY_ATTEMPT_LIMIT {
        return emit_nvst_terminal(
            output,
            lifecycle,
            generation,
            resources,
            "nvst-recovery-exhausted",
            format!("NVST recovery failed after one attempt: {reason}"),
        );
    }

    *recovery_attempts += 1;
    resources.request_keyframe();
    if let Err(recovery_error) = resources.recover() {
        return emit_nvst_terminal(
            output,
            lifecycle,
            generation,
            resources,
            "nvst-recovery-failed",
            format!("NVST recovery could not be started: {recovery_error}"),
        );
    }
    let _ = output.send(event(
        "log",
        json!({
            "level": "warn",
            "message": format!("Attempting bounded NVST recovery with a fresh keyframe: {reason}")
        }),
    ));
    false
}

fn emit_nvst_terminal<R: NvstSessionResources>(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    resources: &R,
    code: &str,
    message: String,
) -> bool {
    {
        let mut lifecycle = lock_lifecycle(lifecycle);
        if lifecycle.generation != generation {
            return true;
        }
        lifecycle.context = None;
        lifecycle.state = State::Idle;
    }
    resources.stop();
    let _ = output.send(event("error", json!({ "code": code, "message": &message })));
    let _ = output.send(event(
        "status",
        json!({ "status": "stopped", "message": message }),
    ));
    true
}

struct NvstMediaFeedbackState {
    drop_reports: HashMap<&'static str, QueueDropReport>,
    recovery_attempts: usize,
    input_origin: Instant,
    input_available: bool,
    telemetry_window_started: Instant,
    telemetry_frames: u64,
    telemetry_bytes: u64,
    peak_bitrate_mbps: f64,
}

impl NvstMediaFeedbackState {
    fn new(input_available: bool) -> Self {
        Self {
            drop_reports: HashMap::new(),
            recovery_attempts: 0,
            input_origin: Instant::now(),
            input_available,
            telemetry_window_started: Instant::now(),
            telemetry_frames: 0,
            telemetry_bytes: 0,
            peak_bitrate_mbps: 0.0,
        }
    }
}

fn forward_nvst_media_feedback<R: NvstSessionResources>(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    resources: &R,
    feedback: MediaFeedback,
    state: &mut NvstMediaFeedbackState,
) {
    if lock_lifecycle(lifecycle).generation != generation {
        return;
    }
    match feedback {
        MediaFeedback::VideoFrameAccepted {
            bytes, keyframe, ..
        } => {
            resources.acknowledge_video_frame(bytes);
            if keyframe {
                state.recovery_attempts = 0;
            }
            state.telemetry_frames = state.telemetry_frames.saturating_add(1);
            state.telemetry_bytes = state.telemetry_bytes.saturating_add(u64::from(bytes));
            let elapsed = state.telemetry_window_started.elapsed();
            if elapsed >= Duration::from_secs(1) {
                let elapsed_seconds = elapsed.as_secs_f64();
                let frames_per_second = state.telemetry_frames as f64 / elapsed_seconds;
                let bitrate_mbps =
                    state.telemetry_bytes as f64 * 8.0 / elapsed_seconds / 1_000_000.0;
                state.peak_bitrate_mbps = state.peak_bitrate_mbps.max(bitrate_mbps);
                let _ = output.send(event(
                    "telemetry",
                    json!({
                        "framesPerSecond": frames_per_second,
                        "bitrateMbps": bitrate_mbps,
                        "peakBitrateMbps": state.peak_bitrate_mbps,
                    }),
                ));
                state.telemetry_window_started = Instant::now();
                state.telemetry_frames = 0;
                state.telemetry_bytes = 0;
            }
        }
        MediaFeedback::PlaybackStarted { backend } => {
            let _ = output.send(event(
                "status",
                json!({
                    "event": "first-frame",
                    "backend": backend,
                    "status": "streaming",
                    "message": format!("{backend} presented the first NVST video frame")
                }),
            ));
        }
        MediaFeedback::BackendFallback { from, to, reason } => {
            let _ = output.send(event(
                "log",
                json!({
                    "event": "backend-fallback",
                    "fromBackend": from,
                    "toBackend": to,
                    "reason": reason,
                    "level": "warn",
                    "message": format!("{from} startup failed; using {to}: {reason}")
                }),
            ));
        }
        MediaFeedback::RequestKeyframe { reason, .. } => {
            resources.request_keyframe();
            let _ = output.send(event(
                "log",
                json!({
                    "event": "keyframe-request",
                    "reason": reason,
                    "level": "info",
                    "message": format!("Requested an NVST video keyframe: {reason}")
                }),
            ));
        }
        MediaFeedback::DecoderError { codec, message } => {
            let _ = output.send(event(
                "error",
                json!({
                    "event": "decoder-error",
                    "codec": codec,
                    "code": "media-decode-error",
                    "message": format!("{codec} decoder error: {message}")
                }),
            ));
        }
        MediaFeedback::OutputError { message } => {
            let _ = output.send(event(
                "error",
                json!({ "event": "output-error", "code": "media-output-error", "message": message }),
            ));
        }
        MediaFeedback::DeviceLost {
            subsystem,
            recovered,
            message,
        } => {
            let _ = output.send(event(
                "log",
                json!({
                    "event": "device-state",
                    "subsystem": subsystem,
                    "recovered": recovered,
                    "level": if recovered { "info" } else { "warn" },
                    "message": message.unwrap_or_else(|| format!(
                        "{subsystem} device {}",
                        if recovered { "recovered" } else { "was lost" }
                    ))
                }),
            ));
        }
        MediaFeedback::QueueDropped { media, count } => {
            if let Some(dropped) = record_queue_drop(&mut state.drop_reports, media, count) {
                let _ = output.send(event(
                    "log",
                    json!({
                        "event": "queue-dropped",
                        "media": media,
                        "count": dropped,
                        "level": "debug",
                        "message": format!("Low-latency {media} queues dropped {dropped} stale samples/frames")
                    }),
                ));
            }
        }
    }
}

#[cfg(test)]
fn forward_nvst_captured_input<R: NvstSessionResources>(
    resources: &R,
    input: CapturedInput,
    state: &NvstMediaFeedbackState,
) -> Result<(), String> {
    if matches!(
        input,
        CapturedInput::Guide
            | CapturedInput::Screenshot
            | CapturedInput::RecordingToggle
            | CapturedInput::Shortcut(_)
    ) {
        return Ok(());
    }
    let timestamp_us = u64::try_from(state.input_origin.elapsed().as_micros()).unwrap_or(u64::MAX);
    resources.send_captured_input(captured_input_packet(input, timestamp_us))
}

fn forward_nvst_captured_sample<R: NvstSessionResources>(
    resources: &R,
    sample: CapturedInputSample,
    state: &NvstMediaFeedbackState,
) -> Result<(), String> {
    if matches!(
        sample.input,
        CapturedInput::Guide
            | CapturedInput::Screenshot
            | CapturedInput::RecordingToggle
            | CapturedInput::Shortcut(_)
    ) {
        return Ok(());
    }
    // Bifrost timestamps native input at OS capture, before aggregation and
    // SCTP sending. Keeping that time prevents a delayed queue drain from
    // making a group of older reports look newly generated.
    let captured = sample
        .captured_at
        .checked_duration_since(state.input_origin)
        .unwrap_or_default();
    let timestamp_us = u64::try_from(captured.as_micros()).unwrap_or(u64::MAX);
    resources.send_captured_input(captured_input_packet(sample.input, timestamp_us))
}

fn captured_input_packet(input: CapturedInput, timestamp_us: u64) -> Vec<u8> {
    match input {
        CapturedInput::Key {
            virtual_key,
            modifiers,
            pressed,
        } => {
            let mut packet = Vec::with_capacity(18);
            packet.extend_from_slice(&(if pressed { 3_u32 } else { 4_u32 }).to_le_bytes());
            packet.extend_from_slice(&virtual_key.to_be_bytes());
            packet.extend_from_slice(&modifiers.to_be_bytes());
            packet.extend_from_slice(&0_u16.to_be_bytes());
            packet.extend_from_slice(&timestamp_us.to_be_bytes());
            packet
        }
        CapturedInput::MouseMove { delta_x, delta_y } => {
            let (delta_x, delta_y) = tune_relative_mouse(delta_x, delta_y, input_tuning());
            let mut packet = Vec::with_capacity(22);
            packet.extend_from_slice(&7_u32.to_le_bytes());
            packet.extend_from_slice(&delta_x.to_be_bytes());
            packet.extend_from_slice(&delta_y.to_be_bytes());
            packet.extend_from_slice(&[0; 6]);
            packet.extend_from_slice(&timestamp_us.to_be_bytes());
            packet
        }
        CapturedInput::MouseAbsolute {
            x,
            y,
            width,
            height,
        } => {
            let mut packet = Vec::with_capacity(26);
            packet.extend_from_slice(&5_u32.to_le_bytes());
            packet.extend_from_slice(&x.to_be_bytes());
            packet.extend_from_slice(&y.to_be_bytes());
            packet.extend_from_slice(&0_u16.to_be_bytes());
            packet.extend_from_slice(&width.to_be_bytes());
            packet.extend_from_slice(&height.to_be_bytes());
            packet.extend_from_slice(&0_u32.to_be_bytes());
            packet.extend_from_slice(&timestamp_us.to_be_bytes());
            packet
        }
        CapturedInput::MouseButton { button, pressed } => {
            let mut packet = Vec::with_capacity(18);
            packet.extend_from_slice(&(if pressed { 8_u32 } else { 9_u32 }).to_le_bytes());
            packet.extend_from_slice(&[button, 0]);
            packet.extend_from_slice(&[0; 4]);
            packet.extend_from_slice(&timestamp_us.to_be_bytes());
            packet
        }
        CapturedInput::MouseWheel { delta_x, delta_y } => {
            let mut packet = Vec::with_capacity(22);
            packet.extend_from_slice(&10_u32.to_le_bytes());
            packet.extend_from_slice(&delta_x.to_be_bytes());
            packet.extend_from_slice(&delta_y.to_be_bytes());
            packet.extend_from_slice(&[0; 6]);
            packet.extend_from_slice(&timestamp_us.to_be_bytes());
            packet
        }
        CapturedInput::Gamepad {
            controller_id,
            bitmap,
            buttons,
            left_trigger,
            right_trigger,
            left_stick_x,
            left_stick_y,
            right_stick_x,
            right_stick_y,
        } => {
            let mut packet = Vec::with_capacity(38);
            packet.extend_from_slice(&12_u32.to_le_bytes());
            packet.extend_from_slice(&26_u16.to_le_bytes());
            packet.extend_from_slice(&u16::from(controller_id & 0x03).to_le_bytes());
            packet.extend_from_slice(&bitmap.to_le_bytes());
            packet.extend_from_slice(&20_u16.to_le_bytes());
            packet.extend_from_slice(&buttons.to_le_bytes());
            packet.extend_from_slice(
                &(u16::from(left_trigger) | (u16::from(right_trigger) << 8)).to_le_bytes(),
            );
            packet.extend_from_slice(&left_stick_x.to_le_bytes());
            packet.extend_from_slice(&left_stick_y.to_le_bytes());
            packet.extend_from_slice(&right_stick_x.to_le_bytes());
            packet.extend_from_slice(&right_stick_y.to_le_bytes());
            packet.extend_from_slice(&0_u16.to_le_bytes());
            packet.extend_from_slice(&85_u16.to_le_bytes());
            packet.extend_from_slice(&0_u16.to_le_bytes());
            packet.extend_from_slice(&timestamp_us.to_le_bytes());
            packet
        }
        CapturedInput::Guide
        | CapturedInput::Screenshot
        | CapturedInput::RecordingToggle
        | CapturedInput::Shortcut(_) => Vec::new(),
    }
}

#[derive(Debug, Clone, Copy)]
struct InputTuning {
    sensitivity: f64,
    acceleration_percent: f64,
}

fn input_tuning() -> InputTuning {
    static TUNING: OnceLock<InputTuning> = OnceLock::new();
    *TUNING.get_or_init(|| InputTuning {
        sensitivity: std::env::var("OPENNOW_MOUSE_SENSITIVITY")
            .ok()
            .and_then(|value| value.parse::<f64>().ok())
            .unwrap_or(1.0)
            .clamp(0.1, 3.0),
        acceleration_percent: std::env::var("OPENNOW_MOUSE_ACCELERATION")
            .ok()
            .and_then(|value| value.parse::<f64>().ok())
            .unwrap_or(1.0)
            .clamp(1.0, 150.0),
    })
}

fn tune_relative_mouse(delta_x: i16, delta_y: i16, tuning: InputTuning) -> (i16, i16) {
    let mut x = f64::from(delta_x) * tuning.sensitivity;
    let mut y = f64::from(delta_y) * tuning.sensitivity;
    if tuning.acceleration_percent > 1.0 {
        let speed = x.hypot(y);
        let strength = (tuning.acceleration_percent - 1.0) / 149.0;
        // Match the legacy client curve: preserve low-speed precision and cap
        // the maximum turn boost at 60% for the 150% setting.
        let factor = 1.0 + (0.6 * strength).min(speed / 50.0 * strength);
        x *= factor;
        y *= factor;
    }
    let clamp = |value: f64| {
        value
            .round()
            .clamp(f64::from(i16::MIN), f64::from(i16::MAX)) as i16
    };
    (clamp(x), clamp(y))
}

fn media_stream_config(context: &SessionContext) -> MediaStreamConfig {
    let codec_name = context
        .session
        .extra
        .get("negotiatedStreamProfile")
        .and_then(|profile| profile.get("codec"))
        .and_then(Value::as_str)
        .or_else(|| context.settings.get("codec").and_then(Value::as_str))
        .unwrap_or("H264");
    let codec = match codec_name.trim().to_ascii_uppercase().as_str() {
        "H265" | "HEVC" => MediaVideoCodec::H265,
        "AV1" => MediaVideoCodec::Av1,
        _ => MediaVideoCodec::H264,
    };
    let color_quality_name = context
        .session
        .extra
        .get("negotiatedStreamProfile")
        .and_then(|profile| profile.get("colorQuality"))
        .and_then(Value::as_str)
        .or_else(|| context.settings.get("colorQuality").and_then(Value::as_str))
        .unwrap_or("8bit_420");
    let color_quality = match color_quality_name.trim().to_ascii_lowercase().as_str() {
        "8bit_444" if codec != MediaVideoCodec::H264 => MediaColorQuality::EightBit444,
        "10bit_420" if codec != MediaVideoCodec::H264 => MediaColorQuality::TenBit420,
        "10bit_444" if codec != MediaVideoCodec::H264 => MediaColorQuality::TenBit444,
        _ => MediaColorQuality::EightBit420,
    };
    let resolution = context
        .session
        .extra
        .get("negotiatedStreamProfile")
        .and_then(|profile| profile.get("resolution"))
        .and_then(Value::as_str)
        .or_else(|| context.settings.get("resolution").and_then(Value::as_str))
        .and_then(|value| {
            let lowercase = value.to_ascii_lowercase();
            let (width, height) = lowercase.split_once('x')?;
            Some((width.parse::<u32>().ok()?, height.parse::<u32>().ok()?))
        })
        .filter(|(width, height)| (48..=4096).contains(width) && (48..=2304).contains(height))
        .unwrap_or((1920, 1080));
    let fps = context
        .session
        .extra
        .get("negotiatedStreamProfile")
        .and_then(|profile| profile.get("fps"))
        .or_else(|| context.settings.get("fps"))
        .and_then(Value::as_u64)
        .and_then(|value| u32::try_from(value).ok())
        .unwrap_or(60)
        .clamp(1, 240);
    let bitrate_mbps = context
        .settings
        .get("maxBitrateMbps")
        .and_then(Value::as_u64)
        .and_then(|value| u32::try_from(value).ok())
        .unwrap_or(75);
    let requested_cloud_gsync = match context
        .settings
        .get("nativeCloudGsyncMode")
        .and_then(Value::as_str)
        .unwrap_or("auto")
    {
        "disabled" => false,
        "forced" => true,
        _ => context
            .settings
            .get("enableCloudGsync")
            .and_then(Value::as_bool)
            .unwrap_or(false),
    };
    // `enableCloudGsync` is the resolved client request, but CloudMatch may
    // explicitly reject it in the finalized profile. Never switch Linux into
    // unthrottled VRR pacing when the server negotiated the feature off.
    let negotiated_cloud_gsync = context
        .session
        .extra
        .get("negotiatedStreamProfile")
        .and_then(|profile| profile.get("enableCloudGsync"))
        .and_then(Value::as_bool);
    let cloud_gsync = requested_cloud_gsync && negotiated_cloud_gsync.unwrap_or(true);
    MediaStreamConfig {
        codec,
        color_quality,
        width: resolution.0,
        height: resolution.1,
        fps,
        bitrate_bps: bitrate_mbps.saturating_mul(1_000_000).max(1),
        cloud_gsync,
        shortcuts: StreamShortcutBindings::from_json(&context.shortcuts),
    }
}

fn consume_encoded_media(
    output: &Sender<Value>,
    receiver: Receiver<EncodedMediaFrame>,
    sink: MediaSink,
) {
    while let Ok(frame) = receiver.recv() {
        let codec = if frame.codec.eq_ignore_ascii_case("h264") {
            MediaCodec::H264
        } else if frame.codec.eq_ignore_ascii_case("h265")
            || frame.codec.eq_ignore_ascii_case("hevc")
        {
            MediaCodec::H265
        } else if frame.codec.eq_ignore_ascii_case("av1") {
            MediaCodec::Av1
        } else if frame.codec.eq_ignore_ascii_case("opus") {
            MediaCodec::Opus {
                channels: frame.channels.unwrap_or(2).clamp(1, 2),
            }
        } else {
            MediaCodec::Unsupported(frame.codec)
        };
        match sink.push(EncodedFrame {
            mid: frame.mid,
            codec,
            data: frame.payload,
            timestamp: frame.rtp_timestamp,
            clock_rate_hz: frame.clock_rate_hz,
            keyframe: frame.keyframe,
            contiguous: frame.contiguous,
        }) {
            PushOutcome::Unsupported => {
                let _ = output.send(event(
                    "log",
                    json!({
                        "level": "warn",
                        "message": "Dropping a frame for a codec not built into native streamer v2"
                    }),
                ));
            }
            PushOutcome::Closed => break,
            PushOutcome::Queued | PushOutcome::DroppedOldest | PushOutcome::Paused => {}
        }
    }
}

struct WebrtcSessionResources {
    transport_events: Receiver<TransportEvent>,
    media_feedback: Option<Receiver<MediaFeedback>>,
    captured_input: Option<Arc<CapturedInputQueue>>,
    microphone_packets: Option<Arc<EncodedMicrophoneQueue>>,
    shortcut_runtime: Option<MediaRuntime>,
    transport: TransportControl,
}

fn forward_session_events(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    resources: WebrtcSessionResources,
) {
    let WebrtcSessionResources {
        transport_events,
        media_feedback,
        captured_input,
        microphone_packets,
        shortcut_runtime,
        transport,
    } = resources;
    let mut drop_reports = HashMap::new();
    let input_origin = Instant::now();
    let mut input_available = false;
    let mut microphone_available = false;
    loop {
        if let Some(feedback) = media_feedback.as_ref() {
            while let Ok(feedback) = feedback.try_recv() {
                forward_media_feedback(
                    output,
                    lifecycle,
                    generation,
                    &transport,
                    feedback,
                    &mut drop_reports,
                );
            }
        }
        if let Some(captured_input) = captured_input.as_ref() {
            if !input_available {
                captured_input.clear();
            } else if captured_input.take_overflowed() {
                let _ = output.send(event(
                    "error",
                    json!({
                        "code":"native-input-capture-overflow",
                        "message":"Native input capture queue overflowed; input was paused to prevent stuck controls"
                    }),
                ));
                input_available = false;
            } else {
                for _ in 0..32 {
                    let Some(sample) = captured_input.take_sample() else {
                        break;
                    };
                    if matches!(sample.input, CapturedInput::Guide) {
                        let _ = output.send(event("overlay-request", json!({"source":"gamepad"})));
                        continue;
                    }
                    if matches!(sample.input, CapturedInput::Screenshot) {
                        let _ =
                            output.send(event("screenshot-request", json!({"source":"keyboard"})));
                        continue;
                    }
                    if matches!(sample.input, CapturedInput::RecordingToggle) {
                        let _ = output.send(event(
                            "recording-toggle-request",
                            json!({"source":"keyboard"}),
                        ));
                        continue;
                    }
                    if let CapturedInput::Shortcut(action) = &sample.input {
                        forward_shortcut_action(output, shortcut_runtime.as_ref(), *action);
                        continue;
                    }
                    let captured = sample
                        .captured_at
                        .checked_duration_since(input_origin)
                        .unwrap_or_default();
                    let timestamp_us = u64::try_from(captured.as_micros()).unwrap_or(u64::MAX);
                    if transport
                        .send_input(captured_input_packet(sample.input, timestamp_us), false)
                        .is_err()
                    {
                        input_available = false;
                        break;
                    }
                }
            }
        }
        if let Some(microphone_packets) = microphone_packets.as_ref() {
            if !microphone_available {
                microphone_packets.clear();
            } else {
                let dropped = microphone_packets.take_dropped();
                if dropped > 0 {
                    let _ = output.send(event(
                        "log",
                        json!({
                            "level":"warn",
                            "message":format!("Dropped {dropped} microphone packets under load")
                        }),
                    ));
                }
                for _ in 0..4 {
                    let Some(packet) = microphone_packets.take() else {
                        break;
                    };
                    let _ = transport.send_microphone(EncodedMicrophoneFrame {
                        payload: packet.payload,
                        captured_at_us: packet.captured_at_us,
                        audio_level_db: packet.audio_level_db,
                        voice_activity: packet.voice_activity,
                    });
                }
            }
        }
        match transport_events.recv_timeout(Duration::from_millis(5)) {
            Ok(transport_event) => {
                match &transport_event {
                    TransportEvent::InputReady(_) => input_available = true,
                    TransportEvent::InputUnavailable(_) | TransportEvent::Disconnected(_) => {
                        input_available = false
                    }
                    TransportEvent::MicrophoneReady => microphone_available = true,
                    TransportEvent::MicrophoneUnavailable(_) => microphone_available = false,
                    _ => {}
                }
                let disconnected = matches!(transport_event, TransportEvent::Disconnected(_));
                forward_transport_event(output, lifecycle, generation, transport_event);
                if disconnected {
                    break;
                }
            }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {}
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => break,
        }
    }
}

fn forward_media_feedback(
    output: &Sender<Value>,
    lifecycle: &Mutex<Lifecycle>,
    generation: u64,
    transport: &TransportControl,
    feedback: MediaFeedback,
    drop_reports: &mut HashMap<&'static str, QueueDropReport>,
) {
    if lock_lifecycle(lifecycle).generation != generation {
        return;
    }
    match feedback {
        MediaFeedback::VideoFrameAccepted { .. } => {}
        MediaFeedback::PlaybackStarted { backend } => {
            let _ = output.send(event(
                "status",
                json!({
                    "event": "first-frame",
                    "backend": backend,
                    "status": "streaming",
                    "message": format!("{backend} presented the first video frame")
                }),
            ));
        }
        MediaFeedback::BackendFallback { from, to, reason } => {
            let _ = output.send(event(
                "log",
                json!({
                    "event": "backend-fallback",
                    "fromBackend": from,
                    "toBackend": to,
                    "reason": reason,
                    "level": "warn",
                    "message": format!("{from} startup failed; using {to}: {reason}")
                }),
            ));
        }
        MediaFeedback::RequestKeyframe { mid, reason } => {
            let request_result = transport.request_keyframe(mid);
            let _ = output.send(event(
                "log",
                json!({
                    "event": "keyframe-request",
                    "reason": reason,
                    "level": if request_result.is_ok() { "info" } else { "warn" },
                    "message": format!("Requested a video keyframe: {reason}")
                }),
            ));
        }
        MediaFeedback::DecoderError { codec, message } => {
            let _ = output.send(event(
                "error",
                json!({
                    "event": "decoder-error",
                    "codec": codec,
                    "code": "media-decode-error",
                    "message": format!("{codec} decoder error: {message}")
                }),
            ));
        }
        MediaFeedback::OutputError { message } => {
            let _ = output.send(event(
                "error",
                json!({ "event": "output-error", "code": "media-output-error", "message": message }),
            ));
        }
        MediaFeedback::DeviceLost {
            subsystem,
            recovered,
            message,
        } => {
            let _ = output.send(event(
                "log",
                json!({
                    "event": "device-state",
                    "subsystem": subsystem,
                    "recovered": recovered,
                    "level": if recovered { "info" } else { "warn" },
                    "message": message.unwrap_or_else(|| format!(
                        "{subsystem} device {}",
                        if recovered { "recovered" } else { "was lost" }
                    ))
                }),
            ));
        }
        MediaFeedback::QueueDropped { media, count } => {
            if let Some(dropped) = record_queue_drop(drop_reports, media, count) {
                let _ = output.send(event(
                    "log",
                    json!({
                        "event": "queue-dropped",
                        "media": media,
                        "count": dropped,
                        "level": "debug",
                        "message": format!(
                            "Low-latency {media} queues dropped {dropped} stale samples/frames"
                        )
                    }),
                ));
            }
        }
    }
}

struct QueueDropReport {
    dropped: usize,
    started: Instant,
}

fn record_queue_drop(
    reports: &mut HashMap<&'static str, QueueDropReport>,
    media: &'static str,
    count: usize,
) -> Option<usize> {
    let report = reports.entry(media).or_insert_with(|| QueueDropReport {
        dropped: 0,
        started: Instant::now(),
    });
    report.dropped = report.dropped.saturating_add(count);
    if report.started.elapsed() < Duration::from_secs(1) {
        return None;
    }
    let dropped = std::mem::take(&mut report.dropped);
    report.started = Instant::now();
    Some(dropped)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::UdpSocket;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::time::Instant;
    use str0m::media::{Direction, MediaKind};
    use str0m::{Candidate, RtcConfig};

    fn command(value: Value) -> Command {
        serde_json::from_value(value).expect("command")
    }

    fn synthetic_context(session_id: &str, ice_servers: Value) -> Value {
        json!({
            "session": {
                "sessionId": session_id,
                "serverIp": "127-0-0-1.synthetic.invalid",
                "iceServers": ice_servers,
                "mediaConnectionInfo": {
                    "ip": "127-0-0-1.media.synthetic.invalid",
                    "port": 18_784,
                    "usage": 17
                },
                "syntheticExtension": "preserved"
            },
            "settings": { "codec": "H264", "fps": 60 },
            "shortcuts": { "stopStream": "Ctrl+Shift+Q" },
            "syntheticContextExtension": true
        })
    }

    fn synthetic_offer() -> String {
        opennow_streamer_transport::install_crypto();
        let mut offerer = RtcConfig::new().build(Instant::now());
        offerer.add_local_candidate(
            Candidate::host("127.0.0.1:49152".parse().expect("candidate address"), "udp")
                .expect("local candidate"),
        );
        let mut change = offerer.sdp_api();
        change.add_media(MediaKind::Video, Direction::SendOnly, None, None, None);
        let (offer, _pending) = change.apply().expect("synthetic offer");
        offer.to_sdp_string()
    }

    fn lifecycle_state(engine: &Engine) -> State {
        lock_lifecycle(&engine.lifecycle).state
    }

    #[test]
    fn shell_shortcuts_emit_typed_actions() {
        let (sender, receiver) = std::sync::mpsc::channel();
        forward_shortcut_action(&sender, None, StreamShortcutAction::ToggleMicrophone);
        let action = receiver.recv().expect("shortcut action");
        assert_eq!(action["type"], "shortcut-action");
        assert_eq!(action["action"], "toggle-microphone");

        forward_shortcut_action(&sender, None, StreamShortcutAction::ToggleStats);
        let action = receiver.recv().expect("stats shortcut action");
        assert_eq!(action["type"], "shortcut-action");
        assert_eq!(action["action"], "toggle-stats");

        forward_shortcut_action(&sender, None, StreamShortcutAction::ToggleFullscreen);
        let action = receiver.recv().expect("fullscreen shortcut action");
        assert_eq!(action["type"], "shortcut-action");
        assert_eq!(action["action"], "toggle-fullscreen");
    }

    #[test]
    fn legacy_stats_toggle_routes_to_the_shell_without_native_rendering() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let (responses, keep_running) = engine.handle(command(json!({
            "id": "stats",
            "type": "stats-toggle"
        })));

        assert!(keep_running);
        assert_eq!(responses[0]["type"], "ok");
        assert_eq!(responses[1]["type"], "shortcut-action");
        assert_eq!(responses[1]["action"], "toggle-stats");
        assert_eq!(responses[1]["source"], "command");
    }

    #[test]
    fn legacy_fullscreen_toggle_routes_to_the_shell_without_native_mutation() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let (responses, keep_running) = engine.handle(command(json!({
            "id": "fullscreen",
            "type": "fullscreen-toggle"
        })));

        assert!(keep_running);
        assert_eq!(responses[0]["type"], "ok");
        assert_eq!(responses[1]["type"], "shortcut-action");
        assert_eq!(responses[1]["action"], "toggle-fullscreen");
        assert_eq!(responses[1]["source"], "command");
    }

    fn unused_udp_port() -> u16 {
        let socket = UdpSocket::bind("127.0.0.1:0").expect("ephemeral UDP port");
        let port = socket.local_addr().expect("socket address").port();
        drop(socket);
        port
    }

    #[derive(Default)]
    struct TestNvstResources {
        keyframe_requests: AtomicUsize,
        acknowledged_frames: AtomicUsize,
        recoveries: AtomicUsize,
        stops: AtomicUsize,
        captured_inputs: Mutex<Vec<Vec<u8>>>,
    }

    impl NvstSessionResources for TestNvstResources {
        fn request_keyframe(&self) {
            self.keyframe_requests.fetch_add(1, Ordering::Relaxed);
        }

        fn acknowledge_video_frame(&self, _bytes: u32) {
            self.acknowledged_frames.fetch_add(1, Ordering::Relaxed);
        }

        fn send_captured_input(&self, bytes: Vec<u8>) -> Result<(), String> {
            self.captured_inputs
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .push(bytes);
            Ok(())
        }

        fn apply_cursor(&self, _bytes: Vec<u8>) {}

        fn recover(&self) -> Result<(), String> {
            self.recoveries.fetch_add(1, Ordering::Relaxed);
            Ok(())
        }

        fn stop(&self) {
            self.stops.fetch_add(1, Ordering::Relaxed);
        }
    }

    fn connected_lifecycle() -> Mutex<Lifecycle> {
        Mutex::new(Lifecycle {
            state: State::Connected,
            context: Some(
                serde_json::from_value(synthetic_context("nvst-recovery", json!([])))
                    .expect("session context"),
            ),
            generation: 7,
        })
    }

    #[test]
    fn decoder_keyframe_feedback_routes_to_nvst_pli_handle() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut state = NvstMediaFeedbackState::new(true);

        forward_nvst_media_feedback(
            &sender,
            &lifecycle,
            7,
            &resources,
            MediaFeedback::RequestKeyframe {
                mid: "nvst-video-0".to_owned(),
                reason: "decoder reference loss".to_owned(),
            },
            &mut state,
        );

        assert_eq!(resources.keyframe_requests.load(Ordering::Relaxed), 1);
        let message = receiver.recv().expect("keyframe log");
        assert_eq!(message["type"], "log");
        assert!(
            message["message"]
                .as_str()
                .is_some_and(|message| message.contains("decoder reference loss"))
        );
    }

    #[test]
    fn accepted_video_keyframe_routes_pacing_feedback_and_resets_recovery_budget() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut state = NvstMediaFeedbackState::new(true);
        state.recovery_attempts = 1;

        forward_nvst_media_feedback(
            &sender,
            &lifecycle,
            7,
            &resources,
            MediaFeedback::VideoFrameAccepted {
                timestamp: 90_000,
                bytes: 1_024,
                keyframe: true,
            },
            &mut state,
        );

        assert_eq!(resources.acknowledged_frames.load(Ordering::Relaxed), 1);
        assert_eq!(state.recovery_attempts, 0);
    }

    #[test]
    fn accepted_video_frames_emit_bounded_shell_telemetry() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut state = NvstMediaFeedbackState::new(true);
        state.telemetry_window_started = Instant::now() - Duration::from_secs(1);

        forward_nvst_media_feedback(
            &sender,
            &lifecycle,
            7,
            &resources,
            MediaFeedback::VideoFrameAccepted {
                timestamp: 90_000,
                bytes: 125_000,
                keyframe: false,
            },
            &mut state,
        );

        let telemetry = receiver.recv().expect("stream telemetry");
        assert_eq!(telemetry["type"], "telemetry");
        assert!(
            telemetry["framesPerSecond"]
                .as_f64()
                .is_some_and(|value| value > 0.0)
        );
        assert!(
            telemetry["bitrateMbps"]
                .as_f64()
                .is_some_and(|value| (0.9..=1.0).contains(&value))
        );
        assert_eq!(telemetry["peakBitrateMbps"], telemetry["bitrateMbps"]);
    }

    #[test]
    fn captured_sdl_input_routes_through_the_nvst_input_codec_packet_shape() {
        let resources = TestNvstResources::default();
        let state = NvstMediaFeedbackState::new(true);

        assert!(
            forward_nvst_captured_input(
                &resources,
                CapturedInput::Key {
                    virtual_key: 0x57,
                    modifiers: 0x01,
                    pressed: true,
                },
                &state,
            )
            .is_ok()
        );
        assert!(
            forward_nvst_captured_input(
                &resources,
                CapturedInput::MouseMove {
                    delta_x: -12,
                    delta_y: 34,
                },
                &state,
            )
            .is_ok()
        );
        assert!(
            forward_nvst_captured_input(
                &resources,
                CapturedInput::MouseAbsolute {
                    x: 321,
                    y: 180,
                    width: 1280,
                    height: 720,
                },
                &state,
            )
            .is_ok()
        );

        let inputs = resources
            .captured_inputs
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        assert_eq!(inputs.len(), 3);
        assert_eq!(u32::from_le_bytes(inputs[0][0..4].try_into().unwrap()), 3);
        assert_eq!(
            u16::from_be_bytes(inputs[0][4..6].try_into().unwrap()),
            0x57
        );
        assert_eq!(
            u16::from_be_bytes(inputs[0][6..8].try_into().unwrap()),
            0x01
        );
        assert_eq!(inputs[0].len(), 18);
        assert_eq!(u32::from_le_bytes(inputs[1][0..4].try_into().unwrap()), 7);
        assert_eq!(i16::from_be_bytes(inputs[1][4..6].try_into().unwrap()), -12);
        assert_eq!(i16::from_be_bytes(inputs[1][6..8].try_into().unwrap()), 34);
        assert_eq!(inputs[1].len(), 22);
        assert_eq!(u32::from_le_bytes(inputs[2][0..4].try_into().unwrap()), 5);
        assert_eq!(u16::from_be_bytes(inputs[2][4..6].try_into().unwrap()), 321);
        assert_eq!(u16::from_be_bytes(inputs[2][6..8].try_into().unwrap()), 180);
        assert_eq!(
            u16::from_be_bytes(inputs[2][10..12].try_into().unwrap()),
            1280
        );
        assert_eq!(
            u16::from_be_bytes(inputs[2][12..14].try_into().unwrap()),
            720
        );
        assert_eq!(inputs[2].len(), 26);
    }

    #[test]
    fn captured_input_preserves_the_os_capture_timestamp() {
        let resources = TestNvstResources::default();
        let input_origin = Instant::now();
        let mut state = NvstMediaFeedbackState::new(true);
        state.input_origin = input_origin;
        forward_nvst_captured_sample(
            &resources,
            CapturedInputSample {
                input: CapturedInput::MouseMove {
                    delta_x: 1,
                    delta_y: -1,
                },
                captured_at: input_origin + Duration::from_micros(4_242),
            },
            &state,
        )
        .expect("captured input");

        let inputs = resources
            .captured_inputs
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        assert_eq!(
            u64::from_be_bytes(inputs[0][14..22].try_into().unwrap()),
            4_242
        );
    }

    #[test]
    fn captured_gamepad_matches_the_official_38_byte_packet() {
        let packet = captured_input_packet(
            CapturedInput::Gamepad {
                controller_id: 2,
                bitmap: 0x0404,
                buttons: 0x5101,
                left_trigger: 17,
                right_trigger: 231,
                left_stick_x: -12_345,
                left_stick_y: 23_456,
                right_stick_x: -30_000,
                right_stick_y: 30_001,
            },
            0x0102_0304_0506_0708,
        );

        assert_eq!(packet.len(), 38);
        assert_eq!(u32::from_le_bytes(packet[0..4].try_into().unwrap()), 12);
        assert_eq!(u16::from_le_bytes(packet[4..6].try_into().unwrap()), 26);
        assert_eq!(u16::from_le_bytes(packet[6..8].try_into().unwrap()), 2);
        assert_eq!(
            u16::from_le_bytes(packet[8..10].try_into().unwrap()),
            0x0404
        );
        assert_eq!(u16::from_le_bytes(packet[10..12].try_into().unwrap()), 20);
        assert_eq!(
            u16::from_le_bytes(packet[12..14].try_into().unwrap()),
            0x5101
        );
        assert_eq!(
            u16::from_le_bytes(packet[14..16].try_into().unwrap()),
            0xe711
        );
        assert_eq!(
            i16::from_le_bytes(packet[16..18].try_into().unwrap()),
            -12_345
        );
        assert_eq!(
            i16::from_le_bytes(packet[18..20].try_into().unwrap()),
            23_456
        );
        assert_eq!(
            i16::from_le_bytes(packet[20..22].try_into().unwrap()),
            -30_000
        );
        assert_eq!(
            i16::from_le_bytes(packet[22..24].try_into().unwrap()),
            30_001
        );
        assert_eq!(u16::from_le_bytes(packet[26..28].try_into().unwrap()), 85);
        assert_eq!(
            u64::from_le_bytes(packet[30..38].try_into().unwrap()),
            0x0102_0304_0506_0708
        );
        assert!(captured_input_packet(CapturedInput::Guide, 1).is_empty());
        assert!(captured_input_packet(CapturedInput::Screenshot, 1).is_empty());
        assert!(captured_input_packet(CapturedInput::RecordingToggle, 1).is_empty());
    }

    #[test]
    fn relative_mouse_tuning_matches_sensitivity_and_bounded_acceleration() {
        assert_eq!(
            tune_relative_mouse(
                20,
                -10,
                InputTuning {
                    sensitivity: 0.5,
                    acceleration_percent: 1.0,
                },
            ),
            (10, -5)
        );
        let accelerated = tune_relative_mouse(
            100,
            0,
            InputTuning {
                sensitivity: 1.0,
                acceleration_percent: 150.0,
            },
        );
        assert_eq!(accelerated, (160, 0));
        assert_eq!(
            tune_relative_mouse(
                i16::MAX,
                i16::MIN,
                InputTuning {
                    sensitivity: 3.0,
                    acceleration_percent: 150.0,
                },
            ),
            (i16::MAX, i16::MIN)
        );
    }

    #[test]
    fn queue_drop_reports_do_not_mix_audio_samples_with_video_frames() {
        let expired = Instant::now() - Duration::from_secs(2);
        let mut reports = HashMap::from([
            (
                "audio-output",
                QueueDropReport {
                    dropped: 0,
                    started: expired,
                },
            ),
            (
                "linux-present",
                QueueDropReport {
                    dropped: 0,
                    started: expired,
                },
            ),
        ]);

        assert_eq!(
            record_queue_drop(&mut reports, "audio-output", 96_000),
            Some(96_000)
        );
        assert_eq!(
            record_queue_drop(&mut reports, "linux-present", 47),
            Some(47)
        );
    }

    #[test]
    fn nvst_recovery_is_attempted_once_with_pli() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut recovery_attempts = 0;

        let terminal = forward_nvst_event(
            &sender,
            &lifecycle,
            7,
            &resources,
            &mut recovery_attempts,
            NvstReceiveEvent::RecoveryNeeded(opennow_streamer_transport::NvstRecovery::Timeout {
                idle_for: Duration::from_secs(2),
            }),
        );

        assert!(!terminal);
        assert_eq!(recovery_attempts, 1);
        assert_eq!(resources.recoveries.load(Ordering::Relaxed), 1);
        assert_eq!(resources.keyframe_requests.load(Ordering::Relaxed), 1);
        assert_eq!(resources.stops.load(Ordering::Relaxed), 0);
        assert_eq!(lock_lifecycle(&lifecycle).state, State::Connected);
        assert!(
            receiver
                .try_iter()
                .all(|message| message["type"] != "error")
        );
    }

    #[test]
    fn repeated_packet_gaps_request_keyframes_without_stopping_the_session() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut recovery_attempts = 0;

        for first_missing_index in [100, 200] {
            assert!(!forward_nvst_event(
                &sender,
                &lifecycle,
                7,
                &resources,
                &mut recovery_attempts,
                NvstReceiveEvent::RecoveryNeeded(NvstRecovery::PacketGap {
                    first_missing_index,
                    last_missing_index: first_missing_index + 31,
                }),
            ));
        }

        assert_eq!(recovery_attempts, 0);
        assert_eq!(resources.recoveries.load(Ordering::Relaxed), 0);
        assert_eq!(resources.keyframe_requests.load(Ordering::Relaxed), 2);
        assert_eq!(resources.stops.load(Ordering::Relaxed), 0);
        assert_eq!(lock_lifecycle(&lifecycle).state, State::Connected);
        assert!(
            receiver
                .try_iter()
                .all(|message| message["type"] != "error")
        );
    }

    #[test]
    fn transient_media_backpressure_requests_keyframe_without_stopping_session() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut recovery_attempts = 0;

        assert!(!forward_nvst_event(
            &sender,
            &lifecycle,
            7,
            &resources,
            &mut recovery_attempts,
            NvstReceiveEvent::Dropped(NvstDropReason::MediaConsumerBackpressured),
        ));

        assert_eq!(recovery_attempts, 0);
        assert_eq!(resources.keyframe_requests.load(Ordering::Relaxed), 1);
        assert_eq!(resources.stops.load(Ordering::Relaxed), 0);
        assert_eq!(lock_lifecycle(&lifecycle).state, State::Connected);
        assert!(
            receiver
                .try_iter()
                .all(|message| message["type"] != "error" && message["type"] != "status")
        );
    }

    #[test]
    fn exhausted_nvst_recovery_stops_every_leg_and_emits_terminal_status() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut recovery_attempts = 0;
        let recovery = || {
            NvstReceiveEvent::RecoveryNeeded(opennow_streamer_transport::NvstRecovery::Timeout {
                idle_for: Duration::from_secs(2),
            })
        };

        assert!(!forward_nvst_event(
            &sender,
            &lifecycle,
            7,
            &resources,
            &mut recovery_attempts,
            recovery(),
        ));
        assert!(forward_nvst_event(
            &sender,
            &lifecycle,
            7,
            &resources,
            &mut recovery_attempts,
            recovery(),
        ));

        assert_eq!(resources.recoveries.load(Ordering::Relaxed), 1);
        assert_eq!(resources.keyframe_requests.load(Ordering::Relaxed), 1);
        assert_eq!(resources.stops.load(Ordering::Relaxed), 1);
        let lifecycle = lock_lifecycle(&lifecycle);
        assert_eq!(lifecycle.state, State::Idle);
        assert!(lifecycle.context.is_none());
        drop(lifecycle);
        let events = receiver.try_iter().collect::<Vec<_>>();
        assert!(events.iter().any(|message| {
            message["type"] == "error" && message["code"] == "nvst-recovery-exhausted"
        }));
        assert!(
            events
                .iter()
                .any(|message| { message["type"] == "status" && message["status"] == "stopped" })
        );
    }

    #[test]
    fn assembled_keyframe_does_not_reset_recovery_episode_budget() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let lifecycle = connected_lifecycle();
        let resources = TestNvstResources::default();
        let mut recovery_attempts = 1;

        assert!(!forward_nvst_event(
            &sender,
            &lifecycle,
            7,
            &resources,
            &mut recovery_attempts,
            NvstReceiveEvent::Frame(opennow_streamer_transport::EncodedVideoAccessUnit {
                codec: opennow_streamer_transport::NvstVideoCodec::H264,
                timestamp: 1,
                frame_index: 1,
                first_stream_packet_index: 1,
                keyframe: true,
                contiguous: true,
                bytes: vec![0, 0, 0, 1, 0x65],
            }),
        ));
        assert_eq!(recovery_attempts, 1);
    }

    #[test]
    fn hello_reports_honest_transport_only_capabilities() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let command = command(json!({
            "id": "hello",
            "type": "hello",
            "protocolVersion": PROTOCOL_VERSION,
        }));
        let (responses, _) = engine.handle(command);
        assert_eq!(responses[0]["type"], "ready");
        assert_eq!(responses[0]["capabilities"]["supportsOfferAnswer"], false);
        assert_eq!(responses[0]["capabilities"]["supportsVideoPresent"], false);
    }

    #[test]
    fn extracts_partial_reliable_threshold() {
        assert_eq!(
            partial_reliable_threshold("v=0\r\na=ri.partialReliableThresholdMs:250\r\n"),
            Some(250),
        );
    }

    #[test]
    fn derives_bounded_windows_media_configuration_from_stream_settings() {
        let mut value = synthetic_context("media-config", json!([]));
        value["settings"] = json!({
            "codec": "H264",
            "resolution": "2560x1440",
            "fps": 120,
            "maxBitrateMbps": 75,
            "enableCloudGsync": true,
            "autoFullScreen": true
        });
        value["session"]["negotiatedStreamProfile"] = json!({
            "enableCloudGsync": true
        });
        let context: SessionContext = serde_json::from_value(value).expect("context");

        assert_eq!(
            media_stream_config(&context),
            MediaStreamConfig {
                codec: MediaVideoCodec::H264,
                color_quality: MediaColorQuality::EightBit420,
                width: 2560,
                height: 1440,
                fps: 120,
                bitrate_bps: 75_000_000,
                cloud_gsync: true,
                shortcuts: StreamShortcutBindings::default(),
            }
        );

        let fallback: SessionContext =
            serde_json::from_value(synthetic_context("fallback-config", json!([])))
                .expect("context");
        assert_eq!(media_stream_config(&fallback), MediaStreamConfig::default());

        let mut high_fps = synthetic_context("high-fps-config", json!([]));
        high_fps["settings"] = json!({
            "codec": "H264",
            "resolution": "1920x1080",
            "fps": 360,
            "maxBitrateMbps": 100
        });
        high_fps["session"]["negotiatedStreamProfile"] = json!({
            "codec": "AV1",
            "fps": 300,
            "colorQuality": "10bit_444"
        });
        let high_fps: SessionContext = serde_json::from_value(high_fps).expect("context");
        assert_eq!(media_stream_config(&high_fps).codec, MediaVideoCodec::Av1);
        assert_eq!(media_stream_config(&high_fps).fps, 240);
        assert_eq!(
            media_stream_config(&high_fps).color_quality,
            MediaColorQuality::TenBit444
        );

        let mut rejected_vrr = synthetic_context("rejected-vrr-config", json!([]));
        rejected_vrr["settings"] = json!({ "enableCloudGsync": true });
        rejected_vrr["session"]["negotiatedStreamProfile"] = json!({
            "enableCloudGsync": false
        });
        let rejected_vrr: SessionContext = serde_json::from_value(rejected_vrr).expect("context");
        assert!(!media_stream_config(&rejected_vrr).cloud_gsync);

        let mut forced_vrr = synthetic_context("forced-vrr-config", json!([]));
        forced_vrr["settings"] = json!({
            "enableCloudGsync": false,
            "nativeCloudGsyncMode": "forced",
            "showNativeStreamerStats": true,
            "statsOverlayPosition": "top-right"
        });
        forced_vrr["session"]["negotiatedStreamProfile"] = json!({
            "enableCloudGsync": true
        });
        let forced_vrr: SessionContext = serde_json::from_value(forced_vrr).expect("context");
        let forced_config = media_stream_config(&forced_vrr);
        assert!(forced_config.cloud_gsync);

        let mut legacy_overlay = synthetic_context("legacy-overlay-config", json!([]));
        legacy_overlay["settings"] = json!({
            "showNativeStreamerStats": true,
            "showStatsOnLaunch": true,
            "statsOverlayPosition": "bottom-left",
            "autoFullScreen": true
        });
        let legacy_overlay: SessionContext =
            serde_json::from_value(legacy_overlay).expect("context");
        assert_eq!(
            media_stream_config(&legacy_overlay),
            MediaStreamConfig::default()
        );
    }

    #[test]
    fn start_validates_stores_context_and_prepares_session() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let context = synthetic_context("synthetic-session", json!([]));
        let start = command(json!({
            "id": "start",
            "type": "start",
            "context": context,
        }));
        let (responses, _) = engine.handle(start);

        assert_eq!(responses[0]["type"], "ok");
        assert_eq!(responses[0]["transport"], "webrtc");
        let lifecycle = lock_lifecycle(&engine.lifecycle);
        assert_eq!(lifecycle.state, State::Prepared);
        let stored = serde_json::to_value(lifecycle.context.as_ref().expect("stored context"))
            .expect("serializable stored context");
        assert_eq!(stored["session"]["sessionId"], "synthetic-session");
        assert_eq!(stored["session"]["syntheticExtension"], "preserved");
        assert_eq!(stored["syntheticContextExtension"], true);
        drop(lifecycle);
        let status = receiver.recv().expect("ready status");
        assert_eq!(status["status"], "ready");
    }

    #[test]
    fn valid_nvst_handoff_starts_udp_video_and_bypasses_webrtc_offer_negotiation() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let (media_sender, _media_receiver) = std::sync::mpsc::sync_channel(4);
        let mut engine = Engine::with_media_consumer(sender, media_sender);
        let mut context = synthetic_context("nvst-session", json!([]));
        context["settings"]["codec"] = json!("AV1");
        context["nvstVideo"] = json!({
            "clientUdpPort": unused_udp_port(),
            "videoPeerIp": "127.0.0.1",
            "videoPeerPort": 5004,
            "srtpAesKeyHex": "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "srtpSaltHex": "00000000000000009ECA935E",
            "codec": "H264"
        });
        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": context.clone(),
        })));

        assert_eq!(responses[0]["type"], "ok");
        assert_eq!(responses[0]["transport"], "nvst");
        assert_eq!(responses[0]["capabilities"]["supportsOfferAnswer"], false);
        assert_eq!(responses[0]["capabilities"]["supportsRemoteIce"], false);
        assert_eq!(responses[0]["capabilities"]["supportsInput"], false);
        assert_eq!(responses[0]["capabilities"]["supportsAudioDecode"], false);
        assert_eq!(lifecycle_state(&engine), State::Connected);
        assert!(engine.nvst_transport.is_some());
        assert!(engine.transport.is_none());
        assert!(receiver.try_iter().any(|message| {
            message["type"] == "status"
                && message["message"]
                    .as_str()
                    .is_some_and(|text| text.contains("NVST"))
        }));

        let (responses, _) = engine.handle(command(json!({
            "id": "offer",
            "type": "offer",
            "context": context,
            "sdp": synthetic_offer(),
        })));
        assert_eq!(responses[0]["code"], "nvst-video-active");

        let (responses, _) = engine.handle(command(json!({
            "id": "stop",
            "type": "stop",
            "reason": "test complete",
        })));
        assert_eq!(responses[0]["type"], "ok");
        assert_eq!(lifecycle_state(&engine), State::Idle);
    }

    #[test]
    fn explicit_invalid_nvst_handoff_fails_closed_without_webrtc_fallback() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let mut context = synthetic_context("invalid-nvst-session", json!([]));
        context["nvstVideo"] = json!({
            "clientUdpPort": 0,
            "codec": "H264"
        });

        let (responses, _) = engine.handle(command(json!({
            "id": "start-invalid-nvst",
            "type": "start",
            "context": context,
        })));

        assert_eq!(responses[0]["code"], "invalid-nvst-handoff");
        assert_eq!(lifecycle_state(&engine), State::Idle);
        assert!(engine.transport.is_none());
        assert!(engine.nvst_transport.is_none());
    }

    #[test]
    fn explicit_nvst_mode_without_endpoint_fails_closed() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let mut context = synthetic_context("missing-nvst-session", json!([]));
        context["settings"]["transportMode"] = json!("nvst");

        let (responses, _) = engine.handle(command(json!({
            "id": "start-missing-nvst",
            "type": "start",
            "context": context,
        })));

        assert_eq!(responses[0]["code"], "missing-rtsps-endpoint");
        assert_eq!(lifecycle_state(&engine), State::Idle);
        assert!(engine.transport.is_none());
        assert!(engine.nvst_transport.is_none());
    }

    #[test]
    fn unused_nvst_reservation_can_be_released_idempotently() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);

        let (responses, _) = engine.handle(command(json!({
            "id": "bind",
            "type": "nvst-bind",
        })));
        assert_eq!(responses[0]["type"], "nvst-bound");
        assert!(engine.reserved_nvst_bundle.is_some());

        for id in ["unbind", "unbind-again"] {
            let (responses, _) = engine.handle(command(json!({
                "id": id,
                "type": "nvst-unbind",
            })));
            assert_eq!(responses[0]["type"], "ok");
            assert!(engine.reserved_nvst_bundle.is_none());
        }
    }

    #[test]
    fn start_rejects_invalid_and_duplicate_sessions() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let invalid = command(json!({
            "id": "invalid",
            "type": "start",
            "context": {
                "session": { "sessionId": "", "serverIp": "host", "iceServers": [] },
                "settings": {},
                "shortcuts": {}
            }
        }));
        let (responses, _) = engine.handle(invalid);
        assert_eq!(responses[0]["code"], "invalid-context");
        assert_eq!(lifecycle_state(&engine), State::Idle);

        for id in ["first", "duplicate"] {
            let start = command(json!({
                "id": id,
                "type": "start",
                "context": synthetic_context("synthetic-session", json!([])),
            }));
            let (responses, _) = engine.handle(start);
            if id == "first" {
                assert_eq!(responses[0]["type"], "ok");
            } else {
                assert_eq!(responses[0]["code"], "invalid-state");
            }
        }
    }

    #[test]
    fn offer_negotiates_directly_with_configured_ice_services() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let (media_sender, _media_receiver) = std::sync::mpsc::sync_channel(4);
        let mut engine = Engine::with_media_consumer(sender, media_sender);
        let context = synthetic_context(
            "synthetic-session",
            json!([{
                "urls": ["stun:stun.synthetic.invalid:3478", "turn:turn.synthetic.invalid:3478"],
                "username": "synthetic-user",
                "credential": "synthetic-credential"
            }]),
        );
        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": context.clone(),
        })));
        assert_eq!(responses[0]["type"], "ok");

        let (responses, _) = engine.handle(command(json!({
            "id": "offer",
            "type": "offer",
            "context": context,
            "sdp": synthetic_offer()
        })));
        assert_eq!(responses[0]["type"], "answer");
        assert!(
            responses[0]["answer"]["sdp"]
                .as_str()
                .is_some_and(|sdp| sdp.contains("m=video") && !sdp.contains("m=video 0"))
        );
        assert_eq!(lifecycle_state(&engine), State::Negotiating);
    }

    #[test]
    fn offer_fails_typed_when_no_in_process_media_consumer_exists() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let context = synthetic_context("synthetic-session", json!([]));
        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": context.clone(),
        })));
        assert_eq!(responses[0]["type"], "ok");

        let (responses, _) = engine.handle(command(json!({
            "id": "offer",
            "type": "offer",
            "context": context,
            "sdp": "v=0\r\n"
        })));

        assert_eq!(responses[0]["code"], "media-consumer-unavailable");
        assert_eq!(lifecycle_state(&engine), State::Prepared);
    }

    #[test]
    fn prepared_session_negotiates_synthetic_offer_for_typed_media_consumer() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let (media_sender, _media_receiver) = std::sync::mpsc::sync_channel(4);
        let mut engine = Engine::with_media_consumer(sender, media_sender);
        let context = synthetic_context("synthetic-session", json!([]));
        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": context.clone(),
        })));
        assert_eq!(responses[0]["type"], "ok");

        let (responses, _) = engine.handle(command(json!({
            "id": "offer",
            "type": "offer",
            "context": context,
            "sdp": synthetic_offer()
        })));

        assert_eq!(responses[0]["type"], "answer");
        assert!(
            responses[0]["answer"]["sdp"]
                .as_str()
                .is_some_and(|sdp| sdp.contains("m=video") && !sdp.contains("m=video 0"))
        );
        assert_eq!(lifecycle_state(&engine), State::Negotiating);
        assert!(
            receiver
                .try_iter()
                .any(|value| value["type"] == "local-ice")
        );
    }

    #[test]
    fn disconnect_clears_context_and_stale_disconnect_cannot_clear_new_session() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender.clone());
        let (responses, _) = engine.handle(command(json!({
            "id": "first",
            "type": "start",
            "context": synthetic_context("first-session", json!([])),
        })));
        assert_eq!(responses[0]["type"], "ok");
        let first_generation = lock_lifecycle(&engine.lifecycle).generation;
        forward_transport_event(
            &sender,
            &engine.lifecycle,
            first_generation,
            TransportEvent::Disconnected("synthetic disconnect".to_owned()),
        );
        {
            let lifecycle = lock_lifecycle(&engine.lifecycle);
            assert_eq!(lifecycle.state, State::Idle);
            assert!(lifecycle.context.is_none());
        }

        let (responses, _) = engine.handle(command(json!({
            "id": "second",
            "type": "start",
            "context": synthetic_context("second-session", json!([])),
        })));
        assert_eq!(responses[0]["type"], "ok");
        forward_transport_event(
            &sender,
            &engine.lifecycle,
            first_generation,
            TransportEvent::Disconnected("late stale disconnect".to_owned()),
        );
        let lifecycle = lock_lifecycle(&engine.lifecycle);
        assert_eq!(lifecycle.state, State::Prepared);
        assert_eq!(
            lifecycle
                .context
                .as_ref()
                .map(|value| value.session.session_id.as_str()),
            Some("second-session")
        );
        drop(lifecycle);

        let events = receiver.try_iter().collect::<Vec<_>>();
        assert!(events.iter().any(|value| value["status"] == "stopped"));
        assert!(
            !events
                .iter()
                .any(|value| value["message"] == "late stale disconnect")
        );
    }

    #[test]
    fn encoded_media_consumer_is_typed_in_process_and_preserves_arc_payload() {
        let (sender, receiver) = std::sync::mpsc::channel();
        let (media_sender, media_receiver) = std::sync::mpsc::sync_channel(4);
        let mut engine = Engine::with_media_consumer(sender, media_sender);
        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": synthetic_context("synthetic-session", json!([])),
        })));
        assert_eq!(responses[0]["type"], "ok");
        let payload: Arc<[u8]> = Arc::from([1_u8, 2, 3]);
        engine
            .media_consumer
            .as_ref()
            .expect("media consumer")
            .send(EncodedMediaFrame {
                mid: "video-0".to_owned(),
                codec: "H264".to_owned(),
                payload: payload.clone(),
                rtp_timestamp: 90_000,
                clock_rate_hz: 90_000,
                channels: None,
                received_at_us: 1_500,
                keyframe: true,
                contiguous: true,
            })
            .expect("frame delivery");

        let frame = media_receiver.recv().expect("encoded frame");
        assert!(Arc::ptr_eq(&frame.payload, &payload));
        assert_eq!(frame.rtp_timestamp, 90_000);
        assert_eq!(frame.clock_rate_hz, 90_000);
        assert_eq!(frame.received_at_us, 1_500);
        assert!(
            receiver
                .try_iter()
                .all(|value| value["type"] != "encoded-media")
        );
    }

    #[test]
    fn unapplied_commands_are_rejected_and_stop_clears_context() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let (responses, _) = engine.handle(command(json!({
            "id": "surface",
            "type": "surface",
            "surface": {}
        })));
        assert_eq!(responses[0]["code"], "unsupported-command");

        let (responses, _) = engine.handle(command(json!({
            "id": "start",
            "type": "start",
            "context": synthetic_context("synthetic-session", json!([])),
        })));
        assert_eq!(responses[0]["type"], "ok");
        let (responses, _) = engine.handle(command(json!({
            "id": "shortcuts",
            "type": "update-shortcuts",
            "shortcuts": { "stopStream": "Ctrl+Alt+Q" }
        })));
        assert_eq!(responses[0]["code"], "unsupported-command");
        let (responses, _) = engine.handle(command(json!({
            "id": "stop",
            "type": "stop",
            "reason": "synthetic test complete"
        })));
        assert_eq!(responses[0]["type"], "ok");
        let lifecycle = lock_lifecycle(&engine.lifecycle);
        assert_eq!(lifecycle.state, State::Idle);
        assert!(lifecycle.context.is_none());
    }

    #[test]
    fn derives_initial_media_dimensions_from_session_settings() {
        assert_eq!(
            media_stream_config(
                &serde_json::from_value(json!({
                    "session": {
                        "sessionId": "test",
                        "serverIp": "127.0.0.1",
                        "negotiatedStreamProfile": { "resolution": "3840x2160" }
                    },
                    "settings": { "resolution": "1920x1080" },
                    "shortcuts": {}
                }))
                .expect("context")
            ),
            MediaStreamConfig {
                width: 3840,
                height: 2160,
                ..MediaStreamConfig::default()
            }
        );
        assert_eq!(
            media_stream_config(
                &serde_json::from_value(json!({
                    "session": { "sessionId": "test", "serverIp": "127.0.0.1" },
                    "settings": { "resolution": "invalid" },
                    "shortcuts": {}
                }))
                .expect("context")
            ),
            MediaStreamConfig::default()
        );
    }
}
