# NVST / Geronimo SDP catalog (Phase 0)

Source: official GFN Windows client logs + read-only binary strings  
Install: `C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\` (observed session 2026-07-08)  
Artifacts (sanitized): `nvst-describe-sdp-sample-*.txt`, `nvst-session-snippets.txt`, `nvst-xnv-keys.json`, `nvst-config-values.json`, `nvst-binary-strings*.txt`

**No NVIDIA binaries or decompiled sources are committed.**

---

## 1. Session shape

| Item | Observed |
|------|----------|
| Client | `nvstCreateClient` (POCO 1.14.1 + OpenSSL 3.5.6) |
| Grid control | `sessionControlInfo` HTTPS `:443`, `appLevelProtocol: 5` |
| Media / signaling | `rtsps://…:322` + `rtsps://…:48322`, `appLevelProtocol: 6`, usage RTSPS |
| Signaling transport | Secure WebSocket over RTSPS (`WSS Options` / `WSS Describe`) |
| RTSP methods | `OPTIONS,DESCRIBE,ANNOUNCE,SETUP,TEARDOWN,PLAY,PAUSE,X_NV_COMMAND,X_NV_EVENT` |
| Control protocols offered | `udp_ag`, `udp_enc`, `udp_ag_enc`, `channel_encrypt` |
| Control selected (sample) | `udp_ag` (not encrypted) |
| Streams | video, audio, mic, input |
| Local present | `DXGI_SWAP_EFFECT_FLIP_SEQUENTIAL`, `FrameLatencyWaitableObject` |
| Local input | `Enabling GSHID`, native HID device list |
| Sample request | 2560×1440@240 client request; Nvsc applied 1920×1080@240 HEVC, max bitrate 100000 kbps (recommended ~45229) |
| SDP scale | Server DESCRIBE ≈ **4093** `NvscClientConfig` attrs; client ANNOUNCE appends ≈ **151**; logged `a=x-nv-*` dump ≈ **54** keys |

Naming:

- Classic NVST DESCRIBE: `a=x-nv-vqos[0].foo:…`
- OpenNOW WebRTC companion SDP: `a=vqos.foo:…` (no `x-nv-` / `[0]`)

---

## 2. DESCRIBE `a=x-nv-*` inventory (54 keys)

Groups from logged DESCRIBE body (`nvst-describe-sdp-sample-1.txt`):

### general (transport / RTC bundle / ping)
- `enetControlChannel.mtuSize:1191`
- `customMessageOnCC:1`, `rtspWebSocketPerConnection:1`
- `rtcAudioOnNativeBundle:1`, `rtcMicOnNativeBundle:1`, `nativeRtcOnBundlePort:1`
- `maxQosMessagesSize:1071`
- `pingIntervalBeforeConnectionMs:20`, `pingIntervalAfterConnectionMs:100`
- `wifiInfoInterval:15`, `enableUDSWifiCongestion:1`, `enableUDSWifiStandard:1`
- `transitSelectionSettings.enableDynamicTransitSelection:1`, `…selectionEnforcementMethod:1`

### video
- `videoSplitEncodeStripsPerFrame:63` (**240 FPS session**)
- `enableAv1RcPrecisionFactor:1`
- `adaptiveQuantization.*` (spatialAQSetting 7, spatialAQStrength 12, temporalAQ 0, SAQ adapt thresholds, perfAdjEnablement 1)
- `framePacing.pid.minTargetFrameTimeUs:7936`

### vqos
- `grc.enable:0`
- `calculateAvgVideoStreamingBitrate:1`
- `statsProcessorThread.flags:1023`, `sendEndOfSessionQosTelemetry:1`
- `relaxMaxBitrate.*` (override/custom avg bitrate & QP thresholds, iirFilterFactor 120)
- `qpDelta.*` (max/min percent, VBV factors per codec, IIR/throttle)

### aqos
- `enableRedundancy:1`, `redundancyLevel:2`
- `enableRedundancyForMic:1`, `redundancyLevelForMic:3`

### ri (input)
- `hidDeviceMask:4` (session-specific; not “all devices”)
- `partialReliableThresholdMs:300`

