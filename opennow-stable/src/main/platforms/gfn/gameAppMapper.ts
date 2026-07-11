import type {
  GameCatalogSkuStrings,
  GameInfo,
  GameVariant,
} from "@shared/gfn";
import { isOwnedLibraryStatus, normalizeGameStore } from "@shared/gfn";
import {
  buildGfnGraphQlHeaders,
  buildGfnLcarsHeaders,
} from "./clientHeaders";
import { supportsInGameSettingsPersistence } from "./gameFeatures";
import { fetchLcarsGraphQl } from "./lcarsGraphql";
import { fetchWithOptionalProxy } from "./proxyFetch";
import type { AppsPageResponse } from "./paginatedApps";

export const GRAPHQL_URL = "https://games.geforce.com/graphql";
export const DEFAULT_LOCALE = "en_US";
export const DEFAULT_CLOUDMATCH_BASE_URL = "https://prod.cloudmatchbeta.nvidiagrid.net/";
export const GFN_FEATURE_FIELDS = `
              __typename
              ... on GfnSubscriptionFeatureValue {
                key
                value
              }
              ... on GfnSubscriptionFeatureValueList {
                key
                values
              }
`;

export interface AppData {
  id: string;
  title: string;
  shortName?: string;
  description?: string;
  longDescription?: string;
  developerName?: string;
  features?: unknown[];
  gameFeatures?: unknown[];
  appFeatures?: unknown[];
  genres?: unknown[];
  tags?: unknown[];
  supportedControls?: unknown[];
  nvidiaTech?: unknown[];
  maxLocalPlayers?: number;
  maxOnlinePlayers?: number;
  images?: Record<string, string | string[] | undefined>;
  publisherName?: string;
  contentRatings?: unknown[];
  variants?: Array<{
    id: string;
    appStore: string;
    storeUrl?: string;
    supportedControls?: string[];
    gfn?: {
      status?: string;
      features?: unknown;
      library?: {
        status?: string;
        selected?: boolean;
        lastPlayedDate?: string;
      };
    };
  }>;
  gfn?: {
    playType?: string;
    playabilityState?: string;
    minimumMembershipTierLabel?: string;
    catalogSkuStrings?: GameCatalogSkuStrings;
  };
  itemMetadata?: {
    campaignIds?: string[];
  };
}

export interface GraphQlResponse {
  data?: {
    panels: Array<{
      id?: string;
      name: string;
      sections: Array<{
        id?: string;
        title?: string;
        items: Array<{
          __typename: string;
          app?: AppData;
        }>;
      }>;
    }>;
  };
  errors?: Array<{ message: string }>;
}

export interface AppMetaDataResponse {
  data?: {
    apps: {
      items: AppData[];
    };
  };
  errors?: Array<{ message: string }>;
}

export type AppsPage = AppsPageResponse<AppData>;

interface ServerInfoResponse {
  requestStatus?: {
    serverId?: string;
  };
}

interface AppResolution {
  numericAppId?: string;
  preferredVariantId?: string;
  selectedVariantIndex: number;
  lastPlayed?: string;
  isInLibrary: boolean;
}

const LANDSCAPE_IMAGE_KEYS = ["MARQUEE_HERO_IMAGE", "HERO_IMAGE", "TV_BANNER", "FEATURE_IMAGE", "KEY_IMAGE", "KEY_ART"] as const;
const POSTER_IMAGE_KEYS = ["GAME_BOX_ART", "KEY_IMAGE", "KEY_ART"] as const;

function optimizeImage(url: string, width = 272): string {
  if (url.includes("img.nvidiagrid.net")) {
    return `${url};f=webp;w=${width}`;
  }
  return url;
}

function normalizeImageValues(value: string | string[] | undefined, width: number): string[] {
  const values = Array.isArray(value) ? value : value ? [value] : [];
  return [...new Set(values.map((url) => url.trim()).filter(Boolean).map((url) => optimizeImage(url, width)))];
}

function getFirstImage(images: AppData["images"], keys: readonly string[], width: number): string | undefined {
  if (!images) return undefined;
  for (const key of keys) {
    const value = normalizeImageValues(images[key], width)[0];
    if (value) return value;
  }
  return undefined;
}

