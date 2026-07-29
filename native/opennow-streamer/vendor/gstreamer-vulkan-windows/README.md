# Windows GStreamer Vulkan plugins

Official GStreamer Windows packages disable the Vulkan plugin in Cerbero
(`disable_plugin('vulkan', ...)` for Linux/Windows binary builds).

OpenNOW vendors a matching `gstvulkan` build so the experimental Windows
Vulkan video backend can load `vulkansink` / `vulkanh264dec` / `vulkanh265dec`.

Artifacts under `1.28.3/` target GStreamer 1.28.3 MSVC x86_64.

## OpenNOW Win32 embed patch

`gstvkwindow_win32.c` is patched so `vkCreateWin32SurfaceKHR` targets the
`GSTVULKAN` child hwnd (not the Electron/Chromium parent overlay hwnd).
Stock GStreamer presents onto the parent when `set_window_handle` is used,
which stays black under DirectComposition hole-punch. With this patch,
VideoOverlay parenting works: GSTVULKAN is a visible child of the Internal
surface and the swapchain presents there (no floating top-level window).
