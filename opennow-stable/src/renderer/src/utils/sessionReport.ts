import type {
  DesktopSessionReport,
  DesktopSessionReportFinding,
  DesktopSessionReportRating,
} from "@shared/bugReport";
import type { StreamDiagnostics } from "../platforms/gfn/webrtcClient";
import { getStreamServerLocationLabel } from "../platforms/gfn/webrtc/sessionDiagnostics";

export interface StreamSessionReportContext {
  gameTitle: string;
  requestedResolution: string;
  requestedCodec: string;
  targetFps: number;
}

interface NumberAccumulator {
  count: number;
  total: number;
  peak: number | null;
}

export class StreamSessionReportAccumulator {
  private readonly context: StreamSessionReportContext;
  private readonly startedAtMs: number;
  private sampleCount = 0;
  private lastRecordedAtMs = Number.NEGATIVE_INFINITY;
  private readonly ping = createNumberAccumulator();
  private readonly bitrate = createNumberAccumulator();
  private readonly jitter = createNumberAccumulator();
  private readonly fps = createNumberAccumulator();
  private readonly decode = createNumberAccumulator();
  private packetLossFallback = createNumberAccumulator();
  private packetsLost = 0;
  private packetsReceived = 0;
  private lastPacketsLost: number | null = null;
  private lastPacketsReceived: number | null = null;
  private framesDropped = 0;
  private framesDecoded = 0;
  private lastFramesDropped: number | null = null;
  private lastFramesDecoded: number | null = null;
  private deliveredResolution: string | null = null;
  private deliveredCodec: string | null = null;
  private transportCounts = new Map<StreamDiagnostics["transportType"], number>();
  private serverLocation: string | null = null;
  private serverGpuType: string | null = null;
  private decoderRecoveryAttempts = 0;

  constructor(context: StreamSessionReportContext, startedAtMs: number) {
    this.context = context;
    this.startedAtMs = startedAtMs;
  }

  record(stats: StreamDiagnostics, recordedAtMs = Date.now()): void {
    if (recordedAtMs - this.lastRecordedAtMs < MIN_SAMPLE_INTERVAL_MS) return;
    if (!hasSessionReportValues(stats)) return;
    this.lastRecordedAtMs = recordedAtMs;
    this.sampleCount += 1;

    addNumber(this.ping, stats.rttMs, (value) => value > 0);
    addNumber(this.bitrate, stats.bitrateKbps, (value) => value > 0);
    addNumber(this.jitter, stats.jitterMs, (value) => value >= 0);
    const renderedFps = stats.renderFps > 0 ? stats.renderFps : stats.decodeFps;
    addNumber(this.fps, renderedFps, (value) => value > 0);
    addNumber(this.decode, stats.decodeTimeMs, (value) => value >= 0);
    if (!stats.nativeRendererActive) {
      addNumber(this.packetLossFallback, stats.packetLossPercent, (value) => value >= 0);
      const packetDelta = counterDelta(
        stats.packetsLost,
        stats.packetsReceived,
        this.lastPacketsLost,
        this.lastPacketsReceived,
      );
      if (packetDelta) {
        this.packetsLost += packetDelta.first;
        this.packetsReceived += packetDelta.second;
      }
      this.lastPacketsLost = finiteNonNegative(stats.packetsLost);
      this.lastPacketsReceived = finiteNonNegative(stats.packetsReceived);
    }

    const frameDelta = counterDelta(
      stats.framesDropped,
      stats.framesDecoded,
      this.lastFramesDropped,
      this.lastFramesDecoded,
    );
    if (frameDelta) {
      this.framesDropped += frameDelta.first;
      this.framesDecoded += frameDelta.second;
    }
    this.lastFramesDropped = finiteNonNegative(stats.framesDropped);
    this.lastFramesDecoded = finiteNonNegative(stats.framesDecoded);

