/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { normalizeShortcut, shortcutFromKeyboardEvent } from "./shortcuts";

function keyboardEvent(input: Partial<KeyboardEvent>): KeyboardEvent {
  return {
    key: "",
    code: "",
    ctrlKey: false,
    altKey: false,
    shiftKey: false,
    metaKey: false,
    repeat: false,
    ...input,
  } as KeyboardEvent;
}

test("shortcut capture falls back to event.key for unknown Electron codes", () => {
  assert.equal(
    shortcutFromKeyboardEvent(keyboardEvent({
      key: "q",
      code: "ElectronWindow",
      ctrlKey: true,
      shiftKey: true,
    })),
    "Ctrl+Shift+Q",
  );
});

test("normalization rejects unknown named key tokens", () => {
  assert.equal(normalizeShortcut("Ctrl+Shift+ElectronWindow").valid, false);
});

test("normalization canonicalizes function keys and rejects out-of-range function keys", () => {
  assert.equal(normalizeShortcut("Ctrl+F1").canonical, "Ctrl+F1");
  assert.equal(normalizeShortcut("Ctrl+F01").canonical, "Ctrl+F1");
  assert.equal(normalizeShortcut("Ctrl+F24").valid, true);
  assert.equal(normalizeShortcut("Ctrl+F25").valid, false);
});

test("normalization accepts numpad digit tokens", () => {
  const parsed = normalizeShortcut("Ctrl+Numpad5");
  assert.equal(parsed.valid, true);
  assert.equal(parsed.key, "NUMPAD5");
});

test("shortcut capture still prefers physical key codes for supported keys", () => {
  assert.equal(
    shortcutFromKeyboardEvent(keyboardEvent({
      key: "a",
      code: "KeyQ",
      ctrlKey: true,
    })),
    "Ctrl+Q",
  );
});
