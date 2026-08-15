import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, symlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  createNativeStreamerRuntimeEnvironment,
  isNativeWaylandSession,
  isPathInside,
  nativeStreamerExecutableName,
  nativeStreamerPlatformKey,
  normalizePathForComparison,
  shouldDefaultLinuxShellToX11,
} from "./runtime";

function createLinuxRuntimeEnvironment(
  baseEnv: NodeJS.ProcessEnv,
  linuxOzonePlatform?: string,
): NodeJS.ProcessEnv {
  return createNativeStreamerRuntimeEnvironment({
    executablePath: "/tmp/opennow-streamer",
    baseEnv,
    platform: "linux",
    arch: "arm64",
    userDataPath: "/tmp/opennow-test",
    protocolVersion: 4,
    backendPreference: "auto",
    videoBackendPreference: "auto",
    externalRendererEnabled: false,
    linuxOzonePlatform,
    cloudGsyncMode: "auto",
    d3dFullscreenMode: "auto",
  }).env;
}

test("detects native Wayland sessions", () => {
  assert.equal(isNativeWaylandSession({ WAYLAND_DISPLAY: "wayland-0" }), true);
  assert.equal(isNativeWaylandSession({ XDG_SESSION_TYPE: "wayland" }), true);
  assert.equal(isNativeWaylandSession({}, "wayland"), true);
});

test("explicit X11 keeps Linux child-surface embedding enabled", () => {
  const waylandEnvironment = {
    ELECTRON_OZONE_PLATFORM_HINT: "wayland",
    WAYLAND_DISPLAY: "wayland-0",
    XDG_SESSION_TYPE: "wayland",
  };

  assert.equal(isNativeWaylandSession(waylandEnvironment, "x11"), false);
  assert.equal(isNativeWaylandSession({ XDG_SESSION_TYPE: "x11" }), false);
  assert.equal(
    isNativeWaylandSession({ ELECTRON_OZONE_PLATFORM_HINT: "x11" }),
    false,
  );
});

test("Linux native runtime rejects unmanaged pure Wayland presentation", () => {
  assert.throws(
    () => createLinuxRuntimeEnvironment({ WAYLAND_DISPLAY: "wayland-0" }),
    /requires Electron to run through X11\/XWayland/,
  );
  assert.equal(
    createLinuxRuntimeEnvironment(
      { WAYLAND_DISPLAY: "wayland-0" },
      "x11",
    ).OPENNOW_NATIVE_EXTERNAL_RENDERER,
    "0",
  );
});

test("Linux shell defaults to X11 unless an Ozone backend was explicit", () => {
  assert.equal(shouldDefaultLinuxShellToX11("linux", ""), true);
  assert.equal(shouldDefaultLinuxShellToX11("linux", undefined), true);
  assert.equal(shouldDefaultLinuxShellToX11("linux", "auto"), true);
  assert.equal(shouldDefaultLinuxShellToX11("linux", "wayland"), false);
  assert.equal(shouldDefaultLinuxShellToX11("win32", ""), false);
});

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
