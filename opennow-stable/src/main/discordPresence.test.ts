/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  discordActivityFromSession,
  discordActivityKindForSession,
  isSameDiscordActivity,
} from "./discordPresence";

test("discordActivityKindForSession maps queued CloudMatch states without marking them streaming", () => {
  assert.equal(discordActivityKindForSession({ status: 1, seatSetupStep: 1, queuePosition: 42 }), "queued");
  assert.equal(discordActivityKindForSession({ status: 1, queuePosition: 2 }), "queued");
  assert.equal(discordActivityKindForSession({ status: 1 }), "queued");
});

test("discordActivityKindForSession maps ready and streaming states distinctly", () => {
  assert.equal(discordActivityKindForSession({ status: 2 }), "starting");
  assert.equal(discordActivityKindForSession({ status: 3 }), "streaming");
  assert.equal(discordActivityKindForSession({ status: 4 }), null);
});

test("discordActivityFromSession includes queue position and only timestamps streaming", () => {
  const queued = discordActivityFromSession({
    sessionId: "s1",
    appId: 1001,
    status: 1,
    queuePosition: 12,
  }, "Test Game");

  assert.deepEqual(queued, {
    gameName: "Test Game",
    kind: "queued",
    appId: "1001",
    queuePosition: 12,
    startTimestampMs: undefined,
  });

  const streaming = discordActivityFromSession({
    sessionId: "s1",
    appId: 1001,
    status: 3,
  }, "Test Game");

  assert.equal(streaming?.kind, "streaming");
  assert.equal(streaming?.appId, "1001");
  assert.equal(typeof streaming?.startTimestampMs, "number");
});

test("isSameDiscordActivity compares kind and queue position for the same app", () => {
  assert.equal(isSameDiscordActivity({
    gameName: "Game",
    kind: "queued",
    appId: "1001",
    queuePosition: 10,
  }, {
    gameName: "1001",
    kind: "queued",
    appId: "1001",
    queuePosition: 10,
  }), true);

  assert.equal(isSameDiscordActivity({
    gameName: "Game",
    kind: "queued",
    appId: "1001",
    queuePosition: 10,
  }, {
    gameName: "Game",
    kind: "queued",
    appId: "1001",
    queuePosition: 9,
  }), false);
});
