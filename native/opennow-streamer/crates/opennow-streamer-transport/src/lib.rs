use std::io::ErrorKind;
use std::net::{IpAddr, SocketAddr, UdpSocket};
use std::sync::Once;
use std::sync::mpsc::{self, Receiver, Sender, TryRecvError};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use opennow_streamer_protocol::IceCandidate;
use str0m::change::SdpOffer;
use str0m::channel::{ChannelConfig, ChannelId, Reliability};
use str0m::crypto::from_feature_flags;
use str0m::net::{Protocol, Receive};
use str0m::{Candidate, Event, IceConnectionState, Input, Output, Rtc, RtcConfig};
use thiserror::Error;

static INSTALL_CRYPTO: Once = Once::new();
const RELIABLE_INPUT_LABEL: &str = "input_channel_v1";
const PARTIAL_INPUT_LABEL: &str = "input_channel_partially_reliable";
const STATS_LABEL: &str = "stats_channel";

#[derive(Debug, Error)]
pub enum TransportError {
    #[error("invalid server IP address: {0}")]
    InvalidServerIp(String),
    #[error("failed to bind media UDP socket: {0}")]
    Bind(#[source] std::io::Error),
    #[error("invalid WebRTC offer: {0}")]
    Offer(String),
    #[error("failed to configure local ICE candidate: {0}")]
    LocalCandidate(String),
    #[error("transport worker is no longer running")]
    Closed,
}

#[derive(Debug)]
pub enum TransportEvent {
    Connected,
    Disconnected(String),
    InputReady(u16),
    MediaFrame {
        mid: String,
        codec: String,
        bytes: usize,
        keyframe: bool,
        contiguous: bool,
    },
    Log(String),
}

enum TransportCommand {
    AddRemoteCandidate(String),
    SendInput {
        bytes: Vec<u8>,
        partially_reliable: bool,
    },
    Stop,
}

pub struct NegotiatedTransport {
    pub answer_sdp: String,
    pub local_candidate: IceCandidate,
    pub session: TransportSession,
}

pub struct TransportSession {
    commands: Sender<TransportCommand>,
    join: Option<JoinHandle<()>>,
}

impl TransportSession {
    pub fn add_remote_candidate(&self, candidate: &IceCandidate) -> Result<(), TransportError> {
        self.commands
            .send(TransportCommand::AddRemoteCandidate(
                candidate.candidate.clone(),
            ))
            .map_err(|_| TransportError::Closed)
    }

    pub fn send_input(
        &self,
        bytes: Vec<u8>,
        partially_reliable: bool,
    ) -> Result<(), TransportError> {
        self.commands
            .send(TransportCommand::SendInput {
                bytes,
                partially_reliable,
            })
            .map_err(|_| TransportError::Closed)
    }

