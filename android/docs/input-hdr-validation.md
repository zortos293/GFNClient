# Android input and HDR validation

Desktop comparison: `origin/dev` at `3002e5c67` (September 7, 2026).

## Input

Desktop `opennow-streamer-core/src/lib.rs::captured_input_packet` encodes relative mouse
motion as type 7 with signed 16-bit deltas and absolute motion as type 5. Its Windows raw
input producer splits large motion into multiple samples. `opennow-streamer-transport/src/nvst_input.rs`
recognizes the existing `0x22` / type 23 Unicode framing.

Android now routes captured, touch, controller, and gyro relative motion through the existing
8 ms burst limiter. The leading event is immediate; excess deltas are summed and flushed before
buttons/wheel events. Capture remains unbounded relative motion. Uncaptured pointer motion retains
its absolute path. Coalesced deltas exceeding the wire range are split instead of clipped. A
flush-job race that could strand trailing motion is fixed in both limiters.

Android retains its reliable channel because previous device evidence did not establish working
partially reliable mouse delivery. Desktop's separate NVST transport uses a different channel policy.

Physical symbols use the same committed Unicode packet format as the IME. Enqueuing them on the
physical key dispatch path keeps the following key from overtaking them. Releases are matched by
physical key identity even when Shift/AltGr is released first. Ctrl/Meta shortcuts and normal letter
controls retain key events. Typed characters are not written to diagnostics.

## HDR10

`StreamHdr.kt` owns runtime display/decoder eligibility. A supported membership and the H.265
transport envelope (up to 3840x2160 at 60 FPS) are still required. The selected display must report
HDR10 and valid luminance data; a hardware Main10 decoder must support the selected size and rate.
Runtime display luminance is not persisted with presets. CloudMatch receives the reported values;
missing values are not replaced with an invented panel brightness.

`HdrSurfaceVideoDecoder.kt` owns HEVC decode into an opaque SurfaceView surface. WebRTC receives
reference-counted opaque output buffers for its presentation scheduler and decode statistics.
Presenting releases the actual codec buffer to the surface; dropping releases it without rendering.
Surface generations invalidate old buffers. Input is bounded and retained during temporary codec
buffer unavailability; failed/changed decoders require a fresh keyframe.

PQ / BT.2020 color metadata and source HEVC metadata reach Android's surface compositor. Explicit
SDR decoder output is rejected. HDR bypasses the 8-bit GL texture path and sharpening, and applies
no SDR white-point multiplier, custom gamma, or fabricated mastering metadata.

This follows Android's [MediaCodec-to-SurfaceView HDR playback guidance](https://developer.android.com/media/grow/hdr-playback).
NVIDIA's [published Android/SHIELD requirements](https://www.nvidia.com/en-us/geforce-now/system-reqs/)
do not establish third-party handset HDR support. OpenNOW's existing desktop-native allocation
profile is used for high-quality handset requests; provider acceptance still needs a live test.

## Evidence and remaining physical checks

The Android unit suite and debug APK build pass. Regression coverage includes 1000 Hz motion,
button-boundary flushes, wire-range splitting, mapped/dedicated symbols, shortcut preservation,
display luminance validation, CloudMatch luminance values, rejected SDR output, and exactly-once
HDR output buffer presentation/release. This is source/build proof, not panel or provider proof.

No ADB device was connected during implementation. Validate the generated APK with:

1. A physical high-polling mouse: slow diagonal motion, fast flicks, full 360-degree camera movement,
   menu cursor motion, click/drag, wheel, focus loss, and capture release/reacquisition.
2. Hardware keyboard and IME: `a@b!c`, shifted punctuation, AltGr symbols, symbol then immediate
   Enter, modifier release before symbol release, Ctrl/Meta shortcuts, and middle-of-draft edits.
3. An HDR10 device and eligible account: confirm negotiated HDR/10-bit, `HDR direct surface decoder`
   and PQ/BT.2020 output diagnostics, neutral white/gray ramps, highlight clipping and shadow detail.
   Check SDR UI overlays, rotation, background/resume, surface recreation, and an SDR session afterward.
4. An unsupported display, decoder, codec, plan, or mode: HDR must remain unavailable. An external
   display replacement must not render old HDR frames onto an incompatible new surface.

Screenshots are tone-mapped to SDR by Android and cannot establish HDR white-point correctness.
