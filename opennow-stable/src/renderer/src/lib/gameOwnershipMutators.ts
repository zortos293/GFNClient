import type { GameInfo, GamePanelResult, GameVariant } from "@shared/gfn";

export function gameIdentityMatches(left: GameInfo, right: GameInfo): boolean {
  if (left.uuid && right.uuid && left.uuid === right.uuid) return true;
  if (left.id && right.id && left.id === right.id) return true;
  if (left.launchAppId && right.launchAppId && left.launchAppId === right.launchAppId) return true;
  return left.title.trim().length > 0 && left.title.localeCompare(right.title, undefined, { sensitivity: "accent" }) === 0;
}

export function markVariantOwned(variant: GameVariant, selected: boolean): GameVariant {
  return {
    ...variant,
    inLibrary: true,
    librarySelected: selected,
    libraryStatus: "MANUAL",
  };
}

export function markGameVariantOwned(game: GameInfo, variantId: string): GameInfo {
  const selectedVariantIndex = game.variants.findIndex((variant) => variant.id === variantId);
  if (selectedVariantIndex < 0) {
    return game;
  }

  return {
    ...game,
    isInLibrary: true,
    selectedVariantIndex,
    variants: game.variants.map((variant, index) => (
      index === selectedVariantIndex
        ? markVariantOwned(variant, true)
        : { ...variant, librarySelected: false }
    )),
  };
}

export function markGameOwnedInList(games: GameInfo[], target: GameInfo, variantId: string): GameInfo[] {
  let changed = false;
  const next = games.map((game) => {
    if (!gameIdentityMatches(game, target)) return game;
    if (!game.variants.some((variant) => variant.id === variantId)) return game;
    changed = true;
    return markGameVariantOwned(game, variantId);
  });
  return changed ? next : games;
}

export function upsertMarkedOwnedLibraryGame(games: GameInfo[], target: GameInfo, variantId: string): GameInfo[] {
  let changed = false;
  const next = games.map((game) => {
    if (!gameIdentityMatches(game, target)) return game;
    if (!game.variants.some((variant) => variant.id === variantId)) return game;
    changed = true;
    return markGameVariantOwned(game, variantId);
  });
  return changed ? next : [markGameVariantOwned(target, variantId), ...games];
}

export function markGameOwnedInPanels(panels: GamePanelResult[], target: GameInfo, variantId: string): GamePanelResult[] {
  let changed = false;
  const next = panels.map((panel) => ({
    ...panel,
    sections: panel.sections.map((section) => ({
      ...section,
      games: section.games.map((game) => {
        if (!gameIdentityMatches(game, target)) return game;
        if (!game.variants.some((variant) => variant.id === variantId)) return game;
        changed = true;
        return markGameVariantOwned(game, variantId);
      }),
    })),
  }));
  return changed ? next : panels;
}

export function getLibrarySelectedVariantId(storeGame: GameInfo, libraryGames: GameInfo[]): string | undefined {
  const libraryGame = libraryGames.find((candidate) => gameIdentityMatches(storeGame, candidate));
  const libraryVariant = libraryGame?.variants.find((variant) => variant.librarySelected)
    ?? libraryGame?.variants.find((variant) => variant.inLibrary)
    ?? libraryGame?.variants[0];
  if (!libraryVariant) return undefined;

  const sameIdVariant = storeGame.variants.find((variant) => variant.id === libraryVariant.id);
  if (sameIdVariant) return sameIdVariant.id;

  const sameStoreVariant = storeGame.variants.find((variant) => variant.store.localeCompare(libraryVariant.store, undefined, { sensitivity: "accent" }) === 0);
  return sameStoreVariant?.id;
}

export function flattenStorePanelGames(panels: GamePanelResult[]): GameInfo[] {
  return panels.flatMap((panel) => panel.sections.flatMap((section) => section.games));
}
