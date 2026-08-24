use std::fmt;
use std::time::{Duration, Instant};

use str0m::Rtc;
use str0m::channel::{ChannelConfig, ChannelId, Reliability};

pub(crate) const CONTROL_KEEPALIVE_INTERVAL: Duration = Duration::from_secs(3);
pub(crate) const INPUT_HANDSHAKE_TIMEOUT: Duration = Duration::from_secs(5);

const CONTROL_RELIABLE_LABEL: &str = "control_channel_reliable";
const CUSTOM_RELIABLE_LABEL: &str = "custom_message_on_sctp_private_reliable";
const CUSTOM_PARTIAL_LABEL: &str = "custom_message_on_sctp_private_partially_reliable";
const CONTROL_PARTIAL_LABEL: &str = "control_channel_partially_reliable";
const CONTROL_UNRELIABLE_LABEL: &str = "control_channel_unreliable";
const INPUT_PARTIAL_LABEL: &str = "input_channel_partially_reliable";
const PARTIAL_RELIABLE_LIFETIME_MS: u16 = 300;

const COMMAND_KEEPALIVE: u16 = 0x0200;
const COMMAND_REMOTE_INPUT: u16 = 0x0206;
const COMMAND_ENABLE_INPUT: u16 = 0x020b;
const COMMAND_GAMEPAD: u16 = 0x020d;
const COMMAND_INPUT_VERSION: u16 = 0x020e;
const COMMAND_READY: u16 = 0x0308;
const COMMAND_WINDOW_STATE: u16 = 0x0320;
const COMMAND_SYSTEM_STATE: u16 = 0x0321;

