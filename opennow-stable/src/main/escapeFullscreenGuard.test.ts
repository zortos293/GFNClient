import test from "node:test";
import assert from "node:assert/strict";

import {
  ESCAPE_HOLD_TO_EXIT_FULLSCREEN_MS,
  isEscapeKeyDownInput,
  markEscapeHoldFired,
  nextPointerLockEscapeCaptureUntilMs,
  POINTER_LOCK_ESCAPE_FULLSCREEN_GRACE_MS,
  resolveEscapeHoldCaptureAction,
  shouldCaptureEscapeFullscreenInput,
} from "./escapeFullscreenGuard";

test("isEscapeKeyDownInput recognizes Electron Escape keydown variants", () => {
  assert.equal(isEscapeKeyDownInput({ type: "keyDown", key: "Escape" }), true);
  assert.equal(isEscapeKeyDownInput({ type: "keyDown", key: "Esc" }), true);
  assert.equal(isEscapeKeyDownInput({ type: "keyDown", code: "Escape" }), true);
  assert.equal(isEscapeKeyDownInput({ type: "keyDown", keyCode: 27 }), true);
  assert.equal(isEscapeKeyDownInput({ type: "keyUp", key: "Escape", keyCode: 27 }), false);
  assert.equal(isEscapeKeyDownInput({ type: "keyDown", key: "Enter" }), false);
});

test("shouldCaptureEscapeFullscreenInput captures Escape while pointer locked", () => {
  assert.equal(shouldCaptureEscapeFullscreenInput(
    { type: "keyDown", key: "Escape" },
    {
      allowEscapeToExitFullscreen: false,
      pointerLockActive: true,
      windowFullscreen: false,
      pointerLockEscapeCaptureUntilMs: 0,
      nowMs: 100,
    },
  ), true);
});

test("shouldCaptureEscapeFullscreenInput captures rapid Escape presses during fullscreen pointer-lock loss", () => {
  assert.equal(shouldCaptureEscapeFullscreenInput(
    { type: "keyDown", key: "Escape" },
    {
      allowEscapeToExitFullscreen: false,
      pointerLockActive: false,
      windowFullscreen: true,
      pointerLockEscapeCaptureUntilMs: 1500,
      nowMs: 1000,
    },
  ), true);
});

test("shouldCaptureEscapeFullscreenInput allows Escape outside protected stream states", () => {
  const input = { type: "keyDown", key: "Escape" };
  assert.equal(shouldCaptureEscapeFullscreenInput(input, {
    allowEscapeToExitFullscreen: true,
    pointerLockActive: true,
    windowFullscreen: true,
    pointerLockEscapeCaptureUntilMs: 1500,
    nowMs: 1000,
  }), false);
  assert.equal(shouldCaptureEscapeFullscreenInput(input, {
    allowEscapeToExitFullscreen: false,
    pointerLockActive: false,
    windowFullscreen: true,
    pointerLockEscapeCaptureUntilMs: 999,
    nowMs: 1000,
  }), false);
  assert.equal(shouldCaptureEscapeFullscreenInput(input, {
    allowEscapeToExitFullscreen: false,
    pointerLockActive: false,
    windowFullscreen: false,
    pointerLockEscapeCaptureUntilMs: 1500,
    nowMs: 1000,
  }), false);
});

test("nextPointerLockEscapeCaptureUntilMs only arms grace for unsuppressed pointer-lock loss", () => {
  assert.equal(nextPointerLockEscapeCaptureUntilMs(true, false, 1000), 0);
  assert.equal(nextPointerLockEscapeCaptureUntilMs(false, true, 1000), 0);
  assert.equal(
    nextPointerLockEscapeCaptureUntilMs(false, false, 1000),
    1000 + POINTER_LOCK_ESCAPE_FULLSCREEN_GRACE_MS,
  );
});

test("resolveEscapeHoldCaptureAction arms hold then taps on early keyup", () => {
  const guard = {
    allowEscapeToExitFullscreen: false,
    pointerLockActive: true,
    windowFullscreen: true,
    pointerLockEscapeCaptureUntilMs: 0,
    nowMs: 1000,
  };
  const armed = resolveEscapeHoldCaptureAction(
    { type: "keyDown", key: "Escape" },
    guard,
    { keyDownCaptured: false, holdFired: false },
  );
  assert.equal(armed.action, "arm-hold");
  assert.equal(ESCAPE_HOLD_TO_EXIT_FULLSCREEN_MS, 1500);

  const tap = resolveEscapeHoldCaptureAction(
    { type: "keyUp", key: "Escape" },
    guard,
    armed.nextHoldState,
  );
  assert.equal(tap.action, "tap");
  assert.deepEqual(tap.nextHoldState, { keyDownCaptured: false, holdFired: false });
});

test("resolveEscapeHoldCaptureAction suppresses tap after hold fires", () => {
  const guard = {
    allowEscapeToExitFullscreen: false,
    pointerLockActive: true,
    windowFullscreen: true,
    pointerLockEscapeCaptureUntilMs: 0,
    nowMs: 1000,
  };
  const armed = resolveEscapeHoldCaptureAction(
    { type: "keyDown", key: "Escape" },
    guard,
    { keyDownCaptured: false, holdFired: false },
  );
  const held = markEscapeHoldFired(armed.nextHoldState);
  assert.equal(held.holdFired, true);

  const keyup = resolveEscapeHoldCaptureAction(
    { type: "keyUp", key: "Escape" },
    guard,
    held,
  );
  assert.equal(keyup.action, "hold-consumed-keyup");
});
