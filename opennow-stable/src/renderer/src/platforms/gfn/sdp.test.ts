/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import * as sdp from "./sdp";

test("sdp barrel preserves the public API", () => {
  assert.deepEqual(Object.keys(sdp).sort(), [
    "buildNvstSdp",
    "extractIceCredentials",
    "extractIceUfragFromOffer",
    "extractNegotiatedVideoCodec",
    "extractPublicIp",
    "fixServerIp",
    "mungeAnswerSdp",
    "preferCodec",
    "resolveNegotiationCandidates",
    "rewriteH265LevelIdByProfile",
    "rewriteH265TierFlag",
    "rewriteIceCandidateEndpoint",
    "rewriteSdpIceCandidateEndpoints",
  ]);
});
