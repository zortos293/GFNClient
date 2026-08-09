import assert from "node:assert/strict";
import test from "node:test";

import { deriveStreamSessionDiagnostics } from "./sessionDiagnostics";

const baseSession = {
  sessionId: "session-123",
  zone: "NP-TYO-01",
  signalingServer: "https://signaling.example.test:443/path",
  streamingBaseUrl: "https://np-tyo-01.cloudmatchbeta.nvidiagrid.net/",
  serverIp: "203.0.113.10",
  gpuType: "RTX 4080",
};

test("preserves session identity, zone, location, and server GPU fields", () => {
  assert.deepEqual(
    deriveStreamSessionDiagnostics({
      ...baseSession,
      serverLocation: "npa-yes-kul-01.example.test:443",
    }),
    {
      sessionId: "session-123",
      serverRegion: "npa-yes-kul-01.example.test",
      serverZone: "NP-TYO-01",
      serverLocation: "npa-yes-kul-01.example.test:443",
      serverGpuType: "RTX 4080",
    },
  );
});

test("falls back through signaling, streaming, and server IP location fields", () => {
  assert.equal(
    deriveStreamSessionDiagnostics({
      ...baseSession,
      serverLocation: undefined,
    }).serverRegion,
    "signaling.example.test",
  );
  assert.equal(
    deriveStreamSessionDiagnostics({
      ...baseSession,
      serverLocation: undefined,
      signalingServer: "",
    }).serverRegion,
    "np-tyo-01.cloudmatchbeta.nvidiagrid.net",
  );
  assert.equal(
    deriveStreamSessionDiagnostics({
      ...baseSession,
      serverLocation: undefined,
      signalingServer: "",
      streamingBaseUrl: "",
    }).serverRegion,
    "203.0.113.10",
  );
});
