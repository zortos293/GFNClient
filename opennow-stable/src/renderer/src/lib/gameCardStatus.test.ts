/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import type { GameInfo, GameVariant } from "@shared/gfn";
import {
  getActiveGameAvailabilityBadge,
  getGameAvailabilityBadgeForStatus,
} from "./gameCardStatus";

function makeVariant(overrides: Partial<GameVariant> = {}): GameVariant {
  return {
    id: overrides.id ?? "variant-1",
    store: overrides.store ?? "Steam",
    supportedControls: overrides.supportedControls ?? [],
    gfnStatus: overrides.gfnStatus,
  };
}

function makeGame(variants: GameVariant[], selectedVariantIndex = 0): GameInfo {
  return {
    id: "game-1",
    title: "Test Game",
    selectedVariantIndex,
    variants,
  };
}

test("does not show a badge for available or missing GFN statuses", () => {
  assert.equal(getGameAvailabilityBadgeForStatus(undefined), null);
  assert.equal(getGameAvailabilityBadgeForStatus(""), null);
  assert.equal(getGameAvailabilityBadgeForStatus("AVAILABLE"), null);
  assert.equal(getGameAvailabilityBadgeForStatus(" available "), null);
});

test("maps maintenance statuses to the maintenance badge", () => {
  assert.deepEqual(getGameAvailabilityBadgeForStatus("SERVER_MAINTENANCE"), {
    kind: "maintenance",
    labelKey: "gameCard.status.maintenance",
    status: "SERVER_MAINTENANCE",
  });
  assert.equal(getGameAvailabilityBadgeForStatus("maintenance")?.kind, "maintenance");
});

test("maps patching and updating statuses to the updating badge", () => {
  assert.deepEqual(getGameAvailabilityBadgeForStatus("PATCHING"), {
    kind: "updating",
    labelKey: "gameCard.status.updating",
    status: "PATCHING",
  });
  assert.equal(getGameAvailabilityBadgeForStatus("manual update")?.kind, "updating");
});

test("maps unknown non-available statuses to the unavailable badge", () => {
  assert.deepEqual(getGameAvailabilityBadgeForStatus("TEMPORARILY_DISABLED"), {
    kind: "unavailable",
    labelKey: "gameCard.status.unavailable",
    status: "TEMPORARILY_DISABLED",
  });
});

test("uses the selected active variant status for the card badge", () => {
  const game = makeGame([
    makeVariant({ id: "steam", store: "STEAM", gfnStatus: "AVAILABLE" }),
    makeVariant({ id: "epic", store: "EPIC", gfnStatus: "PATCHING" }),
  ]);

  assert.equal(getActiveGameAvailabilityBadge(game), null);
  assert.equal(getActiveGameAvailabilityBadge(game, "epic")?.kind, "updating");
});
