//! Independently authored NVST video receive transport.
//!
//! This module only implements the receive side of the classic NVST video handoff:
//! authenticated SRTP video datagrams from the negotiated peer become bounded H.264
//! Annex-B access units. It deliberately does not implement NVST audio, control, input,
//! FEC repair, or NACK transmission because the current handoff does not contain enough
//! wire information to implement those features safely.

use std::collections::{BTreeMap, VecDeque};
use std::fmt;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr, UdpSocket};
use std::sync::Arc;
use std::sync::mpsc::{self, Receiver, Sender, TryRecvError};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use aes::cipher::{KeyIvInit, StreamCipher};
use aes::{Aes128, Aes256};
use aes_gcm::aead::AeadInPlace;
use aes_gcm::{Aes128Gcm, Aes256Gcm, Nonce, Tag};
use ctr::Ctr128BE;
use hmac::{Hmac, Mac};
use serde_json::Value;
use sha1::Sha1;
use subtle::ConstantTimeEq;
use thiserror::Error;

use super::{EncodedMediaFrame, MediaConsumer, TransportError, deliver_media_frame};

const RTP_FIXED_HEADER_LEN: usize = 12;
const SRTP_AES_CM_HMAC_SHA1_80_TAG_LEN: usize = 10;
const SRTP_AEAD_AES_GCM_TAG_LEN: usize = 16;
const NV_VIDEO_PACKET_LEN: usize = 16;
const DEFAULT_REORDER_WINDOW: usize = 32;
const MAX_REORDER_WINDOW: usize = 128;
const DEFAULT_MAX_ACCESS_UNIT_BYTES: usize = 2 * 1024 * 1024;
const MAX_ACCESS_UNIT_BYTES: usize = 16 * 1024 * 1024;
const DEFAULT_TIMEOUT: Duration = Duration::from_secs(5);
const MIN_TIMEOUT: Duration = Duration::from_millis(250);
const MAX_TIMEOUT: Duration = Duration::from_secs(30);
const MAX_PING_BYTES: usize = 512;

/// The independently documented `NV_VIDEO_PACKET` flag values used by an earlier OpenNOW
/// implementation. This module does not borrow code or binaries from NVIDIA.
const FLAG_CONTAINS_PIC_DATA: u8 = 0x01;
const FLAG_EOF: u8 = 0x02;
const FLAG_SOF: u8 = 0x04;

type Aes256Ctr = Ctr128BE<Aes256>;
type Aes128Ctr = Ctr128BE<Aes128>;
type HmacSha1 = Hmac<Sha1>;

/// The SRTP profile must come from negotiated metadata. The legacy `nvstVideo` handoff has no
/// profile field, so its documented 32-byte key plus 12-byte salt convention selects GCM.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NvstSrtpProfile {
    AeadAes128Gcm,
    AeadAes256Gcm,
    AesCm128HmacSha1_32,
    AesCm128HmacSha1_80,
}

impl NvstSrtpProfile {
    fn parse(value: &str) -> Result<Self, NvstConfigError> {
        match value.trim().to_ascii_uppercase().as_str() {
            "AEAD_AES_128_GCM" | "SRTP_AEAD_AES_128_GCM" => Ok(Self::AeadAes128Gcm),
            "AEAD_AES_256_GCM" | "SRTP_AEAD_AES_256_GCM" => Ok(Self::AeadAes256Gcm),
            "AES_CM_128_HMAC_SHA1_32" | "SRTP_AES_CM_128_HMAC_SHA1_32" => {
                Ok(Self::AesCm128HmacSha1_32)
            }
            "AES_CM_128_HMAC_SHA1_80" | "SRTP_AES_CM_128_HMAC_SHA1_80" => {
                Ok(Self::AesCm128HmacSha1_80)
            }
            other => Err(NvstConfigError::UnsupportedSrtpProfile(other.to_owned())),
        }
    }
}

/// The only media codec this receive path currently exposes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NvstVideoCodec {
    H264,
}

impl NvstVideoCodec {
    fn parse(value: &str) -> Result<Self, NvstConfigError> {
        match value.trim().to_ascii_uppercase().as_str() {
            "H264" | "AVC" => Ok(Self::H264),
            other => Err(NvstConfigError::UnsupportedCodec(other.to_owned())),
        }
    }
}

/// Why an NVST handoff cannot be selected. Callers should retain their WebRTC fallback.
#[derive(Debug, Error)]
pub enum NvstConfigError {
    #[error("nvstVideo must be an object")]
    HandoffNotObject,
    #[error("nvstVideo is missing {0}")]
    MissingField(&'static str),
    #[error("nvstVideo.{field} must be a {expected}")]
    InvalidFieldType {
        field: &'static str,
        expected: &'static str,
    },
    #[error("nvstVideo.{field} is out of range")]
    OutOfRange { field: &'static str },
    #[error("nvstVideo.videoPeerIp is not a routable unicast IP address: {0}")]
    InvalidPeerIp(String),
    #[error("nvstVideo.srtpAesKeyHex is invalid for the selected SRTP profile")]
    InvalidAesKey,
    #[error("nvstVideo.srtpSaltHex is invalid for the selected SRTP profile")]
    InvalidSrtpSalt,
    #[error("nvstVideo.srtpProfile {0} is not implemented")]
    UnsupportedSrtpProfile(String),
    #[error("NVST video codec {0} is not implemented; only H264 Annex-B is available")]
    UnsupportedCodec(String),
    #[error("nvstTransport.tracks is not implemented yet; retain the legacy nvstVideo handoff")]
    RichHandoffUnsupported,
}

/// Explicitly records transport features that cannot be sent correctly from current wire data.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NvstUnsupportedFeature {
    Audio,
    Input,
    Nack,
    FecRepair,
}

impl fmt::Display for NvstUnsupportedFeature {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            Self::Audio => "audio",
            Self::Input => "input",
            Self::Nack => "NACK transmission",
            Self::FecRepair => "FEC repair",
        };
        formatter.write_str(name)
    }
}

/// Legacy `nvstVideo` configuration normalized into the bounded receive transport.
///
/// Secret material is never exposed through `Debug`. The legacy `nvstVideo` handoff defaults to
/// `AEAD_AES_256_GCM`; every SRTP profile requires an explicit `srtpSaltHex`.
#[derive(Clone)]
pub struct NvstVideoConfig {
    client_udp_port: u16,
    video_peer: SocketAddr,
    srtp: NvstSrtpMaterial,
    ping_payload: Vec<u8>,
    codec: NvstVideoCodec,
    expected_payload_type: Option<u8>,
    expected_ssrc: Option<u32>,
    reorder_window_packets: usize,
    max_access_unit_bytes: usize,
    timeout: Duration,
}

impl fmt::Debug for NvstVideoConfig {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("NvstVideoConfig")
            .field("client_udp_port", &self.client_udp_port)
            .field("video_peer", &self.video_peer)
            .field("srtp", &self.srtp)
            .field("ping_payload_len", &self.ping_payload.len())
            .field("codec", &self.codec)
            .field("expected_payload_type", &self.expected_payload_type)
            .field("expected_ssrc", &self.expected_ssrc)
            .field("reorder_window_packets", &self.reorder_window_packets)
            .field("max_access_unit_bytes", &self.max_access_unit_bytes)
            .field("timeout", &self.timeout)
            .finish()
    }
}

#[derive(Clone)]
enum NvstSrtpMaterial {
    AeadAes128Gcm {
        master_key: [u8; 16],
        master_salt: [u8; 12],
    },
    AeadAes256Gcm {
        master_key: [u8; 32],
        master_salt: [u8; 12],
    },
    AesCm128HmacSha1 {
        master_key: [u8; 16],
        master_salt: [u8; 14],
        authentication_tag_len: usize,
    },
}

impl fmt::Debug for NvstSrtpMaterial {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("NvstSrtpMaterial")
            .field("profile", &self.profile())
            .field("master_key", &"[redacted]")
            .field("master_salt", &"[redacted]")
            .finish()
    }
}

impl NvstSrtpMaterial {
    fn profile(&self) -> NvstSrtpProfile {
        match self {
            Self::AeadAes128Gcm { .. } => NvstSrtpProfile::AeadAes128Gcm,
            Self::AeadAes256Gcm { .. } => NvstSrtpProfile::AeadAes256Gcm,
            Self::AesCm128HmacSha1 {
                authentication_tag_len: SRTP_AES_CM_HMAC_SHA1_80_TAG_LEN,
                ..
            } => NvstSrtpProfile::AesCm128HmacSha1_80,
            Self::AesCm128HmacSha1 { .. } => NvstSrtpProfile::AesCm128HmacSha1_32,
        }
    }
}

