/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  expandEntitledStreamResolutions,
  getSafeFallbackEntitledResolutions,
  resolveEntitledStreamProfile,
  SAFE_FALLBACK_STREAM_PROFILE,
} from "./gfn";

test("resolves requested stream settings to entitled resolution and fps profiles", () => {
  const entitlements = [
    { width: 1920, height: 1080, fps: 60 },
    { width: 1280, height: 720, fps: 60 },
  ];

  assert.deepEqual(
    resolveEntitledStreamProfile(entitlements, { resolution: "1920x1080", fps: 240 }),
    { resolution: "1920x1080", fps: 60 },
  );
  assert.deepEqual(
    resolveEntitledStreamProfile(entitlements, { resolution: "3840x2160", fps: 120 }),
    { resolution: "1920x1080", fps: 60 },
  );
  assert.equal(resolveEntitledStreamProfile([], { resolution: "1920x1080", fps: 60 }), null);
});

test("safe fallback entitlements resolve oversized requests to 1080p60", () => {
  assert.deepEqual(
    resolveEntitledStreamProfile(
      getSafeFallbackEntitledResolutions(),
      { resolution: "3840x2160", fps: 240 },
    ),
    SAFE_FALLBACK_STREAM_PROFILE,
  );
});

test("derives official ultrawide modes covered by a larger entitlement", () => {
  const entitlements = [
    { width: 3840, height: 2160, fps: 120 },
    { width: 2560, height: 1080, fps: 120 },
  ];

  assert.ok(
    expandEntitledStreamResolutions(entitlements).some(
      (resolution) =>
        resolution.width === 3440 &&
        resolution.height === 1440 &&
        resolution.fps === 120,
    ),
  );
  assert.deepEqual(
    resolveEntitledStreamProfile(entitlements, { resolution: "3440x1440", fps: 120 }),
    { resolution: "3440x1440", fps: 120 },
  );
});

test("does not derive ultrawide modes larger than the entitlement envelope", () => {
  const entitlements = [
    { width: 2560, height: 1440, fps: 120 },
    { width: 2560, height: 1080, fps: 120 },
  ];

  assert.equal(
    expandEntitledStreamResolutions(entitlements).some(
      (resolution) =>
        resolution.width === 3440 &&
        resolution.height === 1440,
    ),
    false,
  );
  assert.deepEqual(
    resolveEntitledStreamProfile(entitlements, { resolution: "3440x1440", fps: 120 }),
    { resolution: "2560x1440", fps: 120 },
  );
});

test("does not derive modes that exceed entitlement dimensions", () => {
  const entitlements = [{ width: 1920, height: 1080, fps: 120 }];
  const expanded = expandEntitledStreamResolutions(entitlements);

  assert.equal(
    expanded.some(
      (resolution) =>
        resolution.width === 1600 &&
        resolution.height === 1200,
    ),
    false,
  );
  assert.equal(
    expanded.some(
      (resolution) =>
        resolution.width === 1680 &&
        resolution.height === 1050,
    ),
    true,
  );
  assert.deepEqual(
    resolveEntitledStreamProfile(entitlements, { resolution: "1600x1200", fps: 120 }),
    { resolution: "1920x1080", fps: 120 },
  );
});

test("does not synthesize 90 FPS from higher FPS entitlement envelopes", () => {
  const highFpsEntitlements = [{ width: 1920, height: 1080, fps: 120 }];
  const highFpsExpanded = expandEntitledStreamResolutions(highFpsEntitlements);

  assert.equal(
    highFpsExpanded.some((resolution) => resolution.fps === 90),
    false,
  );
  // Envelope coverage still allows lower catalog FPS at covered resolutions.
  assert.equal(
    highFpsExpanded.some(
      (resolution) =>
        resolution.width === 1280 &&
        resolution.height === 800 &&
        resolution.fps === 60,
    ),
    true,
  );
  // Without an exact MES 90 tuple, request 90 and clamp to the nearest entitled FPS.
  assert.deepEqual(
    resolveEntitledStreamProfile(highFpsEntitlements, { resolution: "1280x800", fps: 90 }),
    { resolution: "1280x800", fps: 60 },
  );
});

test("preserves exact 90 FPS modes returned by MES entitlements", () => {
  const mesNinety = [
    { width: 1920, height: 1080, fps: 120 },
    { width: 1280, height: 800, fps: 90 },
  ];
  const expanded = expandEntitledStreamResolutions(mesNinety);

  assert.equal(
    expanded.some(
      (resolution) =>
        resolution.width === 1280 &&
        resolution.height === 800 &&
        resolution.fps === 90,
    ),
    true,
  );
  assert.deepEqual(
    resolveEntitledStreamProfile(mesNinety, { resolution: "1280x800", fps: 90 }),
    { resolution: "1280x800", fps: 90 },
  );
});
