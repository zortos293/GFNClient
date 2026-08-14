/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  extractNegotiatedVideoCodec,
  preferCodec,
  resolveNegotiationCandidates,
  rewriteH265LevelIdByProfile,
  rewriteH265TierFlag,
} from "./codec";

test("preferCodec keeps selected video payloads and RTX apt payloads while leaving audio untouched", () => {
  const sdp = [
    "v=0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111 0",
    "a=rtpmap:111 opus/48000/2",
    "a=fmtp:111 minptime=10;useinbandfec=1",
    "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 102",
    "a=rtpmap:96 H264/90000",
    "a=rtcp-fb:96 nack",
    "a=rtpmap:97 rtx/90000",
    "a=fmtp:97 apt=96",
    "a=rtpmap:98 H265/90000",
    "a=fmtp:98 profile-id=1;tier-flag=0;level-id=153",
    "a=rtcp-fb:98 nack pli",
    "a=rtpmap:99 rtx/90000",
    "a=fmtp:99 apt=98",
    "a=rtpmap:100 AV1/90000",
    "a=rtpmap:101 flexfec-03/90000",
    "a=rtpmap:102 ulpfec/90000",
  ].join("\n");

  const filtered = preferCodec(sdp, "H265");

  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 98 99/);
  assert.match(filtered, /a=rtpmap:98 H265\/90000/);
  assert.match(filtered, /a=rtpmap:99 rtx\/90000/);
  assert.match(filtered, /a=fmtp:99 apt=98/);
  assert.doesNotMatch(filtered, /a=rtpmap:96 H264/);
  assert.doesNotMatch(filtered, /a=rtpmap:100 AV1/);
  assert.doesNotMatch(filtered, /flexfec|ulpfec/);
  assert.match(filtered, /m=audio 9 UDP\/TLS\/RTP\/SAVPF 111 0/);
  assert.match(filtered, /a=fmtp:111 minptime=10;useinbandfec=1/);
});

test("rewrites H265 tier flag and clamps oversized level by profile", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98 99",
    "a=rtpmap:98 H265/90000",
    "a=fmtp:98 profile-id=1;tier-flag=1;level-id=186",
    "a=rtpmap:99 H265/90000",
    "a=fmtp:99 profile-id=2;tier-flag=1;level-id=255",
  ].join("\n");

  const tier = rewriteH265TierFlag(sdp, 0);
  const level = rewriteH265LevelIdByProfile(tier.sdp, { 1: 153, 2: 186 });

  assert.equal(tier.replacements, 2);
  assert.equal(level.replacements, 2);
  assert.match(level.sdp, /a=fmtp:98 profile-id=1;tier-flag=0;level-id=153/);
  assert.match(level.sdp, /a=fmtp:99 profile-id=2;tier-flag=0;level-id=186/);
});

test("preferCodec returns the original SDP when the codec is unavailable", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96",
    "a=rtpmap:96 H264/90000",
    "",
  ].join("\r\n");

  assert.equal(preferCodec(sdp, "AV1"), sdp);
});

test("preferCodec orders the requested H265 profile before other primary payloads", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98 99 100 101",
    "a=rtpmap:98 HEVC/90000",
    "a=fmtp:98 profile-id=2;tier-flag=1;level-id=186",
    "a=rtpmap:99 rtx/90000",
    "a=fmtp:99 apt=98",
    "a=rtpmap:100 H265/90000",
    "a=fmtp:100 profile-id=1;tier-flag=1;level-id=186",
    "a=rtpmap:101 rtx/90000",
    "a=fmtp:101 apt=100",
  ].join("\r\n");

  const filtered = preferCodec(sdp, "H265", { preferHevcProfileId: 1 });

  assert.match(filtered, /^m=video 9 UDP\/TLS\/RTP\/SAVPF 100 98 99 101\r\n/);
  assert.match(filtered, /\r\n/);
});

test("preferCodec keepFallbacks keeps all codecs but reorders the preferred one first", () => {
  const sdp = [
    "v=0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111 0",
    "a=rtpmap:111 opus/48000/2",
    "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 102",
    "a=rtpmap:96 H264/90000",
    "a=rtcp-fb:96 nack",
    "a=rtpmap:97 rtx/90000",
    "a=fmtp:97 apt=96",
    "a=rtpmap:98 H265/90000",
    "a=fmtp:98 profile-id=1;tier-flag=0;level-id=153",
    "a=rtcp-fb:98 nack pli",
    "a=rtpmap:99 rtx/90000",
    "a=fmtp:99 apt=98",
    "a=rtpmap:100 AV1/90000",
    "a=rtpmap:101 flexfec-03/90000",
    "a=rtpmap:102 ulpfec/90000",
  ].join("\n");

  const filtered = preferCodec(sdp, "AV1", { keepFallbacks: true });

  // AV1 payload moves to the front; every other codec stays in the m-line.
  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 100 96 97 98 99 101 102/);
  assert.match(filtered, /a=rtpmap:96 H264\/90000/);
  assert.match(filtered, /a=rtpmap:98 H265\/90000/);
  assert.match(filtered, /a=rtpmap:100 AV1\/90000/);
  assert.match(filtered, /a=rtpmap:101 flexfec-03\/90000/);
  assert.match(filtered, /a=rtpmap:102 ulpfec\/90000/);
  assert.match(filtered, /a=fmtp:97 apt=96/);
  assert.match(filtered, /m=audio 9 UDP\/TLS\/RTP\/SAVPF 111 0/);
});