impl NvstVideoConfig {
    /// Parses the stable legacy `nvstVideo` object. The parsing boundary is intentionally
    /// isolated here so a future `nvstTransport.tracks[]` handoff can normalize into this
    /// same configuration without changing the SRTP/RTP receiver.
    pub fn from_legacy_handoff(
        handoff: &Value,
        settings_codec: Option<&str>,
    ) -> Result<Self, NvstConfigError> {
        let object = handoff
            .as_object()
            .ok_or(NvstConfigError::HandoffNotObject)?;
        let client_udp_port = required_u16(object, "clientUdpPort")?;
        if client_udp_port == 0 {
            return Err(NvstConfigError::OutOfRange {
                field: "clientUdpPort",
            });
        }

        let peer_ip_text = required_string(object, "videoPeerIp")?;
        let peer_ip: IpAddr = peer_ip_text
            .parse()
            .map_err(|_| NvstConfigError::InvalidPeerIp(peer_ip_text.to_owned()))?;
        if !is_unicast_peer(peer_ip) {
            return Err(NvstConfigError::InvalidPeerIp(peer_ip_text.to_owned()));
        }
        let video_peer_port = required_u16(object, "videoPeerPort")?;
        if video_peer_port == 0 {
            return Err(NvstConfigError::OutOfRange {
                field: "videoPeerPort",
            });
        }

        let master_key = required_string(object, "srtpAesKeyHex")?;
        let srtp_profile = optional_string(object, "srtpProfile")?
            .map(NvstSrtpProfile::parse)
            .transpose()?
            .unwrap_or(NvstSrtpProfile::AeadAes256Gcm);
        let srtp = match srtp_profile {
            NvstSrtpProfile::AeadAes128Gcm => {
                let master_salt = required_string(object, "srtpSaltHex").and_then(|salt| {
                    decode_fixed_hex::<12>(salt, NvstConfigError::InvalidSrtpSalt)
                })?;
                NvstSrtpMaterial::AeadAes128Gcm {
                    master_key: decode_fixed_hex::<16>(master_key, NvstConfigError::InvalidAesKey)?,
                    master_salt,
                }
            }
            NvstSrtpProfile::AeadAes256Gcm => {
                let master_salt = required_string(object, "srtpSaltHex").and_then(|salt| {
                    decode_fixed_hex::<12>(salt, NvstConfigError::InvalidSrtpSalt)
                })?;
                NvstSrtpMaterial::AeadAes256Gcm {
                    master_key: decode_fixed_hex::<32>(master_key, NvstConfigError::InvalidAesKey)?,
                    master_salt,
                }
            }
            NvstSrtpProfile::AesCm128HmacSha1_32 | NvstSrtpProfile::AesCm128HmacSha1_80 => {
                let master_salt = required_string(object, "srtpSaltHex").and_then(|salt| {
                    decode_fixed_hex::<14>(salt, NvstConfigError::InvalidSrtpSalt)
                })?;
                NvstSrtpMaterial::AesCm128HmacSha1 {
                    master_key: decode_fixed_hex::<16>(master_key, NvstConfigError::InvalidAesKey)?,
                    master_salt,
                    authentication_tag_len: match srtp_profile {
                        NvstSrtpProfile::AesCm128HmacSha1_32 => 4,
                        NvstSrtpProfile::AesCm128HmacSha1_80 => SRTP_AES_CM_HMAC_SHA1_80_TAG_LEN,
                        _ => unreachable!("AES-CM profile selected above"),
                    },
                }
            }
        };

        let codec_name = optional_string(object, "codec")?
            .or(settings_codec)
            .ok_or(NvstConfigError::MissingField("codec"))?;
        let codec = NvstVideoCodec::parse(codec_name)?;
        let ping_payload = optional_string(object, "pingPayload")?
            .map_or_else(|| b"PING".to_vec(), |payload| payload.as_bytes().to_vec());
        if ping_payload.is_empty() || ping_payload.len() > MAX_PING_BYTES {
            return Err(NvstConfigError::OutOfRange {
                field: "pingPayload",
            });
        }

        let expected_payload_type = optional_u8(object, "rtpPayloadType")?;
        let expected_ssrc = optional_u32(object, "rtpSsrc")?;
        let reorder_window_packets =
            optional_usize(object, "reorderWindowPackets")?.unwrap_or(DEFAULT_REORDER_WINDOW);
        if !(1..=MAX_REORDER_WINDOW).contains(&reorder_window_packets) {
            return Err(NvstConfigError::OutOfRange {
                field: "reorderWindowPackets",
            });
        }
        let max_access_unit_bytes =
            optional_usize(object, "maxAccessUnitBytes")?.unwrap_or(DEFAULT_MAX_ACCESS_UNIT_BYTES);
        if !(1..=MAX_ACCESS_UNIT_BYTES).contains(&max_access_unit_bytes) {
            return Err(NvstConfigError::OutOfRange {
                field: "maxAccessUnitBytes",
            });
        }
        let timeout_ms = optional_u64(object, "timeoutMs")?;
        let timeout = timeout_ms
            .map(Duration::from_millis)
            .unwrap_or(DEFAULT_TIMEOUT);
        if !(MIN_TIMEOUT..=MAX_TIMEOUT).contains(&timeout) {
            return Err(NvstConfigError::OutOfRange { field: "timeoutMs" });
        }

        Ok(Self {
            client_udp_port,
            video_peer: SocketAddr::new(peer_ip, video_peer_port),
            srtp,
            ping_payload,
            codec,
            expected_payload_type,
            expected_ssrc,
            reorder_window_packets,
            max_access_unit_bytes,
            timeout,
        })
    }

    pub fn client_udp_port(&self) -> u16 {
        self.client_udp_port
    }

    pub fn video_peer(&self) -> SocketAddr {
        self.video_peer
    }

    pub fn codec(&self) -> NvstVideoCodec {
        self.codec
    }

    pub fn srtp_profile(&self) -> NvstSrtpProfile {
        self.srtp.profile()
    }

    pub fn timeout(&self) -> Duration {
        self.timeout
    }
}

/// Parses `context.nvstVideo` without tying the rest of the transport to JSON field names.
/// `None` means the legacy handoff was not supplied, which is a normal WebRTC fallback case.
pub fn parse_nvst_video_handoff(
    context: &Value,
) -> Result<Option<NvstVideoConfig>, NvstConfigError> {
    let Some(handoff) = context.get("nvstVideo") else {
        if context.get("nvstTransport").is_some() {
            return Err(NvstConfigError::RichHandoffUnsupported);
        }
        return Ok(None);
    };
    let settings_codec = context.pointer("/settings/codec").and_then(Value::as_str);
    NvstVideoConfig::from_legacy_handoff(handoff, settings_codec).map(Some)
}

/// The transport selector always prefers a valid NVST video handoff, while making every
/// incomplete or unsupported handoff a typed WebRTC fallback instead of an optimistic start.
#[derive(Debug)]
pub enum PreferredVideoTransport {
    Nvst(NvstVideoConfig),
    WebRtcFallback(NvstFallbackReason),
}

#[derive(Debug)]
pub enum NvstFallbackReason {
    NoNvstHandoff,
    InvalidNvstHandoff(NvstConfigError),
}

pub fn select_preferred_video_transport(context: &Value) -> PreferredVideoTransport {
    match parse_nvst_video_handoff(context) {
        Ok(Some(config)) => PreferredVideoTransport::Nvst(config),
        Ok(None) => PreferredVideoTransport::WebRtcFallback(NvstFallbackReason::NoNvstHandoff),
        Err(error) => {
            PreferredVideoTransport::WebRtcFallback(NvstFallbackReason::InvalidNvstHandoff(error))
        }
    }
}

fn required_string<'a>(
    object: &'a serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<&'a str, NvstConfigError> {
    let value = object
        .get(field)
        .ok_or(NvstConfigError::MissingField(field))?;
    value.as_str().ok_or(NvstConfigError::InvalidFieldType {
        field,
        expected: "string",
    })
}

fn optional_string<'a>(
    object: &'a serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<Option<&'a str>, NvstConfigError> {
    match object.get(field) {
        None | Some(Value::Null) => Ok(None),
        Some(value) => value
            .as_str()
            .map(Some)
            .ok_or(NvstConfigError::InvalidFieldType {
                field,
                expected: "string",
            }),
    }
}

