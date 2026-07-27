/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  GAMEPAD_A,
  GAMEPAD_DEADZONE,
  GAMEPAD_DPAD_UP,
  GAMEPAD_GUIDE,
  applyDeadzone,
  mapGamepadButtons,
  normalizeToInt16,
  normalizeToUint8,
  readGamepadAxes,
} from "./gamepadMapping";

function gamepad(overrides: Partial<Gamepad> = {}): Gamepad {
  return {
    axes: [0, 0, 0, 0],
    buttons: Array.from({ length: 17 }, () => ({ pressed: false, touched: false, value: 0 })),
    connected: true,
    hapticActuators: [],
    id: "test-gamepad",
    index: 0,
    mapping: "standard",
    timestamp: 0,
    vibrationActuator: null,
    ...overrides,
  } as Gamepad;
}

test("applies the radial gamepad deadzone and preserves direction", () => {
  assert.deepEqual(applyDeadzone(GAMEPAD_DEADZONE / 2, 0), { x: 0, y: 0 });
  const adjusted = applyDeadzone(0.3, 0.4);
  assert.ok(adjusted.x > 0);
  assert.ok(adjusted.y > adjusted.x);
});

test("maps nonzero standard button values to XInput flags", () => {
  const buttons = Array.from({ length: 17 }, () => ({ pressed: false, touched: false, value: 0 }));
  for (const index of [0, 12, 16]) {
    buttons[index].value = 0.25;
  }

  assert.equal(mapGamepadButtons(gamepad({ buttons })), GAMEPAD_A | GAMEPAD_DPAD_UP | GAMEPAD_GUIDE);
});

test("normalizes axes, inversion, triggers, and numeric limits", () => {
  const axes = readGamepadAxes(gamepad({
    axes: [0, 1, 0, -1],
    buttons: Array.from({ length: 8 }, (_, index) => ({
      pressed: false,
      touched: false,
      value: index === 6 ? 0.5 : index === 7 ? 0.75 : 0,
    })),
  }));

  assert.equal(axes.leftStickY, -1);
  assert.equal(axes.rightStickY, 1);
  assert.equal(axes.leftTrigger, 0.5);
  assert.equal(axes.rightTrigger, 0.75);
  assert.equal(normalizeToInt16(-2), -32768);
  assert.equal(normalizeToInt16(2), 32767);
  assert.equal(normalizeToUint8(-1), 0);
  assert.equal(normalizeToUint8(2), 255);
});
