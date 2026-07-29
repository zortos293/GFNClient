import { createHash } from "node:crypto";
import { cacheManager } from "../../services/cacheManager";
import { sessionProxyCacheKeyPart, sessionProxyHasCredentials } from "./proxyUrl";
import { fetchPublicGamesUncached } from "./publicGames";
import type { GameInfo } from "@shared/gfn";

export const LIBRARY_GAMES_CACHE_SCOPE = "library:v2";
export const CATALOG_GAMES_CACHE_SCOPE = "catalog";
const PUBLIC_GAMES_CACHE_KEY = "games:public:v2";

function addProxyCacheScope(hash: ReturnType<typeof createHash>, proxyUrl?: string): void {
  const proxyCachePart = sessionProxyCacheKeyPart(proxyUrl);
  if (proxyCachePart) {
    hash.update("\0").update(proxyCachePart);
  }
}

export function publicGamesCacheKey(proxyUrl?: string): string {
  const proxyCachePart = sessionProxyCacheKeyPart(proxyUrl);
  return proxyCachePart ? `${PUBLIC_GAMES_CACHE_KEY}:${proxyCachePart}` : PUBLIC_GAMES_CACHE_KEY;
}

export function shouldBypassGamesCache(proxyUrl?: string): boolean {
  return sessionProxyHasCredentials(proxyUrl);
}

export function accountScopedGamesCacheKey(scope: string, accountId: string, providerStreamingBaseUrl?: string, proxyUrl?: string): string {
  const hash = createHash("sha256")
    .update(accountId)
    .update("\0")
    .update(providerStreamingBaseUrl ?? "");
  addProxyCacheScope(hash, proxyUrl);
  const digest = hash.digest("hex").slice(0, 16);
  return `games:${scope}:${digest}`;
}

export function legacyTokenScopedGamesCacheKey(scope: string, token: string, providerStreamingBaseUrl?: string, proxyUrl?: string): string {
  const hash = createHash("sha256")
    .update(token)
    .update("\0")
    .update(providerStreamingBaseUrl ?? "");
  addProxyCacheScope(hash, proxyUrl);
  const digest = hash.digest("hex").slice(0, 16);
  return `games:${scope}:${digest}`;
}

export function resolveAccountCacheId(accountId: string | undefined, token: string): string {
  return accountId?.trim() || token;
}

export async function loadAccountScopedFromCache<T>(
  scope: string,
  accountId: string | undefined,
  token: string,
  providerStreamingBaseUrl?: string,
  proxyUrl?: string,
): Promise<Awaited<ReturnType<typeof cacheManager.loadFromCache<T>>>> {
  if (shouldBypassGamesCache(proxyUrl)) {
    return null;
  }

  const resolvedAccountId = resolveAccountCacheId(accountId, token);
  const primaryKey = accountScopedGamesCacheKey(scope, resolvedAccountId, providerStreamingBaseUrl, proxyUrl);
  const cached = await cacheManager.loadFromCache<T>(primaryKey);
  if (cached) {
    return cached;
  }

  if (resolvedAccountId !== token) {
    const legacyKey = legacyTokenScopedGamesCacheKey(scope, token, providerStreamingBaseUrl, proxyUrl);
    if (legacyKey !== primaryKey) {
      const legacy = await cacheManager.loadFromCache<T>(legacyKey);
      if (legacy) {
        void cacheManager.saveToCache(primaryKey, legacy.data);
        void cacheManager.invalidateCache(legacyKey);
        return legacy;
      }
    }
  }

  return null;
}

export function catalogBrowseCacheKey(input: {
  searchQuery?: string;
  sortId?: string;
  filterIds?: string[];
  fetchCount?: number;
  providerStreamingBaseUrl?: string;
  proxyUrl?: string;
}, accountId: string): string {
  const queryDigest = createHash("sha256")
    .update(input.searchQuery?.trim() ?? "")
    .update("\0")
    .update(input.sortId ?? "")
    .update("\0")
    .update((input.filterIds ?? []).join(","))
    .update("\0")
    .update(String(input.fetchCount ?? ""))
    .digest("hex")
    .slice(0, 12);
  return `${getAccountCatalogGamesCachePrefix(accountId, input.providerStreamingBaseUrl, input.proxyUrl)}:${queryDigest}`;
}

export function getAccountCatalogGamesCachePrefix(
  accountId: string,
  providerStreamingBaseUrl?: string,
  proxyUrl?: string,
): string {
  return accountScopedGamesCacheKey(CATALOG_GAMES_CACHE_SCOPE, accountId, providerStreamingBaseUrl, proxyUrl);
}

