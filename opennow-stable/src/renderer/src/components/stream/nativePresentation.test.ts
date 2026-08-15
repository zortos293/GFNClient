import assert from "node:assert/strict";
import test from "node:test";

import { usesNativeInternalSurface } from "./nativePresentation";

test("keeps the internal surface open while native video connects", () => {
  assert.equal(usesNativeInternalSurface({
    nativeRendererActive: false,
    nativeStreamingEnabled: true,
    connecting: true,
    externalRenderer: false,
  }), true);
});

test("removes the native hole when a web fallback becomes active", () => {
  assert.equal(usesNativeInternalSurface({
    nativeRendererActive: false,
    nativeStreamingEnabled: true,
    connecting: false,
    externalRenderer: false,
  }), false);
});

test("keeps an active native renderer visible after connection", () => {
  assert.equal(usesNativeInternalSurface({
    nativeRendererActive: true,
    nativeStreamingEnabled: true,
    connecting: false,
    externalRenderer: false,
  }), true);
});
