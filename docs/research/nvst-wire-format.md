# NVST wire-format research (Phase 0 gate)

**Status:** Research complete + GO-reversal pass  
**Date:** 2026-07-09  
**Session source:** Official GFN Windows client `geronimo.log` (2026-07-08, streamer `X-GS-Version: 14.2`) + Bifrost2.dll string pass  
**Verdict:** **GO-with-Moonlight-hypothesis** — implement hybrid classic UDP video + SCTP input; first live pcap still required to lock RTP/GS-ext/SRTP decrypt and confirm AU assembly.

Related artifacts:

| File | Role |
|------|------|
| `nvst-sdp-catalog.md` | Prior SDP/QoS catalog |
| `nvst-describe-sdp-sample-1.txt` | Sanitized DESCRIBE feature dump |
| `nvst-session-snippets.txt` | Connection / RTSP timeline excerpts |
| `nvst-rtsp-handshake-fixture.md` | Sanitized RTSP method/header timeline (this pass) |
| `nvst-announce-allowlist-1080p60.json` | Minimal ANNOUNCE attr allowlist for 1080p60 |
| `nvst-xnv-keys.json` / `nvst-config-values.json` | Key inventories |
| `nvst-binary-strings*.txt` | Read-only CEF string passes (no binaries committed) |
| `nvst-binary-strings-bifrost2-wire-curated.txt` | Curated Bifrost2 wire-format strings (RTP/FEC/SCTP) |
| `nvst-geronimo-wire-hits.txt` | Compact geronimo.log wire hits (redacted) |

**No NVIDIA binaries, pcaps, or live secrets are committed.** HMAC/AES key material below is truncated or redacted.

---

## Executive verdict

| Exit criterion (plan 0.2–0.5) | Met? | Notes |
|------------------------------|------|-------|
| 0.2 `udp_ag` without `udp_enc` | **Yes (control)** | Live session selected `udp_ag (2) - not encrypted` and reached PLAY |
| 0.3 Video packet header + reconstructible AU fixture | **Partial** | Stack proven (UDP RTP → SRTP → ReedSolomon FEC → H264/H265 depacketizer); **no pcap/AU fixture**; Moonlight RTP+FEC is a **hypothesis**, not byte-verified |
| 0.4 Input framing vs `InputEncoder` | **Yes (SCTP)** | Official uplink is **SCTP datachannels** on WebRtcTransport/bundle; label `input_channel_partially_reliable` matches OpenNOW; **not** catalog UDP `47995` |
| 0.5 Sanitized ANNOUNCE allowlist for 1080p60 | **Partial yes** | Allowlist fixture checked in; full 151-attr body not logged verbatim |

**Gate decision:** **GO-with-Moonlight-hypothesis**

- **Allowed next:** RTSPS handshake probe (unchanged) **plus** (a) video receive scaffold: UDP bind → libsrtp decrypt → RTP/GS-ext v2 parse → ReedSolomon FEC → H265/H264 AU assemble using Moonlight/GameStream as starting layout; (b) input uplink via **SCTP/datachannel** reusing OpenNOW `InputEncoder` (`0x21`/`0x22`/`0x26`) on labels matching official channels — **not** raw UDP to `input 47995`.
- **Still blocked until first live capture validates:** exact GS extension field offsets, SRTP profile/key install path, FEC `packetType`/`fecgrpid`/`pktid` wire layout, and hex proof that datachannel payloads begin with `0x23`/`0x21`/`0x22`/`0x26`.
- **Capture search (2026-07-09):** no `.pcap`/`.pcapng`/`.etl` under GeForceNOW AppData, Temp, or the OpenNOW repo.

---

## 0.1 RTSP-over-WSS transcript summary

### Endpoints (CloudMatch `usage=14`)

Observed twice per session:

| Port | URL shape | Role |
|------|-----------|------|
| **322** | `rtsps://<host>:322` | Primary RTSPS Secure WebSocket (`TAG 'WSS'`) — OPTIONS/DESCRIBE/SETUP/ANNOUNCE/PLAY |
| **48322** | `rtsps://<host>:48322` | Second RTSPS URL; logged as signaling IP for **control ReliableUdp** and hybrid **WebRtcTransport** (audio/mic bypass Mjolnir SETUP) |

