/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  answerHasVideoCodec,
  extractNegotiatedVideoCodec,
} from "./sdpValidation";

test("extractNegotiatedVideoCodec returns the primary codec from the video m-line", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98",
    "a=rtpmap:96 VP8/90000",
    "a=rtpmap:97 H264/90000",
    "a=rtpmap:98 rtx/90000",
    "a=fmtp:98 apt=97",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), "H264");
});

test("extractNegotiatedVideoCodec normalizes HEVC to H265", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98",
    "a=rtpmap:98 HEVC/90000",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), "H265");
});

test("extractNegotiatedVideoCodec honors m-line payload order", () => {
  const sdp = [
    "m=video 9 UDP/TLS/RTP/SAVPF 98 96",
    "a=rtpmap:98 H265/90000",
    "a=rtpmap:96 H264/90000",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), "H265");
});

test("extractNegotiatedVideoCodec returns null for a rejected video m-line", () => {
  const sdp = [
    "m=video 0 UDP/TLS/RTP/SAVPF 0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=rtpmap:111 opus/48000/2",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), null);
});

test("extractNegotiatedVideoCodec returns null when no video m-line exists", () => {
  const sdp = [
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=rtpmap:111 opus/48000/2",
  ].join("\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), null);
});

test("answerHasVideoCodec mirrors extractNegotiatedVideoCodec", () => {
  const good = [
    "m=video 9 UDP/TLS/RTP/SAVPF 96",
    "a=rtpmap:96 H264/90000",
  ].join("\n");
  const rejected = "m=video 0 UDP/TLS/RTP/SAVPF 0";

  assert.equal(answerHasVideoCodec(good), true);
  assert.equal(answerHasVideoCodec(rejected), false);
});

test("rejects a video section omitted from the BUNDLE group", () => {
  const sdp = [
    "v=0",
    "a=group:BUNDLE 0",
    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
    "a=mid:0",
    "a=rtpmap:111 opus/48000/2",
    "m=video 9 UDP/TLS/RTP/SAVPF 96",
    "a=mid:1",
    "a=rtpmap:96 H264/90000",
  ].join("\r\n");

  assert.equal(extractNegotiatedVideoCodec(sdp), null);
  assert.equal(answerHasVideoCodec(sdp), false);
});
