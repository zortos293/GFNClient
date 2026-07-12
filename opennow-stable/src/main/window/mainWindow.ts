import { BrowserWindow, ipcMain } from "electron";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { IPC_CHANNELS } from "@shared/ipc";
import type { DirectLaunchRequest } from "@shared/gfn";
import type { SettingsManager } from "../settings";
import {
  ESCAPE_HOLD_TO_EXIT_FULLSCREEN_MS,
  markEscapeHoldFired,
  nextPointerLockEscapeCaptureUntilMs,
  resolveEscapeHoldCaptureAction,
  type EscapeHoldCaptureState,
} from "../escapeFullscreenGuard";
import { isAppNavigationUrl, openExternalHttpUrl } from "./externalUrl";

export interface CreateMainWindowDeps {
  mainDir: string;
  settingsManager: SettingsManager;
  getMainWindow(): BrowserWindow | null;
  setMainWindow(window: BrowserWindow | null): void;
  getRendererControlledFullscreen(): boolean;
  setRendererControlledFullscreen(value: boolean): void;
  getPendingDirectLaunchRequest(): DirectLaunchRequest | null;
  emitDirectLaunchRequest(request: DirectLaunchRequest): void;
  getPointerLockActive(): boolean;
  setPointerLockActive(active: boolean): void;
  getPointerLockEscapeCaptureUntilMs(): number;
  setPointerLockEscapeCaptureUntilMs(value: number): void;
}

