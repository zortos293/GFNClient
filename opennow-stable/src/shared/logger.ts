/**
 * Shared logger utilities for log capture and privacy-safe export
 * Handles redaction of sensitive information like emails, passwords, tokens, etc.
 */

export interface LogEntry {
  timestamp: number;
  level: "log" | "error" | "warn" | "info" | "debug";
  prefix: string;
  message: string;
  args: unknown[];
}

export interface PreviousLogSnapshot {
  capturedAt: number;
  text: string;
}

interface RetainedLogContext {
  updatedAt: number;
  values: Record<string, unknown>;
}

interface ConsoleOutputStream {
  destroyed?: boolean;
  writable?: boolean;
  writableEnded?: boolean;
  on?(event: "error", listener: (error: unknown) => void): unknown;
  removeListener?(event: "error", listener: (error: unknown) => void): unknown;
  listeners?(event: "error"): unknown[];
}

interface ConsoleOutputStreams {
  stdout?: ConsoleOutputStream;
  stderr?: ConsoleOutputStream;
}

const BROKEN_WRITE_ERROR_CODES = new Set([
  "EIO",
  "EPIPE",
  "ERR_STREAM_DESTROYED",
  "ERR_STREAM_PREMATURE_CLOSE",
  "ERR_STREAM_WRITE_AFTER_END",
]);

/** Maximum number of log entries to keep in memory */
const MAX_LOG_ENTRIES = 5000;

/** Retained state is intentionally small so frequent runtime updates stay cheap. */
const MAX_CONTEXT_SECTIONS = 32;
const MAX_CONTEXT_FIELDS = 64;
const MAX_CONTEXT_VALUE_CHARACTERS = 4000;

