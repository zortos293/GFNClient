/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import { applyNativeAppTheme } from "./windowTheme";

test("native app theme resolves explicit and system window backgrounds", () => {
  let source: "system" | "light" | "dark" = "system";
  let systemDark = true;
  const nativeTheme = {
    get themeSource(): "system" | "light" | "dark" {
      return source;
    },
    set themeSource(value: "system" | "light" | "dark") {
      source = value;
    },
    get shouldUseDarkColors(): boolean {
      return source === "system" ? systemDark : source === "dark";
    },
  };

  assert.equal(applyNativeAppTheme("light", nativeTheme), "#f8fafc");
  assert.equal(nativeTheme.themeSource, "light");

  assert.equal(applyNativeAppTheme("dark", nativeTheme), "#101014");
  assert.equal(nativeTheme.themeSource, "dark");

  assert.equal(applyNativeAppTheme("auto", nativeTheme), "#101014");
  assert.equal(nativeTheme.themeSource, "system");

  systemDark = false;
  assert.equal(applyNativeAppTheme("auto", nativeTheme), "#f8fafc");
});
