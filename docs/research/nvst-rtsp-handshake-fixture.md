# NVST RTSP-over-WSS handshake fixture (sanitized)

Source: `geronimo.log` 2026-07-08 ~17:50:25, host `80-250-97-37.cloudmatchbeta.nvidiagrid.net`, `X-GS-Version: 14.2`.  
Secrets redacted. Use as behavioral template for a handshake probe — not a byte-perfect replay.

## ConnectionInfo

```
rtsps://<host>:322   usage=14  NVB_PU_RTSPS
rtsps://<host>:48322 usage=14  NVB_PU_RTSPS
```

WSS session established to **:322** (`protocol TAG 'WSS'`).

## OPTIONS

**Request:** OPTIONS on `rtsps://<host>:322`

**Response:**

```
HTTP/1.0 200 OK
Request-Id: 1
CSeq: 1
X-GS-Version: 14.2
Public: OPTIONS,DESCRIBE,ANNOUNCE,SETUP,TEARDOWN,PLAY,PAUSE,X_NV_COMMAND,X_NV_EVENT
```

Client notes: server RTP extension header version **2**.

## DESCRIBE

**Response header:**

```
HTTP/1.0 200 OK
Request-Id: 2
CSeq: 2
X-GS-Version: 14.2
Content-Type: application/sdp
Session: XNV<REDACTED>
Content-Length: 216033
```

**SDP head (sanitized):** see `nvst-describe-sdp-sample-1.txt`

```
v=0
o=NvStreamer <n> 14 IN IPv4 <host>
s=NVIDIA Streaming Session
k=HMAC:<64_HEX_REDACTED>
a=x-nv-general.…   (54 feature lines in dump; ~4093 Nvsc attrs total)
t=0 0
m=video 0 RTP/AVP
```

Post-parse:

- `Random HMAC seed: <trunc> [64]`
- Control protocols offered → **selected `udp_ag`**, `controlControlId: streamid=control/10`
- Audio/mic: bypass Mjolnir SETUP → WebRtcTransport

## SETUP video

**Request summary:** `streamid=video/0/0`  
Client binds UDP e.g. `0.0.0.0:49005` (catalog hint serverPort 47998).

**Response:**

```
HTTP/1.0 200 OK
Request-Id: 3
CSeq: 3
X-GS-Version: 14.2
Transport: unicast;X-GS-ServerPort=5004-5005;source=<serverIpv4>
Session: XNV<REDACTED>
X-Nv-Ping-Payload: <token>
x-nv-ping: 6
```

Effective video peer: **`<serverIpv4>:5004`**.

Then (still pre-ANNOUNCE): RTSPS encryption key installed for control/audio/video (`AES Key set keyType 3`); ANNOUNCE SDP append **151** attrs (skip ICE/DTLS fields).

## ANNOUNCE

**Request:** ANNOUNCE with client Nvsc overrides (~151 attrs).  
**Response:** `HTTP/1.0 200 OK` (observed ~500–560 ms).

Post-ANNOUNCE `general.serverEndpoints` catalog:

```
RTSP handshake 322
control 47995
UDP control 47999
audio 48000
input 47995
bundle 48001
stream 0 video 47998
(+ extra video stream ports 48005/48008/48012)
serverEndpoints[0]: 322 4 6
serverEndpoints[1]: 48322 4 6
```

Control connection: ReliableUdp to `<host-from-48322>` **port 48001** (bundle).

## PLAY

**Request:** PLAY, `sessionId: XNV<REDACTED>`  
**Response:** `HTTP/1.0 200 OK` (`Request-Id: 5`, `CSeq: 5`)

First video payload shortly after from `<serverIpv4>:5004` (`UdpRtpSource::readPacket`).

## Notes for implementers

1. Prefer SETUP `X-GS-ServerPort` over catalog video port 47998.
2. `:48322` remains in official hybrid path (WebRtcTransport + control host).
3. `udp_ag` selected does not disable video SRTP key install.
