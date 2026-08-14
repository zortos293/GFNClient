import assert from "node:assert/strict";
import test from "node:test";

import {
  computeBitrateDiagnostics,
  computeIntervalFrameRates,
  type IntervalFrameRateParams,
} from "./streamStatsHelpers";

test("keeps controller target bitrate independent from ICE available bitrate", () => {
  assert.deepEqual(
    computeBitrateDiagnostics(35_000, {
      availableIncomingBitrate: 12_345_678,
    }),
    {
      targetBitrateKbps: 35_000,
      availableBitrateKbps: 12_346,
    },
  );
});

function sample(overrides: Partial<IntervalFrameRateParams> = {}): IntervalFrameRateParams {
  return {
    framesReceived: 120,
    framesDecoded: 120,
    totalDecodeTime: 1,
    prevFramesReceived: 60,
    prevFramesDecoded: 60,
    prevTotalDecodeTime: 0.5,
    timeDeltaMs: 1000,
    prevReceiveFps: 60,
    prevDecodeFps: 60,
    prevDecodeTimeMs: 8.3,
    ...overrides,
  };
}

test("computes receive FPS, decode FPS, and per-frame decode time from deltas", () => {
  assert.deepEqual(computeIntervalFrameRates(sample()), {
    receiveFps: 60,
    decodeFps: 60,
    decodeTimeMs: 8.3,
  });
});

test("keeps measured rates and timing during quiet static-frame intervals", () => {
  assert.deepEqual(
    computeIntervalFrameRates(sample({
      framesReceived: 60,
      framesDecoded: 60,
      totalDecodeTime: 0.5,
    })),
    {
      receiveFps: 60,
      decodeFps: 60,
      decodeTimeMs: 8.3,
    },
  );
});

test("reports a decode stall while receive frames continue", () => {
  assert.deepEqual(
    computeIntervalFrameRates(sample({
      framesDecoded: 60,
      totalDecodeTime: 0.5,
    })),
    {
      receiveFps: 60,
      decodeFps: 0,
      decodeTimeMs: 8.3,
    },
  );
});

test("keeps the previous sample when cumulative counters reset", () => {
  assert.deepEqual(
    computeIntervalFrameRates(sample({
      framesReceived: 5,
      framesDecoded: 4,
      totalDecodeTime: 0.03,
    })),
    {
      receiveFps: 60,
      decodeFps: 60,
      decodeTimeMs: 8.3,
    },
  );
});

test("keeps the previous sample for invalid or non-positive intervals", () => {
  const expected = {
    receiveFps: 60,
    decodeFps: 60,
    decodeTimeMs: 8.3,
  };
  assert.deepEqual(computeIntervalFrameRates(sample({ timeDeltaMs: 0 })), expected);
  assert.deepEqual(computeIntervalFrameRates(sample({ timeDeltaMs: -1 })), expected);
  assert.deepEqual(computeIntervalFrameRates(sample({ framesReceived: Number.NaN })), expected);
});
