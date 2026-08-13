import assert from "node:assert/strict";
import test from "node:test";
import { resolveAppLaunchMode } from "./appLaunchMode";

test("explicit session identity requests gamepad-friendly GFN launch mode", () => {
  assert.equal(resolveAppLaunchMode({
    controllerMode: false,
    requestGamepadFriendlySession: true,
    directLaunchConsoleMode: false,
  }), "gamepadFriendly");
});

test("OpenNOW controller shell and direct launches keep gamepad-friendly sessions", () => {
  assert.equal(resolveAppLaunchMode({
    controllerMode: true,
    requestGamepadFriendlySession: false,
    directLaunchConsoleMode: false,
  }), "gamepadFriendly");
  assert.equal(resolveAppLaunchMode({
    controllerMode: false,
    requestGamepadFriendlySession: false,
    directLaunchConsoleMode: true,
  }), "gamepadFriendly");
});

test("desktop shell without an explicit session request uses default GFN launch mode", () => {
  assert.equal(resolveAppLaunchMode({
    controllerMode: false,
    requestGamepadFriendlySession: false,
    directLaunchConsoleMode: false,
  }), "default");
});
