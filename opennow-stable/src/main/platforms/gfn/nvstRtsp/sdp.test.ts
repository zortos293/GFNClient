/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildAnnounceSdp,
  extractHmacSeed,
  extractRuntimeEncryptionKey,
  packSrtpMasterKeySalt,
} from "./sdp";

test("extractHmacSeed reads DESCRIBE k= line", () => {
  const seed = extractHmacSeed(
    "v=0\r\nk=HMAC:76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F\r\na=x-nv-general.rtspWebSocketPerConnection:1\r\n",
  );
  assert.equal(seed, "76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F");
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