    if (/^\d+x\d+$/i.test(stats.resolution.trim())) {
      this.deliveredResolution = stats.resolution.trim();
    }
    if (stats.codec.trim()) this.deliveredCodec = stats.codec.trim();
    this.transportCounts.set(
      stats.transportType,
      (this.transportCounts.get(stats.transportType) ?? 0) + 1,
    );
    const safeServerLocation = getStreamServerLocationLabel(stats);
    if (safeServerLocation !== "--") {
      this.serverLocation = safeServerLocation;
    }
    if (stats.serverGpuType.trim()) this.serverGpuType = stats.serverGpuType.trim();
    this.decoderRecoveryAttempts = Math.max(
      this.decoderRecoveryAttempts,
      finiteNonNegative(stats.decoderRecoveryAttempts) ?? 0,
    );
  }

  finish(finishedAtMs = Date.now()): DesktopSessionReport | null {
    if (this.sampleCount === 0) return null;
    const averagePingMs = roundedAverage(this.ping, 0);
    const averageBitrateKbps = roundedAverage(this.bitrate, 0);
    const averageJitterMs = roundedAverage(this.jitter, 2);
    const averageFps = roundedAverage(this.fps, 1);
    const averageDecodeMs = roundedAverage(this.decode, 2);
    const packetTotal = this.packetsLost + this.packetsReceived;
    const packetLossPercent = packetTotal > 0
      ? roundTo((this.packetsLost / packetTotal) * 100, 3)
      : roundedAverage(this.packetLossFallback, 3);
    const frameTotal = this.framesDropped + this.framesDecoded;
    const frameDropPercent = frameTotal > 0
      ? roundTo((this.framesDropped / frameTotal) * 100, 3)
      : null;
    const score = sessionQualityScore({
      averagePingMs,
      packetLossPercent,
      averageJitterMs,
      averageFps,
      targetFps: this.context.targetFps,
      averageDecodeMs,
    });
    const findings = buildFindings({
      requestedResolution: this.context.requestedResolution,
      deliveredResolution: this.deliveredResolution,
      requestedCodec: this.context.requestedCodec,
      deliveredCodec: this.deliveredCodec,
      decoderRecoveryAttempts: this.decoderRecoveryAttempts,
    });
    const recommendations = buildRecommendations({
      averagePingMs,
      packetLossPercent,
      averageJitterMs,
      averageFps,
      targetFps: this.context.targetFps,
      averageDecodeMs,
      frameDropPercent,
    });

    return {
      schemaVersion: 1,
      gameTitle: this.context.gameTitle.trim() || "Cloud session",
      startedAt: new Date(this.startedAtMs).toISOString(),
      finishedAt: new Date(finishedAtMs).toISOString(),
      durationSeconds: Math.max(0, Math.floor((finishedAtMs - this.startedAtMs) / 1000)),
      sampleCount: this.sampleCount,
      limitedData: this.sampleCount < MIN_CONFIDENT_SAMPLE_COUNT,
      score,
      rating: sessionReportRating(score),
      averagePingMs,
      peakPingMs: roundedPeak(this.ping, 0),
      averageBitrateKbps,
      peakBitrateKbps: roundedPeak(this.bitrate, 0),
      packetLossPercent,
      averageJitterMs,
      averageFps,
      targetFps: Math.max(1, Math.round(this.context.targetFps)),
      averageDecodeMs,
      frameDropPercent,
      requestedResolution: this.context.requestedResolution,
      deliveredResolution: this.deliveredResolution,
      requestedCodec: this.context.requestedCodec,
      deliveredCodec: this.deliveredCodec,
      transportType: dominantTransport(this.transportCounts),
      serverLocation: this.serverLocation,
      serverGpuType: this.serverGpuType,
      decoderRecoveryAttempts: this.decoderRecoveryAttempts,
      findings,
      recommendations,
    };
  }
}

export interface SessionQualityScoreInput {
  averagePingMs: number | null;
  packetLossPercent: number | null;
  averageJitterMs: number | null;
  averageFps: number | null;
  targetFps: number;
  averageDecodeMs: number | null;
}

