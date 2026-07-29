import type { GameInfo } from "@shared/gfn";
import { getStoreDisplayName } from "../components/GameCard";

const CONTROLLER_HERO_BACKGROUND_KEYS = [
  "MARQUEE_HERO_IMAGE",
  "FEATURE_IMAGE",
  "HERO_IMAGE",
  "TV_BANNER",
  "KEY_ART",
  "KEY_IMAGE",
] as const;

export function appendUnique(values: string[], candidate: string | undefined): void {
  if (!candidate || values.includes(candidate)) return;
  values.push(candidate);
}

export function appendImageType(values: string[], game: GameInfo, type: string): void {
  for (const candidate of game.imageUrlsByType?.[type] ?? []) {
    appendUnique(values, candidate);
  }
}

export function gameMatchesActiveSession(game: GameInfo, activeSessionAppIds: number[]): boolean {
  if (activeSessionAppIds.length === 0) return false;
  const appIds = new Set(activeSessionAppIds.map(String));
  if (game.launchAppId && appIds.has(game.launchAppId)) return true;
  if (appIds.has(game.id)) return true;
  return game.variants.some((variant) => appIds.has(variant.id));
}

export function getControllerHeroBackgroundCandidates(game: GameInfo): string[] {
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

export function getControllerHeroLogoUrl(game: GameInfo): string | undefined {
  return game.imageUrlsByType?.GAME_LOGO?.find(Boolean);
}

export function getGameLogoUrl(game: GameInfo): string | undefined {
  return game.imageUrlsByType?.GAME_LOGO?.find(Boolean);
}

export function getControllerFeaturedGames(featuredGames: GameInfo[], fallbackGames: GameInfo[]): GameInfo[] {
  const source = featuredGames.length > 0 ? featuredGames : fallbackGames;
  return source.slice(0, 6);
}

export function getGameStoreSummary(game: GameInfo, fallback: string): string {
  const stores = [...new Set((game.availableStores?.length ? game.availableStores : game.variants.map((variant) => variant.store)).filter(Boolean))];
  if (stores.length === 0) return fallback;
  const visible = stores.slice(0, 3).join(", ");
  return stores.length > 3 ? `${visible} +${stores.length - 3}` : visible;
}

export function getSelectedVariantStoreLabel(game: GameInfo, selectedVariantId: string | undefined, fallback: string): string {
  const selectedVariant = game.variants.find((variant) => variant.id === selectedVariantId)
    ?? game.variants[game.selectedVariantIndex]
    ?? game.variants[0];
  return selectedVariant?.store ? getStoreDisplayName(selectedVariant.store) : fallback;
}

export function getPlayerSummary(game: GameInfo): string | null {
  const parts: string[] = [];
  if (game.maxLocalPlayers && game.maxLocalPlayers > 0) parts.push(`Local ${game.maxLocalPlayers}`);
  if (game.maxOnlinePlayers && game.maxOnlinePlayers > 0) parts.push(`Online ${game.maxOnlinePlayers}`);
  return parts.length > 0 ? parts.join(" / ") : null;
}
