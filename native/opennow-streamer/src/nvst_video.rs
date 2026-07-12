//! Classic NVST / Mjolnir UDP video receive scaffold (GO-with-Moonlight-hypothesis).
//!
//! Pipeline (intended):
//!   UDP bind → hole-punch PING → (SRTP decrypt) → RTP(+ext) → NV_VIDEO_PACKET →
//!   assemble AUs on FLAG_EOF → appsrc (Annex-B) → h265parse/h264parse → decoder → sink
//!
//! SRTP: master = AES-256 key[32] || salt[12] where salt = key_id as BE u32 zero-padded
//! to 12 bytes (`%024x`). Prefer AEAD_AES_256_GCM. This scaffold does **not** link
//! libsrtp; when decrypt is unavailable it logs once and parses cleartext RTP so the
//! receive/assemble/appsrc path can be exercised. A GStreamer `srtpdec` branch can be
//! added later once the wire profile is confirmed on a live pcap.

#![cfg_attr(not(feature = "gstreamer"), allow(dead_code))]

#[cfg(feature = "gstreamer")]
use crate::gstreamer_backend::send_log;
#[cfg(feature = "gstreamer")]
use crate::protocol::{Event, NvstVideoSession};
#[cfg(feature = "gstreamer")]
use gstreamer as gst;
#[cfg(feature = "gstreamer")]
use gstreamer::prelude::*;
#[cfg(feature = "gstreamer")]
use std::io;
#[cfg(feature = "gstreamer")]
use std::net::{SocketAddr, UdpSocket};
#[cfg(feature = "gstreamer")]
use std::sync::atomic::{AtomicBool, Ordering};
#[cfg(feature = "gstreamer")]
use std::sync::mpsc::Sender;
#[cfg(feature = "gstreamer")]
use std::sync::Arc;
#[cfg(feature = "gstreamer")]
use std::thread::{self, JoinHandle};
#[cfg(feature = "gstreamer")]
use std::time::Duration;

/// Moonlight / GameStream `NV_VIDEO_PACKET` size (little-endian fields).
pub const NV_VIDEO_PACKET_LEN: usize = 16;
/// `FLAG_EOF` — last packet of a frame (assemble AU).
pub const FLAG_EOF: u8 = 0x02;
/// `FLAG_SOF` — first packet of a frame.
#[allow(dead_code)]
pub const FLAG_SOF: u8 = 0x04;
/// `FLAG_CONTAINS_PIC_DATA`.
#[allow(dead_code)]
pub const FLAG_CONTAINS_PIC_DATA: u8 = 0x01;

/// Pack AES-256 key + key ID into the 44-byte libsrtp master key||salt.
///
/// Salt is `key_id` as a big-endian u32, zero-padded to 12 bytes (`printf`-style `%024x`).
pub fn pack_srtp_master_key_salt(aes_key: &[u8; 32], key_id: u32) -> [u8; 44] {
    let mut out = [0u8; 44];
    out[..32].copy_from_slice(aes_key);
    out[40..44].copy_from_slice(&key_id.to_be_bytes());
    out
}

/// Decode a 64-hex AES-256 key string into 32 bytes.
pub fn parse_aes_key_hex(hex: &str) -> Result<[u8; 32], String> {
    let trimmed = hex.trim();
    if trimmed.len() != 64 {
        return Err(format!(
            "srtpAesKeyHex must be 64 hex chars, got {}",
            trimmed.len()
        ));
    }
    let mut out = [0u8; 32];
    for (i, chunk) in trimmed.as_bytes().chunks(2).enumerate() {
        let s = std::str::from_utf8(chunk).map_err(|e| e.to_string())?;
        out[i] = u8::from_str_radix(s, 16)
            .map_err(|e| format!("Invalid hex in srtpAesKeyHex at byte {i}: {e}"))?;
    }
    Ok(out)
}

/// Parsed Moonlight-hypothesis `NV_VIDEO_PACKET` (LE).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct NvVideoPacket {
    pub stream_packet_index: u32,
    pub frame_index: u32,
    pub flags: u8,
    pub extra_flags: u8,
    pub multi_fec_flags: u8,
    pub multi_fec_blocks: u8,
    pub fec_info: u32,
}

