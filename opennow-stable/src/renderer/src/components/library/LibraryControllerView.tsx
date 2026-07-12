import { Search, Gamepad2, MoreHorizontal, Menu } from "lucide-react";
import type { JSX, RefObject } from "react";
import type { GameInfo } from "@shared/gfn";
import {
  gameMatchesActiveSession,
  getControllerHeroBackgroundCandidates,
  getControllerHeroLogoUrl,
  getGameStoreSummary,
  getPlayerSummary,
  getSelectedVariantStoreLabel,
} from "../../lib/controllerCatalogUi";
import type { ControllerStoreFilterItem } from "../../lib/libraryFilters";
import { useTranslation } from "../../i18n";
import { ControllerGameCard } from "./ControllerGameCard";
import { MotionSpinner } from "../MotionSpinner";

export interface LibraryControllerViewProps {
  isLoading: boolean;
  libraryCount: number;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  selectedGameId: string;
  onSelectGame: (id: string) => void;
  onPlayGame: (game: GameInfo) => void;
  onBuyGame?: (game: GameInfo, selectedVariantId?: string) => void;
  selectedVariantByGameId: Record<string, string>;
  activeSessionAppIds: number[];
  featuredGame: GameInfo | undefined;
  controllerFeaturedGames: GameInfo[];
  controllerHeroIndex: number;
  controllerGames: GameInfo[];
  controllerGameRowRef: RefObject<HTMLDivElement | null>;
  controllerStoreFilterOpen: boolean;
  controllerStoreFilterItems: ControllerStoreFilterItem[];
  focusedControllerStoreFilterIndex: number;
  onFocusControllerStoreFilter: (index: number) => void;
  onSelectControllerStoreFilter: (itemId: string) => void;
  controllerSearchOpen: boolean;
  controllerSearchInputRef: RefObject<HTMLInputElement | null>;
  detailsGame: GameInfo | null;
  onCloseDetails: () => void;
  onCycleGameVariant: (game: GameInfo | undefined) => void;
}

/**
 * Controller-mode library shell. Intentionally avoids motion/AnimatePresence here:
 * those animations have been observed to crash the Chromium renderer in this
 * environment when combined with the library hero + card strip.
 */
export function LibraryControllerView({
  isLoading,
  libraryCount,
  searchQuery,
  onSearchChange,
  selectedGameId,
  onSelectGame,
  onPlayGame,
  onBuyGame,
  selectedVariantByGameId,
  activeSessionAppIds,
  featuredGame,
  controllerFeaturedGames,
  controllerHeroIndex,
  controllerGames,
  controllerGameRowRef,
  controllerStoreFilterOpen,
  controllerStoreFilterItems,
  focusedControllerStoreFilterIndex,
  onFocusControllerStoreFilter,
  onSelectControllerStoreFilter,
  controllerSearchOpen,
  controllerSearchInputRef,
  detailsGame,
  onCloseDetails,
  onCycleGameVariant,
}: LibraryControllerViewProps): JSX.Element {
  const { t } = useTranslation();
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
            <MotionSpinner className="library-spinner" size={54} label={t("common.loading")} />
          <p>{t("library.empty.loadingLibrary")}</p>
        </div>
      ) : libraryCount === 0 ? (
        <div className="library-empty-state controller-library-empty">
          <Gamepad2 className="library-empty-icon" size={64} />
          <h3>{t("library.empty.libraryEmpty")}</h3>
          <p>{t("library.empty.ownedGamesAppearHere")}</p>
        </div>
      ) : !featuredGame ? (
        <div className="library-empty-state controller-library-empty">
          <Search className="library-empty-icon" size={64} />
          <h3>{t("library.empty.noGamesFound")}</h3>
          <p>{t("library.empty.noGamesMatch", { query: searchQuery })}</p>
        </div>
      ) : (
        <>
          <section className="controller-hero" aria-label={featuredGame.title}>
            {heroImageUrl ? (
              <img src={heroImageUrl} alt="" className="controller-hero-image" />
            ) : (
              <div className="controller-hero-placeholder" />
            )}
            <div className="controller-hero-scrim" />
            <div className="controller-hero-content">
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
                <button type="button" className="controller-icon-action" aria-label={t("library.moreOptions")} onClick={() => onCycleGameVariant(featuredGame)}>
                  <MoreHorizontal size={30} />
                </button>
              </div>
            </div>
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

          {controllerStoreFilterOpen && (
            <div className="controller-store-filter-overlay" role="dialog" aria-modal="true" aria-label={t("library.chooseStore")}>
              <div className="controller-store-filter-panel">
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
                        onFocusControllerStoreFilter(index);
                        onSelectControllerStoreFilter(item.id);
                      }}
                    >
                      {item.title}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {controllerSearchOpen && (
            <div className="controller-search-overlay" role="dialog" aria-modal="true" aria-label={t("app.actions.search")}>
              <div className="controller-search-panel">
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
              </div>
            </div>
          )}

          {detailsGame && (
            <div className="controller-details-overlay" role="dialog" aria-modal="true" aria-label={detailsGame.title}>
              <div className="controller-details-panel">
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
                  <button type="button" className="controller-secondary-action" onClick={onCloseDetails}>{t("app.actions.back")}</button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
