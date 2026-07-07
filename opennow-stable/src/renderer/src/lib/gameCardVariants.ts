import type { GameInfo, GameVariant } from "@shared/gfn";

export function getResolvedSelectedVariantId(game: GameInfo, selectedVariantId?: string): string | undefined {
  if (selectedVariantId && game.variants.some((variant) => variant.id === selectedVariantId)) {
    return selectedVariantId;
  }

  return game.variants[game.selectedVariantIndex]?.id ?? game.variants[0]?.id;
}

export function getActiveGameVariant(game: GameInfo, selectedVariantId?: string): GameVariant | undefined {
  const resolvedSelectedVariantId = getResolvedSelectedVariantId(game, selectedVariantId);
  return game.variants.find((variant) => variant.id === resolvedSelectedVariantId) ?? game.variants[0];
}
