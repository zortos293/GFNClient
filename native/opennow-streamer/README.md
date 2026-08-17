# OpenNOW Native Streamer v2

This workspace is the clean replacement for the former GStreamer-based native streamer.

The workspace owns the local process protocol, lifecycle state machine, standards-based WebRTC transport, and platform capability boundary. Platform decoders and presenters sit behind explicit capabilities; an unavailable backend is never reported as usable.

The executable retains the versioned JSON-lines process contract used by OpenNOW while the app shell is migrated away from Electron. It does not load or redistribute NVIDIA client libraries.

## Crates

- `opennow-streamer-protocol`: versioned local IPC DTOs.
- `opennow-streamer-core`: session lifecycle and command routing.
- `opennow-streamer-transport`: ICE, DTLS-SRTP, RTP/RTCP, and SCTP data channels.
- `opennow-streamer-platform`: platform decoder/presenter capability boundary.
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

## macOS validation

Run the checks natively on each architecture rather than treating a Linux cross-check as macOS proof:

```sh
rustup target add aarch64-apple-darwin x86_64-apple-darwin
OPENNOW_NATIVE_STREAMER_TARGET="$(rustc -vV | sed -n 's/^host: //p')" \
OPENNOW_NATIVE_STREAMER_PLATFORM_KEY="darwin-$(uname -m | sed 's/arm64/arm64/;s/x86_64/x64/')" \
npm --prefix opennow-stable run native:build
```

The CI package matrix runs this build on both Apple Silicon and Intel macOS runners before producing the DMG and ZIP. Release validation must additionally inspect the app bundle, verify its signature, launch the packaged child executable, and confirm fallback behavior on real hardware.

## Current readiness gate

The app-facing executable, lifecycle boundary, ICE/DTLS-SRTP/RTP/SCTP transport implementation, and cross-platform build path are present. Decoded video presentation and audio output are not implemented, so the process deliberately reports the native backend as unavailable and OpenNOW uses Chromium WebRTC. Do not enable the native media path until decoder/presenter capabilities and authorized live-session conformance tests pass on Windows, Linux, and macOS.
