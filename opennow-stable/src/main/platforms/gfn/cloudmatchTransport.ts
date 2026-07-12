import { buildGfnCloudMatchHeaders } from "./clientHeaders";
import { fetchWithOptionalProxy } from "./proxyFetch";

export const CLOUDMATCH_REQUEST_TIMEOUT_MS = 30_000;
export const CLOUDMATCH_GET_RETRIES = 2;
export const CLOUDMATCH_RETRY_DELAYS_MS = [250, 750];
export const CLOUDMATCH_RETRY_STATUSES = new Set([408, 425, 429, 500, 502, 503, 504]);

export interface CloudMatchServerInfoResponse {
  metaData?: Array<{
    key: string;
    value: string;
  }>;
}

export interface CloudMatchFetchOptions {
  proxyUrl?: string;
  timeoutMs?: number;
  retries?: number;
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function formatErrorForLog(error: unknown): string {
  if (error instanceof Error) {
    const cause = error.cause instanceof Error ? `: ${error.cause.message}` : "";
    return `${error.message}${cause}`;
  }
  return String(error);
}

export async function fetchCloudMatch(
  input: string,
  init: RequestInit,
  options: CloudMatchFetchOptions = {},
): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const retries = options.retries ?? (method === "GET" ? CLOUDMATCH_GET_RETRIES : 0);
  const timeoutMs = options.timeoutMs ?? CLOUDMATCH_REQUEST_TIMEOUT_MS;

  let lastError: unknown;
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetchWithOptionalProxy(input, {
        ...init,
        signal: controller.signal,
      }, options.proxyUrl);
      clearTimeout(timeout);

      if (attempt < retries && CLOUDMATCH_RETRY_STATUSES.has(response.status)) {
        await sleep(CLOUDMATCH_RETRY_DELAYS_MS[Math.min(attempt, CLOUDMATCH_RETRY_DELAYS_MS.length - 1)] ?? 0);
        continue;
      }

      return response;
    } catch (error) {
      clearTimeout(timeout);
      lastError = error;
      if (attempt >= retries) {
        throw error;
      }

      const retryDelay = CLOUDMATCH_RETRY_DELAYS_MS[Math.min(attempt, CLOUDMATCH_RETRY_DELAYS_MS.length - 1)];
      await sleep(retryDelay ?? 0);
    }
  }

  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

export function normalizeCloudMatchBaseUrl(url: string): string {
  const trimmed = url.trim();
  const withProtocol = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  return withProtocol.endsWith("/") ? withProtocol.slice(0, -1) : withProtocol;
}

export function extractServerInfoRegionBases(payload: CloudMatchServerInfoResponse): string[] {
  const metadata = payload.metaData ?? [];
  const byKey = new Map(metadata.map((entry) => [entry.key, entry.value]));
  const regionNames = byKey.get("gfn-regions")
    ?.split(",")
    .map((entry) => entry.trim())
    .filter(Boolean) ?? [];
  const localRegionName = byKey.get("local-region")?.trim();
  const orderedRegionNames = [
    ...(localRegionName ? [localRegionName] : []),
    ...regionNames,
  ];
  const bases: string[] = [];
  const seen = new Set<string>();

  for (const regionName of orderedRegionNames) {
    const regionUrl = byKey.get(regionName);
    if (!regionUrl?.startsWith("http")) {
      continue;
    }
    const normalized = normalizeCloudMatchBaseUrl(regionUrl);
    if (!seen.has(normalized)) {
      seen.add(normalized);
      bases.push(normalized);
    }
  }

  return bases;
}

export function isDefaultStreamingServiceBase(baseUrl: string): boolean {
  try {
    const hostname = new URL(baseUrl).hostname.toLowerCase();
    return hostname === "prod.cloudmatchbeta.nvidiagrid.net" ||
      (hostname.startsWith("prod.") && hostname.endsWith(".nvidiagrid.net"));
  } catch {
    return false;
  }
}

export async function resolveCreateSessionBase(
  base: string,
  token: string,
  clientId: string,
  deviceId: string,
  proxyUrl?: string,
): Promise<string> {
  if (!isDefaultStreamingServiceBase(base)) {
    return base;
  }

  try {
    const response = await fetchCloudMatch(`${base}/v2/serverInfo`, {
      method: "GET",
      headers: buildGfnCloudMatchHeaders({ token, clientId, deviceId, includeOrigin: false }),
    }, { proxyUrl });
    if (!response.ok) {
      return base;
    }

    const [localRegionBase] = extractServerInfoRegionBases(
      (await response.json()) as CloudMatchServerInfoResponse,
    );
    if (!localRegionBase || localRegionBase === base) {
      return base;
    }

    console.log(`[CloudMatch] createSession resolved ${base} to local region ${localRegionBase}`);
    return localRegionBase;
  } catch (error) {
    console.warn(`[CloudMatch] createSession local-region discovery failed: ${formatErrorForLog(error)}`);
    return base;
  }
}

export function cloudmatchUrl(zone: string): string {
  return `https://${zone}.cloudmatchbeta.nvidiagrid.net`;
}

export function resolveStreamingBaseUrl(zone: string, provided?: string): string {
  if (provided && provided.trim()) {
    const trimmed = provided.trim();
    return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
  }
  return cloudmatchUrl(zone);
}

export function shouldUseServerIp(baseUrl: string): boolean {
  return baseUrl.includes("cloudmatchbeta.nvidiagrid.net");
}

/**
 * Check if a given IP/hostname is a CloudMatch zone load balancer hostname
 * (not a real game server IP). Zone hostnames look like:
 *   np-ams-06.cloudmatchbeta.nvidiagrid.net
 */
export function isZoneHostname(ip: string): boolean {
  const hostname = ip.trim().toLowerCase().replace(/\.$/, "");
  return ["cloudmatchbeta.nvidiagrid.net", "cloudmatch.nvidiagrid.net"].some(
    (domain) => hostname === domain || hostname.endsWith(`.${domain}`),
  );
}

export function resolvePollStopBase(zone: string, provided?: string, serverIp?: string): string {
  const base = resolveStreamingBaseUrl(zone, provided);
  // Only use serverIp if it's a real server IP (not a zone hostname).
  // The Rust version checks: if we're NOT an alliance partner AND we have a server_ip, use it.
  // But if the "serverIp" is actually the zone hostname (from an early poll when connectionInfo
  // was empty), using it is circular and doesn't help.
  if (serverIp && shouldUseServerIp(base) && !isZoneHostname(serverIp)) {
    return `https://${serverIp}`;
  }
  return base;
}
