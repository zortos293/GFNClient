export interface RegionsFetchRequest {
  token?: string;
}

export interface StreamRegion {
  name: string;
  url: string;
  pingMs?: number;
}

export interface PingResult {
  url: string;
  pingMs: number | null;
  error?: string;
}

export interface GamesFetchRequest {
  token?: string;
  providerStreamingBaseUrl?: string;
  /** Optional proxy used for GFN games catalog/list requests. */
  proxyUrl?: string;
  /** Stable account id used for on-disk cache scoping (avoids cache misses on token refresh). */
  userId?: string;
}

export interface DirectLaunchRequest {
  id: string;
  source: "cli";
  appId?: string;
  title?: string;
  receivedAt: number;
}

export interface CatalogBrowseRequest extends GamesFetchRequest {
  searchQuery?: string;
  sortId?: string;
  filterIds?: string[];
  fetchCount?: number;
}

export interface ResolveLaunchIdRequest {
  token?: string;
  providerStreamingBaseUrl?: string;
  proxyUrl?: string;
  appIdOrUuid: string;
}

export interface ResolveStoreUrlRequest {
  token?: string;
  providerStreamingBaseUrl?: string;
  proxyUrl?: string;
  appIdOrUuid: string;
  variantId?: string;
  store?: string;
}

export interface MarkGameOwnedRequest extends GamesFetchRequest {
  variantId: string;
}

export interface MarkGameOwnedResult {
  ok: true;
  variantId: string;
  libraryStatus: "MANUAL";
}

export interface SubscriptionFetchRequest {
  token?: string;
  providerStreamingBaseUrl?: string;
  userId: string;
}

export interface PersistentStorageResetRequest {
  /** Null or omitted keeps the current storage region, matching NVIDIA's storage reset flow. */
  storageRegion?: string | null;
}

export interface PersistentStorageResetResult {
  ok: true;
  storageRegion: string | null;
  message?: string;
}

export interface PersistentStorageLocation {
  code: string;
  name: string;
  isAvailable: boolean;
  isCurrent?: boolean;
  isRecommended?: boolean;
}

export interface PersistentStorageLocationsFetchRequest {
  serverRegionId?: string | null;
  currentRegionCode?: string | null;
  currentRegionName?: string | null;
  locale?: string;
}

export interface PersistentStorageLocationsResult {
  locations: PersistentStorageLocation[];
  currentRegionCode?: string;
  currentRegionName?: string;
}

export type GameAccountConnectionStatus = "not_connected" | "connected" | "expired" | "sync_error";

export interface GameAccountConnection {
  provider: string;
  label: string;
  sortOrder: number;
  iconUrl?: string;
  supportsLinking: boolean;
  supportsSync: boolean;
  isRequired: boolean;
  isConnected: boolean;
  status: GameAccountConnectionStatus;
  displayName?: string;
  userIdentifier?: string;
  expiresIn?: string;
  expiresAt?: number;
  syncState?: string;
  syncDate?: string;
  syncedGames: number;
}

export interface GameAccountConnectionsResult {
  accounts: GameAccountConnection[];
  fetchedAt: number;
}

export interface GameAccountOperationRequest {
  provider: string;
  proxyUrl?: string;
}

export interface GameAccountOperationResult extends GameAccountConnectionsResult {
  ok: true;
  account?: GameAccountConnection;
  message?: string;
}

export interface GameVariant {
  id: string;
  store: string;
  storeUrl?: string;
  supportedControls: string[];
  supportsInGameSettingsPersistence?: boolean;
  librarySelected?: boolean;
  inLibrary?: boolean;
  libraryStatus?: string;
  lastPlayedDate?: string;
  gfnStatus?: string;
}

export const OWNED_LIBRARY_STATUSES = ["MANUAL", "PLATFORM_SYNC", "IN_LIBRARY"] as const;

const GAME_STORE_ALIASES: Record<string, string> = {
  BATTLE_NET: "BATTLE_NET",
  BATTLENET: "BATTLE_NET",
  EA: "EA_APP",
  EGS: "EPIC_GAMES_STORE",
  EPIC: "EPIC_GAMES_STORE",
  GAIJIN_NET: "GAIJIN",
  GOG_COM: "GOG",
  MICROSOFT: "XBOX",
  MICROSOFT_STORE: "XBOX",
  ORIGIN: "EA_APP",
  UBISOFT: "UPLAY",
  UBISOFT_CONNECT: "UPLAY",
  XBOX_GAME_PASS: "XBOX",
};

export function normalizeGameStore(store: string): string {
  const key = store
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  return GAME_STORE_ALIASES[key] ?? key;
}

export function isOwnedLibraryStatus(status?: string): boolean {
  return typeof status === "string" && OWNED_LIBRARY_STATUSES.includes(status as (typeof OWNED_LIBRARY_STATUSES)[number]);
}

export function isOwnedVariant(variant: Pick<GameVariant, "libraryStatus">): boolean {
  return isOwnedLibraryStatus(variant.libraryStatus);
}

export interface GameInfo {

  id: string;
  uuid?: string;
  launchAppId?: string;
  title: string;
  shortName?: string;
  description?: string;
  longDescription?: string;
  developerName?: string;
  maxLocalPlayers?: number;
  maxOnlinePlayers?: number;
  featureLabels?: string[];
  genres?: string[];
  supportedControls?: string[];
  nvidiaTech?: string[];
  imageUrl?: string;
  heroImageUrl?: string;
  screenshotUrl?: string;
  screenshotUrls?: string[];
  imageUrlsByType?: Record<string, string[]>;
  playType?: string;
  membershipTierLabel?: string;
  catalogSkuStrings?: GameCatalogSkuStrings;
  publisherName?: string;
  contentRatings?: string[];
  playabilityState?: string;
  availableStores?: string[];
  searchText?: string;
  lastPlayed?: string;
  isInLibrary?: boolean;
  selectedVariantIndex: number;
  variants: GameVariant[];
}

export interface GameCatalogSkuStrings {
  SKU_BASED_TAG?: string[];
  SKU_BASED_PLAYABILITY_TEXT?: string;
  SKU_BASED_UNPLAYABLE_DIALOG_HEADER?: string;
  SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE?: string;
  SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE_ECOMM_RESTRICTED?: string;
}

export function isGameInLibrary(game: Pick<GameInfo, "variants">): boolean {
  return game.variants.some((variant) => isOwnedVariant(variant));
}

export function isEpicStore(store: string): boolean {
  const key = normalizeGameStore(store);
  return key === "EPIC_GAMES_STORE";
}

export interface CatalogFilterOption {
  id: string;
  rawId: string;
  label: string;
  groupId: string;
  groupLabel: string;
}

export interface CatalogFilterGroup {
  id: string;
  label: string;
  options: CatalogFilterOption[];
}

export interface CatalogSortOption {
  id: string;
  label: string;
  orderBy: string;
}

export interface GamePanelSection {
  id: string;
  title: string;
  games: GameInfo[];
}

export interface GamePanelResult {
  id: string;
  title: string;
  sections: GamePanelSection[];
}

export interface CatalogBrowseResult {
  games: GameInfo[];
  numberReturned: number;
  numberSupported: number;
  totalCount: number;
  hasNextPage: boolean;
  endCursor?: string;
  searchQuery: string;
  selectedSortId: string;
  selectedFilterIds: string[];
  filterGroups: CatalogFilterGroup[];
  sortOptions: CatalogSortOption[];
}
