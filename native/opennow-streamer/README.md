# OpenNOW Native Streamer v2

This workspace is the clean replacement for the former GStreamer-based native streamer.

The workspace owns the local process protocol, lifecycle state machine, GeForce NOW RTSPS/NVST negotiation, standards-based WebRTC transport, media sockets, media output path, microphone upstream and source-stream Matroska recording. The UI supplies a complete CloudMatch session context once; the streamer reserves its own bundle/Mjolnir sockets and performs OPTIONS, DESCRIBE, SETUP, ANNOUNCE, PLAY, keepalive, and TEARDOWN itself. OpenH264, Opus, SDL, and the Linux FFmpeg codec stack are compiled into the executable; GStreamer and external codec processes are not used.

The executable retains the versioned JSON-lines process contract used by OpenNOW while the app shell is migrated away from Electron. It does not load or redistribute NVIDIA client libraries.

## Crates

- `opennow-streamer-protocol`: versioned local IPC DTOs.
- `opennow-streamer-core`: session lifecycle and command routing.
- `opennow-streamer-transport`: ICE, DTLS-SRTP, RTP/RTCP, and SCTP data channels.
- `opennow-streamer-platform`: bounded media queues, OpenH264/Opus decode, SDL audio/video output, zero-reencode H.264/H.265/AV1 plus Opus Matroska recording, and shell-neutral native surface ownership.
- `opennow-streamer-platform-windows`: strictly separated Media Foundation H.264/HEVC/AV1 hardware and system-software decode, D3D11 NV12/P010 presentation, and WASAPI PCM output for Windows x64 and ARM64.
- `opennow-streamer-platform-macos`: VideoToolbox H.264/HEVC hardware decode, zero-copy IOSurface/Metal presentation, and CoreAudio output.
- `opennow-streamer`: process entry point.

## Checks

```sh
cargo test --manifest-path native/opennow-streamer/Cargo.toml
cargo build --manifest-path native/opennow-streamer/Cargo.toml --release
```

The legacy Electron build wrapper still checks protocol-version parity during migration. The Qt build compiles and bundles the same executable directly:

```sh
npm --prefix opennow-stable run native:build
```

## Platform integration

The SDL presentation window is created hidden on the process main thread. Windows supports Media Foundation over either native D3D11 or a D3D12-backed D3D11-on-12 device and uses WASAPI for audio. Hardware mode enumerates only hardware MFTs. Software mode keeps bundled OpenH264/SDL for H.264 and can use registered D3D11-aware software MFTs for HEVC/AV1. Automatic mode performs the same class-preserving fallback after startup or unrecoverable device loss. `OPENNOW_NATIVE_VIDEO_BACKEND=software` forces that software path, while `d3d12` and `d3d11` select the corresponding hardware graphics path. Auto prefers D3D12 when its complete decode, presentation, and audio probe succeeds. Renderer surface rectangles are already physical pixels and are never scaled again by `deviceScaleFactor`. The Qt launch contract selects the external presenter, so Linux/X11 and Linux/Wayland both use a separate compositor-managed SDL window aligned to the shell's stream region; that window owns native keyboard, mouse, relative-pointer, and cursor handling. macOS also uses a standalone, resizable SDL/AppKit window because AppKit cannot embed a view across processes. VideoToolbox decodes hardware-supported H.264, HEVC and AV1 into IOSurface-backed pixel buffers and Metal renders directly into that window's SDL-managed `CAMetalLayer`; the window owns keyboard, mouse, relative-pointer, and cursor handling. Fullscreen and other shell UI actions are emitted to Core/Qt; native presentation follows the surface geometry supplied by the shell and does not mutate fullscreen state. AV1 format configuration is derived from the first sequence-header keyframe and reconfigured atomically when that header changes. The macOS streamer is a regular LaunchServices application with its own menu bar, Dock identity, process coalition, and AppKit activation lifecycle. The executable runs `MainThreadHost` on its real main thread; FIFO IPC, NVST transport (including its WebRTC-compatible ICE/DTLS/SCTP control bundle), recording and codec work run on named workers. This is required by AppKit and is checked with `pthread_main_np()` on macOS.

The app and native child select the same Linux window system. `OPENNOW_NATIVE_WINDOW_SYSTEM=x11|wayland` records that launch contract and `SDL_VIDEODRIVER` selects the matching SDL backend. On a native Wayland session the stream opens in its own resizable window; on X11 it follows the Qt stream surface. Linux decode supports H.264, HEVC/H.265, and AV1 through Vulkan Video, CUDA/NVDEC, or FFmpeg software decode; native VA-API and V4L2 provide additional H.264 paths. Vulkan presentation is preferred, with SDL NV12 presentation as an independent fallback so decoder acceleration remains active if the Vulkan window path fails.

Linux production builds statically include FFmpeg, its H.264/HEVC/AV1 decoders, Opus, OpenH264, SDL, and their C/C++ support runtimes. They do not require system FFmpeg, GStreamer, Opus, SDL, VA-API, X11, Wayland, or Vulkan-loader libraries. Only the Linux base ABI and the selected display/audio/GPU driver interfaces remain system responsibilities. Building the bundled stack requires a C/C++ toolchain, CMake, Make, NASM, pkg-config, and Git. Native VA-API remains opt-in with `OPENNOW_NATIVE_LINUX_VAAPI=1` because it would add a host `libva` runtime dependency. `OPENNOW_NATIVE_VIDEO_BACKEND=auto|vulkan|cuda|nvdec|vaapi|v4l2|ffmpeg|software` controls decoder selection; `auto` prefers CUDA/NVDEC on NVIDIA, then Vulkan Video, VA-API, V4L2, and bundled FFmpeg software.

## macOS validation

Run the checks natively on each architecture rather than treating a Linux cross-check as macOS proof:

```sh
rustup target add aarch64-apple-darwin x86_64-apple-darwin
OPENNOW_NATIVE_STREAMER_TARGET="$(rustc -vV | sed -n 's/^host: //p')" \
OPENNOW_NATIVE_STREAMER_PLATFORM_KEY="darwin-$(uname -m | sed 's/arm64/arm64/;s/x86_64/x64/')" \
npm --prefix opennow-stable run native:build
```

The CI package matrix runs this build on both Apple Silicon and Intel macOS runners before producing the DMG and ZIP. Release validation must additionally inspect the app bundle, verify its signature, launch the packaged child executable, and confirm fallback behavior on real hardware.

The generated helper lives at `bin/darwin-<arch>/OpenNOWStreamer.app/Contents/MacOS/opennow-streamer`. Both that nested application and its executable are ad-hoc signed for local/unsigned builds; the release signing pass signs nested code inside-out before sealing the outer OpenNOW application.

## Runtime validation

Unit tests synthesize and decode video and Opus frames, verify drop-oldest queue behavior, and exercise pause/stop lifecycle. Linux hardware validation generates H.264, HEVC, and AV1 keyframes and decodes each through Vulkan Video and CUDA/NVDEC. Release validation must additionally run an authorized live session on Windows, Linux/X11, Linux/Wayland, Intel macOS, and Apple Silicon to validate NVST interoperability, A/V timing, paired-window ordering, native relative input, and device-specific audio output.
