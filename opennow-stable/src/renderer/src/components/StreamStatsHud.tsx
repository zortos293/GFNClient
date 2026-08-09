import { useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, m, useReducedMotion } from "motion/react";
import { AlertTriangle } from "lucide-react";
import type { JSX } from "react";
import type { StatsOverlayPosition } from "@shared/gfn";
import type { StreamLagReason } from "../platforms/gfn/webrtcClient";
import type { StreamDiagnosticsStore } from "../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsStore } from "../utils/streamDiagnosticsStore";
import {
  getPacketLossColor,
  getRttColor,
  getTimingColor,
} from "../utils/streamDiagnosticsFormat";
import {
  formatOptionalBitrate,
  formatServerGameFps,
  isRttSpike,
  PACKET_LOSS_ALERT_PERCENT,
} from "../utils/streamStatsHud";
import { getStreamServerLocationLabel } from "../platforms/gfn/webrtc/sessionDiagnostics";
import {
  disclosureTransition,
  getStatusPulseMotion,
  surfaceRevealTransition,
} from "./MotionProvider";
import { useTranslation } from "../i18n";

function getLagReasonLabel(reason: StreamLagReason): string {
  switch (reason) {
    case "network":
      return "Network";
    case "decoder":
      return "Decode";
    case "input_backpressure":
      return "Input";
    case "render":
      return "Render";
    case "stable":
      return "Stable";
    default:
      return "Unknown";
  }
}

export interface StreamStatsHudProps {
  diagnosticsStore: StreamDiagnosticsStore;
  mode: "compact" | "full";
  position: StatsOverlayPosition;
  gstreamerEnabled: boolean;
  serverRegion?: string;
  sessionTimeRemainingText: string | null;
  hintsVisible?: boolean;
  shaderActive?: boolean;
}

