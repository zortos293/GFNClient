import { randomUUID } from "node:crypto";
import { PostHog } from "posthog-node";
import type { Settings } from "@shared/gfn";
import {
  APP_OPENED_EVENT_NAME,
  isPostHogConfigured,
  POSTHOG_HOST,
  POSTHOG_PROJECT_TOKEN,
} from "@shared/telemetry";
import { getAppBuildInfo } from "../appBuildInfo";
import type { SettingsManager } from "../settings";

let client: PostHog | null = null;
let activeDistinctId: string | null = null;
let processHandlersInstalled = false;
/** One anonymous open per process lifetime (not per settings sync). */
let appOpenedCapturedThisProcess = false;

function buildCommonProperties(): Record<string, string | undefined> {
  const build = getAppBuildInfo();
  return {
    app_version: build.version,
    app_display_version: build.displayVersion,
    app_build_number: build.buildNumber,
    app_commit: build.commit,
    platform: process.platform,
    arch: process.arch,
    electron_version: process.versions.electron,
  };
}

function ensureInstallId(settingsManager: SettingsManager): string {
  const existing = settingsManager.get("telemetryInstallId").trim();
  if (existing) {
    return existing;
  }
  const generated = randomUUID();
  settingsManager.set("telemetryInstallId", generated);
  return generated;
}

function onUncaughtException(error: Error): void {
  captureMainException(error, {
    mechanism: "uncaughtException",
  });
}

function onUnhandledRejection(reason: unknown): void {
  captureMainException(reason, {
    mechanism: "unhandledRejection",
  });
}

function installProcessHandlers(): void {
  if (processHandlersInstalled) {
    return;
  }
  process.on("uncaughtException", onUncaughtException);
  process.on("unhandledRejection", onUnhandledRejection);
  processHandlersInstalled = true;
}

function uninstallProcessHandlers(): void {
  if (!processHandlersInstalled) {
    return;
  }
  process.off("uncaughtException", onUncaughtException);
  process.off("unhandledRejection", onUnhandledRejection);
  processHandlersInstalled = false;
}

function stopClient(): void {
  uninstallProcessHandlers();
  if (!client) {
    return;
  }
  const shuttingDown = client;
  client = null;
  activeDistinctId = null;
  void shuttingDown.shutdown().catch((error) => {
    console.warn("[Telemetry] Failed to shut down PostHog client:", error);
  });
}

function startClient(settingsManager: SettingsManager): void {
  if (!isPostHogConfigured()) {
    console.warn("[Telemetry] PostHog project token is not configured; skipping main-process telemetry.");
    return;
  }

  const distinctId = ensureInstallId(settingsManager);
  if (client && activeDistinctId === distinctId) {
    installProcessHandlers();
    return;
  }

  stopClient();

  // Use manual process handlers instead of enableExceptionAutocapture so we
  // keep a stable install distinct ID and avoid the SDK forcing process.exit.
  client = new PostHog(POSTHOG_PROJECT_TOKEN, {
    host: POSTHOG_HOST,
    enableExceptionAutocapture: false,
  });
  activeDistinctId = distinctId;
  installProcessHandlers();
}

function captureAppOpened(): void {
  if (!client || !activeDistinctId || appOpenedCapturedThisProcess) {
    return;
  }
  client.capture({
    distinctId: activeDistinctId,
    event: APP_OPENED_EVENT_NAME,
    properties: {
      ...buildCommonProperties(),
      process: "main",
    },
  });
  appOpenedCapturedThisProcess = true;
}

/**
 * Start or stop main-process PostHog based on persisted consent.
 * Call after SettingsManager is ready and whenever consent changes.
 * When consent is granted, records one anonymous {@link APP_OPENED_EVENT_NAME} per process.
 */
export function syncMainTelemetry(settingsManager: SettingsManager): void {
  if (settingsManager.get("errorReportingConsent") === "granted") {
    startClient(settingsManager);
    captureAppOpened();
    return;
  }
  stopClient();
}

export function captureMainException(
  error: unknown,
  additionalProperties?: Record<string, unknown>,
): void {
  if (!client || !activeDistinctId) {
    return;
  }

  const normalized = error instanceof Error
    ? error
    : new Error(typeof error === "string" ? error : "Unknown main-process error");

  client.captureException(normalized, activeDistinctId, {
    ...buildCommonProperties(),
    ...additionalProperties,
    process: "main",
  });
}

export function applyTelemetrySettingsChange(
  settingsManager: SettingsManager,
  key: keyof Settings,
  _value: Settings[keyof Settings],
): void {
  if (key !== "errorReportingConsent" && key !== "telemetryInstallId") {
    return;
  }
  syncMainTelemetry(settingsManager);
}

export async function shutdownMainTelemetry(): Promise<void> {
  uninstallProcessHandlers();
  if (!client) {
    return;
  }
  const shuttingDown = client;
  client = null;
  activeDistinctId = null;
  try {
    await shuttingDown.shutdown();
  } catch (error) {
    console.warn("[Telemetry] Failed to flush PostHog on shutdown:", error);
  }
}