function getImageUrlsByType(images: AppData["images"]): Record<string, string[]> | undefined {
  if (!images) return undefined;
  const entries = Object.entries(images)
    .map(([key, value]) => [key, normalizeImageValues(value, 1200)] as const)
    .filter(([, urls]) => urls.length > 0);
  return entries.length > 0 ? Object.fromEntries(entries) : undefined;
}

export function isNumericId(value: string | undefined): value is string {
  if (!value) {
    return false;
  }
  return /^\d+$/.test(value);
}

export async function postGraphQl<T>(
  query: string,
  variables: Record<string, unknown>,
  token?: string,
  proxyUrl?: string,
): Promise<T> {
  const response = await fetchWithOptionalProxy(GRAPHQL_URL, {
    method: "POST",
    headers: buildGfnGraphQlHeaders(token),
    body: JSON.stringify({ query, variables }),
  }, proxyUrl);

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`GFN GraphQL failed (${response.status}): ${text.slice(0, 400)}`);
  }

  return (await response.json()) as T;
}

export async function getVpcId(token: string, providerStreamingBaseUrl?: string, proxyUrl?: string): Promise<string> {
  let validatedBaseUrl: URL;
  try {
    const candidate = new URL(providerStreamingBaseUrl?.trim() || DEFAULT_CLOUDMATCH_BASE_URL);
    const hostname = candidate.hostname.toLowerCase();
    if (
      candidate.protocol !== "https:" ||
      (
        hostname !== "prod.cloudmatchbeta.nvidiagrid.net" &&
        hostname !== "img.nvidiagrid.net" &&
        !hostname.endsWith(".geforcenow.nvidiagrid.net")
      )
    ) {
      validatedBaseUrl = new URL(DEFAULT_CLOUDMATCH_BASE_URL);
    } else {
      validatedBaseUrl = candidate;
    }
  } catch {
    validatedBaseUrl = new URL(DEFAULT_CLOUDMATCH_BASE_URL);
  }

  const serverInfoUrl = new URL("v2/serverInfo", validatedBaseUrl);

  const response = await fetchWithOptionalProxy(serverInfoUrl.toString(), {
    headers: buildGfnLcarsHeaders({
      token,
      clientType: "NATIVE",
      clientStreamer: "NVIDIA-CLASSIC",
      includeUserAgent: true,
      includeEmptyTokenAuthorization: true,
    }),
  }, proxyUrl);

  if (!response.ok) {
    return "GFN-PC";
  }

  const payload = (await response.json()) as ServerInfoResponse;
  return payload.requestStatus?.serverId ?? "GFN-PC";
}

function parseFeatureLabel(value: unknown): string | null {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
  if (value && typeof value === "object") {
    const candidate = value as Record<string, unknown>;
    const keys = ["name", "label", "title", "displayName"];
    for (const key of keys) {
      const raw = candidate[key];
      if (typeof raw === "string") {
        const trimmed = raw.trim();
        if (trimmed.length > 0) {
          return trimmed;
        }
      }
    }
  }
  return null;
}

function extractFeatureLabels(app: AppData): string[] {
  const buckets: unknown[] = [
    app.features,
    app.gameFeatures,
    app.appFeatures,
    app.genres,
    app.tags,
    app.gfn?.catalogSkuStrings?.SKU_BASED_TAG,
  ];

  const labels: string[] = [];
  for (const bucket of buckets) {
    if (!Array.isArray(bucket)) {
      continue;
    }
    for (const entry of bucket) {
      const label = parseFeatureLabel(entry);
      if (label) {
        labels.push(label);
      }
    }
  }

  return [...new Set(labels)];
}

function extractGenres(app: AppData): string[] {
  if (!Array.isArray(app.genres)) {
    return [];
  }

  const genres: string[] = [];
  for (const entry of app.genres) {
    const genre = parseFeatureLabel(entry);
    if (genre) {
      genres.push(genre);
    }
  }

  return [...new Set(genres)];
}

function extractContentRatings(app: AppData): string[] {
  if (!Array.isArray(app.contentRatings)) {
    return [];
  }

  const labels: string[] = [];
  for (const entry of app.contentRatings) {
    const label = parseFeatureLabel(entry);
    if (label) {
      labels.push(label);
    }
  }

  return [...new Set(labels)];
}

function extractStringValues(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value
    .map((entry) => typeof entry === "string" ? entry.trim() : parseFeatureLabel(entry))
    .filter((entry): entry is string => typeof entry === "string" && entry.length > 0))];
}

