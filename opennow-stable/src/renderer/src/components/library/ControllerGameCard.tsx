import type { JSX } from "react";
import type { GameInfo } from "@shared/gfn";
import { getGameLogoUrl } from "../../lib/controllerCatalogUi";
import { getStoreDisplayName, getStoreIconComponent } from "../GameCard";

export function ControllerGameCard({
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
  const variants = game.variants ?? [];
  const selectedVariant = variants.find((variant) => variant.id === selectedVariantId)
    ?? variants[game.selectedVariantIndex]
    ?? variants[0];
  const store = selectedVariant?.store ?? game.availableStores?.[0] ?? "";
  const StoreIcon = getStoreIconComponent(store);
  const logoUrl = getGameLogoUrl(game);

  return (
    <button
      type="button"
      className={`controller-native-card${isSelected ? " selected" : ""}`}
      onClick={onSelect}
      onDoubleClick={onPlay}
      aria-label={game.title}
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
    </button>
  );
}
