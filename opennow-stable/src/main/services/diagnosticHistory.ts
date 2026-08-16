import {
  copyFile,
  mkdir,
  readFile,
  rename,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";
import { gunzip, gzip } from "node:zlib";

import type { LogCapture, PreviousLogSnapshot } from "@shared/logger";

const gzipAsync = promisify(gzip);
const gunzipAsync = promisify(gunzip);

const DIRECTORY_NAME = "diagnostic-history";
const CURRENT_FILE_NAME = "current.txt.gz";
const PREVIOUS_FILE_NAME = "previous.txt.gz";
const DEFAULT_MAX_CHARACTERS = 1_500_000;
const DEFAULT_PERSIST_INTERVAL_MS = 15_000;
const PERSISTED_EVENT_LIMIT = 1200;

async function isFile(path: string): Promise<boolean> {
  try {
    return (await stat(path)).isFile();
  } catch {
    return false;
  }
}

export function boundDiagnosticSnapshot(
  text: string,
  maxCharacters = DEFAULT_MAX_CHARACTERS,
): string {
  if (maxCharacters < 256) {
    throw new Error("Diagnostic snapshot bound must be at least 256 characters");
  }
  if (text.length <= maxCharacters) return text;
  const marker = `\n... persisted diagnostic snapshot truncated ${text.length - maxCharacters} characters ...\n`;
  const available = Math.max(2, maxCharacters - marker.length);
  const headLength = Math.floor(available / 2);
  const tailLength = available - headLength;
  return text.slice(0, headLength) + marker + text.slice(-tailLength);
}

/**
 * Rotates one bounded, already-redacted snapshot between app runs. A corrupt
 * current snapshot never replaces the last readable previous-run evidence.
 */
export class DiagnosticHistoryStore {
  private readonly historyDirectory: string;
  private readonly currentFile: string;
  private readonly previousFile: string;

  constructor(
    userDataDirectory: string,
    private readonly now: () => number = Date.now,
  ) {
    this.historyDirectory = join(userDataDirectory, DIRECTORY_NAME);
    this.currentFile = join(this.historyDirectory, CURRENT_FILE_NAME);
    this.previousFile = join(this.historyDirectory, PREVIOUS_FILE_NAME);
  }

  async beginAppRun(): Promise<PreviousLogSnapshot | null> {
    await mkdir(this.historyDirectory, { recursive: true });
    await this.recoverInterruptedReplacement(this.currentFile);
    await this.recoverInterruptedReplacement(this.previousFile);
    const current = await this.readSnapshot(this.currentFile);
    if (!current) {
      await rm(this.currentFile, { force: true });
      return this.readSnapshot(this.previousFile);
    }

    const stagedPrevious = `${this.previousFile}.stage`;
    await rm(stagedPrevious, { force: true });
    await copyFile(this.currentFile, stagedPrevious);
    await this.replaceFile(stagedPrevious, this.previousFile);
    await rm(this.currentFile, { force: true });
    return this.readSnapshot(this.previousFile);
  }

  async saveCurrent(text: string): Promise<void> {
    await mkdir(this.historyDirectory, { recursive: true });
    await this.recoverInterruptedReplacement(this.currentFile);
    const stagedCurrent = `${this.currentFile}.stage`;
    await rm(stagedCurrent, { force: true });
    const bounded = boundDiagnosticSnapshot(text);
    const payload = Buffer.from(`${this.now()}\n${bounded}`, "utf8");
    const compressed = await gzipAsync(payload, { level: 1 });
    await writeFile(stagedCurrent, compressed);
    await this.replaceFile(stagedCurrent, this.currentFile);
  }

  private async readSnapshot(path: string): Promise<PreviousLogSnapshot | null> {
    if (!await isFile(path)) return null;
    try {
      const compressed = await readFile(path);
      const raw = (await gunzipAsync(compressed)).toString("utf8");
      const newline = raw.indexOf("\n");
      if (newline <= 0) return null;
      const capturedAt = Number.parseInt(raw.slice(0, newline), 10);
      const text = raw.slice(newline + 1).trimEnd();
      if (!Number.isFinite(capturedAt) || !text) return null;
      return { capturedAt, text };
    } catch {
      return null;
    }
  }

  private async recoverInterruptedReplacement(target: string): Promise<void> {
    const backup = `${target}.backup`;
    if (!await isFile(target) && await isFile(backup)) {
      await rename(backup, target);
    } else if (await isFile(target)) {
      await rm(backup, { force: true });
    }
  }

  private async replaceFile(staged: string, target: string): Promise<void> {
    const backup = `${target}.backup`;
    await rm(backup, { force: true });
    const hadTarget = await isFile(target);
    if (hadTarget) {
      await rename(target, backup);
    }
    try {
      await rename(staged, target);
    } catch (error) {
      if (hadTarget && await isFile(backup)) {
        await rename(backup, target);
      }
      await rm(staged, { force: true });
      throw error;
    }
    await rm(backup, { force: true });
  }
}

export class DiagnosticHistoryController {
  private timer: NodeJS.Timeout | null = null;
  private lastPersistedRevision = -1;
  private writeInFlight: Promise<void> | null = null;
  private persistAgain = false;

  constructor(
    private readonly store: DiagnosticHistoryStore,
    private readonly capture: LogCapture,
    private readonly intervalMs = DEFAULT_PERSIST_INTERVAL_MS,
  ) {}

  async start(): Promise<void> {
    this.capture.setPreviousRunSnapshot(await this.store.beginAppRun());
    await this.persistIfChanged(true);
    this.timer = setInterval(() => {
      void this.persistIfChanged().catch((error) => {
        console.warn("[Diagnostics] Failed to persist current diagnostic snapshot:", error);
      });
    }, this.intervalMs);
    this.timer.unref?.();
  }

  async flush(): Promise<void> {
    await this.persistIfChanged(true);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private async persistIfChanged(force = false): Promise<void> {
    const revision = this.capture.getRevision();
    if (!force && revision === this.lastPersistedRevision) return;
    if (this.writeInFlight) {
      this.persistAgain = true;
      await this.writeInFlight;
      return;
    }

    // Manual exports keep the full in-memory ring. Periodic crash history uses
    // a smaller tail plus retained state so it cannot contend with streaming.
    const snapshot = this.capture.exportCurrentRedacted(PERSISTED_EVENT_LIMIT);
    this.writeInFlight = this.store.saveCurrent(snapshot);
    try {
      await this.writeInFlight;
      this.lastPersistedRevision = revision;
    } finally {
      this.writeInFlight = null;
    }
    if (this.persistAgain) {
      this.persistAgain = false;
      await this.persistIfChanged();
    }
  }
}
