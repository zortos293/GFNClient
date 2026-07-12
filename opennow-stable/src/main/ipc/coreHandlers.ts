import type {
  App,
  BrowserWindow,
  Clipboard,
  Dialog,
  IpcMain,
  Shell,
  SystemPreferences,
} from "electron";
import { IPC_CHANNELS } from "@shared/ipc";
import type { CommunityProxyProvisionResult } from "@shared/communityProxy";
import type { DiscordActivityUpdate } from "@shared/discord";
import type {
  AppUpdaterState,
  DirectLaunchRequest,
  MicrophonePermissionResult,
  PingResult,
  Settings,
  StreamRegion,
  ThankYouDataResult,
} from "@shared/gfn";
import { exportLogs } from "@shared/logger";
import { provisionZortosCommunityProxy } from "../community/provisionSessionProxy";
import {
  connectDiscordRpc,
  destroyDiscordRpc,
  setActivity,
  clearActivity,
} from "../discordRpc";
import { registerMediaIpcHandlers } from "./mediaHandlers";
import { getAppBuildInfo } from "../appBuildInfo";
import { getReleaseHighlightsPayload, normalizeReleaseVersion } from "../releaseHighlights";
import { cacheManager } from "../services/cacheManager";
import {
  fetchPrintedWasteQueue,
  fetchPrintedWasteServerMapping,
} from "../services/printedWaste";
import { pingRegions } from "../services/regionPing";
import type { SettingsManager } from "../settings";
import type { AppUpdaterController } from "../updater";
import type { SignalingCoordinator } from "../signaling/signalingCoordinator";
import { fetchThanksData } from "../thanks/fetchThanksData";
import { openExternalHttpUrl } from "../window/externalUrl";

type DiscordMonitor = {
  start(): void;
  stop(): void;
};

export interface CoreIpcHandlerDeps {
  ipcMain: IpcMain;
  app: App;
  dialog: Dialog;
  shell: Shell;
  clipboard: Clipboard;
  systemPreferences: SystemPreferences;
  settingsManager: SettingsManager;
  refreshScheduler: { manualRefresh(): Promise<void> };
  getMainWindow(): BrowserWindow | null;
  setRendererControlledFullscreen(value: boolean): void;
  getPendingDirectLaunchRequest(): DirectLaunchRequest | null;
  setPendingDirectLaunchRequest(request: DirectLaunchRequest | null): void;
  getAppUpdater(): AppUpdaterController | null;
  getSignalingCoordinator(): SignalingCoordinator | null;
  discordMonitor: DiscordMonitor;
  requestAppShutdown(options?: {
    reason?: string;
    forceExitFallback?: boolean;
    exitCode?: number;
  }): void;
}

function disabledUpdaterState(app: App): AppUpdaterState {
  const buildInfo = getAppBuildInfo();
  return {
    status: "disabled",
    currentVersion: buildInfo.version,
    currentDisplayVersion: buildInfo.displayVersion,
    currentBuildNumber: buildInfo.buildNumber,
    updateSource: "github-releases",
    canCheck: false,
    canDownload: false,
    canInstall: false,
    isPackaged: app.isPackaged,
    message: "Updater is unavailable.",
  };
}

