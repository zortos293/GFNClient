import assert from "node:assert/strict";
import test from "node:test";

import { resolveStatsShortcutInterception, type StreamShortcutInput } from "./streamShortcutInterception";

const keyDown: StreamShortcutInput = {
  type: "keyDown",
  key: "n",
  code: "KeyN",
  control: true,
  alt: false,
  shift: false,
  meta: false,
  isAutoRepeat: false,
};

test("only dispatches the stats shortcut for an active stream outside capture fields", () => {
  assert.equal(
    resolveStatsShortcutInterception(
      { streamActive: true, shortcutCaptureActive: false },
      keyDown,
      "Ctrl+N",
    ),
    "dispatch",
  );
  assert.equal(
    resolveStatsShortcutInterception(
      { streamActive: false, shortcutCaptureActive: false },
      keyDown,
      "Ctrl+N",
    ),
    "ignore",
  );
  assert.equal(
    resolveStatsShortcutInterception(
      { streamActive: true, shortcutCaptureActive: true },
      keyDown,
      "Ctrl+N",
    ),
    "ignore",
  );
});

test("consumes matching repeats and keyup without dispatching a second toggle", () => {
  assert.equal(
    resolveStatsShortcutInterception(
      { streamActive: true, shortcutCaptureActive: false },
      { ...keyDown, isAutoRepeat: true },
      "Ctrl+N",
    ),
    "consume",
  );
  assert.equal(
    resolveStatsShortcutInterception(
      { streamActive: true, shortcutCaptureActive: false },
      { ...keyDown, type: "keyUp" },
      "Ctrl+N",
    ),
    "consume",
  );
});