function buildSearchText(title: string, variants: GameVariant[], genres: string[], featureLabels: string[], publisherName?: string, developerName?: string): string {
  const stores = variants.map((variant) => variant.store);
  return [title, publisherName, developerName, ...stores, ...genres, ...featureLabels]
    .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    .join(" ")
    .toLowerCase();
}

export function resolveAppData(app: AppData): AppResolution {
  const variants = app.variants ?? [];
  const selectedVariantIndex = variants.findIndex((variant) => variant.gfn?.library?.selected === true);
  const preferredVariant = selectedVariantIndex >= 0 ? variants[selectedVariantIndex] : undefined;
  const numericVariants = variants.filter((variant) => isNumericId(variant.id));
  const preferredNumericVariant = preferredVariant && isNumericId(preferredVariant.id) ? preferredVariant.id : undefined;
  const fallbackNumericVariant = numericVariants[0]?.id;
  const numericAppId = preferredNumericVariant ?? fallbackNumericVariant ?? (isNumericId(app.id) ? app.id : undefined);
  const preferredVariantId = preferredVariant?.id ?? numericAppId ?? variants[0]?.id ?? app.id;
  const lastPlayed = variants
    .map((variant) => variant.gfn?.library?.lastPlayedDate)
    .find((value): value is string => typeof value === "string" && value.length > 0);
  const isInLibrary = variants.some((variant) => isOwnedLibraryStatus(variant.gfn?.library?.status));

  return {
    numericAppId,
    preferredVariantId,
    selectedVariantIndex: selectedVariantIndex >= 0 ? selectedVariantIndex : Math.max(0, variants.findIndex((variant) => variant.id === preferredVariantId)),
    lastPlayed,
    isInLibrary,
  };
}

export function appToVariants(app: AppData): GameVariant[] {
  return app.variants?.map((variant) => {
    const supportsPersistence = supportsInGameSettingsPersistence(variant);
    return {
      id: variant.id,
      store: variant.appStore,
      storeUrl: variant.storeUrl,
      supportedControls: variant.supportedControls ?? [],
      ...(supportsPersistence ? { supportsInGameSettingsPersistence: true } : {}),
      librarySelected: variant.gfn?.library?.selected,
      inLibrary: variant.gfn?.library?.selected === true,
      libraryStatus: variant.gfn?.library?.status,
      lastPlayedDate: variant.gfn?.library?.lastPlayedDate,
      gfnStatus: variant.gfn?.status,
    };
  }) ?? [];
}

export function appToGame(app: AppData): GameInfo {
  const variants = appToVariants(app);
  const resolution = resolveAppData(app);
  const heroImageUrl = getFirstImage(app.images, LANDSCAPE_IMAGE_KEYS, 1200);
  const posterImageUrl = getFirstImage(app.images, POSTER_IMAGE_KEYS, 900);
  const imageUrl = heroImageUrl ?? posterImageUrl;
  const screenshotUrls = normalizeImageValues(app.images?.SCREENSHOTS, 720);
  const genres = extractGenres(app);
  const featureLabels = extractFeatureLabels(app);
  const supportedControls = extractStringValues(app.supportedControls);
  const nvidiaTech = extractStringValues(app.nvidiaTech);

  return {
    id: app.id,
    uuid: app.id,
    launchAppId: resolution.numericAppId,
    title: app.title,
    shortName: app.shortName,
    description: app.description,
    longDescription: app.longDescription,
    developerName: app.developerName,
    maxLocalPlayers: app.maxLocalPlayers,
    maxOnlinePlayers: app.maxOnlinePlayers,
    featureLabels,
    genres,
    supportedControls: supportedControls.length > 0 ? supportedControls : undefined,
    nvidiaTech: nvidiaTech.length > 0 ? nvidiaTech : undefined,
    imageUrl,
    heroImageUrl,
    screenshotUrl: screenshotUrls[0],
    screenshotUrls: screenshotUrls.length > 0 ? screenshotUrls : undefined,
    imageUrlsByType: getImageUrlsByType(app.images),
    playType: app.gfn?.playType,
    membershipTierLabel: app.gfn?.minimumMembershipTierLabel,
    catalogSkuStrings: app.gfn?.catalogSkuStrings,
    publisherName: app.publisherName,
    contentRatings: extractContentRatings(app),
    playabilityState: app.gfn?.playabilityState,
    availableStores: [...new Set(variants.map((variant) => variant.store).filter(Boolean))],
    searchText: buildSearchText(app.title, variants, genres, featureLabels, app.publisherName, app.developerName),
    lastPlayed: resolution.lastPlayed,
    isInLibrary: resolution.isInLibrary,
    selectedVariantIndex: Math.max(0, Math.min(resolution.selectedVariantIndex, Math.max(variants.length - 1, 0))),
    variants,
  };
}