export function sessionQualityScore(input: SessionQualityScoreInput): number {
  const components: Array<{ score: number; weight: number }> = [];
  if (input.averagePingMs !== null) {
    components.push({ score: latencyScore(input.averagePingMs), weight: 35 });
  }
  if (input.packetLossPercent !== null) {
    components.push({ score: packetLossScore(input.packetLossPercent), weight: 30 });
  }
  if (input.averageJitterMs !== null) {
    components.push({ score: jitterScore(input.averageJitterMs), weight: 15 });
  }
  if (input.averageFps !== null) {
    components.push({ score: frameRateScore(input.averageFps, input.targetFps), weight: 15 });
  }
  if (input.averageDecodeMs !== null) {
    components.push({ score: decodeScore(input.averageDecodeMs, input.targetFps), weight: 5 });
  }
  if (components.length === 0) return 50;
  const weightedTotal = components.reduce((total, item) => total + item.score * item.weight, 0);
  const availableWeight = components.reduce((total, item) => total + item.weight, 0);
  return Math.max(0, Math.min(100, Math.round(weightedTotal / availableWeight)));
}

export function sessionReportRating(score: number): DesktopSessionReportRating {
  if (score >= 90) return "excellent";
  if (score >= 75) return "good";
  if (score >= 60) return "fair";
  return "poor";
}

function latencyScore(value: number): number {
  if (value <= 30) return 100;
  if (value <= 50) return 92;
  if (value <= 80) return 80;
  if (value <= 120) return 60;
  if (value <= 180) return 35;
  return 10;
}

function packetLossScore(value: number): number {
  if (value <= 0.1) return 100;
  if (value <= 0.5) return 90;
  if (value <= 1) return 75;
  if (value <= 2) return 55;
  if (value <= 5) return 25;
  return 5;
}

function jitterScore(value: number): number {
  if (value <= 5) return 100;
  if (value <= 10) return 90;
  if (value <= 20) return 70;
  if (value <= 30) return 50;
  if (value <= 50) return 25;
  return 5;
}

function frameRateScore(value: number, targetFps: number): number {
  const ratio = value / Math.max(1, targetFps);
  if (ratio >= 0.98) return 100;
  if (ratio >= 0.95) return 95;
  if (ratio >= 0.9) return 82;
  if (ratio >= 0.8) return 60;
  if (ratio >= 0.65) return 35;
  return 10;
}

function decodeScore(value: number, targetFps: number): number {
  const frameBudgetMs = 1000 / Math.max(1, targetFps);
  const ratio = value / frameBudgetMs;
  if (ratio <= 0.5) return 100;
  if (ratio <= 0.75) return 90;
  if (ratio <= 1) return 75;
  if (ratio <= 1.5) return 45;
  return 15;
}

function buildFindings(input: {
  requestedResolution: string;
  deliveredResolution: string | null;
  requestedCodec: string;
  deliveredCodec: string | null;
  decoderRecoveryAttempts: number;
}): DesktopSessionReportFinding[] {
  const findings: DesktopSessionReportFinding[] = [];
  if (
    input.deliveredResolution
    && normalizeResolution(input.deliveredResolution) !== normalizeResolution(input.requestedResolution)
  ) {
    findings.push({
      title: "Delivered resolution changed",
      detail: `The stream delivered ${input.deliveredResolution} instead of the requested ${input.requestedResolution}. This can reflect a provider, game, or recovery mode change.`,
      kind: "warning",
    });
  }
  if (
    input.deliveredCodec
    && input.requestedCodec.toLowerCase() !== "auto"
    && !input.deliveredCodec.toLowerCase().includes(input.requestedCodec.toLowerCase())
  ) {
    findings.push({
      title: "Delivered codec changed",
      detail: `The negotiated stream used ${input.deliveredCodec} instead of ${input.requestedCodec}.`,
      kind: "warning",
    });
  }
  if (input.decoderRecoveryAttempts > 0) {
    findings.push({
      title: "Video recovery was used",
      detail: `OpenNOW recorded ${input.decoderRecoveryAttempts} decoder recovery ${input.decoderRecoveryAttempts === 1 ? "attempt" : "attempts"} during this session.`,
      kind: "warning",
    });
  }
  return findings;
}

