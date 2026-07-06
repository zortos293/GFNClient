/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  discordActivityFromSession,
  discordActivityKindForSession,
  discordMonitorActivityDecision,
  isSameDiscordActivity,
} from "./discordPresence";

test("discordActivityKindForSession maps queued CloudMatch states without marking them streaming", () => {
  assert.equal(discordActivityKindForSession({ status: 1, seatSetupStep: 1, queuePosition: 42 }), "queued");
  assert.equal(discordActivityKindForSession({ status: 1, queuePosition: 2 }), "queued");
});

test("discordActivityKindForSession maps ready and streaming states distinctly", () => {
  assert.equal(discordActivityKindForSession({ status: 1 }), "starting");
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

test("discordMonitorActivityDecision does not downgrade active streaming presence", () => {
  const startedAt = new Date("2026-01-01T00:00:00.000Z");
  const decision = discordMonitorActivityDecision({
    gameName: "Test Game",
    kind: "streaming",
    appId: "1001",
    startTimestamp: startedAt,
  }, {
    sessionId: "s1",
    appId: 1001,
    status: 2,
  });

  assert.deepEqual(decision, { action: "none" });
});

test("discordMonitorActivityDecision preserves current game title on queue updates", () => {
  const decision = discordMonitorActivityDecision({
    gameName: "Human Game Title",
    kind: "queued",
    appId: "1001",
    queuePosition: 12,
  }, {
    sessionId: "s1",
    appId: 1001,
    status: 1,
    queuePosition: 11,
  });

  assert.equal(decision.action, "set");
  if (decision.action === "set") {
    assert.equal(decision.activity.gameName, "Human Game Title");
    assert.equal(decision.activity.queuePosition, 11);
  }
});

test("discordMonitorActivityDecision does not reset active streaming timer", () => {
  const startedAt = new Date("2026-01-01T00:00:00.000Z");
  const decision = discordMonitorActivityDecision({
    gameName: "Test Game",
    kind: "streaming",
    appId: "1001",
    startTimestamp: startedAt,
  }, {
    sessionId: "s1",
    appId: 1001,
    status: 3,
  });

  assert.deepEqual(decision, { action: "none" });
});
