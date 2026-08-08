export function parseExternalHttpUrl(url: string): URL {
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
    throw new Error("Only HTTP(S) external URLs can be opened.");
  }
  return parsed;
}

const LAUNCHER_PROTOCOLS: Partial<Record<NodeJS.Platform, ReadonlySet<string>>> = {
  darwin: new Set([
    "com.epicgames.launcher:",
    "itms-apps:",
    "macappstore:",
    "steam:",
  ]),
  win32: new Set([
    "com.epicgames.launcher:",
    "ms-windows-store:",
    "steam:",
  ]),
};

export function parseExplicitExternalUrl(
  url: string,
  platform: NodeJS.Platform = process.platform,
): URL {
  const parsed = new URL(url);
  if (parsed.protocol === "https:" || parsed.protocol === "http:") {
    return parsed;
  }
  if (LAUNCHER_PROTOCOLS[platform]?.has(parsed.protocol)) {
    return parsed;
  }
  throw new Error("Only HTTP(S) and supported store launcher URLs can be opened.");
}
