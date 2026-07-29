/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { buildNvstSdp } from "./nvstOffer";

test("buildNvstSdp includes stream quality and partially reliable input parameters", () => {
  const sdp = buildNvstSdp({
    width: 2560,
    height: 1440,
    fps: 120,
    maxBitrateKbps: 80000,
    partialReliableThresholdMs: 16,
    codec: "AV1",
    colorQuality: "10bit_444",
    credentials: {
      ufrag: "ufrag-test",
      pwd: "password-test",
      fingerprint: "AA:BB:CC",
    },
    hidDeviceMask: 128,
    enablePartiallyReliableTransferGamepad: 15,
    enablePartiallyReliableTransferHid: 128,
  });

  for (const line of [
    "a=video.clientViewportWd:2560",
    "a=video.clientViewportHt:1440",
    "a=video.maxFPS:120",
    "a=video.initialBitrateKbps:20000",
    "a=video.initialPeakBitrateKbps:20000",
    "a=vqos.bw.maximumBitrateKbps:80000",
    "a=vqos.bw.minimumBitrateKbps:4000",
    "a=vqos.bw.peakBitrateKbps:80000",
    "a=vqos.drc.minRequiredBitrateCheckEnabled:1",
    "a=vqos.fec.repairPercent:5",
    "a=vqos.fec.repairMaxPercent:35",
    "a=packetPacing.enableAccurateSleep:1",
    "a=packetPacing.minNumPacketsPerGroup:15",
    "a=video.framePacing.mode:2",
    "a=video.framePacing.pid.minTargetFrameTimeUs:7916",
    "a=video.adaptiveQuantization.spatialAQSetting:7",
    "a=video.rtpNackQueueLength:1024",
    "a=video.rtpNackQueueMaxPackets:512",
    "a=video.rtpNackMaxPacketCount:25",
    "a=aqos.enableRedundancy:1",
    "a=aqos.redundancyLevel:2",
    "a=ri.timestampsEnabled:1",
    "a=video.bitDepth:10",
    "a=vqos.drc.enable:0",
    "a=vqos.dfc.enable:1",
    "a=vqos.dfc.qpMaxResThresholdAdj:20",
    "a=vqos.grc.qpMaxResThresholdAdj:20",
    "a=vqos.resControl.cpmRtc.enable:0",
    "a=ri.partialReliableThresholdMs:16",
    "a=ri.hidDeviceMask:128",
    "a=ri.enablePartiallyReliableTransferGamepad:15",
    "a=ri.enablePartiallyReliableTransferHid:128",
  ]) {
    assert.match(sdp, new RegExp(line.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("buildNvstSdp applies the official 90 FPS capture profile", () => {
  const sdp = buildNvstSdp({
    width: 1280,
    height: 800,
    fps: 90,
    maxBitrateKbps: 50000,
    partialReliableThresholdMs: 16,
    codec: "H265",
    colorQuality: "10bit_420",
    credentials: {
      ufrag: "ufrag-test",
      pwd: "password-test",
      fingerprint: "AA:BB:CC",
    },
  });

  const lines = new Set(sdp.split(/\r?\n/));
  for (const line of [
    "a=video.maxFPS:90",
    "a=video.framePacing.pid.minTargetFrameTimeUs:10555",
    "a=vqos.dfc.enable:1",
    "a=vqos.dfc.dfcAlgoVersion:1",
    "a=vqos.dfc.minTargetFps:60",
    "a=video.fbcDynamicFpsGrabTimeoutMs:9",
    "a=vqos.resControl.cpmRtc.decodeTimeThresholdMs:11",
  ]) {
    assert.equal(lines.has(line), true, `missing exact SDP line: ${line}`);
  }
  assert.equal(lines.has("a=vqos.maxStreamFpsEstimate:240"), false);
});

test("buildNvstSdp keeps dynamic split encode updates enabled for 240 FPS by default", () => {
  const defaultSdp = buildNvstSdp({
    width: 1920,
    height: 1080,
    fps: 240,
    maxBitrateKbps: 75000,
    partialReliableThresholdMs: 16,
    codec: "H265",
    colorQuality: "10bit_420",
    credentials: {
      ufrag: "ufrag-test",
      pwd: "password-test",
      fingerprint: "AA:BB:CC",
    },
  });
  const diagnosticOffSdp = buildNvstSdp({
    width: 1920,
    height: 1080,
    fps: 240,
    maxBitrateKbps: 75000,
    partialReliableThresholdMs: 16,
    codec: "H265",
    colorQuality: "10bit_420",
    credentials: {
      ufrag: "ufrag-test",
      pwd: "password-test",
      fingerprint: "AA:BB:CC",
    },
    dynamicSplitEncodeUpdatesEnabled: false,
  });

  assert.match(defaultSdp, /a=video\.updateSplitEncodeStateDynamically:1/);
  assert.match(defaultSdp, /a=video\.videoSplitEncodeStripsPerFrame:63/);
  assert.match(defaultSdp, /a=vqos\.relaxMaxBitrate\.iirFilterFactor:120/);
  assert.match(diagnosticOffSdp, /a=video\.updateSplitEncodeStateDynamically:0/);
});

test("buildNvstSdp floors low bitrate and applies protocol input defaults", () => {
  const sdp = buildNvstSdp({
    width: 1280,
    height: 720,
    fps: 60,
    maxBitrateKbps: 3999.9,
    partialReliableThresholdMs: 12,
    codec: "H264",
    colorQuality: "10bit_420",
    credentials: {
      ufrag: "ufrag-test",
      pwd: "password-test",
      fingerprint: "AA:BB:CC",
    },
  });

  const lines = new Set(sdp.split("\n"));
  assert.equal(lines.has("a=video.initialBitrateKbps:4000"), true);
  assert.equal(lines.has("a=vqos.bw.maximumBitrateKbps:4000"), true);
  assert.equal(lines.has("a=video.bitDepth:8"), true);
  assert.equal(lines.has("a=ri.hidDeviceMask:4294967295"), true);
  assert.equal(lines.has("a=ri.enablePartiallyReliableTransferGamepad:15"), true);
  assert.equal(lines.has("a=ri.enablePartiallyReliableTransferHid:4294967295"), true);
  assert.equal(sdp.endsWith("\n"), true);
});
