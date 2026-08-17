use std::sync::mpsc::Sender;

use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64;
use opennow_streamer_platform::video_backends;
use opennow_streamer_protocol::{Capabilities, Command, PROTOCOL_VERSION, error, event, response};
use opennow_streamer_transport::{TransportEvent, TransportSession, negotiate};
use serde_json::{Value, json};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum State {
    Idle,
    Prepared,
    Negotiating,
}

const TRANSPORT_UNAVAILABLE: &str =
    "Native streamer v2 transport is not ready for decoded presentation; web fallback is required";

pub struct Engine {
    state: State,
    context: Option<Value>,
    transport: Option<TransportSession>,
    events: Sender<Value>,
}

impl Engine {
    pub fn new(events: Sender<Value>) -> Self {
        Self {
            state: State::Idle,
            context: None,
            transport: None,
            events,
        }
    }

    pub fn handle(&mut self, command: Command) -> (Vec<Value>, bool) {
        let id = command.id.clone();
        let result = match command.kind.as_str() {
            "hello" => self.hello(&command),
            "start" => self.start(command),
            "offer" => self.offer(command),
            "remote-ice" => self.remote_ice(command),
            "input" => self.input(command),
            "input-paused" | "surface" | "bitrate" | "update-shortcuts" => {
                Ok(vec![response(id, "ok")])
            }
            "stop" => {
                self.stop(command.reason.as_deref().unwrap_or("stopped"));
                Ok(vec![response(id, "ok")])
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
                format!("Native streamer v2 requires protocol {PROTOCOL_VERSION}"),
            ));
        }
        let backends = video_backends();
        let video_ready = backends.iter().any(|backend| backend.available);
        let capabilities = Capabilities {
            protocol_version: PROTOCOL_VERSION,
            backend: "native",
            fallback_reason: TRANSPORT_UNAVAILABLE,
            supports_offer_answer: false,
            supports_remote_ice: false,
            supports_local_ice: false,
            supports_input: false,
            supports_video_decode: video_ready,
            supports_video_present: video_ready,
            video_backends: backends,
        };
        Ok(vec![json!({
            "id": command.id,
            "type": "ready",
            "capabilities": capabilities,
        })])
    }

    fn start(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        Err(error(
            Some(&command.id),
            "transport-unavailable",
            TRANSPORT_UNAVAILABLE,
        ))
    }

    fn offer(&mut self, command: Command) -> Result<Vec<Value>, Value> {
        if self.state == State::Idle {
            return Err(error(
                Some(&command.id),
                "not-started",
                "Start must be sent before offer",
            ));
        }
        let context = command
            .context
            .or_else(|| self.context.clone())
            .ok_or_else(|| {
                error(
                    Some(&command.id),
                    "missing-context",
                    "Offer requires session context",
                )
            })?;
        let server_ip = context
            .pointer("/session/serverIp")
            .and_then(Value::as_str)
            .ok_or_else(|| {
                error(
                    Some(&command.id),
                    "missing-server-ip",
                    "Session context does not include serverIp",
                )
            })?;
        let offer_sdp = command.sdp.as_deref().ok_or_else(|| {
            error(
                Some(&command.id),
                "missing-sdp",
                "Offer command does not include SDP",
            )
        })?;
        let threshold = partial_reliable_threshold(offer_sdp).unwrap_or(300);
        let (transport_events, receiver) = std::sync::mpsc::channel();
        let output = self.events.clone();
        std::thread::spawn(move || {
            while let Ok(event_value) = receiver.recv() {
                forward_transport_event(&output, event_value);
            }
        });

        self.state = State::Negotiating;
        let negotiated = negotiate(offer_sdp, server_ip, threshold, transport_events).map_err(
            |error_value| {
                self.state = State::Prepared;
                error(
                    Some(&command.id),
                    "webrtc-negotiation-failed",
                    error_value.to_string(),
                )
            },
        )?;
        self.transport = Some(negotiated.session);
        self.context = Some(context);
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
                    "remote-ice-failed",
                    transport_error.to_string(),
                )
            })?;
        Ok(vec![response(command.id, "ok")])
    }

    fn input(&self, command: Command) -> Result<Vec<Value>, Value> {
        let transport = self.transport.as_ref().ok_or_else(|| {
            error(
                Some(&command.id),
                "transport-not-ready",
                "No active WebRTC transport",
            )
        })?;
        let input = command
            .input
            .as_ref()
            .ok_or_else(|| error(Some(&command.id), "missing-input", "Input command is empty"))?;
        let bytes = BASE64
            .decode(&input.payload_base64)
            .map_err(|decode_error| {
                error(Some(&command.id), "invalid-input", decode_error.to_string())
            })?;
        transport
            .send_input(bytes, input.partially_reliable)
            .map_err(|transport_error| {
                error(
                    Some(&command.id),
                    "input-send-failed",
                    transport_error.to_string(),
                )
            })?;
        Ok(Vec::new())
    }

    fn stop(&mut self, reason: &str) {
        if let Some(transport) = self.transport.take() {
            transport.stop();
        }
        if self.state != State::Idle {
            let _ = self.events.send(event(
                "status",
                json!({ "status": "stopped", "message": reason }),
            ));
        }
        self.context = None;
        self.state = State::Idle;
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

fn forward_transport_event(output: &Sender<Value>, transport_event: TransportEvent) {
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
        TransportEvent::MediaFrame {
            mid,
            codec,
            bytes,
            keyframe,
            contiguous,
        } => event(
            "log",
            json!({
                "level": "debug",
                "message": format!(
                    "Received {codec} frame on {mid}: {bytes} bytes, keyframe={keyframe}, contiguous={contiguous}"
                ),
            }),
        ),
        TransportEvent::Log(message) => {
            event("log", json!({ "level": "warn", "message": message }))
        }
    };
    let _ = output.send(value);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hello_reports_honest_transport_only_capabilities() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let command: Command = serde_json::from_value(json!({
            "id": "hello",
            "type": "hello",
            "protocolVersion": PROTOCOL_VERSION,
        }))
        .expect("command");
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
    fn start_fails_closed_until_media_is_implemented() {
        let (sender, _receiver) = std::sync::mpsc::channel();
        let mut engine = Engine::new(sender);
        let command: Command = serde_json::from_value(json!({
            "id": "start",
            "type": "start",
            "context": { "session": { "sessionId": "session" } },
        }))
        .expect("command");
        let (responses, _) = engine.handle(command);
        assert_eq!(responses[0]["type"], "error");
        assert_eq!(responses[0]["code"], "transport-unavailable");
        assert_eq!(engine.state, State::Idle);
    }
}
