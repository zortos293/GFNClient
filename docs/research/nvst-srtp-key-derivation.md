# NVST SRTP key derivation (AES → master key/salt)

Sources: local `geronimo.log` (2026-07-08, three sessions), `CEF\Bifrost2.dll` ASCII strings.  
No full AES key material is committed here — log truncations only (`1C98…07D2`).

## Verdict

| Question | Answer |
|---|---|
| Where does the 64-hex AES key come from? | **RTSPS session material exposed as `NvstClientRuntimeEncryptionKey` / `runtime.encryptionKey` (+ `encryptionKeyId`)**, logged as **`Encryption key in RTSPS`**. Not SETUP headers, not `k=HMAC:…`, not the DTLS-SRTP exporter path. |
| Best concrete carrier | **Almost certainly DESCRIBE SDP Nvsc attrs** `x-nv-runtime.encryptionKey` + `x-nv-runtime.encryptionKeyId` (among the ~4093 attrs; truncated “new features” dump omits them). Client-generated-then-ANNOUNCE remains a weaker alternate. |
| 64 → 88 hex packing | **Confirmed:** `master[44] = AES-256 key[32] \|\| salt[12]`, salt = **big-endian key ID zero-padded to 12 bytes** (`printf`-style `%024x`). |
| Can OpenNOW’s current RTSPS probe extract it? | **Not yet.** Probe only pulls `k=HMAC:`. Need full DESCRIBE parse for `encryptionKey`/`encryptionKeyId` (or confirm client-gen). TLS exporter is the wrong path for Mjolnir video. |

## Timeline (first session)

| Time | Event |
|---|---|
| 25.697 | DESCRIBE 200, `Content-Length: 216033`, `Session: XNV…` |
| 25.698 | Truncated SDP “new features” dump includes `k=HMAC:76A2…965F` and `audioSrtp`/`micSrtp` — **no** `encryptionKey` |
| 25.707 | `Random HMAC seed: 76A2…965F [64]` (= DESCRIBE `k=`) |
| 25.713 | `Successfully read 4093 NvscClientConfig attributes from SDP` |
| 25.746 | SETUP video 200 — headers only: `Transport`, `Session`, `X-Nv-Ping-Payload`, `x-nv-ping` |
| 25.747 | **`Encryption key in RTSPS`** → `setEncryptionKey` control/audio/video → `key ID 2664076126`, `key: 1C98…07D2 [64]`, `AES Key set keyType 3` |
| 25.747 | `AppendNvscConfigToSdp` (151 attrs) → ANNOUNCE |
| 26.365 | Config dump: `runtime.videoSrtp: 1`, `runtime.encryptionKeyId: -1630891170` |
| 26.503 | `AES 256 key and key ID to master key and salt directly` → `set master key/salt to 1C98…935E [88]` → `SRTP for video is enabled` |

## Ruled out

### DESCRIBE `k=HMAC:` is not the AES key

| Session | HMAC seed (DESCRIBE `k=`) | AES key (truncated) |
|---|---|---|
| 1 | `76A2…965F` | `1C98…07D2` |
| 2 | `E30D…0D60` | `3567…503E` |
| 3 | `C016…1A17` | `261A…E344` |

Prefixes/suffixes differ; HMAC is logged separately as **Random HMAC seed**.

### SETUP response headers do not carry the key

Observed SETUP 200 headers:

```
Transport: unicast;X-GS-ServerPort=5004-5005;source=<ip>
Session: XNV…
X-Nv-Ping-Payload: <token>
x-nv-ping: 6
```

No `X-Nv-Enc*`, no key hex, no SDP body.

### Truncated DESCRIBE “features” dump has no key hex

First-session feature dump (through `m=video`) contains only one 64-hex value: the HMAC line. No other `[0-9A-F]{64}` / `{88}` appears around SETUP→key install. Full DESCRIBE body was never persisted to disk in this capture.

### DTLS `SSL_export_keying_material` is a different path

Bifrost2 ties `SSL_export_keying_material failed` to **`EXTRACTOR-dtls_srtp`** (WebRTC audio/mic SRTP). Mjolnir video uses **`Separate SRTP setup for Mjolnir video`** + `cryptoutils` packing from AES key + key ID. No RTSPS-specific exporter label string was found.

## Best evidence: key source

### Positive signals

1. **Log literal:** `Encryption key in RTSPS` immediately after successful video SETUP, before ANNOUNCE.
2. **Runtime object:** `NvstClientRuntimeEncryptionKey` → `getEncryptionKey` / `setEncryptionKey`; failure string `failed to generate AesParams from (bogus?) NvstClientRuntimeEncryptionKey`.
3. **Config / SDP field names in Bifrost2:**
   - `runtime.encryptionKey`, `runtime.encryptionKeyId`
   - `x-nv-runtime.encryptionKey`, `x-nv-runtime.encryptionKeyId`
