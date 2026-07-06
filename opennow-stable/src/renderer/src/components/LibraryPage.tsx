import { Library, Search, Clock, Gamepad2, Loader2, ArrowUpDown, MoreHorizontal, Menu, Filter, ChevronDown, X } from "lucide-react";
import { memo, useEffect, useMemo, useRef, useState } from "react";
import type { JSX } from "react";
import { AnimatePresence, m } from "motion/react";
import type { CatalogSortOption, GameInfo } from "@shared/gfn";
import { getStoreDisplayName, getStoreIconComponent, normalizeStoreKey } from "./GameCard";
import { GameCardListItem, useCatalogCardActionsRef } from "./GameCardListItem";
import type { PlaytimeData } from "../lib/gameCatalog";
import { useTranslation } from "../i18n";
import { formatCatalogLastPlayed } from "../utils/lastPlayedFormat";
import { controllerButton, readControllerGamepadButtons } from "../utils/controllerGamepad";
import { pageTransition, panelSpring } from "./MotionProvider";
import { SelectDropdown } from "./ui/SelectDropdown";

const CONTROLLER_HERO_ROTATION_MS = 8000;
const CONTROLLER_MOVE_REPEAT_MS = 140;
const CONTROLLER_Y_HOLD_MS = 350;

const CONTROLLER_HERO_BACKGROUND_KEYS = [
  "MARQUEE_HERO_IMAGE",
  "FEATURE_IMAGE",
  "HERO_IMAGE",
  "TV_BANNER",
  "KEY_ART",
  "KEY_IMAGE",
] as const;

interface ControllerStoreFilterItem {
  id: string;
  title: string;
}

interface LibraryFilterOption {
  id: string;
  label: string;
  count: number;
}

interface LibraryFilterGroup {
  id: string;
  label: string;
  options: LibraryFilterOption[];
}

type LibraryTranslation = (key: string, values?: Record<string, string | number | boolean | null | undefined>) => string;

export interface LibraryPageProps {
  games: GameInfo[];
  allGames: GameInfo[];
  playtimeData: PlaytimeData;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onPlayGame: (game: GameInfo) => void;
  onBuyGame?: (game: GameInfo, selectedVariantId?: string) => void;
  isLoading: boolean;
  selectedGameId: string;
  onSelectGame: (id: string) => void;
  selectedVariantByGameId: Record<string, string>;
  onSelectGameVariant: (gameId: string, variantId: string) => void;
  libraryCount: number;
  sortOptions: CatalogSortOption[];
  selectedSortId: string;
  onSortChange: (sortId: string) => void;
  controllerMode?: boolean;
  featuredGames?: GameInfo[];
  activeSessionAppIds?: number[];
  onPreviousControllerPage?: () => void;
  onNextControllerPage?: () => void;
}

function appendUnique(values: string[], candidate: string | undefined): void {
  if (!candidate || values.includes(candidate)) return;
  values.push(candidate);
}

function appendImageType(values: string[], game: GameInfo, type: string): void {
  for (const candidate of game.imageUrlsByType?.[type] ?? []) {
    appendUnique(values, candidate);
  }
}

function getControllerHeroBackgroundCandidates(game: GameInfo): string[] {
  const candidates: string[] = [];
  for (const type of CONTROLLER_HERO_BACKGROUND_KEYS) {
    appendImageType(candidates, game, type);
  }
  appendUnique(candidates, game.heroImageUrl);
  appendUnique(candidates, game.imageUrl);
  for (const candidate of game.screenshotUrls ?? []) appendUnique(candidates, candidate);
  appendUnique(candidates, game.screenshotUrl);
  return candidates;
}

function getControllerHeroLogoUrl(game: GameInfo): string | undefined {
  return game.imageUrlsByType?.GAME_LOGO?.find(Boolean);
}

function getGameLogoUrl(game: GameInfo): string | undefined {
  return game.imageUrlsByType?.GAME_LOGO?.find(Boolean);
}

function getControllerFeaturedGames(featuredGames: GameInfo[], fallbackGames: GameInfo[]): GameInfo[] {
  const source = featuredGames.length > 0 ? featuredGames : fallbackGames;
  return source.slice(0, 6);
}

function getGameStoreSummary(game: GameInfo, fallback: string): string {
  const stores = [...new Set((game.availableStores?.length ? game.availableStores : game.variants.map((variant) => variant.store)).filter(Boolean))];
  if (stores.length === 0) return fallback;
  const visible = stores.slice(0, 3).join(", ");
  return stores.length > 3 ? `${visible} +${stores.length - 3}` : visible;
}

function getSelectedVariantStoreLabel(game: GameInfo, selectedVariantId: string | undefined, fallback: string): string {
  const selectedVariant = game.variants.find((variant) => variant.id === selectedVariantId)
    ?? game.variants[game.selectedVariantIndex]
    ?? game.variants[0];
  return selectedVariant?.store ? getStoreDisplayName(selectedVariant.store) : fallback;
}

function getPlayerSummary(game: GameInfo): string | null {
  const parts: string[] = [];
  if (game.maxLocalPlayers && game.maxLocalPlayers > 0) parts.push(`Local ${game.maxLocalPlayers}`);
  if (game.maxOnlinePlayers && game.maxOnlinePlayers > 0) parts.push(`Online ${game.maxOnlinePlayers}`);
  return parts.length > 0 ? parts.join(" / ") : null;
}

function gameMatchesActiveSession(game: GameInfo, activeSessionAppIds: number[]): boolean {
  if (activeSessionAppIds.length === 0) return false;
  const appIds = new Set(activeSessionAppIds.map(String));
  if (game.launchAppId && appIds.has(game.launchAppId)) return true;
  if (appIds.has(game.id)) return true;
  return game.variants.some((variant) => appIds.has(variant.id));
}

function gameMatchesStoreFilter(game: GameInfo, filterId: string): boolean {
  if (filterId === "library") return true;
  const store = filterId.slice("store:".length);
  return game.variants.some((variant) => variant.store === store) || (game.availableStores ?? []).includes(store);
}