function buildRecommendations(input: {
  averagePingMs: number | null;
  packetLossPercent: number | null;
  averageJitterMs: number | null;
  averageFps: number | null;
  targetFps: number;
  averageDecodeMs: number | null;
  frameDropPercent: number | null;
}): DesktopSessionReportFinding[] {
  const recommendations: DesktopSessionReportFinding[] = [];
  if ((input.packetLossPercent ?? 0) > 1) {
    recommendations.push({
      title: "Reduce packet loss",
      detail: "Pause competing uploads, reduce wireless interference, or use Ethernet. Packet loss above 1% can cause blur, stutter, and reconnects.",
      kind: "warning",
    });
  }
  if ((input.averagePingMs ?? 0) > 80 || (input.averageJitterMs ?? 0) > 20) {
    recommendations.push({
      title: "Stabilize latency",
      detail: "Choose the closest server, disable VPN routing, and pause background downloads. Wired Ethernet or strong 5/6 GHz Wi-Fi is usually the most predictable.",
      kind: "warning",
    });
  }
  const frameBudgetMs = 1000 / Math.max(1, input.targetFps);
  if (
    input.averageFps !== null
    && input.averageFps < input.targetFps * 0.85
    && (input.averageDecodeMs ?? 0) > frameBudgetMs * 0.85
  ) {
    recommendations.push({
      title: "Reduce local decode load",
      detail: `The decoder used much of the ${frameBudgetMs.toFixed(1)} ms frame budget. Try a lower resolution or FPS, close GPU-heavy apps, or switch the video decoder mode.`,
      kind: "warning",
    });
  }
  if ((input.frameDropPercent ?? 0) > 1 && recommendations.length < MAX_RECOMMENDATIONS) {
    recommendations.push({
      title: "Reduce frame drops",
      detail: "Close GPU-heavy background apps and update the graphics driver. If drops continue, lower the stream resolution or FPS.",
      kind: "warning",
    });
  }
  if (recommendations.length === 0) {
    recommendations.push({
      title: "Connection looked healthy",
      detail: "No network or decoder metric crossed the report thresholds. Keep the same server and network setup for similarly consistent sessions.",
      kind: "info",
    });
  }
  return recommendations.slice(0, MAX_RECOMMENDATIONS);
}

function hasSessionReportValues(stats: StreamDiagnostics): boolean {
  return stats.framesDecoded > 0
    || stats.framesReceived > 0
    || stats.renderFps > 0
    || stats.decodeFps > 0
    || stats.rttMs > 0
    || stats.bitrateKbps > 0;
}

function createNumberAccumulator(): NumberAccumulator {
  return { count: 0, total: 0, peak: null };
}

function addNumber(
  accumulator: NumberAccumulator,
  value: number,
  isValid: (value: number) => boolean,
): void {
  if (!Number.isFinite(value) || !isValid(value)) return;
  accumulator.count += 1;
  accumulator.total += value;
  accumulator.peak = Math.max(accumulator.peak ?? value, value);
}

function roundedAverage(accumulator: NumberAccumulator, digits: number): number | null {
  return accumulator.count > 0 ? roundTo(accumulator.total / accumulator.count, digits) : null;
}

function roundedPeak(accumulator: NumberAccumulator, digits: number): number | null {
  return accumulator.peak === null ? null : roundTo(accumulator.peak, digits);
}

function roundTo(value: number, digits: number): number {
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function finiteNonNegative(value: number): number | null {
  return Number.isFinite(value) && value >= 0 ? value : null;
}

function counterDelta(
  first: number,
  second: number,
  previousFirst: number | null,
  previousSecond: number | null,
): { first: number; second: number } | null {
  const normalizedFirst = finiteNonNegative(first);
  const normalizedSecond = finiteNonNegative(second);
  if (
    normalizedFirst === null
    || normalizedSecond === null
    || previousFirst === null
    || previousSecond === null
    || normalizedFirst < previousFirst
    || normalizedSecond < previousSecond
  ) {
    return null;
  }
  return {
    first: normalizedFirst - previousFirst,
    second: normalizedSecond - previousSecond,
  };
}

function dominantTransport(
  counts: ReadonlyMap<StreamDiagnostics["transportType"], number>,
): StreamDiagnostics["transportType"] {
  let selected: StreamDiagnostics["transportType"] = "unknown";
  let selectedCount = 0;
  for (const [transport, count] of counts) {
    if (count > selectedCount) {
      selected = transport;
      selectedCount = count;
    }
  }
  return selected;
}

function normalizeResolution(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, "");
}

const MIN_SAMPLE_INTERVAL_MS = 750;
const MIN_CONFIDENT_SAMPLE_COUNT = 10;
const MAX_RECOMMENDATIONS = 4;