const INPUT_KEY_DOWN: u32 = 3;
const INPUT_KEY_UP: u32 = 4;
const INPUT_MOUSE_ABSOLUTE: u32 = 5;
const INPUT_MOUSE_RELATIVE: u32 = 7;
const INPUT_MOUSE_BUTTON_DOWN: u32 = 8;
const INPUT_MOUSE_BUTTON_UP: u32 = 9;
const INPUT_MOUSE_WHEEL: u32 = 10;
const INPUT_GAMEPAD: u32 = 12;
const INPUT_TEXT: u32 = 23;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum NvstChannelReliability {
    Reliable,
    Lifetime(u16),
    MaxRetransmits(u16),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct NvstChannelDefinition {
    pub(crate) sid: u16,
    pub(crate) label: &'static str,
    pub(crate) ordered: bool,
    pub(crate) reliability: NvstChannelReliability,
}

pub(crate) const NVST_CHANNEL_PROFILE: [NvstChannelDefinition; 6] = [
    NvstChannelDefinition {
        sid: 0,
        label: CONTROL_RELIABLE_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::Reliable,
    },
    NvstChannelDefinition {
        sid: 2,
        label: CUSTOM_RELIABLE_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::Reliable,
    },
    NvstChannelDefinition {
        sid: 4,
        label: CUSTOM_PARTIAL_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::Lifetime(PARTIAL_RELIABLE_LIFETIME_MS),
    },
    NvstChannelDefinition {
        sid: 6,
        label: CONTROL_PARTIAL_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::Lifetime(PARTIAL_RELIABLE_LIFETIME_MS),
    },
    NvstChannelDefinition {
        sid: 8,
        label: CONTROL_UNRELIABLE_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::MaxRetransmits(0),
    },
    NvstChannelDefinition {
        sid: 10,
        label: INPUT_PARTIAL_LABEL,
        ordered: true,
        reliability: NvstChannelReliability::Lifetime(PARTIAL_RELIABLE_LIFETIME_MS),
    },
];

#[derive(Debug, Clone, Copy)]
pub(crate) struct NvstInputChannels {
    pub(crate) control_reliable: ChannelId,
    pub(crate) custom_reliable: ChannelId,
    pub(crate) custom_partial: ChannelId,
    pub(crate) control_partial: ChannelId,
    pub(crate) control_unreliable: ChannelId,
    pub(crate) input_partial: ChannelId,
}

impl NvstInputChannels {
    pub(crate) fn create(rtc: &mut Rtc) -> Self {
        let mut ids = Vec::with_capacity(NVST_CHANNEL_PROFILE.len());
        for definition in NVST_CHANNEL_PROFILE {
            ids.push(
                rtc.direct_api()
                    .create_data_channel(channel_config(definition)),
            );
        }
        Self {
            control_reliable: ids[0],
            custom_reliable: ids[1],
            custom_partial: ids[2],
            control_partial: ids[3],
            control_unreliable: ids[4],
            input_partial: ids[5],
        }
    }

    pub(crate) fn contains(self, id: ChannelId) -> bool {
        self.all().contains(&id)
    }

    pub(crate) fn send_control(self, rtc: &mut Rtc, bytes: &[u8]) -> bool {
        write_channel(rtc, self.control_reliable, bytes)
    }

    pub(crate) fn send_partial_control(self, rtc: &mut Rtc, bytes: &[u8]) -> bool {
        write_channel(rtc, self.control_partial, bytes)
    }

    pub(crate) fn send_keepalive(self, rtc: &mut Rtc, stream_value: u32) -> bool {
        self.send_control(rtc, &control_keepalive(stream_value))
    }

    pub(crate) fn send_activation(self, rtc: &mut Rtc, timestamp_us: u64) -> bool {
        activation_chain(timestamp_us)
            .iter()
            .all(|bytes| self.send_control(rtc, bytes))
    }

    pub(crate) fn send_encoded(self, rtc: &mut Rtc, message: &NvstEncodedInput) -> bool {
        let id = match message.route {
            NvstInputRoute::ControlReliable => self.control_reliable,
            NvstInputRoute::InputPartial => self.input_partial,
        };
        write_channel(rtc, id, &message.bytes)
    }

    fn all(self) -> [ChannelId; 6] {
        [
            self.control_reliable,
            self.custom_reliable,
            self.custom_partial,
            self.control_partial,
            self.control_unreliable,
            self.input_partial,
        ]
    }
}

fn channel_config(definition: NvstChannelDefinition) -> ChannelConfig {
    let reliability = match definition.reliability {
        NvstChannelReliability::Reliable => Reliability::Reliable,
        NvstChannelReliability::Lifetime(lifetime) => Reliability::MaxPacketLifetime { lifetime },
        NvstChannelReliability::MaxRetransmits(retransmits) => {
            Reliability::MaxRetransmits { retransmits }
        }
    };
    ChannelConfig {
        label: definition.label.to_owned(),
        ordered: definition.ordered,
        reliability,
        negotiated: None,
        protocol: String::new(),
    }
}

fn write_channel(rtc: &mut Rtc, id: ChannelId, bytes: &[u8]) -> bool {
    rtc.channel(id)
        .is_some_and(|mut channel| channel.write(true, bytes).unwrap_or(false))
}

#[derive(Debug, Default)]
pub(crate) struct NvstInputChannelState {
    control_open: bool,
    negotiated_version: Option<u16>,
    ready_reported: bool,
    activation_sent: bool,
}

impl NvstInputChannelState {
    pub(crate) fn channel_opened(
        &mut self,
        channels: NvstInputChannels,
        id: ChannelId,
    ) -> Option<u16> {
        if id == channels.control_reliable {
            self.control_open = true;
        }
        self.take_new_ready_version()
    }

    pub(crate) fn channel_data(
        &mut self,
        channels: NvstInputChannels,
        id: ChannelId,
        bytes: &[u8],
    ) -> Option<u16> {
        if id == channels.control_reliable
            && let Some(version) = input_protocol_version(bytes)
        {
            self.negotiated_version = Some(version);
        }
        self.take_new_ready_version()
    }

    pub(crate) fn is_ready(&self) -> bool {
        self.control_open && self.negotiated_version.is_some()
    }

    pub(crate) fn control_is_open(&self) -> bool {
        self.control_open
    }

    pub(crate) fn handshake_timed_out(&self, started_at: Option<Instant>, now: Instant) -> bool {
        !self.is_ready()
            && started_at.is_some_and(|started| {
                now.saturating_duration_since(started) >= INPUT_HANDSHAKE_TIMEOUT
            })
    }

    pub(crate) fn activation_sent(&self) -> bool {
        self.activation_sent
    }

    pub(crate) fn mark_activation_sent(&mut self) {
        self.activation_sent = true;
    }

    pub(crate) fn channel_closed(&mut self, channels: NvstInputChannels, id: ChannelId) -> bool {
        if id == channels.input_partial {
            return self.is_ready();
        }
        if id != channels.control_reliable {
            return false;
        }
        let was_ready = self.is_ready();
        self.control_open = false;
        self.negotiated_version = None;
        self.ready_reported = false;
        self.activation_sent = false;
        was_ready
    }

    fn take_new_ready_version(&mut self) -> Option<u16> {
        if self.ready_reported || !self.is_ready() {
            return None;
        }
        self.ready_reported = true;
        self.negotiated_version
    }
}

pub(crate) fn next_control_keepalive(now: Instant) -> Instant {
    now + CONTROL_KEEPALIVE_INTERVAL
}

pub(crate) fn input_protocol_version(bytes: &[u8]) -> Option<u16> {
    if bytes.len() < 2 {
        return None;
    }
    let mut cursor = 0;
    while bytes.len().saturating_sub(cursor) >= 4 {
        let code = u16::from_le_bytes([bytes[cursor], bytes[cursor + 1]]);
        let payload_len = usize::from(u16::from_le_bytes([bytes[cursor + 2], bytes[cursor + 3]]));
        let payload_start = cursor + 4;
        let payload_end = payload_start.checked_add(payload_len)?;
        if payload_end > bytes.len() {
            break;
        }
        if code == COMMAND_INPUT_VERSION && payload_len >= 2 {
            return Some(u16::from_le_bytes([
                bytes[payload_start],
                bytes[payload_start + 1],
            ]));
        }
        cursor = payload_end;
    }
    let first = u16::from_le_bytes([bytes[0], bytes[1]]);
    if first == COMMAND_INPUT_VERSION {
        return Some(if bytes.len() >= 4 {
            u16::from_le_bytes([bytes[2], bytes[3]])
        } else {
            2
        });
    }
    (bytes[0] == 0x0e).then_some(first)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum NvstInputRoute {
    ControlReliable,
    InputPartial,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct NvstEncodedInput {
    pub(crate) route: NvstInputRoute,
    pub(crate) bytes: Vec<u8>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum NvstInputCodecError {
    Malformed(&'static str),
    UnsupportedType(u32),
    UnsupportedText,
}

impl fmt::Display for NvstInputCodecError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Malformed(reason) => write!(formatter, "malformed native input: {reason}"),
            Self::UnsupportedType(input_type) => {
                write!(formatter, "unsupported native input type {input_type}")
            }
            Self::UnsupportedText => {
                formatter.write_str("native NVST text contains an unmappable character")
            }
        }
    }
}

#[derive(Debug, Default)]
pub(crate) struct NvstInputCodec {
    gamepad_sequence: u8,
    gamepad_registered: bool,
}

impl NvstInputCodec {
    pub(crate) fn encode(
        &mut self,
        packet: &[u8],
        fallback_timestamp_us: u64,
    ) -> Result<Vec<NvstEncodedInput>, NvstInputCodecError> {
        let events = native_events(packet, fallback_timestamp_us)?;
        let mut encoded = Vec::new();
        for event in events {
            let input_type = read_u32_le(event.bytes, 0)
                .ok_or(NvstInputCodecError::Malformed("missing input type"))?;
            match input_type {
                INPUT_KEY_DOWN | INPUT_KEY_UP => {
                    require_len(event.bytes, 18, "short keyboard packet")?;
                    let key = read_u16_be(event.bytes, 4).expect("keyboard length checked");
                    let modifiers = read_u16_be(event.bytes, 6).expect("keyboard length checked");
                    let timestamp = read_u64_be(event.bytes, 10).unwrap_or(event.timestamp_us);
                    encoded.push(remote_input_message(
                        remote_input_packet(
                            input_type,
                            &[
                                (key >> 8) as u8,
                                key as u8,
                                (modifiers >> 8) as u8,
                                modifiers as u8,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                            ],
                        ),
                        timestamp,
                    ));
                }
                INPUT_MOUSE_RELATIVE => {
                    require_len(event.bytes, 22, "short relative mouse packet")?;
                    let body = [
                        event.bytes[4],
                        event.bytes[5],
                        event.bytes[6],
                        event.bytes[7],
                        0,
                        0,
                    ];
                    encoded.push(remote_input_message(
                        remote_input_packet(input_type, &body),
                        read_u64_be(event.bytes, 14).unwrap_or(event.timestamp_us),
                    ));
                }
                INPUT_MOUSE_ABSOLUTE => {
                    require_len(event.bytes, 26, "short absolute mouse packet")?;
                    let body = [
                        event.bytes[4],
                        event.bytes[5],
                        event.bytes[6],
                        event.bytes[7],
                        0x08,
                        0x00,
                        event.bytes[10],
                        event.bytes[11],
                        event.bytes[12],
                        event.bytes[13],
                    ];
                    encoded.push(remote_input_message(
                        remote_input_packet(input_type, &body),
                        read_u64_be(event.bytes, 18).unwrap_or(event.timestamp_us),
                    ));
                }
                INPUT_MOUSE_BUTTON_DOWN | INPUT_MOUSE_BUTTON_UP => {
                    require_len(event.bytes, 18, "short mouse button packet")?;
                    encoded.push(remote_input_message(
                        remote_input_packet(input_type, &[event.bytes[4], 0]),
                        read_u64_be(event.bytes, 10).unwrap_or(event.timestamp_us),
                    ));
                }
                INPUT_MOUSE_WHEEL => {
                    require_len(event.bytes, 22, "short mouse wheel packet")?;
                    let body = [0, 0, event.bytes[6], event.bytes[7], 0, 0];
                    encoded.push(remote_input_message(
                        remote_input_packet(input_type, &body),
                        read_u64_be(event.bytes, 14).unwrap_or(event.timestamp_us),
                    ));
                }
                INPUT_GAMEPAD => {
                    require_len(event.bytes, 38, "short gamepad packet")?;
                    let timestamp = read_u64_le(event.bytes, 30).unwrap_or(event.timestamp_us);
                    if !self.gamepad_registered {
                        self.gamepad_registered = true;
                        encoded.push(NvstEncodedInput {
                            route: NvstInputRoute::ControlReliable,
                            bytes: device_descriptor(timestamp, 3),
                        });
                    }
                    self.gamepad_sequence = self.gamepad_sequence.wrapping_add(1);
                    encoded.push(NvstEncodedInput {
                        route: NvstInputRoute::InputPartial,
                        bytes: gamepad_command(event.bytes, timestamp, self.gamepad_sequence),
                    });
                }
                INPUT_TEXT => encoded.extend(text_messages(&event)?),
                other => return Err(NvstInputCodecError::UnsupportedType(other)),
            }
        }
        Ok(encoded)
    }
}

#[derive(Debug, Clone, Copy)]
struct NativeEvent<'a> {
    bytes: &'a [u8],
    timestamp_us: u64,
}

fn native_events(
    packet: &[u8],
    fallback_timestamp_us: u64,
) -> Result<Vec<NativeEvent<'_>>, NvstInputCodecError> {
    if packet.is_empty() {
        return Err(NvstInputCodecError::Malformed("empty packet"));
    }
    if packet[0] == 0x22 {
        if packet.len() < 5 || read_u32_le(packet, 1) != Some(INPUT_TEXT) {
            return Err(NvstInputCodecError::Malformed(
                "unknown leading 0x22 packet",
            ));
        }
        return Ok(vec![NativeEvent {
            bytes: &packet[1..],
            timestamp_us: fallback_timestamp_us,
        }]);
    }
    if packet[0] != 0x23 {
        return Ok(vec![NativeEvent {
            bytes: packet,
            timestamp_us: fallback_timestamp_us,
        }]);
    }
    require_len(packet, 10, "short protocol-v3 wrapper")?;
    let timestamp_us = read_u64_be(packet, 1).expect("wrapper length checked");
    let mut cursor = 9;
    let mut events = Vec::new();
    while cursor < packet.len() {
        match packet[cursor] {
            0x22 => {
                let start = cursor + 1;
                let input_type = read_u32_le(packet, start)
                    .ok_or(NvstInputCodecError::Malformed("short single-event wrapper"))?;
                let length = native_event_length(input_type, packet.len() - start)?;
                let end = start + length;
                if end > packet.len() {
                    return Err(NvstInputCodecError::Malformed("truncated single event"));
                }
                events.push(NativeEvent {
                    bytes: &packet[start..end],
                    timestamp_us,
                });
                cursor = end;
            }
            0x21 => {
                require_remaining(packet, cursor, 3, "short length-prefixed wrapper")?;
                let length =
                    usize::from(u16::from_be_bytes([packet[cursor + 1], packet[cursor + 2]]));
                let start = cursor + 3;
                let end = start + length;
                if end > packet.len() {
                    return Err(NvstInputCodecError::Malformed(
                        "truncated length-prefixed event",
                    ));
                }
                events.push(NativeEvent {
                    bytes: &packet[start..end],
                    timestamp_us,
                });
                cursor = end;
            }
            0x26 => {
                require_remaining(packet, cursor, 7, "short partially-reliable wrapper")?;
                if packet[cursor + 4] != 0x21 {
                    return Err(NvstInputCodecError::Malformed(
                        "missing gamepad payload marker",
                    ));
                }
                let length =
                    usize::from(u16::from_be_bytes([packet[cursor + 5], packet[cursor + 6]]));
                let start = cursor + 7;
                let end = start + length;
                if end > packet.len() {
                    return Err(NvstInputCodecError::Malformed("truncated gamepad event"));
                }
                events.push(NativeEvent {
                    bytes: &packet[start..end],
                    timestamp_us,
                });
                cursor = end;
            }
            _ => return Err(NvstInputCodecError::Malformed("unknown protocol-v3 marker")),
        }
    }
    Ok(events)
}