Grid control remains HTTPS `:443` (`sessionControlInfo`, `appLevelProtocol: 5`). Media RTSPS uses `appLevelProtocol: 6`.

**Is `:48322` required for video-only?** Official client still opens WebRtcTransport and waits for it even when video SETUP is classic Mjolnir UDP. Audio/mic SETUP is explicitly deferred to WebRtcTransport. A video-only OpenNOW MVP can likely skip audio SETUP, but the official hybrid still binds an early ICE/bundle socket and creates control on the `:48322` host’s **bundle** port — treat `:48322` as required for parity until a handshake-only probe proves otherwise.

### Method sequence (session `XNV1269784751`, host `80-250-97-37…`, ~17:50:25)

| t (log) | Step | Evidence |
|---------|------|----------|
| 25.640 | **OPTIONS** | `200 OK`, `Public: OPTIONS,DESCRIBE,ANNOUNCE,SETUP,TEARDOWN,PLAY,PAUSE,X_NV_COMMAND,X_NV_EVENT`, `X-GS-Version: 14.2` |
| 25.646 | **DESCRIBE** | `200 OK`, `Content-Type: application/sdp`, `Session: XNV…`, `Content-Length: ~216033` (~4093 Nvsc attrs) |
| 25.703 | Control protocol pick | Offered `udp`, `udp_ag`, `tcp`, `udp_enc`, `udp_ag_enc`, `tcp_enc`, `channel_encrypt` → **`udp_ag`**, `streamid=control/10` |
| 25.714 | Audio/mic | **Bypass Mjolnir SETUP** → WebRtcTransport |
| 25.715 | **SETUP** `streamid=video/0/0` | Client local UDP `0.0.0.0:49005`, catalog serverPort hint `47998` |
| 25.746 | SETUP response | `Transport: unicast;X-GS-ServerPort=5004-5005;source=<serverIp>` → peer **`serverIp:5004`** |
| 25.749 | ANNOUNCE prep | Append **151** NvscClientConfig attrs; skip ICE/DTLS fields |
| 25.800–26.358 | **ANNOUNCE** | `200 OK` (~558 ms) |
| 26.359 | `serverEndpoints` dump | See table below |
| 26.422 | Control UDP | `ServerControlReliableUdp` → host from `:48322` URL, **port 48001** (bundle) |
| 26.506–26.619 | **PLAY** | `sessionId: XNV…`, `200 OK` |
| 26.801 | First video payload | `UdpRtpSource::readPacket() received first payload` from `serverIp:5004` |

Same shape repeated on later hosts (`…40`, `…39`) with new HMAC seeds and `Session` ids.

### `streamid` / Transport

| Stream | streamid | Transport / peer |
|--------|----------|------------------|
| Control | `control/10` | Protocol `udp_ag`; ReliableUdp to **bundle 48001** (not the catalog “control 47995” alone) |
| Video[0] | `video/0/0` (SETUP), source `video/0` | SETUP returns **`X-GS-ServerPort=5004-5005`**; client binds ephemeral (e.g. 49005); NAT hole-punch `PING` v6 |
| Audio / mic | (no Mjolnir SETUP) | WebRtcTransport / SCTP |

Client SETUP log line shows an empty `transport` string in the summary logger; server response Transport is authoritative.

### `general.serverEndpoints` (post-ANNOUNCE catalog)

```
RTSP handshake 322
control 47995
UDP control 47999
audio 48000
input 47995
bundle 48001
stream 0 video 47998
stream 1..3 video 48005 / 48008 / 48012
```

Plus serialized:

- `serverEndpoints[0]: 322 4 6`
- `serverEndpoints[1]: 48322 4 6`

**Critical mapping note:** Catalog video port **47998** is **not** the peer used after SETUP. Live video RTP is received from **`source:5004`**. Prefer SETUP `X-GS-ServerPort` over the catalog for the media socket.

Sanitized timeline: [`nvst-rtsp-handshake-fixture.md`](nvst-rtsp-handshake-fixture.md).

---

## 0.2 HMAC / session key

### What we know (evidence)