    pub fn stop(mut self) {
        let _ = self.commands.send(TransportCommand::Stop);
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
    }
}

impl Drop for TransportSession {
    fn drop(&mut self) {
        let _ = self.commands.send(TransportCommand::Stop);
    }
}

pub fn negotiate(
    offer_sdp: &str,
    server_ip: &str,
    partial_reliable_lifetime_ms: u16,
    events: Sender<TransportEvent>,
) -> Result<NegotiatedTransport, TransportError> {
    INSTALL_CRYPTO.call_once(|| from_feature_flags().install_process_default());

    let server_ip: IpAddr = server_ip
        .parse()
        .map_err(|_| TransportError::InvalidServerIp(server_ip.to_owned()))?;
    let socket = bind_routed_socket(server_ip).map_err(TransportError::Bind)?;
    let local_addr = socket.local_addr().map_err(TransportError::Bind)?;
    let local_candidate = Candidate::host(local_addr, "udp")
        .map_err(|error| TransportError::LocalCandidate(error.to_string()))?;

    let mut rtc = RtcConfig::new().build(Instant::now());
    rtc.add_local_candidate(local_candidate.clone());
    let offer = SdpOffer::from_sdp_string(offer_sdp)
        .map_err(|error| TransportError::Offer(error.to_string()))?;
    let answer = rtc
        .sdp_api()
        .accept_offer(offer)
        .map_err(|error| TransportError::Offer(error.to_string()))?;

    let reliable = rtc.direct_api().create_data_channel(ChannelConfig {
        label: RELIABLE_INPUT_LABEL.to_owned(),
        ..Default::default()
    });
    let partial = rtc.direct_api().create_data_channel(ChannelConfig {
        label: PARTIAL_INPUT_LABEL.to_owned(),
        ordered: false,
        reliability: Reliability::MaxPacketLifetime {
            lifetime: partial_reliable_lifetime_ms,
        },
        ..Default::default()
    });
    let stats = rtc.direct_api().create_data_channel(ChannelConfig {
        label: STATS_LABEL.to_owned(),
        ordered: false,
        reliability: Reliability::MaxRetransmits { retransmits: 0 },
        ..Default::default()
    });

    let answer_sdp = answer.to_sdp_string();
    let candidate_text = local_candidate.to_sdp_string();
    let (command_tx, command_rx) = mpsc::channel();
    let join = thread::Builder::new()
        .name("opennow-webrtc".to_owned())
        .spawn(move || {
            run_transport(rtc, socket, command_rx, events, reliable, partial, stats);
        })
        .map_err(TransportError::Bind)?;

    Ok(NegotiatedTransport {
        answer_sdp,
        local_candidate: IceCandidate {
            candidate: candidate_text,
            sdp_mid: Some("0".to_owned()),
            sdp_m_line_index: Some(0),
            username_fragment: None,
        },
        session: TransportSession {
            commands: command_tx,
            join: Some(join),
        },
    })
}

fn bind_routed_socket(server_ip: IpAddr) -> std::io::Result<UdpSocket> {
    let unspecified = match server_ip {
        IpAddr::V4(_) => "0.0.0.0:0",
        IpAddr::V6(_) => "[::]:0",
    };
    let route_probe = UdpSocket::bind(unspecified)?;
    route_probe.connect(SocketAddr::new(server_ip, 9))?;
    let local_ip = route_probe.local_addr()?.ip();
    UdpSocket::bind(SocketAddr::new(local_ip, 0))
}

fn run_transport(
    mut rtc: Rtc,
    socket: UdpSocket,
    commands: Receiver<TransportCommand>,
    events: Sender<TransportEvent>,
    reliable: ChannelId,
    partial: ChannelId,
    stats: ChannelId,
) {
    let mut receive_buffer = vec![0_u8; 65_536];
    let mut input_ready = false;
    let mut next_heartbeat = Instant::now() + Duration::from_secs(2);

    loop {
        loop {
            match commands.try_recv() {
                Ok(TransportCommand::AddRemoteCandidate(candidate)) => {
                    let candidate = candidate.strip_prefix("a=").unwrap_or(&candidate);
                    match Candidate::from_sdp_string(candidate) {
                        Ok(candidate) => rtc.add_remote_candidate(candidate),
                        Err(error) => {
                            let _ = events.send(TransportEvent::Log(format!(
                                "Ignoring invalid remote ICE candidate: {error}"
                            )));
                        }
                    }
                }
                Ok(TransportCommand::SendInput {
                    bytes,
                    partially_reliable,
                }) => {
                    if input_ready {
                        let channel_id = if partially_reliable {
                            partial
                        } else {
                            reliable
                        };
                        if let Some(mut channel) = rtc.channel(channel_id) {
                            let _ = channel.write(true, &bytes);
                        }
                    }
                }
                Ok(TransportCommand::Stop) | Err(TryRecvError::Disconnected) => {
                    rtc.disconnect();
                    let _ = events.send(TransportEvent::Disconnected("stopped".to_owned()));
                    return;
                }
                Err(TryRecvError::Empty) => break,
            }
        }

        if input_ready && Instant::now() >= next_heartbeat {
            if let Some(mut channel) = rtc.channel(reliable) {
                let _ = channel.write(true, &[2, 0, 0, 0]);
            }
            next_heartbeat = Instant::now() + Duration::from_secs(2);
        }

        let timeout = loop {
            match rtc.poll_output() {
                Ok(Output::Timeout(timeout)) => break timeout,
                Ok(Output::Transmit(transmit)) => {
                    let _ = socket.send_to(&transmit.contents, transmit.destination);
                }
                Ok(Output::Event(event)) => match event {
                    Event::IceConnectionStateChange(IceConnectionState::Connected) => {
                        let _ = events.send(TransportEvent::Connected);
                    }
                    Event::IceConnectionStateChange(IceConnectionState::Disconnected) => {
                        let _ = events
                            .send(TransportEvent::Disconnected("ICE disconnected".to_owned()));
                    }
                    Event::ChannelData(data) if data.id == reliable => {
                        if let Some(version) = input_protocol_version(&data.data) {
                            input_ready = true;
                            let _ = events.send(TransportEvent::InputReady(version));
                        }
                    }
                    Event::ChannelData(data) if data.id == stats => {}
                    Event::MediaData(data) => {
                        let _ = events.send(TransportEvent::MediaFrame {
                            mid: data.mid.to_string(),
                            codec: format!("{:?}", data.params.spec()),
                            bytes: data.data.len(),
                            keyframe: data.is_keyframe(),
                            contiguous: data.contiguous,
                        });
                    }
                    _ => {}
                },
                Err(error) => {
                    let _ = events.send(TransportEvent::Disconnected(error.to_string()));
                    return;
                }
            }
        };

        let wait = timeout
            .saturating_duration_since(Instant::now())
            .min(Duration::from_millis(10));
        if wait.is_zero() {
            let _ = rtc.handle_input(Input::Timeout(Instant::now()));
            continue;
        }

        let _ = socket.set_read_timeout(Some(wait));
        let input = match socket.recv_from(&mut receive_buffer) {
            Ok((length, source)) => {
                let destination = match socket.local_addr() {
                    Ok(value) => value,
                    Err(error) => {
                        let _ = events.send(TransportEvent::Disconnected(error.to_string()));
                        return;
                    }
                };
                let contents = match receive_buffer[..length].try_into() {
                    Ok(value) => value,
                    Err(error) => {
                        let _ = events.send(TransportEvent::Log(format!(
                            "Dropping oversized UDP packet: {error}"
                        )));
                        continue;
                    }
                };
                Input::Receive(
                    Instant::now(),
                    Receive {
                        proto: Protocol::Udp,
                        source,
                        destination,
                        contents,
                    },
                )
            }
            Err(error) if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {
                Input::Timeout(Instant::now())
            }
            Err(error) => {
                let _ = events.send(TransportEvent::Disconnected(error.to_string()));
                return;
            }
        };
        if let Err(error) = rtc.handle_input(input) {
            let _ = events.send(TransportEvent::Disconnected(error.to_string()));
            return;
        }
    }
}

fn input_protocol_version(bytes: &[u8]) -> Option<u16> {
    if bytes.len() < 2 {
        return None;
    }
    let first = u16::from_le_bytes([bytes[0], bytes[1]]);
    if first == 526 {
        return Some(if bytes.len() >= 4 {
            u16::from_le_bytes([bytes[2], bytes[3]])
        } else {
            2
        });
    }
    (bytes[0] == 0x0e).then_some(first)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_both_input_handshake_layouts() {
        assert_eq!(input_protocol_version(&[0x0e, 0x02, 0x03, 0x00]), Some(3));
        assert_eq!(input_protocol_version(&[0x0e, 0x03]), Some(0x030e));
        assert_eq!(input_protocol_version(&[1]), None);
    }
}