### packetPacing / audio / runtime
- `packetPacing.enableAccurateSleep:1`
- `audio.enableDynamicAudioConfig:1`, `audio.enableTimestampAudioBuffer:1`
- `runtime.maxVerboseEtlSizeMb:60`, `runtime.audioSrtp:0`, `runtime.micSrtp:0`

Full key list: `nvst-xnv-keys.json`.

---

## 3. Broader NvscClientConfig (≈942 keys with values)

Beyond the 54 DESCRIBE lines, config dumps expose the full QoS surface OpenNOW already partially mirrors:

| Area | Official samples (high signal) | OpenNOW today (pre-Phase-0) |
|------|--------------------------------|-----------------------------|
| packetPacing | `version:3`, `mode:1`, `numGroups:5`, `maxDelayUs:1000`, `minNumPacketsFrame:10`, `minNumPacketsPerGroup:0`, `enableAccurateSleep:1`, `enableSmoothTransition:1`, `allowFpsBasedToggle:1` | subset; `minNumPacketsPerGroup:15`; no accurate-sleep / version / mode |
| framePacing | `mode:2`, PID + jitter history; `minTargetFrameTimeUs:7936` | **missing** |
| FEC | `fec.enable:1`, repairPercent **20**, repairMax **40**; `bllFec.enable:1` | repair 5/5/35; `bllFec.enable:0` |
| NACK queues | length **2048**, maxPackets **1024**, maxPacketCount **64** | 1024 / 512 / 25 |
| split encode @240 | strips **63**, dynamic updates **1** | strips **3** |
| DRC/DFC/GRC | large trees; client requests DRC/dynamic res off | mostly aligned (drc.enable 0, dfc.adjustResAndFps 0) |
| L4S | `video.l4sHandling.*` (~30 keys) | **missing** |
| ri | threshold **300**; gamepad PR mask **255**; HID PR **-1** | threshold via offer/default 300; masks “all” |
| aqos redundancy | as DESCRIBE | **missing** |
| adaptiveQuantization | as DESCRIBE | **missing** (had qpg.enable only) |
| relaxMaxBitrate / qpDelta | as DESCRIBE | **missing** |

Overlap: **88 / ~100** OpenNOW companion keys appear in official dumps. See `nvst-config-values.json`.

---

## 4. Mapping: official ↔ OpenNOW

| Official | OpenNOW companion | Notes |
|----------|-------------------|--------|
| `a=x-nv-vqos[0].foo` | `a=vqos.foo` | Drop `x-nv-` and `[N]` |
| `a=x-nv-video[0].foo` | `a=video.foo` | Same |
| `a=x-nv-ri.foo` | `a=ri.foo` | Same |
| `a=x-nv-packetPacing.foo` | `a=packetPacing.foo` | Same |
| `a=x-nv-general.iceUsernamePwd` | `a=general.icePassword` | WebRTC path naming |
| `a=x-nv-general.iceUsernameFragment` | `a=general.iceUserNameFragment` | WebRTC path naming |
| DESCRIBE `k=HMAC:…` + enet/udp_* | N/A | Classic NVST media plane only |
| WebRTC ICE/DTLS in companion | Present | OpenNOW path |

Client-request vs server-authoritative (heuristic):

- **Client-request / ANNOUNCE:** viewport, maxFPS, bitrates, DFC/DRC enable flags, ri masks/threshold, packetPacing toggles, split-encode, AQ, aqos redundancy.
- **Server-authoritative / DESCRIBE defaults:** large Nvsc trees (L4S, enet CWND, FEC internals), then client may override a subset on ANNOUNCE (~151 attrs).

---

## 5. Prioritized gap list

