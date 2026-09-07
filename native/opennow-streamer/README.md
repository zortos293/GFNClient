# OpenNOW native streamer

This workspace implements the native GeForce NOW NVST runtime. It owns RTSPS negotiation, the dedicated Mjolnir SRTP video socket, the NVST ICE/DTLS/SCTP bundle used for audio, RTCP and input, bounded media queues, platform decode/audio output, source-stream Matroska recording and GPU frame publication. It does not implement the browser WebRTC offer/answer or trickle-ICE protocol and it has no microphone upstream path.

The Qt application loads `opennow-streamer-ffi` as an in-process shared library. Qt supplies one complete CloudMatch session context; the embedded engine reserves its bundle and Mjolnir sockets and performs OPTIONS, DESCRIBE, SETUP, ANNOUNCE, PLAY, keepalive and TEARDOWN. The runtime does not load or redistribute NVIDIA client libraries.

## Crates

### Hardware selection in the Qt app

Stream settings default to `nativeVideoBackend: auto`. On Windows the embedded presenter
currently uses D3D11; its picker offers an explicit DX11 override and shows DX12/Vulkan as
unavailable. Standalone D3D12 support does **not** imply embedded D3D12 texture interop.
Unsupported forced backends fail explicitly; they never silently select a different API.
On Linux supported decoder overrides are passed per session, without changing process-global
environment variables. Qt's compositor still uses the platform-native presentation API.

Windows codec detection requires both a usable Media Foundation transform and a matching
DXVA decoder profile/configuration on the probed GPU. Decoder startup repeats the check on
Qt's adopted device with the actual codec, dimensions, bit depth and chroma. Installing an
AV1 codec package on a GPU without AV1 hardware support no longer makes it hardware-capable.
The core resolves Auto against the embedded capability report before CloudMatch allocation.
This is capability selection, not an automatic reconnect or codec change inside a live stream.

GPU import/shader failures are reported to the shell and Qt log. To diagnose an individual
black-screen report, collect `diagnostics/native-streamer.log` plus the Qt log and distinguish
an entirely black application window from a stream-only black video surface.

### Workspace components

- `opennow-streamer-protocol`: versioned local command and session DTOs.
- `opennow-streamer-core`: NVST lifecycle, command routing, media feedback and recording.
- `opennow-streamer-transport`: Mjolnir SRTP plus the NVST-required ICE/DTLS/SCTP, RTCP and input implementation.
- `opennow-streamer-platform`: bounded media queues, decode/audio output, recording and GPU-frame publication.
- `opennow-streamer-platform-{windows,macos,linux}`: platform decoders and native GPU texture producers.
- `opennow-streamer-ffi`: bounded C ABI used in process by Qt.
- `opennow-streamer`: a development JSON-lines host for the same engine; Qt packages do not include it.

## Qt GPU integration

The Qt path does not create an SDL video window or a child streamer process. `NativeStreamRuntime` owns the embedded Rust handle, and `StreamVideoItem` drives the GPU-only FFI from Qt's render thread:

1. Qt lends the current QRhi native graphics objects to the runtime.
2. Platform decode publishes a native GPU frame into a one-frame, drop-stale mailbox.
3. Qt acquires the latest opaque frame token and asks Rust to record conversion and synchronization into the same QRhi command buffer that will render it.
4. Qt imports the returned RGBA8 native texture and samples it in the scene graph, so QML overlays compose above video normally.
5. Qt releases the token after GPU use and shuts down the scene-graph binding on the render thread before QRhi teardown.

The FFI exposes no CPU image, encoded-frame callback, swap chain, window or Qt object. See `crates/opennow-streamer-ffi/README.md` for ownership and threading details.

### Audio output selection

Protocol 5 accepts the additive request `{"type":"audioDevices","id":"audio-1"}`
and returns `{"type":"audioDevices","id":"audio-1","devices":[{"id":"...","name":"..."}]}`.
Failures return a correlated `error` with code `audio-devices-unavailable`; an empty
successful list is not an enumeration failure. Queries run on the existing native
host worker with a two-second response timeout and at most one outstanding query.
Lists exceeding 512 KiB return an error rather than exceeding Qt's callback bound.
Queries neither open a playback device
nor pause, recreate, or stop the active session.

The optional session-context `settings.audioOutputDevice` string is an opaque ID
from this response. An absent or empty string uses the system default and preserves
existing backend fallback behavior. Nonempty IDs are used only when the next session
starts, must contain no NUL and be at most 1024 UTF-8 bytes, and must identify exactly
one available output. Missing or ambiguous fixed outputs fail explicitly rather than
opening the default. Clients persist IDs, never list positions, device indices, or
display names. Duplicate identities are excluded from enumeration.

The Qt embedded backends use their actual playback identities:

