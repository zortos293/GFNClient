import { AlertTriangle, Bug, CheckCircle2, X } from "lucide-react";
import { useEffect, useState, type JSX } from "react";
import type { DesktopSessionReport } from "@shared/bugReport";
import { useTranslation } from "../i18n";
import { ModalSurface } from "./ui/ModalSurface";

export interface SessionReportModalProps {
  open: boolean;
  report: DesktopSessionReport | null;
  onClose: () => void;
  onReportBug: (report: DesktopSessionReport) => void;
  onShowReportsChange: (show: boolean) => void;
}

export function SessionReportModal({
  open,
  report,
  onClose,
  onReportBug,
  onShowReportsChange,
}: SessionReportModalProps): JSX.Element {
  const { t } = useTranslation();
  const [dontShowAgain, setDontShowAgain] = useState(false);

  useEffect(() => {
    if (open) setDontShowAgain(false);
  }, [open]);

  return (
    <ModalSurface
      open={open && report !== null}
      onClose={onClose}
      motion="compact"
      overlayClassName="rh-overlay session-report-overlay"
      backdropClassName="rh-backdrop"
      panelClassName="rh-card session-report-card"
      ariaLabel={t("sessionReport.title")}
      backdropLabel={t("app.actions.close")}
    >
      {report ? (
        <>
          <div className="rh-header session-report-header">
            <div>
              <div className="rh-kicker">{t("sessionReport.kicker")}</div>
              <h2 className="rh-title">{t("sessionReport.title")}</h2>
              <p className="session-report-game">{report.gameTitle}</p>
            </div>
            <button
              type="button"
              className="rh-close-btn"
              onClick={onClose}
              aria-label={t("app.actions.close")}
            >
              <X size={18} />
            </button>
          </div>

          <div className="rh-body session-report-body">
            <section className={`session-report-score session-report-score--${report.rating}`}>
              <div
                className="session-report-score-ring"
                aria-label={t("sessionReport.scoreLabel", { score: report.score })}
              >
                <strong>{report.score}</strong>
                <span>/100</span>
              </div>
              <div>
                <span className="session-report-rating">
                  {t(`sessionReport.ratings.${report.rating}`)}
                </span>
                <p>{t("sessionReport.summary", { duration: formatDuration(report.durationSeconds) })}</p>
                {report.limitedData ? (
                  <p className="session-report-limited">
                    <AlertTriangle size={14} />
                    {t("sessionReport.limitedData", { count: report.sampleCount })}
                  </p>
                ) : null}
              </div>
            </section>

            <section>
              <h3 className="session-report-section-title">{t("sessionReport.connection")}</h3>
              <div className="session-report-metrics">
                <Metric
                  label={t("sessionReport.metrics.latency")}
                  value={formatMs(report.averagePingMs)}
                  detail={report.peakPingMs === null
                    ? undefined
                    : t("sessionReport.peak", { value: `${report.peakPingMs} ms` })}
                />
                <Metric
                  label={t("sessionReport.metrics.speed")}
                  value={formatBitrate(report.averageBitrateKbps)}
                  detail={report.peakBitrateKbps === null
                    ? undefined
                    : t("sessionReport.peak", { value: formatBitrate(report.peakBitrateKbps) })}
                />
                <Metric label={t("sessionReport.metrics.loss")} value={formatPercent(report.packetLossPercent)} />
                <Metric label={t("sessionReport.metrics.jitter")} value={formatMs(report.averageJitterMs)} />
                <Metric
                  label={t("sessionReport.metrics.fps")}
                  value={report.averageFps === null ? "—" : `${report.averageFps.toFixed(1)} / ${report.targetFps}`}
                />
                <Metric label={t("sessionReport.metrics.decode")} value={formatMs(report.averageDecodeMs)} />
              </div>
            </section>

            <section className="session-report-profile">
              <h3 className="session-report-section-title">{t("sessionReport.profile")}</h3>
              <div className="session-report-profile-grid">
                <span>{t("sessionReport.requested")}</span>
                <strong>{report.requestedResolution} · {report.requestedCodec}</strong>
                <span>{t("sessionReport.delivered")}</span>
                <strong>{report.deliveredResolution ?? "—"} · {report.deliveredCodec ?? "—"}</strong>
                <span>{t("sessionReport.route")}</span>
                <strong>
                  {[
                    report.serverLocation,
                    report.transportType === "unknown" ? null : report.transportType.toUpperCase(),
                    report.serverGpuType,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </strong>
              </div>
            </section>

            {report.findings.length > 0 ? (
              <section>
                <h3 className="session-report-section-title">{t("sessionReport.changes")}</h3>
                <div className="session-report-findings">
                  {report.findings.map((finding) => (
                    <Finding key={`${finding.title}:${finding.detail}`} finding={finding} />
                  ))}
                </div>
              </section>
            ) : null}

            <section>
              <h3 className="session-report-section-title">{t("sessionReport.recommendations")}</h3>
              <div className="session-report-findings">
                {report.recommendations.map((finding) => (
                  <Finding key={`${finding.title}:${finding.detail}`} finding={finding} />
                ))}
              </div>
            </section>

            <label className="telemetry-feedback-checkbox session-report-hide-toggle">
              <input
                type="checkbox"
                checked={dontShowAgain}
                onChange={(event) => {
                  setDontShowAgain(event.target.checked);
                  onShowReportsChange(!event.target.checked);
                }}
              />
              <span>{t("sessionReport.dontShowAgain")}</span>
            </label>
          </div>

          <div className="rh-footer session-report-footer">
            <button type="button" className="rh-btn-secondary" onClick={onClose}>
              {t("sessionReport.close")}
            </button>
            <button type="button" className="rh-btn-primary" onClick={() => onReportBug(report)}>
              <Bug size={15} />
              {t("sessionReport.reportBug")}
            </button>
          </div>
        </>
      ) : null}
    </ModalSurface>
  );
}

function Metric({ label, value, detail }: { label: string; value: string; detail?: string }): JSX.Element {
  return (
    <div className="session-report-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      {detail ? <small>{detail}</small> : null}
    </div>
  );
}

function Finding({ finding }: { finding: DesktopSessionReport["recommendations"][number] }): JSX.Element {
  const Icon = finding.kind === "warning" ? AlertTriangle : CheckCircle2;
  return (
    <article className={`session-report-finding session-report-finding--${finding.kind}`}>
      <Icon size={16} />
      <div>
        <strong>{finding.title}</strong>
        <p>{finding.detail}</p>
      </div>
    </article>
  );
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return minutes > 0 ? `${minutes}m ${remainingSeconds}s` : `${remainingSeconds}s`;
}

function formatMs(value: number | null): string {
  if (value === null) return "—";
  return `${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)} ms`;
}

function formatBitrate(kbps: number | null): string {
  if (kbps === null) return "—";
  return kbps >= 1_000 ? `${(kbps / 1_000).toFixed(1)} Mbps` : `${kbps.toFixed(0)} kbps`;
}

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(value < 1 ? 2 : 1)}%`;
}
