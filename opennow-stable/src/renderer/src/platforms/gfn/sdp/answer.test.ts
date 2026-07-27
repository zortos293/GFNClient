/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { mungeAnswerSdp } from "./answer";

test("mungeAnswerSdp injects bitrate lines and appends opus stereo once", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98",
    "c=IN IP4 127.0.0.1",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=fmtp:111 minptime=10;useinbandfec=1",
  ].join("\n");

  const munged = mungeAnswerSdp(sdp, 50000);
  assert.match(munged, /m=video.*\nb=AS:50000\n/);
  assert.match(munged, /m=audio.*\nb=AS:128\n/);
  assert.match(munged, /a=fmtp:111 minptime=10;useinbandfec=1;stereo=1/);

  const alreadyStereo = mungeAnswerSdp("m=audio 9 UDP/TLS/RTP/SAVPF 111\nb=AS:128\na=fmtp:111 minptime=10;stereo=1", 50000);
  assert.equal((alreadyStereo.match(/stereo=1/g) ?? []).length, 1);
  assert.equal((alreadyStereo.match(/b=AS:128/g) ?? []).length, 1);
});

test("mungeAnswerSdp preserves CRLF and treats any existing bandwidth line as authoritative", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98",
    "b=TIAS:50000000",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "b=AS:96",
    "a=fmtp:111 minptime=10",
    "",
  ].join("\r\n");

  const munged = mungeAnswerSdp(sdp, 50000);

  assert.equal(munged, sdp.replace("minptime=10", "minptime=10;stereo=1"));
  assert.doesNotMatch(munged, /b=AS:50000/);
  assert.match(munged, /\r\n/);
});
