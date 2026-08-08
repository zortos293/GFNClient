/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  DEFAULT_FRAME_GENERATION_SETTINGS,
  normalizeFrameGenerationSettings,
} from "./frameGeneration";

test("normalizes frame generation defaults and explicit values", () => {
  assert.deepEqual(
    normalizeFrameGenerationSettings(undefined),
    DEFAULT_FRAME_GENERATION_SETTINGS,
  );
  assert.deepEqual(
    normalizeFrameGenerationSettings({ enabled: true, quality: 1080 }),
    { enabled: true, quality: 1080 },
  );
  assert.deepEqual(
    normalizeFrameGenerationSettings({ enabled: "true", quality: "720" }),
    { enabled: false, quality: 720 },
  );
});

test("clamps persisted quality to the nearest supported processing cap", () => {
  assert.equal(normalizeFrameGenerationSettings({ quality: -1 }).quality, 480);
  assert.equal(normalizeFrameGenerationSettings({ quality: 600 }).quality, 480);
  assert.equal(normalizeFrameGenerationSettings({ quality: 900 }).quality, 720);
  assert.equal(normalizeFrameGenerationSettings({ quality: 9999 }).quality, 1080);
  assert.equal(normalizeFrameGenerationSettings({ quality: "invalid" }).quality, 720);
});
