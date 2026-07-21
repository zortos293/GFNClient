import posthog from "posthog-js";
import type { ErrorReportingConsent, Settings } from "@shared/gfn";
import { getLogCapture } from "@shared/logger";
import {
  FEEDBACK_EVENT_NAME,
  FEEDBACK_LOGS_MAX_CHARS,
  isPostHogConfigured,
  POSTHOG_HOST,
  POSTHOG_PROJECT_TOKEN,
  type FeedbackPayload,
} from "@shared/telemetry";

let initialized = false;
let exceptionsEnabled = false;
let activeDistinctId: string | null = null;
let registeredVersionProperties: Record<string, string | undefined> | null = null;

function buildRendererBaseProperties(): Record<string, string | undefined> {
  return {
    app_platform: navigator.platform,
    app_user_agent: navigator.userAgent,
    process: "renderer",
  };
}

async function getAppVersionProperties(): Promise<Record<string, string | undefined>> {
  try {
    const state = await window.openNow.getUpdaterState();
    const version = state.currentVersion?.trim();
    if (!version) {
      return {};
    }
    return {
      app_version: version,
      app_display_version: state.currentDisplayVersion?.trim() || version,
      app_build_number: state.currentBuildNumber?.trim() || undefined,
    };
  } catch (error) {
    console.warn("[Telemetry] Failed to read app version for PostHog:", error);
    return {};
  }
}

async function registerClientProperties(): Promise<void> {
  if (!initialized) {
    return;
  }
  const versionProperties = await getAppVersionProperties();
  registeredVersionProperties = versionProperties;
  posthog.register({
    ...buildRendererBaseProperties(),
    ...versionProperties,
  });
}

async function ensureClient(distinctId: string, enableExceptions: boolean): Promise<boolean> {
  if (!isPostHogConfigured()) {
    return false;
  }

  if (!initialized) {
    posthog.init(POSTHOG_PROJECT_TOKEN, {
      api_host: POSTHOG_HOST,
      autocapture: false,
      capture_pageview: false,
      capture_pageleave: false,
      disable_session_recording: true,
      person_profiles: "identified_only",
      persistence: "localStorage",
      bootstrap: {
        distinctID: distinctId,
      },
      capture_exceptions: enableExceptions
        ? {
            capture_unhandled_errors: true,
            capture_unhandled_rejections: true,
            capture_console_errors: false,
          }
        : false,
      loaded: (client) => {
        client.register({
          ...buildRendererBaseProperties(),
        });
      },
    });
    initialized = true;
    exceptionsEnabled = enableExceptions;
    activeDistinctId = distinctId;
    await registerClientProperties();
    return true;
  }

  if (activeDistinctId !== distinctId) {
    posthog.identify(distinctId);
    activeDistinctId = distinctId;
  }

  if (enableExceptions && !exceptionsEnabled) {
    posthog.startExceptionAutocapture({
      capture_unhandled_errors: true,
      capture_unhandled_rejections: true,
      capture_console_errors: false,
    });
    exceptionsEnabled = true;
  } else if (!enableExceptions && exceptionsEnabled) {
    posthog.stopExceptionAutocapture();
    exceptionsEnabled = false;
  }

  if (!registeredVersionProperties?.app_version) {
    await registerClientProperties();
  }

  return true;
}

async function ensureInstallId(settings: Settings): Promise<string> {
  const existing = settings.telemetryInstallId.trim();
  if (existing) {
    return existing;
  }

  // Main may have just generated an ID when consent was granted.
  try {
    const fresh = await window.openNow.getSettings();
    const fromMain = fresh.telemetryInstallId.trim();
    if (fromMain) {
      return fromMain;
    }
  } catch {
    // Fall through and create one locally.
  }

  const generated = crypto.randomUUID();
  await window.openNow.setSetting("telemetryInstallId", generated);
  return generated;
}

/**
 * Sync renderer PostHog with consent. Exception autocapture only runs when granted.
 * Feedback can call {@link captureFeedback} even when consent is denied.
 */
export async function syncRendererTelemetry(settings: Settings): Promise<void> {
  const consent: ErrorReportingConsent = settings.errorReportingConsent;
  if (consent !== "granted") {
    if (initialized && exceptionsEnabled) {
      posthog.stopExceptionAutocapture();
      exceptionsEnabled = false;
    }
    return;
  }

  const distinctId = await ensureInstallId(settings);
  await ensureClient(distinctId, true);
}

export function captureRendererException(
  error: unknown,
  additionalProperties?: Record<string, unknown>,
): void {
  if (!initialized || !exceptionsEnabled) {
    return;
  }

  const normalized = error instanceof Error
    ? error
    : new Error(typeof error === "string" ? error : "Unknown renderer error");

  posthog.captureException(normalized, {
    ...additionalProperties,
    process: "renderer",
  });
}

function takeLogTail(text: string, maxChars: number): string {
  if (text.length <= maxChars) {
    return text;
  }
  return `…[truncated ${text.length - maxChars} earlier chars]\n${text.slice(-maxChars)}`;
}

async function collectFeedbackLogs(): Promise<{ logs: string; logsBytes: number }> {
  let mainLogs = "";
  try {
    mainLogs = await window.openNow.exportLogs("text");
  } catch (error) {
    console.warn("[Telemetry] Failed to export main-process logs for feedback:", error);
    mainLogs = "[main logs unavailable]";
  }

  const rendererLogs = getLogCapture()?.exportRedacted() ?? "[renderer logs unavailable]";
  const combined = [
    "===== MAIN =====",
    mainLogs.trimEnd(),
    "",
    "===== RENDERER =====",
    rendererLogs.trimEnd(),
    "",
  ].join("\n");

  const logs = takeLogTail(combined, FEEDBACK_LOGS_MAX_CHARS);
  return { logs, logsBytes: logs.length };
}

export async function captureFeedback(
  settings: Settings,
  payload: FeedbackPayload,
): Promise<boolean> {
  if (!isPostHogConfigured()) {
    console.warn("[Telemetry] PostHog is not configured; feedback was not sent.");
    return false;
  }

  const distinctId = await ensureInstallId(settings);
  if (!(await ensureClient(distinctId, settings.errorReportingConsent === "granted"))) {
    return false;
  }

  const properties: Record<string, unknown> = {
    category: payload.category,
    message: payload.message.trim().slice(0, 4000),
    process: "renderer",
    // Always attach lightweight platform so dashboard feedback tables are readable.
    platform: navigator.platform,
    logs_included: payload.includeLogs,
  };
  if (payload.includeSystemInfo) {
    const versionProperties = registeredVersionProperties?.app_version
      ? registeredVersionProperties
      : await getAppVersionProperties();
    Object.assign(properties, buildRendererBaseProperties(), versionProperties);
  }
  if (payload.includeLogs) {
    const { logs, logsBytes } = await collectFeedbackLogs();
    // `app_logs` is the canonical property for dashboards; keep `logs` for older events.
    properties.app_logs = logs;
    properties.logs = logs;
    properties.logs_bytes = logsBytes;
  }

  posthog.capture(FEEDBACK_EVENT_NAME, properties);
  return true;
}

export function isTelemetryConfigured(): boolean {
  return isPostHogConfigured();
}