1. **DESCRIBE SDP** includes `k=HMAC:<64 hex chars>` (32 bytes). Example shape: `k=HMAC:76A28E94…5060965F` (full value in local log only; sample file redacts).
2. Client logs **`Random HMAC seed: <first4>...<last4> [64]`** immediately after parsing DESCRIBE — the DESCRIBE `k=` value **is** the HMAC seed (length 64 hex nibbles).
3. Binary strings (`Bifrost2.dll`): control protocol fallthrough  
   `UDP_AG_ENCRYPT → UDP_ENCRYPT → UDP_AG` and literal `udp_enc` — confirms ordered preference / fallback among encrypted and clear control modes.
4. **Separate from HMAC seed:** after video SETUP, client logs:
   - `Encryption key in RTSPS`
   - `setEncryptionKey` for **control**, **audio**, and **video**
   - `AES Key set keyType 3`, key logged as `1C98...07D2 [64]` (also 64 hex / 32 bytes)
   - `runtime.encryptionKeyId` numeric; `runtime.videoSrtp: 1` (audio/mic SRTP `0` in DESCRIBE feature lines)
5. Video path stats mention **`percPacketsDroppedByAuthFail`** / replay protection → media plane has **auth** (consistent with SRTP or NVST packet MAC), even when control protocol is cleartext `udp_ag`.

### What we do **not** know

- Exact HMAC algorithm name (SHA-256 vs truncated SHA-1, etc.) — not printed; 32-byte seed is consistent with SHA-256-sized material but not proof of usage site.
- Whether HMAC authenticates RTSP messages, SDP bodies, UDP packets, or only seeds key derivation.
- How RTSPS “Encryption key” is derived from HMAC seed vs delivered in another DESCRIBE/ANNOUNCE field.
- Packet MAC layout for SRTP/`keyType 3`.

### Exit criterion: can `udp_ag` MVP proceed without `udp_enc`?

**Yes for control-plane selection.** A real session:

- Offered encrypted protocols,
- **Picked `udp_ag (2) - not encrypted`**,
- Completed ANNOUNCE + PLAY + received video payloads.

**Caveat for media:** `udp_ag` does **not** mean “no crypto anywhere.” Official client still installs an AES/SRTP key for **video**. A handshake-only probe can ignore media crypto; a video MVP almost certainly cannot.

---

## 0.3 UDP video packet layout

### Extracted facts (logs + strings + config)

| Fact | Evidence |
|------|----------|
| Framing class | `UdpRtpSource`, `m=video 0 RTP/AVP`, “RTP extension header version **2**” |
| Peer | `serverIp:5004` after SETUP (not catalog 47998) |
| Packet size target | `video[0].packetSize: 1408` (NCT detected 1432) |
| Dynamic sizes | L0 1024 / L1 1280 optional |
| Extensions | `general.sendFrameSizeInGsExtnHeader: 1` (GS extension carries frame size) |
| FEC | Dynamic FEC enabled; GaloisField28; repairPercent 20 / max 40; `bllFec.enable: 1` |
| NACK | Queue 2048 / maxPackets 1024 / maxPacketCount 64; backoff 4 ms; retries 3 |
| Split encode | `videoSplitEncodeStripsPerFrame: 63` at 240 FPS session |
| Codec (session) | HEVC, applied **1920×1080@240** (client asked 2560×1440@240) |
| Auth | `runtime.videoSrtp: 1`; auth-fail counters on `UdpRtpSource` close |
| Hole punch | `PING` string, ping-version 6, `X-Nv-Ping-Payload` on SETUP |

### Gaps (honest) — partially closed by GO reversal

