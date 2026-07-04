import { app } from "electron";
import { join } from "node:path";
import { readFile, writeFile } from "node:fs/promises";
import type { ReleaseHighlightsPayload } from "@shared/gfn";
import { pickRuntimeGitHubToken } from "./githubRuntimeToken";

const GITHUB_API_BASE = "https://api.github.com/repos/OpenCloudGaming/OpenNOW";
const FETCH_TIMEOUT_MS = 8000;
const CACHE_FILE = "release-notes-cache.json";
const RELEASE_VERSION_PATTERN = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/;

// ---------------------------------------------------------------------------
// Version comparison
// ---------------------------------------------------------------------------

interface SemverParts {
  major: number;
  minor: number;
  patch: number;
  /** Prerelease identifiers, e.g. ["beta", "1"]. Empty = stable release. */
  prerelease: string[];
}

export function normalizeReleaseVersion(version: unknown): string | null {
  if (typeof version !== "string") {
    return null;
  }
  const clean = version.trim().replace(/^v/i, "");
  return RELEASE_VERSION_PATTERN.test(clean) ? clean : null;
}

function parseSemver(v: string): SemverParts {
  const clean = v.replace(/^v/i, "").split("+", 1)[0] ?? "";
  const dashIdx = clean.indexOf("-");
  const numericPart = dashIdx === -1 ? clean : clean.slice(0, dashIdx);
  const prerelease = dashIdx === -1 ? [] : clean.slice(dashIdx + 1).split(".");
  const [rawMajor = "0", rawMinor = "0", rawPatch = "0"] = numericPart.split(".");
  return {
    major: Math.max(0, parseInt(rawMajor, 10) || 0),
    minor: Math.max(0, parseInt(rawMinor, 10) || 0),
    patch: Math.max(0, parseInt(rawPatch, 10) || 0),
    prerelease,
  };
}

/**
 * Compare two prerelease strings.
 * Stable (empty string) is GREATER than any prerelease per semver spec.
 * When both have prerelease identifiers, compare component-by-component.
 * Returns: positive if a > b, negative if a < b, 0 if equal.
 */
function comparePrerelease(a: string[], b: string[]): number {
  if (a.length === 0 && b.length === 0) return 0;
  if (a.length === 0) return 1;
  if (b.length === 0) return -1;

  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    const left = a[index];
    const right = b[index];
    if (left === undefined) return -1;
    if (right === undefined) return 1;
    if (left === right) continue;

    const leftNumeric = /^[0-9]+$/.test(left);
    const rightNumeric = /^[0-9]+$/.test(right);
    if (leftNumeric && rightNumeric) {
      return Number(left) - Number(right);
    }
    if (leftNumeric) return -1;
    if (rightNumeric) return 1;
    return left < right ? -1 : 1;
  }

  return 0;
}

/**
 * Returns true if `current` is strictly greater than `lastSeen` (semver-aware).
 * An empty `lastSeen` means the user has never seen highlights.
 */
export function shouldShowReleaseHighlights(current: string, lastSeen: string): boolean {
  if (!current) return false;
  if (!lastSeen) return true;
  const a = parseSemver(current);
  const b = parseSemver(lastSeen);
  if (a.major !== b.major) return a.major > b.major;
  if (a.minor !== b.minor) return a.minor > b.minor;
  if (a.patch !== b.patch) return a.patch > b.patch;
  return comparePrerelease(a.prerelease, b.prerelease) > 0;
}

// ---------------------------------------------------------------------------
// Release-notes cache (for offline fallback)
// ---------------------------------------------------------------------------

type ReleaseNotesCache = Record<string, string>;

function getCachePath(): string {
  return join(app.getPath("userData"), CACHE_FILE);
}

async function readCache(): Promise<ReleaseNotesCache> {
  try {
    return JSON.parse(await readFile(getCachePath(), "utf-8")) as ReleaseNotesCache;
  } catch {
    return {};
  }
}

export async function writeCacheEntry(version: string, body: string): Promise<void> {
  try {
    const cache = await readCache();
    cache[version] = body;
    await writeFile(getCachePath(), JSON.stringify(cache, null, 2), "utf-8");
  } catch (error) {
    console.warn("[ReleaseHighlights] Failed to write cache:", error);
  }
}

async function readCacheEntry(version: string): Promise<string | null> {
  const cache = await readCache();
  return cache[version] ?? null;
}

// ---------------------------------------------------------------------------
// GitHub API fetch
// ---------------------------------------------------------------------------

interface GitHubRelease {
  tag_name: string;
  name: string | null;
  body: string | null;
}

async function fetchFromGitHub(version: string): Promise<string | null> {
  const tag = version.startsWith("v") ? version : `v${version}`;
  const url = `${GITHUB_API_BASE}/releases/tags/${encodeURIComponent(tag)}`;

  const headers: HeadersInit = {
    Accept: "application/vnd.github+json",
    "User-Agent": `OpenNOW/${version} (electron)`,
  };

  const token = pickRuntimeGitHubToken();
  if (token) {
    (headers as Record<string, string>)["Authorization"] = `token ${token}`;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

  try {
    const response = await fetch(url, { headers, signal: controller.signal });
    if (!response.ok) {
      console.warn(`[ReleaseHighlights] GitHub API returned ${response.status} for ${tag}`);
      return null;
    }
    const data = (await response.json()) as GitHubRelease;
    return data.body ?? null;
  } catch (error) {
    if ((error as Error).name === "AbortError") {
      console.warn("[ReleaseHighlights] GitHub fetch timed out");
    } else {
      console.warn("[ReleaseHighlights] GitHub fetch failed:", error);
    }
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

const FALLBACK_BODY_TEMPLATE = (version: string): string =>
  `Release notes for OpenNOW v${version} could not be loaded right now.\n\nView the full changelog on GitHub.`;

/**
 * Build the full ReleaseHighlightsPayload for a given version.
 * Tries GitHub first, then the local updater cache, then a fallback string.
 * Never throws — always returns a payload.
 */
export async function getReleaseHighlightsPayload(version: string): Promise<ReleaseHighlightsPayload> {
  const cleanVersion = normalizeReleaseVersion(version)
    ?? normalizeReleaseVersion(app.getVersion())
    ?? "0.0.0";
  const displayTitle = `OpenNOW v${cleanVersion}`;

  // 1. Try GitHub
  const githubBody = await fetchFromGitHub(cleanVersion);
  if (githubBody) {
    // Cache successful GitHub fetch for offline fallback next time
    await writeCacheEntry(cleanVersion, githubBody);
    return {
      version: cleanVersion,
      title: displayTitle,
      bodyMarkdown: githubBody,
      source: "github",
    };
  }

  // 2. Try local updater cache
  const cachedBody = await readCacheEntry(cleanVersion);
  if (cachedBody) {
    return {
      version: cleanVersion,
      title: displayTitle,
      bodyMarkdown: cachedBody,
      source: "updater-cache",
    };
  }

  // 3. Fallback copy
  return {
    version: cleanVersion,
    title: displayTitle,
    bodyMarkdown: FALLBACK_BODY_TEMPLATE(cleanVersion),
    source: "fallback",
  };
}
