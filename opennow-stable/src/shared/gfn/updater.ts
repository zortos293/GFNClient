export type AppUpdaterStatus =
  | "disabled"
  | "idle"
  | "checking"
  | "available"
  | "not-available"
  | "downloading"
  | "downloaded"
  | "error";

export type UpdateChannel = "stable" | "nightly";

export function normalizeUpdateChannel(value: unknown): UpdateChannel {
  return value === "nightly" ? "nightly" : "stable";
}

/** Payload sent to the renderer for the What's New modal */
export interface ReleaseHighlightsPayload {
  /** App version these notes are for (e.g. "0.4.2") */
  version: string;
  /** Display title — defaults to "OpenNOW v{version}" */
  title: string;
  /** Release notes in markdown format */
  bodyMarkdown: string;
  /** Where the body came from */
  source: "github" | "updater-cache" | "fallback";
}

export interface AppUpdaterProgress {
  percent: number;
  transferred: number;
  total: number;
  bytesPerSecond: number;
}

export interface AppUpdaterState {
  status: AppUpdaterStatus;
  currentVersion: string;
  currentDisplayVersion?: string;
  currentBuildNumber?: string;
  availableVersion?: string;
  downloadedVersion?: string;
  progress?: AppUpdaterProgress;
  lastCheckedAt?: number;
  message?: string;
  errorCode?: string;
  updateSource: "github-releases";
  canCheck: boolean;
  canDownload: boolean;
  canInstall: boolean;
  isPackaged: boolean;
}