/** Patterns for sensitive data redaction */
const SENSITIVE_PATTERNS = [
  // Email addresses
  { pattern: /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, replacement: "[Redacted for privacy]" },
  // Authorization tokens (GFNJWT, Bearer, etc.)
  { pattern: /Authorization["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]+\s+[a-zA-Z0-9_-]+/gi, replacement: 'Authorization: [Redacted for privacy]' },
  { pattern: /\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, replacement: "Bearer [Redacted for privacy]" },
  // JWT tokens (three base64url parts separated by dots)
  { pattern: /[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}/g, replacement: "[Redacted for privacy]" },
  // Client tokens, access tokens
  { pattern: /client[_-]?token["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]{20,}/gi, replacement: 'client_token: [Redacted for privacy]' },
  { pattern: /access[_-]?token["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]{20,}/gi, replacement: 'access_token: [Redacted for privacy]' },
  { pattern: /refresh[_-]?token["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]{20,}/gi, replacement: 'refresh_token: [Redacted for privacy]' },
  { pattern: /\b(?:ice[_-]?pwd|ice[_-]?ufrag|pwd|ufrag)["']?\s*[:=]\s*["']?[^\s"',;&]+/gi, replacement: 'ice_credential: [Redacted for privacy]' },
  // Session IDs (UUID-like)
  { pattern: /session[_-]?id["']?\s*[:=]\s*["']?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, replacement: 'session_id: [Redacted for privacy]' },
  // Passwords
  { pattern: /password["']?\s*[:=]\s*["']?[^\s"']{4,}/gi, replacement: 'password: [Redacted for privacy]' },
  // API keys
  { pattern: /api[_-]?key["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]{16,}/gi, replacement: 'api_key: [Redacted for privacy]' },
  // Credential/secret
  { pattern: /credential["']?\s*[:=]\s*["']?[^\s"']{8,}/gi, replacement: 'credential: [Redacted for privacy]' },
  { pattern: /secret["']?\s*[:=]\s*["']?[^\s"']{8,}/gi, replacement: 'secret: [Redacted for privacy]' },
  // IP addresses (might be sensitive in some contexts)
  { pattern: /\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b/g, replacement: "[Redacted IP]" },
  { pattern: /(?<![A-F0-9:])(?:[A-F0-9]{1,4}:){2,7}[A-F0-9]{1,4}(?![A-F0-9:])/gi, replacement: "[Redacted IP]" },
  { pattern: /(?<![A-F0-9:])(?:(?:[A-F0-9]{1,4}:){1,7}:|::(?:[A-F0-9]{1,4}:){0,6})(?:[A-F0-9]{1,4})?(?![A-F0-9:])/gi, replacement: "[Redacted IP]" },
  // Device IDs, client IDs (UUID-like patterns)
  { pattern: /device[_-]?id["']?\s*[:=]\s*["']?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, replacement: 'device_id: [Redacted for privacy]' },
  { pattern: /client[_-]?id["']?\s*[:=]\s*["']?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, replacement: 'client_id: [Redacted for privacy]' },
  // User IDs that look like UUIDs
  { pattern: /user[_-]?id["']?\s*[:=]\s*["']?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, replacement: 'user_id: [Redacted for privacy]' },
  // OAuth codes
  { pattern: /code["']?\s*[:=]\s*["']?[a-zA-Z0-9_-]{20,}/gi, replacement: 'code: [Redacted for privacy]' },
  // Peer names/IDs in signaling
  { pattern: /peer[_-]?name["']?\s*[:=]\s*["']?peer-\d+/gi, replacement: 'peer_name: [Redacted for privacy]' },
  // UUIDs and user-home paths can appear without a descriptive key.
  { pattern: /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/gi, replacement: "[Redacted ID]" },
  { pattern: /([/\\](?:Users|home)[/\\])[^/\\\s"']+/g, replacement: "$1[Redacted]" },
];

function serializeLogValue(value: unknown): string {
  try {
    const serialized = typeof value === "string"
      ? value
      : typeof value === "object" && value !== null
        ? JSON.stringify(value)
        : String(value);
    const singleLine = serialized.replace(/\s+/g, " ").trim();
    if (singleLine.length <= MAX_CONTEXT_VALUE_CHARACTERS) {
      return singleLine;
    }
    return `${singleLine.slice(0, MAX_CONTEXT_VALUE_CHARACTERS)}... [truncated ${singleLine.length - MAX_CONTEXT_VALUE_CHARACTERS} chars]`;
  } catch {
    return "[Unserializable value]";
  }
}

/**
 * Redact sensitive information from a string
 */
export function redactSensitiveData(text: string): string {
  let redacted = text;
  for (const { pattern, replacement } of SENSITIVE_PATTERNS) {
    redacted = redacted.replace(pattern, replacement);
  }
  return redacted;
}

/**
 * Format a log entry to string
 */
export function formatLogEntry(entry: LogEntry): string {
  const date = new Date(entry.timestamp);
  const timeStr = date.toISOString();
  const levelStr = entry.level.toUpperCase().padStart(5);
  const prefixStr = entry.prefix ? `[${entry.prefix}] ` : "";
  const argsStr = entry.args.length > 0
    ? " " + entry.args.map(arg => {
        try {
          if (typeof arg === "object" && arg !== null) {
            return JSON.stringify(arg);
          }
          return String(arg);
        } catch {
          return "[Object]";
        }
      }).join(" ")
    : "";

  return `${timeStr} ${levelStr} ${prefixStr}${entry.message}${argsStr}`;
}

/**
 * Create redacted log export
 */
export function createRedactedLogExport(entries: LogEntry[]): string {
  const lines = entries.map(entry => {
    const formatted = formatLogEntry(entry);
    return redactSensitiveData(formatted);
  });

  return lines.join("\n");
}

export function isTerminalBrokenWriteError(
  error: unknown,
  stream?: Pick<ConsoleOutputStream, "destroyed" | "writable" | "writableEnded">,
): boolean {
  if (!error || (typeof error !== "object" && typeof error !== "function")) {
    return false;
  }

  const code = "code" in error && typeof error.code === "string" ? error.code : "";
  if (BROKEN_WRITE_ERROR_CODES.has(code)) {
    return true;
  }

  if (!stream?.destroyed && stream?.writable !== false && !stream?.writableEnded) {
    return false;
  }

  const message = "message" in error && typeof error.message === "string" ? error.message : "";
  return /broken pipe|destroyed stream|stream (?:was )?destroyed|write after end|write.*(?:closed|ended|stream)/i.test(message);
}

function getProcessOutputStreams(): ConsoleOutputStreams {
  const runtimeProcess = (globalThis as {
    process?: ConsoleOutputStreams;
  }).process;
  return {
    stdout: runtimeProcess?.stdout,
    stderr: runtimeProcess?.stderr,
  };
}

/**
 * Logger class that captures console output
 */
export class LogCapture {
  private entries: LogEntry[] = [];
  private retainedContext = new Map<string, RetainedLogContext>();
  private previousRunSnapshot: PreviousLogSnapshot | null = null;
  private revision = 0;
  private originalConsole: Partial<typeof console> | null = null;
  private streamErrorListeners: Array<{
    stream: ConsoleOutputStream;
    listener: (error: unknown) => void;
  }> = [];
  private processName: string;

  constructor(
    processName: string,
    private readonly outputStreams: ConsoleOutputStreams = getProcessOutputStreams(),
  ) {
    this.processName = processName;
  }

  /**
   * Get all captured log entries
   */
  getEntries(): LogEntry[] {
    return [...this.entries];
  }

  /**
   * Clear all log entries
   */
  clear(): void {
    this.entries = [];
    this.revision += 1;
  }

  /**
   * Get count of captured entries
   */
  getCount(): number {
    return this.entries.length;
  }

  getRevision(): number {
    return this.revision;
  }

  /**
   * Keep the latest bounded state for a diagnostic concern. Replacing rather
   * than merging makes ownership explicit and prevents stale fields from
   * surviving when a module changes what it reports.
   */
  setContext(section: string, values: Record<string, unknown>): void {
    const normalizedSection = section.trim().replace(/[^a-zA-Z0-9_.-]+/g, "-");
    if (!normalizedSection) {
      return;
    }

    const boundedValues = Object.fromEntries(
      Object.entries(values).slice(0, MAX_CONTEXT_FIELDS),
    );
    if (!this.retainedContext.has(normalizedSection) && this.retainedContext.size >= MAX_CONTEXT_SECTIONS) {
      const oldestSection = this.retainedContext.keys().next().value;
      if (typeof oldestSection === "string") {
        this.retainedContext.delete(oldestSection);
      }
    }
    this.retainedContext.set(normalizedSection, {
      updatedAt: Date.now(),
      values: boundedValues,
    });
    this.revision += 1;
  }

  setPreviousRunSnapshot(snapshot: PreviousLogSnapshot | null): void {
    this.previousRunSnapshot = snapshot;
  }

  /**
   * Add a log entry directly
   */
  addEntry(level: LogEntry["level"], prefix: string, message: string, args: unknown[]): void {
    const entry: LogEntry = {
      timestamp: Date.now(),
      level,
      prefix,
      message,
      args,
    };

    this.entries.push(entry);
    this.revision += 1;

    // Keep log size bounded
    if (this.entries.length > MAX_LOG_ENTRIES) {
      this.entries.shift();
    }
  }

  /**
   * Intercept console methods to capture logs
   */
  interceptConsole(): void {
    if (this.originalConsole) {
      return; // Already intercepted
    }

    this.originalConsole = {
      log: console.log,
      error: console.error,
      warn: console.warn,
      info: console.info,
      debug: console.debug,
    };
    this.installStreamErrorHandlers();

    // Extract prefix from first argument if it's a string like "[Module] message"
    const extractPrefix = (args: unknown[]): { prefix: string; message: string; rest: unknown[] } => {
      if (args.length > 0 && typeof args[0] === "string") {
        const match = args[0].match(/^\[([^\]]+)\]\s*(.*)$/);
        if (match) {
          return {
            prefix: match[1],
            message: match[2],
            rest: args.slice(1),
          };
        }
      }
      return {
        prefix: this.processName,
        message: args.length > 0 ? String(args[0]) : "",
        rest: args.slice(1),
      };
    };

    const forward = (level: LogEntry["level"], args: unknown[]): void => {
      const method = this.originalConsole?.[level];
      if (!method) {
        return;
      }

      const stream = level === "error" || level === "warn"
        ? this.outputStreams.stderr
        : this.outputStreams.stdout;
      try {
        method.apply(console, args);
      } catch (error) {
        if (!isTerminalBrokenWriteError(error, stream)) {
          throw error;
        }
      }
    };

    console.log = (...args: unknown[]) => {
      const { prefix, message, rest } = extractPrefix(args);
      this.addEntry("log", prefix, message, rest);
      forward("log", args);
    };

    console.error = (...args: unknown[]) => {
      const { prefix, message, rest } = extractPrefix(args);
      this.addEntry("error", prefix, message, rest);
      forward("error", args);
    };

    console.warn = (...args: unknown[]) => {
      const { prefix, message, rest } = extractPrefix(args);
      this.addEntry("warn", prefix, message, rest);
      forward("warn", args);
    };

    console.info = (...args: unknown[]) => {
      const { prefix, message, rest } = extractPrefix(args);
      this.addEntry("info", prefix, message, rest);
      forward("info", args);
    };

    console.debug = (...args: unknown[]) => {
      const { prefix, message, rest } = extractPrefix(args);
      this.addEntry("debug", prefix, message, rest);
      forward("debug", args);
    };
  }

  private installStreamErrorHandlers(): void {
    const streams = new Set(
      [this.outputStreams.stdout, this.outputStreams.stderr].filter(
        (stream): stream is ConsoleOutputStream => Boolean(stream),
      ),
    );

    for (const stream of streams) {
      if (!stream.on) {
        continue;
      }

      const listener = (error: unknown): void => {
        if (isTerminalBrokenWriteError(error, stream)) {
          return;
        }

        const otherErrorListeners = stream.listeners?.("error").filter(
          (candidate) => candidate !== listener,
        );
        if (!otherErrorListeners || otherErrorListeners.length === 0) {
          throw error;
        }
      };
      stream.on("error", listener);
      this.streamErrorListeners.push({ stream, listener });
    }
  }

  /**
   * Restore original console methods
   */
  restoreConsole(): void {
    if (this.originalConsole) {
      if (this.originalConsole.log) console.log = this.originalConsole.log;
      if (this.originalConsole.error) console.error = this.originalConsole.error;
      if (this.originalConsole.warn) console.warn = this.originalConsole.warn;
      if (this.originalConsole.info) console.info = this.originalConsole.info;
      if (this.originalConsole.debug) console.debug = this.originalConsole.debug;
      for (const { stream, listener } of this.streamErrorListeners) {
        stream.removeListener?.("error", listener);
      }
      this.streamErrorListeners = [];
      this.originalConsole = null;
    }
  }

  /**
   * Export logs as redacted text
   */
  exportCurrentRedacted(entryLimit = MAX_LOG_ENTRIES): string {
    const boundedEntryLimit = Math.max(0, Math.min(MAX_LOG_ENTRIES, Math.floor(entryLimit)));
    const exportedEntries = boundedEntryLimit === 0 ? [] : this.entries.slice(-boundedEntryLimit);
    const contextLines: string[] = [];
    for (const [section, context] of [...this.retainedContext.entries()].sort(([left], [right]) => left.localeCompare(right))) {
      contextLines.push(`context.${section}.updatedAt=${new Date(context.updatedAt).toISOString()}`);
      for (const [key, value] of Object.entries(context.values).sort(([left], [right]) => left.localeCompare(right))) {
        contextLines.push(`context.${section}.${key}=${serializeLogValue(value)}`);
      }
    }

    const header = [
      "OpenNOW Desktop diagnostics",
      `generatedAt=${new Date().toISOString()}`,
      `source=${this.processName}`,
      `context.count=${this.retainedContext.size} max=${MAX_CONTEXT_SECTIONS}`,
      ...contextLines,
      `events.count=${exportedEntries.length} max=${MAX_LOG_ENTRIES}`,
      ...(exportedEntries.length < this.entries.length
        ? [`events.omittedEarlier=${this.entries.length - exportedEntries.length}`]
        : []),
    ].join("\n");
    const redactedLogs = createRedactedLogExport(exportedEntries);
    return redactSensitiveData(`${header}\n${redactedLogs}`.trimEnd());
  }

  exportRedacted(includePreviousRun = true): string {
    const current = this.exportCurrentRedacted();
    if (!includePreviousRun || !this.previousRunSnapshot) {
      return current;
    }
    return [
      current,
      "",
      "previousAppRun.diagnostics:",
      `previousAppRun.capturedAt=${new Date(this.previousRunSnapshot.capturedAt).toISOString()}`,
      "----- BEGIN PREVIOUS APP RUN -----",
      redactSensitiveData(this.previousRunSnapshot.text.trimEnd()),
      "----- END PREVIOUS APP RUN -----",
    ].join("\n");
  }

  /**
   * Export logs as JSON (for programmatic use)
   */
  exportJSON(): string {
    const exportData = {
      source: this.processName,
      generatedAt: Date.now(),
      entryCount: this.entries.length,
      context: Object.fromEntries(
        [...this.retainedContext.entries()].map(([section, context]) => [
          section,
          {
            updatedAt: context.updatedAt,
            values: Object.fromEntries(
              Object.entries(context.values).map(([key, value]) => [
                key,
                redactSensitiveData(serializeLogValue(value)),
              ]),
            ),
          },
        ]),
      ),
      entries: this.entries.map(entry => ({
        ...entry,
        // Redact sensitive data in messages and args
        message: redactSensitiveData(entry.message),
        args: entry.args.map(arg => {
          if (typeof arg === "string") {
            return redactSensitiveData(arg);
          }
          if (typeof arg === "object" && arg !== null) {
            try {
              return JSON.parse(redactSensitiveData(JSON.stringify(arg)));
            } catch {
              return arg;
            }
          }
          return arg;
        }),
      })),
      ...(this.previousRunSnapshot ? {
        previousAppRun: {
          capturedAt: this.previousRunSnapshot.capturedAt,
          text: this.previousRunSnapshot.text,
        },
      } : {}),
    };
    return redactSensitiveData(JSON.stringify(exportData, null, 2));
  }
}

// Global log capture instance (used in main process or renderer)
let globalLogCapture: LogCapture | null = null;

/**
 * Initialize global log capture
 */
export function initLogCapture(processName: string): LogCapture {
  if (!globalLogCapture) {
    globalLogCapture = new LogCapture(processName);
    globalLogCapture.interceptConsole();
  }
  return globalLogCapture;
}

/**
 * Get global log capture instance
 */
export function getLogCapture(): LogCapture | null {
  return globalLogCapture;
}

/** Retain the latest diagnostic state in the current process log export. */
export function setLogContext(section: string, values: Record<string, unknown>): void {
  globalLogCapture?.setContext(section, values);
}

/**
 * Export logs from main process (to be called via IPC)
 */
export function exportLogs(format: "text" | "json" = "text"): string {
  if (!globalLogCapture) {
    return "No logs captured";
  }
  return format === "json" ? globalLogCapture.exportJSON() : globalLogCapture.exportRedacted();
}
