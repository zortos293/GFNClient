---
description: Build, launch, drive, and screenshot the OpenNOW Electron settings UI on Windows.
---

# Verify OpenNOW desktop UI

## Build and launch

1. Build the current tree from the repository root:
   ```powershell
   npm --prefix opennow-stable run build
   ```
2. Launch Electron with Chrome DevTools Protocol enabled. Run this as a background command so Electron remains available:
   ```powershell
   $env:OPENNOW_REMOTE_DEBUG = '1'; & "opennow-stable\node_modules\electron\dist\electron.exe" "opennow-stable"
   ```
3. Wait for `http://127.0.0.1:9222/json/list` to expose a page titled `OpenNOW`. The renderer URL should end in `opennow-stable/dist/index.html`.

## Drive the renderer

Use CDP against the target's `webSocketDebuggerUrl`. Node in this environment provides the global `WebSocket` API. The working driver supports:

- `Runtime.evaluate` with `awaitPromise: true`, `returnByValue: true`, and `userGesture: true` for DOM inspection and clicks.
- `Input.dispatchKeyEvent` (`keyDown`, then `keyUp`) for real keyboard behavior. This is required for native range-input arrow-key changes; synthetic DOM keyboard events do not invoke browser default actions.
- `Input.insertText` for typing into the focused control.
- `Page.captureScreenshot` with `{ format: "png", fromSurface: true, captureBeyondViewport: false }`.

The target can be found with:
```js
const targets = await fetch("http://127.0.0.1:9222/json/list").then((response) => response.json());
const target = targets.find((item) => item.type === "page" && item.title === "OpenNOW") ?? targets[0];
```

Send CDP requests as incrementing `{ id, method, params }` JSON messages and resolve responses by matching `id`.

## Settings flows worth exercising

- Click the navbar `Settings` button; the modal root is `.settings-modal`.
- Section navigation buttons are `.settings-nav-item`.
- Shared dropdowns use `[role="combobox"]`, `aria-controls`, and `aria-activedescendant`.
- Region controls use `#settings-stream-region-trigger`, `.region-dropdown`, and `.region-dropdown-search-input`.
- Numeric mouse controls are `#settings-input-mouse-sensitivity-number` and `#settings-input-mouse-acceleration-number`.
- Resize through `window.resizeTo(1024, 680)` and `window.resizeTo(1400, 900)`; verify `outerWidth`/`outerHeight` afterward.
- Switch Interface theme through `#appTheme`, capture light and dark screenshots, then restore the original theme.

For popup Escape behavior, use real CDP key events: the first Escape closes the focused popup while Settings remains open; after the close transition, the next Escape closes Settings. Restore any changed theme, region, toggle, slider, or numeric setting before finishing.

## Evidence

Capture screenshots to a stable local path and read them visually. Useful frames include an open grouped resolution dropdown, filtered region results, focused toggle/slider/chip/region trigger, Account action sizing, About action sizing, and Native Streamer diagnostics at both 1400×900 and 1024×680.
