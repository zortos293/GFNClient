import { createHash } from "node:crypto";
import {
  DESKTOP_BUG_REPORT_MAX_FILE_BYTES,
  DESKTOP_BUG_REPORT_MAX_FILES,
  desktopBugReportDescriptionError,
  desktopBugReportTitleError,
  type DesktopBugReportReceipt,
} from "@shared/bugReport";

export const DESKTOP_BUG_REPORT_ENDPOINT =
  "https://api.printedwaste.com/releases/opennow-desktop/bug-reports";
export const DESKTOP_BUG_REPORT_REPORTER_ID_PREFIX = "br1_";

export interface DesktopBugReportAttachment {
  fileName: string;
  contentType: string;
  bytes: Uint8Array;
}

export interface DesktopBugReportUpload {
  title: string;
  description: string;
  versionName: string;
  versionCode: string;
  reporterId: string;
  metadata: Record<string, unknown>;
  files: DesktopBugReportAttachment[];
}

export interface DesktopBugReportServerError {
  code: string | null;
  message: string;
  retryable: boolean | null;
}

export class DesktopBugReportUploadError extends Error {
  public readonly serverCode: string | null;
  public readonly retryable: boolean | null;

  constructor(error: DesktopBugReportServerError) {
    super(error.message);
    this.name = "DesktopBugReportUploadError";
    this.serverCode = error.code;
    this.retryable = error.retryable;
  }
}

export function desktopBugReportReporterId(installationId: string): string {
  if (!installationId.trim()) {
    throw new Error("Bug report installation ID is unavailable");
  }
  const digest = createHash("sha256")
    .update(`opennow-desktop-bug-report-v1:${installationId}`, "utf8")
    .digest("hex");
  return `${DESKTOP_BUG_REPORT_REPORTER_ID_PREFIX}${digest}`;
}

export function buildDesktopBugReportFormData(upload: DesktopBugReportUpload): FormData {
  const title = upload.title.trim();
  const description = upload.description.trim();
  const titleError = desktopBugReportTitleError(title);
  if (titleError) throw new Error(titleError);
  const descriptionError = desktopBugReportDescriptionError(description);
  if (descriptionError) throw new Error(descriptionError);
  if (!upload.versionName.trim()) throw new Error("App version is unavailable");
  if (!upload.versionCode.trim()) throw new Error("App build is unavailable");
  if (!/^br1_[0-9a-f]{64}$/.test(upload.reporterId)) {
    throw new Error("Bug report installation ID is invalid");
  }
  if (upload.files.length > DESKTOP_BUG_REPORT_MAX_FILES) {
    throw new Error(`Bug reports support up to ${DESKTOP_BUG_REPORT_MAX_FILES} files`);
  }

  const metadata = JSON.stringify(upload.metadata);
  if (Buffer.byteLength(metadata, "utf8") > MAX_BUG_REPORT_METADATA_BYTES) {
    throw new Error("Bug report metadata is too large");
  }

  const form = new FormData();
  form.append("title", title);
  form.append("description", description);
  form.append("versionName", upload.versionName.trim());
  form.append("versionCode", upload.versionCode.trim());
  form.append("platform", "desktop");
  form.append("reporterId", upload.reporterId);
  form.append("metadata", metadata);

  for (const file of upload.files) {
    const fileName = file.fileName.trim();
    if (!fileName) throw new Error("Bug report files must have a name");
    if (file.bytes.byteLength > DESKTOP_BUG_REPORT_MAX_FILE_BYTES) {
      throw new Error(`${fileName} is larger than 10 MiB`);
    }
    const blobBytes = new Uint8Array(file.bytes.byteLength);
    blobBytes.set(file.bytes);
    form.append(
      "files",
      new Blob([blobBytes.buffer], { type: file.contentType || "application/octet-stream" }),
      fileName,
    );
  }
  return form;
}

export function parseDesktopBugReportReference(body: string): string | null {
  const payload = parseJsonObject(body);
  for (const key of ["id", "reportId", "bugReportId"]) {
    const value = payload?.[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return null;
}

export function parseDesktopBugReportServerError(
  body: string,
  statusCode: number,
): DesktopBugReportServerError {
  const root = parseJsonObject(body);
  const nestedError = root?.error;
  const payload = isJsonObject(nestedError) ? nestedError : root;
  const customMessage = typeof payload?.message === "string"
    ? payload.message.replace(/\s+/g, " ").trim().slice(0, 320)
    : "";
  const code = typeof payload?.code === "string" ? payload.code.slice(0, 80) : null;
  const retryable = typeof payload?.retryable === "boolean" ? payload.retryable : null;

  return {
    code,
    retryable,
    message: customMessage || (statusCode === 403
      ? "Bug reporting is unavailable for this installation."
      : statusCode === 429
        ? "Too many bug reports were sent. Try again later."
        : `Bug report upload failed (HTTP ${statusCode}).`),
  };
}

export function parseDesktopBugReportResponse(
  body: string,
  statusCode: number,
  ok: boolean,
): DesktopBugReportReceipt {
  const payload = parseJsonObject(body);
  if (!ok || payload?.ok === false) {
    throw new DesktopBugReportUploadError(parseDesktopBugReportServerError(body, statusCode));
  }
  return { reference: parseDesktopBugReportReference(body) };
}

function parseJsonObject(body: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(body);
    return isJsonObject(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function isJsonObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

const MAX_BUG_REPORT_METADATA_BYTES = 256 * 1024;