- **No live pcap / hex dump** → still cannot document **byte offsets** for sequence, FEC markers, or NAL start.
- Annex-B vs length-prefixed after RTP(+ext)+SRTP still unproven on the wire.
- **No reconstructed AU fixture** for `ffprobe` / `h265parse` yet.
- **Mitigation:** processing chain + named fields + Moonlight/GameStream hypothesis documented in [GO reversal research](#go-reversal-research-2026-07-09). Scaffold allowed; byte layout validation deferred to first pcap.

### Fixtures

None reconstructible yet. Do not invent packet bytes; capture steps are in the GO reversal section.

---

## 0.4 UDP input + control vs `InputEncoder`

### OpenNOW encoder (known good on WebRTC datachannel)

[`native/opennow-streamer/src/input.rs`](../../native/opennow-streamer/src/input.rs):

| Wrapper | Value | Use |
|---------|-------|-----|
| Legacy | `0x21` | Mouse move / non-PR gamepad |
| Single | `0x22` | Keys, buttons, wheel |
| Partially reliable | `0x26` | PR gamepad |
| Version marker | `0x23` + BE timestamp | Protocol v3 outer header; batching multiple `0x22` bodies |

Bodies: LE input-type u32 + BE fields (keyboard/mouse/gamepad layouts as implemented).

### Official session observations

| Observation | Implication |
|-------------|-------------|
| `input 47995` in `serverEndpoints` (same port number as **control 47995**) | Catalog claims a classic input UDP port; **no SETUP log line** for `streamid=input/…` in the captured transcript |
| `Remote input stream connection created` right after RTSP connect | Input object exists early; transport not hex-logged |
| `ri.protocol: 0`, `ri.usePartiallyReliableUdpChannel: 0` | Does not prove UDP datagrams vs SCTP |
| Continuous **`SctpTransport`** stats with `totalPartialReliableTxAttempts` growing during play | Uplink (input/QoS) heavily uses **SCTP on WebRtcTransport/bundle**, matching OpenNOW’s current datachannel path more than raw UDP |
| Control: `ServerControlReliableUdp` → **48001** | Control plane ≠ simple fire-and-forget UDP |
| GSHID enabled | Native HID path exists; orthogonal to wrapper bytes |

### Exit criterion

**Met for transport choice (SCTP); not met for UDP.** Official Windows hybrid path creates SCTP channel `input_channel_partially_reliable` (stream-id 10) with growing `SctpTransport` PR TX stats. Catalog `input 47995` is **not** the live uplink. OpenNOW already targets the same PR label + `0x21`/`0x22`/`0x26` wrappers — treat as **GO for SCTP reuse**, with first-session hex validation of wrapper bytes. See [GO reversal research](#go-reversal-research-2026-07-09).

Control keepalive/QoS: command packets logged (`Command packet dump: code 0x020e…`) over ReliableUdp — layout not fully reverse-engineered; not required for handshake probe.

---

## 0.5 ANNOUNCE template allowlist (1080p60)

### Official shape

- DESCRIBE ≈ **4093** server attrs + **54** “feature” `a=x-nv-*` lines (see sample).
- Client **appends ≈ 151** attrs on ANNOUNCE.
- Skipped on classic path: `iceUsernameFragment`, `iceUsernamePwd`, `dtlsFingerprint`.
- Session studied was **1080p240** applied (`clientViewport 1920×1080`, `maxFPS 240`, strips **63**). MVP target is **1080p60** → use strips **3** (OpenNOW non-240 default), `maxFPS:60`, viewport 1920×1080.

### Minimal allowlist (checked-in)

[`nvst-announce-allowlist-1080p60.json`](nvst-announce-allowlist-1080p60.json) — keys the client should be willing to send for a first PLAY attempt. Prefer **server DESCRIBE defaults** for large QoS trees; ANNOUNCE overrides only client-request surface (viewport, fps, codec prefs, ri, pacing toggles, FEC/NACK sizes aligned to catalog).

This is an **allowlist / shape**, not a byte-identical capture of the 151-attr body (ANNOUNCE SDP body was not fully dumped to geronimo).

---

## 0.6 Go / no-go matrix

| Work item | Decision | Rationale |
|-----------|----------|-----------|
| RTSPS WSS handshake (`OPTIONS`…`PLAY`) | **GO (probe)** | Full method/header timeline + streamids + SETUP Transport evidenced |
| Prefer `udp_ag` | **GO** | Live selection without `udp_enc` |
| UDP video decode path | **GO-with-Moonlight-hypothesis** | Stack + field names proven; byte layout/AU fixture still need first pcap |
| Input over NVST **UDP** using `InputEncoder` | **NO-GO** | Catalog `input 47995` unused in live path; `ri.usePartiallyReliableUdpChannel: 0` |
| Input over **SCTP/datachannel** using `InputEncoder` | **GO** | Channel labels + SCTP stats match OpenNOW path; validate wrappers on first session |
| Settings `transportMode` + fallback | **GO (parallel)** | Keep WebRTC fallback until video AU path is proven live |
| Product “NVST streaming” | **NO-GO** | Do not claim until Phase 3–4 succeed with live decode + input |

### Narrow implementation scope (if proceeding past research)

1. Preserve both `rtsps://…:322` and `:48322`.
2. Implement WSS RTSP client matching fixture headers (`X-GS-Version`, `Session`, `CSeq` / `Request-Id` behavior as observed).
3. Parse DESCRIBE `k=HMAC:` (store seed; do not invent MAC usage).
4. SETUP only `video/0/0`; skip audio/mic Mjolnir SETUP.
5. ANNOUNCE from 1080p60 allowlist; PLAY; log peers from SETUP `X-GS-ServerPort`.
6. Video scaffold: hole-punch PING v6 → UDP recv from `:5004` → libsrtp → RTP+GS-ext v2 → ReedSolomon FEC → H265/H264 AU (Moonlight as starting point).
7. Input: open SCTP/datachannels with official labels; send OpenNOW `InputEncoder` payloads; do **not** invent a separate “NVST input UDP” codec.
8. Capture pcap on first live session (filters below) before claiming decode/input success.

---

## GO reversal research (2026-07-09)

Second pass over Bifrost2.dll strings + full `geronimo.log` to reverse the Phase 0 **NO-GO / narrow** on media + input. Sources: official install under `%LOCALAPPDATA%\NVIDIA Corporation\GeForceNOW\` (`CEF\Bifrost2.dll`, `geronimo.log`); OpenNOW `native/opennow-streamer/src/input.rs` + `gstreamer_input.rs`; public Moonlight/GameStream knowledge used **only** as a hypothesis to verify against NVIDIA evidence.

### Capture inventory

| Location searched | Result |
|-------------------|--------|
| `GeForceNOW\` (incl. `logs\`, `CEF\`, `CefCache\`) | **No** `.pcap` / `.pcapng` / `.etl` / `.cap` |
| `%LOCALAPPDATA%\Temp`, `%TEMP%` | **No** media captures |
| OpenNOW repo `docs/research` and tree | **No** packet captures |

User must capture on the next official (or OpenNOW probe) session — see Wireshark steps below.

### Video: best-effort packet / pipeline model

#### Proven processing chain (logs + RTTI/format strings)

```
UDP peer serverIp:5004  (SETUP X-GS-ServerPort; catalog 47998 is NOT the live peer)
  → UdpRtpSource::readPacket  (+ PING v6 hole-punch on client ephemeral, e.g. 49005)
  → SecureRtp / libsrtp2 2.7.0   (runtime.videoSrtp:1; "SRTP for video is enabled")
  → RtpSourceQueue / RtpSourceQueueExtV2  (seq/ts/frame num; NACK queue)
  → ReedSolomonFecDecoder + GaloisField28  (vqos.fec.type:1, Dynamic FEC enabled)
  → NvstStreamProcessor::processBuffer(RtpPacket) / dePacketize
  → H265Depacketizer | H264Depacketizer | NvstSliceDepacketizer | NvstDePacketizer
  → frame-based depacketizer (session log: "Initialized the frame-based depacketizer")
```

Evidence citations:

| Fact | Source |
|------|--------|
| Class `UdpRtpSource`, first payload from `:5004` | geronimo `[NVST:UdpRtpSource]` |
| RTP extension header **version 2** | geronimo `Parser server RTP extension header version 2` |
| `general.sendFrameSizeInGsExtnHeader: 1` | geronimo NvscClientConfig |
| SRTP via **libsrtp2 2.7.0**, video only | geronimo `SecureRtp` / `SRTP for video is enabled`; audio/mic SRTP `0` |
| Auth/replay counters on UDP path | `percPacketsDroppedByAuthFail` / `ReplayProtection` on `UdpRtpSource` close |
| FEC: ReedSolomon + GF(2^8), `fec.type:1`, repair 20%/max 40% | Bifrost `ReedSolomonFecDecoder`, `GaloisField28`; geronimo `Dynamic FEC is enabled` |
| Depacketizers for H264/H265/slice/frame | Bifrost RTTI `H264Depacketizer`, `H265Depacketizer`, `NvstSliceDepacketizer`; geronimo frame-based init |
| Target UDP payload size | `video[0].packetSize: 1408` (NCT 1432) |
| GameStream lineage (not Moonlight brand) | Bifrost strings `NVIDIA GameStream`, `GAMESTREAM_CONTROL`, `GAMESTREAM_SECURE_CONTROL` |

#### Best-effort header / field inventory (names only — **offsets unknown without pcap**)

From Bifrost format strings (see `nvst-binary-strings-bifrost2-wire-curated.txt`):

| Layer | Fields named in binary | Notes |
|-------|------------------------|-------|
| UDP / hole punch | ping-version **6**, ping-string `PING`, `X-Nv-Ping-Payload` | Pre-media NAT; not RTP |
| RTP | `seq`, `ts` (timestamp), payload type, SSRC (implied by RTP stack) | Standard RTP after SRTP decrypt |
| Queue / frame | `frame num`, `size`, `bIsLate`, `ECN`, “source” vs FEC packet | `RtpSourceQueue: return %spacket SEQ %u, size %u frame num %u…` |
| GS / NvST extension | `gsHeaderLength`, `gsHeaderExtensionLength`, frame size in GS extn | Tied to RTP ext **v2** + `sendFrameSizeInGsExtnHeader` |
| FEC | `pktid`, `fecgrpid`, `fec%`, `srcpkts`, `packetType`, frame id/size | ReedSolomon; FEC packets must not assemble into frame payload |
| NAL / AU | STAP-A / `nalusSplit`, startcode checks, H265 packetize/depacketize | Suggests Annex-B-ish NAL handling **after** RTP+FEC reassembly — **not proven on wire** |

**Still unknown (blockers for “verified AU”):** SRTP crypto suite + key derivation from RTSPS `keyType 3` AES material; exact RTP extension ID/length/layout for GS v2; whether FEC is in-band RTP PT or NVIDIA custom; Annex-B vs length-prefixed AU after depacketize.

#### Moonlight / GameStream hypothesis — verification status

| Moonlight/GameStream public trait | NVST evidence | Match? |
|-----------------------------------|---------------|--------|
| UDP RTP video (`RTP/AVP`) | `m=video 0 RTP/AVP`, `UdpRtpSource` | **Consistent** |
| Custom NVIDIA RTP / “GS” extension carrying frame metadata | RTP ext **v2**, `GsExtnHeader`, `gsHeaderLength` | **Consistent (names)** |
| Reed-Solomon FEC over GF(2^8) with fec%/src packet counts | `ReedSolomonFec*`, `GaloisField28`, format fields `fec%`/`srcpkts`/`fecgrpid`/`pktid` | **Consistent (names)** |
| H264/H265 NAL depacketize to access units | `H264Depacketizer` / `H265Depacketizer` / frame-based depacketizer | **Consistent** |
| Cleartext RTP (classic Sunshine/Moonlight often AES-GCM or none depending era) | **`runtime.videoSrtp:1` + libsrtp2** | **Divergence** — NVST wraps media in SRTP |
| Control/input on separate GameStream ports | Hybrid: video Mjolnir UDP + **SCTP datachannels** on bundle | **Divergence** for input |

**Conclusion:** Moonlight RTP+FEC is a **reasonable starting implementation hypothesis** for NVST `UdpRtpSource` post-SRTP payload layout, backed by GameStream lineage strings and matching FEC/depacketizer class names. It is **not** a verified byte-identical match. Do not invent OpenNOW packet bytes from Moonlight alone.

#### Wireshark / capture steps (required on first live session)

1. Install Npcap; start capture **before** PLAY on the interface used for GFN.
2. From geronimo, note `source=<serverIpv4>` and client ephemeral (e.g. `49005`) after SETUP.
3. Display filter (adjust IPs/ports):

```
udp and ip.addr == <serverIpv4> and (udp.port == 5004 or udp.port == 5005 or udp.port == 48001 or udp.port == 47995)
```

Optional: `rtp` after disabling SRTP (or use “Decode As” once keys known); also capture DTLS/SCTP on the WebRtcTransport/bundle peer for input.

4. Export ~2–5 s of video UDP after first frame; save as `docs/research/fixtures/nvst-video-<date>.pcapng` (local only — do not commit secrets).
5. Acceptance for full video **GO**: decrypt/assemble ≥1 HEVC (or H264) AU that `ffprobe`/`h265parse` accepts; document byte offsets for RTP hdr, GS ext v2, FEC vs media `packetType`.

### Input: UDP vs SCTP (settled for official Windows client)

#### Verdict: **SCTP datachannels**, not catalog input UDP

| Evidence | Implication |
|----------|-------------|
| `ri.usePartiallyReliableUdpChannel: 0`, `ri.protocol: 0` | Classic UDP PR input path **off** |
| Continuous `SctpTransport` stats (`totalPartialReliableTxAttempts` growing during play) | Uplink traffic is SCTP |
| `general.rtcDataChannelOnNativeBundle: 1`, `rtcpOnSctp: 1` | Datachannels on native bundle |
| Channel create log (stream-id → label) | Explicit SCTP channel map (below) |
| Catalog `input 47995` (= control port) with **no** `SETUP streamid=input` | Legacy/alternate; unused for this hybrid session |
| OpenNOW already uses WebRTC datachannels with the same PR label | Reuse path, not invent UDP framing |

#### Official SCTP channel map (geronimo)

| stream-id | label | Role (inferred) |
|-----------|-------|-----------------|
| 0 | `control_channel_reliable` | Control |
| 2 | `custom_message_on_sctp_private_reliable` | Private reliable msgs |
| 4 | `custom_message_on_sctp_private_partially_reliable` | Private PR msgs |
| 6 | `control_channel_partially_reliable` | Control PR |
| 8 | `control_channel_unreliable` | Control unreliable |
| 10 | `input_channel_partially_reliable` | **Input (PR)** — `maxRetransmitTime = 300` |

SCTP datachannel init also logged for `channelId` 0,2,4,6,8,10 (same set).

**PPID:** Bifrost has format `, ppid=` / `Invalid ppid %d` and WebRTC `UDP/DTLS/SCTP webrtc-datachannel`, but **no numeric PPID** appeared in geronimo. Expect standard WebRTC datachannel PPIDs (DCEP / binary / string) unless a capture shows otherwise.

#### OpenNOW `InputEncoder` comparison

| OpenNOW (`input.rs` / `gstreamer_input.rs`) | Official NVST (this pass) |
|---------------------------------------------|---------------------------|
| Wrappers `0x21` legacy, `0x22` single, `0x26` PR, outer `0x23`+BE timestamp | **Not hex-logged** in geronimo; no contradiction; treat as already-validated GFN codec for datachannel |
| Labels `input_channel_v1` (reliable) + `input_channel_partially_reliable` | Official creates **`input_channel_partially_reliable`** (stream 10); reliable `input_channel_v1` not named in this log — may be WebRTC-only or aliased via control/custom channels |
| Bodies: LE input-type u32 + BE fields | Unchanged assumption for SCTP payload |

**Recommendation:** Implement NVST classic hybrid input as **SCTP/datachannel send of existing `InputEncoder` bytes**, prioritizing `input_channel_partially_reliable`. Do **not** send wrappers as raw UDP to `47995` unless a future capture proves that alternate path.

### Explicit recommendation

| Question | Answer |
|----------|--------|
| Flip Phase 0 media/input gate? | **Yes → GO-with-Moonlight-hypothesis** (not full unconditional GO) |
| Video depacketize into HEVC/H264 AUs? | **Start scaffold** (UDP→SRTP→RTP/GS-ext→RS-FEC→H265/H264). **Validate** Moonlight field layout + produce AU fixture on first pcap. |
| Input uplink? | **GO on SCTP**; **NO-GO on NVST input UDP**. Reuse OpenNOW wrappers/labels. |
| Must validate on first live session | (1) SRTP suite/key install, (2) GS ext v2 offsets + FEC `packetType`, (3) one `ffprobe`-clean AU, (4) datachannel payload hex starts with `0x23`/`0x21`/`0x22`/`0x26`, (5) whether reliable input uses `input_channel_v1` or another label |

---

## Research completeness checklist

- [x] 0.1 RTSP transcript summary (methods, ports, streamids, serverEndpoints, SETUP 5004)
- [x] 0.2 HMAC / `udp_ag` vs `udp_enc` with live-session evidence + media crypto caveat
- [x] 0.3 Video: stack + field names + Moonlight hypothesis status + pcap steps
- [x] 0.4 Input: **SCTP proven**; channel map; OpenNOW wrapper/label comparison
- [x] 0.5 1080p60 ANNOUNCE allowlist fixture
- [x] 0.6 Explicit **GO-with-Moonlight-hypothesis** verdict
- [x] GO reversal research section (Bifrost2 + geronimo + capture inventory)

**Phase 0 research gate: COMPLETE — verdict GO-with-Moonlight-hypothesis.**