fn native_event_length(input_type: u32, remaining: usize) -> Result<usize, NvstInputCodecError> {
    match input_type {
        INPUT_KEY_DOWN | INPUT_KEY_UP | INPUT_MOUSE_BUTTON_DOWN | INPUT_MOUSE_BUTTON_UP => Ok(18),
        INPUT_MOUSE_RELATIVE | INPUT_MOUSE_WHEEL => Ok(22),
        INPUT_MOUSE_ABSOLUTE => Ok(26),
        INPUT_GAMEPAD => Ok(38),
        INPUT_TEXT => Ok(remaining),
        other => Err(NvstInputCodecError::UnsupportedType(other)),
    }
}

fn remote_input_message(inner: Vec<u8>, timestamp_us: u64) -> NvstEncodedInput {
    let input_type = read_u32_le(&inner, 4).expect("remote input packet has type");
    let mut body = inner;
    let padded = 24.max(body.len().div_ceil(8) * 8);
    body.resize(padded, 0);
    let timestamp_count = usize::from(matches!(
        input_type,
        INPUT_KEY_DOWN | INPUT_KEY_UP | INPUT_MOUSE_ABSOLUTE
    )) + 1;
    for _ in 0..timestamp_count {
        body.extend_from_slice(&timestamp_us.to_le_bytes());
    }
    NvstEncodedInput {
        route: NvstInputRoute::ControlReliable,
        bytes: control_command(COMMAND_REMOTE_INPUT, &remote_input_packet(0x0e, &body)),
    }
}

