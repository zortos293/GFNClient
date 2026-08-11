/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  hasConnectedGamepad,
  resolveControllerModePromptAction,
  shouldOfferControllerModePrompt,
} from "./useControllerModePrompt";
import { controllerButton } from "../utils/controllerGamepad";

const ELIGIBLE = {
  settingsLoaded: true,
  controllerMode: false,
  directLaunchConsoleMode: false,
  promptDismissed: false,
};

test("offers controller mode only from a loaded desktop session that has not opted out", () => {
  assert.equal(shouldOfferControllerModePrompt(ELIGIBLE), true);
  assert.equal(shouldOfferControllerModePrompt({ ...ELIGIBLE, settingsLoaded: false }), false);
  assert.equal(shouldOfferControllerModePrompt({ ...ELIGIBLE, controllerMode: true }), false);
  assert.equal(shouldOfferControllerModePrompt({ ...ELIGIBLE, directLaunchConsoleMode: true }), false);
  assert.equal(shouldOfferControllerModePrompt({ ...ELIGIBLE, promptDismissed: true }), false);
});

test("recognizes only connected gamepads", () => {
  const connected = { connected: true } as Gamepad;
  const disconnected = { connected: false } as Gamepad;

  assert.equal(hasConnectedGamepad(undefined), false);
  assert.equal(hasConnectedGamepad([null, disconnected]), false);
  assert.equal(hasConnectedGamepad([null, connected]), true);
});

test("maps A to switch mode and B to decline", () => {
  assert.equal(resolveControllerModePromptAction(controllerButton.south), "accept");
  assert.equal(resolveControllerModePromptAction(controllerButton.east), "decline");
  assert.equal(resolveControllerModePromptAction(controllerButton.menu), null);
});