impl NvVideoPacket {
    pub fn parse(bytes: &[u8]) -> Option<Self> {
        if bytes.len() < NV_VIDEO_PACKET_LEN {
            return None;
        }
        Some(Self {
            stream_packet_index: u32::from_le_bytes(bytes[0..4].try_into().ok()?),
            frame_index: u32::from_le_bytes(bytes[4..8].try_into().ok()?),
            flags: bytes[8],
            extra_flags: bytes[9],
            multi_fec_flags: bytes[10],
            multi_fec_blocks: bytes[11],
            fec_info: u32::from_le_bytes(bytes[12..16].try_into().ok()?),
        })
    }

    /// FEC / non-picture packets use `flags == 0` in the Moonlight hypothesis.
    pub fn is_fec_or_empty(&self) -> bool {
        self.flags == 0
    }

    pub fn is_eof(&self) -> bool {
        self.flags & FLAG_EOF != 0
    }
}

/// Strip standard RTP header (+ optional 4-byte extension pad when X is set) and
/// return the payload starting at `NV_VIDEO_PACKET`.
///
/// Layout hypothesis (post-SRTP): `12B RTP + 4B ext pad if X + 16B NV_VIDEO_PACKET + payload`.
pub fn strip_rtp_header(packet: &[u8]) -> Option<&[u8]> {
    if packet.len() < 12 {
        return None;
    }
    let v_pxcc = packet[0];
    let version = v_pxcc >> 6;
    if version != 2 {
        return None;
    }
    let has_padding = (v_pxcc & 0x20) != 0;
    let has_extension = (v_pxcc & 0x10) != 0;
    let csrc_count = (v_pxcc & 0x0f) as usize;
    let mut offset = 12 + csrc_count * 4;
    if packet.len() < offset {
        return None;
    }
    if has_extension {
        // Moonlight-hypothesis: fixed 4-byte extension pad (not full RFC 5285 walk).
        offset = offset.checked_add(4)?;
        if packet.len() < offset {
            return None;
        }
    }

    let mut payload = &packet[offset..];
    if has_padding {
        let pad_len = *payload.last()? as usize;
        if pad_len == 0 || pad_len > payload.len() {
            return None;
        }
        payload = &payload[..payload.len() - pad_len];
    }
    Some(payload)
}

/// Parse RTP → NV_VIDEO_PACKET → media payload. Returns `None` for FEC (`flags==0`)
/// or malformed packets.
pub fn parse_nvst_rtp_payload(packet: &[u8]) -> Option<(NvVideoPacket, &[u8])> {
    let after_rtp = strip_rtp_header(packet)?;
    let header = NvVideoPacket::parse(after_rtp)?;
    if header.is_fec_or_empty() {
        return None;
    }
    let media = after_rtp.get(NV_VIDEO_PACKET_LEN..)?;
    Some((header, media))
}

/// Assembles Annex-B access units from NVST RTP payloads (Moonlight hypothesis).
#[derive(Debug, Default)]
pub struct NvstFrameAssembler {
    current_frame: Option<u32>,
    buffer: Vec<u8>,
}

impl NvstFrameAssembler {
    pub fn new() -> Self {
        Self::default()
    }

    /// Push one media packet. Returns a completed AU when `FLAG_EOF` is set.
    pub fn push(&mut self, header: &NvVideoPacket, payload: &[u8]) -> Option<Vec<u8>> {
        if header.is_fec_or_empty() {
            return None;
        }

        match self.current_frame {
            Some(frame) if frame != header.frame_index => {
                self.buffer.clear();
                self.current_frame = Some(header.frame_index);
            }
            None => {
                self.current_frame = Some(header.frame_index);
            }
            _ => {}
        }

        self.buffer.extend_from_slice(payload);

        if header.is_eof() {
            let au = std::mem::take(&mut self.buffer);
            self.current_frame = None;
            if au.is_empty() {
                None
            } else {
                Some(au)
            }
        } else {
            None
        }
    }
}

