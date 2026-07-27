import { Cpu, SlidersHorizontal, Zap } from "lucide-react";
import { useState, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import {
  shouldShowLinuxHardwareCodecHint,
  type CodecTestResult,
} from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import { MotionSpinner } from "../../MotionSpinner";
import { accelerationOptions } from "../settingsFormatters";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface CodecDiagnosticsSectionProps {
  settings: Settings;
  showAll: boolean;
  showStreamVideo: boolean;
  showStreamCodecDiagnostics: boolean;
  handleChange: SettingsChangeHandler;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
}

export function CodecDiagnosticsSection({
  settings,
  showAll,
  showStreamVideo,
  showStreamCodecDiagnostics,
  handleChange,
  codecResults,
  codecTesting,
  onRunCodecTest,
}: CodecDiagnosticsSectionProps): JSX.Element | null {
  const { t } = useTranslation();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const codecTestOpen = codecResults !== null || codecTesting;

  if (!showStreamVideo && !showStreamCodecDiagnostics) return null;

  return (
    <div className="settings-advanced-wrap">
      <button
        type="button"
        className="settings-advanced-toggle"
        onClick={() => setAdvancedOpen((open) => !open)}
      >
        <SlidersHorizontal size={14} />
        Advanced
        <svg
          viewBox="0 0 16 16"
          width="12"
          height="12"
          fill="currentColor"
          className={`settings-advanced-chevron ${advancedOpen ? "flipped" : ""}`}
        >
          <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
        </svg>
      </button>
      {advancedOpen && (
        <section className="settings-section">
          {showAll && (
            <div className="settings-section-context">{t("settings.sections.stream")}</div>
          )}
          <div className="settings-section-header">
            <Cpu size={18} />
            <h2>Advanced</h2>
          </div>
          <div className="settings-rows">
            {showStreamVideo && (
              <>
                <div className="settings-row">
                  <label className="settings-label">{t("settings.video.decoder")}</label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`decoder-${option.value}`}
                          className={`settings-chip ${settings.decoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.decoderPreference === option.value}
                          onClick={() => handleChange("decoderPreference", option.value)}
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
                  <label className="settings-label">{t("settings.video.encoder")}</label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`encoder-${option.value}`}
                          className={`settings-chip ${settings.encoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.encoderPreference === option.value}
                          onClick={() => handleChange("encoderPreference", option.value)}
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
              </>
            )}

            {showStreamCodecDiagnostics && (
              <>
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
                    {shouldShowLinuxHardwareCodecHint(codecResults) ? (
                      <div className="codec-result-hint">
                        {t("settings.codecDiagnostics.linuxHardwareHint")}
                      </div>
                    ) : null}
                    {codecResults.map((result) => (
                      <div key={result.codec} className="codec-result-card">
                        <div className="codec-result-header">
                          <span className="codec-result-name">{result.codec}</span>
                          <span className={`codec-result-badge ${result.webrtcSupported ? "supported" : "unsupported"}`}>
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
                            <span className={`codec-result-status ${result.decodeSupported ? (result.hwAccelerated ? "hw" : "sw") : "none"}`}>
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
                            <span className={`codec-result-status ${result.encodeSupported ? (result.encodeHwAccelerated ? "hw" : "sw") : "none"}`}>
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
              </>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