export async function createMainWindow(
  deps: CreateMainWindowDeps,
): Promise<void> {
  const preloadMjsPath = join(deps.mainDir, "../preload/index.mjs");
  const preloadJsPath = join(deps.mainDir, "../preload/index.js");
  const preloadPath = existsSync(preloadMjsPath)
    ? preloadMjsPath
    : preloadJsPath;

  const settings = deps.settingsManager.getAll();
  let escapeHoldState: EscapeHoldCaptureState = { keyDownCaptured: false, holdFired: false };
  let escapeHoldTimer: NodeJS.Timeout | null = null;
  const clearEscapeHoldTimer = (): void => {
    if (escapeHoldTimer !== null) {
      clearTimeout(escapeHoldTimer);
      escapeHoldTimer = null;
    }
  };

  // Console mode (big picture): mirror GeForce NOW's TV mode by launching
  // fullscreen with the controller-oriented shell enabled.
  if (settings.launchInConsoleMode && !settings.controllerMode) {
    deps.settingsManager.set("controllerMode", true);
  }

  // Direct-launch arguments always start fullscreen; the renderer applies the
  // console shell for the run without persisting the Controller Mode setting.
  const startFullscreen =
    settings.launchInConsoleMode ||
    deps.getPendingDirectLaunchRequest() !== null;

  const window = new BrowserWindow({
    width: settings.windowWidth || 1400,
    height: settings.windowHeight || 900,
    minWidth: 1024,
    minHeight: 680,
    fullscreen: startFullscreen,
    autoHideMenuBar: true,
    backgroundColor: "#0f172a",
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });
  deps.setMainWindow(window);

  window.webContents.on("render-process-gone", (_event, details) => {
    console.error("[Main] Renderer process gone:", details);
  });
  window.webContents.on("console-message", (_event, level, message, line, sourceId) => {
    if (level < 2) return;
    console.error(`[renderer:console:${level}]`, message, sourceId ? `(${sourceId}:${line})` : "");
  });

  window.webContents.setWindowOpenHandler(({ url }) => {
    void openExternalHttpUrl(url).catch((error) => {
      console.warn(
        "Blocked non-external window open:",
        error instanceof Error ? error.message : error,
      );
    });
    return { action: "deny" };
  });

  window.webContents.on("will-navigate", (event, url) => {
    if (isAppNavigationUrl(url, deps.mainDir)) {
      return;
    }

    event.preventDefault();
    void openExternalHttpUrl(url).catch((error) => {
      console.warn(
        "Blocked app window navigation:",
        error instanceof Error ? error.message : error,
      );
    });
  });

  if (process.platform === "win32") {
    // Keep native window fullscreen in sync with HTML fullscreen so Windows treats
    // stream playback like a real fullscreen window instead of only DOM fullscreen.
    window.webContents.on("enter-html-full-screen", () => {
      const mainWindow = deps.getMainWindow();
      if (
        mainWindow &&
        !mainWindow.isDestroyed() &&
        !mainWindow.isFullScreen()
      ) {
        mainWindow.setFullScreen(true);
      }
    });

    window.webContents.on("leave-html-full-screen", () => {
      if (deps.getRendererControlledFullscreen()) {
        return;
      }
      const mainWindow = deps.getMainWindow();
      if (
        mainWindow &&
        !mainWindow.isDestroyed() &&
        mainWindow.isFullScreen()
      ) {
        mainWindow.setFullScreen(false);
      }
    });
  }

  // Track pointer-lock state from renderer; used to decide whether to swallow
  // Escape at the native level (before Chromium handles it).
  ipcMain.on(
    IPC_CHANNELS.POINTER_LOCK_CHANGE,
    (_ev, active: boolean, suppressEscapeFullscreenGrace?: boolean) => {
      const pointerLockActive = Boolean(active);
      deps.setPointerLockActive(pointerLockActive);
      deps.setPointerLockEscapeCaptureUntilMs(
        nextPointerLockEscapeCaptureUntilMs(
          pointerLockActive,
          Boolean(suppressEscapeFullscreenGrace),
          Date.now(),
        ),
      );
    },
  );

  // Intercept Escape early to avoid Chromium exiting fullscreen before the
  // renderer can forward the key to the remote session. Keep a short fullscreen
  // grace window after pointer lock drops so rapid repeated Escape presses cannot
  // win the race before the renderer re-locks the pointer.
  window.webContents.on("before-input-event", (event, input) => {
    try {
      const mainWindow = deps.getMainWindow();
      const resolved = resolveEscapeHoldCaptureAction(
        input,
        {
          allowEscapeToExitFullscreen: Boolean(
            deps.settingsManager?.get("allowEscapeToExitFullscreen"),
          ),
          pointerLockActive: deps.getPointerLockActive(),
          windowFullscreen: Boolean(
            mainWindow &&
              !mainWindow.isDestroyed() &&
              mainWindow.isFullScreen(),
          ),
          pointerLockEscapeCaptureUntilMs:
            deps.getPointerLockEscapeCaptureUntilMs(),
          nowMs: Date.now(),
        },
        escapeHoldState,
      );
      escapeHoldState = resolved.nextHoldState;

      if (resolved.action === "ignore") return;
      event.preventDefault();

      if (resolved.action === "arm-hold") {
        clearEscapeHoldTimer();
        escapeHoldTimer = setTimeout(() => {
          escapeHoldTimer = null;
          const activeWindow = deps.getMainWindow();
          if (!activeWindow || activeWindow.isDestroyed()) return;
          if (!activeWindow.isFullScreen() && !deps.getRendererControlledFullscreen()) return;
          escapeHoldState = markEscapeHoldFired(escapeHoldState);
          activeWindow.webContents.send(IPC_CHANNELS.EXIT_FULLSCREEN);
        }, ESCAPE_HOLD_TO_EXIT_FULLSCREEN_MS);
        return;
      }

      if (resolved.action === "tap") {
        clearEscapeHoldTimer();
        mainWindow?.webContents.send(IPC_CHANNELS.EXTERNAL_ESCAPE);
      } else if (resolved.action === "hold-consumed-keyup") {
        clearEscapeHoldTimer();
      }
    } catch {
      // ignore errors - interception is best-effort
    }
  });

  if (process.env.ELECTRON_RENDERER_URL) {
    await window.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    await window.loadFile(join(deps.mainDir, "../../dist/index.html"));
  }
  const pendingDirectLaunchRequest = deps.getPendingDirectLaunchRequest();
  if (pendingDirectLaunchRequest) {
    deps.emitDirectLaunchRequest(pendingDirectLaunchRequest);
  }

  window.on("closed", () => {
    clearEscapeHoldTimer();
    escapeHoldState = { keyDownCaptured: false, holdFired: false };
    deps.setMainWindow(null);
    deps.setRendererControlledFullscreen(false);
    deps.setPointerLockActive(false);
    deps.setPointerLockEscapeCaptureUntilMs(0);
  });
}
