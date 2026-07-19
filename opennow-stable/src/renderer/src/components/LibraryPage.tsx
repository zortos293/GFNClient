import { Library, Search, Clock, Gamepad2, ArrowUpDown, Filter, ChevronDown, X } from "lucide-react";
import { memo, useEffect, useMemo, useRef, useState } from "react";
import type { JSX } from "react";
import { AnimatePresence, m } from "motion/react";
import type { CatalogSortOption, GameInfo } from "@shared/gfn";
import { GameCardListItem, useCatalogCardActionsRef } from "./GameCardListItem";
import type { PlaytimeData } from "../lib/gameCatalog";
import { getControllerFeaturedGames, getControllerHeroBackgroundCandidates } from "../lib/controllerCatalogUi";
import {
  gameMatchesLibraryFilters,
  gameMatchesStoreFilter,
  getControllerStoreFilterItems,
  getLibraryFilterGroups,
  getLibraryFilterOptionById,
  type ControllerStoreFilterItem,
  type LibraryFilterOption,
} from "../lib/libraryFilters";
import { useTranslation } from "../i18n";
import { formatCatalogLastPlayed } from "../utils/lastPlayedFormat";
import { controllerButton, readControllerGamepadButtons } from "../utils/controllerGamepad";
import { pageTransition } from "./MotionProvider";
import { SelectDropdown } from "./ui/SelectDropdown";
import { LibraryControllerView } from "./library/LibraryControllerView";
import { MotionSpinner } from "./MotionSpinner";