/// Caps string for Annex-B appsrc feeding `h265parse` / `h264parse`.
pub fn annexb_appsrc_caps(codec: &str) -> &'static str {
    match codec.to_ascii_uppercase().as_str() {
        "H264" => "video/x-h264,stream-format=byte-stream,alignment=au",
        _ => "video/x-h265,stream-format=byte-stream,alignment=au",
    }
}

/// Build description for a GStreamer branch that would decrypt SRTP then feed RTP
/// into an appsrc-style path. Documented for future `srtpdec` wiring; the live
/// scaffold currently decrypts (or skips) in the UDP thread and pushes Annex-B AUs.
pub fn srtpdec_pipeline_branch_hint(codec: &str) -> String {
    let caps = annexb_appsrc_caps(codec);
    let parser = match codec.to_ascii_uppercase().as_str() {
        "H264" => "h264parse",
        _ => "h265parse",
    };
    format!(
        "appsrc name=nvst-annexb caps=\"{caps}\" is-live=true format=time ! \
         {parser} ! …decoder… ! …sink…  \
         (SRTP: prefer libsrtp AEAD_AES_256_GCM with pack_srtp_master_key_salt; \
         GStreamer srtpdec may be wired later — do not use appsrc caps=application/x-rtp \
         named nvst-rtp for assembled AUs)"
    )
}

#[cfg(feature = "gstreamer")]
pub(crate) struct NvstVideoReceiveHandle {
    stop: Arc<AtomicBool>,
    join: Option<JoinHandle<()>>,
}

#[cfg(feature = "gstreamer")]
impl std::fmt::Debug for NvstVideoReceiveHandle {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("NvstVideoReceiveHandle")
            .field("stop", &self.stop.load(Ordering::SeqCst))
            .field("join", &self.join.as_ref().map(|_| "JoinHandle"))
            .finish()
    }
}

#[cfg(feature = "gstreamer")]
impl NvstVideoReceiveHandle {
    pub(crate) fn stop(mut self) {
        self.stop.store(true, Ordering::SeqCst);
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
    }
}

#[cfg(feature = "gstreamer")]
impl Drop for NvstVideoReceiveHandle {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::SeqCst);
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
    }
}

/// Spawn UDP bind → PING hole-punch → recv → parse → push Annex-B AUs into `appsrc`.
#[cfg(feature = "gstreamer")]
pub(crate) fn spawn_nvst_udp_receive(
    session: NvstVideoSession,
    appsrc: gst::Element,
    event_sender: Option<Sender<Event>>,
) -> Result<NvstVideoReceiveHandle, String> {
    let aes_key = parse_aes_key_hex(&session.srtp_aes_key_hex)?;
    let master = pack_srtp_master_key_salt(&aes_key, session.srtp_key_id);
    let _ = master; // reserved for future libsrtp / srtpdec install

    let peer: SocketAddr = format!("{}:{}", session.video_peer_ip, session.video_peer_port)
        .parse()
        .map_err(|e| format!("Invalid nvstVideo peer address: {e}"))?;

    let bind_addr = SocketAddr::from(([0, 0, 0, 0], session.client_udp_port));
    let socket = UdpSocket::bind(bind_addr)
        .map_err(|e| format!("Failed to bind NVST UDP {bind_addr}: {e}"))?;
    socket
        .set_read_timeout(Some(Duration::from_millis(250)))
        .map_err(|e| format!("Failed to set NVST UDP read timeout: {e}"))?;

    let ping = session
        .ping_payload
        .as_deref()
        .filter(|s| !s.is_empty())
        .unwrap_or("PING");
    match socket.send_to(ping.as_bytes(), peer) {
        Ok(_) => send_log(
            &event_sender,
            "info",
            format!(
                "NVST video hole-punch sent ({ping_len} B) to {peer} from {bind_addr}.",
                ping_len = ping.len()
            ),
        ),
        Err(e) => send_log(
            &event_sender,
            "warn",
            format!("NVST video hole-punch to {peer} failed: {e}"),
        ),
    }

    send_log(
        &event_sender,
        "info",
        format!(
            "NVST SRTP scaffold: master key/salt packed (44 B) for keyId {}; \
             libsrtp/srtpdec not linked — parsing cleartext RTP if packets arrive undecrypted. {}",
            session.srtp_key_id,
            srtpdec_pipeline_branch_hint(session.codec.as_deref().unwrap_or("H265"))
        ),
    );

    let stop = Arc::new(AtomicBool::new(false));
    let stop_flag = stop.clone();
    let join = thread::Builder::new()
        .name("nvst-udp-video".to_owned())
        .spawn(move || {
            nvst_udp_recv_loop(socket, appsrc, event_sender, stop_flag);
        })
        .map_err(|e| format!("Failed to spawn NVST UDP thread: {e}"))?;

    Ok(NvstVideoReceiveHandle {
        stop,
        join: Some(join),
    })
}

