import assert from "node:assert/strict";
import test from "node:test";
import { defaultDiagnostics } from "../lib/streamDiagnostics";
import {
  StreamSessionReportAccumulator,
  sessionQualityScore,
  sessionReportRating,
} from "./sessionReport";

test("sessionQualityScore matches the Android quality ladders", () => {
  assert.equal(sessionQualityScore({
    averagePingMs: 20,
    packetLossPercent: 0.05,
    averageJitterMs: 3,
    averageFps: 60,
    targetFps: 60,
    averageDecodeMs: 4,
  }), 100);
  assert.equal(sessionReportRating(100), "excellent");

  const poor = sessionQualityScore({
    averagePingMs: 190,
    packetLossPercent: 6,
    averageJitterMs: 55,
    averageFps: 30,
    targetFps: 60,
    averageDecodeMs: 30,
  });
  assert.ok(poor < 30);
  assert.equal(sessionReportRating(poor), "poor");
});

test("accumulator builds a privacy-safe report from cumulative counters", () => {
  const accumulator = new StreamSessionReportAccumulator({
    gameTitle: "Example Game",
    requestedResolution: "1920x1080",
    requestedCodec: "auto",
    targetFps: 60,
  }, 1_000);

  accumulator.record({
    ...defaultDiagnostics(),
    framesDecoded: 100,
    framesReceived: 101,
    framesDropped: 1,
    renderFps: 60,
    rttMs: 20,
    jitterMs: 2,
    bitrateKbps: 20_000,
    packetsLost: 1,
    packetsReceived: 99,
    resolution: "1920x1080",
    codec: "H264",
    transportType: "udp",
    serverRegion: "US Northwest",
  }, 2_000);
  accumulator.record({
    ...defaultDiagnostics(),
    framesDecoded: 160,
    framesReceived: 162,
    framesDropped: 2,
    renderFps: 58,
    rttMs: 30,
    jitterMs: 4,
    bitrateKbps: 18_000,
    packetsLost: 2,
    packetsReceived: 198,
    resolution: "1920x1080",
    codec: "H264",
    transportType: "udp",
    serverRegion: "US Northwest",
  }, 3_000);

  const report = accumulator.finish(4_000);
  assert.ok(report);
  assert.equal(report.gameTitle, "Example Game");
  assert.equal(report.averagePingMs, 25);
  assert.equal(report.packetLossPercent, 1);
  assert.equal(report.frameDropPercent, 1.639);
  assert.equal(report.transportType, "udp");
  assert.equal(report.serverLocation, "US Northwest");
  assert.equal("sessionId" in report, false);
  assert.equal("serverIp" in report, false);
});

test("counter resets do not create negative packet or frame deltas", () => {
  const accumulator = new StreamSessionReportAccumulator({
    gameTitle: "Recovery",
    requestedResolution: "1920x1080",
    requestedCodec: "h264",
    targetFps: 60,
  }, 0);
  const first = {
    ...defaultDiagnostics(),
    framesDecoded: 100,
    framesDropped: 10,
    renderFps: 60,
    packetsLost: 10,
    packetsReceived: 1_000,
  };
  accumulator.record(first, 1_000);
  accumulator.record({
    ...first,
    framesDecoded: 2,
    framesDropped: 0,
    packetsLost: 0,
    packetsReceived: 5,
  }, 2_000);
  accumulator.record({
    ...first,
    framesDecoded: 62,
    framesDropped: 1,
    packetsLost: 1,
    packetsReceived: 104,
  }, 3_000);

  const report = accumulator.finish(4_000);
  assert.ok(report);
  assert.equal(report.packetLossPercent, 1);
  assert.equal(report.frameDropPercent, 1.639);
});

test("session report does not retain a raw server IP", () => {
  const accumulator = new StreamSessionReportAccumulator({
    gameTitle: "Privacy",
    requestedResolution: "1920x1080",
    requestedCodec: "auto",
    targetFps: 60,
  }, 0);
  accumulator.record({
    ...defaultDiagnostics(),
    framesDecoded: 10,
    renderFps: 60,
    serverRegion: "161.248.11.132",
  }, 1_000);

  const report = accumulator.finish(2_000);
  assert.ok(report);
  assert.equal(report.serverLocation, null);
  assert.equal(JSON.stringify(report).includes("161.248.11.132"), false);
});
