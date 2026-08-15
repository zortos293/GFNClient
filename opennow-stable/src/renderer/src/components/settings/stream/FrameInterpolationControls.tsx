import { type JSX } from "react";
import type {
  FrameInterpolationFactor,
  FrameInterpolationQuality,
  Settings,
} from "@shared/gfn";
import { DEFAULT_FRAME_INTERPOLATION_SETTINGS } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

const FACTOR_OPTIONS: FrameInterpolationFactor[] = [2, 3, 4];
const QUALITY_OPTIONS: FrameInterpolationQuality[] = [360, 480, 720];

interface FrameInterpolationControlsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
}

export function FrameInterpolationControls({
  settings,
  handleChange,
}: FrameInterpolationControlsProps): JSX.Element {
  const { t } = useTranslation();
  const fi = settings.frameInterpolation;
  const nativeBlocked = settings.streamClientMode === "native";

  return (
    <div className="settings-row settings-row--column">
      <div className="settings-row-top settings-row-top--compact">
        <label
          className="settings-label settings-label--wrap"
          htmlFor="settings-stream-frame-interpolation-enabled"
        >
          <span className="settings-label-title">
            {t("settings.frameInterpolation.title")}
            <span className="settings-inline-badge settings-inline-badge--beta">
              {t("app.labels.experimental")}
            </span>
          </span>
        </label>
        <label className="settings-toggle">
          <input
            id="settings-stream-frame-interpolation-enabled"
            type="checkbox"
            checked={fi.enabled}
            disabled={nativeBlocked}
            onChange={(event) => {
              handleChange("frameInterpolation", {
                ...fi,
                enabled: event.target.checked,
              });
            }}
          />
          <span className="settings-toggle-track" />
        </label>
      </div>
      <span className="settings-subtle-hint">
        {nativeBlocked
          ? t("settings.frameInterpolation.nativeUnavailable")
          : t("settings.frameInterpolation.hint")}
      </span>
      {fi.enabled && !nativeBlocked && (
        <>
          <div className="settings-row settings-row--column">
            <div className="settings-row-top">
              <span className="settings-label">
                {t("settings.frameInterpolation.factor")}
              </span>
              <span className="settings-value-badge">{fi.factor}×</span>
            </div>
            <div className="settings-chip-row">
              {FACTOR_OPTIONS.map((factor) => (
                <button
                  key={factor}
                  type="button"
                  aria-pressed={fi.factor === factor}
                  className={`settings-chip${fi.factor === factor ? " settings-chip--active" : ""}`}
                  onClick={() => {
                    handleChange("frameInterpolation", { ...fi, factor });
                  }}
                >
                  <span>{factor}×</span>
                </button>
              ))}
            </div>
          </div>
          <div className="settings-row settings-row--column">
            <div className="settings-row-top">
              <span className="settings-label">
                {t("settings.frameInterpolation.quality")}
              </span>
              <span className="settings-value-badge">{fi.quality}p</span>
            </div>
            <div className="settings-chip-row">
              {QUALITY_OPTIONS.map((quality) => (
                <button
                  key={quality}
                  type="button"
                  aria-pressed={fi.quality === quality}
                  className={`settings-chip${fi.quality === quality ? " settings-chip--active" : ""}`}
                  onClick={() => {
                    handleChange("frameInterpolation", { ...fi, quality });
                  }}
                >
                  <span>{quality}p</span>
                </button>
              ))}
            </div>
            <span className="settings-subtle-hint">
              {t("settings.frameInterpolation.qualityHint")}
            </span>
          </div>
          <p className="settings-subtle-hint">
            {t("settings.frameInterpolation.weightsNotice")}
          </p>
          <div className="settings-chip-row">
            <button
              type="button"
              className="settings-chip"
              onClick={() => {
                handleChange("frameInterpolation", {
                  ...DEFAULT_FRAME_INTERPOLATION_SETTINGS,
                  enabled: true,
                });
              }}
            >
              <span>{t("settings.frameInterpolation.reset")}</span>
            </button>
          </div>
        </>
      )}
    </div>
  );
}