#[cfg(feature = "gstreamer")]
fn nvst_udp_recv_loop(
    socket: UdpSocket,
    appsrc: gst::Element,
    event_sender: Option<Sender<Event>>,
    stop: Arc<AtomicBool>,
) {
    let mut buf = [0u8; 2048];
    let mut assembler = NvstFrameAssembler::new();
    let mut first_packet_logged = false;
    let mut first_au_logged = false;
    let mut decrypt_fail_logged = false;
    let mut cleartext_notice_logged = false;

    while !stop.load(Ordering::SeqCst) {
        match socket.recv_from(&mut buf) {
            Ok((len, _from)) => {
                if !first_packet_logged {
                    first_packet_logged = true;
                    send_log(
                        &event_sender,
                        "info",
                        format!("NVST UDP received first packet ({len} B)."),
                    );
                }
                if !cleartext_notice_logged {
                    cleartext_notice_logged = true;
                    send_log(
                        &event_sender,
                        "info",
                        "NVST SRTP decrypt unavailable in scaffold; attempting cleartext RTP parse."
                            .to_owned(),
                    );
                }

                let packet = &buf[..len];
                if packet.first().map(|b| b >> 6) != Some(2) {
                    if !decrypt_fail_logged {
                        decrypt_fail_logged = true;
                        send_log(
                            &event_sender,
                            "warn",
                            "NVST UDP packet does not look like cleartext RTP (version != 2); \
                             SRTP decrypt required — continuing to listen."
                                .to_owned(),
                        );
                    }
                    continue;
                }

                let Some((header, payload)) = parse_nvst_rtp_payload(packet) else {
                    continue;
                };
                let Some(au) = assembler.push(&header, payload) else {
                    continue;
                };
                if !first_au_logged {
                    first_au_logged = true;
                    send_log(
                        &event_sender,
                        "info",
                        format!(
                            "NVST assembled first Annex-B AU ({} B, frameIndex={}).",
                            au.len(),
                            header.frame_index
                        ),
                    );
                }
                if let Err(err) = push_au_to_appsrc(&appsrc, &au) {
                    send_log(
                        &event_sender,
                        "warn",
                        format!("NVST appsrc push failed: {err}"),
                    );
                }
            }
            Err(e) if e.kind() == io::ErrorKind::WouldBlock || e.kind() == io::ErrorKind::TimedOut => {
                continue;
            }
            Err(e) => {
                if !stop.load(Ordering::SeqCst) {
                    send_log(
                        &event_sender,
                        "warn",
                        format!("NVST UDP recv error: {e}"),
                    );
                }
                break;
            }
        }
    }
}

