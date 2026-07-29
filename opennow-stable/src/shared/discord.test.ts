/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { discordGameImageUrl } from "./discord";

test("discordGameImageUrl prefers box artwork over landscape fallbacks", () => {
  assert.equal(discordGameImageUrl({
    imageUrl: "https://example.com/hero.jpg",
    imageUrlsByType: {
      GAME_BOX_ART: ["https://example.com/box-art.jpg"],
    },
  }), "https://example.com/box-art.jpg");
});

test("discordGameImageUrl falls back to the catalog image", () => {
  assert.equal(discordGameImageUrl({
    imageUrl: "https://example.com/catalog.jpg",
  }), "https://example.com/catalog.jpg");
});
