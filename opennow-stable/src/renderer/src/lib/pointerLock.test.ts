import assert from "node:assert/strict";
import test from "node:test";

import {
  canForwardStreamPointerInput,
  didStreamPointerLockExit,
  getStreamPointerLockTarget,
  isStreamPointerLocked,
} from "./pointerLock";

test("stream pointer lock accepts the video wrapper used by input capture", () => {
  const wrapper = {} as HTMLElement;
  const video = { parentElement: wrapper } as HTMLVideoElement;

  assert.equal(getStreamPointerLockTarget(video), wrapper);
  assert.equal(isStreamPointerLocked(video, wrapper), true);
  assert.equal(isStreamPointerLocked(video, video), true);
  assert.equal(isStreamPointerLocked(video, null), false);
});

test("stream pointer lock falls back to the video without a wrapper", () => {
  const video = { parentElement: null } as HTMLVideoElement;

  assert.equal(getStreamPointerLockTarget(video), video);
  assert.equal(isStreamPointerLocked(video, video), true);
});

test("unlocked pointer input only continues during the Escape fallback", () => {
  assert.equal(canForwardStreamPointerInput(true, false, false), true);
  assert.equal(canForwardStreamPointerInput(false, true, true), true);
  assert.equal(canForwardStreamPointerInput(false, true, false), false);
  assert.equal(canForwardStreamPointerInput(false, false, true), false);
});

test("pointer lock loss only fires on an active-to-inactive transition", () => {
  assert.equal(didStreamPointerLockExit(true, false), true);
  assert.equal(didStreamPointerLockExit(false, false), false);
  assert.equal(didStreamPointerLockExit(true, true), false);
  assert.equal(didStreamPointerLockExit(false, true), false);
});
