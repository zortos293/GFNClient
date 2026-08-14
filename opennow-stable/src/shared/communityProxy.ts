export const ZORTOS_COMMUNITY_PROXY_HOST = "altaria.proxy.rlwy.net";
export const ZORTOS_COMMUNITY_PROXY_PORT = 51545;
export const ZORTOS_COMMUNITY_PROXY_PROVISION_URL =
  "https://opennow-proxy-production.up.railway.app/api/public/proxy";
export const ZORTOS_GITHUB_SPONSORS_URL = "https://github.com/sponsors/zortos293";

const LEGACY_COMMUNITY_PROXY_ENDPOINTS = [
  { host: "opennow-proxy-tcp.zortos.me", port: "3128" },
  { host: "217.76.50.166", port: "3128" },
] as const;

export interface CommunityProxyProvisionResult {
  proxyUrl: string;
}

export function buildZortosCommunityProxyUrl(username: string, password: string): string {
  return `http://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${ZORTOS_COMMUNITY_PROXY_HOST}:${ZORTOS_COMMUNITY_PROXY_PORT}`;
}

function isCommunityProxyEndpoint(host: string, port: string): boolean {
  if (host === ZORTOS_COMMUNITY_PROXY_HOST && port === String(ZORTOS_COMMUNITY_PROXY_PORT)) {
    return true;
  }
  return LEGACY_COMMUNITY_PROXY_ENDPOINTS.some((endpoint) => endpoint.host === host && endpoint.port === port);
}

export function isZortosCommunityProxyUrl(raw?: string): boolean {
  const trimmed = raw?.trim() ?? "";
  if (!trimmed) {
    return false;
  }

  try {
    const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
    const parsed = new URL(candidate);
    return isCommunityProxyEndpoint(parsed.hostname.toLowerCase(), parsed.port);
  } catch {
    return false;
  }
}
