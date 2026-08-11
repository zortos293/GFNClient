import assert from "node:assert/strict";
import test from "node:test";

import {
  deriveStreamSessionDiagnostics,
  formatServerLocation,
  getStreamServerLocationLabel,
  mapServerGpuType,
} from "./sessionDiagnostics";

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

test("prefers parsed location metadata over a raw CloudMatch zone URL", () => {
  assert.equal(
    getStreamServerLocationLabel({
      serverLocation: "Amsterdam",
      serverRegion: "https://np-ams-06.cloudmatchbeta.nvidiagrid.net/",
      serverZone: "https://prod.cloudmatchbeta.nvidiagrid.net/",
    }),
    "Amsterdam",
  );
  assert.equal(
    getStreamServerLocationLabel({
      serverLocation: "",
      serverRegion: "https://np-ams-06.cloudmatchbeta.nvidiagrid.net/",
      serverZone: "https://prod.cloudmatchbeta.nvidiagrid.net/",
    }),
    "Netherlands (NP-AMS-06)",
  );
});

test("formats partner hostnames and zone ids like the official client", () => {
  assert.equal(
    formatServerLocation("prod", "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net"),
    "Malaysia (NP-KUL-01)",
  );
  assert.equal(formatServerLocation("NP-TYO-01", ""), "Japan (NP-TYO-01)");
  assert.equal(
    formatServerLocation("prod", "183-78-14-236.yes.geforcenow.nvidiagrid.net"),
    "--",
  );
});

test("maps CloudMatch GPU codes and preserves unknown names", () => {
  assert.equal(mapServerGpuType("2080d / T10"), "GeForce RTX");
  assert.equal(mapServerGpuType("3080p / A10Gx2"), "GeForce RTX 3080");
  assert.equal(mapServerGpuType("4080h / L40S"), "GeForce RTX 4080");
  assert.equal(mapServerGpuType("5080h / B40"), "GeForce RTX 5080");
  assert.equal(mapServerGpuType("1060b / T10-8"), "Basic Rig");
  assert.equal(mapServerGpuType("Custom Rig"), "Custom Rig");
  assert.equal(mapServerGpuType("   "), "");
});

test("derives a friendly server GPU name from session metadata", () => {
  assert.equal(
    deriveStreamSessionDiagnostics({
      ...baseSession,
      serverLocation: "np-tyo-01.cloudmatchbeta.nvidiagrid.net",
      gpuType: "4080h / L40S",
    }).serverGpuType,
    "GeForce RTX 4080",
  );
});