function normalizeLibraryFilterValue(value: string | undefined): string {
  return (value ?? "")
    .trim()
    .toLowerCase()
    .replace(/['’]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function formatLibraryFilterLabel(value: string): string {
  return value
    .trim()
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function getGameCatalogSkuText(game: GameInfo): string {
  const values = Object.values(game.catalogSkuStrings ?? {});
  const parts: string[] = [];
  for (const value of values) {
    if (typeof value === "string") {
      parts.push(value);
    } else if (Array.isArray(value)) {
      parts.push(...value.filter((item): item is string => typeof item === "string"));
    }
  }
  return parts.join(" ");
}

function gameRequiresInstallToPlay(game: GameInfo): boolean {
  const haystack = [
    game.playType,
    game.playabilityState,
    ...(game.featureLabels ?? []),
    getGameCatalogSkuText(game),
  ].join(" ").toLowerCase();
  return haystack.includes("install");
}

function getGamePlayTypeFilter(game: GameInfo, t: LibraryTranslation): LibraryFilterOption | null {
  if (gameRequiresInstallToPlay(game)) {
    return { id: "play:install-to-play", label: t("library.filterOptions.installToPlay"), count: 0 };
  }
  if (!game.playType?.trim()) return null;
  const normalized = normalizeLibraryFilterValue(game.playType);
  if (!normalized) return null;
  if (normalized.includes("instant") || normalized.includes("stream") || normalized.includes("cloud")) {
    return { id: "play:cloud-launch", label: t("library.filterOptions.cloudLaunch"), count: 0 };
  }
  const label = formatLibraryFilterLabel(game.playType);
  const key = normalizeLibraryFilterValue(label);
  return key ? { id: `play:${key}`, label, count: 0 } : null;
}

function getGameStoreFilters(game: GameInfo): Array<{ id: string; label: string }> {
  const stores = game.availableStores?.length ? game.availableStores : game.variants.map((variant) => variant.store);
  const seen = new Set<string>();
  const filters: Array<{ id: string; label: string }> = [];
  for (const store of stores) {
    if (!store.trim()) continue;
    const key = normalizeStoreKey(store);
    if (seen.has(key)) continue;
    seen.add(key);
    filters.push({ id: `platform:${key}`, label: getStoreDisplayName(store) });
  }
  return filters;
}

function getControlFilter(control: string, t: LibraryTranslation): { id: string; label: string } | null {
  const normalized = normalizeLibraryFilterValue(control);
  if (!normalized) return null;
  if (normalized.includes("keyboard") || normalized.includes("mouse")) {
    return { id: "controls:keyboard-mouse", label: t("library.filterOptions.keyboardMouse") };
  }
  if (normalized.includes("gamepad") || normalized.includes("controller")) {
    return { id: "controls:controller", label: t("library.filterOptions.controller") };
  }
  if (normalized.includes("touch")) {
    return { id: "controls:touch", label: t("library.filterOptions.touch") };
  }
  const label = formatLibraryFilterLabel(control);
  const key = normalizeLibraryFilterValue(label);
  return key ? { id: `controls:${key}`, label } : null;
}

function getGameControlFilters(game: GameInfo, t: LibraryTranslation): Array<{ id: string; label: string }> {
  const controls = [
    ...(game.supportedControls ?? []),
    ...game.variants.flatMap((variant) => variant.supportedControls),
  ];
  const seen = new Set<string>();
  const filters: Array<{ id: string; label: string }> = [];
  for (const control of controls) {
    const filter = getControlFilter(control, t);
    if (!filter || seen.has(filter.id)) continue;
    seen.add(filter.id);
    filters.push(filter);
  }
  return filters;
}

function upsertLibraryFilterOption(options: Map<string, LibraryFilterOption>, id: string, label: string): void {
  const existing = options.get(id);
  if (existing) {
    existing.count += 1;
    return;
  }
  options.set(id, { id, label, count: 1 });
}

function mapLibraryFilterOptions(options: Map<string, LibraryFilterOption>): LibraryFilterOption[] {
  return [...options.values()].sort((left, right) => left.label.localeCompare(right.label));
}

function gameHasLibraryActivity(game: GameInfo, playtimeData: PlaytimeData): boolean {
  if (game.lastPlayed) return true;
  const record = playtimeData[game.id];
  if (!record) return false;
  return Boolean(record.lastPlayedAt) || (record.totalSeconds ?? 0) > 0 || (record.sessionCount ?? 0) > 0;
}

function getLibraryFilterGroups(games: GameInfo[], playtimeData: PlaytimeData, t: LibraryTranslation): LibraryFilterGroup[] {
  const playTypeOptions = new Map<string, LibraryFilterOption>();
  const platformOptions = new Map<string, LibraryFilterOption>();
  const controlOptions = new Map<string, LibraryFilterOption>();
  const activityOptions = new Map<string, LibraryFilterOption>();

  for (const game of games) {
    const playTypeOption = getGamePlayTypeFilter(game, t);
    if (playTypeOption) upsertLibraryFilterOption(playTypeOptions, playTypeOption.id, playTypeOption.label);

    for (const option of getGameStoreFilters(game)) {
      upsertLibraryFilterOption(platformOptions, option.id, option.label);
    }

    for (const option of getGameControlFilters(game, t)) {
      upsertLibraryFilterOption(controlOptions, option.id, option.label);
    }

    const hasActivity = gameHasLibraryActivity(game, playtimeData);
    upsertLibraryFilterOption(
      activityOptions,
      hasActivity ? "activity:played" : "activity:never-played",
      hasActivity ? t("library.filterOptions.played") : t("library.filterOptions.neverPlayed"),
    );
  }

  return [
    { id: "play", label: t("library.filterGroups.playType"), options: mapLibraryFilterOptions(playTypeOptions) },
    { id: "platform", label: t("library.filterGroups.platform"), options: mapLibraryFilterOptions(platformOptions) },
    { id: "controls", label: t("library.filterGroups.controls"), options: mapLibraryFilterOptions(controlOptions) },
    { id: "activity", label: t("library.filterGroups.activity"), options: mapLibraryFilterOptions(activityOptions) },
  ].filter((group) => group.options.length > 0);
}

function getLibraryFilterGroupId(filterId: string): string {
  const separatorIndex = filterId.indexOf(":");
  return separatorIndex >= 0 ? filterId.slice(0, separatorIndex) : filterId;
}

function gameMatchesLibraryFilter(game: GameInfo, filterId: string, playtimeData: PlaytimeData, t: LibraryTranslation): boolean {
  const [groupId, value] = filterId.split(":");
  if (!value) return true;

  if (groupId === "play") {
    return getGamePlayTypeFilter(game, t)?.id === filterId;
  }

  if (groupId === "platform") {
    return getGameStoreFilters(game).some((option) => option.id === filterId);
  }

  if (groupId === "controls") {
    return getGameControlFilters(game, t).some((option) => option.id === filterId);
  }

  if (groupId === "activity") {
    const hasActivity = gameHasLibraryActivity(game, playtimeData);
    return value === "played" ? hasActivity : !hasActivity;
  }

  return true;
}

function gameMatchesLibraryFilters(game: GameInfo, selectedFilterIds: string[], playtimeData: PlaytimeData, t: LibraryTranslation): boolean {
  if (selectedFilterIds.length === 0) return true;
  const selectedByGroup = new Map<string, string[]>();
  for (const filterId of selectedFilterIds) {
    const groupId = getLibraryFilterGroupId(filterId);
    selectedByGroup.set(groupId, [...(selectedByGroup.get(groupId) ?? []), filterId]);
  }
  for (const groupFilterIds of selectedByGroup.values()) {
    if (!groupFilterIds.some((filterId) => gameMatchesLibraryFilter(game, filterId, playtimeData, t))) return false;
  }
  return true;
}

function getLibraryFilterOptionById(groups: LibraryFilterGroup[], id: string): LibraryFilterOption | undefined {
  for (const group of groups) {
    const option = group.options.find((candidate) => candidate.id === id);
    if (option) return option;
  }
  return undefined;
}

function getControllerStoreFilterItems(games: GameInfo[], allStoresLabel: string): ControllerStoreFilterItem[] {
  const stores = new Set<string>();
  for (const game of games) {
    for (const store of game.availableStores ?? []) {
      if (store.trim()) stores.add(store);
    }
    for (const variant of game.variants) {
      if (variant.store.trim()) stores.add(variant.store);
    }
  }

  return [
    { id: "library", title: allStoresLabel },
    ...[...stores].sort((left, right) => left.localeCompare(right)).map((store) => ({ id: `store:${store}`, title: store })),
  ];
}

function ControllerGameCard({
  game,
  isSelected,
  selectedVariantId,
  onSelect,
  onPlay,
}: {
  game: GameInfo;
  isSelected: boolean;
  selectedVariantId?: string;
  onSelect: () => void;
  onPlay: () => void;
}): JSX.Element {
  const selectedVariant = game.variants.find((variant) => variant.id === selectedVariantId) ?? game.variants[game.selectedVariantIndex] ?? game.variants[0];
  const store = selectedVariant?.store ?? game.availableStores?.[0] ?? "";
  const StoreIcon = getStoreIconComponent(store);
  const logoUrl = getGameLogoUrl(game);

  return (
    <m.button
      type="button"
      className={`controller-native-card${isSelected ? " selected" : ""}`}
      onClick={onSelect}
      onDoubleClick={onPlay}
      aria-label={game.title}
      animate={{ y: isSelected ? -3 : 0, scale: isSelected ? 1.025 : 1 }}
      whileHover={{ y: -3, scale: 1.02 }}
      whileTap={{ scale: 0.985 }}
      transition={panelSpring}
    >
      <span className="controller-native-card-art">
        {game.imageUrl ? <img src={game.imageUrl} alt="" loading="lazy" /> : <span className="controller-native-card-placeholder">{game.title.slice(0, 1)}</span>}
      </span>
      {store && (
        <span className="controller-native-card-store" title={getStoreDisplayName(store)}>
          <StoreIcon />
        </span>
      )}
      <span className="controller-native-card-title">
        {logoUrl ? <img src={logoUrl} alt={game.title} loading="lazy" /> : game.title}
      </span>
    </m.button>
  );
}

export const LibraryPage = memo(function LibraryPage({
  games,
  allGames,
  playtimeData,
  searchQuery,
  onSearchChange,
  onPlayGame,
  onBuyGame,
  isLoading,
  selectedGameId,
  onSelectGame,
  selectedVariantByGameId,
  onSelectGameVariant,
  libraryCount,
  sortOptions,
  selectedSortId,
  onSortChange,
  controllerMode = false,
  featuredGames = [],
  activeSessionAppIds = [],
  onPreviousControllerPage,
  onNextControllerPage,
}: LibraryPageProps): JSX.Element {
  const { t } = useTranslation();
  const catalogActionsRef = useCatalogCardActionsRef({
    onPlayGame,
    onSelectGame,
    onSelectGameVariant,
  });
  const [controllerHeroIndex, setControllerHeroIndex] = useState(0);
  const [detailsGame, setDetailsGame] = useState<GameInfo | null>(null);
  const [controllerStoreFilterId, setControllerStoreFilterId] = useState("library");
  const [controllerStoreFilterOpen, setControllerStoreFilterOpen] = useState(false);
  const [controllerSearchOpen, setControllerSearchOpen] = useState(false);
  const [focusedControllerStoreFilterIndex, setFocusedControllerStoreFilterIndex] = useState(0);
  const [selectedLibraryFilterIds, setSelectedLibraryFilterIds] = useState<string[]>([]);
  const controllerSearchInputRef = useRef<HTMLInputElement | null>(null);
  const gamepadPreviousButtonsRef = useRef(0);
  const gamepadLastMoveAtRef = useRef(0);
  const gamepadFrameRef = useRef<number | null>(null);
  const controllerYPressedAtRef = useRef(0);
  const controllerYConsumedByHoldRef = useRef(false);
  const controllerGameRowRef = useRef<HTMLDivElement | null>(null);
  const controllerInputStateRef = useRef({
    detailsGame: null as GameInfo | null,
    selectedControllerGame: undefined as GameInfo | undefined,
    selectedControllerGameIndex: 0,
    controllerStoreFilterOpen: false,
    focusedControllerStoreFilterIndex: 0,
    controllerStoreFilterItems: [] as ControllerStoreFilterItem[],
    focusControllerGame: (_index: number): void => {},
    cycleSelectedVariant: (): void => {},
    cycleControllerStoreFilter: (): void => {},
    moveControllerStoreFilterFocusBy: (_delta: number): void => {},
    hideControllerStoreFilterOverlay: (_applySelection: boolean): void => {},
    showControllerStoreFilterOverlay: (): void => {},
    onPlayGame: (_game: GameInfo): void => {},
  });

  useEffect(() => {
    if (!controllerMode || !controllerSearchOpen) return;
    controllerSearchInputRef.current?.focus();
  }, [controllerMode, controllerSearchOpen]);

  const librarySearchHasQuery = searchQuery.trim().length > 0;
  const libraryFilterGroups = useMemo(
    () => getLibraryFilterGroups(allGames, playtimeData, t),
    [allGames, playtimeData, t],
  );
  const visibleLibraryGames = useMemo(
    () => games.filter((game) => gameMatchesLibraryFilters(game, selectedLibraryFilterIds, playtimeData, t)),
    [games, playtimeData, selectedLibraryFilterIds, t],
  );
  const activeLibraryFilterOptions = useMemo(
    () => selectedLibraryFilterIds
      .map((filterId) => getLibraryFilterOptionById(libraryFilterGroups, filterId))
      .filter((option): option is LibraryFilterOption => Boolean(option)),
    [libraryFilterGroups, selectedLibraryFilterIds],
  );
  const hasActiveLibraryFilters = activeLibraryFilterOptions.length > 0;
  const libraryCountLabel = hasActiveLibraryFilters || librarySearchHasQuery
    ? t("library.filteredGameCount", { shown: visibleLibraryGames.length, total: libraryCount, count: libraryCount })
    : t("library.gameCount", { count: libraryCount });

  useEffect(() => {
    const availableFilterIds = new Set(libraryFilterGroups.flatMap((group) => group.options.map((option) => option.id)));
    setSelectedLibraryFilterIds((previous) => {
      const next = previous.filter((filterId) => availableFilterIds.has(filterId));
      return next.length === previous.length ? previous : next;
    });
  }, [libraryFilterGroups]);

  useEffect(() => {
    if (controllerMode || visibleLibraryGames.length === 0) return;
    if (visibleLibraryGames.some((game) => game.id === selectedGameId)) return;
    onSelectGame(visibleLibraryGames[0].id);
  }, [controllerMode, onSelectGame, selectedGameId, visibleLibraryGames]);

  const toggleLibraryFilter = (filterId: string): void => {
    setSelectedLibraryFilterIds((previous) => (
      previous.includes(filterId)
        ? previous.filter((selectedFilterId) => selectedFilterId !== filterId)
        : [...previous, filterId]
    ));
  };

  const clearLibraryFilters = (): void => {
    setSelectedLibraryFilterIds([]);
  };

  const controllerStoreFilterItems = useMemo(
    () => getControllerStoreFilterItems(games, t("library.allStores")),
    [games, t],
  );
  const controllerGames = useMemo(
    () => controllerStoreFilterId === "library" ? games : games.filter((game) => gameMatchesStoreFilter(game, controllerStoreFilterId)),
    [controllerStoreFilterId, games],
  );
  const controllerFeaturedGames = useMemo(
    () => getControllerFeaturedGames(featuredGames, controllerGames),
    [featuredGames, controllerGames],
  );

  useEffect(() => {
    if (!controllerMode) return;
    setControllerHeroIndex(0);
  }, [controllerMode, controllerFeaturedGames]);

  useEffect(() => {
    if (controllerMode) return;
    gamepadPreviousButtonsRef.current = 0;
    gamepadLastMoveAtRef.current = 0;
  }, [controllerMode]);

  useEffect(() => {
    if (!controllerMode || controllerFeaturedGames.length <= 1) return;
    const interval = window.setInterval(() => {
      setControllerHeroIndex((index) => (index + 1) % controllerFeaturedGames.length);
    }, CONTROLLER_HERO_ROTATION_MS);
    return () => window.clearInterval(interval);
  }, [controllerMode, controllerFeaturedGames.length]);

  useEffect(() => {
    if (!controllerMode || games.length === 0) return;
    if (controllerGames.some((game) => game.id === selectedGameId)) return;
    onSelectGame(controllerGames[0]?.id ?? games[0].id);
  }, [controllerGames, controllerMode, games, onSelectGame, selectedGameId]);

  useEffect(() => {
    if (controllerStoreFilterItems.some((item) => item.id === controllerStoreFilterId)) return;
    setControllerStoreFilterId("library");
    setFocusedControllerStoreFilterIndex(0);
  }, [controllerStoreFilterId, controllerStoreFilterItems]);

  const selectedControllerGameIndex = Math.max(0, controllerGames.findIndex((game) => game.id === selectedGameId));
  const selectedControllerGame = controllerGames[selectedControllerGameIndex] ?? controllerGames[0];

  const focusControllerGame = (index: number): void => {
    if (controllerGames.length === 0) return;
    const nextIndex = Math.max(0, Math.min(index, controllerGames.length - 1));
    const nextGame = controllerGames[nextIndex];
    onSelectGame(nextGame.id);
    window.requestAnimationFrame(() => {
      const row = controllerGameRowRef.current;
      const card = row?.querySelector<HTMLElement>(`[data-controller-game-id="${CSS.escape(nextGame.id)}"]`);
      card?.scrollIntoView({ inline: "nearest", block: "nearest", behavior: "auto" });
    });
  };

  const cycleGameVariant = (game: GameInfo | undefined): void => {
    if (!game || game.variants.length <= 1) return;
    const activeVariantId = selectedVariantByGameId[game.id];
    const activeIndex = Math.max(0, game.variants.findIndex((variant) => variant.id === activeVariantId));
    const nextVariant = game.variants[(activeIndex + 1) % game.variants.length];
    if (nextVariant) onSelectGameVariant(game.id, nextVariant.id);
  };

  const cycleSelectedVariant = (): void => {
    cycleGameVariant(selectedControllerGame);
  };

  const cycleControllerStoreFilter = (): void => {
    if (controllerStoreFilterItems.length <= 1) return;
    const activeIndex = Math.max(0, controllerStoreFilterItems.findIndex((item) => item.id === controllerStoreFilterId));
    const nextItem = controllerStoreFilterItems[(activeIndex + 1) % controllerStoreFilterItems.length];
    setControllerStoreFilterId(nextItem.id);
    setFocusedControllerStoreFilterIndex((activeIndex + 1) % controllerStoreFilterItems.length);
    setControllerHeroIndex(0);
  };

  const showControllerStoreFilterOverlay = (): void => {
    const activeIndex = Math.max(0, controllerStoreFilterItems.findIndex((item) => item.id === controllerStoreFilterId));
    setFocusedControllerStoreFilterIndex(activeIndex);
    setControllerStoreFilterOpen(true);
  };

  const moveControllerStoreFilterFocusBy = (delta: number): void => {
    if (controllerStoreFilterItems.length === 0) return;
    setFocusedControllerStoreFilterIndex((index) => Math.max(0, Math.min(index + delta, controllerStoreFilterItems.length - 1)));
  };

  const hideControllerStoreFilterOverlay = (applySelection: boolean): void => {
    if (applySelection) {
      const item = controllerStoreFilterItems[focusedControllerStoreFilterIndex] ?? controllerStoreFilterItems[0];
      if (item) {
        setControllerStoreFilterId(item.id);
        setControllerHeroIndex(0);
      }
    }
    setControllerStoreFilterOpen(false);
  };

  useEffect(() => {
    controllerInputStateRef.current = {
      detailsGame,
      selectedControllerGame,
      selectedControllerGameIndex,
      controllerStoreFilterOpen,
      focusedControllerStoreFilterIndex,
      controllerStoreFilterItems,
      focusControllerGame,
      cycleSelectedVariant,
      cycleControllerStoreFilter,
      moveControllerStoreFilterFocusBy,
      hideControllerStoreFilterOverlay,
      showControllerStoreFilterOverlay,
      onPlayGame,
    };
  }, [controllerStoreFilterItems, controllerStoreFilterOpen, detailsGame, focusedControllerStoreFilterIndex, focusControllerGame, cycleSelectedVariant, cycleControllerStoreFilter, moveControllerStoreFilterFocusBy, hideControllerStoreFilterOverlay, showControllerStoreFilterOverlay, onPlayGame, selectedControllerGame, selectedControllerGameIndex]);

  useEffect(() => {
    if (!controllerMode) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (detailsGame) {
        if (event.key === "Escape" || event.key.toLowerCase() === "b") {
          event.preventDefault();
          setDetailsGame(null);
        }
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onPlayGame(detailsGame);
        }
        return;
      }
      if (controllerSearchOpen) {
        if (event.key === "Escape") {
          event.preventDefault();
          setControllerSearchOpen(false);
        }
        return;
      }
      if (event.key === "ArrowLeft") {
        event.preventDefault();
        focusControllerGame(selectedControllerGameIndex - 1);
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        focusControllerGame(selectedControllerGameIndex + 1);
      } else if (event.key === "ArrowDown") {
        event.preventDefault();
        cycleSelectedVariant();
      } else if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        if (selectedControllerGame) onPlayGame(selectedControllerGame);
      } else if (event.key.toLowerCase() === "x") {
        event.preventDefault();
        setControllerSearchOpen(true);
      } else if (event.key.toLowerCase() === "b" || event.key === "Escape") {
        event.preventDefault();
        onPreviousControllerPage?.();
      } else if (event.key === "[") {
        event.preventDefault();
        onPreviousControllerPage?.();
      } else if (event.key === "]") {
        event.preventDefault();
        onNextControllerPage?.();
      } else if (event.key.toLowerCase() === "i" || event.key.toLowerCase() === "m") {
        event.preventDefault();
        if (selectedControllerGame) setDetailsGame(selectedControllerGame);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [controllerMode, controllerSearchOpen, detailsGame, onNextControllerPage, onPlayGame, onPreviousControllerPage, selectedControllerGame, selectedControllerGameIndex]);

  useEffect(() => {
    if (!controllerMode) return;
    const readButtons = (): number => {
      const pad = navigator.getGamepads?.().find((gamepad): gamepad is Gamepad => Boolean(gamepad));
      return readControllerGamepadButtons(pad);
    };

    const handleGamepadFrame = () => {
      const buttons = readButtons();
      let pressed = buttons & ~gamepadPreviousButtonsRef.current;
      const released = gamepadPreviousButtonsRef.current & ~buttons;
      const moveMask = controllerButton.up | controllerButton.down | controllerButton.left | controllerButton.right;
      const yButton = controllerButton.north;
      const now = performance.now();
      const activeMoves = buttons & moveMask;
      const pressedMoves = pressed & moveMask;
      if (pressedMoves) {
        gamepadLastMoveAtRef.current = now;
      } else if (activeMoves && now - gamepadLastMoveAtRef.current > CONTROLLER_MOVE_REPEAT_MS) {
        pressed |= activeMoves;
        gamepadLastMoveAtRef.current = now;
      }

      const {
        detailsGame: currentDetailsGame,
        selectedControllerGame: currentSelectedGame,
        selectedControllerGameIndex: currentSelectedIndex,
        controllerStoreFilterOpen: storeFilterOpen,
        focusControllerGame: focusGame,
        cycleSelectedVariant: cycleVariant,
        cycleControllerStoreFilter: cycleStoreFilter,
        moveControllerStoreFilterFocusBy: moveStoreFilter,
        hideControllerStoreFilterOverlay: hideStoreFilter,
        showControllerStoreFilterOverlay: showStoreFilter,
        onPlayGame: playGame,
      } = controllerInputStateRef.current;

      if (pressed & yButton) {
        controllerYPressedAtRef.current = now;
        controllerYConsumedByHoldRef.current = false;
      }

      if ((buttons & yButton) && !controllerYConsumedByHoldRef.current && now - controllerYPressedAtRef.current >= CONTROLLER_Y_HOLD_MS) {
        controllerYConsumedByHoldRef.current = true;
        showStoreFilter();
      }

      if (controllerSearchOpen) {
        if (pressed & controllerButton.east) setControllerSearchOpen(false);
        gamepadPreviousButtonsRef.current = buttons;
        gamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
        return;
      }

      if (storeFilterOpen) {
        if (pressed & controllerButton.up) moveStoreFilter(-1);
        if (pressed & controllerButton.down) moveStoreFilter(1);
        if (pressed & controllerButton.east) hideStoreFilter(false);
        if (released & yButton) hideStoreFilter(true);
        gamepadPreviousButtonsRef.current = buttons;
        gamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
        return;
      }

      if (currentDetailsGame) {
        if (pressed & controllerButton.south) playGame(currentDetailsGame);
        if (pressed & controllerButton.east) setDetailsGame(null);
      } else {
        if ((released & yButton) && !controllerYConsumedByHoldRef.current) cycleStoreFilter();
        if (pressed & controllerButton.south) {
          if (currentSelectedGame) playGame(currentSelectedGame);
        }
        if (pressed & controllerButton.east) onPreviousControllerPage?.();
        if (pressed & controllerButton.west) setControllerSearchOpen(true);
        if (pressed & controllerButton.leftShoulder) onPreviousControllerPage?.();
        if (pressed & controllerButton.rightShoulder) onNextControllerPage?.();
        if (pressed & controllerButton.menu) {
          if (currentSelectedGame) setDetailsGame(currentSelectedGame);
        }
        if (pressed & controllerButton.left) focusGame(currentSelectedIndex - 1);
        if (pressed & controllerButton.right) focusGame(currentSelectedIndex + 1);
        if (pressed & controllerButton.down) cycleVariant();
      }
      gamepadPreviousButtonsRef.current = buttons;

      gamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };

    const startGamepadNavigation = () => {
      if (gamepadFrameRef.current !== null) return;
      gamepadPreviousButtonsRef.current = readButtons();
      gamepadLastMoveAtRef.current = performance.now();
      gamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };

    const stopGamepadNavigation = () => {
      if (gamepadFrameRef.current !== null) {
        window.cancelAnimationFrame(gamepadFrameRef.current);
        gamepadFrameRef.current = null;
      }
      gamepadPreviousButtonsRef.current = 0;
      gamepadLastMoveAtRef.current = 0;
    };

    const handleDisconnect = () => {
      const hasConnectedPad = navigator.getGamepads?.().some(Boolean) ?? false;
      if (!hasConnectedPad) stopGamepadNavigation();
    };

    window.addEventListener("gamepadconnected", startGamepadNavigation);
    window.addEventListener("gamepaddisconnected", handleDisconnect);
    startGamepadNavigation();

    return () => {
      window.removeEventListener("gamepadconnected", startGamepadNavigation);
      window.removeEventListener("gamepaddisconnected", handleDisconnect);
      stopGamepadNavigation();
    };
  }, [controllerMode, controllerSearchOpen, onNextControllerPage, onPreviousControllerPage]);

  const libraryGridItems = useMemo(
    () => visibleLibraryGames.map((game) => (
      <div key={game.id} className="library-game-wrapper">
        <GameCardListItem
          game={game}
          isSelected={game.id === selectedGameId}
          selectedVariantId={selectedVariantByGameId[game.id]}
          actionsRef={catalogActionsRef}
        />
        {game.lastPlayed && (
          <div className="library-last-played">
            <Clock size={12} />
            <span>{formatCatalogLastPlayed(t, game.lastPlayed)}</span>
          </div>
        )}
      </div>
    )),
    [catalogActionsRef, selectedGameId, selectedVariantByGameId, t, visibleLibraryGames],
  );

  if (controllerMode) {
    const featuredGame = controllerFeaturedGames[controllerHeroIndex] ?? selectedControllerGame;
    const heroImageUrl = featuredGame ? getControllerHeroBackgroundCandidates(featuredGame)[0] : undefined;
    const heroLogoUrl = featuredGame ? getControllerHeroLogoUrl(featuredGame) : undefined;
    const heroSelectedVariantId = featuredGame ? selectedVariantByGameId[featuredGame.id] : undefined;
    const heroStoreLabel = featuredGame ? getSelectedVariantStoreLabel(featuredGame, selectedVariantByGameId[featuredGame.id], t("library.storeNotListed")) : "";
    const featuredGameHasActiveSession = featuredGame ? gameMatchesActiveSession(featuredGame, activeSessionAppIds) : false;
    const heroShouldBuy = Boolean(featuredGame && !featuredGameHasActiveSession && !featuredGame.isInLibrary);
    const dotCount = Math.min(Math.max(controllerFeaturedGames.length, 1), 6);
    const activeDotIndex = dotCount > 0 && controllerFeaturedGames.length > 0 ? Math.min(controllerHeroIndex, dotCount - 1) : 0;

    return (
      <div className="library-page controller-library-page">
        {isLoading ? (
          <div className="library-empty-state controller-library-empty">
            <Loader2 className="library-spinner" size={54} />
            <p>{t("library.empty.loadingLibrary")}</p>
          </div>
        ) : libraryCount === 0 ? (
          <div className="library-empty-state controller-library-empty">
            <Gamepad2 className="library-empty-icon" size={64} />
            <h3>{t("library.empty.libraryEmpty")}</h3>
            <p>{t("library.empty.ownedGamesAppearHere")}</p>
          </div>
        ) : featuredGame ? (
          <>
            <section className="controller-hero" aria-label={featuredGame.title}>
              <AnimatePresence initial={false} mode="popLayout">
                {heroImageUrl ? (
                  <m.img
                    key={heroImageUrl}
                    src={heroImageUrl}
                    alt=""
                    className="controller-hero-image"
                    initial={{ opacity: 0, scale: 1.035 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 1.015 }}
                    transition={pageTransition}
                  />
                ) : (
                  <m.div
                    key="controller-library-hero-placeholder"
                    className="controller-hero-placeholder"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={pageTransition}
                  />
                )}
              </AnimatePresence>
              <div className="controller-hero-scrim" />
              <AnimatePresence initial={false} mode="wait">
                <m.div
                  key={featuredGame.id}
                  className="controller-hero-content"
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={pageTransition}
                >
                  {heroLogoUrl ? (
                    <img src={heroLogoUrl} alt={featuredGame.title} className="controller-hero-logo" />
                  ) : (
                    <h1>{featuredGame.title}</h1>
                  )}
                  <div className="controller-hero-actions">
                    <button
                      type="button"
                      className="controller-primary-action"
                      onClick={() => {
                        if (heroShouldBuy) {
                          onBuyGame?.(featuredGame, heroSelectedVariantId);
                          return;
                        }
                        onPlayGame(featuredGame);
                      }}
                    >
                      {featuredGameHasActiveSession ? t("app.actions.resume") : heroShouldBuy ? t("app.actions.buy") : t("app.actions.play")}
                    </button>
                    {heroStoreLabel && <span className="controller-hero-variant-pill">{heroStoreLabel}</span>}
                    <button type="button" className="controller-icon-action" aria-label={t("library.moreOptions")} onClick={() => cycleGameVariant(featuredGame)}>
                      <MoreHorizontal size={30} />
                    </button>
                  </div>
                </m.div>
              </AnimatePresence>
            </section>

            <div className="controller-hero-dots" aria-hidden="true">
              {Array.from({ length: dotCount }).map((_, index) => (
                <span key={index} className={index === activeDotIndex ? "active" : ""} />
              ))}
            </div>

            <section className="controller-library-strip" aria-label={t("library.title")}> 
              <div className="controller-library-heading">
                <h2>{t("library.controllerTitle")}</h2>
                <span>{t("library.gameCount", { count: controllerGames.length })}</span>
              </div>
              {controllerGames.length === 0 ? (
                <div className="library-empty-state controller-library-empty controller-library-empty--compact">
                  <Search className="library-empty-icon" size={44} />
                  <h3>{t("library.empty.noGamesFound")}</h3>
                  <p>{t("library.empty.noGamesMatch", { query: searchQuery })}</p>
                </div>
              ) : (
                <div className="controller-game-row" ref={controllerGameRowRef}>
                  {controllerGames.map((game) => (
                    <div key={game.id} className="controller-library-card" data-controller-game-id={game.id}>
                      <ControllerGameCard
                        game={game}
                        isSelected={game.id === selectedGameId}
                        onSelect={() => onSelectGame(game.id)}
                        onPlay={() => onPlayGame(game)}
                        selectedVariantId={selectedVariantByGameId[game.id]}
                      />
                    </div>
                  ))}
                </div>
              )}
            </section>

            <div className="controller-bottom-hints" aria-hidden="true">
              <div className="controller-hint"><span className="controller-button controller-button--a">A</span><span>{t("app.actions.select")}</span></div>
              <div className="controller-hint"><span className="controller-button controller-button--b">B</span><span>{t("app.actions.back")}</span></div>
              <div className="controller-hint"><span className="controller-button controller-button--y">Y</span><span>{t("library.filter")}</span></div>
              <div className="controller-hint"><span className="controller-button controller-button--x">X</span><span>{t("app.actions.search")}</span></div>
              <div className="controller-hint controller-hint--more"><span className="controller-menu-button"><Menu size={22} /></span><span>{t("library.moreOptions")}</span></div>
            </div>

            <AnimatePresence initial={false}>
              {controllerStoreFilterOpen && (
                <m.div
                  className="controller-store-filter-overlay"
                  role="dialog"
                  aria-modal="true"
                  aria-label={t("library.chooseStore")}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={pageTransition}
                >
                  <m.div
                    className="controller-store-filter-panel"
                    initial={{ opacity: 0, y: 16, scale: 0.985 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: 10, scale: 0.99 }}
                    transition={panelSpring}
                  >
                  <span className="controller-store-filter-eyebrow">{t("library.storeFilter")}</span>
                  <h3>{t("library.chooseStore")}</h3>
                  <p>{t("library.storeFilterHint")}</p>
                  <div className="controller-store-filter-options">
                    {controllerStoreFilterItems.map((item, index) => (
                      <button
                        key={item.id}
                        type="button"
                        className={`controller-store-filter-option${index === focusedControllerStoreFilterIndex ? " focused" : ""}`}
                        onClick={() => {
                          setFocusedControllerStoreFilterIndex(index);
                          setControllerStoreFilterId(item.id);
                          setControllerStoreFilterOpen(false);
                        }}
                      >
                        {item.title}
                      </button>
                    ))}
                  </div>
                  </m.div>
                </m.div>
              )}
            </AnimatePresence>

            <AnimatePresence initial={false}>
              {controllerSearchOpen && (
                <m.div
                  className="controller-search-overlay"
                  role="dialog"
                  aria-modal="true"
                  aria-label={t("app.actions.search")}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={pageTransition}
                >
                  <m.div
                    className="controller-search-panel"
                    initial={{ opacity: 0, y: 16, scale: 0.985 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: 10, scale: 0.99 }}
                    transition={panelSpring}
                  >
                  <span className="controller-search-eyebrow">{t("app.actions.search")}</span>
                  <input
                    ref={controllerSearchInputRef}
                    type="text"
                    value={searchQuery}
                    onChange={(event) => onSearchChange(event.target.value)}
                    placeholder={t("library.searchPlaceholder")}
                    className="controller-search-input"
                  />
                  <p>{t("app.actions.back")}</p>
                  </m.div>
                </m.div>
              )}
            </AnimatePresence>

            <AnimatePresence initial={false}>
              {detailsGame && (
                <m.div
                  className="controller-details-overlay"
                  role="dialog"
                  aria-modal="true"
                  aria-label={detailsGame.title}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={pageTransition}
                >
                  <m.div
                    className="controller-details-panel"
                    initial={{ opacity: 0, y: 18, scale: 0.985 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: 10, scale: 0.99 }}
                    transition={panelSpring}
                  >
                  <h3>{detailsGame.title}</h3>
                  <p className="controller-details-store">{t("library.selectedStore", { store: getGameStoreSummary(detailsGame, t("library.storeNotListed")) })}</p>
                  <p className="controller-details-body">{detailsGame.description || detailsGame.longDescription || detailsGame.featureLabels?.join(" / ") || t("library.loadingGameDetails")}</p>
                  <div className="controller-details-meta">
                    {detailsGame.developerName && <span>{t("library.developer", { developer: detailsGame.developerName })}</span>}
                    {detailsGame.publisherName && <span>{t("library.publisher", { publisher: detailsGame.publisherName })}</span>}
                    {getPlayerSummary(detailsGame) && <span>{t("library.players", { players: getPlayerSummary(detailsGame) })}</span>}
                    {detailsGame.supportedControls?.length ? <span>{t("library.controls", { controls: detailsGame.supportedControls.slice(0, 4).join(", ") })}</span> : null}
                    {detailsGame.nvidiaTech?.length ? <span>{t("library.nvidiaTech", { tech: detailsGame.nvidiaTech.slice(0, 4).join(", ") })}</span> : null}
                    {detailsGame.genres?.length ? <span>{t("library.genres", { genres: detailsGame.genres.slice(0, 4).join(", ") })}</span> : null}
                    {detailsGame.contentRatings?.length ? <span>{t("library.rating", { rating: detailsGame.contentRatings.slice(0, 2).join(", ") })}</span> : null}
                  </div>
                  <div className="controller-details-actions">
                    <button type="button" className="controller-primary-action" onClick={() => onPlayGame(detailsGame)}>{t("app.actions.play")}</button>
                    <button type="button" className="controller-secondary-action" onClick={() => setDetailsGame(null)}>{t("app.actions.back")}</button>
                  </div>
                  </m.div>
                </m.div>
              )}
            </AnimatePresence>
          </>
        ) : null}
      </div>
    );
  }

  return (
    <div className="library-page">
      <header className="library-toolbar">
        <div className="library-title">
          <Library className="library-title-icon" size={22} />
          <h1>{t("library.title")}</h1>
        </div>

        <div className="library-search">
          <Search className="library-search-icon" size={16} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder={t("library.searchPlaceholder")}
            className="library-search-input"
          />
        </div>

        {libraryFilterGroups.length > 0 && (
          <details className="library-filter-dropdown">
            <summary className="library-filter-dropdown-trigger">
              <span className="library-filter-dropdown-label">
                <Filter size={14} />
                {t("library.filters")}
              </span>
              {selectedLibraryFilterIds.length > 0 && <span className="library-filter-dropdown-count">{selectedLibraryFilterIds.length}</span>}
              <ChevronDown size={14} className="library-filter-dropdown-chevron" />
            </summary>
            <div className="library-filter-dropdown-menu">
              {libraryFilterGroups.map((group) => (
                <div key={group.id} className="library-filter-dropdown-group">
                  <div className="library-filter-group-label">{group.label}</div>
                  <div className="library-filter-chips">
                    {group.options.map((option) => {
                      const active = selectedLibraryFilterIds.includes(option.id);
                      return (
                        <button
                          key={option.id}
                          type="button"
                          className={`library-filter-chip ${active ? "active" : ""}`}
                          onClick={() => toggleLibraryFilter(option.id)}
                          aria-pressed={active}
                        >
                          <span>{option.label}</span>
                          <span className="library-filter-chip-count">{option.count}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </details>
        )}

        {sortOptions.length > 0 && (
          <div className="library-sort">
            <ArrowUpDown size={14} />
            <SelectDropdown
              value={selectedSortId}
              options={sortOptions.map((option) => ({ value: option.id, label: option.label }))}
              onChange={onSortChange}
              ariaLabel={t("library.sortAriaLabel")}
            />
          </div>
        )}

        <span className="library-count">{libraryCountLabel}</span>
      </header>

      <AnimatePresence initial={false}>
        {activeLibraryFilterOptions.length > 0 && (
          <m.div
            className="library-active-filters"
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={pageTransition}
          >
            <span className="library-active-filter-label">{t("library.activeFilters")}</span>
            {activeLibraryFilterOptions.map((option) => (
              <button
                key={option.id}
                type="button"
                className="library-active-filter-chip"
                onClick={() => toggleLibraryFilter(option.id)}
                aria-label={t("library.removeFilter", { filter: option.label })}
              >
                <span>{option.label}</span>
                <X size={12} />
              </button>
            ))}
            <button type="button" className="library-clear-filters" onClick={clearLibraryFilters}>
              {t("library.clearFilters")}
            </button>
          </m.div>
        )}
      </AnimatePresence>

      <div className="library-grid-area">
        {isLoading ? (
          <div className="library-empty-state">
            <Loader2 className="library-spinner" size={36} />
            <p>{t("library.empty.loadingLibrary")}</p>
          </div>
        ) : libraryCount === 0 ? (
          <div className="library-empty-state">
            <Gamepad2 className="library-empty-icon" size={44} />
            <h3>{t("library.empty.libraryEmpty")}</h3>
            <p>{t("library.empty.ownedGamesAppearHere")}</p>
          </div>
        ) : visibleLibraryGames.length === 0 ? (
          <div className="library-empty-state">
            <Search className="library-empty-icon" size={44} />
            <h3>{hasActiveLibraryFilters && !librarySearchHasQuery ? t("library.empty.noFilteredGames") : t("library.empty.noGamesFound")}</h3>
            <p>
              {librarySearchHasQuery
                ? t("library.empty.noGamesMatch", { query: searchQuery })
                : hasActiveLibraryFilters
                  ? t("library.empty.tryAdjustingFilters")
                : t("library.empty.noGamesMatch", { query: searchQuery })}
            </p>
          </div>
        ) : (
          <div className="game-grid">
            {libraryGridItems}
          </div>
        )}
      </div>
    </div>
  );
});