#[cfg(feature = "gstreamer")]
fn push_au_to_appsrc(appsrc: &gst::Element, au: &[u8]) -> Result<(), String> {
    let mut buffer = gst::Buffer::from_mut_slice(au.to_vec());
    {
        let buffer = buffer
            .get_mut()
            .ok_or_else(|| "NVST appsrc buffer not writable".to_owned())?;
        buffer.set_pts(gst::ClockTime::NONE);
        buffer.set_dts(gst::ClockTime::NONE);
    }
    let flow = appsrc.emit_by_name::<gst::FlowReturn>("push-buffer", &[&buffer]);
    if flow != gst::FlowReturn::Ok {
        return Err(format!("appsrc push-buffer returned {flow:?}"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pack_srtp_master_key_salt_matches_docs_key_id() {
        // Docs: key_id 2664076126 → salt ends 9ECA935E (`%024x` → 00000000000000009ECA935E).
        let key = [0xABu8; 32];
        let packed = pack_srtp_master_key_salt(&key, 2664076126);
        assert_eq!(&packed[..32], &key);
        assert_eq!(&packed[32..40], &[0u8; 8]);
        assert_eq!(&packed[40..44], &[0x9E, 0xCA, 0x93, 0x5E]);
    }

    #[test]
    fn pack_srtp_additional_doc_key_ids() {
        let key = [0u8; 32];
        let a = pack_srtp_master_key_salt(&key, 1664590642);
        assert_eq!(&a[40..44], &[0x63, 0x37, 0xA3, 0x32]);
        let b = pack_srtp_master_key_salt(&key, 2478780175);
        assert_eq!(&b[40..44], &[0x93, 0xBF, 0x2F, 0x0F]);
    }

    #[test]
    fn parse_nv_video_packet_synthetic() {
        let mut bytes = [0u8; 16];
        bytes[0..4].copy_from_slice(&0x11223344u32.to_le_bytes());
        bytes[4..8].copy_from_slice(&7u32.to_le_bytes());
        bytes[8] = FLAG_SOF | FLAG_EOF | FLAG_CONTAINS_PIC_DATA;
        bytes[9] = 0x10;
        bytes[10] = 0x20;
        bytes[11] = 0x30;
        bytes[12..16].copy_from_slice(&0xAABBCCDDu32.to_le_bytes());

        let pkt = NvVideoPacket::parse(&bytes).expect("parse");
        assert_eq!(pkt.stream_packet_index, 0x11223344);
        assert_eq!(pkt.frame_index, 7);
        assert!(pkt.is_eof());
        assert!(!pkt.is_fec_or_empty());
        assert_eq!(pkt.extra_flags, 0x10);
        assert_eq!(pkt.fec_info, 0xAABBCCDD);
    }

    #[test]
    fn fec_flags_zero_ignored_by_parser() {
        let mut packet = vec![0x80u8, 0x60, 0x00, 0x01, 0, 0, 0, 0, 0, 0, 0, 0];
        packet.extend_from_slice(&[0u8; 16]);
        packet.extend_from_slice(&[0xDE, 0xAD]);
        assert!(parse_nvst_rtp_payload(&packet).is_none());
    }

    #[test]
    fn assemble_frame_on_eof() {
        let mut asm = NvstFrameAssembler::new();
        let sof = NvVideoPacket {
            stream_packet_index: 1,
            frame_index: 3,
            flags: FLAG_SOF | FLAG_CONTAINS_PIC_DATA,
            extra_flags: 0,
            multi_fec_flags: 0,
            multi_fec_blocks: 0,
            fec_info: 0,
        };
        let eof = NvVideoPacket {
            flags: FLAG_EOF | FLAG_CONTAINS_PIC_DATA,
            stream_packet_index: 2,
            ..sof
        };
        assert!(asm.push(&sof, b"AA").is_none());
        let au = asm.push(&eof, b"BB").expect("AU");
        assert_eq!(au, b"AABB");
    }

    #[test]
    fn strip_rtp_with_extension_pad() {
        let mut packet = vec![0x90u8, 0x60, 0x00, 0x02, 0, 0, 0, 0, 0, 0, 0, 0];
        packet.extend_from_slice(&[0xBE, 0xDE, 0x00, 0x00]);
        let mut nv = [0u8; 16];
        nv[8] = FLAG_EOF | FLAG_CONTAINS_PIC_DATA;
        packet.extend_from_slice(&nv);
        packet.extend_from_slice(b"NAL");
        let (hdr, payload) = parse_nvst_rtp_payload(&packet).expect("parse");
        assert!(hdr.is_eof());
        assert_eq!(payload, b"NAL");
    }
}
