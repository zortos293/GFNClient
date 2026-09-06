# OpenNOW Qt shell

The supported OpenNOW Qt Quick/QML desktop application. It targets Qt 6.8
or newer and uses SDL3 for controller input. A bundled Rust process owns settings
and is the start of the shell-neutral application core. See
`docs/qt-migration.md` for the migration history and remaining release checklist.

## Remote streaming diagnostics

Settings → About → Copy diagnostics exports a bounded report containing both
`native-streamer.log` and `qt-native.log` from the core's diagnostics directory
(`%APPDATA%/OpenNOW/diagnostics` on Windows, or `OPENNOW_DATA_DIR/diagnostics`).
Reproduce the problem, wait at least 10 seconds, then export before closing the app.

The `diagnostics-v2` Qt startup marker identifies the detailed trace build. The trace
records request IDs on both sides of the embedded ABI, queue acceptance, callback
delivery, timed RTSPS stages, ICE/DTLS progress, two-second receive/assembly counters
(including zero frames), decoder submission, adapter identity and first-frame events.
Queue acceptance is not a successful handshake; a frame notification is not a
presented frame. Compare the successive stages to locate a stall.

Raw session contexts, credentials, URLs, SDP and media payloads are excluded from
the new handshake trace. Logs rotate during use, and exports retain readable lines.
No per-packet or per-input file logging is added to the gameplay path.

## Build

Install the Qt ShaderTools development module with Qt Quick and Multimedia; CMake
bakes the portable video-composition shaders into the executable at build time.

```sh
cmake -S opennow-qt -B build/opennow-qt -DCMAKE_BUILD_TYPE=Debug
cmake --build build/opennow-qt
ctest --test-dir build/opennow-qt --output-on-failure
```

Run with the offscreen Qt platform plugin for a startup smoke test:

```sh
QT_QPA_PLATFORM=offscreen ./build/opennow-qt/opennow-qt \
  --smoke-test --allow-multiple-instances --route home
```

Useful development switches are `--route <name>`, `--overlay <name>`,
`--reduced-motion`, `--core <path>` and `--screenshot <png-path>`. The test suite
opens every route and overlay with QML warnings treated as failures.

The representative-hardware performance workload drives production route and popup motion,
checks focus after every transition, and records refresh-relative frame budgets atomically:

```sh
./build/opennow-qt/opennow-qt --allow-multiple-instances \
  --performance-report "$PWD/opennow-1080p.json" \
  --performance-width 1920 --performance-height 1080 \
  --performance-cycles 3 --performance-label linux-intel-uhd \
  --performance-require-hardware
```

Requested dimensions are physical pixels, so the workload remains comparable on HiDPI screens.
Offscreen/software runs validate the harness only and are rejected when
`--performance-require-hardware` is present. Hardware acceptance also forbids the test-only
`--performance-refresh-hz` override and records both the effective and display-reported rates.
See `docs/qt-acceptance.md` for the release matrix.

The `theme-store` route implements the current Paper V3 collection with
controller filters, temporary preview, persistent install/apply and access to
the local plain-file theme directory.

The versioned Rust core owns settings, NVIDIA device login and token refresh,
OS-protected accounts, PINs, catalogs, subscriptions, regions and latency tests,
account connections, persistent storage, CloudMatch lifecycle/recovery/ads,
NVST session orchestration, diagnostics, media listing, Discord, telemetry,
feedback and update discovery. The protocol-v5 native streamer is linked into
the Qt executable as an in-process Rust library. It owns NVST RTSPS negotiation,
Mjolnir video, the ICE/DTLS/SCTP control bundle, decode, audio and native input.
Qt/QML owns stream status, stats, menus, recovery, failure and fullscreen
chrome. The shell sends one complete CloudMatch session context and never
proxies RTSPS, ICE, SRTP or encoded media. Decoders publish native GPU frames
through the C FFI; `StreamVideoItem` imports and samples them on Qt's QRhi
render command stream without a child streamer process or native presenter
window. CPack installs the Qt executable, `opennow-core`, the runtime library
and `opennow-streamer` for the core's capability probe. Qt streaming still runs
in process through the runtime library, not in the probe executable. CI produces
the platform packages from that layout.
The screenshot shortcut captures the exact stream region, and F12 records the
negotiated H.264/H.265/AV1 source stream plus Opus audio atomically into Matroska
before generating a media thumbnail. Microphone capture is not part of the
native NVST runtime. Live multi-OS streaming, GPU interop and
hardware validation plus production signing/notarization remain release gates.
The legacy Electron application has been removed; it is not a fallback in this tree.

The current presenter is a Qt scene-graph item. Platform decoders retain native
textures, record any required conversion and synchronization into the active
QRhi command buffer, and expose only opaque frame tokens across the FFI. QML
overlays therefore compose normally above the stream. No CPU frame callback,
standalone SDL presenter, child HWND or paired top-level video window participates
in the Qt path.

`StreamVideoItem` uses `QSGRenderNode`: native YUV conversion runs before the Qt
scene pass, then the converted texture is drawn directly into that pass. There is
no additional item-sized RGBA render target. The shared video material preserves
transforms, inherited opacity, scissor/stencil clips and letterboxing, and caches
imported texture bindings per QRhi frame slot.

For GPU pixel acceptance (including overlays, clipping and fullscreen), run
`opennow-streamvideo-tests` with the native platform plugin, not `offscreen`.
The normal offscreen CTest run deliberately skips those hardware-only checks.
