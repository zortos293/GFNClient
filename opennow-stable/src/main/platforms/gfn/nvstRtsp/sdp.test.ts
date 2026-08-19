/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildAnnounceSdp,
  extractHmacSeed,
  extractNvstIceCredentials,
  extractNvstSdpAttribute,
  extractMediaControl,
  extractRuntimeEncryptionKey,
  generateNvstIceCredentials,
  packSrtpMasterKeySalt,
} from "./sdp";

test("extractNvstIceCredentials reads native and standard SDP forms", () => {
  assert.deepEqual(
    extractNvstIceCredentials(
      "a=x-nv-general.iceUsernameFragment:native-user\r\na=x-nv-general.iceUsernamePwd:native-password\r\n",
    ),
    { usernameFragment: "native-user", password: "native-password" },
  );
  assert.deepEqual(
    extractNvstIceCredentials("a=ice-ufrag:standard-user\na=ice-pwd:standard-password\n"),
    { usernameFragment: "standard-user", password: "standard-password" },
  );
  assert.deepEqual(
    extractNvstIceCredentials(
      "a=x-nv-general.iceUserNameFragmentV2:native-v2-user\r\na=x-nv-general.icePasswordV2:native-v2-password\r\n",
    ),
    { usernameFragment: "native-v2-user", password: "native-v2-password" },
  );
  assert.equal(extractNvstIceCredentials("a=ice-ufrag:incomplete\n"), null);
});

test("extractHmacSeed reads DESCRIBE k= line", () => {
  const seed = extractHmacSeed(
    "v=0\r\nk=HMAC:76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F\r\na=x-nv-general.rtspWebSocketPerConnection:1\r\n",
  );
  assert.equal(seed, "76A28E94D8C07CB67C04C29CFAAAAF64BE4BA0899456217CB73D070E5060965F");
});

test("extractNvstSdpAttribute reads DESCRIBE transport fields with or without x-nv-", () => {
  assert.equal(
    extractNvstSdpAttribute(
      "a=x-nv-general.serverTransport:192.0.2.20:5004\r\na=x-nv-general.useNewIceInfo:0\r\n",
      "general.serverTransport",
    ),
    "192.0.2.20:5004",
  );
  assert.equal(
    extractNvstSdpAttribute("a=general.useNewIceInfo:0\n", "general.useNewIceInfo"),
    "0",
  );
  assert.equal(extractNvstSdpAttribute("a=general.clientTransport:\n", "general.clientTransport"), null);
});

test("buildAnnounceSdp uses Bifrost session and attribute shape", () => {
  const sdp = buildAnnounceSdp({
    resolution: "1920x1080",
    fps: 60,
    videoPort: 5004,
    clientPorts: { video: 45000, audio: 45002, control: 45004 },
  });
  assert.match(sdp, /a=x-nv-video\[0\]\.clientViewportWd:1920/);
  assert.match(sdp, /a=x-nv-video\[0\]\.maxFPS:60/);
  assert.match(sdp, /a=x-nv-general\.clientPorts\.video:45000/);
  assert.match(sdp, /a=x-nv-general\.clientPorts\.audio:45002/);
  assert.match(sdp, /a=x-nv-general\.clientPorts\.control:45004/);
  assert.match(sdp, /m=video 5004\r\ni=DeviceString, DeviceName/);
  assert.doesNotMatch(sdp, /RTP\/AVP|msid:video_0|clientTransport|nativeRtcOnBundlePort|iceUsernameFragment|dtlsFingerprint|controlProtocol/);
});

test("buildAnnounceSdp echoes nativeRtcOnBundlePort when the server advertised it", () => {
  const sdp = buildAnnounceSdp({
    videoPort: 5004,
    nativeRtcOnBundlePort: "1",
    clientPorts: { video: 45000, audio: 45000, control: 45000, bundle: 45000 },
  });
  assert.match(sdp, /a=x-nv-general\.nativeRtcOnBundlePort:1/);
  assert.match(sdp, /a=x-nv-general\.clientPorts\.bundle:45000/);
});

test("buildAnnounceSdp includes clientTransport only when provided", () => {
  const sdp = buildAnnounceSdp({
    videoPort: 5004,
    clientTransport: "192.0.2.8:45000",
  });
  assert.match(sdp, /a=x-nv-general\.clientTransport:192\.0\.2\.8:45000/);
});

test("buildAnnounceSdp includes DTLS fingerprint V1 and V2 when provided", () => {
  const fingerprint = "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF";
  const sdp = buildAnnounceSdp({ dtlsFingerprint: fingerprint });
  assert.ok(sdp.includes(`a=x-nv-general.dtlsFingerprint:${fingerprint}`));
  assert.ok(sdp.includes(`a=x-nv-general.dtlsFingerprintV2:${fingerprint}`));
});

test("buildAnnounceSdp includes generated ICE V2 credentials when negotiated", () => {
  const credentials = generateNvstIceCredentials();
  assert.match(credentials.usernameFragment, /^[A-Za-z0-9+/]{4}$/);
  assert.match(credentials.password, /^[A-Za-z0-9+/]{22}$/);

  const sdp = buildAnnounceSdp({ iceCredentials: credentials });
  assert.ok(sdp.includes(`a=x-nv-general.iceUsernameFragment:${credentials.usernameFragment}`));
  assert.ok(sdp.includes(`a=x-nv-general.iceUsernamePwd:${credentials.password}`));
  assert.ok(sdp.includes(`a=x-nv-general.iceUserNameFragmentV2:${credentials.usernameFragment}`));
  assert.ok(sdp.includes(`a=x-nv-general.icePasswordV2:${credentials.password}`));
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

test("extractMediaControl reads the advertised video track instead of session control", () => {
  const sdp = [
    "v=0",
    "a=control:*",
    "m=audio 0 RTP/AVP 97",
    "a=control:streamid=audio/0/0",
    "m=video 0 RTP/AVP 96",
    "a=control:tracks/server-selected-video",
    "",
  ].join("\r\n");

  assert.equal(extractMediaControl(sdp, "video"), "tracks/server-selected-video");
  assert.equal(extractMediaControl(sdp, "audio"), "streamid=audio/0/0");
  assert.equal(extractMediaControl(sdp, "control"), null);
});
