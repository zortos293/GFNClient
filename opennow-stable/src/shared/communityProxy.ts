export const ZORTOS_COMMUNITY_PROXY_HOST = "opennow-proxy-tcp.zortos.me";
export const ZORTOS_COMMUNITY_PROXY_FALLBACK_HOST = "217.76.50.166";
export const ZORTOS_COMMUNITY_PROXY_PORT = 3128;
export const ZORTOS_COMMUNITY_PROXY_PROVISION_URL = "https://opennow-proxy.zortos.me/api/public/proxy";
export const ZORTOS_GITHUB_SPONSORS_URL = "https://github.com/sponsors/zortos293";

export interface CommunityProxyProvisionResult {
  proxyUrl: string;
}

export function buildZortosCommunityProxyUrl(username: string, password: string): string {
  return `http://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${ZORTOS_COMMUNITY_PROXY_HOST}:${ZORTOS_COMMUNITY_PROXY_PORT}`;
}

export function isZortosCommunityProxyUrl(raw?: string): boolean {
  const trimmed = raw?.trim() ?? "";
  if (!trimmed) {
    return false;
  }

  try {
    const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
    const parsed = new URL(candidate);
    const host = parsed.hostname.toLowerCase();
    return (
      (host === ZORTOS_COMMUNITY_PROXY_HOST || host === ZORTOS_COMMUNITY_PROXY_FALLBACK_HOST)
      && parsed.port === String(ZORTOS_COMMUNITY_PROXY_PORT)
    );
  } catch {
    return false;
  }
}