### P0 — apply to WebRTC `nvstSdp` (high confidence)
1. `packetPacing.enableAccurateSleep:1` (+ version/mode/smooth/fps-toggle)
2. Align `packetPacing.minNumPacketsPerGroup` → `0`; keep `numGroups`/`maxDelayUs`/`minNumPacketsFrame`
3. `video.framePacing.mode:2` + `video.framePacing.pid.minTargetFrameTimeUs` (fps-derived)
4. 240 FPS `video.videoSplitEncodeStripsPerFrame` → **63**
5. FEC repairPercent **20**, repairMaxPercent **40**; `vqos.bllFec.enable:1`
6. NACK queue sizes → 2048 / 1024 / 64
7. DESCRIBE AQ block (`video.adaptiveQuantization.*`)
8. `vqos.relaxMaxBitrate.*` + `vqos.qpDelta.*` + `vqos.calculateAvgVideoStreamingBitrate:1`
9. `aqos.enableRedundancy*` / levels; `audio.enableDynamicAudioConfig` / `enableTimestampAudioBuffer`

### P1 — research further (do not blindly copy)
- Full `video.l4sHandling.*` / ECN (needs WebRTC path validation)
- `ri.hidDeviceMask:4` (session-specific; OpenNOW “all” is intentional for browser HID)
- `video.packetSize:1408` (official NCT-driven; OpenNOW 1140 may be safer on WebRTC MTU)
- `video.prefilterParams.prefilterModel:4` vs OpenNOW `0`
- Transport-only general.* (enet, WSS-per-connection, UDS wifi)

### P2 — full NVST media client only
- RTSPS + WSS DESCRIBE/SETUP/PLAY
- `udp_enc` / `channel_encrypt` / HMAC session key
- GSHID native input path
- DX11 FLIP_SEQUENTIAL present path

---

## 6. Binary string pass

| Binary | Hits |
|--------|------|
| `CEF\Bifrost2.dll` | Primary NVST/NVSC surface: `x-nv-`, `udp_enc`, `packetPacing`, `enableAccurateSleep`, `NvscClientConfig` (5249 filtered strings → `nvst-binary-strings-bifrost2.txt`) |
| `CEF\Geronimo.dll` | `GSHID`, `NvscClientConfig`, frame pacing / Nvst* types (`nvst-binary-strings.txt`) |
| `CEF\dependencies\CrimsonUtil.dll` | Crimson util only; no SDP keys |
| `CEF\dependencies\NetworkTestSDK.dll` | `x-nv-` |
| `CEF\libcef.dll` | No Phase-0 needles of interest |

---

## 7. Go / no-go: full NVST media client

**Verdict: NO-GO for product path (keep WebRTC).**

Reasons:

1. Media plane is a second stack (RTSPS/WSS, custom UDP encrypt, enet control, HMAC), not a thin SDP tweak.
2. ~4k server config attrs + crypto/control protocols imply large reverse-engineering and breakage risk on Grid updates.
3. OpenNOW already has a working WebRTC companion `nvstSdp`; Phase 0 can close the largest QoS/pacing gaps there with low risk.
4. GSHID + DX flip present are local-feel items orthogonal to replacing WebRTC transport.

**Revisit when:** P0 companion deltas are measured (decode/sink FPS, input lag, bitrate stability) and still leave a clear gap that only classic NVST transport can close.

---

## 8. Phase 0 apply notes

Applied to `buildNvstSdp` (TS) + `build_nvst_sdp` (Rust):

| Attribute family | Change |
|------------------|--------|
| FEC | repairPercent 20, repairMaxPercent 40; `bllFec.enable:1` |
| packetPacing | version 3, mode 1, minNumPacketsPerGroup 0, enableAccurateSleep/smooth/fps-toggle |
| framePacing | mode 2 + fps-derived `pid.minTargetFrameTimeUs` |
| NACK queues | 2048 / 1024 / 64 |
| 240 FPS split encode | strips **63** |
| AQ | full DESCRIBE `video.adaptiveQuantization.*` block |
| bitrate QoS | `calculateAvgVideoStreamingBitrate`, `relaxMaxBitrate.*`, `qpDelta.*` |
| aqos / audio | redundancy + dynamic/timestamp audio config |
| ri | `timestampsEnabled`, `useMultipleGamepads` |
| Rust bitrate floor | aligned to TS 4000 kbps / startup max/4 |

Intentionally **not** applied: L4S suite, packetSize 1408, hidDeviceMask:4, transport `general.*` / `runtime.*Srtp`.

Tests: `opennow-stable` `sdp.test.ts` (suite green); `cargo test sdp::` (18 passed).