4. **Timing:** key is installed **before** ANNOUNCE, so it is already known from DESCRIBE parse and/or local generation at that point — not from ANNOUNCE response.
5. **Same key** applied to control, audio, and video (`keyType 3`).

### Confidence ranking

| Hypothesis | Confidence | Notes |
|---|---|---|
| DESCRIBE Nvsc attrs `x-nv-runtime.encryptionKey` + `encryptionKeyId` | **High** | Matches field names, “in RTSPS”, 4093 attrs unread in truncated dump |
| Client-generated (`RAND_bytes`) then sent in ANNOUNCE | Medium | Fits pre-ANNOUNCE install + append; Moonlight `rikey`/`rikeyid` analogy; needs ANNOUNCE body capture |
| TLS exporter on RTSPS/WSS | Low | No NVST exporter label; exporter strings sit on DTLS-SRTP path |
| Derived from HMAC seed | **Ruled out** | Distinct values every session |
| SETUP header | **Ruled out** | Headers logged in full |

**Next capture to lock source:** dump full DESCRIBE SDP and search for `encryptionKey` / `encryptionKeyId`; if absent, dump ANNOUNCE body for the same attrs.

## SRTP packing: 64 hex → 88 hex

### Layout (confirmed)

```
88 hex chars = 44 bytes
[0..31]  AES-256 master key   (32 bytes)  ← same as logged 64-hex key
[32..43] master salt          (12 bytes)  ← key ID as %024x (BE, zero-padded)
```

Bifrost2 string next to the packer: **`AES 256 key and key ID to master key and salt directly`**, plus format **`%024x`**.

### Cross-check (all three sessions)

| key ID (u32 log) | hex | as i32 (`runtime.encryptionKeyId`) | master/salt suffix | `%024x` salt |
|---|---|---|---|---|
| 2664076126 | `9ECA935E` | -1630891170 | `…935E` | `00000000000000009ECA935E` |
| 1664590642 | `6337A332` | 1664590642 | `…A332` | `00000000000000006337A332` |
| 2478780175 | `93BF2F0F` | -1816187121 | `…2F0F` | `000000000000000093BF2F0F` |

So:

```text
master_key_salt_hex =
  aes256_key_hex (64 chars)
  + f"{key_id:024x}"          # 24 hex chars, big-endian u32 in low 4 bytes
```

Example shape (redacted): `1C98…07D2` + `00000000000000009ECA935E` → `1C98…935E` [88].

### libsrtp profile

- `Using libsrtp2 2.7.0`
- Preferred profiles string: `SRTP_AEAD_AES_256_GCM:SRTP_AEAD_AES_128_GCM:SRTP_AES128_CM_SHA1_80`
- `runtime.videoSrtp: 1` (audio/mic SRTP remain 0 on classic path; WebRTC has built-in crypto)

AEAD-AES-256-GCM expects 32-byte key + 12-byte salt — matches this packing exactly (no RFC 3711 KDF from a longer master; NVIDIA packs “directly”).

## Bifrost2 string evidence (curated)

Saved: `docs/research/nvst-binary-strings-bifrost2-srtp-keys.txt`

Notable:

- `Encryption key in RTSPS`
- `setEncryptionKey` / `getEncryptionKey` / `key ID %u` / `key: `
- `AES Key set keyType %d` / `AES Key set from raw key, keyLength %d`
- `cryptoutils` / `AES 256 key and key ID to master key and salt directly` / `%024x`
- `set master key/salt to ` / `SecureRtp` / `Separate SRTP setup for Mjolnir video`
- `x-nv-runtime.encryptionKey` / `x-nv-runtime.encryptionKeyId`
- `SSL_export_keying_material failed` + `EXTRACTOR-dtls_srtp` (DTLS path only)
- `Random HMAC seed:` (separate from AES)

## OpenNOW implications

Current `nvstRtspProbe.ts`:

- Parses DESCRIBE `k=HMAC:` only.
- Does not scan for `x-nv-runtime.encryptionKey` / `encryptionKeyId`.
- Uses Node `ws` TLS; does not call `SSL_export_keying_material` (and that API is the wrong path for Mjolnir video anyway).

**To obtain the video SRTP key from the handshake we already implement:**

1. Persist/parse the **full DESCRIBE body** for:
   - `a=x-nv-runtime.encryptionKey:<64 hex>`
   - `a=x-nv-runtime.encryptionKeyId:<int>`
2. If missing in DESCRIBE, capture **ANNOUNCE** attrs (client-gen hypothesis).
3. Pack with the layout above and feed libsrtp `SRTP_AEAD_AES_256_GCM` (or confirm suite on first decrypt).

Until step 1/2 succeeds on a live DESCRIBE, OpenNOW **cannot** reconstruct `1C98…`-class keys from HMAC or SETUP alone.
