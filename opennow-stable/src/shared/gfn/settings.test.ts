/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  DEFAULT_SHORTCUT_SETTINGS,
  createDefaultSettings,
  createPlatformShortcutDefaults,
  resolveRuntimePlatform,
} from "./settings";

test("preserves Windows, macOS, and Linux shortcut defaults", () => {
  const windows = createPlatformShortcutDefaults("win32");
  const macOs = createPlatformShortcutDefaults("darwin");
  const linux = createPlatformShortcutDefaults("linux");

  assert.deepEqual(windows.bindings, DEFAULT_SHORTCUT_SETTINGS);
  assert.deepEqual(macOs.bindings, DEFAULT_SHORTCUT_SETTINGS);
  assert.deepEqual(linux.bindings, DEFAULT_SHORTCUT_SETTINGS);
  assert.deepEqual(windows.sidebarToggleAliases, ["Ctrl+G", "Ctrl+Shift+G"]);
  assert.equal(windows.sidebarToggle, "Ctrl+G");
  assert.deepEqual(linux.sidebarToggleAliases, ["Ctrl+G", "Ctrl+Shift+G"]);
  assert.equal(linux.sidebarToggle, "Ctrl+G");
  assert.deepEqual(macOs.sidebarToggleAliases, ["Meta+G"]);
  assert.equal(macOs.sidebarToggle, "Meta+G");
});

test("resolves main and renderer platform names without environment globals", () => {
  assert.equal(resolveRuntimePlatform("win32"), "win32");
  assert.equal(resolveRuntimePlatform("Win32"), "win32");
  assert.equal(resolveRuntimePlatform("MacIntel"), "darwin");
  assert.equal(resolveRuntimePlatform("Linux x86_64"), "linux");
  assert.equal(resolveRuntimePlatform("plan9"), "unknown");
});

test("creates fresh mutable nested settings defaults", () => {
  const first = createDefaultSettings("win32");
  const second = createDefaultSettings("win32");

  assert.notStrictEqual(first, second);
  assert.notStrictEqual(first.favoriteGameIds, second.favoriteGameIds);
  assert.notStrictEqual(first.videoShader, second.videoShader);

  first.favoriteGameIds.push("game-1");
  first.videoShader.sharpen = 0;

  assert.deepEqual(second.favoriteGameIds, []);
  assert.equal(second.videoShader.sharpen, 40);
});

test("creates fresh platform shortcut collections", () => {
  const first = createPlatformShortcutDefaults("linux");
  const second = createPlatformShortcutDefaults("linux");

  assert.notStrictEqual(first.bindings, second.bindings);
  assert.notStrictEqual(first.sidebarToggleAliases, second.sidebarToggleAliases);
  first.bindings.shortcutToggleStats = "F4";
  first.sidebarToggleAliases.push("Alt+G");

  assert.equal(second.bindings.shortcutToggleStats, "F3");
  assert.deepEqual(second.sidebarToggleAliases, ["Ctrl+G", "Ctrl+Shift+G"]);
});
