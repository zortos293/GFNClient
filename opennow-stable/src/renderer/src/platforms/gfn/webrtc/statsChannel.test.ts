import assert from "node:assert/strict";
import test from "node:test";

import { parseStatsChannelGameFps } from "./statsChannel";

function buildMessage(type: 3 | 4, version: number, fps: number): ArrayBuffer {
  const payloadOffset = type === 3 ? 1 : 0;
  const buffer = new ArrayBuffer(payloadOffset + 33);
  const view = new DataView(buffer);
  if (type === 3) {
    view.setUint8(0, 3);
  }
  view.setUint8(payloadOffset, version);
  view.setFloat64(payloadOffset + 25, fps, true);
  return buffer;
}

test("parses NVIDIA type 3 version 4 and 5 game FPS", () => {
  assert.deepEqual(parseStatsChannelGameFps(buildMessage(3, 4, 59.6)), {
    version: 4,
    fps: 60,
  });
  assert.deepEqual(parseStatsChannelGameFps(buildMessage(3, 5, 239.7)), {
    version: 5,
    fps: 240,
  });
});

test("parses NVIDIA type 4 unprefixed version 4 game FPS", () => {
  assert.deepEqual(parseStatsChannelGameFps(buildMessage(4, 4, 89.8)), {
    version: 4,
    fps: 90,
  });
});

test("rejects unknown types, old versions, malformed values, and short payloads", () => {
  const unknownType = buildMessage(3, 5, 60);
  new Uint8Array(unknownType)[0] = 2;

  assert.equal(parseStatsChannelGameFps(unknownType), null);
  assert.equal(parseStatsChannelGameFps(buildMessage(3, 3, 60)), null);
  assert.equal(parseStatsChannelGameFps(buildMessage(3, 5, Number.NaN)), null);
  assert.equal(parseStatsChannelGameFps(buildMessage(3, 5, 0)), null);
  assert.equal(parseStatsChannelGameFps(buildMessage(3, 5, 361)), null);
  assert.equal(parseStatsChannelGameFps(new ArrayBuffer(32)), null);
  assert.equal(parseStatsChannelGameFps(new ArrayBuffer(0)), null);
});
