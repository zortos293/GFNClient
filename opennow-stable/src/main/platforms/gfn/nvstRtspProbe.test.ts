/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildAnnounceSdp,
  buildEmptyPathUpgradeRequest,
  buildNvstWssUpgradeRequest,
  buildNvstWssUpgradeRequestTarget,
  collectRtspsEndpoints,
  extractHmacSeed,
  extractRuntimeEncryptionKey,
  extractVideoPeer,
  packSrtpMasterKeySalt,
  rtspsUrlToWssUrl,
  selectPrimaryRtspsEndpoint,
} from "./nvstRtspProbe";

test("selectPrimaryRtspsEndpoint prefers :322", () => {
  const selected = selectPrimaryRtspsEndpoint([
    "rtsps://host.example:48322",
    "rtsps://host.example:322",
  ]);
  assert.equal(selected, "rtsps://host.example:322");
});

test("rtspsUrlToWssUrl is host:port with no path (empty upgrade path is manual)", () => {
  assert.equal(
    rtspsUrlToWssUrl("rtsps://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322"),
    "wss://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322",
  );
});

test("buildNvstWssUpgradeRequest uses Bifrost-shaped GET / by default", () => {
  const request = buildNvstWssUpgradeRequest(
    "80-250-97-40.cloudmatchbeta.nvidiagrid.net",
    322,
    "dGVzdGtleTEyMzQ1Njc4OQ==",
  );
  const requestLine = request.split("\r\n")[0] ?? "";
  assert.equal(requestLine, "GET / HTTP/1.1");
  assert.equal(Buffer.from(requestLine, "utf8").toString("hex"), "474554202f20485454502f312e31");
  assert.equal(buildNvstWssUpgradeRequestTarget("host.example", 322, "slash"), "/");
  assert.equal(
    buildNvstWssUpgradeRequestTarget("host.example", 322, "sessionPath", "abc-uuid"),
    "/v2/session/abc-uuid",
  );
  assert.match(request, /^GET \/ HTTP\/1\.1\r\nHost: 80-250-97-40\.cloudmatchbeta\.nvidiagrid\.net:322\r\n/);
  assert.match(request, /\r\nConnection: Upgrade\r\n/);
  assert.match(request, /\r\nUpgrade: websocket\r\n/);
  assert.match(request, /\r\nSec-WebSocket-Version: 13\r\n/);
  assert.match(request, /\r\nSec-WebSocket-Key: dGVzdGtleTEyMzQ1Njc4OQ==\r\n/);
  assert.match(request, /\r\nContent-Length: 0\r\n\r\n$/);
  assert.doesNotMatch(request, /Sec-WebSocket-Protocol/i);
  assert.doesNotMatch(request, /User-Agent/i);
  assert.doesNotMatch(request, /x-nv-sessionid/i);
});

test("buildEmptyPathUpgradeRequest keeps empty URI for research (live → 400)", () => {
  const request = buildEmptyPathUpgradeRequest(
    "80-250-97-40.cloudmatchbeta.nvidiagrid.net",
    322,
    "dGVzdGtleTEyMzQ1Njc4OQ==",
  );
  const requestLine = request.split("\r\n")[0] ?? "";
  assert.equal(requestLine, "GET  HTTP/1.1");
  assert.equal(Buffer.from(requestLine, "utf8").toString("hex"), "4745542020485454502f312e31");
});

test("buildNvstWssUpgradeRequest can attach x-nv-sessionid for 403 retry", () => {
  const request = buildNvstWssUpgradeRequest("host.example", 322, "abc", {
    form: "slash",
    sessionId: "sess-uuid",
  });
  assert.match(request, /\r\nx-nv-sessionid: sess-uuid\r\n/);
});

test("collectRtspsEndpoints keeps both usage=14 paths", () => {
  const endpoints = collectRtspsEndpoints(
    [
      {
        usage: 14,
        port: 322,
        resourcePath: "rtsps://host.example:322",
      },
      {
        usage: 14,
        port: 48322,
        resourcePath: "rtsps://host.example:48322",
      },
      {
        usage: 2,
        port: 49006,
        resourcePath: null,
      },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});

test("collectRtspsEndpoints synthesizes from port when resourcePath missing", () => {
  const endpoints = collectRtspsEndpoints(
    [
      { usage: 14, port: 322, resourcePath: null },
      { usage: 14, port: 48322, resourcePath: null },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});

test("extractHmacSeed reads DESCRIBE k= line", () => {
  const seed = extractHmacSeed(
    "v=0\r\nk=HMAC:76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F\r\na=x-nv-general.rtspWebSocketPerConnection:1\r\n",
  );
  assert.equal(seed, "76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F");
});

test("extractVideoPeer prefers SETUP X-GS-ServerPort", () => {
  assert.deepEqual(
    extractVideoPeer("unicast;X-GS-ServerPort=5004-5005;source=80.250.97.37"),
    { ip: "80.250.97.37", port: 5004 },
  );
});

test("buildAnnounceSdp uses allowlist shape and omits ICE/DTLS", () => {
  const sdp = buildAnnounceSdp({ resolution: "1920x1080", fps: 60 });
  assert.match(sdp, /a=x-nv-video\[0\]\.clientViewportWd:1920/);
  assert.match(sdp, /a=x-nv-video\[0\]\.maxFPS:60/);
  assert.match(sdp, /a=x-nv-general\.controlProtocol:udp_ag/);
  assert.doesNotMatch(sdp, /iceUsernameFragment|dtlsFingerprint/);
});

test("packSrtpMasterKeySalt matches geronimo keyId packing", () => {
  const aes = `${"1C98".padEnd(60, "0")}07D2`;
  const packed = packSrtpMasterKeySalt(aes, 2664076126);
  assert.equal(packed.length, 88);
  assert.equal(packed.slice(0, 64), aes.toUpperCase());
  assert.equal(packed.slice(64), "00000000000000009ECA935E");
});

test("extractRuntimeEncryptionKey reads DESCRIBE attrs", () => {
  const sdp = [
    "v=0",
    "a=x-nv-runtime.encryptionKey:AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
    "a=x-nv-runtime.encryptionKeyId:-1630891170",
    "",
  ].join("\r\n");
  const parsed = extractRuntimeEncryptionKey(sdp);
  assert.ok(parsed);
  assert.equal(parsed?.keyId, 2664076126);
});
