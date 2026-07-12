import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";

import type {
  AuthSession,
  CatalogBrowseResult,
  CatalogFilterGroup,
  CatalogSortOption,
  GameInfo,
  GamePanelResult,
  StreamRegion,
  SubscriptionInfo,
} from "@shared/gfn";

import { loadCatalogPreferences, saveCatalogPreferences, VARIANT_SELECTION_LOCALSTORAGE_KEY } from "../lib/catalogPreferences";
import {
  clearCatalogSnapshot,
  loadCatalogSnapshot,
  saveCatalogSnapshot,
} from "../lib/catalogSnapshot";
import {
  areStringArraysEqual,
  defaultVariantId,
  getSelectedVariant,
  mergeVariantSelections,
} from "../lib/gameCatalog";
import {
  flattenStorePanelGames,
  getLibrarySelectedVariantId,
  markGameOwnedInList,
  markGameOwnedInPanels,
  upsertMarkedOwnedLibraryGame,
} from "../lib/gameOwnershipMutators";
import {
  buildProxyAwareCatalogQueryKey,
  getSessionProxyUiScope,
  hasSessionProxyCredentials,
} from "../lib/sessionProxy";

type TranslateFunction = typeof import("../i18n").t;

export type CatalogClearMode = "logout" | "no-session";

export interface ClearSessionCatalogOptions {
  clearFeatured?: boolean;
}

export interface UseCatalogDataInput {
  authSession: AuthSession | null;
  activeSessionProxyUrl: string | undefined;
  effectiveStreamingBaseUrl: string;
  currentPage: "home" | "library" | "settings";
  effectiveControllerMode: boolean;
  isInitializing: boolean;
  t: TranslateFunction;
}

export interface CatalogData {
  games: GameInfo[];
  featuredGames: GameInfo[];
  storePanels: GamePanelResult[];
  libraryGames: GameInfo[];
  searchQuery: string;
  setSearchQuery: Dispatch<SetStateAction<string>>;
  selectedGameId: string;
  setSelectedGameId: Dispatch<SetStateAction<string>>;
  variantByGameId: Record<string, string>;
  setVariantByGameId: Dispatch<SetStateAction<Record<string, string>>>;
  isLoadingCatalog: boolean;
  isLoadingLibrary: boolean;
  isLoadingStorePanels: boolean;
  catalogFilterGroups: CatalogFilterGroup[];
  catalogSortOptions: CatalogSortOption[];
  catalogSelectedSortId: string;
  setCatalogSelectedSortId: Dispatch<SetStateAction<string>>;
  catalogSelectedFilterIds: string[];
  setCatalogSelectedFilterIds: Dispatch<SetStateAction<string[]>>;
  catalogTotalCount: number;
  catalogSupportedCount: number;
  catalogFilterKey: string;
  markOwnedInFlightByVariantId: Record<string, boolean>;
  catalogActionNotice: { tone: "success" | "warn"; text: string } | null;
  setCatalogActionNotice: Dispatch<SetStateAction<{ tone: "success" | "warn"; text: string } | null>>;
  regions: StreamRegion[];
  setRegions: Dispatch<SetStateAction<StreamRegion[]>>;
  subscriptionInfo: SubscriptionInfo | null;
  setSubscriptionInfo: Dispatch<SetStateAction<SubscriptionInfo | null>>;
  storePanelGames: GameInfo[];
  allKnownGames: GameInfo[];
  resetStorePanels: () => void;
  applyVariantSelections: (catalog: GameInfo[]) => void;
  hydrateCatalogSnapshot: (session: AuthSession, proxyUrl?: string) => string | null;
  loadSessionRuntimeData: (
    session: AuthSession,
    options?: { background?: boolean; proxyUrl?: string },
  ) => Promise<void>;
  clearSessionCatalog: (mode: CatalogClearMode, options?: ClearSessionCatalogOptions) => void;
  loadGames: (targetSource: "main" | "library", options?: { background?: boolean }) => Promise<void>;
  loadStorePanels: (options?: { force?: boolean; background?: boolean }) => Promise<void>;
  handleMarkGameOwned: (game: GameInfo, selectedVariantId?: string) => Promise<void>;
  handleSelectGameVariant: (gameId: string, variantId: string) => void;
  handleToggleCatalogFilter: (filterId: string) => void;
  loadSubscriptionInfo: (session: AuthSession) => Promise<void>;
}

