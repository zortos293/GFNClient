# OpenNOW Native Prototype

This prototype validates a non-Electron OpenNOW shell with a real local WebRTC media path.

## What is implemented

- Complete Qt 6 Quick/QML implementation of the supplied Paper canvas: sign-in, profile selection, Home, Library, controller search keyboard, Sessions, six Settings sections, game details, server selection, preparing, live HUD, quick settings, recovery, confirmation, report, dropdown, modal, and toast states.
- Live provider discovery plus real NVIDIA/alliance device authorization challenges, browser handoff, and token approval polling without exposing credentials to OpenNOW.
- Mouse, keyboard, D-pad, and analog-stick navigation with real filtering, sorting, favorites, profile PINs, settings persistence, session-range reports, CSV export, screenshots, and focus-safe overlays.
- SDL3 gamepad hot-plug, controller count/name reporting, standardized D-pad/A/B/X/Y navigation, and context-aware bumper actions.
- Restartable Rust runtime controlled through versioned JSON-line messages.
- Local peer-to-peer GStreamer `webrtcbin` session with real SDP/ICE negotiation.
- Low-latency queues, live bitrate updates, recovery attempts, and runtime statistics surfaced in the Qt stream HUD.

The default software-encoded demo profile is 720p60 so it remains responsive on development machines; higher stress-test profiles remain available from the game detail menu.

The catalog, entitlement, queue, membership, and session-history records remain deterministic prototype data; authentication challenges and the local WebRTC media path are real. The native prototype does not alter persisted Electron state or claim a production GeForce NOW game session.

## Linux dependencies

```bash
sudo apt install qt6-base-dev qt6-declarative-dev qml6-module-qtquick \
  qml6-module-qtquick-controls qml6-module-qtquick-layouts \
  libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
  libgstreamer-plugins-bad1.0-dev gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-nice
```

## Build

```bash
./scripts/build.sh
```

## Run

```bash
./scripts/run.sh
```

Pass `--demo-signed-in` to the native executable when developing post-authentication screens without an NVIDIA account.

Individual Paper states can be opened directly for visual work:

```bash
./build/opennow-native --demo-signed-in --demo-page=library
./build/opennow-native --demo-signed-in --demo-page=search
./build/opennow-native --demo-signed-in --demo-page=sessions
./build/opennow-native --demo-signed-in --demo-page=settings
./build/opennow-native --demo-profile-picker
./build/opennow-native --demo-server-selector
```

The WebRTC demo currently owns a native GStreamer output surface. Production integration will import D3D11 textures, IOSurfaces, or DMA-BUF handles into the Qt scene graph rather than copying decoded pixels or passing native view pointers between processes.
