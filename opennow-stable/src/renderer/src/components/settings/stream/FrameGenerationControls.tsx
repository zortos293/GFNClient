import { type JSX } from "react";
import type { FrameGenerationQuality, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import {
  FRAMEGEN_RUNTIME_LICENSE_URL,
  FRAMEGEN_WEIGHTS_LICENSE_URL,
} from "../../../platforms/gfn/frameGenerationAssets";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

const QUALITY_OPTIONS: readonly FrameGenerationQuality[] = [480, 720, 1080];

interface FrameGenerationControlsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
}

export function FrameGenerationControls({
  settings,
  handleChange,
}: FrameGenerationControlsProps): JSX.Element {
  const { t } = useTranslation();
  const nativeUnavailable = settings.streamClientMode === "native";
  const filtersEnabled = settings.videoShader.enabled;

  return (
    <div className="settings-row settings-row--column">
      <div className="settings-row-top settings-row-top--compact">
        <label
          className="settings-label settings-label--wrap"
          htmlFor="settings-stream-frame-generation-enabled"
        >
          <span className="settings-label-title">
            {t("settings.frameGeneration.title")}
            <span className="settings-inline-badge settings-inline-badge--beta">
              {t("app.labels.experimental")}
            </span>
          </span>
        </label>
        <label className="settings-toggle">
          <input
            id="settings-stream-frame-generation-enabled"
            type="checkbox"
            checked={settings.frameGeneration.enabled}
            disabled={
              nativeUnavailable
              || (filtersEnabled && !settings.frameGeneration.enabled)
            }
            onChange={(event) => {
              handleChange("frameGeneration", {
                ...settings.frameGeneration,
                enabled: event.target.checked,
              });
            }}
          />
          <span className="settings-toggle-track" />
        </label>
      </div>
      <span className="settings-subtle-hint">
        {nativeUnavailable
          ? t("settings.frameGeneration.nativeUnavailable")
          : filtersEnabled
            ? t("settings.frameGeneration.filtersUnavailable")
            : t("settings.frameGeneration.hint")}
      </span>
      {settings.frameGeneration.enabled && !nativeUnavailable && (
        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <span className="settings-label">{t("settings.frameGeneration.quality")}</span>
            <span className="settings-value-badge">{settings.frameGeneration.quality}p</span>
          </div>
          <div className="settings-chip-row">
            {QUALITY_OPTIONS.map((quality) => (
              <button
                key={quality}
                type="button"
                className={`settings-chip ${settings.frameGeneration.quality === quality ? "active" : ""}`}
                aria-pressed={settings.frameGeneration.quality === quality}
                onClick={() => {
                  handleChange("frameGeneration", {
                    ...settings.frameGeneration,
                    quality,
                  });
                }}
              >
                <span>{quality}p</span>
              </button>
            ))}
          </div>
          <span className="settings-subtle-hint">
            {t("settings.frameGeneration.qualityHint")}
          </span>
        </div>
      )}
      <span className="settings-subtle-hint">
        {t("settings.frameGeneration.attribution")}{" "}
        <a href={FRAMEGEN_RUNTIME_LICENSE_URL} target="_blank" rel="noreferrer">
          {t("settings.frameGeneration.runtimeLicense")}
        </a>
        {" · "}
        <a href={FRAMEGEN_WEIGHTS_LICENSE_URL} target="_blank" rel="noreferrer">
          {t("settings.frameGeneration.weightsLicense")}
        </a>
      </span>
    </div>
  );
}
