/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  appendPublicGameSearchMatches,
  inferPublicGameStore,
  mergePublicGameVariants,
  publicGameToGameInfo,
} from "./publicGames";
import {
  isGfnVariantFeatureSupported,
  supportsInGameSettingsPersistence,
} from "./gameFeatures";

test("infers NCSoft as the public catalog store for Guild Wars 2", () => {
  assert.equal(
    inferPublicGameStore({
      id: 17940711,
      title: "Guild Wars 2",
      steamUrl: "",
      publisher: "NCsoft Corp.",
      store: "",
      status: "AVAILABLE",
    }),
    "NCSoft",
  );
});

test("uses explicit public catalog stores before publisher fallback", () => {
  assert.equal(
    inferPublicGameStore({
      title: "Steam Game",
      store: "Steam",
      publisher: "NCsoft Corp.",
      status: "AVAILABLE",
    }),
    "Steam",
  );
});

test("uses Unknown for blank public catalog stores without a known launcher publisher", () => {
  assert.equal(inferPublicGameStore({ title: "Unlabeled Game", status: "AVAILABLE" }), "Unknown");
  assert.equal(
    inferPublicGameStore({ title: "Publisher Only Game", publisher: "Some Publisher", status: "AVAILABLE" }),
    "Unknown",
  );
});

test("maps Guild Wars 2 public catalog data to an NCSoft default-icon variant", () => {
  const game = publicGameToGameInfo({
    id: 17940711,
    title: "Guild Wars 2",
    steamUrl: "",
    publisher: "NCsoft Corp.",
    store: "",
    status: "AVAILABLE",
  });

  assert.equal(game.launchAppId, "17940711");
  assert.deepEqual(game.variants, [{ id: "17940711", store: "NCSoft", supportedControls: [] }]);
  assert.deepEqual(game.availableStores, ["NCSoft"]);
  assert.equal(game.searchText, "guild wars 2 ncsoft corp.");
});

test("detects official per-variant in-game settings persistence metadata", () => {
  assert.equal(
    supportsInGameSettingsPersistence({
      gfn: {
        features: [
          { key: "IN_GAME_SETTINGS_PERSISTENCE_ENABLED", value: "true" },
        ],
      },
    }),
    true,
  );
  assert.equal(
    supportsInGameSettingsPersistence({
      gfn: {
        features: [
          { key: "IN_GAME_SETTINGS_PERSISTENCE_ENABLED", value: "false" },
        ],
      },
    }),
    false,
  );
  assert.equal(
    isGfnVariantFeatureSupported(
      {
        gfn: {
          features: {
            key: "SUPPORTED_HDR_VERSION",
            values: ["HDR10_PLUS_GAMING"],
          },
        },
      },
      "SUPPORTED_HDR_VERSION",
      "HDR10_PLUS_GAMING",
    ),
    true,
  );
});

