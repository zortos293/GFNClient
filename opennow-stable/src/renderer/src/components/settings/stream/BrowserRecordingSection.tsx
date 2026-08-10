import { Video } from "lucide-react";
import { type JSX } from "react";
import type { Settings } from "@shared/gfn";
import {
  DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS,
  MAX_RECORDING_BITRATE_MBPS,
  RECORDING_FPS_OPTIONS,
  RECORDING_RESOLUTION_OPTIONS,
} from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface BrowserRecordingSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: SettingsChangeHandler;
  handlePreview: SettingsChangeHandler;
}

export function BrowserRecordingSection({
  settings,
  showAll,
  handleChange,
  handlePreview,
}: BrowserRecordingSectionProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="settings-section">
      {showAll && (
        <div className="settings-section-context">
          {t("settings.sections.stream")}
        </div>
      )}
      <div className="settings-section-header settings-section-header--with-copy">
        <Video size={18} />
        <div>
          <h2>{t("settings.recording.title")}</h2>
          <p className="settings-section-description">
            {t("settings.recording.description")}
          </p>
        </div>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label
            className="settings-label"
            htmlFor="settings-stream-recording-resolution"
          >
            {t("settings.video.recordingResolution")}
          </label>
          <div className="settings-row-control">
            <SelectDropdown
              id="settings-stream-recording-resolution"
              value={settings.recordingResolution}
              options={RECORDING_RESOLUTION_OPTIONS.map((value) => ({
                value,
                label: value,
              }))}
              onChange={(value) =>
                handleChange(
                  "recordingResolution",
                  value as Settings["recordingResolution"],
                )
              }
              ariaLabel={t("settings.video.recordingResolution")}
            />
          </div>
        </div>

        <div className="settings-row">
          <label className="settings-label">
            {t("settings.video.recordingFps")}
          </label>
          <div className="settings-row-control">
            <div className="settings-chip-row">
              {RECORDING_FPS_OPTIONS.map((fps) => (
                <button
                  key={fps}
                  type="button"
                  className={`settings-chip ${settings.recordingFps === fps ? "active" : ""}`}
                  aria-pressed={settings.recordingFps === fps}
                  onClick={() => handleChange("recordingFps", fps)}
                >
                  <span>{fps}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label
              className="settings-label"
              htmlFor="settings-stream-recording-bitrate"
            >
              {t("settings.video.recordingBitrate")}
            </label>
            <span className="settings-value-badge">
              {settings.recordingBitrateMbps === null
                ? t("app.labels.auto")
                : `${settings.recordingBitrateMbps} Mbps`}
            </span>
          </div>
          <div className="settings-chip-row">
            <button
              type="button"
              className={`settings-chip ${settings.recordingBitrateMbps === null ? "active" : ""}`}
              aria-pressed={settings.recordingBitrateMbps === null}
              onClick={() => handleChange("recordingBitrateMbps", null)}
            >
              <span>{t("app.labels.auto")}</span>
            </button>
            <button
              type="button"
              className={`settings-chip ${settings.recordingBitrateMbps !== null ? "active" : ""}`}
              aria-pressed={settings.recordingBitrateMbps !== null}
              onClick={() => {
                handleChange(
                  "recordingBitrateMbps",
                  settings.recordingBitrateMbps ??
                    DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS,
                );
              }}
            >
              <span>{t("settings.video.customBitrate")}</span>
            </button>
          </div>
          <SettingRange
            id="settings-stream-recording-bitrate"
            className="settings-slider"
            min={1}
            max={MAX_RECORDING_BITRATE_MBPS}
            step={1}
            value={
              settings.recordingBitrateMbps ??
              DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS
            }
            disabled={settings.recordingBitrateMbps === null}
            onPreview={(value) => handlePreview("recordingBitrateMbps", value)}
            onCommit={(value) => handleChange("recordingBitrateMbps", value)}
          />
          <span className="settings-subtle-hint">
            {t("settings.video.recordingBitrateHint")}
          </span>
        </div>
      </div>
    </section>
  );
}
