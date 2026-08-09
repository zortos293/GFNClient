import assert from "node:assert/strict";
import test from "node:test";

import type { NativeStreamStats } from "@shared/gfn";

import { defaultDiagnostics, mergeNativeStreamStats } from "./streamDiagnostics";

function nativeStats(overrides: Partial<NativeStreamStats> = {}): NativeStreamStats {
  return {
    codec: "H265",
    resolution: "2560x1440",
    hardwareAcceleration: "NVDEC",
    bitrateKbps: 22_000,
    targetBitrateKbps: 35_000,
    bitratePerformancePercent: 63,
    decodedFps: 118,
    renderFps: 117,
    framesDecoded: 10_000,
    framesRendered: 9_990,
    zeroCopyD3D11: false,
    zeroCopyD3D12: false,
    ...overrides,
  };
}

test("native diagnostics leave receive and ICE available bitrate unknown", () => {
  const current = {
    ...defaultDiagnostics(),
    receiveFps: 120,
    availableBitrateKbps: 48_000,
  };

  const merged = mergeNativeStreamStats(current, nativeStats());

  assert.equal(merged.decodeFps, 118);
  assert.equal(merged.targetBitrateKbps, 35_000);
  assert.equal(merged.receiveFps, 0);
  assert.equal(merged.availableBitrateKbps, 0);
});