export function registerCoreIpcHandlers(deps: CoreIpcHandlerDeps): void {
  const {
    ipcMain,
    app,
    dialog,
    shell,
    clipboard,
    systemPreferences,
    settingsManager,
    refreshScheduler,
    discordMonitor,
  } = deps;

  ipcMain.handle(IPC_CHANNELS.DISCORD_CLEAR_ACTIVITY, async () => {
    void clearActivity();
  });

  ipcMain.handle(
    IPC_CHANNELS.DISCORD_SET_ACTIVITY,
    async (_event, activity: DiscordActivityUpdate) => {
      if (!settingsManager.get("discordRichPresence")) {
        return;
      }

      void setActivity({
        ...activity,
        startTimestamp: activity.startTimestampMs
          ? new Date(activity.startTimestampMs)
          : undefined,
      });
    },
  );

  // Toggle fullscreen via IPC (for completeness)
  ipcMain.handle(IPC_CHANNELS.TOGGLE_FULLSCREEN, async () => {
    const mainWindow = deps.getMainWindow();
    if (mainWindow && !mainWindow.isDestroyed()) {
      const isFullScreen = mainWindow.isFullScreen();
      const nextFullscreen = !isFullScreen;
      mainWindow.setFullScreen(nextFullscreen);
      deps.setRendererControlledFullscreen(nextFullscreen);
    }
  });

  ipcMain.handle(
    IPC_CHANNELS.SET_FULLSCREEN,
    async (_event, value: boolean) => {
      const mainWindow = deps.getMainWindow();
      if (mainWindow && !mainWindow.isDestroyed()) {
        try {
          const nextFullscreen = Boolean(value);
          mainWindow.setFullScreen(nextFullscreen);
          deps.setRendererControlledFullscreen(nextFullscreen);
        } catch (err) {
          console.warn("Failed to set fullscreen:", err);
        }
      }
    },
  );

  // Toggle pointer lock via IPC (F8 shortcut)
  ipcMain.handle(IPC_CHANNELS.TOGGLE_POINTER_LOCK, async () => {
    const mainWindow = deps.getMainWindow();
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send("app:toggle-pointer-lock");
    }
  });

  ipcMain.handle(IPC_CHANNELS.QUIT_APP, async () => {
    deps.requestAppShutdown({
      reason: "renderer-explicit-exit",
      forceExitFallback: true,
    });
  });

  ipcMain.handle(
    IPC_CHANNELS.OPEN_EXTERNAL_URL,
    async (_event, url: string): Promise<void> => {
      await openExternalHttpUrl(url);
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.DIRECT_LAUNCH_GET_PENDING,
    async (): Promise<DirectLaunchRequest | null> => {
      const request = deps.getPendingDirectLaunchRequest();
      deps.setPendingDirectLaunchRequest(null);
      return request;
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.APP_UPDATER_GET_STATE,
    async (): Promise<AppUpdaterState> => {
      return deps.getAppUpdater()?.getState() ?? disabledUpdaterState(app);
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.APP_UPDATER_CHECK,
    async (): Promise<AppUpdaterState> => {
      return (
        deps.getAppUpdater()?.checkForUpdates("manual") ??
        disabledUpdaterState(app)
      );
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.APP_UPDATER_DOWNLOAD,
    async (): Promise<AppUpdaterState> => {
      return deps.getAppUpdater()?.downloadUpdate() ?? disabledUpdaterState(app);
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.APP_UPDATER_INSTALL,
    async (): Promise<AppUpdaterState> => {
      return deps.getAppUpdater()?.quitAndInstall() ?? disabledUpdaterState(app);
    },
  );

  // Settings IPC handlers
  ipcMain.handle(IPC_CHANNELS.SETTINGS_GET, async (): Promise<Settings> => {
    return settingsManager.getAll();
  });

  ipcMain.handle(IPC_CHANNELS.CLIPBOARD_READ_TEXT, async (): Promise<string> => {
    return clipboard.readText();
  });

  ipcMain.handle(
    IPC_CHANNELS.SETTINGS_SET,
    async <K extends keyof Settings>(
      _event: Electron.IpcMainInvokeEvent,
      key: K,
      value: Settings[K],
    ) => {
      settingsManager.set(key, value);
      const appliedValue = settingsManager.get(key);
      // React to certain setting changes immediately in main process
      try {
        if (key === "autoCheckForUpdates") {
          deps.getAppUpdater()?.setAutomaticChecksEnabled(appliedValue as boolean);
        }
        if (key === "updateChannel") {
          deps.getAppUpdater()?.setUpdateChannel(appliedValue as Settings["updateChannel"]);
        }
        deps.getSignalingCoordinator()?.applySettingsChange(key, appliedValue);
        if (key === "discordRichPresence") {
          if (appliedValue) {
            void connectDiscordRpc().then(() => discordMonitor.start());
          } else {
            discordMonitor.stop();
            void destroyDiscordRpc();
          }
        }
      } catch (err) {
        console.warn("Failed to apply setting change in main process:", err);
      }
    },
  );

  ipcMain.handle(IPC_CHANNELS.SETTINGS_RESET, async (): Promise<Settings> => {
    const resetSettings = settingsManager.reset();
    deps
      .getAppUpdater()
      ?.setAutomaticChecksEnabled(resetSettings.autoCheckForUpdates);
    deps.getAppUpdater()?.setUpdateChannel(resetSettings.updateChannel);
    deps.getSignalingCoordinator()?.stopNativeStreamer("settings reset");
    deps.getSignalingCoordinator()?.resetNativeStreamerContext();
    return resetSettings;
  });

  ipcMain.handle(
    IPC_CHANNELS.SETTINGS_SELECT_NATIVE_STREAMER_EXECUTABLE,
    async (): Promise<string | null> => {
      const filters =
        process.platform === "win32"
          ? [
              { name: "Executable", extensions: ["exe"] },
              { name: "All Files", extensions: ["*"] },
            ]
          : [{ name: "All Files", extensions: ["*"] }];

      const options: Electron.OpenDialogOptions = {
        title: "Select OpenNOW streamer executable",
        properties: ["openFile"],
        filters,
      };
      const mainWindow = deps.getMainWindow();
      const result =
        mainWindow && !mainWindow.isDestroyed()
          ? await dialog.showOpenDialog(mainWindow, options)
          : await dialog.showOpenDialog(options);

      if (result.canceled || result.filePaths.length === 0) {
        return null;
      }
      return result.filePaths[0] ?? null;
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.MICROPHONE_PERMISSION_GET,
    async (): Promise<MicrophonePermissionResult> => {
      if (process.platform !== "darwin") {
        return {
          platform: process.platform,
          isMacOs: false,
          status: "not-applicable",
          granted: false,
          canRequest: false,
          shouldUseBrowserApi: true,
        };
      }

      const currentStatus =
        systemPreferences.getMediaAccessStatus("microphone");
      console.log("[Main] macOS microphone permission status:", currentStatus);

      if (currentStatus === "granted") {
        return {
          platform: process.platform,
          isMacOs: true,
          status: "granted",
          granted: true,
          canRequest: false,
          shouldUseBrowserApi: true,
        };
      }

      if (currentStatus === "not-determined") {
        const granted = await systemPreferences.askForMediaAccess("microphone");
        const nextStatus = systemPreferences.getMediaAccessStatus("microphone");
        console.log(
          "[Main] Requested macOS microphone permission:",
          granted,
          nextStatus,
        );
        return {
          platform: process.platform,
          isMacOs: true,
          status: nextStatus,
          granted,
          canRequest: nextStatus === "not-determined",
          shouldUseBrowserApi: granted,
        };
      }

      return {
        platform: process.platform,
        isMacOs: true,
        status: currentStatus,
        granted: false,
        canRequest: false,
        shouldUseBrowserApi: false,
      };
    },
  );

  // Logs export IPC handler
  ipcMain.handle(
    IPC_CHANNELS.LOGS_EXPORT,
    async (_event, format: "text" | "json" = "text"): Promise<string> => {
      return exportLogs(format);
    },
  );

  registerMediaIpcHandlers({
    ipcMain,
    dialog,
    shell,
    getMainWindow: deps.getMainWindow,
  });

  ipcMain.handle(IPC_CHANNELS.CACHE_REFRESH_MANUAL, async (): Promise<void> => {
    await refreshScheduler.manualRefresh();
  });

  ipcMain.handle(IPC_CHANNELS.CACHE_DELETE_ALL, async (): Promise<void> => {
    await cacheManager.deleteAll();
    console.log("[IPC] Cache deletion completed successfully");
  });

  ipcMain.handle(
    IPC_CHANNELS.COMMUNITY_GET_THANKS,
    async (): Promise<ThankYouDataResult> => {
      return fetchThanksData();
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.COMMUNITY_PROVISION_SESSION_PROXY,
    async (): Promise<CommunityProxyProvisionResult> => {
      return provisionZortosCommunityProxy();
    },
  );

  ipcMain.handle(
    IPC_CHANNELS.PING_REGIONS,
    async (_event, regions: StreamRegion[]): Promise<PingResult[]> => {
      return pingRegions(regions);
    },
  );

  // PrintedWaste queue API — fetched from main process so User-Agent can be set
  ipcMain.handle(IPC_CHANNELS.PRINTEDWASTE_QUEUE_FETCH, async () => {
    return fetchPrintedWasteQueue(app.getVersion());
  });

  ipcMain.handle(IPC_CHANNELS.PRINTEDWASTE_SERVER_MAPPING_FETCH, async () => {
    return fetchPrintedWasteServerMapping(app.getVersion());
  });

  // Release highlights IPC handlers
  ipcMain.handle(
    IPC_CHANNELS.RELEASE_HIGHLIGHTS_GET,
    async (
      _event,
      version?: string,
    ): Promise<import("@shared/gfn").ReleaseHighlightsPayload> => {
      const appVersion = normalizeReleaseVersion(app.getVersion()) ?? "0.0.0";
      const targetVersion =
        normalizeReleaseVersion(version ?? appVersion) ?? appVersion;
      return getReleaseHighlightsPayload(targetVersion);
    },
  );

  ipcMain.handle(IPC_CHANNELS.RELEASE_HIGHLIGHTS_ACK, async (): Promise<void> => {
    settingsManager.set(
      "lastSeenReleaseHighlightsVersion",
      app.getVersion().replace(/^v/, ""),
    );
  });

  // Save window size when it changes (skip fullscreen so the saved size
  // stays meaningful for windowed launches, e.g. after console mode)
  deps.getMainWindow()?.on("resize", () => {
    const mainWindow = deps.getMainWindow();
    if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isFullScreen()) {
      const [width, height] = mainWindow.getSize();
      settingsManager.set("windowWidth", width);
      settingsManager.set("windowHeight", height);
    }
  });
}
