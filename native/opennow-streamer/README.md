# OpenNOW Native Streamer v2

This workspace is the clean replacement for the former GStreamer-based native streamer.

The workspace owns the local process protocol, lifecycle state machine, standards-based WebRTC transport, and media output path. The baseline backend links OpenH264, Opus, and SDL from source, so it does not require a GStreamer or FFmpeg runtime.

The executable retains the versioned JSON-lines process contract used by OpenNOW while the app shell is migrated away from Electron. It does not load or redistribute NVIDIA client libraries.

## Crates

- `opennow-streamer-protocol`: versioned local IPC DTOs.
- `opennow-streamer-core`: session lifecycle and command routing.
- `opennow-streamer-transport`: ICE, DTLS-SRTP, RTP/RTCP, and SCTP data channels.
- `opennow-streamer-platform`: bounded media queues, OpenH264/Opus decode, SDL audio/video output, and platform-native Electron surface ownership.
- `opennow-streamer-platform-windows`: Media Foundation H.264 hardware decode, D3D11 NV12 presentation, and WASAPI PCM output for Windows x64 and ARM64.
- `opennow-streamer`: process entry point.

## Checks

```sh
cargo test --manifest-path native/opennow-streamer/Cargo.toml
cargo build --manifest-path native/opennow-streamer/Cargo.toml --release
```

The Electron app's build wrapper also checks protocol-version parity, copies the executable into the platform package directory, and runs a JSON-lines `hello`/`stop` process smoke test:

```sh
npm --prefix opennow-stable run native:build
```

## Platform integration

The SDL presentation window is created hidden on the process main thread. Windows supports Media Foundation over either native D3D11 or a D3D12-backed D3D11-on-12 device and uses WASAPI for audio; it creates a non-activating, input-transparent Electron child HWND and falls back to OpenH264/SDL after startup or unrecoverable device loss. `OPENNOW_NATIVE_VIDEO_BACKEND=software` forces the software path, while `d3d12` and `d3d11` select the corresponding Windows graphics path. Auto prefers D3D12 when its complete decode, presentation, and audio probe succeeds. Renderer surface rectangles are already physical pixels and are never scaled again by `deviceScaleFactor`. Linux/X11 reparents the SDL window into Electron. Linux/Wayland uses a separate compositor-managed SDL window because Wayland intentionally has no portable foreign child-window embedding API; that window owns native keyboard, mouse, relative-pointer, and cursor handling, and F11 toggles compositor fullscreen. macOS keeps the output as a non-activating, mouse-ignoring child `NSWindow` and converts renderer-relative surface rectangles through the Electron `NSView`. The executable runs `MainThreadHost` on its real main thread; stdin, WebRTC, and codec work run on named workers. This is required by AppKit and is checked with `pthread_main_np()` on macOS.

The app and native child select the same Linux window system. `OPENNOW_NATIVE_WINDOW_SYSTEM=x11|wayland` records that launch contract and `SDL_VIDEODRIVER` selects the matching SDL backend. On a native Wayland session the stream opens in its own resizable window; on X11 it remains embedded in the Electron stream rectangle. VA-API and V4L2 hardware decode are advertised only when the selected window system also has the matching Vulkan WSI extension; OpenH264/SDL remains the automatic fallback.

Linux production builds include VA-API by default. Its Rust bindings are generated against the build host's libva headers and tolerate current extension fields. Set `OPENNOW_NATIVE_LINUX_VAAPI=0` only for portable build hosts without libva development files; those builds still probe V4L2 and retain the OpenH264 fallback.

## macOS validation

Run the checks natively on each architecture rather than treating a Linux cross-check as macOS proof:

```sh
rustup target add aarch64-apple-darwin x86_64-apple-darwin
OPENNOW_NATIVE_STREAMER_TARGET="$(rustc -vV | sed -n 's/^host: //p')" \
OPENNOW_NATIVE_STREAMER_PLATFORM_KEY="darwin-$(uname -m | sed 's/arm64/arm64/;s/x86_64/x64/')" \
npm --prefix opennow-stable run native:build
```

The CI package matrix runs this build on both Apple Silicon and Intel macOS runners before producing the DMG and ZIP. Release validation must additionally inspect the app bundle, verify its signature, launch the packaged child executable, and confirm fallback behavior on real hardware.

## Runtime validation

Unit tests synthesize and decode H.264 and Opus frames, verify drop-oldest queue behavior, and exercise pause/stop lifecycle. Release validation must additionally run an authorized live session on Windows, Linux/X11, Linux/Wayland, Intel macOS, and Apple Silicon to validate WebRTC interoperability, A/V timing, Electron child-surface stacking or Wayland top-level behavior, native relative input, and device-specific audio output.
