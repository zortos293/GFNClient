import assert from "node:assert/strict";
import test from "node:test";

import { isShortcutCaptureTarget } from "./shortcutCaptureFocus";

function target(...classes: string[]) {
  return {
    classList: {
      contains: (value: string) => classes.includes(value),
    },
  };
}

test("recognizes editable settings and quick-menu shortcut capture fields", () => {
  assert.equal(isShortcutCaptureTarget(target("settings-shortcut-input")), true);
  assert.equal(
    isShortcutCaptureTarget(target("settings-shortcut-input", "sidebar-shortcut-input")),
    true,
  );
});

test("ignores static shortcut displays and unrelated inputs", () => {
  assert.equal(
    isShortcutCaptureTarget(target("settings-shortcut-input", "settings-shortcut-input--static")),
    false,
  );
  assert.equal(isShortcutCaptureTarget(target("settings-text-input")), false);
  assert.equal(isShortcutCaptureTarget(null), false);
});
