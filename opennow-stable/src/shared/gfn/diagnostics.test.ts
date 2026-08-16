/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import {
  iceCandidateDiagnosticSummary,
  sdpDiagnosticSummary,
  signalingUrlForDiagnostics,
  streamDiagnosticId,
} from "./diagnostics";

test("shortens session IDs while preserving a stable correlation key", () => {
  assert.equal(streamDiagnosticId("01234567-89ab-4cde-8fab-0123456789ab"), "0123...6789ab");
  assert.equal(streamDiagnosticId("short-id"), "short-id");
  assert.equal(streamDiagnosticId(null), "-");
});

test("redacts signaling URL secrets and replaces the full session ID", () => {
  const sessionId = "01234567-89ab-4cde-8fab-0123456789ab";
  const result = signalingUrlForDiagnostics(
    `wss://example.test/nvst/sign_in?pairing_id=${sessionId}&access_token=secret&version=2`,
    sessionId,
  );

  assert.doesNotMatch(result, /01234567-89ab-4cde-8fab-0123456789ab|secret/);
  assert.match(result, /0123(?:\.{3}|%2E%2E%2E)6789ab/);
  assert.match(result, /access_token=%5Bredacted%5D/);
});

test("summarizes SDP without copying credentials or the complete payload", () => {
  const sdp = [
    "v=0",
    "m=video 9 UDP/TLS/RTP/SAVPF 96",
    "a=rtpmap:96 H264/90000",
    "a=ice-ufrag:user-secret",
    "a=ice-pwd:password-secret",
    "a=fingerprint:sha-256 fingerprint-secret",
    "a=candidate:1 1 udp 1 192.0.2.10 5000 typ host",
  ].join("\r\n");

  const result = sdpDiagnosticSummary("Received offer", sdp);

  assert.match(result, /lines=7/);
  assert.match(result, /codecs=H264/);
  assert.match(result, /candidates=1/);
  assert.match(result, /ice=true fingerprint=true/);
  assert.doesNotMatch(result, /user-secret|password-secret|fingerprint-secret/);
});

test("summarizes ICE transport without retaining the full candidate line", () => {
  const result = iceCandidateDiagnosticSummary({
    candidate: "candidate:1 1 udp 2122260223 192.0.2.10 5000 typ host generation 0 ufrag private",
    sdpMid: "0",
    sdpMLineIndex: 0,
  });

  assert.equal(result, "mid=0 line=0 type=host protocol=udp address=192.0.2.10:5000");
  assert.doesNotMatch(result, /ufrag|private/);
});
