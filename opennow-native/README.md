# OpenNOW Native Prototype

This prototype validates a non-Electron OpenNOW shell with a real local WebRTC media path.

## What is implemented

- Qt 6 Quick/QML application shell with Home, Library, Search, Settings, game detail, and stream states.
- Keyboard/D-pad focus navigation and a layout designed at 1280×800 first.
- SDL3 gamepad hot-plug, standardized D-pad/A/B navigation, and bumper section switching.
- Restartable Rust runtime controlled through versioned JSON-line messages.
- Local peer-to-peer GStreamer `webrtcbin` session with real SDP/ICE negotiation.
- Low-latency queues and live runtime statistics surfaced in the Qt stream HUD.

The default software-encoded demo profile is 720p60 so it remains responsive on development machines; higher stress-test profiles remain available from the game detail menu.

The catalog is intentionally local prototype data. No NVIDIA credentials are required and the prototype does not alter persisted Electron state.

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

The WebRTC demo currently owns a native GStreamer output surface. Production integration will import D3D11 textures, IOSurfaces, or DMA-BUF handles into the Qt scene graph rather than copying decoded pixels or passing native view pointers between processes.