export function useCatalogData({
  authSession,
  activeSessionProxyUrl,
  effectiveStreamingBaseUrl,
  currentPage,
  effectiveControllerMode,
  isInitializing,
  t,
}: UseCatalogDataInput): CatalogData {
  const [games, setGames] = useState<GameInfo[]>([]);
  const [featuredGames, setFeaturedGames] = useState<GameInfo[]>([]);
  const [storePanels, setStorePanels] = useState<GamePanelResult[]>([]);
  const [libraryGames, setLibraryGames] = useState<GameInfo[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedGameId, setSelectedGameId] = useState("");
  const [variantByGameId, setVariantByGameId] = useState<Record<string, string>>({});
  const [isLoadingCatalog, setIsLoadingCatalog] = useState(false);
  const [isLoadingLibrary, setIsLoadingLibrary] = useState(false);
  const [isLoadingStorePanels, setIsLoadingStorePanels] = useState(false);
  const [catalogFilterGroups, setCatalogFilterGroups] = useState<CatalogFilterGroup[]>([]);
  const [catalogSortOptions, setCatalogSortOptions] = useState<CatalogSortOption[]>([]);
  const [catalogSelectedSortId, setCatalogSelectedSortId] = useState(() => loadCatalogPreferences().sortId);
  const [catalogSelectedFilterIds, setCatalogSelectedFilterIds] = useState<string[]>(() => loadCatalogPreferences().filterIds);
  const [catalogTotalCount, setCatalogTotalCount] = useState(0);
  const [catalogSupportedCount, setCatalogSupportedCount] = useState(0);
  const catalogFilterKey = useMemo(() => catalogSelectedFilterIds.join("|"), [catalogSelectedFilterIds]);
  const [markOwnedInFlightByVariantId, setMarkOwnedInFlightByVariantId] = useState<Record<string, boolean>>({});
  const [catalogActionNotice, setCatalogActionNotice] = useState<{
    tone: "success" | "warn";
    text: string;
  } | null>(null);
  const [regions, setRegions] = useState<StreamRegion[]>([]);
  const [subscriptionInfo, setSubscriptionInfo] = useState<SubscriptionInfo | null>(null);

  const storePanelsLoadedContextRef = useRef("");
  const storePanelsLoadIdRef = useRef(0);
  const runtimeDataLoadIdRef = useRef(0);
  const lastCatalogQueryRef = useRef<string | null>(null);
  const lastCatalogProxyUrlRef = useRef<string | undefined>(undefined);

  const resetStorePanels = useCallback((): void => {
    storePanelsLoadIdRef.current += 1;
    storePanelsLoadedContextRef.current = "";
    setStorePanels([]);
    setIsLoadingStorePanels(false);
  }, []);

  const applyVariantSelections = useCallback((catalog: GameInfo[]): void => {
    setVariantByGameId((prev) => mergeVariantSelections(prev, catalog));
  }, []);

  const applyCatalogBrowseResult = useCallback((catalogResult: CatalogBrowseResult): void => {
    setGames(catalogResult.games);
    setCatalogFilterGroups(catalogResult.filterGroups);
    setCatalogSortOptions(catalogResult.sortOptions);
    setCatalogSelectedSortId((previous) => previous === catalogResult.selectedSortId ? previous : catalogResult.selectedSortId);
    setCatalogSelectedFilterIds((previous) => areStringArraysEqual(previous, catalogResult.selectedFilterIds) ? previous : catalogResult.selectedFilterIds);
    setCatalogTotalCount(catalogResult.totalCount);
    setCatalogSupportedCount(catalogResult.numberSupported);
    setSelectedGameId((previous) => catalogResult.games.some((game) => game.id === previous) ? previous : (catalogResult.games[0]?.id ?? ""));
    applyVariantSelections(catalogResult.games);
  }, [applyVariantSelections]);

  const persistCatalogSnapshot = useCallback((
    session: AuthSession,
    catalogResult: CatalogBrowseResult,
    library: GameInfo[],
    queryKey: string,
    proxyUrl?: string,
  ): void => {
    if (hasSessionProxyCredentials(proxyUrl)) {
      clearCatalogSnapshot();
      return;
    }

    saveCatalogSnapshot({
      version: 1,
      userId: session.user.userId,
      streamingBaseUrl: session.provider.streamingServiceUrl,
      queryKey,
      games: catalogResult.games,
      libraryGames: library,
      filterGroups: catalogResult.filterGroups,
      sortOptions: catalogResult.sortOptions,
      totalCount: catalogResult.totalCount,
      supportedCount: catalogResult.numberSupported,
      savedAt: Date.now(),
    });
  }, []);

  const hydrateCatalogSnapshot = useCallback((session: AuthSession, proxyUrl: string | undefined = activeSessionProxyUrl): string | null => {
    if (hasSessionProxyCredentials(proxyUrl)) {
      clearCatalogSnapshot();
      return null;
    }

    const queryKey = buildProxyAwareCatalogQueryKey("", catalogSelectedFilterIds, catalogSelectedSortId, proxyUrl);
    const snapshot = loadCatalogSnapshot(
      session.user.userId,
      session.provider.streamingServiceUrl,
      queryKey,
    );
    if (!snapshot) {
      return null;
    }

    setGames(snapshot.games);
    setLibraryGames(snapshot.libraryGames);
    setCatalogFilterGroups(snapshot.filterGroups);
    setCatalogSortOptions(snapshot.sortOptions);
    setCatalogTotalCount(snapshot.totalCount);
    setCatalogSupportedCount(snapshot.supportedCount);
    setSelectedGameId((previous) => (
      snapshot.games.some((game) => game.id === previous) ? previous : (snapshot.games[0]?.id ?? "")
    ));
    applyVariantSelections([...snapshot.games, ...snapshot.libraryGames]);
    lastCatalogQueryRef.current = queryKey;
    lastCatalogProxyUrlRef.current = proxyUrl;
    return queryKey;
  }, [activeSessionProxyUrl, applyVariantSelections, catalogSelectedFilterIds, catalogSelectedSortId]);

  const loadSessionRuntimeData = useCallback(async (
    session: AuthSession,
    options?: { background?: boolean; proxyUrl?: string },
  ): Promise<void> => {
    const token = session.tokens.idToken ?? session.tokens.accessToken;
    const streamingBaseUrl = session.provider.streamingServiceUrl;
    const userId = session.user.userId;
    const loadId = ++runtimeDataLoadIdRef.current;
    const isCurrentLoad = (): boolean => runtimeDataLoadIdRef.current === loadId;
    const background = options?.background === true;
    const proxyUrl = options?.proxyUrl ?? activeSessionProxyUrl;
    const catalogQueryKey = buildProxyAwareCatalogQueryKey("", catalogSelectedFilterIds, catalogSelectedSortId, proxyUrl);

    if (!background) {
      lastCatalogQueryRef.current = null;
      lastCatalogProxyUrlRef.current = proxyUrl;
      setIsLoadingCatalog(true);
      setIsLoadingLibrary(true);
    }

    void window.openNow.getRegions({ token }).then((discovered) => {
      if (isCurrentLoad()) setRegions(discovered);
    }).catch((error) => {
      console.warn("Failed to load regions:", error);
      if (isCurrentLoad()) setRegions([]);
    });

    void window.openNow.fetchSubscription({
      token,
      providerStreamingBaseUrl: streamingBaseUrl,
      userId: session.user.userId,
    }).then((subscription) => {
      if (isCurrentLoad()) setSubscriptionInfo(subscription);
    }).catch((error) => {
      console.warn("Failed to load subscription info:", error);
      if (isCurrentLoad()) setSubscriptionInfo(null);
    });

    let latestCatalogResult: CatalogBrowseResult | null = null;
    let latestLibraryGames: GameInfo[] | null = null;

    void window.openNow.browseCatalog({
      token,
      userId,
      providerStreamingBaseUrl: streamingBaseUrl,
      proxyUrl,
      searchQuery: "",
      sortId: catalogSelectedSortId,
      filterIds: catalogSelectedFilterIds,
    }).then((catalogResult) => {
      if (!isCurrentLoad()) return;
      latestCatalogResult = catalogResult;
      applyCatalogBrowseResult(catalogResult);
      lastCatalogQueryRef.current = catalogQueryKey;
      lastCatalogProxyUrlRef.current = proxyUrl;
      if (latestLibraryGames) {
        persistCatalogSnapshot(session, catalogResult, latestLibraryGames, catalogQueryKey, proxyUrl);
      }
    }).catch((error) => {
      console.error("Catalog load failed:", error);
      if (!isCurrentLoad() || background) return;
      setGames([]);
      setCatalogFilterGroups([]);
      setCatalogSortOptions([]);
      setCatalogTotalCount(0);
      setCatalogSupportedCount(0);
    }).finally(() => {
      if (isCurrentLoad() && !background) setIsLoadingCatalog(false);
    });

    void window.openNow.fetchLibraryGames({
      token,
      userId,
      providerStreamingBaseUrl: streamingBaseUrl,
      proxyUrl,
    }).then((libGames) => {
      if (!isCurrentLoad()) return;
      latestLibraryGames = libGames;
      setLibraryGames(libGames);
      applyVariantSelections(libGames);
      if (latestCatalogResult) {
        persistCatalogSnapshot(session, latestCatalogResult, libGames, catalogQueryKey, proxyUrl);
      }
    }).catch((error) => {
      console.error("Library load failed:", error);
      if (!isCurrentLoad() || background) return;
      setLibraryGames([]);
    }).finally(() => {
      if (isCurrentLoad() && !background) setIsLoadingLibrary(false);
    });

    void window.openNow.fetchFeaturedGames({
      token,
      userId,
      providerStreamingBaseUrl: streamingBaseUrl,
      proxyUrl,
    }).then((featured) => {
      if (isCurrentLoad()) setFeaturedGames(featured);
    }).catch((error) => {
      console.warn("Featured games load failed:", error);
      if (isCurrentLoad()) setFeaturedGames([]);
    });
  }, [
    activeSessionProxyUrl,
    applyCatalogBrowseResult,
    applyVariantSelections,
    catalogSelectedFilterIds,
    catalogSelectedSortId,
    persistCatalogSnapshot,
  ]);

  const clearSessionCatalog = useCallback((mode: CatalogClearMode, options?: ClearSessionCatalogOptions): void => {
    runtimeDataLoadIdRef.current += 1;
    resetStorePanels();
    setGames([]);
    setLibraryGames([]);
    setSubscriptionInfo(null);
    setCatalogFilterGroups([]);
    setCatalogSortOptions([]);
    setCatalogTotalCount(0);
    setCatalogSupportedCount(0);
    setIsLoadingCatalog(false);
    setIsLoadingLibrary(false);

    if (mode === "logout") {
      clearCatalogSnapshot();
      setVariantByGameId({});
      setCatalogSelectedSortId("relevance");
      setCatalogSelectedFilterIds([]);
      setSelectedGameId("");
      return;
    }

    setRegions([]);
    if (options?.clearFeatured) {
      setFeaturedGames([]);
    }
  }, [resetStorePanels]);

  const loadSubscriptionInfo = useCallback(
    async (session: AuthSession): Promise<void> => {
      const token = session.tokens.idToken ?? session.tokens.accessToken;
      const subscription = await window.openNow.fetchSubscription({
        token,
        providerStreamingBaseUrl: session.provider.streamingServiceUrl,
        userId: session.user.userId,
      });
      setSubscriptionInfo(subscription);
    },
    [],
  );

  const loadGames = useCallback(async (
    targetSource: "main" | "library",
    options?: { background?: boolean },
  ) => {
    const setLoading = targetSource === "main" ? setIsLoadingCatalog : setIsLoadingLibrary;
    if (!options?.background) {
      setLoading(true);
    }
    try {
      const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
      const userId = authSession?.user.userId;
      const baseUrl = effectiveStreamingBaseUrl;
      const proxyUrl = activeSessionProxyUrl;
      if (!token || !userId) {
        return;
      }

      if (targetSource === "main") {
        const catalogResult = await window.openNow.browseCatalog({
          token,
          userId,
          providerStreamingBaseUrl: baseUrl,
          proxyUrl,
          searchQuery,
          sortId: catalogSelectedSortId,
          filterIds: catalogSelectedFilterIds,
        });
        applyCatalogBrowseResult(catalogResult);
        if (featuredGames.length === 0) {
          void window.openNow.fetchFeaturedGames({ token, userId, providerStreamingBaseUrl: baseUrl, proxyUrl }).then((featured) => {
            if (featured.length > 0) setFeaturedGames(featured);
          }).catch((error) => {
            console.warn("Featured games refresh failed:", error);
          });
        }
        return;
      }

      const result = await window.openNow.fetchLibraryGames({ token, userId, providerStreamingBaseUrl: baseUrl, proxyUrl });
      setLibraryGames(result);
      setSelectedGameId((previous) => result.some((game) => game.id === previous) ? previous : (result[0]?.id ?? ""));
      applyVariantSelections(result);
    } catch (error) {
      console.error("Failed to load games:", error);
    } finally {
      if (!options?.background) {
        setLoading(false);
      }
    }
  }, [activeSessionProxyUrl, applyCatalogBrowseResult, applyVariantSelections, authSession, effectiveStreamingBaseUrl, featuredGames.length, searchQuery, catalogFilterKey, catalogSelectedSortId]);

  const loadStorePanels = useCallback(async (options?: { force?: boolean; background?: boolean }) => {
    const session = authSession;
    if (!session) return;

    const token = session.tokens.idToken ?? session.tokens.accessToken;
    if (!token) return;

    const contextKey = `${session.user.userId}\0${effectiveStreamingBaseUrl}\0${getSessionProxyUiScope(activeSessionProxyUrl)}`;
    if (!options?.force && storePanelsLoadedContextRef.current === contextKey) return;

    const loadId = ++storePanelsLoadIdRef.current;
    const isCurrentLoad = (): boolean => storePanelsLoadIdRef.current === loadId;
    if (!options?.background) setIsLoadingStorePanels(true);
    try {
      const panels = await window.openNow.fetchStorePanels({
        token,
        providerStreamingBaseUrl: effectiveStreamingBaseUrl,
        proxyUrl: activeSessionProxyUrl,
      });
      if (!isCurrentLoad()) return;
      const panelGames = flattenStorePanelGames(panels);
      storePanelsLoadedContextRef.current = contextKey;
      setStorePanels(panels);
      setSelectedGameId((previous) => panelGames.some((game) => game.id === previous) ? previous : (panelGames[0]?.id ?? ""));
      setVariantByGameId((previous) => {
        const next = { ...previous };
        for (const game of panelGames) {
          next[game.id] = defaultVariantId(game);
        }
        return next;
      });
    } catch (error) {
      if (!isCurrentLoad()) return;
      console.error("Failed to load Store panels:", error);
      storePanelsLoadedContextRef.current = "";
      setStorePanels([]);
    } finally {
      if (isCurrentLoad() && !options?.background) setIsLoadingStorePanels(false);
    }
  }, [activeSessionProxyUrl, authSession, effectiveStreamingBaseUrl]);

  const handleMarkGameOwned = useCallback(async (game: GameInfo, selectedVariantId?: string): Promise<void> => {
    const session = authSession;
    const token = session?.tokens.idToken ?? session?.tokens.accessToken;
    const userId = session?.user.userId;
    if (!token || !userId) {
      setCatalogActionNotice({ tone: "warn", text: t("errors.markOwnedSignInRequired") });
      return;
    }

    const selectedVariant = getSelectedVariant(game, selectedVariantId ?? variantByGameId[game.id] ?? defaultVariantId(game));
    const variantId = selectedVariant?.id ?? selectedVariantId;
    if (!variantId) {
      setCatalogActionNotice({ tone: "warn", text: t("errors.markOwnedMissingVariant") });
      return;
    }
    if (markOwnedInFlightByVariantId[variantId]) {
      return;
    }

    setMarkOwnedInFlightByVariantId((previous) => ({ ...previous, [variantId]: true }));
    try {
      await window.openNow.markGameOwned({
        token,
        userId,
        providerStreamingBaseUrl: effectiveStreamingBaseUrl,
        proxyUrl: activeSessionProxyUrl,
        variantId,
      });

      setVariantByGameId((previous) => ({ ...previous, [game.id]: variantId }));
      setGames((previous) => markGameOwnedInList(previous, game, variantId));
      setFeaturedGames((previous) => markGameOwnedInList(previous, game, variantId));
      setLibraryGames((previous) => upsertMarkedOwnedLibraryGame(previous, game, variantId));
      setStorePanels((previous) => markGameOwnedInPanels(previous, game, variantId));
      setCatalogActionNotice({ tone: "success", text: t("games.markOwned.success", { title: game.title }) });

      void loadGames("main", { background: true });
      void loadGames("library", { background: true });
      void loadStorePanels({ force: true, background: true });
    } catch (error) {
      console.error("Failed to mark game as owned:", error);
      setCatalogActionNotice({
        tone: "warn",
        text: error instanceof Error && error.message
          ? t("errors.markOwnedFailedWithReason", { reason: error.message })
          : t("errors.markOwnedFailed"),
      });
    } finally {
      setMarkOwnedInFlightByVariantId((previous) => {
        const next = { ...previous };
        delete next[variantId];
        return next;
      });
    }
  }, [
    activeSessionProxyUrl,
    authSession,
    effectiveStreamingBaseUrl,
    loadGames,
    loadStorePanels,
    markOwnedInFlightByVariantId,
    t,
    variantByGameId,
  ]);

  const storePanelGames = useMemo(() => flattenStorePanelGames(storePanels), [storePanels]);
  const allKnownGames = useMemo(() => [...games, ...libraryGames, ...storePanelGames], [games, libraryGames, storePanelGames]);

  useEffect(() => {
    if (storePanelGames.length === 0 || libraryGames.length === 0) return;
    setVariantByGameId((previous) => {
      let changed = false;
      const next = { ...previous };
      for (const game of storePanelGames) {
        const libraryVariantId = getLibrarySelectedVariantId(game, libraryGames);
        if (libraryVariantId && next[game.id] !== libraryVariantId) {
          next[game.id] = libraryVariantId;
          changed = true;
        }
      }
      return changed ? next : previous;
    });
  }, [libraryGames, storePanelGames]);

  useEffect(() => {
    if (!authSession || currentPage !== "home" || effectiveControllerMode || isInitializing) {
      return;
    }
    const queryKey = buildProxyAwareCatalogQueryKey(searchQuery, catalogSelectedFilterIds, catalogSelectedSortId, activeSessionProxyUrl);
    if (
      lastCatalogQueryRef.current === queryKey
      && lastCatalogProxyUrlRef.current === activeSessionProxyUrl
      && games.length > 0
    ) {
      return;
    }
    lastCatalogQueryRef.current = queryKey;
    lastCatalogProxyUrlRef.current = activeSessionProxyUrl;

    const handle = window.setTimeout(() => {
      void loadGames("main", { background: games.length > 0 });
    }, searchQuery.trim() ? 220 : 0);
    return () => window.clearTimeout(handle);
  }, [
    authSession,
    currentPage,
    games.length,
    isInitializing,
    loadGames,
    searchQuery,
    activeSessionProxyUrl,
    catalogFilterKey,
    catalogSelectedSortId,
    effectiveControllerMode,
  ]);

  useEffect(() => {
    if (!authSession || currentPage !== "home" || !effectiveControllerMode) {
      return;
    }
    void loadStorePanels();
  }, [authSession, currentPage, loadStorePanels, effectiveControllerMode]);

  useEffect(() => {
    if (!catalogActionNotice) return;
    const timer = window.setTimeout(() => {
      setCatalogActionNotice((current) => (current === catalogActionNotice ? null : current));
    }, 4500);
    return () => window.clearTimeout(timer);
  }, [catalogActionNotice]);

  useEffect(() => {
    saveCatalogPreferences({ sortId: catalogSelectedSortId, filterIds: catalogSelectedFilterIds });
  }, [catalogSelectedSortId, catalogSelectedFilterIds]);

  const handleSelectGameVariant = useCallback((gameId: string, variantId: string): void => {
    setVariantByGameId((prev) => {
      if (prev[gameId] === variantId) {
        return prev;
      }
      const next = { ...prev, [gameId]: variantId };
      try {
        localStorage.setItem(VARIANT_SELECTION_LOCALSTORAGE_KEY, JSON.stringify(next));
      } catch {
        // ignore storage errors
      }
      return next;
    });
  }, []);

  const handleToggleCatalogFilter = useCallback((filterId: string): void => {
    setCatalogSelectedFilterIds((previous) => (
      previous.includes(filterId)
        ? previous.filter((value) => value !== filterId)
        : [...previous, filterId]
    ));
  }, []);

  return {
    games,
    featuredGames,
    storePanels,
    libraryGames,
    searchQuery,
    setSearchQuery,
    selectedGameId,
    setSelectedGameId,
    variantByGameId,
    setVariantByGameId,
    isLoadingCatalog,
    isLoadingLibrary,
    isLoadingStorePanels,
    catalogFilterGroups,
    catalogSortOptions,
    catalogSelectedSortId,
    setCatalogSelectedSortId,
    catalogSelectedFilterIds,
    setCatalogSelectedFilterIds,
    catalogTotalCount,
    catalogSupportedCount,
    catalogFilterKey,
    markOwnedInFlightByVariantId,
    catalogActionNotice,
    setCatalogActionNotice,
    regions,
    setRegions,
    subscriptionInfo,
    setSubscriptionInfo,
    storePanelGames,
    allKnownGames,
    resetStorePanels,
    applyVariantSelections,
    hydrateCatalogSnapshot,
    loadSessionRuntimeData,
    clearSessionCatalog,
    loadGames,
    loadStorePanels,
    handleMarkGameOwned,
    handleSelectGameVariant,
    handleToggleCatalogFilter,
    loadSubscriptionInfo,
  };
}