fn remote_input_packet(input_type: u32, body: &[u8]) -> Vec<u8> {
    let mut packet = Vec::with_capacity(8 + body.len());
    packet.extend_from_slice(&(4_u32 + body.len() as u32).to_be_bytes());
    packet.extend_from_slice(&input_type.to_le_bytes());
    packet.extend_from_slice(body);
    packet
}

fn gamepad_command(packet: &[u8], timestamp_us: u64, sequence: u8) -> Vec<u8> {
    let mut payload = Vec::with_capacity(52);
    payload.push(0x23);
    payload.extend_from_slice(&timestamp_us.to_be_bytes());
    let mut body = [
        0x26, 0x00, 0x00, 0x00, 0x22, 0x0c, 0x00, 0x00, 0x00, 0x1a, 0x00, 0x00, 0x00, 0x03, 0x00,
        0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    ];
    body[3] = sequence;
    body[17..19].copy_from_slice(&packet[12..14]);
    body[19] = packet[14];
    body[20] = packet[15];
    body[21..29].copy_from_slice(&packet[16..24]);
    payload.extend_from_slice(&body);
    control_command(COMMAND_GAMEPAD, &payload)
}

fn text_messages(event: &NativeEvent<'_>) -> Result<Vec<NvstEncodedInput>, NvstInputCodecError> {
    require_len(event.bytes, 5, "short text packet")?;
    let text =
        std::str::from_utf8(&event.bytes[5..]).map_err(|_| NvstInputCodecError::UnsupportedText)?;
    let mut messages = Vec::with_capacity(text.chars().count() * 2);
    for character in text.chars() {
        let (virtual_key, modifiers) =
            text_keystroke(character).ok_or(NvstInputCodecError::UnsupportedText)?;
        for input_type in [INPUT_KEY_DOWN, INPUT_KEY_UP] {
            let mut body = Vec::with_capacity(14);
            body.extend_from_slice(&virtual_key.to_be_bytes());
            body.extend_from_slice(&modifiers.to_be_bytes());
            body.resize(14, 0);
            messages.push(remote_input_message(
                remote_input_packet(input_type, &body),
                event.timestamp_us,
            ));
        }
    }
    Ok(messages)
}

