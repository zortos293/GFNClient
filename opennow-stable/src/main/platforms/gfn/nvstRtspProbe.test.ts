/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import * as nvstRtspProbe from "./nvstRtspProbe";

test("nvstRtspProbe preserves its public runtime exports", () => {
  assert.deepEqual(Object.keys(nvstRtspProbe).sort(), [
    "buildAnnounceSdp",
    "buildEmptyPathUpgradeRequest",
    "buildNvstWssUpgradeRequest",
    "buildNvstWssUpgradeRequestTarget",
    "collectRtspsEndpoints",
    "extractHmacSeed",
    "extractRuntimeEncryptionKey",
    "extractVideoPeer",
    "packSrtpMasterKeySalt",
    "rtspsUrlToWssUrl",
    "runNvstRtspHandshakeProbe",
    "selectPrimaryRtspsEndpoint",
  ]);
});
