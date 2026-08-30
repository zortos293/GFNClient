# OpenNOW Qt shell

The Qt Quick/QML replacement for the Electron desktop shell. It targets Qt 6.8
or newer and uses SDL3 for controller input. A bundled Rust process owns settings
and is the start of the shell-neutral application core. See
`docs/qt-migration.md` for the full parity and removal checklist.

## Build

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
feedback and update discovery. The supervised protocol-v5 native streamer owns
the complete NVST negotiation, its media sockets, hardware/software decode,
audio, native input and presentation controls. Qt/QML owns stream status, stats,
menus, recovery, failure and fullscreen chrome. The Qt shell sends one
complete CloudMatch session context and never proxies RTSPS or ICE/SRTP state.
CPack installs the shell, core and streamer together and can generate
a Linux deb locally, while CI defines a checksum-pinned x64 AppImage build.
The screenshot shortcut captures the exact stream region, and F12 records the
negotiated H.264/H.265/AV1 source stream plus Opus audio atomically into Matroska
before generating a media thumbnail. NVST microphone upstream is not currently
supported and fails closed. Live multi-OS streaming, window-composition and
hardware validation plus production signing/notarization remain removal gates,
so Electron is still retained as the shipping fallback.

The current presenter is not a Qt scene-graph texture. Windows reparents its native
presenter HWND into the Qt top-level window, while macOS, X11 and Wayland use paired
native top-level windows. Ordinary QML cannot reliably cover those surfaces, so
opening Qt-owned stream UI temporarily hides native presentation and restores it
afterward. This is deliberately not described as a composited live-video overlay or
cross-platform zero-copy.