fn text_keystroke(character: char) -> Option<(u16, u16)> {
    let virtual_key = match character {
        'A'..='Z' => return Some((character as u16, 1)),
        'a'..='z' => return Some((character.to_ascii_uppercase() as u16, 0)),
        '0'..='9' => return Some((character as u16, 0)),
        ' ' => return Some((0x20, 0)),
        '\t' => return Some((0x09, 0)),
        '\n' | '\r' => return Some((0x0d, 0)),
        '!' => 0x31,
        '@' => 0x32,
        '#' => 0x33,
        '$' => 0x34,
        '%' => 0x35,
        '^' => 0x36,
        '&' => 0x37,
        '*' => 0x38,
        '(' => 0x39,
        ')' => 0x30,
        '_' => 0xbd,
        '+' => 0xbb,
        '{' => 0xdb,
        '}' => 0xdd,
        '|' => 0xdc,
        ':' => 0xba,
        '"' => 0xde,
        '<' => 0xbc,
        '>' => 0xbe,
        '?' => 0xbf,
        '~' => 0xc0,
        '-' => return Some((0xbd, 0)),
        '=' => return Some((0xbb, 0)),
        '[' => return Some((0xdb, 0)),
        ']' => return Some((0xdd, 0)),
        '\\' => return Some((0xdc, 0)),
        ';' => return Some((0xba, 0)),
        '\'' => return Some((0xde, 0)),
        ',' => return Some((0xbc, 0)),
        '.' => return Some((0xbe, 0)),
        '/' => return Some((0xbf, 0)),
        '`' => return Some((0xc0, 0)),
        _ => return None,
    };
    Some((virtual_key, 1))
}

