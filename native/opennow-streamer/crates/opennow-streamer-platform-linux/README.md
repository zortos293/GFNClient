# OpenNOW Linux platform backend

This crate owns the Linux hardware decode, audio, and Vulkan presentation path used by the native-streamer workspace.

The backend accepts H.264 Annex-B access units and Opus packets. Video decode uses VA-API when the `vaapi` feature is built and a working DRM render node is present, then a stateful V4L2 M2M decoder when one advertises H.264 input and NV12/I420 output. There is no software decoder and no GStreamer or FFmpeg dependency. Presentation uploads the decoded NV12/I420 frame to a Vulkan swapchain attached to a caller-owned X11 or Wayland surface. Audio decodes through libopus and writes to PipeWire through `pw-cat` or directly to ALSA.

Runtime probing opens the real device or service. A compiled feature or a shared library by itself is never reported as an available decoder, presenter, or audio sink.

`LinuxSession` owns the bounded decode and audio workers. Submit complete Annex-B H.264 access units and Opus packets, drain decoded frames and typed events, and call `reconfigure` or `stop` explicitly. Queue overflow drops the oldest media, flushes video reference state, and emits `NeedKeyframe` instead of allowing corrupted prediction chains to continue.

Presentation stays on the caller's window-system thread. Construct `NativeSurface::borrow_x11` or `NativeSurface::borrow_wayland` from handles that the caller owns, then create `VulkanPresenter` and feed it frames returned by the session. The presenter owns its Vulkan instance, device, swapchain, and `VkSurfaceKHR`; it never destroys the borrowed X11 window, Xlib display, `wl_surface`, or `wl_display`. Its lifetime is tied to `NativeSurface`, and it is intentionally not `Send`.

## Build requirements

The default build needs a Linux C toolchain, Linux UAPI headers, libclang (for `v4l2-sys`), and a Vulkan loader at runtime. VA-API is optional because its maintained Rust bindings generate against the host libva headers:

```sh
cargo test -p opennow-streamer-platform-linux
cargo check -p opennow-streamer-platform-linux --all-targets
cargo check -p opennow-streamer-platform-linux --all-targets --all-features
./scripts/check-linux.sh
```

The last command additionally needs the libva and DRM development packages. Cross-check the architecture-independent path with:

```sh
rustup target add aarch64-unknown-linux-gnu
cargo check -p opennow-streamer-platform-linux --target aarch64-unknown-linux-gnu --no-default-features
```

The V4L2 fallback supports both single-planar and multi-planar stateful decoder nodes used on x86_64, aarch64, and Raspberry Pi. Actual decode tests require `/dev/video*`; VA-API tests require `/dev/dri/renderD*`; Vulkan presentation requires a live X11 or Wayland surface; audio requires a PipeWire server or an ALSA PCM. Unit tests do not claim those devices exist.
