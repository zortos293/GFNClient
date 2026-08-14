import assert from "node:assert/strict";
import test from "node:test";

import {
  formatOptionalBitrate,
  formatServerGameFps,
  isRttSpike,
  nextStatsOverlayMode,
} from "./streamStatsHud";

test("cycles the stats HUD through off, compact, and full", () => {
  assert.equal(nextStatsOverlayMode("off"), "compact");
  assert.equal(nextStatsOverlayMode("compact"), "full");
  assert.equal(nextStatsOverlayMode("full"), "off");
});

test("detects only sudden high RTT spikes", () => {
  assert.equal(isRttSpike(30, 80), true);
  assert.equal(isRttSpike(50, 90), false);
  assert.equal(isRttSpike(0, 120), false);
});

test("keeps unavailable and native bandwidth unknown", () => {
  assert.equal(formatOptionalBitrate(48_000), "48.0 Mbps");
  assert.equal(formatOptionalBitrate(950), "950 kbps");
  assert.equal(formatOptionalBitrate(0), "--");
  assert.equal(formatOptionalBitrate(48_000, true), "--");
});

test("never substitutes local frame rates for server-reported game FPS", () => {
  assert.equal(formatServerGameFps({ gameFps: undefined, decodeFps: 120, renderFps: 120 }), "--");
  assert.equal(formatServerGameFps({ gameFps: 0, decodeFps: 120, renderFps: 120 }), "--");
  assert.equal(formatServerGameFps({ gameFps: 87, decodeFps: 60, renderFps: 59 }), "87");
});
