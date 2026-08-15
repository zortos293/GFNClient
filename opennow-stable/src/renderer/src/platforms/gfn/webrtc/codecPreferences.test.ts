import assert from "node:assert/strict";
import test from "node:test";

import { buildCodecPreferenceList } from "./codecPreferences";

function codec(mimeType: string, sdpFmtpLine?: string): RTCRtpCodec {
  return {
    mimeType,
    clockRate: 90000,
    channels: undefined,
    sdpFmtpLine: sdpFmtpLine ?? "",
  };
}

function mimeTypes(list: RTCRtpCodec[]): string[] {
  return list.map((entry) => entry.mimeType.toLowerCase());
}

test("keeps only the requested codec and auxiliary entries by default", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/AV1"),
    codec("video/rtx", "apt=98"),
    codec("video/flexfec-03"),
  ];
  const list = buildCodecPreferenceList(caps, "H265");
  assert.deepEqual(mimeTypes(list), [
    "video/h265",
    "video/rtx",
    "video/flexfec-03",
  ]);
});

test("includes fallback primaries after the requested codec when keepFallbacks is set", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/AV1"),
    codec("video/rtx", "apt=100"),
    codec("video/flexfec-03"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", { keepFallbacks: true });
  // Requested codec first, then the other GFN primaries, then auxiliary.
  assert.deepEqual(mimeTypes(list), [
    "video/av1",
    "video/h264",
    "video/h265",
    "video/rtx",
    "video/flexfec-03",
  ]);
});

test("H265 profile ordering prefers the requested profile-id", () => {
  const caps = [
    codec("video/H265", "profile-id=2;level-id=186"),
    codec("video/H265", "profile-id=1;level-id=186"),
  ];
  const list = buildCodecPreferenceList(caps, "H265", { preferredHevcProfileId: 1 });
  assert.deepEqual(mimeTypes(list), ["video/h265", "video/h265"]);
  assert.equal(list[0].sdpFmtpLine, "profile-id=1;level-id=186");
});

test("returns empty list when no GFN codec matches the caps", () => {
  const list = buildCodecPreferenceList([codec("video/VP8"), codec("video/VP9")], "AV1", {
    keepFallbacks: true,
  });
  assert.deepEqual(list, []);
});

test("fallbacks are still returned when the requested codec is absent from caps", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/rtx", "apt=96"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", { keepFallbacks: true });
  assert.deepEqual(mimeTypes(list), ["video/h264", "video/h265", "video/rtx"]);
});

test("user-pinned fallback codec is ordered first among fallbacks", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/AV1"),
    codec("video/rtx", "apt=100"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "H265",
  });
  // Requested AV1 first, then the pinned H265, then the remaining primary.
  assert.deepEqual(mimeTypes(list), [
    "video/av1",
    "video/h265",
    "video/h264",
    "video/rtx",
  ]);
});

test("pinned H265 fallback entries are ordered by the preferred profile-id", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265", "profile-id=2;level-id=186"),
    codec("video/H265", "profile-id=1;level-id=186"),
    codec("video/AV1"),
    codec("video/rtx", "apt=100"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "H265",
    preferredHevcProfileId: 1,
  });
  // Requested AV1 first, then profile-id=1 H265 before profile-id=2, then H264, then aux.
  assert.deepEqual(
    list.map((entry) => `${entry.mimeType.toLowerCase()}|${entry.sdpFmtpLine ?? ""}`),
    [
      "video/av1|",
      "video/h265|profile-id=1;level-id=186",
      "video/h265|profile-id=2;level-id=186",
      "video/h264|",
      "video/rtx|apt=100",
    ],
  );
});

test("pinned fallback equal to the requested codec keeps the default order", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/AV1"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", {
    keepFallbacks: true,
    fallbackCodec: "AV1",
  });
  assert.deepEqual(mimeTypes(list), ["video/av1", "video/h264", "video/h265"]);
});

test("pinned fallback has no effect when keepFallbacks is off", () => {
  const caps = [
    codec("video/H264"),
    codec("video/H265"),
    codec("video/AV1"),
  ];
  const list = buildCodecPreferenceList(caps, "AV1", { fallbackCodec: "H265" });
  assert.deepEqual(mimeTypes(list), ["video/av1"]);
});
