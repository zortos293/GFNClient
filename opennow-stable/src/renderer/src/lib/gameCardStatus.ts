import type { GameInfo } from "@shared/gfn";
import { getActiveGameVariant } from "./gameCardVariants";

export type GameAvailabilityBadgeKind = "maintenance" | "updating" | "unavailable";

export interface GameAvailabilityBadge {
  kind: GameAvailabilityBadgeKind;
  labelKey: string;
  status: string;
}

function normalizeGfnStatus(status?: string): string {
  return status?.trim().toUpperCase().replace(/[^A-Z0-9]+/g, "_") ?? "";
}

export function getGameAvailabilityBadgeForStatus(status?: string): GameAvailabilityBadge | null {
  const normalizedStatus = normalizeGfnStatus(status);
  if (!normalizedStatus || normalizedStatus === "AVAILABLE") {
    return null;
  }

  if (normalizedStatus.includes("PATCH") || normalizedStatus.includes("UPDAT")) {
    return { kind: "updating", labelKey: "gameCard.status.updating", status: normalizedStatus };
  }

  if (normalizedStatus.includes("MAINTENANCE")) {
    return { kind: "maintenance", labelKey: "gameCard.status.maintenance", status: normalizedStatus };
  }

  return { kind: "unavailable", labelKey: "gameCard.status.unavailable", status: normalizedStatus };
}

export function getActiveGameAvailabilityBadge(game: GameInfo, selectedVariantId?: string): GameAvailabilityBadge | null {
  return getGameAvailabilityBadgeForStatus(getActiveGameVariant(game, selectedVariantId)?.gfnStatus);
}