function mergeAppMetaIntoGame(game: GameInfo, app: AppData): GameInfo {
  const merged = appToGame(app);
  const selectedVariantId = game.variants[game.selectedVariantIndex]?.id;
  const variants = merged.variants.map((variant) => {
    const existing = game.variants.find((candidate) => candidate.id === variant.id);
    return {
      ...variant,
      librarySelected: variant.librarySelected ?? existing?.librarySelected,
      inLibrary: variant.inLibrary ?? existing?.inLibrary,
      libraryStatus: variant.libraryStatus ?? existing?.libraryStatus,
      lastPlayedDate: variant.lastPlayedDate ?? existing?.lastPlayedDate,
    };
  });
  const selectedVariantIndex = selectedVariantId
    ? variants.findIndex((variant) => variant.id === selectedVariantId)
    : -1;

  return {
    ...game,
    ...merged,
    id: game.id,
    isInLibrary: merged.isInLibrary || game.isInLibrary,
    lastPlayed: merged.lastPlayed ?? game.lastPlayed,
    variants,
    selectedVariantIndex: selectedVariantIndex >= 0 ? selectedVariantIndex : merged.selectedVariantIndex,
  };
}

export function dedupeGames(games: GameInfo[]): GameInfo[] {
  const byId = new Map<string, GameInfo>();

  for (const game of games) {
    const existing = byId.get(game.id);
    if (!existing) {
      byId.set(game.id, game);
      continue;
    }

    const mergedVariants = new Map<string, GameVariant>();
    for (const variant of [...existing.variants, ...game.variants]) {
      mergedVariants.set(variant.id, variant);
    }

    const merged: GameInfo = {
      ...existing,
      ...game,
      id: existing.id,
      uuid: existing.uuid ?? game.uuid,
      launchAppId: existing.launchAppId ?? game.launchAppId,
      title: existing.title || game.title,
      shortName: existing.shortName ?? game.shortName,
      description: existing.description ?? game.description,
      longDescription: existing.longDescription ?? game.longDescription,
      developerName: existing.developerName ?? game.developerName,
      maxLocalPlayers: existing.maxLocalPlayers ?? game.maxLocalPlayers,
      maxOnlinePlayers: existing.maxOnlinePlayers ?? game.maxOnlinePlayers,
      imageUrl: existing.imageUrl ?? game.imageUrl,
      heroImageUrl: existing.heroImageUrl ?? game.heroImageUrl,
      screenshotUrl: existing.screenshotUrl ?? game.screenshotUrl,
      screenshotUrls: [...new Set([...(existing.screenshotUrls ?? []), ...(game.screenshotUrls ?? [])])],
      imageUrlsByType: {
        ...(game.imageUrlsByType ?? {}),
        ...(existing.imageUrlsByType ?? {}),
      },
      playType: existing.playType ?? game.playType,
      membershipTierLabel: existing.membershipTierLabel ?? game.membershipTierLabel,
      catalogSkuStrings: existing.catalogSkuStrings ?? game.catalogSkuStrings,
      publisherName: existing.publisherName ?? game.publisherName,
      playabilityState: existing.playabilityState ?? game.playabilityState,
      lastPlayed: existing.lastPlayed ?? game.lastPlayed,
      isInLibrary: existing.isInLibrary || game.isInLibrary,
      variants: [...mergedVariants.values()],
      genres: [...new Set([...(existing.genres ?? []), ...(game.genres ?? [])])],
      featureLabels: [...new Set([...(existing.featureLabels ?? []), ...(game.featureLabels ?? [])])],
      supportedControls: [...new Set([...(existing.supportedControls ?? []), ...(game.supportedControls ?? [])])],
      nvidiaTech: [...new Set([...(existing.nvidiaTech ?? []), ...(game.nvidiaTech ?? [])])],
      contentRatings: [...new Set([...(existing.contentRatings ?? []), ...(game.contentRatings ?? [])])],
      availableStores: [...new Set([...(existing.availableStores ?? []), ...(game.availableStores ?? [])])],
      searchText: [existing.searchText, game.searchText].filter(Boolean).join(" ").trim() || undefined,
      selectedVariantIndex: Math.max(0, existing.variants[existing.selectedVariantIndex]
        ? [...mergedVariants.values()].findIndex((variant) => variant.id === existing.variants[existing.selectedVariantIndex]?.id)
        : game.selectedVariantIndex),
    };

    byId.set(game.id, merged);
  }

  return [...byId.values()];
}

