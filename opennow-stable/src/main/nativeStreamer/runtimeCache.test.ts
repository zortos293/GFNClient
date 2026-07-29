import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  buildPackagedNativeStreamerCacheMarker,
  isSamePackagedNativeStreamerCacheMarker,
  shouldUseStablePackagedNativeStreamerCache,
} from "./runtimeCache";

test("cache markers compare every executable and runtime identity field", (t) => {
  const source = mkdtempSync(join(tmpdir(), "opennow-native-marker-"));
  t.after(() => rmSync(source, { recursive: true, force: true }));
  const runtime = join(source, "gstreamer");
  mkdirSync(runtime);
  writeFileSync(join(source, "opennow-streamer.exe"), "streamer-v1");
  writeFileSync(join(runtime, "OPENNOW-GSTREAMER-RUNTIME.txt"), "runtime-v1");

  const marker = buildPackagedNativeStreamerCacheMarker(
    source,
    "opennow-streamer.exe",
    "win32-x64",
    "1.2.3",
  );
  const sameMarker = buildPackagedNativeStreamerCacheMarker(
    source,
    "opennow-streamer.exe",
    "win32-x64",
    "1.2.3",
  );
  assert.equal(isSamePackagedNativeStreamerCacheMarker(marker, sameMarker), true);

  writeFileSync(join(runtime, "OPENNOW-GSTREAMER-RUNTIME.txt"), "runtime-v2");
  const changedRuntimeMarker = buildPackagedNativeStreamerCacheMarker(
    source,
    "opennow-streamer.exe",
    "win32-x64",
    "1.2.3",
  );
  assert.equal(isSamePackagedNativeStreamerCacheMarker(marker, changedRuntimeMarker), false);
  assert.equal(isSamePackagedNativeStreamerCacheMarker(null, marker), false);
});

test("stable packaged cache selection is constrained to Windows temporary resources", (t) => {
  const temporaryRoot = mkdtempSync(join(tmpdir(), "opennow-native-cache-"));
  t.after(() => rmSync(temporaryRoot, { recursive: true, force: true }));
  const resourcesPath = join(temporaryRoot, "resources");
  mkdirSync(resourcesPath);

  assert.equal(shouldUseStablePackagedNativeStreamerCache({
    isPackaged: true,
    platform: "win32",
    resourcesPath,
    tempDirectory: tmpdir(),
  }), true);
  assert.equal(shouldUseStablePackagedNativeStreamerCache({
    isPackaged: true,
    platform: "linux",
    resourcesPath,
    tempDirectory: tmpdir(),
  }), false);
  assert.equal(shouldUseStablePackagedNativeStreamerCache({
    isPackaged: false,
    platform: "win32",
    resourcesPath,
    tempDirectory: tmpdir(),
  }), false);
});
