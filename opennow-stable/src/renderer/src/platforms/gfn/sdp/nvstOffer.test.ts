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
    "a=video.initialBitrateKbps:20000", // startup = max(4000, max/4), official web formula
    "a=video.initialPeakBitrateKbps:20000", // initialPeak mirrors initial (official web client)
    "a=vqos.bw.maximumBitrateKbps:80000",
    "a=vqos.bw.minimumBitrateKbps:4000", // official web client keeps the floor at 4000
    "a=vqos.drc.minRequiredBitrateCheckEnabled:1",
    "a=vqos.fec.repairPercent:5",
    "a=vqos.fec.repairMaxPercent:35",
    "a=packetPacing.minNumPacketsPerGroup:15",
    "a=packetPacing.maxDelayUs:1000",
    "a=video.rtpNackQueueLength:1024",
    "a=video.rtpNackQueueMaxPackets:512",
    "a=video.rtpNackMaxPacketCount:25",
    "a=video.bitDepth:10",
    "a=video.encoderCscMode:3", // desktop web always 3 (official: TIZEN ? 2 : 3)
    "a=video.encoderHdrCscMode:4",
    "a=video.dynamicRangeMode:1",
    "a=vqos.drc.enable:0",
    "a=vqos.dfc.enable:1",
    "a=vqos.dfc.adjustResAndFps:1", // official mode-3 high-FPS case
    "a=vqos.dfc.qpMaxResThresholdAdj:20",
    "a=vqos.grc.qpMaxResThresholdAdj:20",
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
  // The 90 FPS capture profile quirks below stay active; maxFPS mirrors the
  // requested FPS like the official web client.
  for (const line of [
    "a=video.maxFPS:90",
    "a=vqos.dfc.enable:1",
    "a=vqos.dfc.dfcAlgoVersion:1",
    "a=vqos.dfc.minTargetFps:60", // official web: 60 target for 90 FPS sessions
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
  assert.match(defaultSdp, /a=video\.fakeEncodeFps:120/); // official 120 FPS encoder hint
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
  assert.equal(lines.has("a=vqos.bw.minimumBitrateKbps:4000"), true);
  assert.equal(lines.has("a=video.maxFPS:60"), true);
  assert.equal(lines.has("a=video.encoderCscMode:3"), true);
  // Official web client, 60 FPS + dynamicStreamingMode=3: mode 3 + drc.enable:1.
  assert.equal(lines.has("a=vqos.dynamicStreamingMode:3"), true);
  assert.equal(lines.has("a=vqos.drc.enable:1"), true);
  // Official web client sends featureMask:3 (CPM enabled); the old fork-only
  // cpmRtc.enable:0 / minResolutionPercent:100 / resolutionChangeHoldonMs:999999
  // lock attrs are gone (zero hits in the play.geforcenow.com bundle).
  assert.equal(lines.has("a=vqos.resControl.cpmRtc.featureMask:3"), true);
  // Fork-only attributes the official web client never sends — guard against
  // regressions (the BWE ones made the server throttle to the bitrate floor).
  // Prefix match on the full line so "a=foo" also catches "a=foo:123".
  for (const absent of [
    "a=vqos.bw.enableBandwidthEstimation",
    "a=vqos.bw.disableBitrateLimit",
    "a=vqos.bw.serverPeakBitrateKbps",
    "a=vqos.bw.peakBitrateKbps",
    "a=vqos.grc.maximumBitrateKbps",
    "a=vqos.grc.enable",
    "a=vqos.resControl.cpmRtc.enable",
    "a=vqos.resControl.cpmRtc.minResolutionPercent",
    "a=vqos.resControl.cpmRtc.resolutionChangeHoldonMs",
    "a=vqos.calculateAvgVideoStreamingBitrate",
    "a=vqos.dfc.adjustResAndFps",
    "a=vqos.drc.enable:0", // 60 FPS sessions send no drc/dfc (high-FPS only)
    "a=vqos.dfc.enable:0",
    "a=video.framePacing.mode",
    "a=video.adaptiveQuantization.spatialAQSetting",
    "a=vqos.relaxMaxBitrate.overrideAvgBitrateThresholdPercent",
    "a=vqos.qpDelta.qpDeltaMaxPercent",
    "a=packetPacing.version",
    "a=packetPacing.enableAccurateSleep",
    "a=vqos.rtcPreemptiveIdrSettings.minBurstNackSize",
    "a=video.minQp", // H264 does not get the H265 minQp:14 pin
    "a=video.fakeEncodeFps", // 60 FPS is below the 120 FPS hint threshold
    // Audio/input extras the official web client never sends.
    "a=aqos.enableRedundancy",
    "a=aqos.redundancyLevel",
    "a=aqos.enableRedundancyForMic",
    "a=aqos.redundancyLevelForMic",
    "a=audio.enableDynamicAudioConfig",
    "a=audio.enableTimestampAudioBuffer",
    "a=ri.timestampsEnabled",
    "a=ri.useMultipleGamepads",
  ]) {
    const leaked = [...lines].some((line) => line.startsWith(absent));
    assert.equal(leaked, false, `fork-only attribute still sent: ${absent}`);
  }
  assert.equal(lines.has("a=video.bitDepth:8"), true);
  assert.equal(lines.has("a=ri.hidDeviceMask:4294967295"), true);
  assert.equal(lines.has("a=ri.enablePartiallyReliableTransferGamepad:15"), true);
  assert.equal(lines.has("a=ri.enablePartiallyReliableTransferHid:4294967295"), true);
  assert.equal(sdp.endsWith("\n"), true);
});
