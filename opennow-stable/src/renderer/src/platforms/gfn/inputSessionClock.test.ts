/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  captureTimestampUs,
  sendTimestampUs,
  startInputSessionClock,
  writeSessionTimestamp,
} from "./inputSessionClock";

test("uses a nonnegative session-relative microsecond clock", () => {
  startInputSessionClock(1_000);
  assert.equal(captureTimestampUs(1_010.125), 10_125n);
  assert.equal(sendTimestampUs(1_025), 25_000n);
  assert.equal(captureTimestampUs(999), 0n);
});

test("writes clamped session timestamps as big-endian uint64 values", () => {
  const bytes = new Uint8Array(16);
  const view = new DataView(bytes.buffer);

  writeSessionTimestamp(view, 4, 0x0102030405060708n);
  assert.deepEqual(Array.from(bytes.subarray(4, 12)), [1, 2, 3, 4, 5, 6, 7, 8]);

  writeSessionTimestamp(view, 4, -1n);
  assert.equal(view.getBigUint64(4, false), 0n);
});