fn activation_chain(timestamp_us: u64) -> [Vec<u8>; 6] {
    [
        enable_input(1, false),
        device_descriptor(timestamp_us, 2),
        control_command(COMMAND_READY, &[0]),
        state_change(COMMAND_WINDOW_STATE),
        state_change(COMMAND_SYSTEM_STATE),
        enable_input(1, true),
    ]
}

fn control_keepalive(stream_value: u32) -> Vec<u8> {
    control_command(COMMAND_KEEPALIVE, &stream_value.to_le_bytes())
}

fn enable_input(counter: u32, enabled: bool) -> Vec<u8> {
    let mut payload = Vec::with_capacity(12);
    payload.extend_from_slice(&0_u32.to_le_bytes());
    payload.extend_from_slice(&counter.to_le_bytes());
    payload.extend_from_slice(&u32::from(enabled).to_le_bytes());
    control_command(COMMAND_ENABLE_INPUT, &payload)
}

fn device_descriptor(timestamp_us: u64, descriptor_index: u8) -> Vec<u8> {
    let mut payload = Vec::with_capacity(48);
    payload.push(0x23);
    payload.extend_from_slice(&timestamp_us.to_be_bytes());
    let mut body = [
        0x22, 0x0c, 0x00, 0x00, 0x00, 0x1a, 0x00, 0x00, 0x00, 0x02, 0x00, 0x14, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x55, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    ];
    body[9] = descriptor_index;
    payload.extend_from_slice(&body);
    control_command(COMMAND_GAMEPAD, &payload)
}

fn state_change(code: u16) -> Vec<u8> {
    control_command(code, &[0; 12])
}

fn control_command(code: u16, payload: &[u8]) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(4 + payload.len());
    bytes.extend_from_slice(&code.to_le_bytes());
    bytes.extend_from_slice(&(payload.len() as u16).to_le_bytes());
    bytes.extend_from_slice(payload);
    bytes
}

fn require_len(
    bytes: &[u8],
    minimum: usize,
    reason: &'static str,
) -> Result<(), NvstInputCodecError> {
    if bytes.len() < minimum {
        return Err(NvstInputCodecError::Malformed(reason));
    }
    Ok(())
}

fn require_remaining(
    bytes: &[u8],
    offset: usize,
    minimum: usize,
    reason: &'static str,
) -> Result<(), NvstInputCodecError> {
    if bytes.len().saturating_sub(offset) < minimum {
        return Err(NvstInputCodecError::Malformed(reason));
    }
    Ok(())
}

fn read_u16_be(bytes: &[u8], offset: usize) -> Option<u16> {
    Some(u16::from_be_bytes(
        bytes.get(offset..offset + 2)?.try_into().ok()?,
    ))
}

fn read_u32_le(bytes: &[u8], offset: usize) -> Option<u32> {
    Some(u32::from_le_bytes(
        bytes.get(offset..offset + 4)?.try_into().ok()?,
    ))
}

fn read_u64_be(bytes: &[u8], offset: usize) -> Option<u64> {
    Some(u64::from_be_bytes(
        bytes.get(offset..offset + 8)?.try_into().ok()?,
    ))
}