test("preferCodec keepFallbacks orders a user-pinned fallback codec right after the preferred one", () => {
  const sdp = [
    "v=0",
    "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 102",
    "a=rtpmap:96 H264/90000",
    "a=rtpmap:97 rtx/90000",
    "a=fmtp:97 apt=96",
    "a=rtpmap:98 H265/90000",
    "a=rtpmap:99 rtx/90000",
    "a=fmtp:99 apt=98",
    "a=rtpmap:100 AV1/90000",
    "a=rtpmap:101 flexfec-03/90000",
    "a=rtpmap:102 ulpfec/90000",
  ].join("\n");

  // Preferred AV1 first, pinned H265 primary payload (98) next, then the rest
  // in offer order (H265's RTX 99 stays in place; it is linked via apt=98).
  const filtered = preferCodec(sdp, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "H265",
  });
  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 100 98 96 97 99 101 102/);
  assert.match(filtered, /a=rtpmap:98 H265\/90000/);
  assert.match(filtered, /a=rtpmap:100 AV1\/90000/);
  assert.match(filtered, /a=fmtp:99 apt=98/);
});

test("preferCodec orders pinned H265 fallback payloads by the preferred profile-id", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96 98 99 100 101",
    "a=rtpmap:96 AV1/90000",
    "a=rtpmap:98 H265/90000",
    "a=fmtp:98 profile-id=2;tier-flag=1;level-id=186",
    "a=rtpmap:99 rtx/90000",
    "a=fmtp:99 apt=98",
    "a=rtpmap:100 H265/90000",
    "a=fmtp:100 profile-id=1;tier-flag=1;level-id=186",
    "a=rtpmap:101 rtx/90000",
    "a=fmtp:101 apt=100",
  ].join("\n");

  const filtered = preferCodec(sdp, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "H265",
    preferHevcProfileId: 1,
  });

  // AV1 first, then the profile-id=1 H265 payload (100) before profile-id=2 (98).
  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 96 100 98 99 101/);
});

test("preferCodec ignores a fallback equal to the preferred codec", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96 100",
    "a=rtpmap:96 H264/90000",
    "a=rtpmap:100 AV1/90000",
  ].join("\n");

  const filtered = preferCodec(sdp, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "AV1",
  });
  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 100 96/);
});

test("preferCodec default mode stays a hard filter", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96 100",
    "a=rtpmap:96 H264/90000",
    "a=rtpmap:100 AV1/90000",
  ].join("\n");

  const filtered = preferCodec(sdp, "AV1");
  assert.match(filtered, /m=video 9 UDP\/TLS\/RTP\/SAVPF 100/);
  assert.doesNotMatch(filtered, /a=rtpmap:96 H264/);
});

test("extractNegotiatedVideoCodec returns the first negotiated video codec", () => {
  const answer = [
    "v=0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=rtpmap:111 opus/48000/2",
    "m=video 9 UDP/TLS/RTP/SAVPF 100 102",
    "a=rtpmap:100 AV1/90000",
    "a=rtpmap:102 rtx/90000",
    "a=fmtp:102 apt=100",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(answer), "AV1");
});

test("extractNegotiatedVideoCodec honors m-line payload order", () => {
  const answer = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98 96",
    "a=rtpmap:98 H265/90000",
    "a=rtpmap:96 H264/90000",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(answer), "H265");
});

test("extractNegotiatedVideoCodec returns null for a rejected video m-line", () => {
  const answer = [
    "m=video 0 UDP/TLS/RTP/SAVPF 0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=rtpmap:111 opus/48000/2",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(answer), null);
});

test("H265 rewriting leaves already-compatible and non-H265 payloads unchanged", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96 98",
    "a=rtpmap:96 H264/90000",
    "a=fmtp:96 profile-id=1;tier-flag=1;level-id=255",
    "a=rtpmap:98 HEVC/90000",
    "a=fmtp:98 profile-id=1;tier-flag=0;level-id=120",
  ].join("\n");

  assert.deepEqual(rewriteH265TierFlag(sdp, 0), { sdp, replacements: 0 });
  assert.deepEqual(rewriteH265LevelIdByProfile(sdp, { 1: 153 }), {
    sdp,
    replacements: 0,
  });
});

test("resolveNegotiationCandidates orders primary then GFN-web fallbacks", () => {
  assert.deepEqual(resolveNegotiationCandidates("AV1"), ["AV1", "H264", "H265"]);
  assert.deepEqual(resolveNegotiationCandidates("H265"), ["H265", "H264", "AV1"]);
});

test("resolveNegotiationCandidates pins the user fallback right after the primary", () => {
  assert.deepEqual(
    resolveNegotiationCandidates("AV1", "H265"),
    ["AV1", "H265", "H264"],
  );
});

test("resolveNegotiationCandidates dedupes a fallback equal to the primary", () => {
  assert.deepEqual(
    resolveNegotiationCandidates("H265", "H265"),
    ["H265", "H264", "AV1"],
  );
});

test("resolveNegotiationCandidates drops codecs the browser cannot decode", () => {
  assert.deepEqual(
    resolveNegotiationCandidates("AV1", undefined, ["H264", "H265"]),
    ["H264", "H265"],
  );
  assert.deepEqual(
    resolveNegotiationCandidates("AV1", "H265", ["H264", "H265"]),
    ["H265", "H264"],
  );
  assert.deepEqual(
    resolveNegotiationCandidates("AV1", undefined, ["H264"]),
    ["H264"],
  );
});

test("resolveNegotiationCandidates keeps every candidate when support is unknown", () => {
  assert.deepEqual(
    resolveNegotiationCandidates("AV1", "H265", []),
    ["AV1", "H265", "H264"],
  );
});