export function getAccountGamesCacheKeys(accountId: string, providerStreamingBaseUrl?: string, proxyUrl?: string): {
  main: string;
  featured: string;
  storePanels: string;
  library: string;
  catalogPrefix: string;
  public: string;
} {
  return {
    main: accountScopedGamesCacheKey("main", accountId, providerStreamingBaseUrl, proxyUrl),
    featured: accountScopedGamesCacheKey("featured", accountId, providerStreamingBaseUrl, proxyUrl),
    storePanels: accountScopedGamesCacheKey("store-panels", accountId, providerStreamingBaseUrl, proxyUrl),
    library: accountScopedGamesCacheKey(LIBRARY_GAMES_CACHE_SCOPE, accountId, providerStreamingBaseUrl, proxyUrl),
    catalogPrefix: getAccountCatalogGamesCachePrefix(accountId, providerStreamingBaseUrl, proxyUrl),
    public: publicGamesCacheKey(proxyUrl),
  };
}

export function getLegacyTokenScopedAccountGamesCacheKeys(token: string, providerStreamingBaseUrl?: string, proxyUrl?: string): {
  main: string;
  featured: string;
  storePanels: string;
  library: string;
  catalogPrefix: string;
} {
  return {
    main: legacyTokenScopedGamesCacheKey("main", token, providerStreamingBaseUrl, proxyUrl),
    featured: legacyTokenScopedGamesCacheKey("featured", token, providerStreamingBaseUrl, proxyUrl),
    storePanels: legacyTokenScopedGamesCacheKey("store-panels", token, providerStreamingBaseUrl, proxyUrl),
    library: legacyTokenScopedGamesCacheKey(LIBRARY_GAMES_CACHE_SCOPE, token, providerStreamingBaseUrl, proxyUrl),
    catalogPrefix: legacyTokenScopedGamesCacheKey(CATALOG_GAMES_CACHE_SCOPE, token, providerStreamingBaseUrl, proxyUrl),
  };
}

export interface AccountGameCacheInvalidationInput {
  userId: string;
  providerStreamingBaseUrl?: string;
  tokens?: Array<string | undefined>;
  proxyUrl?: string;
  logPrefix?: string;
}

export async function invalidateAccountGameCaches(input: AccountGameCacheInvalidationInput): Promise<void> {
  const cacheKeySets: Array<{ main: string; featured: string; storePanels: string; library: string; catalogPrefix: string }> = [
    getAccountGamesCacheKeys(input.userId, input.providerStreamingBaseUrl),
  ];
  const legacyTokens = [...new Set((input.tokens ?? []).filter((token): token is string => Boolean(token)))];
  cacheKeySets.push(
    ...legacyTokens.map((token) => getLegacyTokenScopedAccountGamesCacheKeys(token, input.providerStreamingBaseUrl)),
  );

  if (input.proxyUrl?.trim()) {
    try {
      cacheKeySets.push(getAccountGamesCacheKeys(input.userId, input.providerStreamingBaseUrl, input.proxyUrl));
      cacheKeySets.push(
        ...legacyTokens.map((token) => getLegacyTokenScopedAccountGamesCacheKeys(token, input.providerStreamingBaseUrl, input.proxyUrl)),
      );
    } catch (error) {
      console.warn(`${input.logPrefix ?? "[Games]"} Skipping proxy-scoped game cache invalidation:`, error);
    }
  }

  const invalidations = new Map<string, Promise<void>>();
  for (const keys of cacheKeySets) {
    invalidations.set(keys.main, cacheManager.invalidateCache(keys.main));
    invalidations.set(keys.featured, cacheManager.invalidateCache(keys.featured));
    invalidations.set(keys.storePanels, cacheManager.invalidateCache(keys.storePanels));
    invalidations.set(keys.library, cacheManager.invalidateCache(keys.library));
    invalidations.set(keys.catalogPrefix, cacheManager.invalidateCachesByPrefix(keys.catalogPrefix));
  }
  await Promise.allSettled(invalidations.values());
}

export async function fetchPublicGames(proxyUrl?: string): Promise<GameInfo[]> {
  if (shouldBypassGamesCache(proxyUrl)) {
    return fetchPublicGamesUncached(proxyUrl);
  }

  const cacheKey = publicGamesCacheKey(proxyUrl);
  const cached = await cacheManager.loadFromCache<GameInfo[]>(cacheKey);
  if (cached) {
    return cached.data;
  }

  const games = await fetchPublicGamesUncached(proxyUrl);
  await cacheManager.saveToCache(cacheKey, games);
  return games;
}
