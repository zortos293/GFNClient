/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import type { SessionInfo } from "@shared/gfn";

import { mergePolledSessionState } from "./queueAds";

function session(overrides: Partial<SessionInfo> = {}): SessionInfo {
  return {
    sessionId: "session-1",
    status: 2,
    zone: "prod",
    serverIp: "server",
    signalingServer: "signal",
    signalingUrl: "wss://signal/nvst/",
    iceServers: [],
    ...overrides,
  };
}

test("preserves session GPU and region metadata when a ready poll omits them", () => {
  const merged = mergePolledSessionState(
    session({
      gpuType: "2080d / T10",
      serverLocation: "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net",
    }),
    session(),
  );

  assert.equal(merged.gpuType, "2080d / T10");
  assert.equal(merged.serverLocation, "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net");
});

test("preserves session GPU and region metadata while a session is in setup", () => {
  const merged = mergePolledSessionState(
    session({ status: 1, gpuType: "RTX", serverLocation: "np-tyo-01.cloudmatchbeta.nvidiagrid.net" }),
    session({ status: 1, gpuType: "", serverLocation: "" }),
  );

  assert.equal(merged.gpuType, "RTX");
  assert.equal(merged.serverLocation, "np-tyo-01.cloudmatchbeta.nvidiagrid.net");
});

test("uses later non-empty session GPU and region metadata", () => {
  const merged = mergePolledSessionState(
    session({ gpuType: "RTX", serverLocation: "np-lax-01.cloudmatchbeta.nvidiagrid.net" }),
    session({ gpuType: "5080h / B40", serverLocation: "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net" }),
  );

  assert.equal(merged.gpuType, "5080h / B40");
  assert.equal(merged.serverLocation, "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net");
});

test("leaves session GPU and region metadata absent when never reported", () => {
  const merged = mergePolledSessionState(session(), session());

  assert.equal(merged.gpuType, undefined);
  assert.equal(merged.serverLocation, undefined);
});