fn read_u64_le(bytes: &[u8], offset: usize) -> Option<u64> {
    Some(u64::from_le_bytes(
        bytes.get(offset..offset + 8)?.try_into().ok()?,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hex(value: &str) -> Vec<u8> {
        assert_eq!(value.len() % 2, 0, "hex input must contain whole bytes");
        value
            .as_bytes()
            .chunks_exact(2)
            .map(|pair| {
                u8::from_str_radix(std::str::from_utf8(pair).expect("hex utf8"), 16)
                    .expect("hex byte")
            })
            .collect()
    }

    #[test]
    fn channel_profile_matches_official_sids_labels_and_reliability() {
        assert_eq!(
            NVST_CHANNEL_PROFILE.map(|definition| definition.sid),
            [0, 2, 4, 6, 8, 10]
        );
        assert_eq!(
            NVST_CHANNEL_PROFILE.map(|definition| definition.label),
            [
                CONTROL_RELIABLE_LABEL,
                CUSTOM_RELIABLE_LABEL,
                CUSTOM_PARTIAL_LABEL,
                CONTROL_PARTIAL_LABEL,
                CONTROL_UNRELIABLE_LABEL,
                INPUT_PARTIAL_LABEL,
            ]
        );
        assert!(
            NVST_CHANNEL_PROFILE
                .iter()
                .all(|definition| definition.ordered)
        );
        assert_eq!(
            NVST_CHANNEL_PROFILE.map(|definition| definition.reliability),
            [
                NvstChannelReliability::Reliable,
                NvstChannelReliability::Reliable,
                NvstChannelReliability::Lifetime(300),
                NvstChannelReliability::Lifetime(300),
                NvstChannelReliability::MaxRetransmits(0),
                NvstChannelReliability::Lifetime(300),
            ]
        );
        let configs = NVST_CHANNEL_PROFILE.map(channel_config);
        assert_eq!(configs.clone().map(|config| config.ordered), [true; 6]);
        assert_eq!(
            configs.map(|config| config.reliability),
            [
                Reliability::Reliable,
                Reliability::Reliable,
                Reliability::MaxPacketLifetime { lifetime: 300 },
                Reliability::MaxPacketLifetime { lifetime: 300 },
                Reliability::MaxRetransmits { retransmits: 0 },
                Reliability::MaxPacketLifetime { lifetime: 300 },
            ]
        );
    }

    #[test]
    fn parses_full_control_handshake_and_requires_only_control_plus_version() {
        assert_eq!(
            input_protocol_version(&[0x0e, 0x02, 0x02, 0x00, 0x03, 0x00]),
            Some(3)
        );
        assert_eq!(input_protocol_version(&[0x0e, 0x02, 0x03, 0x00]), Some(3));
        assert_eq!(input_protocol_version(&[0x0e, 0x03]), Some(0x030e));
        assert_eq!(input_protocol_version(&[0x0e, 0x02]), Some(2));
        assert_eq!(input_protocol_version(&[1]), None);
        assert_eq!(
            input_protocol_version(&[0x01, 0x01, 0x00, 0x00, 0x0e, 0x02, 0x02, 0x00, 0x03, 0x00,]),
            Some(3)
        );

        let mut rtc = Rtc::new(Instant::now());
        let channels = NvstInputChannels::create(&mut rtc);
        let mut state = NvstInputChannelState::default();
        assert_eq!(
            state.channel_opened(channels, channels.control_reliable),
            None
        );
        assert!(!state.is_ready());
        assert_eq!(
            state.channel_data(
                channels,
                channels.input_partial,
                &[0x0e, 0x02, 0x02, 0x00, 0x03, 0x00],
            ),
            None
        );
        assert!(!state.is_ready());
        assert_eq!(
            state.channel_data(
                channels,
                channels.control_reliable,
                &[0x0e, 0x02, 0x02, 0x00, 0x03, 0x00],
            ),
            Some(3)
        );
        assert!(state.is_ready());
    }

    #[test]
    fn control_keepalive_and_activation_are_byte_exact() {
        assert_eq!(control_keepalive(0), hex("0002040000000000"));
        let now = Instant::now();
        assert_eq!(
            next_control_keepalive(now).duration_since(now),
            Duration::from_secs(3)
        );

        let chain = activation_chain(20_102_193);
        assert_eq!(chain[0], hex("0b020c00000000000100000000000000"));
        assert_eq!(
            chain[1],
            hex(
                "0d02300023000000000132bc31220c0000001a000000020014000000000000000000000000000000550000000000000000000000"
            )
        );
        assert_eq!(chain[2], hex("0803010000"));
        assert_eq!(chain[3], hex("20030c00000000000000000000000000"));
        assert_eq!(chain[4], hex("21030c00000000000000000000000000"));
        assert_eq!(chain[5], hex("0b020c00000000000100000001000000"));
    }

    #[test]
    fn keyboard_and_mouse_translate_to_byte_exact_control_commands() {
        let mut codec = NvstInputCodec::default();
        let mut key = vec![0; 18];
        key[..4].copy_from_slice(&INPUT_KEY_DOWN.to_le_bytes());
        key[4..6].copy_from_slice(&0x41_u16.to_be_bytes());
        key[6..8].copy_from_slice(&1_u16.to_be_bytes());
        key[10..18].copy_from_slice(&0x015b_15b1_u64.to_be_bytes());
        let mut wrapped_key = vec![0x23];
        wrapped_key.extend_from_slice(&99_u64.to_be_bytes());
        wrapped_key.push(0x22);
        wrapped_key.extend_from_slice(&key);
        let key = codec.encode(&wrapped_key, 0).expect("key");
        assert_eq!(key.len(), 1);
        assert_eq!(key[0].route, NvstInputRoute::ControlReliable);
        assert_eq!(
            key[0].bytes,
            hex(
                "060230000000002c0e000000000000120300000000410001000000000000000000000000b1155b0100000000b1155b0100000000"
            )
        );

        let mut mouse = vec![0; 22];
        mouse[..4].copy_from_slice(&INPUT_MOUSE_RELATIVE.to_le_bytes());
        mouse[4..6].copy_from_slice(&24_i16.to_be_bytes());
        mouse[6..8].copy_from_slice(&24_i16.to_be_bytes());
        mouse[14..22].copy_from_slice(&0x0186_1330_u64.to_be_bytes());
        let mut wrapped_mouse = vec![0x23];
        wrapped_mouse.extend_from_slice(&100_u64.to_be_bytes());
        wrapped_mouse.push(0x21);
        wrapped_mouse.extend_from_slice(&(mouse.len() as u16).to_be_bytes());
        wrapped_mouse.extend_from_slice(&mouse);
        let mouse = codec.encode(&wrapped_mouse, 0).expect("mouse");
        assert_eq!(
            mouse[0].bytes,
            hex(
                "06022800000000240e0000000000000a07000000001800180000000000000000000000003013860100000000"
            )
        );
    }

    #[test]
    fn standard_gamepad_translates_to_input_partial_and_registers_once() {
        let mut codec = NvstInputCodec::default();
        let mut gamepad = vec![0; 38];
        gamepad[..4].copy_from_slice(&INPUT_GAMEPAD.to_le_bytes());
        gamepad[12..14].copy_from_slice(&0x1000_u16.to_le_bytes());
        gamepad[30..38].copy_from_slice(&0x015b_171a_u64.to_le_bytes());
        let mut wrapped_gamepad = vec![0x23];
        wrapped_gamepad.extend_from_slice(&101_u64.to_be_bytes());
        wrapped_gamepad.extend_from_slice(&[0x26, 0, 0, 1, 0x21]);
        wrapped_gamepad.extend_from_slice(&(gamepad.len() as u16).to_be_bytes());
        wrapped_gamepad.extend_from_slice(&gamepad);

        let first = codec.encode(&wrapped_gamepad, 0).expect("gamepad");
        assert_eq!(first.len(), 2);
        assert_eq!(first[0].route, NvstInputRoute::ControlReliable);
        assert_eq!(first[1].route, NvstInputRoute::InputPartial);
        assert_eq!(
            first[1].bytes,
            hex(
                "0d0234002300000000015b171a26000001220c0000001a000000030014000010000000000000000000000000550000000000000000000000"
            )
        );

        let second = codec.encode(&wrapped_gamepad, 0).expect("second gamepad");
        assert_eq!(second.len(), 1);
        assert_eq!(second[0].route, NvstInputRoute::InputPartial);
        assert_eq!(second[0].bytes[16], 2);
    }

    #[test]
    fn unsupported_packets_fail_closed() {
        let mut codec = NvstInputCodec::default();
        assert_eq!(
            codec.encode(&13_u32.to_le_bytes(), 0),
            Err(NvstInputCodecError::UnsupportedType(13))
        );
        let mut text = vec![0x22];
        text.extend_from_slice(&INPUT_TEXT.to_le_bytes());
        text.extend_from_slice("é".as_bytes());
        assert_eq!(
            codec.encode(&text, 0),
            Err(NvstInputCodecError::UnsupportedText)
        );
    }

    #[test]
    fn closure_and_timeout_state_are_predictable() {
        let now = Instant::now();
        let mut rtc = Rtc::new(now);
        let channels = NvstInputChannels::create(&mut rtc);
        let mut state = NvstInputChannelState::default();
        state.channel_opened(channels, channels.control_reliable);
        assert!(!state.handshake_timed_out(Some(now), now + Duration::from_millis(4_999)));
        assert!(state.handshake_timed_out(Some(now), now + INPUT_HANDSHAKE_TIMEOUT));
        state.channel_data(
            channels,
            channels.control_reliable,
            &[0x0e, 0x02, 0x02, 0x00, 0x03, 0x00],
        );
        assert!(!state.handshake_timed_out(Some(now), now + INPUT_HANDSHAKE_TIMEOUT));
        state.mark_activation_sent();
        assert_eq!(
            state.channel_data(
                channels,
                channels.control_reliable,
                &[0x0e, 0x02, 0x02, 0x00, 0x03, 0x00],
            ),
            None
        );
        assert!(state.activation_sent());
        assert!(state.channel_closed(channels, channels.input_partial));
        assert!(state.is_ready());
        assert!(state.channel_closed(channels, channels.control_reliable));
        assert!(!state.is_ready());
        assert!(!state.channel_closed(channels, channels.input_partial));
        assert!(!state.is_ready());
    }
}
