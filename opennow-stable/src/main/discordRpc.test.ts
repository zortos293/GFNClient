/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { discordRpcActivityPayload, discordRpcImageUrl } from "./discordRpc";

test("discordRpcImageUrl accepts public HTTPS artwork", () => {
  assert.equal(
    discordRpcImageUrl("https://img.nvidiagrid.net/game.jpg;f=webp;w=1200"),
    "https://img.nvidiagrid.net/game.jpg;f=webp;w=1200",
  );
});

test("discordRpcImageUrl rejects non-HTTPS and malformed artwork URLs", () => {
  assert.equal(discordRpcImageUrl("http://example.com/game.jpg"), undefined);
  assert.equal(discordRpcImageUrl("not a URL"), undefined);
});

test("discordRpcActivityPayload sends game artwork as the large presence image", () => {
  const startedAt = new Date("2026-01-01T00:00:00.000Z");
  assert.deepEqual(discordRpcActivityPayload({
    gameName: "7 Days to Die",
    gameImageUrl: "https://example.com/7-days-to-die.jpg",
    kind: "streaming",
    appId: "1001",
    startTimestamp: startedAt,
  }), {
    details: "7 Days to Die",
    state: "Streaming via OpenNow",
    startTimestamp: startedAt,
    largeImageKey: "https://example.com/7-days-to-die.jpg",
    largeImageText: "7 Days to Die",
    instance: false,
  });
});
