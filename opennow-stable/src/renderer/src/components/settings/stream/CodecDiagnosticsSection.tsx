import { Zap } from "lucide-react";
import { useEffect, useState, type JSX } from "react";
import type { GpuBackendInfo, Settings } from "@shared/gfn";
import {
  getGpuBackendInfo,
  getGpuDriverSubtitle,
  shouldShowLinuxHardwareCodecHint,
  shouldShowQuickSyncDriverHint,
  type CodecTestResult,
} from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import { MotionSpinner } from "../../MotionSpinner";
import { accelerationOptions } from "../settingsFormatters";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface CodecDiagnosticsSectionProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
}

export function CodecDiagnosticsSection({
  settings,
  handleChange,
  codecResults,
  codecTesting,
  onRunCodecTest,
}: CodecDiagnosticsSectionProps): JSX.Element | null {
  const { t } = useTranslation();
  const codecTestOpen = codecResults !== null || codecTesting;
  const [gpuSubtitle, setGpuSubtitle] = useState<{
    name: string;
    version: string | null;
  } | null>(null);

  useEffect(() => {
    if (!codecTestOpen) {
      setGpuSubtitle(null);
      return;
    }
    let cancelled = false;
    void getGpuBackendInfo().then((gpuInfo: GpuBackendInfo | null) => {
      if (!cancelled) {
        setGpuSubtitle(getGpuDriverSubtitle(gpuInfo));
      }
    });
    return () => {
      cancelled = true;
    };
  }, [codecTestOpen]);

  return (
    <div className="settings-group">
      <div className="settings-group-header">
        <h3>{t("settings.diagnostics.codecHardware")}</h3>
        <p>{t("settings.diagnostics.codecHardwareHint")}</p>
          </div>
      <div className="settings-group-rows">
                <div className="settings-row">
          <label className="settings-label">
            {t("settings.video.decoder")}
          </label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`decoder-${option.value}`}
                          className={`settings-chip ${settings.decoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.decoderPreference === option.value}
                  onClick={() =>
                    handleChange("decoderPreference", option.value)
                  }
                        >
                          {option.value === "auto"
                            ? t("app.labels.auto")
                            : option.value === "hardware"
                              ? t("app.labels.hardware")
                              : t("settings.video.softwareCpu")}
                        </button>
                      ))}
                    </div>
                    <span className="settings-subtle-hint">
                      {t("settings.video.appliesAfterRestart")}
                    </span>
                  </div>
                </div>

                <div className="settings-row">
          <label className="settings-label">
            {t("settings.video.encoder")}
          </label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`encoder-${option.value}`}
                          className={`settings-chip ${settings.encoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.encoderPreference === option.value}
                  onClick={() =>
                    handleChange("encoderPreference", option.value)
                  }
                        >
                          {option.value === "auto"
                            ? t("app.labels.auto")
                            : option.value === "hardware"
                              ? t("app.labels.hardware")
                              : t("settings.video.softwareCpu")}
                        </button>
                      ))}
                    </div>
                    <span className="settings-subtle-hint">
                      {t("settings.video.appliesAfterRestart")}
                    </span>
                  </div>
                </div>
                <div className="settings-row codec-test-row">
                  <label className="settings-label codec-test-description">
                    {t("settings.codecDiagnostics.description")}
                  </label>
                  <button
                    className="codec-test-btn"
                    onClick={() => {
                      void onRunCodecTest();
                    }}
                    disabled={codecTesting}
                    type="button"
                  >
                    {codecTesting ? (
                      <>
                        <MotionSpinner size={16} className="settings-loading-icon" />
                        {t("settings.video.testing")}
                      </>
                    ) : (
                      <>
                        <Zap size={16} />
                        {codecResults
                          ? t("settings.codecDiagnostics.retest")
                          : t("settings.codecDiagnostics.testCodecs")}
                      </>
                    )}
                  </button>
                </div>
                {codecTestOpen && codecResults && (
                  <div className="codec-results">
                    {gpuSubtitle && (
                      <div className="codec-result-hint">
                        {gpuSubtitle.version
                          ? t("settings.codecDiagnostics.gpuDriverLine", {
                              gpu: gpuSubtitle.name,
                              version: gpuSubtitle.version,
                            })
                          : gpuSubtitle.name}
                      </div>
                    )}
                    {shouldShowLinuxHardwareCodecHint(codecResults) ? (
                      <div className="codec-result-hint">
                        {t("settings.codecDiagnostics.linuxHardwareHint")}
                      </div>
                    ) : null}
                    {shouldShowQuickSyncDriverHint(codecResults) ? (
                      <div className="codec-result-hint">
                        {t("settings.codecDiagnostics.quickSyncHint")}
                      </div>
                    ) : null}
                    {codecResults.map((result) => (
                      <div key={result.codec} className="codec-result-card">
                        <div className="codec-result-header">
                          <span className="codec-result-name">{result.codec}</span>
                  <span
                    className={`codec-result-badge ${result.webrtcSupported ? "supported" : "unsupported"}`}
                  >
                            {result.webrtcSupported
                              ? t("settings.codecDiagnostics.webrtcReady")
                              : t("settings.codecDiagnostics.notInWebrtc")}
                          </span>
                        </div>
                        <div className="codec-result-rows">
                          <div className="codec-result-row">
                            <span className="codec-result-direction">
                              {t("settings.codecDiagnostics.decode")}
                            </span>
                    <span
                      className={`codec-result-status ${result.decodeSupported ? (result.hwAccelerated ? "hw" : "sw") : "none"}`}
                    >
                              {result.decodeSupported
                                ? result.hwAccelerated
                                  ? t("settings.video.gpu")
                                  : t("settings.video.cpu")
                                : t("app.labels.no")}
                            </span>
                            <span className="codec-result-via">{result.decodeVia}</span>
                          </div>
                          <div className="codec-result-row">
                            <span className="codec-result-direction">
                              {t("settings.codecDiagnostics.encode")}
                            </span>
                    <span
                      className={`codec-result-status ${result.encodeSupported ? (result.encodeHwAccelerated ? "hw" : "sw") : "none"}`}
                    >
                              {result.encodeSupported
                                ? result.encodeHwAccelerated
                                  ? t("settings.video.gpu")
                                  : t("settings.video.cpu")
                                : t("app.labels.no")}
                            </span>
                            <span className="codec-result-via">{result.encodeVia}</span>
                          </div>
                        </div>
                        {result.profiles.length > 0 && (
                          <div className="codec-result-profiles">
                            <span className="codec-result-profiles-label">
                              {t("settings.codecDiagnostics.profiles")}
                            </span>
                            <div className="codec-result-profiles-list">
                              {result.profiles.map((profile, index) => (
                                <code key={index} className="codec-result-profile">
                                  {profile}
                                </code>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
          </div>
    </div>
  );
}
