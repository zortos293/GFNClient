import { arch, platform, release } from "node:os";
import type { App } from "electron";
import type { DesktopBugReportRequest, DesktopBugReportReceipt } from "@shared/bugReport";
import { exportLogs } from "@shared/logger";
import { getAppBuildInfo } from "../appBuildInfo";
import { EMPTY_GPU_BACKEND_INFO, getGpuBackendInfo } from "../gpuInfo";
import { getOrCreateTelemetryInstallId } from "../installationId";
import type { SettingsManager } from "../settings";
import { fetchWithTimeout } from "./requestTimeout";
import {
  DESKTOP_BUG_REPORT_ENDPOINT,
  buildDesktopBugReportFormData,
  desktopBugReportReporterId,
  parseDesktopBugReportResponse,
  type DesktopBugReportAttachment,
} from "./desktopBugReportProtocol";

export interface DesktopBugReportDeps {
  app: App;
  settingsManager: SettingsManager;
}

export async function uploadDesktopBugReport(
  deps: DesktopBugReportDeps,
  input: DesktopBugReportRequest,
): Promise<DesktopBugReportReceipt> {
  const build = getAppBuildInfo();
  const reporterId = desktopBugReportReporterId(
    getOrCreateTelemetryInstallId(deps.settingsManager),
  );
  const files = buildAttachments(input);
  const gpu = input.includeSystemInfo
    ? await getGpuBackendInfo(deps.app).catch((error) => {
      console.warn("[BugReport] Failed to collect GPU info:", error);
      return EMPTY_GPU_BACKEND_INFO;
    })
    : undefined;
  const versionCode = build.buildNumber ?? "0";
  const metadata: Record<string, unknown> = {
    source: "opennow-desktop",
    attachments: files.map((file) => file.fileName),
    sessionReportIncluded: Boolean(input.sessionReport),
    app: {
      version: build.version,
      displayVersion: build.displayVersion,
      buildNumber: build.buildNumber,
      commit: build.commit,
    },
    ...(input.includeSystemInfo ? {
      device: `${platform()} ${arch()}`,
      system: {
        platform: platform(),
        arch: arch(),
        release: release(),
        electronVersion: process.versions.electron,
        chromeVersion: process.versions.chrome,
        locale: boundedText(input.locale, 40),
        clientPlatform: boundedText(input.clientPlatform, 120),
        userAgent: boundedText(input.userAgent, 500),
        gpu,
      },
    } : {}),
    ...(input.sessionReport ? {
      sessionReport: {
        score: input.sessionReport.score,
        rating: input.sessionReport.rating,
        gameTitle: input.sessionReport.gameTitle,
        durationSeconds: input.sessionReport.durationSeconds,
        sampleCount: input.sessionReport.sampleCount,
      },
    } : {}),
  };

  const form = buildDesktopBugReportFormData({
    title: input.title,
    description: input.description,
    versionName: build.version,
    versionCode,
    reporterId,
    metadata,
    files,
  });
  const response = await fetchWithTimeout(
    DESKTOP_BUG_REPORT_ENDPOINT,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "User-Agent": `opennow-desktop/${build.version}`,
      },
      body: form,
    },
    BUG_REPORT_UPLOAD_TIMEOUT_MS,
    "Bug report upload",
  );
  const body = (await response.text()).slice(0, MAX_BUG_REPORT_RESPONSE_CHARS);
  return parseDesktopBugReportResponse(body, response.status, response.ok);
}

function buildAttachments(input: DesktopBugReportRequest): DesktopBugReportAttachment[] {
  const attachments: DesktopBugReportAttachment[] = [];
  if (typeof input.rendererLogs === "string") {
    const rendererLogBytes = takeUtf8Tail(input.rendererLogs, MAX_RENDERER_LOG_ATTACHMENT_BYTES);
    const combined = [
      "===== MAIN =====",
      exportLogs("text").trimEnd(),
      "",
      "===== RENDERER =====",
      Buffer.from(rendererLogBytes).toString("utf8").trimEnd(),
      "",
    ].join("\n");
    attachments.push({
      fileName: "opennow.log",
      contentType: "text/plain; charset=utf-8",
      bytes: takeUtf8Tail(combined, MAX_LOG_ATTACHMENT_BYTES),
    });
  }
  if (input.sessionReport) {
    const sessionReport = `${JSON.stringify(input.sessionReport, null, 2)}\n`;
    const sessionReportBytes = Buffer.from(sessionReport, "utf8");
    if (sessionReportBytes.byteLength > MAX_SESSION_REPORT_ATTACHMENT_BYTES) {
      throw new Error("Session report attachment is too large");
    }
    attachments.push({
      fileName: "opennow-session-report.json",
      contentType: "application/json; charset=utf-8",
      bytes: sessionReportBytes,
    });
  }
  return attachments;
}

function takeUtf8Tail(text: string, maxBytes: number): Uint8Array {
  const bytes = Buffer.from(text, "utf8");
  if (bytes.byteLength <= maxBytes) return bytes;
  const prefix = Buffer.from(`...[truncated ${bytes.byteLength - maxBytes} earlier bytes]\n`, "utf8");
  const tail = bytes.subarray(bytes.byteLength - (maxBytes - prefix.byteLength));
  return Buffer.concat([prefix, Buffer.from(tail.toString("utf8").replace(/^�/, ""), "utf8")]);
}

function boundedText(value: string | undefined, maxChars: number): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed.slice(0, maxChars) : undefined;
}

const BUG_REPORT_UPLOAD_TIMEOUT_MS = 30_000;
const MAX_BUG_REPORT_RESPONSE_CHARS = 64 * 1024;
const MAX_LOG_ATTACHMENT_BYTES = 8 * 1024 * 1024;
const MAX_RENDERER_LOG_ATTACHMENT_BYTES = 4 * 1024 * 1024;
const MAX_SESSION_REPORT_ATTACHMENT_BYTES = 1024 * 1024;
