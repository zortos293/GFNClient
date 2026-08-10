import assert from "node:assert/strict";
import test from "node:test";

import {
  StreamEscapeShortcutController,
  type GlobalShortcutRegistrar,
} from "./streamEscapeShortcut";

class FakeShortcutRegistrar implements GlobalShortcutRegistrar {
  callback: (() => void) | null = null;
  registerCalls = 0;
  unregisterCalls = 0;
  shouldRegister = true;

  register(accelerator: string, callback: () => void): boolean {
    assert.equal(accelerator, "Escape");
    this.registerCalls += 1;
    if (this.shouldRegister) this.callback = callback;
    return this.shouldRegister;
  }

  unregister(accelerator: string): void {
    assert.equal(accelerator, "Escape");
    this.unregisterCalls += 1;
    this.callback = null;
  }
}

test("stream Escape shortcut captures once and releases with pointer lock", () => {
  const registrar = new FakeShortcutRegistrar();
  let escapeTaps = 0;
  const shortcut = new StreamEscapeShortcutController(registrar, () => {
    escapeTaps += 1;
  });

  assert.equal(shortcut.setCaptureActive(true), true);
  assert.equal(shortcut.setCaptureActive(true), true);
  assert.equal(registrar.registerCalls, 1);
  registrar.callback?.();
  assert.equal(escapeTaps, 1);

  assert.equal(shortcut.setCaptureActive(false), false);
  assert.equal(registrar.unregisterCalls, 1);
  assert.equal(registrar.callback, null);
});

test("stream Escape shortcut leaves the native fallback available when registration fails", () => {
  const registrar = new FakeShortcutRegistrar();
  registrar.shouldRegister = false;
  const shortcut = new StreamEscapeShortcutController(registrar, () => {});

  assert.equal(shortcut.setCaptureActive(true), false);
  assert.equal(shortcut.setCaptureActive(true), false);
  assert.equal(registrar.registerCalls, 2);
  assert.equal(registrar.unregisterCalls, 0);
});