fn required_u16(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<u16, NvstConfigError> {
    let value = required_u64(object, field)?;
    u16::try_from(value).map_err(|_| NvstConfigError::OutOfRange { field })
}

fn required_u64(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<u64, NvstConfigError> {
    let value = object
        .get(field)
        .ok_or(NvstConfigError::MissingField(field))?;
    value.as_u64().ok_or(NvstConfigError::InvalidFieldType {
        field,
        expected: "unsigned integer",
    })
}

fn optional_u64(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<Option<u64>, NvstConfigError> {
    match object.get(field) {
        None | Some(Value::Null) => Ok(None),
        Some(value) => value
            .as_u64()
            .map(Some)
            .ok_or(NvstConfigError::InvalidFieldType {
                field,
                expected: "unsigned integer",
            }),
    }
}

fn optional_u8(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<Option<u8>, NvstConfigError> {
    optional_u64(object, field)?.map_or(Ok(None), |value| {
        u8::try_from(value)
            .map(Some)
            .map_err(|_| NvstConfigError::OutOfRange { field })
    })
}

fn optional_u32(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<Option<u32>, NvstConfigError> {
    optional_u64(object, field)?.map_or(Ok(None), |value| {
        u32::try_from(value)
            .map(Some)
            .map_err(|_| NvstConfigError::OutOfRange { field })
    })
}

fn optional_usize(
    object: &serde_json::Map<String, Value>,
    field: &'static str,
) -> Result<Option<usize>, NvstConfigError> {
    optional_u64(object, field)?.map_or(Ok(None), |value| {
        usize::try_from(value)
            .map(Some)
            .map_err(|_| NvstConfigError::OutOfRange { field })
    })
}

fn decode_fixed_hex<const N: usize>(
    value: &str,
    error: NvstConfigError,
) -> Result<[u8; N], NvstConfigError> {
    if value.len() != N * 2 {
        return Err(error);
    }
    let mut decoded = [0_u8; N];
    for (index, output) in decoded.iter_mut().enumerate() {
        let offset = index * 2;
        let pair = match value.get(offset..offset + 2) {
            Some(pair) => pair,
            None => return Err(error),
        };
        *output = match u8::from_str_radix(pair, 16) {
            Ok(byte) => byte,
            Err(_) => return Err(error),
        };
    }
    Ok(decoded)
}

fn is_unicast_peer(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => !ip.is_unspecified() && !ip.is_multicast() && ip != Ipv4Addr::BROADCAST,
        IpAddr::V6(ip) => !ip.is_unspecified() && !ip.is_multicast() && ip != Ipv6Addr::UNSPECIFIED,
    }
}

/// A received H.264 byte-stream access unit ready for a decoder queue.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EncodedH264Frame {
    pub timestamp: u32,
    pub frame_index: u32,
    pub first_stream_packet_index: u32,
    pub keyframe: bool,
    pub bytes: Vec<u8>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NvstReceiverState {
    Running,
    Paused,
    RecoveryRequired,
    Stopped,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NvstDropReason {
    UnexpectedSource {
        expected: SocketAddr,
        actual: SocketAddr,
    },
    Paused,
    Stopped,
    RecoveryRequired,
    MalformedRtp(RtpParseError),
    AuthenticationFailed,
    ReplayRejected,
    UnexpectedPayloadType {
        expected: u8,
        actual: u8,
    },
    UnexpectedSsrc {
        expected: u32,
        actual: u32,
    },
    StaleRtpPacket {
        index: u64,
    },
    DuplicateRtpPacket {
        index: u64,
    },
    Unsupported(NvstUnsupportedFeature),
    AwaitingStartOfFrame,
    FrameDiscontinuity,
    MissingAnnexBStartCode,
    AccessUnitTooLarge {
        limit: usize,
    },
    MediaConsumerBackpressured,
    MediaConsumerClosed,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RtpParseError {
    TooShort,
    InvalidVersion,
    InvalidCsrcLength,
    InvalidExtensionLength,
    MissingAuthenticationTag,
    InvalidPadding,
    MissingNvVideoHeader,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NvstRecovery {
    PacketGap {
        first_missing_index: u64,
        last_missing_index: u64,
        nack: NvstUnsupportedFeature,
    },
    Timeout {
        idle_for: Duration,
    },
}

/// All receive decisions are explicit so callers can collect operational metrics without
/// treating malformed network traffic as a fatal thread error.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NvstReceiveEvent {
    Frame(EncodedH264Frame),
    Dropped(NvstDropReason),
    RecoveryNeeded(NvstRecovery),
    Lifecycle(NvstReceiverState),
}

#[derive(Debug, Clone, Copy)]
struct RtpHeader {
    payload_type: u8,
    sequence_number: u16,
    timestamp: u32,
    ssrc: u32,
    payload_offset: usize,
    has_padding: bool,
}

impl RtpHeader {
    fn parse(packet: &[u8]) -> Result<Self, RtpParseError> {
        if packet.len() < RTP_FIXED_HEADER_LEN {
            return Err(RtpParseError::TooShort);
        }
        let first = packet[0];
        if first >> 6 != 2 {
            return Err(RtpParseError::InvalidVersion);
        }
        let csrc_count = usize::from(first & 0x0f);
        let mut payload_offset = RTP_FIXED_HEADER_LEN
            .checked_add(csrc_count.saturating_mul(4))
            .ok_or(RtpParseError::InvalidCsrcLength)?;
        if packet.len() < payload_offset {
            return Err(RtpParseError::InvalidCsrcLength);
        }
        if first & 0x10 != 0 {
            if packet.len() < payload_offset + 4 {
                return Err(RtpParseError::InvalidExtensionLength);
            }
            let words =
                u16::from_be_bytes([packet[payload_offset + 2], packet[payload_offset + 3]]);
            let extension_len = usize::from(words)
                .checked_mul(4)
                .and_then(|length| length.checked_add(4))
                .ok_or(RtpParseError::InvalidExtensionLength)?;
            payload_offset = payload_offset
                .checked_add(extension_len)
                .ok_or(RtpParseError::InvalidExtensionLength)?;
            if packet.len() < payload_offset {
                return Err(RtpParseError::InvalidExtensionLength);
            }
        }
        Ok(Self {
            payload_type: packet[1] & 0x7f,
            sequence_number: u16::from_be_bytes([packet[2], packet[3]]),
            timestamp: u32::from_be_bytes([packet[4], packet[5], packet[6], packet[7]]),
            ssrc: u32::from_be_bytes([packet[8], packet[9], packet[10], packet[11]]),
            payload_offset,
            has_padding: first & 0x20 != 0,
        })
    }

    fn payload<'a>(&self, packet: &'a [u8]) -> Result<&'a [u8], RtpParseError> {
        if packet.len() < self.payload_offset {
            return Err(RtpParseError::TooShort);
        }
        let mut end = packet.len();
        if self.has_padding {
            let padding = usize::from(*packet.last().ok_or(RtpParseError::InvalidPadding)?);
            if padding == 0 || padding > end.saturating_sub(self.payload_offset) {
                return Err(RtpParseError::InvalidPadding);
            }
            end -= padding;
        }
        Ok(&packet[self.payload_offset..end])
    }
}

#[derive(Debug)]
struct RtpPacket {
    index: u64,
    header: RtpHeader,
    plaintext: Vec<u8>,
}

#[derive(Clone)]
struct SrtpReceiver {
    cipher: SrtpCipher,
    replay: ReplayWindow,
}

#[derive(Clone)]
enum SrtpCipher {
    AeadAes128Gcm {
        encryption_key: [u8; 16],
        session_salt: [u8; 12],
    },
    AeadAes256Gcm {
        encryption_key: [u8; 32],
        session_salt: [u8; 12],
    },
    AesCm128HmacSha1 {
        encryption_key: [u8; 16],
        authentication_key: [u8; 20],
        session_salt: [u8; 14],
        authentication_tag_len: usize,
    },
}

impl SrtpReceiver {
    fn from_material(material: &NvstSrtpMaterial) -> Self {
        let cipher = match material {
            NvstSrtpMaterial::AeadAes128Gcm {
                master_key,
                master_salt,
            } => SrtpCipher::AeadAes128Gcm {
                encryption_key: derive_aes128_cm_key::<16>(master_key, master_salt, 0x00),
                session_salt: derive_aes128_cm_key::<12>(master_key, master_salt, 0x02),
            },
            NvstSrtpMaterial::AeadAes256Gcm {
                master_key,
                master_salt,
            } => SrtpCipher::AeadAes256Gcm {
                encryption_key: derive_aes_cm_key::<32>(master_key, master_salt, 0x00),
                session_salt: derive_aes_cm_key::<12>(master_key, master_salt, 0x02),
            },
            NvstSrtpMaterial::AesCm128HmacSha1 {
                master_key,
                master_salt,
                authentication_tag_len,
            } => SrtpCipher::AesCm128HmacSha1 {
                encryption_key: derive_aes128_cm_key::<16>(master_key, master_salt, 0x00),
                authentication_key: derive_aes128_cm_key::<20>(master_key, master_salt, 0x01),
                session_salt: derive_aes128_cm_key::<14>(master_key, master_salt, 0x02),
                authentication_tag_len: *authentication_tag_len,
            },
        };
        Self {
            cipher,
            replay: ReplayWindow::default(),
        }
    }

    fn unprotect(&mut self, datagram: &[u8]) -> Result<RtpPacket, NvstDropReason> {
        let header = RtpHeader::parse(datagram).map_err(NvstDropReason::MalformedRtp)?;
        let packet_index = self.replay.guess_packet_index(header.sequence_number)?;
        let roc = u32::try_from(packet_index >> 16).map_err(|_| NvstDropReason::ReplayRejected)?;
        let plaintext = match &self.cipher {
            SrtpCipher::AeadAes128Gcm {
                encryption_key,
                session_salt,
            } => unprotect_aead_aes_128_gcm(datagram, header, roc, encryption_key, session_salt)?,
            SrtpCipher::AeadAes256Gcm {
                encryption_key,
                session_salt,
            } => unprotect_aead_aes_256_gcm(datagram, header, roc, encryption_key, session_salt)?,
            cipher @ SrtpCipher::AesCm128HmacSha1 { .. } => {
                unprotect_aes_cm_hmac_sha1(datagram, header, packet_index, roc, cipher)?
            }
        };
        self.replay.check(packet_index)?;
        header
            .payload(&plaintext)
            .map_err(NvstDropReason::MalformedRtp)?;
        self.replay.commit(packet_index);
        Ok(RtpPacket {
            index: packet_index,
            header,
            plaintext,
        })
    }
}

fn unprotect_aead_aes_256_gcm(
    datagram: &[u8],
    header: RtpHeader,
    roc: u32,
    encryption_key: &[u8; 32],
    session_salt: &[u8; 12],
) -> Result<Vec<u8>, NvstDropReason> {
    let ciphertext_end = datagram
        .len()
        .checked_sub(SRTP_AEAD_AES_GCM_TAG_LEN)
        .ok_or(NvstDropReason::MalformedRtp(
            RtpParseError::MissingAuthenticationTag,
        ))?;
    if ciphertext_end < header.payload_offset {
        return Err(NvstDropReason::MalformedRtp(
            RtpParseError::MissingAuthenticationTag,
        ));
    }
    let mut plaintext = datagram[..ciphertext_end].to_vec();
    let iv = srtp_gcm_iv(*session_salt, header.ssrc, roc, header.sequence_number);
    let cipher = <Aes256Gcm as aes_gcm::aead::KeyInit>::new_from_slice(encryption_key)
        .expect("AES-256-GCM accepts a fixed-size key");
    cipher
        .decrypt_in_place_detached(
            Nonce::from_slice(&iv),
            &datagram[..header.payload_offset],
            &mut plaintext[header.payload_offset..],
            Tag::from_slice(&datagram[ciphertext_end..]),
        )
        .map_err(|_| NvstDropReason::AuthenticationFailed)?;
    Ok(plaintext)
}

fn unprotect_aead_aes_128_gcm(
    datagram: &[u8],
    header: RtpHeader,
    roc: u32,
    encryption_key: &[u8; 16],
    session_salt: &[u8; 12],
) -> Result<Vec<u8>, NvstDropReason> {
    let ciphertext_end = datagram
        .len()
        .checked_sub(SRTP_AEAD_AES_GCM_TAG_LEN)
        .ok_or(NvstDropReason::MalformedRtp(
            RtpParseError::MissingAuthenticationTag,
        ))?;
    if ciphertext_end < header.payload_offset {
        return Err(NvstDropReason::MalformedRtp(
            RtpParseError::MissingAuthenticationTag,
        ));
    }
    let mut plaintext = datagram[..ciphertext_end].to_vec();
    let iv = srtp_gcm_iv(*session_salt, header.ssrc, roc, header.sequence_number);
    let cipher = <Aes128Gcm as aes_gcm::aead::KeyInit>::new_from_slice(encryption_key)
        .expect("AES-128-GCM accepts a fixed-size key");
    cipher
        .decrypt_in_place_detached(
            Nonce::from_slice(&iv),
            &datagram[..header.payload_offset],
            &mut plaintext[header.payload_offset..],
            Tag::from_slice(&datagram[ciphertext_end..]),
        )
        .map_err(|_| NvstDropReason::AuthenticationFailed)?;
    Ok(plaintext)
}

fn unprotect_aes_cm_hmac_sha1(
    datagram: &[u8],
    header: RtpHeader,
    packet_index: u64,
    roc: u32,
    cipher: &SrtpCipher,
) -> Result<Vec<u8>, NvstDropReason> {
    let SrtpCipher::AesCm128HmacSha1 {
        encryption_key,
        authentication_key,
        session_salt,
        authentication_tag_len,
    } = cipher
    else {
        unreachable!("AES-CM helper requires an AES-CM cipher")
    };
    let authenticated_len =
        datagram
            .len()
            .checked_sub(*authentication_tag_len)
            .ok_or(NvstDropReason::MalformedRtp(
                RtpParseError::MissingAuthenticationTag,
            ))?;
    if authenticated_len < header.payload_offset {
        return Err(NvstDropReason::MalformedRtp(
            RtpParseError::MissingAuthenticationTag,
        ));
    }
    let mut mac =
        HmacSha1::new_from_slice(authentication_key).expect("HMAC-SHA1 accepts a fixed-size key");
    mac.update(&datagram[..authenticated_len]);
    mac.update(&roc.to_be_bytes());
    let expected_tag = mac.finalize().into_bytes();
    let received_tag = &datagram[authenticated_len..];
    if expected_tag[..*authentication_tag_len]
        .ct_eq(received_tag)
        .unwrap_u8()
        != 1
    {
        return Err(NvstDropReason::AuthenticationFailed);
    }
    let mut plaintext = datagram[..authenticated_len].to_vec();
    let iv = srtp_aes_cm_iv(session_salt, header.ssrc, packet_index);
    let mut cipher = Aes128Ctr::new(encryption_key.into(), (&iv).into());
    cipher.apply_keystream(&mut plaintext[header.payload_offset..]);
    Ok(plaintext)
}

fn derive_aes_cm_key<const N: usize>(
    master_key: &[u8; 32],
    master_salt: &[u8],
    label: u8,
) -> [u8; N] {
    let mut iv = [0_u8; 16];
    iv[..master_salt.len()].copy_from_slice(master_salt);
    // RFC 3711 key_id = label * 2^48, then x = master_salt * 2^16 XOR key_id * 2^16.
    iv[7] ^= label;
    let mut output = [0_u8; N];
    let mut cipher = Aes256Ctr::new(master_key.into(), (&iv).into());
    cipher.apply_keystream(&mut output);
    output
}

fn derive_aes128_cm_key<const N: usize>(
    master_key: &[u8; 16],
    master_salt: &[u8],
    label: u8,
) -> [u8; N] {
    let mut iv = [0_u8; 16];
    iv[..master_salt.len()].copy_from_slice(master_salt);
    iv[7] ^= label;
    let mut output = [0_u8; N];
    let mut cipher = Aes128Ctr::new(master_key.into(), (&iv).into());
    cipher.apply_keystream(&mut output);
    output
}

fn srtp_aes_cm_iv(session_salt: &[u8; 14], ssrc: u32, packet_index: u64) -> [u8; 16] {
    let mut iv = [0_u8; 16];
    iv[..14].copy_from_slice(session_salt);
    for (target, source) in iv[4..8].iter_mut().zip(ssrc.to_be_bytes()) {
        *target ^= source;
    }
    let index_bytes = packet_index.to_be_bytes();
    for (target, source) in iv[8..14].iter_mut().zip(&index_bytes[2..]) {
        *target ^= source;
    }
    iv
}

fn srtp_gcm_iv(session_salt: [u8; 12], ssrc: u32, roc: u32, sequence_number: u16) -> [u8; 12] {
    let mut iv = session_salt;
    for (target, source) in iv[2..6].iter_mut().zip(ssrc.to_be_bytes()) {
        *target ^= source;
    }
    for (target, source) in iv[6..10].iter_mut().zip(roc.to_be_bytes()) {
        *target ^= source;
    }
    for (target, source) in iv[10..].iter_mut().zip(sequence_number.to_be_bytes()) {
        *target ^= source;
    }
    iv
}

#[derive(Clone, Default)]
struct ReplayWindow {
    highest_index: Option<u64>,
    seen: u64,
}

impl ReplayWindow {
    fn guess_packet_index(&self, sequence_number: u16) -> Result<u64, NvstDropReason> {
        let Some(highest_index) = self.highest_index else {
            return Ok(u64::from(sequence_number));
        };
        let roc = highest_index >> 16;
        let highest_sequence = (highest_index & 0xffff) as u16;
        let delta = i32::from(sequence_number) - i32::from(highest_sequence);
        let guessed_roc = if delta < -32_768 {
            roc.checked_add(1).ok_or(NvstDropReason::ReplayRejected)?
        } else if delta > 32_768 {
            roc.checked_sub(1).ok_or(NvstDropReason::ReplayRejected)?
        } else {
            roc
        };
        Ok((guessed_roc << 16) | u64::from(sequence_number))
    }

    fn check(&self, index: u64) -> Result<(), NvstDropReason> {
        let Some(highest_index) = self.highest_index else {
            return Ok(());
        };
        if index > highest_index {
            return Ok(());
        }
        let age = highest_index - index;
        if age >= 64 || self.seen & (1_u64 << age) != 0 {
            return Err(NvstDropReason::ReplayRejected);
        }
        Ok(())
    }

    fn commit(&mut self, index: u64) {
        match self.highest_index {
            None => {
                self.highest_index = Some(index);
                self.seen = 1;
            }
            Some(highest_index) if index > highest_index => {
                let advance = index - highest_index;
                self.seen = if advance >= 64 {
                    1
                } else {
                    (self.seen << advance) | 1
                };
                self.highest_index = Some(index);
            }
            Some(highest_index) => {
                self.seen |= 1_u64 << (highest_index - index);
            }
        }
    }
}

#[derive(Debug, Clone, Copy)]
struct NvVideoPacket {
    stream_packet_index: u32,
    frame_index: u32,
    flags: u8,
}

impl NvVideoPacket {
    fn parse(payload: &[u8]) -> Result<(Self, &[u8]), RtpParseError> {
        if payload.len() < NV_VIDEO_PACKET_LEN {
            return Err(RtpParseError::MissingNvVideoHeader);
        }
        let header = Self {
            stream_packet_index: u32::from_le_bytes(
                payload[0..4].try_into().expect("length checked"),
            ),
            frame_index: u32::from_le_bytes(payload[4..8].try_into().expect("length checked")),
            flags: payload[8],
        };
        Ok((header, &payload[NV_VIDEO_PACKET_LEN..]))
    }

    fn contains_picture_data(self) -> bool {
        self.flags & FLAG_CONTAINS_PIC_DATA != 0
    }

    fn is_start_of_frame(self) -> bool {
        self.flags & FLAG_SOF != 0
    }

    fn is_end_of_frame(self) -> bool {
        self.flags & FLAG_EOF != 0
    }
}

struct H264AccessUnitAssembler {
    current_frame: Option<u32>,
    first_stream_packet_index: Option<u32>,
    bytes: Vec<u8>,
    max_access_unit_bytes: usize,
}

impl H264AccessUnitAssembler {
    fn new(max_access_unit_bytes: usize) -> Self {
        Self {
            current_frame: None,
            first_stream_packet_index: None,
            bytes: Vec::new(),
            max_access_unit_bytes,
        }
    }

    fn reset(&mut self) {
        self.current_frame = None;
        self.first_stream_packet_index = None;
        self.bytes.clear();
    }

    fn push(
        &mut self,
        header: NvVideoPacket,
        timestamp: u32,
        payload: &[u8],
    ) -> Result<Option<EncodedH264Frame>, NvstDropReason> {
        if !header.contains_picture_data() {
            return Err(NvstDropReason::Unsupported(
                NvstUnsupportedFeature::FecRepair,
            ));
        }
        if header.is_start_of_frame() {
            self.reset();
            if !starts_with_annex_b_start_code(payload) {
                return Err(NvstDropReason::MissingAnnexBStartCode);
            }
            self.current_frame = Some(header.frame_index);
            self.first_stream_packet_index = Some(header.stream_packet_index);
        } else if self.current_frame != Some(header.frame_index) {
            self.reset();
            return Err(NvstDropReason::AwaitingStartOfFrame);
        }

        let remaining = self.max_access_unit_bytes.saturating_sub(self.bytes.len());
        if payload.len() > remaining {
            self.reset();
            return Err(NvstDropReason::AccessUnitTooLarge {
                limit: self.max_access_unit_bytes,
            });
        }
        self.bytes.extend_from_slice(payload);
        if !header.is_end_of_frame() {
            return Ok(None);
        }

        let bytes = std::mem::take(&mut self.bytes);
        self.current_frame = None;
        let first_stream_packet_index = self
            .first_stream_packet_index
            .take()
            .expect("start-of-frame initializes the packet index");
        Ok(Some(EncodedH264Frame {
            timestamp,
            frame_index: header.frame_index,
            first_stream_packet_index,
            keyframe: h264_access_unit_is_keyframe(&bytes),
            bytes,
        }))
    }
}

fn starts_with_annex_b_start_code(payload: &[u8]) -> bool {
    payload.starts_with(&[0, 0, 1]) || payload.starts_with(&[0, 0, 0, 1])
}

fn h264_access_unit_is_keyframe(bytes: &[u8]) -> bool {
    let mut offset = 0;
    while let Some((start, prefix_len)) = find_annex_b_start_code(&bytes[offset..]) {
        let nal_start = offset + start + prefix_len;
        if let Some(nal_header) = bytes.get(nal_start)
            && nal_header & 0x1f == 5
        {
            return true;
        }
        offset = nal_start;
    }
    false
}

fn find_annex_b_start_code(bytes: &[u8]) -> Option<(usize, usize)> {
    bytes
        .windows(4)
        .position(|window| window == [0, 0, 0, 1])
        .map_or_else(
            || {
                bytes
                    .windows(3)
                    .position(|window| window == [0, 0, 1])
                    .map(|position| (position, 3))
            },
            |position| Some((position, 4)),
        )
}

struct RtpReorderBuffer {
    next_index: Option<u64>,
    packets: BTreeMap<u64, RtpPacket>,
    max_packets: usize,
}

struct ReorderResult {
    ready: Vec<RtpPacket>,
    recovery: Option<NvstRecovery>,
    dropped: Option<NvstDropReason>,
}

impl RtpReorderBuffer {
    fn new(max_packets: usize) -> Self {
        Self {
            next_index: None,
            packets: BTreeMap::new(),
            max_packets,
        }
    }

    fn reset(&mut self) {
        self.next_index = None;
        self.packets.clear();
    }

    fn push(&mut self, packet: RtpPacket) -> ReorderResult {
        let index = packet.index;
        let next_index = *self.next_index.get_or_insert(index);
        if index < next_index {
            return ReorderResult {
                ready: Vec::new(),
                recovery: None,
                dropped: Some(NvstDropReason::StaleRtpPacket { index }),
            };
        }
        if self.packets.contains_key(&index) {
            return ReorderResult {
                ready: Vec::new(),
                recovery: None,
                dropped: Some(NvstDropReason::DuplicateRtpPacket { index }),
            };
        }

        let mut recovery = None;
        if index.saturating_sub(next_index) >= self.max_packets as u64 {
            recovery = Some(NvstRecovery::PacketGap {
                first_missing_index: next_index,
                last_missing_index: index - 1,
                nack: NvstUnsupportedFeature::Nack,
            });
            self.packets.clear();
            self.next_index = Some(index);
        }
        self.packets.insert(index, packet);

        if self.packets.len() >= self.max_packets {
            let first_available = *self
                .packets
                .first_key_value()
                .expect("non-empty after insertion")
                .0;
            let expected = self.next_index.expect("next index set above");
            if first_available > expected {
                recovery = Some(NvstRecovery::PacketGap {
                    first_missing_index: expected,
                    last_missing_index: first_available - 1,
                    nack: NvstUnsupportedFeature::Nack,
                });
                self.next_index = Some(first_available);
            }
        }

        let mut ready = Vec::new();
        while let Some(next) = self.next_index {
            let Some(packet) = self.packets.remove(&next) else {
                break;
            };
            ready.push(packet);
            self.next_index = Some(next + 1);
        }
        ReorderResult {
            ready,
            recovery,
            dropped: None,
        }
    }
}

/// Stateful, non-blocking NVST video receiver. `process_datagram` is deterministic and testable;
/// `spawn_nvst_udp_receiver` below is a thin UDP/thread wrapper for the production path.
pub struct NvstVideoReceiver {
    config: NvstVideoConfig,
    srtp: SrtpReceiver,
    reorder: RtpReorderBuffer,
    assembler: H264AccessUnitAssembler,
    state: NvstReceiverState,
    bound_ssrc: Option<u32>,
    timeout_origin: Instant,
    last_authenticated_packet: Option<Instant>,
}

impl NvstVideoReceiver {
    pub fn new(config: NvstVideoConfig) -> Self {
        let srtp = SrtpReceiver::from_material(&config.srtp);
        let reorder = RtpReorderBuffer::new(config.reorder_window_packets);
        let assembler = H264AccessUnitAssembler::new(config.max_access_unit_bytes);
        Self {
            config,
            srtp,
            reorder,
            assembler,
            state: NvstReceiverState::Running,
            bound_ssrc: None,
            timeout_origin: Instant::now(),
            last_authenticated_packet: None,
        }
    }

    pub fn state(&self) -> NvstReceiverState {
        self.state
    }

    pub fn pause(&mut self) -> Option<NvstReceiveEvent> {
        if self.state != NvstReceiverState::Running {
            return None;
        }
        self.reset_media_state();
        self.state = NvstReceiverState::Paused;
        Some(NvstReceiveEvent::Lifecycle(self.state))
    }

    pub fn resume(&mut self) -> Option<NvstReceiveEvent> {
        if self.state != NvstReceiverState::Paused {
            return None;
        }
        self.reset_media_state();
        self.timeout_origin = Instant::now();
        self.state = NvstReceiverState::Running;
        Some(NvstReceiveEvent::Lifecycle(self.state))
    }

    pub fn recover(&mut self) -> Option<NvstReceiveEvent> {
        if self.state != NvstReceiverState::RecoveryRequired {
            return None;
        }
        self.reset_media_state();
        self.timeout_origin = Instant::now();
        self.state = NvstReceiverState::Running;
        Some(NvstReceiveEvent::Lifecycle(self.state))
    }

    pub fn stop(&mut self) -> Option<NvstReceiveEvent> {
        if self.state == NvstReceiverState::Stopped {
            return None;
        }
        self.reset_media_state();
        self.state = NvstReceiverState::Stopped;
        Some(NvstReceiveEvent::Lifecycle(self.state))
    }

    /// Returns a typed timeout only once. The caller must deliberately call `recover` before
    /// more media is accepted, preventing a stale stream from silently resuming.
    pub fn poll_timeout(&mut self, now: Instant) -> Option<NvstReceiveEvent> {
        if self.state != NvstReceiverState::Running {
            return None;
        }
        let last_packet = self
            .last_authenticated_packet
            .unwrap_or(self.timeout_origin);
        let idle_for = now.saturating_duration_since(last_packet);
        if idle_for < self.config.timeout {
            return None;
        }
        self.reset_media_state();
        self.state = NvstReceiverState::RecoveryRequired;
        Some(NvstReceiveEvent::RecoveryNeeded(NvstRecovery::Timeout {
            idle_for,
        }))
    }

    pub fn process_datagram(
        &mut self,
        source: SocketAddr,
        datagram: &[u8],
        now: Instant,
    ) -> Vec<NvstReceiveEvent> {
        if source != self.config.video_peer {
            return vec![NvstReceiveEvent::Dropped(
                NvstDropReason::UnexpectedSource {
                    expected: self.config.video_peer,
                    actual: source,
                },
            )];
        }
        match self.state {
            NvstReceiverState::Paused => {
                return vec![NvstReceiveEvent::Dropped(NvstDropReason::Paused)];
            }
            NvstReceiverState::Stopped => {
                return vec![NvstReceiveEvent::Dropped(NvstDropReason::Stopped)];
            }
            NvstReceiverState::RecoveryRequired => {
                return vec![NvstReceiveEvent::Dropped(NvstDropReason::RecoveryRequired)];
            }
            NvstReceiverState::Running => {}
        }
        let packet = match self.srtp.unprotect(datagram) {
            Ok(packet) => packet,
            Err(reason) => return vec![NvstReceiveEvent::Dropped(reason)],
        };
        if let Some(expected) = self.config.expected_payload_type
            && packet.header.payload_type != expected
        {
            return vec![NvstReceiveEvent::Dropped(
                NvstDropReason::UnexpectedPayloadType {
                    expected,
                    actual: packet.header.payload_type,
                },
            )];
        }
        let expected_ssrc = self.config.expected_ssrc.or(self.bound_ssrc);
        if let Some(expected) = expected_ssrc
            && packet.header.ssrc != expected
        {
            return vec![NvstReceiveEvent::Dropped(NvstDropReason::UnexpectedSsrc {
                expected,
                actual: packet.header.ssrc,
            })];
        }
        self.bound_ssrc.get_or_insert(packet.header.ssrc);
        self.last_authenticated_packet = Some(now);

        let result = self.reorder.push(packet);
        let mut events = Vec::new();
        if let Some(reason) = result.dropped {
            events.push(NvstReceiveEvent::Dropped(reason));
        }
        if let Some(recovery) = result.recovery {
            self.assembler.reset();
            events.push(NvstReceiveEvent::RecoveryNeeded(recovery));
        }
        for packet in result.ready {
            let payload = match packet.header.payload(&packet.plaintext) {
                Ok(payload) => payload,
                Err(error) => {
                    events.push(NvstReceiveEvent::Dropped(NvstDropReason::MalformedRtp(
                        error,
                    )));
                    continue;
                }
            };
            let (nv_packet, media) = match NvVideoPacket::parse(payload) {
                Ok(value) => value,
                Err(error) => {
                    events.push(NvstReceiveEvent::Dropped(NvstDropReason::MalformedRtp(
                        error,
                    )));
                    continue;
                }
            };
            match self
                .assembler
                .push(nv_packet, packet.header.timestamp, media)
            {
                Ok(Some(frame)) => events.push(NvstReceiveEvent::Frame(frame)),
                Ok(None) => {}
                Err(reason) => events.push(NvstReceiveEvent::Dropped(reason)),
            }
        }
        events
    }

    fn reset_media_state(&mut self) {
        self.reorder.reset();
        self.assembler.reset();
    }
}

enum UdpReceiverCommand {
    Pause,
    Resume,
    Recover,
    Stop,
}

/// Owns the bounded UDP receive worker. Frames go through the same bounded `MediaConsumer` used
/// by WebRTC, so a slow decoder cannot make UDP receive unbounded.
pub struct NvstUdpReceiverSession {
    commands: Sender<UdpReceiverCommand>,
    join: Option<JoinHandle<()>>,
}

#[derive(Debug, Error)]
pub enum NvstUdpReceiverError {
    #[error("failed to bind NVST UDP socket: {0}")]
    Bind(#[source] std::io::Error),
    #[error("failed to configure NVST UDP socket: {0}")]
    Configure(#[source] std::io::Error),
    #[error("failed to start NVST receive worker: {0}")]
    Spawn(#[source] std::io::Error),
    #[error("NVST receive worker is no longer running")]
    Closed,
}

impl NvstUdpReceiverSession {
    pub fn pause(&self) -> Result<(), NvstUdpReceiverError> {
        self.send(UdpReceiverCommand::Pause)
    }

    pub fn resume(&self) -> Result<(), NvstUdpReceiverError> {
        self.send(UdpReceiverCommand::Resume)
    }

    pub fn recover(&self) -> Result<(), NvstUdpReceiverError> {
        self.send(UdpReceiverCommand::Recover)
    }

    pub fn stop(mut self) {
        let _ = self.commands.send(UdpReceiverCommand::Stop);
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
    }

    fn send(&self, command: UdpReceiverCommand) -> Result<(), NvstUdpReceiverError> {
        self.commands
            .send(command)
            .map_err(|_| NvstUdpReceiverError::Closed)
    }
}

impl Drop for NvstUdpReceiverSession {
    fn drop(&mut self) {
        let _ = self.commands.send(UdpReceiverCommand::Stop);
    }
}

pub fn spawn_nvst_udp_receiver(
    config: NvstVideoConfig,
    media_consumer: MediaConsumer,
    event_sender: Sender<NvstReceiveEvent>,
) -> Result<NvstUdpReceiverSession, NvstUdpReceiverError> {
    let bind_ip = match config.video_peer.ip() {
        IpAddr::V4(_) => IpAddr::V4(Ipv4Addr::UNSPECIFIED),
        IpAddr::V6(_) => IpAddr::V6(Ipv6Addr::UNSPECIFIED),
    };
    let socket = UdpSocket::bind(SocketAddr::new(bind_ip, config.client_udp_port))
        .map_err(NvstUdpReceiverError::Bind)?;
    socket
        .set_read_timeout(Some(Duration::from_millis(50)))
        .map_err(NvstUdpReceiverError::Configure)?;
    let _ = socket.send_to(&config.ping_payload, config.video_peer);
    let (commands, receiver) = mpsc::channel();
    let transport_origin = Instant::now();
    let join = thread::Builder::new()
        .name("opennow-nvst-video".to_owned())
        .spawn(move || {
            run_nvst_udp_receiver(
                socket,
                config,
                receiver,
                media_consumer,
                event_sender,
                transport_origin,
            )
        })
        .map_err(NvstUdpReceiverError::Spawn)?;
    Ok(NvstUdpReceiverSession {
        commands,
        join: Some(join),
    })
}

fn run_nvst_udp_receiver(
    socket: UdpSocket,
    config: NvstVideoConfig,
    commands: Receiver<UdpReceiverCommand>,
    media_consumer: MediaConsumer,
    event_sender: Sender<NvstReceiveEvent>,
    transport_origin: Instant,
) {
    let mut receiver = NvstVideoReceiver::new(config);
    let mut datagram = vec![0_u8; 65_536];
    loop {
        loop {
            match commands.try_recv() {
                Ok(UdpReceiverCommand::Pause) => forward_optional(&event_sender, receiver.pause()),
                Ok(UdpReceiverCommand::Resume) => {
                    forward_optional(&event_sender, receiver.resume())
                }
                Ok(UdpReceiverCommand::Recover) => {
                    forward_optional(&event_sender, receiver.recover())
                }
                Ok(UdpReceiverCommand::Stop) | Err(TryRecvError::Disconnected) => {
                    forward_optional(&event_sender, receiver.stop());
                    return;
                }
                Err(TryRecvError::Empty) => break,
            }
        }

        match socket.recv_from(&mut datagram) {
            Ok((length, source)) => {
                for event in receiver.process_datagram(source, &datagram[..length], Instant::now())
                {
                    if !forward_receive_event(
                        &media_consumer,
                        &event_sender,
                        transport_origin,
                        event,
                    ) {
                        forward_optional(&event_sender, receiver.stop());
                        return;
                    }
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::TimedOut => {}
            Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {}
            Err(_) => {
                forward_optional(&event_sender, receiver.stop());
                return;
            }
        }
        forward_optional(&event_sender, receiver.poll_timeout(Instant::now()));
    }
}

fn forward_optional(sender: &Sender<NvstReceiveEvent>, event: Option<NvstReceiveEvent>) {
    if let Some(event) = event {
        let _ = sender.send(event);
    }
}

fn forward_receive_event(
    media_consumer: &MediaConsumer,
    event_sender: &Sender<NvstReceiveEvent>,
    transport_origin: Instant,
    event: NvstReceiveEvent,
) -> bool {
    if let NvstReceiveEvent::Frame(frame) = event {
        let media_frame = EncodedMediaFrame {
            mid: "nvst-video-0".to_owned(),
            codec: "H264".to_owned(),
            payload: Arc::from(frame.bytes),
            rtp_timestamp: u64::from(frame.timestamp),
            clock_rate_hz: 90_000,
            received_at_us: transport_origin
                .elapsed()
                .as_micros()
                .try_into()
                .unwrap_or(u64::MAX),
            keyframe: frame.keyframe,
            contiguous: true,
        };
        let result = match deliver_media_frame(media_consumer, media_frame) {
            Ok(()) => return true,
            Err(TransportError::MediaConsumerBackpressured) => {
                NvstDropReason::MediaConsumerBackpressured
            }
            Err(TransportError::MediaConsumerClosed) => NvstDropReason::MediaConsumerClosed,
            Err(_) => NvstDropReason::MediaConsumerClosed,
        };
        let _ = event_sender.send(NvstReceiveEvent::Dropped(result));
        return false;
    }
    let _ = event_sender.send(event);
    true
}

/// There is no safe NVST NACK wire encoder in the current handoff. Expose this explicit answer
/// rather than emitting an invented control datagram.
pub fn nack_transmission_support() -> Result<(), NvstUnsupportedFeature> {
    Err(NvstUnsupportedFeature::Nack)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    const TEST_KEY: &str = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    const TEST_SALT: &str = "000102030405060708090A0B0C0D";
    const TEST_PEER: &str = "192.0.2.20";

    fn legacy_handoff() -> Value {
        json!({
            "clientUdpPort": 49005,
            "videoPeerIp": TEST_PEER,
            "videoPeerPort": 5004,
            "srtpAesKeyHex": TEST_KEY,
            "srtpSaltHex": "00000000000000009ECA935E",
            "codec": "H264",
            "rtpPayloadType": 96,
            "rtpSsrc": 0x11223344u32,
            "reorderWindowPackets": 4,
            "maxAccessUnitBytes": 4096,
            "timeoutMs": 500
        })
    }

    fn config() -> NvstVideoConfig {
        NvstVideoConfig::from_legacy_handoff(&legacy_handoff(), None).expect("valid config")
    }

    fn peer() -> SocketAddr {
        SocketAddr::new(TEST_PEER.parse().expect("test IP"), 5004)
    }

    fn build_plaintext_rtp(sequence: u16, flags: u8, frame_index: u32, media: &[u8]) -> Vec<u8> {
        let mut packet = vec![0x80, 0xe0];
        packet.extend_from_slice(&sequence.to_be_bytes());
        packet.extend_from_slice(&0x01020304u32.to_be_bytes());
        packet.extend_from_slice(&0x11223344u32.to_be_bytes());
        packet.extend_from_slice(&u32::from(sequence).to_le_bytes());
        packet.extend_from_slice(&frame_index.to_le_bytes());
        packet.push(flags);
        packet.extend_from_slice(&[0, 0, 0]);
        packet.extend_from_slice(&[0; 4]);
        packet.extend_from_slice(media);
        packet
    }

    fn test_srtp(config: &NvstVideoConfig) -> SrtpReceiver {
        SrtpReceiver::from_material(&config.srtp)
    }

    fn protect_for_test(crypto: &SrtpReceiver, mut packet: Vec<u8>, roc: u32) -> Vec<u8> {
        let header = RtpHeader::parse(&packet).expect("RTP header");
        let packet_index = (u64::from(roc) << 16) | u64::from(header.sequence_number);
        match &crypto.cipher {
            SrtpCipher::AeadAes128Gcm {
                encryption_key,
                session_salt,
            } => {
                let iv = srtp_gcm_iv(*session_salt, header.ssrc, roc, header.sequence_number);
                let cipher = <Aes128Gcm as aes_gcm::aead::KeyInit>::new_from_slice(encryption_key)
                    .expect("fixed key");
                let (aad, payload) = packet.split_at_mut(header.payload_offset);
                let tag = cipher
                    .encrypt_in_place_detached(Nonce::from_slice(&iv), aad, payload)
                    .expect("AES-GCM encryption");
                packet.extend_from_slice(&tag);
            }
            SrtpCipher::AeadAes256Gcm {
                encryption_key,
                session_salt,
            } => {
                let iv = srtp_gcm_iv(*session_salt, header.ssrc, roc, header.sequence_number);
                let cipher = <Aes256Gcm as aes_gcm::aead::KeyInit>::new_from_slice(encryption_key)
                    .expect("fixed key");
                let (aad, payload) = packet.split_at_mut(header.payload_offset);
                let tag = cipher
                    .encrypt_in_place_detached(Nonce::from_slice(&iv), aad, payload)
                    .expect("AES-GCM encryption");
                packet.extend_from_slice(&tag);
            }
            SrtpCipher::AesCm128HmacSha1 {
                encryption_key,
                authentication_key,
                session_salt,
                authentication_tag_len,
            } => {
                let iv = srtp_aes_cm_iv(session_salt, header.ssrc, packet_index);
                let mut cipher = Aes128Ctr::new(encryption_key.into(), (&iv).into());
                cipher.apply_keystream(&mut packet[header.payload_offset..]);
                let mut mac = HmacSha1::new_from_slice(authentication_key).expect("fixed key");
                mac.update(&packet);
                mac.update(&roc.to_be_bytes());
                packet.extend_from_slice(&mac.finalize().into_bytes()[..*authentication_tag_len]);
            }
        }
        packet
    }

    #[test]
    fn legacy_schema_defaults_to_aes_256_gcm_with_explicit_salt() {
        let config = config();
        assert_eq!(config.video_peer(), peer());
        assert_eq!(config.srtp_profile(), NvstSrtpProfile::AeadAes256Gcm);
        let NvstSrtpMaterial::AeadAes256Gcm { master_salt, .. } = config.srtp else {
            panic!("legacy handoff must choose AES-256-GCM");
        };
        assert_eq!(
            master_salt,
            decode_fixed_hex::<12>("00000000000000009ECA935E", NvstConfigError::InvalidSrtpSalt)
                .expect("salt"),
        );
    }

    #[test]
    fn legacy_gcm_key_derivation_matches_known_answer() {
        let config = config();
        let receiver = test_srtp(&config);
        let SrtpCipher::AeadAes256Gcm {
            encryption_key,
            session_salt,
        } = receiver.cipher
        else {
            panic!("legacy handoff must derive an AES-256-GCM session");
        };
        assert_eq!(
            encryption_key,
            decode_fixed_hex::<32>(
                "0E44A0B0E7F1BDBB298CBEE52C9F8AC1C37726768C946F59BDAAA099608CBF66",
                NvstConfigError::InvalidAesKey,
            )
            .expect("key"),
        );
        assert_eq!(
            session_salt,
            decode_fixed_hex::<12>("78B9D97B7FFB37CAD539A29D", NvstConfigError::InvalidSrtpSalt)
                .expect("salt"),
        );
    }

    #[test]
    fn selector_prefers_valid_legacy_nvst_and_falls_back_for_h265() {
        let context = json!({ "nvstVideo": legacy_handoff() });
        assert!(matches!(
            select_preferred_video_transport(&context),
            PreferredVideoTransport::Nvst(_)
        ));
        assert!(matches!(
            select_preferred_video_transport(&json!({ "nvstTransport": { "tracks": [] } })),
            PreferredVideoTransport::WebRtcFallback(NvstFallbackReason::InvalidNvstHandoff(
                NvstConfigError::RichHandoffUnsupported
            ))
        ));

        let mut missing_gcm_salt = legacy_handoff();
        missing_gcm_salt
            .as_object_mut()
            .expect("handoff object")
            .remove("srtpSaltHex");
        assert!(matches!(
            NvstVideoConfig::from_legacy_handoff(&missing_gcm_salt, None),
            Err(NvstConfigError::MissingField("srtpSaltHex"))
        ));

        let mut invalid = legacy_handoff();
        invalid["codec"] = json!("H265");
        let context = json!({ "nvstVideo": invalid });
        assert!(matches!(
            select_preferred_video_transport(&context),
            PreferredVideoTransport::WebRtcFallback(NvstFallbackReason::InvalidNvstHandoff(
                NvstConfigError::UnsupportedCodec(_)
            ))
        ));

        let mut missing_cm_salt = legacy_handoff();
        missing_cm_salt
            .as_object_mut()
            .expect("handoff object")
            .remove("srtpSaltHex");
        missing_cm_salt["srtpProfile"] = json!("AES_CM_128_HMAC_SHA1_80");
        missing_cm_salt["srtpAesKeyHex"] = json!("000102030405060708090A0B0C0D0E0F");
        assert!(matches!(
            NvstVideoConfig::from_legacy_handoff(&missing_cm_salt, None),
            Err(NvstConfigError::MissingField("srtpSaltHex"))
        ));
        missing_cm_salt["srtpSaltHex"] = json!(TEST_SALT);
        let explicit_cm = NvstVideoConfig::from_legacy_handoff(&missing_cm_salt, None)
            .expect("explicit AES-CM profile");
        assert_eq!(
            explicit_cm.srtp_profile(),
            NvstSrtpProfile::AesCm128HmacSha1_80
        );

        let mut gcm_128 = legacy_handoff();
        gcm_128["srtpProfile"] = json!("AEAD_AES_128_GCM");
        gcm_128["srtpAesKeyHex"] = json!("000102030405060708090A0B0C0D0E0F");
        gcm_128["srtpSaltHex"] = json!("00000000000000009ECA935E");
        assert_eq!(
            NvstVideoConfig::from_legacy_handoff(&gcm_128, None)
                .expect("explicit AES-128-GCM profile")
                .srtp_profile(),
            NvstSrtpProfile::AeadAes128Gcm
        );

        let mut cm_32 = gcm_128;
        cm_32["srtpProfile"] = json!("AES_CM_128_HMAC_SHA1_32");
        cm_32["srtpSaltHex"] = json!(TEST_SALT);
        assert_eq!(
            NvstVideoConfig::from_legacy_handoff(&cm_32, None)
                .expect("explicit AES-CM-32 profile")
                .srtp_profile(),
            NvstSrtpProfile::AesCm128HmacSha1_32
        );
    }

    #[test]
    fn rejects_invalid_peer_and_secret_material() {
        let mut handoff = legacy_handoff();
        handoff["videoPeerIp"] = json!("0.0.0.0");
        assert!(matches!(
            NvstVideoConfig::from_legacy_handoff(&handoff, None),
            Err(NvstConfigError::InvalidPeerIp(_))
        ));
        let mut handoff = legacy_handoff();
        handoff["srtpAesKeyHex"] = json!("not-a-key");
        assert!(matches!(
            NvstVideoConfig::from_legacy_handoff(&handoff, None),
            Err(NvstConfigError::InvalidAesKey)
        ));

        let mut aes_128 = legacy_handoff();
        aes_128["srtpProfile"] = json!("AEAD_AES_128_GCM");
        aes_128["srtpAesKeyHex"] = json!("000102030405060708090A0B0C0D0E0G");
        assert!(matches!(
            NvstVideoConfig::from_legacy_handoff(&aes_128, None),
            Err(NvstConfigError::InvalidAesKey)
        ));
    }

    #[test]
    fn srtp_aes_cm_hmac_sha1_known_answer_unprotects_packet_when_explicitly_selected() {
        let key = decode_fixed_hex::<16>(
            "000102030405060708090A0B0C0D0E0F",
            NvstConfigError::InvalidAesKey,
        )
        .expect("key");
        let salt =
            decode_fixed_hex::<14>(TEST_SALT, NvstConfigError::InvalidSrtpSalt).expect("salt");
        let material = NvstSrtpMaterial::AesCm128HmacSha1 {
            master_key: key,
            master_salt: salt,
            authentication_tag_len: SRTP_AES_CM_HMAC_SHA1_80_TAG_LEN,
        };
        let crypto = SrtpReceiver::from_material(&material);
        let plaintext = build_plaintext_rtp(
            0x1234,
            FLAG_SOF | FLAG_EOF | FLAG_CONTAINS_PIC_DATA,
            7,
            &[0, 0, 0, 1, 0x65, 0x88],
        );
        let protected = protect_for_test(&crypto, plaintext.clone(), 0);
        assert_eq!(
            protected,
            hex_bytes(
                "80E01234010203041122334408BA995FCB62EA430BE6EDEF8D4DE06268F0D6D702702082DDFADA13AE83402B"
            ),
            "independent AES-CTR/HMAC known-answer vector",
        );
        let mut receiver = SrtpReceiver::from_material(&material);
        let unprotected = receiver.unprotect(&protected).expect("authenticated SRTP");
        assert_eq!(unprotected.plaintext, plaintext);
        assert_eq!(unprotected.index, 0x1234);

        let mut tampered = protected;
        let last = tampered.last_mut().expect("authentication tag");
        *last ^= 0x01;
        let mut receiver = SrtpReceiver::from_material(&material);
        assert!(matches!(
            receiver.unprotect(&tampered),
            Err(NvstDropReason::AuthenticationFailed)
        ));
    }

    #[test]
    fn srtp_aead_aes_256_gcm_rfc_known_answer_unprotects_and_rejects_tampering_and_replay() {
        let session_key =
            decode_fixed_hex::<32>(TEST_KEY, NvstConfigError::InvalidAesKey).expect("key");
        let session_salt =
            decode_fixed_hex::<12>("517569642070726F2071756F", NvstConfigError::InvalidSrtpSalt)
                .expect("salt");
        let mut receiver = SrtpReceiver {
            cipher: SrtpCipher::AeadAes256Gcm {
                encryption_key: session_key,
                session_salt,
            },
            replay: ReplayWindow::default(),
        };
        let protected = hex_bytes(
            "8040F17B8041F8D35501A0B232B1DE78A822FE12EF9F78FA332E33AAB18012389A58E2F3B50B2A0276FFAE0F1BA63799B87B7AA3DB36DFFFD6B0F9BB7878D7A76C13",
        );
        let expected = hex_bytes(
            "8040F17B8041F8D35501A0B247616C6C696120657374206F6D6E69732064697669736120696E207061727465732074726573",
        );
        let unprotected = receiver.unprotect(&protected).expect("RFC 7714 packet");
        assert_eq!(unprotected.plaintext, expected);
        assert!(matches!(
            receiver.unprotect(&protected),
            Err(NvstDropReason::ReplayRejected)
        ));

        let mut tampered = protected;
        tampered[20] ^= 0x01;
        let mut receiver = SrtpReceiver {
            cipher: SrtpCipher::AeadAes256Gcm {
                encryption_key: session_key,
                session_salt,
            },
            replay: ReplayWindow::default(),
        };
        assert!(matches!(
            receiver.unprotect(&tampered),
            Err(NvstDropReason::AuthenticationFailed)
        ));
    }

    #[test]
    fn srtp_aead_aes_128_gcm_rfc_known_answer_unprotects_and_rejects_tampering_and_replay() {
        let session_key = decode_fixed_hex::<16>(
            "000102030405060708090A0B0C0D0E0F",
            NvstConfigError::InvalidAesKey,
        )
        .expect("key");
        let session_salt =
            decode_fixed_hex::<12>("517569642070726F2071756F", NvstConfigError::InvalidSrtpSalt)
                .expect("salt");
        let mut receiver = SrtpReceiver {
            cipher: SrtpCipher::AeadAes128Gcm {
                encryption_key: session_key,
                session_salt,
            },
            replay: ReplayWindow::default(),
        };
        let protected = hex_bytes(
            "8040F17B8041F8D35501A0B2F24DE3A3FB34DE6CACBA861C9D7E4BCABE633BD50D294E6F42A5F47A51C7D19B36DE3ADF8833899D7F27BEB16A9152CF765EE4390CCE",
        );
        let expected = hex_bytes(
            "8040F17B8041F8D35501A0B247616C6C696120657374206F6D6E69732064697669736120696E207061727465732074726573",
        );
        let unprotected = receiver.unprotect(&protected).expect("RFC 7714 packet");
        assert_eq!(unprotected.plaintext, expected);
        assert!(matches!(
            receiver.unprotect(&protected),
            Err(NvstDropReason::ReplayRejected)
        ));

        let mut tampered = protected;
        tampered[20] ^= 0x01;
        let mut receiver = SrtpReceiver {
            cipher: SrtpCipher::AeadAes128Gcm {
                encryption_key: session_key,
                session_salt,
            },
            replay: ReplayWindow::default(),
        };
        assert!(matches!(
            receiver.unprotect(&tampered),
            Err(NvstDropReason::AuthenticationFailed)
        ));
    }

    #[test]
    fn parses_rtp_extensions_and_padding_after_srtp_unprotect() {
        let mut packet = vec![0xb0, 0x60, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1];
        packet.extend_from_slice(&[0xbe, 0xde, 0, 1, 0xaa, 0xbb, 0xcc, 0xdd]);
        packet.extend_from_slice(&[1, 2, 3, 4, 2, 2]);
        let header = RtpHeader::parse(&packet).expect("extension parsed");
        assert_eq!(header.payload_offset, 20);
        assert_eq!(
            header.payload(&packet).expect("padding removed"),
            &[1, 2, 3, 4]
        );
    }

    #[test]
    fn receiver_reorders_authenticated_packets_and_emits_annex_b_frame() {
        let config = config();
        let crypto = test_srtp(&config);
        let first = protect_for_test(
            &crypto,
            build_plaintext_rtp(
                10,
                FLAG_SOF | FLAG_CONTAINS_PIC_DATA,
                9,
                &[0, 0, 0, 1, 0x65],
            ),
            0,
        );
        let middle = protect_for_test(
            &crypto,
            build_plaintext_rtp(11, FLAG_CONTAINS_PIC_DATA, 9, &[0xaa]),
            0,
        );
        let last = protect_for_test(
            &crypto,
            build_plaintext_rtp(12, FLAG_EOF | FLAG_CONTAINS_PIC_DATA, 9, &[0xbb]),
            0,
        );
        let mut receiver = NvstVideoReceiver::new(config);
        assert!(
            receiver
                .process_datagram(peer(), &first, Instant::now())
                .is_empty()
        );
        assert!(
            receiver
                .process_datagram(peer(), &last, Instant::now())
                .is_empty()
        );
        let events = receiver.process_datagram(peer(), &middle, Instant::now());
        assert_eq!(events.len(), 1);
        let NvstReceiveEvent::Frame(frame) = &events[0] else {
            panic!("expected frame, got {events:?}");
        };
        assert_eq!(frame.frame_index, 9);
        assert!(frame.keyframe);
        assert_eq!(frame.bytes, [0, 0, 0, 1, 0x65, 0xaa, 0xbb]);
    }

    #[test]
    fn receiver_rejects_wrong_peer_and_duplicate_authenticated_packet() {
        let config = config();
        let crypto = test_srtp(&config);
        let packet = protect_for_test(
            &crypto,
            build_plaintext_rtp(
                1,
                FLAG_SOF | FLAG_EOF | FLAG_CONTAINS_PIC_DATA,
                1,
                &[0, 0, 1, 0x65],
            ),
            0,
        );
        let mut receiver = NvstVideoReceiver::new(config);
        let wrong_peer = SocketAddr::new("192.0.2.21".parse().expect("IP"), 5004);
        assert!(matches!(
            receiver
                .process_datagram(wrong_peer, &packet, Instant::now())
                .as_slice(),
            [NvstReceiveEvent::Dropped(
                NvstDropReason::UnexpectedSource { .. }
            )]
        ));
        assert!(matches!(
            receiver
                .process_datagram(peer(), &packet, Instant::now())
                .as_slice(),
            [NvstReceiveEvent::Frame(_)]
        ));
        assert!(matches!(
            receiver
                .process_datagram(peer(), &packet, Instant::now())
                .as_slice(),
            [NvstReceiveEvent::Dropped(NvstDropReason::ReplayRejected)]
        ));
    }

    #[test]
    fn gaps_require_recovery_and_never_emit_an_incomplete_frame() {
        let config = config();
        let crypto = test_srtp(&config);
        let first = protect_for_test(
            &crypto,
            build_plaintext_rtp(1, FLAG_SOF | FLAG_CONTAINS_PIC_DATA, 1, &[0, 0, 1, 0x65]),
            0,
        );
        let far = protect_for_test(
            &crypto,
            build_plaintext_rtp(6, FLAG_EOF | FLAG_CONTAINS_PIC_DATA, 1, &[0xaa]),
            0,
        );
        let mut receiver = NvstVideoReceiver::new(config);
        let _ = receiver.process_datagram(peer(), &first, Instant::now());
        let events = receiver.process_datagram(peer(), &far, Instant::now());
        assert!(events.iter().any(|event| matches!(
            event,
            NvstReceiveEvent::RecoveryNeeded(NvstRecovery::PacketGap {
                nack: NvstUnsupportedFeature::Nack,
                ..
            })
        )));
        assert!(
            !events
                .iter()
                .any(|event| matches!(event, NvstReceiveEvent::Frame(_)))
        );
        assert_eq!(
            nack_transmission_support(),
            Err(NvstUnsupportedFeature::Nack)
        );
    }

    #[test]
    fn pause_timeout_recovery_and_stop_fail_closed() {
        let mut receiver = NvstVideoReceiver::new(config());
        assert_eq!(
            receiver.pause(),
            Some(NvstReceiveEvent::Lifecycle(NvstReceiverState::Paused))
        );
        assert!(matches!(
            receiver
                .process_datagram(peer(), &[], Instant::now())
                .as_slice(),
            [NvstReceiveEvent::Dropped(NvstDropReason::Paused)]
        ));
        assert_eq!(
            receiver.resume(),
            Some(NvstReceiveEvent::Lifecycle(NvstReceiverState::Running))
        );
        receiver.last_authenticated_packet = Some(Instant::now() - Duration::from_secs(1));
        assert!(matches!(
            receiver.poll_timeout(Instant::now()),
            Some(NvstReceiveEvent::RecoveryNeeded(
                NvstRecovery::Timeout { .. }
            ))
        ));
        assert_eq!(receiver.state(), NvstReceiverState::RecoveryRequired);
        assert_eq!(
            receiver.recover(),
            Some(NvstReceiveEvent::Lifecycle(NvstReceiverState::Running))
        );
        assert_eq!(
            receiver.stop(),
            Some(NvstReceiveEvent::Lifecycle(NvstReceiverState::Stopped))
        );
        assert!(matches!(
            receiver
                .process_datagram(peer(), &[], Instant::now())
                .as_slice(),
            [NvstReceiveEvent::Dropped(NvstDropReason::Stopped)]
        ));
    }

    #[test]
    fn startup_without_authenticated_packets_times_out() {
        let mut receiver = NvstVideoReceiver::new(config());
        receiver.timeout_origin = Instant::now() - Duration::from_secs(1);

        assert!(matches!(
            receiver.poll_timeout(Instant::now()),
            Some(NvstReceiveEvent::RecoveryNeeded(
                NvstRecovery::Timeout { .. }
            ))
        ));
        assert_eq!(receiver.state(), NvstReceiverState::RecoveryRequired);
    }

    #[test]
    fn h264_frame_queue_is_bounded_and_prefers_current_frames() {
        let mut queue = BoundedFrameQueue::new(2);
        for frame_index in 1..=3 {
            queue.push(EncodedH264Frame {
                timestamp: frame_index,
                frame_index,
                first_stream_packet_index: frame_index,
                keyframe: false,
                bytes: vec![frame_index as u8],
            });
        }
        assert_eq!(queue.dropped_frames(), 1);
        assert_eq!(queue.pop().expect("frame").frame_index, 2);
        assert_eq!(queue.pop().expect("frame").frame_index, 3);
    }

    fn hex_bytes(value: &str) -> Vec<u8> {
        value
            .as_bytes()
            .chunks(2)
            .map(|chunk| {
                u8::from_str_radix(std::str::from_utf8(chunk).expect("hex"), 16).expect("hex")
            })
            .collect()
    }
}

/// In-process bounded queue for callers that drive `NvstVideoReceiver` directly rather than
/// using the UDP worker. It drops the oldest frame to keep interactive latency bounded.
#[derive(Debug)]
pub struct BoundedFrameQueue {
    frames: VecDeque<EncodedH264Frame>,
    capacity: usize,
    dropped_frames: u64,
}

impl BoundedFrameQueue {
    pub fn new(capacity: usize) -> Self {
        Self {
            frames: VecDeque::with_capacity(capacity),
            capacity: capacity.max(1),
            dropped_frames: 0,
        }
    }

    pub fn push(&mut self, frame: EncodedH264Frame) {
        if self.frames.len() == self.capacity {
            let _ = self.frames.pop_front();
            self.dropped_frames += 1;
        }
        self.frames.push_back(frame);
    }

    pub fn pop(&mut self) -> Option<EncodedH264Frame> {
        self.frames.pop_front()
    }

    pub fn dropped_frames(&self) -> u64 {
        self.dropped_frames
    }
}