export function StreamStatsHud({
  diagnosticsStore,
  mode,
  position,
  gstreamerEnabled,
  serverRegion,
  sessionTimeRemainingText,
  hintsVisible = false,
  shaderActive = false,
}: StreamStatsHudProps): JSX.Element {
  const { t } = useTranslation();
  const reducedMotion = useReducedMotion();
  const statusPulseMotion = getStatusPulseMotion(reducedMotion);
  const stats = useStreamDiagnosticsStore(diagnosticsStore);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [rttSpikeActive, setRttSpikeActive] = useState(false);
  const [rttSpikeValueMs, setRttSpikeValueMs] = useState(0);
  const [codecFallbackVisible, setCodecFallbackVisible] = useState(false);
  const lastRttRef = useRef(0);
  const rttSpikeTimerRef = useRef<number | undefined>(undefined);
  const codecFallbackTimerRef = useRef<number | undefined>(undefined);

  // Detect sudden RTT spikes (≥2× previous sample, ≥80ms) so the HUD can show
  // a visible "ping tinggi tiba-tiba" banner instead of relying on the log.
  // Note: this only catches sudden jumps, not gradual degradation (40→60→80→100
  // never doubles per step) — sustained high RTT is already visible via the red
  // ping KPI and the "network" lag reason.
  useEffect(() => {
    const currentRtt = stats.rttMs;
    const previousRtt = lastRttRef.current;
    lastRttRef.current = currentRtt;

    if (isRttSpike(previousRtt, currentRtt)) {
      // Freeze the spike RTT so the banner keeps showing the jumped value even
      // if the link recovers to a low RTT on the next poll before auto-hide.
      setRttSpikeValueMs(Math.round(currentRtt));
      setRttSpikeActive(true);
      if (rttSpikeTimerRef.current !== undefined) {
        window.clearTimeout(rttSpikeTimerRef.current);
      }
      rttSpikeTimerRef.current = window.setTimeout(() => {
        setRttSpikeActive(false);
        rttSpikeTimerRef.current = undefined;
      }, 5000);
    }
  }, [stats.rttMs]);

  useEffect(() => {
    return () => {
      if (rttSpikeTimerRef.current !== undefined) {
        window.clearTimeout(rttSpikeTimerRef.current);
      }
      if (codecFallbackTimerRef.current !== undefined) {
        window.clearTimeout(codecFallbackTimerRef.current);
      }
    };
  }, []);

  // ── KPI values (GFN parity) ──
  // GAME is server-reported stats_channel FPS; STREAM is locally rendered FPS.
  const gameFps = formatServerGameFps(stats);
  const streamFps = stats.renderFps > 0 ? String(stats.renderFps) : "--";
  // ── Frame pipeline (server-sent vs locally decoded) ──
  // receiveFps = what the server sent, decodeFps = what the local decoder
  // produced. A decode rate well below the RX rate (or a decode time over the
  // 16.7ms 60fps budget) means the local decoder is the bottleneck — the
  // server and network are fine.
  const receiveFpsText = !stats.nativeRendererActive && stats.receiveFps > 0
    ? `${stats.receiveFps} fps`
    : "--";
  // "0 fps" only when the server is sending but nothing is being decoded — that
  // is the decoder-stall signal this section exists to surface. Plain "--" is
  // reserved for "no data yet" (both rates zero at stream start).
  const decodeFpsText = stats.decodeFps > 0
    ? `${stats.decodeFps} fps`
    : (!stats.nativeRendererActive && stats.receiveFps > 0 ? "0 fps" : "--");
  const decodeTimeText = stats.decodeTimeMs > 0 ? `${stats.decodeTimeMs.toFixed(1)} ms` : "--";
  // Decode lagging the RX rate by >3fps, or decodeFps 0 while frames still
  // arrive (stall): the local decoder is the bottleneck. The `decodeFps > 0`
  // guard is intentionally absent so the stall case (0 < rx - 3) warns too.
  const decodeFallingBehind =
    !stats.nativeRendererActive
    && stats.receiveFps > 0
    && stats.decodeFps < stats.receiveFps - 3;
  const decodeOverBudget = stats.decodeTimeMs > 16.7;
  const decodeTimeColor = getTimingColor(stats.decodeTimeMs, 8, 16.7);
  const rttColor = getRttColor(stats.rttMs);
  const pingText = stats.rttMs > 0 ? String(Math.round(stats.rttMs)) : "--";

  const gpuTitle = stats.serverGpuType || stats.gpuType || t("stream.stats.title");
  const regionLabel = getStreamServerLocationLabel(stats, serverRegion);

  // ── Network section ──
  // Packet loss shown as a percentage over the sampling interval (WebRTC raw
  // packetsLost can go negative from duplicates, so the percent is clamped ≥0).
  const packetLossPct = Math.max(0, stats.packetLossPercent);
  const packetLossColor = getPacketLossColor(packetLossPct);
  const packetLossText = `${packetLossPct.toFixed(2)}%`;
  const lossLabel = stats.nativeRendererActive
    ? t("stream.stats.frameLoss")
    : t("stream.stats.packetLoss");
  const jitterColor = stats.nativeRendererActive
    ? "var(--ink-muted)"
    : getTimingColor(stats.jitterMs, 5, 12);
  const totalAvailableText = formatOptionalBitrate(
    stats.availableBitrateKbps,
    stats.nativeRendererActive,
  );
  const targetBitrateText = formatOptionalBitrate(stats.targetBitrateKbps);
  const totalUsedText = formatOptionalBitrate(stats.bitrateKbps);
  // Active ICE transport — "UDP" normally; "TCP" means Chromium fell back
  // because UDP is unreachable (ISP blocking/throttling), which caps the
  // stream at a low hard bitrate. Hidden in native mode (no WebRTC ICE).
  const transportKnown = !stats.nativeRendererActive && stats.transportType !== "unknown";
  const transportText = stats.transportType === "unknown"
    ? "--"
    : `${stats.transportType.toUpperCase()}${stats.localCandidateType ? ` · ${stats.localCandidateType}` : ""}`;
  const jitterText = !stats.nativeRendererActive && stats.jitterMs > 0
    ? `${stats.jitterMs.toFixed(1)}ms`
    : (!stats.nativeRendererActive && (stats.rttMs > 0 || stats.framesDecoded > 0) ? "<0.1ms" : "--");

  // ── Stream section ──
  const resolutionText = stats.resolution && stats.resolution !== ""
    ? stats.resolution
    : (stats.nativeRendererActive ? "Native renderer" : "--");
  const codecText = [stats.codec, stats.colorCodec].filter((v) => v && v !== "").join(", ") || "--";
  // True when the live stream negotiated a different codec than the one
  // requested in settings (e.g. AV1 requested but it couldn't be negotiated, so
  // the session fell back to H265). Surfacing this makes silent codec fallback
  // visible to the user, with a short reason showing both endpoints.
  const codecFellBack = Boolean(
    stats.codec
    && stats.requestedCodec
    && stats.requestedCodec !== stats.codec,
  );
  const codecFallbackText = codecFellBack
    ? t("stream.stats.codecFallback", { requested: stats.requestedCodec, negotiated: stats.codec })
    : "";
  const codecFallbackShortText = codecFellBack
    ? t("stream.stats.codecFallbackShort", { requested: stats.requestedCodec, negotiated: stats.codec })
    : "";

  // Transient codec-fallback notice: the yellow pill (compact) and the
  // fallback line (full) appear for a few seconds once a fallback is detected,
  // then auto-hide for the session — the negotiated codec stays visible in the
  // Codec row, so the notice is a heads-up, not a permanent sticker.
  useEffect(() => {
    if (codecFellBack) {
      setCodecFallbackVisible(true);
      if (codecFallbackTimerRef.current !== undefined) {
        window.clearTimeout(codecFallbackTimerRef.current);
      }
      codecFallbackTimerRef.current = window.setTimeout(() => {
        setCodecFallbackVisible(false);
        codecFallbackTimerRef.current = undefined;
      }, 5000);
    }
  }, [codecFellBack]);

  const hasLagIssue = stats.lagReason !== "stable" && stats.lagReason !== "unknown";
  const effectivePacketLossPercent = packetLossPct;
  const hasPacketLoss = packetLossPct >= PACKET_LOSS_ALERT_PERCENT;
  const bannerPacketLoss = hasPacketLoss;
  const hasIssues = hasLagIssue || hasPacketLoss;

  const advancedLines = useMemo(() => {
    const lines: string[] = [];
    lines.push(
      `Decode ${stats.decodeTimeMs.toFixed(1)}ms · Render ${stats.renderTimeMs.toFixed(1)}ms · JitterBuf ${stats.jitterBufferDelayMs.toFixed(1)}ms · Jitter ${stats.jitterMs.toFixed(1)}ms`,
    );
    lines.push(
      `Input queue ${(stats.inputQueueBufferedBytes / 1024).toFixed(1)}KB · peak ${(stats.inputQueuePeakBufferedBytes / 1024).toFixed(1)}KB · drops ${stats.inputQueueDropCount} · sched ${stats.inputQueueMaxSchedulingDelayMs.toFixed(1)}ms · residual ${stats.mouseResidualMagnitude.toFixed(2)}px`,
    );
    lines.push(
      `Mouse flush ${stats.mouseFlushIntervalMs.toFixed(0)}ms · ${stats.mousePacketsPerSecond}/s · PR ${stats.partiallyReliableInputOpen ? `${stats.mouseMoveTransport} · ${(stats.partiallyReliableInputQueueBufferedBytes / 1024).toFixed(1)}KB` : "off"}`,
    );
    lines.push(
      gstreamerEnabled
        ? `GStreamer enabled · ${stats.nativeRendererActive ? "in use" : "not active"}`
        : "GStreamer disabled · Chromium WebRTC",
    );
    if (!stats.nativeRendererActive && stats.transportType !== "unknown") {
      lines.push(`ICE ${transportText} candidate`);
    }
    const hwLine = [stats.hardwareAcceleration, stats.gpuType].filter(Boolean).join(" · ");
    if (hwLine) lines.push(hwLine);
    if (shaderActive) {
      lines.push("Shader FX active (WebGL post-processing)");
    }
    if (stats.decoderPressureActive || stats.decoderRecoveryAttempts > 0) {
      lines.push(
        `Decoder recovery ${stats.decoderPressureActive ? "active" : "idle"} · attempts ${stats.decoderRecoveryAttempts} · action ${stats.decoderRecoveryAction}`,
      );
    }
    if (stats.nativeTransitionSummary || stats.nativeQueueMode || stats.nativeCapsFramerate) {
      lines.push(
        `Native transition ${stats.nativeTransitionSummary ?? "none"} · queue ${stats.nativeQueueMode ?? "unknown"} · caps ${stats.nativeCapsFramerate ?? "unknown"}${typeof stats.nativeRequestedFps === "number" ? ` · requested ${stats.nativeRequestedFps}fps` : ""}${typeof stats.nativeFramesPendingToPresent === "number" ? ` · pending ${stats.nativeFramesPendingToPresent}` : ""}${typeof stats.nativePartialFlushCount === "number" || typeof stats.nativeCompleteFlushCount === "number" ? ` · flush ${stats.nativePartialFlushCount ?? 0}/${stats.nativeCompleteFlushCount ?? 0}` : ""}`,
      );
    }
    if (stats.nativeRequestedStreamingFeaturesSummary || stats.nativeFinalizedStreamingFeaturesSummary) {
      lines.push(
        `Stream features requested ${stats.nativeRequestedStreamingFeaturesSummary ?? "none"} · finalized ${stats.nativeFinalizedStreamingFeaturesSummary ?? "none"}`,
      );
    }
    if (hasLagIssue) {
      lines.push(`Lag source ${getLagReasonLabel(stats.lagReason).toLowerCase()} · ${stats.lagReasonDetail}`);
    }
    return lines;
  }, [gstreamerEnabled, hasLagIssue, shaderActive, stats, transportText]);

  const kpiRow = (
    <div className="sv-stats-kpis">
      <div className="sv-stats-kpi-card">
        <span className="sv-stats-kpi-num">{gameFps}</span>
        <span className="sv-stats-kpi-unit">{t("stream.stats.fpsUnit")}</span>
        <span className="sv-stats-kpi-name">{t("stream.stats.game")}</span>
      </div>
      <div className="sv-stats-kpi-card">
        <span className="sv-stats-kpi-num">{streamFps}</span>
        <span className="sv-stats-kpi-unit">{t("stream.stats.fpsUnit")}</span>
        <span className="sv-stats-kpi-name">{t("stream.stats.stream")}</span>
      </div>
      <div className="sv-stats-kpi-card">
        <span className="sv-stats-kpi-num" style={{ color: rttColor }}>{pingText}</span>
        <span className="sv-stats-kpi-unit">{t("stream.stats.msUnit")}</span>
        <span className="sv-stats-kpi-name">{t("stream.stats.ping")}</span>
      </div>
    </div>
  );

  return (
    <m.aside
      className={[
        "sv-stats",
        `sv-stats--${mode}`,
        `sv-stats--pos-${position}`,
        hasIssues ? "sv-stats--warn" : "",
        hintsVisible ? "sv-stats--hints" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 6 }}
      transition={surfaceRevealTransition}
      aria-label={t("stream.stats.overlayLabel")}
    >
      <header className="sv-stats-head">
        <span className="sv-stats-head-accent" aria-hidden />
        <span className="sv-stats-head-title">{gpuTitle}</span>
        {hasIssues && (
          <m.span
            className="sv-stats-alert-dot"
            aria-hidden
            animate={statusPulseMotion.animate}
            transition={statusPulseMotion.transition}
          >
            <AlertTriangle size={12} />
          </m.span>
        )}
      </header>

      {/* Transient network alert banner: sudden RTT spike or real packet loss. */}
      <AnimatePresence initial={false}>
        {(rttSpikeActive || bannerPacketLoss) && (
          <m.div
            key="network-alert"
            className={[
              "sv-stats-net-alert",
              rttSpikeActive && bannerPacketLoss ? "sv-stats-net-alert--critical" : "",
            ].filter(Boolean).join(" ")}
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={disclosureTransition}
            role="status"
          >
            <AlertTriangle size={12} aria-hidden />
            <span>
              {rttSpikeActive && `RTT spike ${rttSpikeValueMs}ms`}
              {rttSpikeActive && bannerPacketLoss && " · "}
              {bannerPacketLoss && (
                stats.nativeRendererActive
                  ? `${effectivePacketLossPercent.toFixed(2)}% frame loss`
                  : `${effectivePacketLossPercent.toFixed(2)}% packet loss`
              )}
            </span>
          </m.div>
        )}
      </AnimatePresence>

      {kpiRow}

      {mode === "compact" ? (
        <>
          <div className="sv-stats-serverbar" title={regionLabel}>
            {regionLabel}
          </div>
          {codecFallbackVisible && (
            <div className="sv-stats-serverbar sv-stats-serverbar--codec-fallback" title={codecFallbackText}>
              {codecFallbackShortText}
            </div>
          )}
          {shaderActive && (
            <div
              className="sv-stats-serverbar sv-stats-serverbar--shader"
              title="Client-side WebGL post-processing is applying a visible effect to the stream frames"
            >
              Shader FX on
            </div>
          )}
        </>
      ) : (
        <div className="sv-stats-full">
          <section className="sv-stats-section">
            <h4 className="sv-stats-section-title">{t("stream.stats.framePipeline")}</h4>
            <div className="sv-stats-row" title={t("stream.stats.serverRxHint")}>
              <span>{t("stream.stats.serverRx")}</span>
              <span>{receiveFpsText}</span>
            </div>
            <div className="sv-stats-row" title={t("stream.stats.localDecodeHint")}>
              <span>{t("stream.stats.localDecode")}</span>
              <span style={{ color: decodeFallingBehind ? "var(--warning)" : undefined }}>{decodeFpsText}</span>
            </div>
            <div className="sv-stats-row" title={t("stream.stats.frameDecodeTimeHint")}>
              <span>{t("stream.stats.frameDecodeTime")}</span>
              <span style={{ color: decodeOverBudget ? decodeTimeColor : undefined }}>{decodeTimeText}</span>
            </div>
          </section>

          <section className="sv-stats-section">
            <h4 className="sv-stats-section-title">{t("stream.stats.network")}</h4>
            <p className="sv-stats-subhead">{t("stream.stats.stability")}</p>
            <div className="sv-stats-row">
              <span>{lossLabel}</span>
              <span style={{ color: hasPacketLoss ? packetLossColor : undefined }}>{packetLossText}</span>
            </div>
            <div className="sv-stats-row">
              <span>{t("stream.stats.jitter")}</span>
              <span style={{ color: jitterColor }}>{jitterText}</span>
            </div>
            <p className="sv-stats-subhead">{t("stream.stats.bandwidth")}</p>
            <div className="sv-stats-row">
              <span>{t("stream.stats.totalAvailable")}</span>
              <span>{totalAvailableText}</span>
            </div>
            <div className="sv-stats-row">
              <span>{t("stream.stats.targetBitrate")}</span>
              <span>{targetBitrateText}</span>
            </div>
            <div className="sv-stats-row">
              <span>{t("stream.stats.totalUsed")}</span>
              <span>{totalUsedText}</span>
            </div>
            {transportKnown && (
              <div className="sv-stats-row">
                <span>{t("stream.stats.transport")}</span>
                <span>{transportText}</span>
              </div>
            )}
          </section>

          <section className="sv-stats-section">
            <h4 className="sv-stats-section-title">{t("stream.stats.streamSection")}</h4>
            <div className="sv-stats-row">
              <span>{t("stream.stats.resolution")}</span>
              <span>{resolutionText}</span>
            </div>
            <div className="sv-stats-row">
              <span>{t("stream.stats.codec")}</span>
              <span>{codecText}</span>
            </div>
            {codecFallbackVisible && (
              <p className="sv-stats-foot">{codecFallbackText}</p>
            )}
            {shaderActive && (
              <div className="sv-stats-row">
                <span>{t("stream.stats.shaderFx")}</span>
                <span>{t("stream.stats.on")}</span>
              </div>
            )}
            <div className="sv-stats-row">
              <span>{t("stream.stats.serverLocation")}</span>
              <span>{regionLabel}</span>
            </div>
            {sessionTimeRemainingText && (
              <div className="sv-stats-row">
                <span>{t("stream.stats.timeRemainingShort")}</span>
                <span>{sessionTimeRemainingText}</span>
              </div>
            )}
          </section>

          {advancedLines.length > 0 && (
            <div className="sv-stats-advanced">
              <button
                type="button"
                className="sv-stats-advanced-toggle"
                onClick={() => setAdvancedOpen((v) => !v)}
                aria-expanded={advancedOpen}
              >
                {advancedOpen ? t("stream.stats.hideAdvanced") : t("stream.stats.showAdvanced")}
              </button>
              <AnimatePresence initial={false}>
                {advancedOpen && (
                  <m.div
                    key="advanced"
                    className="sv-stats-advanced-body"
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={disclosureTransition}
                  >
                    {advancedLines.map((line) => (
                      <p key={line} className="sv-stats-foot">{line}</p>
                    ))}
                  </m.div>
                )}
              </AnimatePresence>
            </div>
          )}
        </div>
      )}
    </m.aside>
  );
}
