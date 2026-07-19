import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  keyboardLayoutOptions,
  resolveGfnKeyboardLayout,
} from "./keyboard";

describe("resolveGfnKeyboardLayout", () => {
  const scandinavianLayouts = [
    { value: "da-DK", macValue: "m-da" },
    { value: "nb-NO", macValue: "m-no" },
    { value: "sv-SE", macValue: "m-sv" },
    { value: "fi-FI", macValue: "m-fi" },
  ] as const;

  it("exposes Scandinavian layouts matching official GFN key codes", () => {
    for (const expected of scandinavianLayouts) {
      const option = keyboardLayoutOptions.find((candidate) => candidate.value === expected.value);
      assert.ok(option, `missing layout option ${expected.value}`);
      assert.equal(option.macValue, expected.macValue);
    }
  });

  it("sends Windows layout IDs unchanged", () => {
    for (const { value } of scandinavianLayouts) {
      assert.equal(resolveGfnKeyboardLayout(value, "win32"), value);
      assert.equal(resolveGfnKeyboardLayout(value, "linux"), value);
    }
  });

  it("maps Scandinavian layouts to official mac key codes on darwin", () => {
    for (const { value, macValue } of scandinavianLayouts) {
      assert.equal(resolveGfnKeyboardLayout(value, "darwin"), macValue);
    }
  });
});
