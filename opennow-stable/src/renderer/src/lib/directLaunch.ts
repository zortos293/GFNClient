import type { DirectLaunchRequest, GameInfo } from "@shared/gfn";
import { isGameInLibrary } from "@shared/gfn";

import {
  findSessionContextForAppId,
  matchesGameSearch,
  parseNumericId,
} from "./gameCatalog";

export interface DirectLaunchTarget {
  game: GameInfo;
  variantId?: string;
}

export function normalizeDirectLaunchText(value: string | undefined): string {
  return value?.trim().replace(/\s+/g, " ").toLowerCase() ?? "";
}

export function directLaunchOwnershipScore(game: GameInfo): number {
  return game.isInLibrary || isGameInLibrary(game) ? 10 : 0;
}

export function findDirectLaunchTargetByTitle(catalog: GameInfo[], title: string): DirectLaunchTarget | null {
  const normalizedTitle = normalizeDirectLaunchText(title);
  if (!normalizedTitle) return null;

  let best: { target: DirectLaunchTarget; score: number } | null = null;
  for (const game of catalog) {
    const gameTitle = normalizeDirectLaunchText(game.title);
    const shortName = normalizeDirectLaunchText(game.shortName);
    let score = 0;
    if (gameTitle === normalizedTitle) {
      score = 100;
    } else if (shortName && shortName === normalizedTitle) {
      score = 95;
    } else if (gameTitle.startsWith(normalizedTitle)) {
      score = 80;
    } else if (matchesGameSearch(game, title)) {
      score = 60;
    }

    if (score === 0) continue;
    score += directLaunchOwnershipScore(game);
    if (!best || score > best.score) {
      best = { target: { game }, score };
    }
  }

  return best?.target ?? null;
}

export function createSyntheticDirectLaunchGame(request: DirectLaunchRequest, appId: string): GameInfo {
  const title = request.title?.trim() || `GFN App ${appId}`;
  return {
    id: `direct-launch-${appId}`,
    launchAppId: appId,
    title,
    searchText: normalizeDirectLaunchText(title),
    isInLibrary: true,
    selectedVariantIndex: 0,
    variants: [
      {
        id: appId,
        store: "UNKNOWN",
        supportedControls: [],
        libraryStatus: "IN_LIBRARY",
      },
    ],
  };
}

export function findDirectLaunchTarget(
  request: DirectLaunchRequest,
  catalog: GameInfo[],
  variantByGameId: Record<string, string>,
): DirectLaunchTarget | null {
  const numericAppId = parseNumericId(request.appId);
  if (numericAppId !== null) {
    const matched = findSessionContextForAppId(catalog, variantByGameId, numericAppId);
    if (matched) {
      return { game: matched.game, variantId: matched.variant?.id };
    }
  }

  if (request.title) {
    return findDirectLaunchTargetByTitle(catalog, request.title);
  }

  return null;
}
