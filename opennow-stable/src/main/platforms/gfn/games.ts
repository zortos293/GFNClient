/**
 * GFN games facade — public API for IPC/index/tests.
 *
 * Implementation lives in focused modules:
 * - gamesCache.ts — account/proxy cache keys + invalidation + public games cache
 * - gameAppMapper.ts — AppData mapping, metadata enrichment, launch/store resolution
 * - catalogBrowse.ts — catalog browse + main/featured/store panels
 * - libraryGames.ts — library pagination/fetch/cache + mark owned
 */

export {
  getAccountCatalogGamesCachePrefix,
  getAccountGamesCacheKeys,
  getLegacyTokenScopedAccountGamesCacheKeys,
  invalidateAccountGameCaches,
  fetchPublicGames,
  type AccountGameCacheInvalidationInput,
} from "./gamesCache";

export {
  resolveLaunchAppId,
  resolveStoreUrl,
} from "./gameAppMapper";

export {
  browseCatalog,
  browseCatalogUncached,
  peekCachedBrowseCatalog,
  fetchMainGames,
  fetchMainGamesUncached,
  fetchFeaturedGames,
  fetchStorePanels,
} from "./catalogBrowse";

export {
  peekCachedLibraryGames,
  fetchLibraryGamesFromCache,
  fetchLibraryGames,
  fetchLibraryGamesUncached,
  markGameOwned,
  type MarkGameOwnedInput,
} from "./libraryGames";

export { fetchPublicGamesUncached } from "./publicGames";
