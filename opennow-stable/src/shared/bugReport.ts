export const DESKTOP_BUG_REPORT_MAX_FILES = 5;
export const DESKTOP_BUG_REPORT_MAX_FILE_BYTES = 10 * 1024 * 1024;
export const DESKTOP_BUG_REPORT_MIN_MEANINGFUL_CHARS = 50;
export const DESKTOP_BUG_REPORT_MIN_WORDS = 8;
export const DESKTOP_BUG_REPORT_MIN_UNIQUE_WORDS = 6;
export const DESKTOP_BUG_REPORT_MAX_TITLE_CHARS = 160;
export const DESKTOP_BUG_REPORT_MAX_DESCRIPTION_CHARS = 8_000;

export type DesktopSessionReportRating = "excellent" | "good" | "fair" | "poor";
export type DesktopSessionReportFindingKind = "info" | "warning";

export interface DesktopSessionReportFinding {
  title: string;
  detail: string;
  kind: DesktopSessionReportFindingKind;
}

/**
 * Privacy-safe quality summary for one completed stream. Deliberately excludes
 * provider credentials, session IDs, server IPs, and account identifiers.
 */
export interface DesktopSessionReport {
  schemaVersion: 1;
  gameTitle: string;
  startedAt: string;
  finishedAt: string;
  durationSeconds: number;
  sampleCount: number;
  limitedData: boolean;
  score: number;
  rating: DesktopSessionReportRating;
  averagePingMs: number | null;
  peakPingMs: number | null;
  averageBitrateKbps: number | null;
  peakBitrateKbps: number | null;
  packetLossPercent: number | null;
  averageJitterMs: number | null;
  averageFps: number | null;
  targetFps: number;
  averageDecodeMs: number | null;
  frameDropPercent: number | null;
  requestedResolution: string;
  deliveredResolution: string | null;
  requestedCodec: string;
  deliveredCodec: string | null;
  transportType: "udp" | "tcp" | "unknown";
  serverLocation: string | null;
  serverGpuType: string | null;
  decoderRecoveryAttempts: number;
  findings: DesktopSessionReportFinding[];
  recommendations: DesktopSessionReportFinding[];
}

export interface DesktopBugReportRequest {
  title: string;
  description: string;
  includeSystemInfo: boolean;
  /** Redacted renderer logs. Main-process logs are collected by the IPC owner. */
  rendererLogs?: string;
  sessionReport?: DesktopSessionReport;
  locale?: string;
  clientPlatform?: string;
  userAgent?: string;
}

export interface DesktopBugReportReceipt {
  reference: string | null;
}

export function desktopBugReportMeaningfulCharacterCount(description: string): number {
  return Array.from(description).filter((character) => /[\p{L}\p{N}]/u.test(character)).length;
}

export function desktopBugReportTitleError(title: string): string | null {
  const trimmed = title.trim();
  if (!trimmed) return "Enter a short issue title";
  if (trimmed.length > DESKTOP_BUG_REPORT_MAX_TITLE_CHARS) {
    return `Keep the issue title under ${DESKTOP_BUG_REPORT_MAX_TITLE_CHARS} characters`;
  }
  if (BUG_REPORT_REPEATED_CHARACTER.test(trimmed)) {
    return "Remove repeated or random text from the issue title";
  }
  return null;
}

export function desktopBugReportDescriptionError(description: string): string | null {
  const trimmed = description.trim();
  if (trimmed.length > DESKTOP_BUG_REPORT_MAX_DESCRIPTION_CHARS) {
    return `Keep the description under ${DESKTOP_BUG_REPORT_MAX_DESCRIPTION_CHARS} characters`;
  }
  const meaningfulCharacters = desktopBugReportMeaningfulCharacterCount(trimmed);
  if (meaningfulCharacters < DESKTOP_BUG_REPORT_MIN_MEANINGFUL_CHARS) {
    return `Describe what happened using at least ${DESKTOP_BUG_REPORT_MIN_MEANINGFUL_CHARS} letters or numbers`;
  }
  const words = Array.from(trimmed.matchAll(BUG_REPORT_WORD), (match) => match[0].toLocaleLowerCase());
  if (
    words.length < DESKTOP_BUG_REPORT_MIN_WORDS
    || new Set(words).size < DESKTOP_BUG_REPORT_MIN_UNIQUE_WORDS
  ) {
    return "Use complete sentences that explain the steps, result, and expected behavior";
  }
  if (BUG_REPORT_REPEATED_CHARACTER.test(trimmed) || hasRepeatedPadding(words)) {
    return "Remove repeated or random text and describe the real problem";
  }
  return null;
}

function hasRepeatedPadding(words: readonly string[]): boolean {
  if (words.length === 0) return false;
  const counts = new Map<string, number>();
  for (const word of words) {
    counts.set(word, (counts.get(word) ?? 0) + 1);
  }
  const mostFrequent = Math.max(...counts.values());
  if (mostFrequent > words.length / 2) return true;

  for (let period = 1; period <= Math.floor(words.length / 2); period += 1) {
    if (words.length % period !== 0) continue;
    if (words.every((word, index) => word === words[index % period])) return true;
  }
  return false;
}

const BUG_REPORT_WORD = /[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu;
const BUG_REPORT_REPEATED_CHARACTER = /([^\s])\1{5,}/iu;
