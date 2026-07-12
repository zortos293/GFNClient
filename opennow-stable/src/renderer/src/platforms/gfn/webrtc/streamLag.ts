import type { StreamLagReason } from "./streamDiagnosticsTypes";

export interface ClassifyStreamLagReasonParams {
  nativeInputActive: boolean;
  nativeRendererActive: boolean;
  framesReceived: number;
  framesDecoded: number;
  decodeTimeMs: number;
  decodeFps: number;
  renderFps: number;
  rttMs: number;
  packetLossPercent: number;
  jitterMs: number;
  jitterBufferDelayMs: number;
  inputQueueBufferedBytes: number;
  inputQueueDropCount: number;
  decoderPressureActive: boolean;
  decoderPressureReason: string;
  decoderBacklogFrames: number;
  dropRatePercent: number;
  backpressureThresholdBytes: number;
}

/** Classify overlay lag warnings using sustained pressure signals, not timer jitter or normal decode times. */
export function classifyStreamLagReason(
  params: ClassifyStreamLagReasonParams,
): { reason: StreamLagReason; detail: string } {
  if (params.nativeInputActive || params.nativeRendererActive) {
    return {
      reason: "stable",
      detail: "Native streamer input bridge active",
    };
  }

  const networkSignals: string[] = [];
  if (params.packetLossPercent >= 1) networkSignals.push(`${params.packetLossPercent.toFixed(1)}% loss`);
  if (params.rttMs >= 75) networkSignals.push(`RTT ${params.rttMs.toFixed(0)}ms`);
  if (params.jitterMs >= 12) networkSignals.push(`jitter ${params.jitterMs.toFixed(1)}ms`);
  if (params.jitterBufferDelayMs >= 20) networkSignals.push(`buffer ${params.jitterBufferDelayMs.toFixed(1)}ms`);
  if (networkSignals.length > 0) {
    return {
      reason: "network",
      detail: networkSignals.join(" · "),
    };
  }

  const severeDecoderStall = params.framesReceived > 100 && params.framesDecoded === 0;
  if (params.decoderPressureActive || severeDecoderStall) {
    const detailParts: string[] = [];
    if (severeDecoderStall) detailParts.push("frames received but not decoded");
    if (params.decoderPressureReason === "decode_saturated" && params.decodeTimeMs > 0) {
      detailParts.push(`decode ${params.decodeTimeMs.toFixed(1)}ms`);
    }
    if (params.decoderBacklogFrames >= 45) detailParts.push(`backlog ${params.decoderBacklogFrames}`);
    if (params.dropRatePercent >= 6) detailParts.push(`${params.dropRatePercent.toFixed(1)}% drops`);
    if (detailParts.length === 0 && params.decoderPressureReason !== "stable") {
      detailParts.push(params.decoderPressureReason.replace(/_/g, " "));
    }
    return {
      reason: "decoder",
      detail: detailParts.join(" · ") || "decode pressure",
    };
  }

  if (
    params.inputQueueDropCount > 0
    || params.inputQueueBufferedBytes >= params.backpressureThresholdBytes
  ) {
    const detailParts: string[] = [];
    if (params.inputQueueDropCount > 0) detailParts.push(`drops ${params.inputQueueDropCount}`);
    if (params.inputQueueBufferedBytes >= params.backpressureThresholdBytes) {
      detailParts.push(`buffered ${(params.inputQueueBufferedBytes / 1024).toFixed(1)}KB`);
    }
    return {
      reason: "input_backpressure",
      detail: detailParts.join(" · "),
    };
  }

  if (params.renderFps > 0 && params.decodeFps > 0) {
    const renderGap = params.decodeFps - params.renderFps;
    const renderGapPercent = renderGap / params.decodeFps;
    // Absolute fps gaps are misleading at 120/240fps streams — require a large relative drop.
    const renderPressure =
      params.renderFps < 30
      || (renderGap >= 20 && renderGapPercent >= 0.2);
    if (renderPressure) {
      return {
        reason: "render",
        detail: `render ${params.renderFps}fps vs decode ${params.decodeFps}fps`,
      };
    }
  }

  return {
    reason: params.decodeFps > 0 || params.renderFps > 0 ? "stable" : "unknown",
    detail: params.decodeFps > 0 || params.renderFps > 0
      ? "No dominant lag source detected"
      : "Waiting for stream stats",
  };
}