export async function fetchAppMetaData(
  token: string,
  appIds: string[],
  vpcId: string,
  proxyUrl?: string,
): Promise<AppMetaDataResponse> {
  const normalizedIds = [...new Set(appIds.map((id) => id.trim()).filter((id) => id.length > 0))];
  if (normalizedIds.length === 0) {
    return { data: { apps: { items: [] } } };
  }

  return await fetchLcarsGraphQl<AppMetaDataResponse>(
    "AppDataForAppId",
    {
      vpcId,
      locale: DEFAULT_LOCALE,
      appIds: normalizedIds,
    },
    token,
    proxyUrl,
    { context: "App metadata failed" },
  );
}

export async function enrichGamesWithMetadata(token: string, vpcId: string, games: GameInfo[], proxyUrl?: string): Promise<GameInfo[]> {
  const uuids = [...new Set(games.map((game) => game.uuid).filter((uuid): uuid is string => !!uuid))];

  if (uuids.length === 0) {
    return games;
  }

  const chunkSize = 40;
  const appById = new Map<string, AppData>();

  for (let index = 0; index < uuids.length; index += chunkSize) {
    const chunk = uuids.slice(index, index + chunkSize);
    const payload = await fetchAppMetaData(token, chunk, vpcId, proxyUrl);
    if (payload.errors?.length) {
      throw new Error(payload.errors.map((error) => error.message).join(", "));
    }

    for (const app of payload.data?.apps.items ?? []) {
      appById.set(app.id, app);
    }
  }

  return dedupeGames(
    games.map((game) => {
      const metadata = game.uuid ? appById.get(game.uuid) : undefined;
      return metadata ? mergeAppMetaIntoGame(game, metadata) : game;
    }),
  );
}

export async function resolveLaunchAppId(
  token: string,
  appIdOrUuid: string,
  providerStreamingBaseUrl?: string,
  proxyUrl?: string,
): Promise<string | null> {
  if (isNumericId(appIdOrUuid)) {
    return appIdOrUuid;
  }

  const vpcId = await getVpcId(token, providerStreamingBaseUrl, proxyUrl);
  const payload = await fetchAppMetaData(token, [appIdOrUuid], vpcId, proxyUrl);

  if (payload.errors?.length) {
    throw new Error(payload.errors.map((error) => error.message).join(", "));
  }

  const app = payload.data?.apps.items?.[0];
  if (!app) {
    return null;
  }

  return resolveAppData(app).numericAppId ?? null;
}

export async function resolveStoreUrl(
  token: string,
  appIdOrUuid: string,
  providerStreamingBaseUrl?: string,
  options: { variantId?: string; store?: string; proxyUrl?: string } = {},
): Promise<string | null> {
  const vpcId = await getVpcId(token, providerStreamingBaseUrl, options.proxyUrl);
  const payload = await fetchAppMetaData(token, [appIdOrUuid], vpcId, options.proxyUrl);

  if (payload.errors?.length) {
    throw new Error(payload.errors.map((error) => error.message).join(", "));
  }

  const app = payload.data?.apps.items?.[0];
  const variants = app?.variants ?? [];
  const selectedVariant = options.variantId
    ? variants.find((variant) => variant.id === options.variantId)
    : undefined;
  if (selectedVariant?.storeUrl) return selectedVariant.storeUrl;

  const storeKey = options.store ? normalizeGameStore(options.store) : undefined;
  const matchingStoreVariant = storeKey
    ? variants.find((variant) => normalizeGameStore(variant.appStore) === storeKey && variant.storeUrl)
    : undefined;
  if (matchingStoreVariant?.storeUrl) return matchingStoreVariant.storeUrl;

  return variants.find((variant) => variant.storeUrl)?.storeUrl ?? null;
}
