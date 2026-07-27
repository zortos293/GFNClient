import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, symlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  isPathInside,
  nativeStreamerExecutableName,
  nativeStreamerPlatformKey,
  normalizePathForComparison,
} from "./runtime";

test("normalizes real and symlinked paths to the same comparison path", (t) => {
  const root = mkdtempSync(join(tmpdir(), "opennow-native-path-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const runtime = join(root, "runtime");
  const alias = join(root, "runtime-alias");
  mkdirSync(runtime);
  mkdirSync(join(runtime, "gstreamer"));
  symlinkSync(runtime, alias, "dir");

  assert.equal(
    normalizePathForComparison(alias),
    normalizePathForComparison(runtime),
  );
  assert.equal(isPathInside(alias, join(runtime, "gstreamer")), true);
});

test("path containment rejects sibling names that only share a prefix", (t) => {
  const root = mkdtempSync(join(tmpdir(), "opennow-native-path-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const runtime = join(root, "runtime");

  assert.equal(isPathInside(runtime, runtime), true);
  assert.equal(isPathInside(runtime, join(runtime, "cached", "streamer")), true);
  assert.equal(isPathInside(runtime, `${runtime}-old/streamer`), false);
});

test("runtime executable names and platform keys accept explicit platforms", () => {
  assert.equal(nativeStreamerExecutableName("win32"), "opennow-streamer.exe");
  assert.equal(nativeStreamerExecutableName("linux"), "opennow-streamer");
  assert.equal(nativeStreamerPlatformKey("darwin", "arm64"), "darwin-arm64");
});
