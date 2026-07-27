/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  codeMap,
  lockKeysStateFromEvent,
  mapKeyboardEvent,
  mapTextCharToKeySpec,
  modifierFlags,
  shiftModifierByte,
} from "./keyboardMapping";

function keyboardEvent(init: Partial<KeyboardEvent> & Pick<KeyboardEvent, "code" | "key">): KeyboardEvent {
  return {
    code: init.code,
    key: init.key,
    location: init.location ?? 0,
    shiftKey: init.shiftKey ?? false,
    ctrlKey: init.ctrlKey ?? false,
    altKey: init.altKey ?? false,
    metaKey: init.metaKey ?? false,
    keyCode: init.keyCode ?? 0,
    getModifierState: init.getModifierState ?? (() => false),
  } as KeyboardEvent;
}

test("maps keyboard events by physical code with zero protocol scancode", () => {
  assert.deepEqual(
    mapKeyboardEvent(keyboardEvent({ code: "KeyZ", key: "y", keyCode: 89 })),
    { vk: codeMap.KeyZ.vk, scancode: 0 },
  );
  assert.deepEqual(
    mapKeyboardEvent(keyboardEvent({ code: "ControlRight", key: "Control", keyCode: 17 })),
    { vk: 0xa3, scancode: 0 },
  );
});

test("maps synthetic text using the requested keyboard layout", () => {
  assert.deepEqual(mapTextCharToKeySpec("N"), { ...codeMap.KeyN, shift: true });
  assert.deepEqual(mapTextCharToKeySpec("ü", "de-DE"), { ...codeMap.BracketLeft });
  assert.deepEqual(mapTextCharToKeySpec("/", "de-DE"), { ...codeMap.Digit7, shift: true });
});

test("encodes per-key and lock-key modifiers independently", () => {
  const event = keyboardEvent({
    code: "KeyA",
    key: "A",
    shiftKey: true,
    ctrlKey: true,
    getModifierState: (key) => key === "CapsLock" || key === "NumLock",
  });

  assert.equal(shiftModifierByte(event, false), 1);
  assert.equal(modifierFlags(event, false), 0x03);
  assert.equal(lockKeysStateFromEvent(event), 0x73);
});
