import { Info, RefreshCcw, Download, FileDown, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState, type JSX } from "react";
import type { AppUpdaterState, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { formatBytes, formatUpdaterTimestamp, getUpdaterBadgeLabel } from "../settingsFormatters";
import { MotionSpinner } from "../../MotionSpinner";

export interface SettingsAboutSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  onOpenWhatsNew?: () => void;
}

export function SettingsAboutSection({ settings, showAll, handleChange, onOpenWhatsNew }: SettingsAboutSectionProps): JSX.Element {
  const { t } = useTranslation();
  const [updaterState, setUpdaterState] = useState<AppUpdaterState>({
    status: "idle",
    currentVersion: "0.0.0",
    currentDisplayVersion: "0.0.0",
    updateSource: "github-releases",
    canCheck: false,
    canDownload: false,
    canInstall: false,
    isPackaged: false,
  });

  useEffect(() => {
    let cancelled = false;

    void window.openNow.getUpdaterState().then((state) => {
      if (!cancelled) {
        setUpdaterState(state);
      }
    }).catch((error) => {
      console.warn("[Settings] Failed to load updater state:", error);
    });

    const unsubscribe = window.openNow.onUpdaterStateChanged((state) => {
      if (!cancelled) {
        setUpdaterState(state);
      }
    });

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  const updaterLastCheckedLabel = useMemo(() => formatUpdaterTimestamp(updaterState.lastCheckedAt), [updaterState.lastCheckedAt]);
  const updaterProgressPercent = updaterState.progress ? Math.max(0, Math.min(100, Math.round(updaterState.progress.percent))) : 0;
  const updaterProgressLabel = updaterState.progress
    ? `${formatBytes(updaterState.progress.transferred)} / ${formatBytes(updaterState.progress.total || updaterState.progress.transferred)}`
    : null;
  const updaterDownloadRateLabel = updaterState.progress?.bytesPerSecond
    ? `${formatBytes(updaterState.progress.bytesPerSecond)}/s`
    : null;
  const updaterBadgeLabel = useMemo(() => getUpdaterBadgeLabel(updaterState), [updaterState]);

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.about")}</div>}
      <div className="settings-section-header">
        <h2>{t("settings.sections.about")}</h2>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label">
            {t("settings.about.whatsNew")}
            <span className="settings-hint">{t("settings.about.whatsNewHint")}</span>
          </label>
          <button
            type="button"
            className="settings-export-logs-btn"
            onClick={() => onOpenWhatsNew?.()}
          >
            <Info size={16} />
            {t("settings.about.whatsNew")}
          </button>
        </div>

        <div className="settings-row">
          <label className="settings-label settings-label--wrap">
            <span className="settings-label-title">
              {t("settings.about.applicationUpdates")}
              <span className={`settings-inline-badge settings-inline-badge--updater settings-inline-badge--updater-${updaterState.status}`}>
                {updaterBadgeLabel}
              </span>
            </span>
            <span className="settings-hint">
              {t("settings.about.version", { version: updaterState.currentDisplayVersion ?? updaterState.currentVersion })} · {settings.autoCheckForUpdates
                ? t("settings.about.backgroundChecksOn")
                : t("settings.about.backgroundChecksOff")}
            </span>
            {updaterState.message ? (
              <span className="settings-hint settings-hint--updater-message">{updaterState.message}</span>
            ) : null}
            {updaterLastCheckedLabel ? (
              <span className="settings-hint">{t("settings.about.lastChecked", { value: updaterLastCheckedLabel })}</span>
            ) : null}
            {updaterState.availableVersion && updaterState.status !== "downloaded" ? (
              <span className="settings-hint">{t("settings.about.availableVersion", { version: updaterState.availableVersion })}</span>
            ) : null}
            {updaterState.downloadedVersion ? (
              <span className="settings-hint">{t("settings.about.downloadedVersion", { version: updaterState.downloadedVersion })}</span>
            ) : null}
            {updaterState.status === "downloading" && updaterState.progress ? (
              <span className="settings-hint">
                {t("settings.about.downloadProgress", { percent: updaterProgressPercent })}{updaterProgressLabel ? ` · ${updaterProgressLabel}` : ""}{updaterDownloadRateLabel ? ` · ${updaterDownloadRateLabel}` : ""}
              </span>
            ) : null}
          </label>
          <div className="settings-updater-actions">
            <button
              type="button"
              className="settings-export-logs-btn"
              disabled={!updaterState.canCheck}
              onClick={() => {
                void window.openNow.checkForUpdates().catch((error) => {
                  console.error("[Settings] Failed to trigger update check:", error);
                });
              }}
            >
              {updaterState.status === "checking" ? <MotionSpinner size={16} label="Checking for updates" /> : <RefreshCcw size={16} />}
              {t("settings.about.checkForUpdates")}
            </button>
            {updaterState.status === "available" ? (
              <button
                type="button"
                className="settings-export-logs-btn"
                disabled={!updaterState.canDownload}
                onClick={() => {
                  void window.openNow.downloadUpdate().catch((error) => {
                    console.error("[Settings] Failed to download update:", error);
                  });
                }}
              >
                <Download size={16} />
                {t("settings.about.downloadUpdate")}
              </button>
            ) : null}
            {updaterState.status === "downloaded" ? (
              <button
                type="button"
                className="settings-save-btn settings-save-btn--compact"
                disabled={!updaterState.canInstall}
                onClick={() => {
                  void window.openNow.installUpdateAndRestart().catch((error) => {
                    console.error("[Settings] Failed to install update:", error);
                  });
                }}
              >
                <RefreshCcw size={16} />
                {t("settings.about.restartToInstall")}
              </button>
            ) : null}
          </div>
        </div>

        <div className="settings-row">
          <label className="settings-label settings-label--wrap">
            {t("settings.about.automaticallyCheckForUpdates")}
            <span className="settings-hint">
              {t("settings.about.automaticallyCheckForUpdatesOnHint")}
            </span>
            <span className="settings-hint">
              {t("settings.about.automaticallyCheckForUpdatesOffHint")}
            </span>
          </label>
          <label className="settings-toggle">
            <input
              type="checkbox"
              checked={settings.autoCheckForUpdates}
              onChange={(e) => handleChange("autoCheckForUpdates", e.target.checked)}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>

        {updaterState.status === "downloading" && updaterState.progress ? (
          <div className="settings-row settings-row--column">
            <div className="settings-updater-progress">
              <div className="settings-updater-progress-bar" style={{ width: `${updaterProgressPercent}%` }} />
            </div>
          </div>
        ) : null}

        <div className="settings-row">
          <label className="settings-label">
            {t("settings.about.exportLogs")}
            <span className="settings-hint">{t("settings.about.exportLogsHint")}</span>
          </label>
          <button
            type="button"
            className="settings-export-logs-btn"
            onClick={async () => {
              try {
                const logs = await window.openNow.exportLogs("text");
                const blob = new Blob([logs], { type: "text/plain" });
                const url = URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = `opennow-logs-${new Date().toISOString().replace(/[:.]/g, "-")}.txt`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
              } catch (err) {
                console.error("[Settings] Failed to export logs:", err);
                alert(t("settings.about.exportLogsFailed"));
              }
            }}
          >
            <FileDown size={16} />
            {t("settings.about.exportLogs")}
          </button>
        </div>

        <div className="settings-row">
          <label className="settings-label">
            {t("settings.about.deleteCache")}
            <span className="settings-hint">{t("settings.about.deleteCacheHint")}</span>
          </label>
          <button
            type="button"
            className="settings-delete-cache-btn"
            onClick={async () => {
              if (!window.confirm(t("settings.about.deleteCacheConfirm"))) {
                return;
              }
              try {
                await window.openNow.deleteCache();
                alert(t("settings.about.cacheCleared"));
              } catch (err) {
                console.error("[Settings] Failed to delete cache:", err);
                alert(t("settings.about.deleteCacheFailed"));
              }
            }}
          >
    	                  <Trash2 size={16} />
    	                  {t("settings.about.deleteCache")}
    	                </button>
        </div>

      </div>
    </section>
  );
}
