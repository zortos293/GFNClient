import test from "node:test";
import assert from "node:assert/strict";

import {
  parseExplicitExternalUrl,
  parseExternalHttpUrl,
} from "./externalUrlPolicy";

test("automatic external URLs remain limited to HTTP(S)", () => {
  assert.equal(parseExternalHttpUrl("https://store.steampowered.com/app/1").protocol, "https:");
  assert.throws(() => parseExternalHttpUrl("steam://store/1"));
  assert.throws(() => parseExternalHttpUrl("file:///tmp/example"));
  assert.throws(() => parseExternalHttpUrl("javascript:alert(1)"));
});

test("explicit external URLs allow only known launchers for Windows", () => {
  assert.equal(parseExplicitExternalUrl("steam://store/1", "win32").protocol, "steam:");
  assert.equal(
    parseExplicitExternalUrl("com.epicgames.launcher://store/product/example", "win32").protocol,
    "com.epicgames.launcher:",
  );
  assert.equal(
    parseExplicitExternalUrl("ms-windows-store://pdp/?productid=example", "win32").protocol,
    "ms-windows-store:",
  );
  assert.throws(() => parseExplicitExternalUrl("macappstore://itunes.apple.com/app/id1", "win32"));
});

test("explicit external URLs allow only known launchers for macOS", () => {
  assert.equal(parseExplicitExternalUrl("steam://store/1", "darwin").protocol, "steam:");
  assert.equal(
    parseExplicitExternalUrl("itms-apps://itunes.apple.com/app/id1", "darwin").protocol,
    "itms-apps:",
  );
  assert.equal(
    parseExplicitExternalUrl("macappstore://itunes.apple.com/app/id1", "darwin").protocol,
    "macappstore:",
  );
  assert.throws(() => parseExplicitExternalUrl("ms-windows-store://pdp/", "darwin"));
});

test("explicit external URLs reject arbitrary and dangerous schemes", () => {
  for (const url of [
    "file:///tmp/example",
    "javascript:alert(1)",
    "data:text/html,example",
    "arbitrary-launcher://store/example",
  ]) {
    assert.throws(() => parseExplicitExternalUrl(url, "win32"));
    assert.throws(() => parseExplicitExternalUrl(url, "darwin"));
  }
  assert.throws(() => parseExplicitExternalUrl("steam://store/1", "linux"));
});
