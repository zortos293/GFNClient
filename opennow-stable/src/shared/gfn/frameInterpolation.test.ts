/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  DEFAULT_FRAME_INTERPOLATION_SETTINGS,
  frameInterpolationIsActive,
  normalizeFrameInterpolationSettings,
} from "./frameInterpolation";

test("normalizeFrameInterpolationSettings returns defaults for invalid input", () => {
  assert.deepEqual(normalizeFrameInterpolationSettings(undefined), {
    ...DEFAULT_FRAME_INTERPOLATION_SETTINGS,
  });
  assert.deepEqual(normalizeFrameInterpolationSettings(null), {
    ...DEFAULT_FRAME_INTERPOLATION_SETTINGS,
  });
  assert.deepEqual(normalizeFrameInterpolationSettings("nope"), {
    ...DEFAULT_FRAME_INTERPOLATION_SETTINGS,
  });
});

test("normalizeFrameInterpolationSettings clamps factor and quality", () => {
  assert.deepEqual(
    normalizeFrameInterpolationSettings({
      enabled: true,
      factor: 6,
      quality: 1080,
    }),
    {
      enabled: true,
      factor: DEFAULT_FRAME_INTERPOLATION_SETTINGS.factor,
      quality: DEFAULT_FRAME_INTERPOLATION_SETTINGS.quality,
    },
  );

  assert.deepEqual(
    normalizeFrameInterpolationSettings({
      enabled: 1,
      factor: 3,
      quality: 720,
    }),
    {
      enabled: false,
      factor: 3,
      quality: 720,
    },
  );
});

test("frameInterpolationIsActive tracks the master toggle", () => {
  assert.equal(frameInterpolationIsActive({ ...DEFAULT_FRAME_INTERPOLATION_SETTINGS }), false);
  assert.equal(
    frameInterpolationIsActive({ ...DEFAULT_FRAME_INTERPOLATION_SETTINGS, enabled: true }),
    true,
  );
});
