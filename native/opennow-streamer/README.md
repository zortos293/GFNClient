# OpenNOW Native Streamer

This crate contains OpenNOW's native Rust streaming infrastructure.

> [!NOTE]
> Native streamer / native streaming is experimental. Issues can be platform-specific and users may see fallback to Chromium/WebRTC; report problems on [GitHub Issues](https://github.com/OpenCloudGaming/OpenNOW/issues) or [Discord](https://discord.gg/8EJYaJcNfD).

Canonical native streamer, WebRTC, GStreamer, packaging, and development documentation lives at [opennow.zortos.me](https://opennow.zortos.me). This README is intentionally only a pointer so repository docs do not drift from the site.

## Linux runtime requirements

Linux native video is embedded into the Electron window through an X11 child surface and `GstVideoOverlay`. OpenNOW defaults its Linux Electron shell to X11, which also works on Wayland desktops through XWayland. An explicit pure-Wayland launch is rejected with a diagnostic instead of starting an unmanaged GStreamer window that can remain invisible behind Electron.

The distro GStreamer runtime must provide WebRTC, the selected codec parser/depayloader, a compatible X11 video sink, and at least one decoder. Hardware decode is preferred, but unavailable hardware plugins fall back to the native GStreamer software decoder. If a hardware decoder starts but produces no frames while audio and RTP remain active, OpenNOW reconnects the same native session once with software decoding.
