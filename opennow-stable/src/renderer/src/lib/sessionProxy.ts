import type { Settings } from "@shared/gfn";

import { buildCatalogQueryKey } from "./catalogSnapshot";

export function getEnabledSessionProxyUrl(settings: Pick<Settings, "sessionProxyEnabled" | "sessionProxyUrl">): string | undefined {
  const proxyUrl = settings.sessionProxyEnabled ? settings.sessionProxyUrl.trim() : "";
  return proxyUrl || undefined;
}

export function getSessionProxyUiScope(proxyUrl: string | undefined): string {
  if (!proxyUrl) return "direct";
  const trimmed = proxyUrl.trim();
  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;

  try {
    const parsed = new URL(candidate);
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    return "proxy";
  }
}

export function hasSessionProxyCredentials(proxyUrl: string | undefined): boolean {
  if (!proxyUrl) return false;
  const trimmed = proxyUrl.trim();
  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;

  try {
    const parsed = new URL(candidate);
    return parsed.username.length > 0 || parsed.password.length > 0;
  } catch {
    return false;
  }
}

export function buildProxyAwareCatalogQueryKey(
  searchQuery: string,
  filterIds: string[],
  sortId: string,
  proxyUrl: string | undefined,
): string {
  return `${buildCatalogQueryKey(searchQuery, filterIds, sortId)}|${getSessionProxyUiScope(proxyUrl)}`;
}
