import type {
  GameInfo,
  MarkGameOwnedResult,
} from "@shared/gfn";
import { cacheManager } from "../../services/cacheManager";
import { mergePublicGameVariants } from "./publicGames";
import { fetchAllAppsPages } from "./paginatedApps";
import { postLcarsMutation } from "./lcarsGraphql";
import {
  accountScopedGamesCacheKey,
  fetchPublicGames,
  invalidateAccountGameCaches,
  LIBRARY_GAMES_CACHE_SCOPE,
  loadAccountScopedFromCache,
  resolveAccountCacheId,
  shouldBypassGamesCache,
} from "./gamesCache";
import {
  appToGame,
  type AppData,
  type AppsPage,
  DEFAULT_LOCALE,
  dedupeGames,
  enrichGamesWithMetadata,
  GFN_FEATURE_FIELDS,
  type GraphQlResponse,
  getVpcId,
  postGraphQl,
} from "./gameAppMapper";
import { fetchPanels, flattenPanels } from "./catalogBrowse";

const LIBRARY_FETCH_COUNT = 200;
const MAX_LIBRARY_PAGES = 25;
const DEFAULT_LIBRARY_SORT = "variants.gfn.library.lastPlayedDate:DESC,computedValues.libraryAddedDate:DESC,sortName:ASC";
const LIBRARY_APPS_FILTER = {
  variants: {
    gfn: {
      library: {
        status: {
          notEquals: "NOT_OWNED",
        },
      },
    },
  },
} satisfies Record<string, unknown>;

interface AddOwnedVariantResponse {
  data?: {
    addOwnedVariant?: {
      app?: {
        id?: string;
      };
    };
  };
  errors?: Array<{ message: string }>;
}

export interface MarkGameOwnedInput {
  token: string;
  userId: string;
  variantId: string;
  providerStreamingBaseUrl?: string;
  proxyUrl?: string;
  tokens?: Array<string | undefined>;
}

export async function peekCachedLibraryGames(
  token: string,
  providerStreamingBaseUrl?: string,
  accountId?: string,
  proxyUrl?: string,
): Promise<GameInfo[] | null> {
  const cached = await loadAccountScopedFromCache<GameInfo[]>(LIBRARY_GAMES_CACHE_SCOPE, accountId, token, providerStreamingBaseUrl, proxyUrl);
  return cached?.data ?? null;
}

export async function fetchLibraryGamesFromCache(
  token: string,
  providerStreamingBaseUrl?: string,
  accountId?: string,
  proxyUrl?: string,
): Promise<GameInfo[] | null> {
  const cached = await peekCachedLibraryGames(token, providerStreamingBaseUrl, accountId, proxyUrl);
  if (!cached) {
    return null;
  }
  return mergePublicGameVariants(cached, await fetchPublicGames(proxyUrl));
}

export async function fetchLibraryGames(
  token: string,
  providerStreamingBaseUrl?: string,
  accountId?: string,
  proxyUrl?: string,
): Promise<GameInfo[]> {
  const cached = await loadAccountScopedFromCache<GameInfo[]>(LIBRARY_GAMES_CACHE_SCOPE, accountId, token, providerStreamingBaseUrl, proxyUrl);
  if (cached) {
    return mergePublicGameVariants(cached.data, await fetchPublicGames(proxyUrl));
  }

  const games = await fetchLibraryGamesUncached(token, providerStreamingBaseUrl, proxyUrl);
  if (!shouldBypassGamesCache(proxyUrl)) {
    const cacheKey = accountScopedGamesCacheKey(LIBRARY_GAMES_CACHE_SCOPE, resolveAccountCacheId(accountId, token), providerStreamingBaseUrl, proxyUrl);
    await cacheManager.saveToCache(cacheKey, games);
  }
  return games;
}

export async function fetchLibraryGamesUncached(
  token: string,
  providerStreamingBaseUrl?: string,
  proxyUrl?: string,
): Promise<GameInfo[]> {
  const vpcId = await getVpcId(token, providerStreamingBaseUrl, proxyUrl);
  try {
    const apps = await fetchPaginatedLibraryApps(token, vpcId, proxyUrl);
    const games = dedupeGames(apps.map(appToGame));
    return mergePublicGameVariants(await enrichGamesWithMetadata(token, vpcId, games, proxyUrl), await fetchPublicGames(proxyUrl));
  } catch (error) {
    console.warn("Paginated library query failed, falling back to library panel:", error);
  }

  let payload: GraphQlResponse;

  try {
    payload = await fetchPanels(token, ["LIBRARY"], vpcId, { withLibraryTime: true }, proxyUrl);
  } catch {
    payload = await fetchPanels(token, ["LIBRARY"], vpcId, undefined, proxyUrl);
  }

  const games = flattenPanels(payload);
  return mergePublicGameVariants(await enrichGamesWithMetadata(token, vpcId, games, proxyUrl), await fetchPublicGames(proxyUrl));
}

async function fetchPaginatedLibraryApps(token: string, vpcId: string, proxyUrl?: string): Promise<AppData[]> {
  const query = `query GetLibraryApps(
    $vpcId: String!,
    $locale: String!,
    $sortString: String!,
    $fetchCount: Int!,
    $cursor: String!,
    $filters: AppFilterFields!
  ) {
    apps(
      vpcId: $vpcId,
      language: $locale,
      orderBy: $sortString,
      first: $fetchCount,
      after: $cursor,
      filters: $filters
    ) {
      numberReturned
      numberSupported
      pageInfo { hasNextPage endCursor totalCount }
      items {
        id
        title
        images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }
        variants {
          id
          appStore
          storeUrl
          supportedControls
          gfn {
            status
            features {
${GFN_FEATURE_FIELDS}
            }
            library { status selected lastPlayedDate }
          }
        }
        gfn {
          playabilityState
          minimumMembershipTierLabel
          catalogSkuStrings {
            SKU_BASED_TAG
            SKU_BASED_PLAYABILITY_TEXT
            SKU_BASED_UNPLAYABLE_DIALOG_HEADER
            SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE
            SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE_ECOMM_RESTRICTED
          }
        }
        itemMetadata { campaignIds }
      }
    }
  }`;

  const result = await fetchAllAppsPages<AppData>(
    (cursor) => postGraphQl<AppsPage>(
      query,
      {
        vpcId,
        locale: DEFAULT_LOCALE,
        sortString: DEFAULT_LIBRARY_SORT,
        fetchCount: LIBRARY_FETCH_COUNT,
        cursor,
        filters: LIBRARY_APPS_FILTER,
      },
      token,
      proxyUrl,
    ),
    { maxPages: MAX_LIBRARY_PAGES },
  );
  return result.items;
}

export async function markGameOwned(input: MarkGameOwnedInput): Promise<MarkGameOwnedResult> {
  const variantId = input.variantId.trim();
  if (!variantId) {
    throw new Error("Cannot mark game as owned without a variant ID");
  }

  const payload = await postLcarsMutation<AddOwnedVariantResponse>(
    "AddOwnedVariant",
    {
      cmsId: variantId,
      locale: DEFAULT_LOCALE,
    },
    input.token,
    input.proxyUrl,
  );

  if (!payload.data?.addOwnedVariant?.app?.id) {
    throw new Error("GFN library mutation failed: missing AddOwnedVariant response");
  }

  await invalidateAccountGameCaches({
    userId: input.userId,
    providerStreamingBaseUrl: input.providerStreamingBaseUrl,
    tokens: [input.token, ...(input.tokens ?? [])],
    proxyUrl: input.proxyUrl,
  });

  return {
    ok: true,
    variantId,
    libraryStatus: "MANUAL",
  };
}
