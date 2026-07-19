/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { appToGame, dedupeGames } from "./gameAppMapper";

test("appToGame marks owned-but-unselected variants as inLibrary", () => {
  const game = appToGame({
    id: "game-1",
    title: "Owned Game",
    variants: [
      {
        id: "v1",
        appStore: "Steam",
        gfn: {
          library: {
            status: "PLATFORM_SYNC",
            selected: false,
          },
        },
      },
      {
        id: "v2",
        appStore: "Epic",
        gfn: {
          library: {
            status: "NOT_OWNED",
            selected: false,
          },
        },
      },
    ],
  });

  assert.equal(game.isInLibrary, true);
  assert.equal(game.variants[0]?.inLibrary, true);
  assert.equal(game.variants[0]?.librarySelected, false);
  assert.equal(game.variants[1]?.inLibrary, false);
});

test("dedupeGames preserves ownership metadata when merging same-id variants", () => {
  const [merged] = dedupeGames([
    {
      id: "game-1",
      title: "Game",
      selectedVariantIndex: 0,
      isInLibrary: true,
      variants: [{
        id: "v1",
        store: "Steam",
        supportedControls: [],
        inLibrary: true,
        libraryStatus: "MANUAL",
        librarySelected: true,
      }],
    },
    {
      id: "game-1",
      title: "Game",
      selectedVariantIndex: 0,
      variants: [{
        id: "v1",
        store: "Steam",
        supportedControls: ["KEYBOARD_MOUSE"],
        storeUrl: "https://store.example/steam",
      }],
    },
  ]);

  assert.equal(merged?.isInLibrary, true);
  assert.equal(merged?.variants[0]?.inLibrary, true);
  assert.equal(merged?.variants[0]?.libraryStatus, "MANUAL");
  assert.equal(merged?.variants[0]?.librarySelected, true);
  assert.equal(merged?.variants[0]?.storeUrl, "https://store.example/steam");
  assert.deepEqual(merged?.variants[0]?.supportedControls, ["KEYBOARD_MOUSE"]);
});

test("dedupeGames accepts an explicit NOT_OWNED status from a later view", () => {
  const [merged] = dedupeGames([
    {
      id: "game-1",
      title: "Game",
      selectedVariantIndex: 0,
      isInLibrary: true,
      variants: [{
        id: "v1",
        store: "Steam",
        supportedControls: [],
        inLibrary: true,
        libraryStatus: "MANUAL",
      }],
    },
    {
      id: "game-1",
      title: "Game",
      selectedVariantIndex: 0,
      isInLibrary: false,
      variants: [{
        id: "v1",
        store: "Steam",
        supportedControls: [],
        inLibrary: false,
        libraryStatus: "NOT_OWNED",
      }],
    },
  ]);

  assert.equal(merged?.isInLibrary, false);
  assert.equal(merged?.variants[0]?.inLibrary, false);
  assert.equal(merged?.variants[0]?.libraryStatus, "NOT_OWNED");
});
