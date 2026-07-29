/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  extractIceCredentials,
  extractIceUfragFromOffer,
  extractPublicIp,
  fixServerIp,
  rewriteIceCandidateEndpoint,
  rewriteSdpIceCandidateEndpoints,
} from "./ice";

test("fixServerIp replaces 0.0.0.0 candidate IPs without changing connection lines", () => {
  const sdp = [
    "v=0",
    "c=IN IP4 0.0.0.0",
    "a=candidate:1 1 udp 2130706431 0.0.0.0 47998 typ host",
    "a=candidate:2 1 tcp 1 192.168.1.5 9 typ host",
  ].join("\n");

  const fixed = fixServerIp(sdp, "161-248-11-132.bpc.geforcenow.nvidiagrid.net");

  assert.match(fixed, /c=IN IP4 0\.0\.0\.0/);
  assert.match(fixed, /a=candidate:1 1 udp 2130706431 161\.248\.11\.132 47998 typ host/);
  assert.match(fixed, /a=candidate:2 1 tcp 1 192\.168\.1\.5 9 typ host/);
  assert.equal(fixServerIp(sdp, "unparseable.example.com"), sdp);
});

test("rewriteSdpIceCandidateEndpoints points server candidates at WebRTC mediaConnectionInfo", () => {
  const sdp = [
    "v=0",
    "c=IN IP4 0.0.0.0",
    "a=candidate:1 1 udp 2122260223 203.0.113.10 47998 typ host",
    "a=candidate:2 1 tcp 1518214911 203.0.113.10 9 typ host tcptype active",
  ].join("\r\n");

  const rewritten = rewriteSdpIceCandidateEndpoints(sdp, {
    ip: "198.51.100.55",
    port: 18784,
    usage: 2,
  });

  assert.equal(rewritten.replacements, 2);
  assert.match(rewritten.sdp, /a=candidate:1 1 udp 2122260223 198\.51\.100\.55 18784 typ host/);
  assert.match(rewritten.sdp, /a=candidate:2 1 tcp 1518214911 198\.51\.100\.55 18784 typ host tcptype active/);
  assert.match(rewritten.sdp, /c=IN IP4 0\.0\.0\.0/);
  assert.match(rewritten.sdp, /\r\n/);
});

test("rewriteIceCandidateEndpoint skips non-WebRTC mediaConnectionInfo usages", () => {
  const candidate = "candidate:1 1 udp 2122260223 203.0.113.10 47998 typ host";

  assert.deepEqual(
    rewriteIceCandidateEndpoint(candidate, { ip: "198.51.100.55", port: 18784, usage: 14 }),
    { candidate, rewritten: false },
  );
  assert.deepEqual(
    rewriteIceCandidateEndpoint(candidate, { ip: "198.51.100.55", port: 18784, usage: 17 }),
    {
      candidate: "candidate:1 1 udp 2122260223 198.51.100.55 18784 typ host",
      rewritten: true,
    },
  );
});

test("ICE extraction uses the first credentials and accepts GFN hostnames", () => {
  const offer = [
    "a=ice-ufrag: session-ufrag ",
    "a=ice-pwd: session-password ",
    "a=fingerprint:sha-256 AA:BB:CC ",
    "m=video 9 UDP/TLS/RTP/SAVPF 98",
    "a=ice-ufrag:media-ufrag",
  ].join("\r\n");

  assert.deepEqual(extractIceCredentials(offer), {
    ufrag: "session-ufrag",
    pwd: "session-password",
    fingerprint: "AA:BB:CC",
  });
  assert.equal(extractIceUfragFromOffer(offer), "session-ufrag");
  assert.equal(extractPublicIp("80-250-97-40.cloudmatchbeta.nvidiagrid.net"), "80.250.97.40");
  assert.equal(extractPublicIp("198.51.100.7"), "198.51.100.7");
  assert.equal(extractPublicIp("not-an-ip.example.com"), null);
});

test("candidate rewriting preserves malformed and invalid-endpoint input exactly", () => {
  const sdp = "v=0\r\na=candidate:malformed\r\n";

  assert.deepEqual(
    rewriteSdpIceCandidateEndpoints(sdp, { ip: "198.51.100.55", port: 0, usage: 2 }),
    { sdp, replacements: 0 },
  );
  assert.deepEqual(
    rewriteIceCandidateEndpoint("candidate:malformed", {
      ip: "198.51.100.55",
      port: 18784,
      usage: 2,
    }),
    { candidate: "candidate:malformed", rewritten: false },
  );
});