test("merges supplemental public launcher variants into catalog games by title", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "guild-wars-2",
        title: "Guild Wars 2",
        selectedVariantIndex: 0,
        variants: [{ id: "steam", store: "Steam", supportedControls: [] }],
        availableStores: ["Steam"],
        searchText: "guild wars 2 steam",
      },
    ],
    [
      publicGameToGameInfo({
        id: 17940711,
        title: "Guild Wars 2",
        steamUrl: "",
        publisher: "NCsoft Corp.",
        store: "",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.deepEqual(
    game?.variants.map((variant) => ({ id: variant.id, store: variant.store })),
    [
      { id: "steam", store: "Steam" },
      { id: "17940711", store: "NCSoft" },
    ],
  );
  assert.deepEqual(game?.availableStores, ["Steam", "NCSoft"]);
});

test("preserves public Steam hero fallback when merging supplemental variants", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "third-party-steam-game",
        title: "Third Party Steam Game",
        selectedVariantIndex: 0,
        variants: [{ id: "third-party", store: "GOG", supportedControls: [] }],
        availableStores: ["GOG"],
      },
    ],
    [
      publicGameToGameInfo({
        id: 456,
        title: "Third Party Steam Game",
        steamUrl: "https://store.steampowered.com/app/456",
        store: "Steam",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.equal(game?.heroImageUrl, "https://cdn.cloudflare.steamstatic.com/steam/apps/456/library_hero.jpg");
});

test("merges same-id public launcher variants when the store is missing from catalog data", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "guild-wars-2",
        title: "Guild Wars 2",
        selectedVariantIndex: 0,
        variants: [{ id: "17940711", store: "Steam", supportedControls: [] }],
        availableStores: ["Steam"],
      },
    ],
    [
      publicGameToGameInfo({
        id: 17940711,
        title: "Guild Wars 2",
        steamUrl: "",
        publisher: "NCsoft Corp.",
        store: "",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.deepEqual(
    game?.variants.map((variant) => ({ id: variant.id, store: variant.store })),
    [
      { id: "17940711", store: "Steam" },
      { id: "17940711", store: "NCSoft" },
    ],
  );
});

test("does not duplicate primary catalog store variants from public data", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "steam-game",
        title: "Steam Game",
        selectedVariantIndex: 0,
        variants: [{ id: "steam", store: "Steam", supportedControls: [] }],
        availableStores: ["Steam"],
      },
    ],
    [
      publicGameToGameInfo({
        id: 123,
        title: "Steam Game",
        steamUrl: "https://store.steampowered.com/app/123",
        store: "Steam",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.deepEqual(game?.variants.map((variant) => variant.store), ["Steam"]);
});

test("does not add Unknown public variants when catalog stores already exist", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "war-thunder",
        title: "War Thunder",
        selectedVariantIndex: 0,
        variants: [
          { id: "10839111", store: "GAIJIN", supportedControls: [] },
          { id: "100234911", store: "STEAM", supportedControls: [] },
        ],
        availableStores: ["GAIJIN", "STEAM"],
      },
    ],
    [
      publicGameToGameInfo({
        id: 10839111,
        title: "War Thunder",
        steamUrl: "",
        publisher: "Gaijin Entertainment",
        store: "",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.deepEqual(
    game?.variants.map((variant) => ({ id: variant.id, store: variant.store })),
    [
      { id: "10839111", store: "GAIJIN" },
      { id: "100234911", store: "STEAM" },
    ],
  );
  assert.deepEqual(game?.availableStores, ["GAIJIN", "STEAM"]);
});

test("adds Unknown public variants when catalog only has a None placeholder store", () => {
  const [game] = mergePublicGameVariants(
    [
      {
        id: "launcher-only-game",
        title: "Launcher Only Game",
        selectedVariantIndex: 0,
        variants: [{ id: "placeholder", store: "None", supportedControls: [] }],
        availableStores: ["None"],
      },
    ],
    [
      publicGameToGameInfo({
        id: 123456,
        title: "Launcher Only Game",
        steamUrl: "",
        publisher: "Standalone Publisher",
        store: "",
        status: "AVAILABLE",
      }),
    ],
  );

  assert.deepEqual(
    game?.variants.map((variant) => ({ id: variant.id, store: variant.store })),
    [
      { id: "placeholder", store: "None" },
      { id: "123456", store: "Unknown" },
    ],
  );
  assert.equal(game?.uuid, "123456");
  assert.equal(game?.launchAppId, "123456");
});

test("appends public-only games that match catalog search", () => {
  const games = appendPublicGameSearchMatches(
    [
      {
        id: "catalog-game",
        title: "Catalog Game",
        selectedVariantIndex: 0,
        variants: [{ id: "catalog-game", store: "Steam", supportedControls: [] }],
      },
    ],
    [
      publicGameToGameInfo({
        id: 17940711,
        title: "Guild Wars 2",
        steamUrl: "",
        publisher: "NCsoft Corp.",
        store: "",
        status: "AVAILABLE",
      }),
      publicGameToGameInfo({
        id: 123,
        title: "Unrelated Game",
        steamUrl: "",
        store: "Unknown",
        status: "AVAILABLE",
      }),
    ],
    "guild wars",
  );

  assert.deepEqual(games.map((game) => game.title), ["Catalog Game", "Guild Wars 2"]);
});

test("does not append public search matches already represented by catalog results", () => {
  const games = appendPublicGameSearchMatches(
    [
      {
        id: "guild-wars-2-catalog",
        title: "Guild Wars 2",
        selectedVariantIndex: 0,
        variants: [{ id: "steam", store: "Steam", supportedControls: [] }],
      },
    ],
    [
      publicGameToGameInfo({
        id: 17940711,
        title: "Guild Wars 2",
        steamUrl: "",
        publisher: "NCsoft Corp.",
        store: "",
        status: "AVAILABLE",
      }),
    ],
    "guild wars",
  );

  assert.deepEqual(games.map((game) => game.title), ["Guild Wars 2"]);
});
