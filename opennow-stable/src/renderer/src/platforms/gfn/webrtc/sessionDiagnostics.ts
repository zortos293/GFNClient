import type { SessionInfo } from "@shared/gfn";

const SERVER_CITY_LABELS: Record<string, string> = {
  dal: "US Central",
  ash: "US East",
  chi: "US Central",
  nwk: "US East",
  pdx: "US West",
  atl: "US East",
  mia: "US East",
  lax: "US West",
  phx: "US West",
  sjc: "US West",
  sjc6: "US West",
  ams: "Netherlands",
  frk: "Germany",
  fra: "Germany",
  par: "France",
  lon: "United Kingdom",
  lhr: "United Kingdom",
  sth: "Sweden",
  arn: "Sweden",
  sof: "Bulgaria",
  waw: "Poland",
  bom: "India",
  tyo: "Japan",
  osa: "Japan",
  mon: "Canada",
  yyz: "Canada",
  sel: "South Korea",
  seo: "South Korea",
  bkk: "Thailand",
  kul: "Malaysia",
  sin: "Singapore",
  hkg: "Hong Kong",
  tpe: "Taiwan",
  syd: "Australia",
  mad: "Spain",
  mil: "Italy",
  yto: "Canada",
  gru: "Brazil",
  sao: "Brazil",
};

const SERVER_ZONE_SKIP_TOKENS = new Set([
  "yes",
  "geforcenow",
  "nvidiagrid",
  "net",
  "com",
  "cloudmatch",
  "cloudmatchbeta",
]);

const SERVER_ZONE_PREFIX_LABELS: Record<string, string> = {
  npa: "NP",
  np: "NP",
};

const SERVER_GPU_NAME_MAP: Record<string, string> = {
  "1060b / T10-8": "Basic Rig",
  "1060bi / T10-8": "Basic Rig",
  "1060c / T10-8": "Basic Rig",
  "1080d / P40": "Basic Rig",
  "2080c / T10": "Basic Rig",
  "3050b / L40-6": "Basic Rig",
  "3050b / L40G-6": "Basic Rig",
  "3050b / L40S-6": "Basic Rig",
  "3050b / A10G-6": "Basic Rig",
  "2060c / L40G-8": "GeForce RTX 2060",
  "2080d / T10": "GeForce RTX",
  "2080h / T10": "GeForce RTX",
  "3060d / L40-24": "GeForce RTX",
  "3060d / L40-12": "GeForce RTX",
  "3060d / L40G-12": "GeForce RTX",
  "3060d / L40S-12": "GeForce RTX",
  "3060d / L40S-24": "GeForce RTX",
  "3060d / A10G-12": "GeForce RTX",
  "3080h / A10G": "GeForce RTX 3080",
  "3080p / A10Gx2": "GeForce RTX 3080",
  "4080h / L40": "GeForce RTX 4080",
  "4080h / L40G": "GeForce RTX 4080",
  "4080h / L40S": "GeForce RTX 4080",
  "4080p / L40x2": "GeForce RTX 4080",
  "4080p / L40Gx2": "GeForce RTX 4080",
  "4080p / L40Sx2": "GeForce RTX 4080",
  "5080h / B40": "GeForce RTX 5080",
};

export interface StreamSessionDiagnostics {
  sessionId: string;
  serverRegion: string;
  serverZone: string;
  serverLocation: string;
  serverGpuType: string;
}

export function normalizeServerRegion(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }

  try {
    const url = new URL(trimmed.includes("://") ? trimmed : `https://${trimmed}`);
    return url.hostname || trimmed;
  } catch {
    return trimmed;
  }
}

export function mapServerGpuType(value: string): string {
  const normalized = value.trim();
  return SERVER_GPU_NAME_MAP[normalized] ?? normalized;
}

function serverZonePrefix(tokens: string[], cityIndex: number): string | undefined {
  const token = tokens
    .slice(0, cityIndex)
    .filter((candidate) => !SERVER_ZONE_SKIP_TOKENS.has(candidate))
    .at(-1);
  return token ? SERVER_ZONE_PREFIX_LABELS[token] ?? token.toUpperCase() : undefined;
}

export function formatServerLocation(zone: string, location: string): string {
  const normalizedZone = normalizeServerRegion(zone);
  const zoneCode = normalizedZone === "prod" || normalizedZone.startsWith("prod.")
    ? "prod"
    : normalizedZone.toLowerCase();
  const normalizedLocation = normalizeServerRegion(location);
  const zoneTokens = zoneCode && zoneCode !== "prod" ? zoneCode.split("-") : [];
  const locationTokens = normalizedLocation.toLowerCase().split(/[.-]/);

  for (const tokens of [zoneTokens, locationTokens]) {
    const cityIndex = tokens.findIndex((token) => token in SERVER_CITY_LABELS);
    if (cityIndex < 0) {
      continue;
    }
    const city = tokens[cityIndex];
    const index = tokens[cityIndex + 1];
    const suffix = index && /^\d+$/.test(index) ? `-${index}` : "";
    const prefix = serverZonePrefix(tokens, cityIndex);
    const zoneId = `${prefix ? `${prefix}-` : ""}${city.toUpperCase()}${suffix}`;
    return `${SERVER_CITY_LABELS[city]} (${zoneId})`;
  }

  if (zoneCode && zoneCode !== "prod") {
    return zoneCode.toUpperCase();
  }

  const rawLocation = location.trim();
  if (rawLocation && !/[.:/]/.test(rawLocation) && !/^(?:\d+-){2,3}\d+$/.test(rawLocation)) {
    return rawLocation;
  }

  const firstHostnameToken = normalizedLocation.split(".")[0];
  if (firstHostnameToken && /[a-z]/i.test(firstHostnameToken)) {
    return firstHostnameToken.toUpperCase();
  }
  return "--";
}

export function getStreamServerLocationLabel(
  diagnostics: Pick<StreamSessionDiagnostics, "serverLocation" | "serverRegion" | "serverZone">,
  fallbackRegion = "",
): string {
  return formatServerLocation(
    diagnostics.serverZone,
    diagnostics.serverLocation.trim()
      || diagnostics.serverRegion.trim()
      || fallbackRegion.trim(),
  );
}

export function deriveStreamSessionDiagnostics(
  session: Pick<
    SessionInfo,
    | "sessionId"
    | "zone"
    | "serverLocation"
    | "signalingServer"
    | "streamingBaseUrl"
    | "serverIp"
    | "gpuType"
  >,
): StreamSessionDiagnostics {
  const serverLocation = session.serverLocation?.trim() ?? "";
  return {
    sessionId: session.sessionId,
    serverZone: session.zone,
    serverLocation,
    serverGpuType: mapServerGpuType(session.gpuType ?? ""),
    serverRegion: normalizeServerRegion(
      serverLocation ||
      session.signalingServer ||
      session.streamingBaseUrl ||
      session.serverIp,
    ),
  };
}
