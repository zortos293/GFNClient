import { useEffect, useRef, useState } from "react";
import type { JSX, ReactNode } from "react";
import type { GameInfo } from "@shared/gfn";
import {
  getControllerHeroBackgroundCandidates,
  getControllerHeroLogoUrl,
  getPrimaryGenre,
  getPrimaryStoreName,
} from "../../lib/controllerCatalogUi";

export interface ConsoleBillboardProps {
  game: GameInfo;
  selectedVariantId?: string;
  /** Extra chips rendered after store and genre. */
  extraChips?: Array<{ label: string; accent?: boolean }>;
  actions: ReactNode;
  dotCount?: number;
  activeDotIndex?: number;
  fallbackGenreLabel: string;
}

/**
 * Full-bleed hero. Two stacked <img> layers cross-fade via CSS: the incoming
 * layer runs `console-hero-in` while the outgoing one fades out and is dropped
 * on animationend. This replaces an `AnimatePresence mode="popLayout"` — see
 * the note on ConsoleRow about why the console tree carries no motion.
 *
 * The copy block is keyed on the game id so React remounts it, which restarts
 * its CSS entrance without any timers.
 */
export function ConsoleBillboard({
  game,
  selectedVariantId,
  extraChips = [],
  actions,
  dotCount = 0,
  activeDotIndex = 0,
  fallbackGenreLabel,
}: ConsoleBillboardProps): JSX.Element {
  const artUrl = getControllerHeroBackgroundCandidates(game)[0];
  const logoUrl = getControllerHeroLogoUrl(game);

  const [previousArtUrl, setPreviousArtUrl] = useState<string | undefined>(undefined);
  const renderedArtUrlRef = useRef(artUrl);

  useEffect(() => {
    if (renderedArtUrlRef.current === artUrl) return;
    setPreviousArtUrl(renderedArtUrlRef.current);
    renderedArtUrlRef.current = artUrl;
  }, [artUrl]);

  const storeName = getPrimaryStoreName(game, selectedVariantId);
  const genre = getPrimaryGenre(game) ?? fallbackGenreLabel;

  return (
    <section className="console-billboard" aria-label={game.title}>
      {previousArtUrl && (
        <img
          key={previousArtUrl}
          className="console-billboard-art console-billboard-art--leaving"
          src={previousArtUrl}
          alt=""
          aria-hidden="true"
          onAnimationEnd={() => setPreviousArtUrl(undefined)}
        />
      )}
      {artUrl ? (
        <img
          key={artUrl}
          className="console-billboard-art console-billboard-art--entering"
          src={artUrl}
          alt=""
          decoding="async"
        />
      ) : (
        <div className="console-billboard-placeholder" />
      )}

      <div className="console-billboard-scrim" />

      <div className="console-billboard-copy" key={game.id}>
        {logoUrl
          ? <img className="console-billboard-logo" src={logoUrl} alt={game.title} decoding="async" />
          : <h1 className="console-billboard-title">{game.title}</h1>}

        <p className="console-billboard-meta">
          <span className="console-billboard-meta-chip">{storeName}</span>
          <span className="console-billboard-meta-chip">{genre}</span>
          {extraChips.map((chip) => (
            <span
              key={chip.label}
              className={`console-billboard-meta-chip${chip.accent ? " console-billboard-meta-chip--accent" : ""}`}
            >
              {chip.label}
            </span>
          ))}
        </p>

        {game.description && <p className="console-billboard-description">{game.description}</p>}

        <div className="console-billboard-actions">{actions}</div>
      </div>

      {dotCount > 1 && (
        <div className="console-billboard-dots" aria-hidden="true">
          {Array.from({ length: dotCount }).map((_, index) => (
            <span key={index} className={index === activeDotIndex ? "active" : ""} />
          ))}
        </div>
      )}
    </section>
  );
}