const CONTROLLER_HERO_ROTATION_MS = 8000;
const CONTROLLER_MOVE_REPEAT_MS = 140;
const CONTROLLER_Y_HOLD_MS = 350;

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
  surfaceActive?: boolean;
  featuredGames?: GameInfo[];
  activeSessionAppIds?: number[];
  onPreviousControllerPage?: () => void;
  onNextControllerPage?: () => void;
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
  surfaceActive = true,
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
  const pendingScrollFrameRef = useRef<number | null>(null);
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
    if (surfaceActive) return undefined;
    if (pendingScrollFrameRef.current !== null) {
      window.cancelAnimationFrame(pendingScrollFrameRef.current);
      pendingScrollFrameRef.current = null;
    }
    gamepadPreviousButtonsRef.current = 0;
    gamepadLastMoveAtRef.current = 0;
    controllerYPressedAtRef.current = 0;
    controllerYConsumedByHoldRef.current = false;
    return undefined;
  }, [surfaceActive]);

  useEffect(() => () => {
    if (pendingScrollFrameRef.current !== null) {
      window.cancelAnimationFrame(pendingScrollFrameRef.current);
    }
  }, []);

  useEffect(() => {
    if (!controllerMode || !surfaceActive || !controllerSearchOpen) return;
    controllerSearchInputRef.current?.focus();
  }, [controllerMode, controllerSearchOpen, surfaceActive]);

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
    if (!surfaceActive || controllerMode || visibleLibraryGames.length === 0) return;
    if (visibleLibraryGames.some((game) => game.id === selectedGameId)) return;
    onSelectGame(visibleLibraryGames[0].id);
  }, [controllerMode, onSelectGame, selectedGameId, surfaceActive, visibleLibraryGames]);

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
    if (!controllerMode || !surfaceActive) return;
    setControllerHeroIndex(0);
  }, [controllerMode, controllerStoreFilterId, controllerFeaturedGames.length, controllerFeaturedGames[0]?.id, surfaceActive]);

  useEffect(() => {
    if (controllerMode) return;
    gamepadPreviousButtonsRef.current = 0;
    gamepadLastMoveAtRef.current = 0;
  }, [controllerMode]);

  useEffect(() => {
    if (!controllerMode || !surfaceActive || controllerFeaturedGames.length <= 1) return;
    let cancelled = false;
    let advancing = false;
    const interval = window.setInterval(() => {
      if (advancing) return;
      advancing = true;
      const nextIndex = (controllerHeroIndex + 1) % controllerFeaturedGames.length;
      const nextGame = controllerFeaturedGames[nextIndex];
      const nextImageUrl = nextGame ? getControllerHeroBackgroundCandidates(nextGame)[0] : undefined;
      if (!nextImageUrl) {
        if (!cancelled) setControllerHeroIndex(nextIndex);
        advancing = false;
        return;
      }

      const image = new Image();
      image.src = nextImageUrl;
      void image.decode()
        .catch(() => undefined)
        .then(() => {
          if (!cancelled) setControllerHeroIndex(nextIndex);
        })
        .finally(() => {
          advancing = false;
        });
    }, CONTROLLER_HERO_ROTATION_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [controllerHeroIndex, controllerMode, controllerFeaturedGames, surfaceActive]);

  useEffect(() => {
    if (!controllerMode || !surfaceActive || games.length === 0) return;
    if (controllerGames.some((game) => game.id === selectedGameId)) return;
    onSelectGame(controllerGames[0]?.id ?? games[0].id);
  }, [controllerGames, controllerMode, games, onSelectGame, selectedGameId, surfaceActive]);

  useEffect(() => {
    if (controllerStoreFilterItems.some((item) => item.id === controllerStoreFilterId)) return;
    setControllerStoreFilterId("library");
    setFocusedControllerStoreFilterIndex(0);
  }, [controllerStoreFilterId, controllerStoreFilterItems]);

  const selectedControllerGameIndex = Math.max(0, controllerGames.findIndex((game) => game.id === selectedGameId));
  const selectedControllerGame = controllerGames[selectedControllerGameIndex] ?? controllerGames[0];

  const focusControllerGame = (index: number): void => {
    if (!surfaceActive || controllerGames.length === 0) return;
    const nextIndex = Math.max(0, Math.min(index, controllerGames.length - 1));
    const nextGame = controllerGames[nextIndex];
    onSelectGame(nextGame.id);
    if (pendingScrollFrameRef.current !== null) {
      window.cancelAnimationFrame(pendingScrollFrameRef.current);
    }
    pendingScrollFrameRef.current = window.requestAnimationFrame(() => {
      pendingScrollFrameRef.current = null;
      if (!surfaceActive) return;
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

  // Keep the gamepad poller on a stable ref snapshot. Do not list the inline
  // helpers as effect deps — they are recreated every render and only need to
  // be copied into the ref, not trigger another commit.
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

  useEffect(() => {
    if (!controllerMode || !surfaceActive) return;
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
  }, [controllerMode, controllerSearchOpen, detailsGame, onNextControllerPage, onPlayGame, onPreviousControllerPage, selectedControllerGame, selectedControllerGameIndex, surfaceActive]);

  useEffect(() => {
    if (!controllerMode || !surfaceActive) return;
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
  }, [controllerMode, controllerSearchOpen, onNextControllerPage, onPreviousControllerPage, surfaceActive]);

  const libraryGridItems = useMemo(
    () => visibleLibraryGames.map((game) => (
      <div key={game.id} className="library-game-wrapper">
        <GameCardListItem
          game={game}
          isSelected={game.id === selectedGameId}
          selectedVariantId={selectedVariantByGameId[game.id]}
          surface="library"
          actionsRef={catalogActionsRef}
        />
        <div
          className={`library-last-played${game.lastPlayed ? "" : " library-last-played--empty"}`}
          aria-hidden={game.lastPlayed ? undefined : true}
        >
          <Clock size={12} />
          <span>{game.lastPlayed ? formatCatalogLastPlayed(t, game.lastPlayed) : "—"}</span>
        </div>
      </div>
    )),
    [catalogActionsRef, selectedGameId, selectedVariantByGameId, t, visibleLibraryGames],
  );

  if (controllerMode) {
    const featuredGame = controllerFeaturedGames[controllerHeroIndex] ?? selectedControllerGame;
    return (
      <LibraryControllerView
        isLoading={isLoading}
        libraryCount={libraryCount}
        searchQuery={searchQuery}
        onSearchChange={onSearchChange}
        selectedGameId={selectedGameId}
        onSelectGame={onSelectGame}
        onPlayGame={onPlayGame}
        onBuyGame={onBuyGame}
        selectedVariantByGameId={selectedVariantByGameId}
        activeSessionAppIds={activeSessionAppIds}
        featuredGame={featuredGame}
        controllerFeaturedGames={controllerFeaturedGames}
        controllerHeroIndex={controllerHeroIndex}
        controllerGames={controllerGames}
        controllerGameRowRef={controllerGameRowRef}
        controllerStoreFilterOpen={controllerStoreFilterOpen}
        controllerStoreFilterItems={controllerStoreFilterItems}
        focusedControllerStoreFilterIndex={focusedControllerStoreFilterIndex}
        onFocusControllerStoreFilter={setFocusedControllerStoreFilterIndex}
        onSelectControllerStoreFilter={(itemId) => {
          setControllerStoreFilterId(itemId);
          setControllerStoreFilterOpen(false);
        }}
        controllerSearchOpen={controllerSearchOpen}
        controllerSearchInputRef={controllerSearchInputRef}
        detailsGame={detailsGame}
        onCloseDetails={() => setDetailsGame(null)}
        onCycleGameVariant={cycleGameVariant}
        onSelectHint={() => {
          if (selectedControllerGame) onPlayGame(selectedControllerGame);
        }}
        onBackHint={() => onPreviousControllerPage?.()}
        onFilterHint={showControllerStoreFilterOverlay}
        onSearchHint={() => setControllerSearchOpen(true)}
        onMoreOptionsHint={() => {
          if (selectedControllerGame) setDetailsGame(selectedControllerGame);
        }}
        onCloseSearchHint={() => setControllerSearchOpen(false)}
      />
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
            <MotionSpinner className="library-spinner" size={36} label={t("common.loading")} />
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