- Windows uses exact SDL2 playback device names. SDL playback and enumeration share
  the existing embedded host worker; Qt does not create a second audio owner.
- Linux uses `pipewire:<node.name>` or `alsa:<PCM hint name>`. PipeWire enumeration
  requires `pw-dump` and is bounded to 1.5 seconds and 4 MiB; playback uses `pw-cat`.
  ALSA enumeration uses the playback PCM hints from the same dynamically loaded
  libasound backend. Default-routing aliases are excluded from fixed ALSA choices.
  Fixed outputs disable cross-backend fallback; PipeWire streams also set
  `node.dont-fallback` and `node.dont-reconnect`.
- macOS uses `coreaudio:<device UID>` and passes that UID through native backend
  startup and audio recovery rather than persisting an AudioDeviceID.

The development standalone host uses SDL identities for SDL playback and CoreAudio
identities for native macOS playback. Standalone Windows native WASAPI output does
not support fixed SDL device selection; use the Qt embedded path instead.
The startup selection guarantee does not promise that the operating system will
never reroute an already-playing stream after device loss.

This extension does not change protocol or FFI ABI versions: old clients omit the
setting and keep default behavior, and new clients must tolerate `unknown-command`
from older runtimes that do not implement enumeration.

## Packaging

The Qt CMake build always compiles `opennow-streamer-ffi` with Cargo's release profile and links the resulting shared library to `opennow-qt`. CPack installs that library beside the Qt executable and `opennow-core`; there is no separate streamer executable, helper application or native video window in the Qt package. Linux packages enable the bundled FFmpeg fallback while optional GPU driver interfaces remain dynamically discovered.

The standalone `opennow-streamer` binary remains a development host and is not evidence for the packaged Qt presentation path.

### Embedded latency and resource bounds

- Windows decoder workers wait for actionable input or a bounded 1 ms output
  poll; queued frames do not wake a worker that has no decoder input credits.
  Closing the queue interrupts either wait immediately.
- Windows conversion targets are allocated lazily for the QRhi slots actually
  used, with an eight-slot upper bound. Format changes discard the old slot set.
- Stereo SDL playout retains the 120 ms overflow bound. A backlog above 60 ms
  lasting 200 ms enables allocation-free catch-up resampling (at most 2% faster)
  until the queued tail reaches 40 ms. Normal playback is bit-exact; clearing
  the buffer resets recovery. This controls the application queue, not latency
  inside the OS audio device, and the temporary correction can slightly alter pitch.
- Qt samples the converted surface directly in its scene pass; it does not
  allocate a second video-sized intermediate render target. Native conversion
  and Qt drawing remain on the same graphics command stream.

## Checks

### Embedded session diagnostics

Protocol 5 telemetry includes optional `jitterMs` (RTP interarrival jitter on
the video 90 kHz clock) and `packetLossPercent` (cumulative authenticated RTP
reception loss since stream start). Values are null before a stream is known.
Reading these measurements does not advance RTCP report intervals. Qt forwards
measured values without converting nulls into zeros; RTT, decode duration and
end-to-end latency remain unavailable when no measurement source provides them.

During Windows decoder recreation, the worker retains one pending recovery
keyframe outside the bounded input queue. Already-queued descendants remain in
FIFO order instead of being silently cleared. This adds at most one owned access
unit while the replacement MFT waits for input credits; it adds no presenter or
CPU pixel-copy path. Regression coverage verifies reference-chain ordering.

```sh
cargo fmt --manifest-path native/opennow-streamer/Cargo.toml --all -- --check
cargo clippy --manifest-path native/opennow-streamer/Cargo.toml --workspace --all-targets -- -D warnings
cargo test --manifest-path native/opennow-streamer/Cargo.toml --workspace
```

Controlled output tests can also run without physical audio hardware:

```sh
SDL_AUDIODRIVER=dummy cargo test --manifest-path native/opennow-streamer/Cargo.toml -p opennow-streamer-platform selected_sdl_output -- --ignored
ALSA_CONFIG_PATH="$PWD/native/opennow-streamer/crates/opennow-streamer-platform-linux/tests/fixtures/audio-null.conf" cargo test --manifest-path native/opennow-streamer/Cargo.toml -p opennow-streamer-platform-linux selected_alsa_output -- --ignored
```

The ignored `selected_pipewire_output` test requires an isolated PipeWire server
and session manager with an `Audio/Sink` node named `opennow_test_output`. Point
`XDG_RUNTIME_DIR` at that server's private runtime directory when running the test.

Release validation must additionally run authorized live sessions on Windows, Linux/X11, Linux/Wayland, Intel macOS and Apple Silicon to validate NVST interoperability, native GPU import, audio, input, recovery and device-loss behavior.
