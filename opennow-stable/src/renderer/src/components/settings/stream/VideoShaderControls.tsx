import { type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { DEFAULT_VIDEO_SHADER_SETTINGS } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SettingRange } from "../SettingRange";
import { SettingRow, SettingToggleRow } from "../SettingRow";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

const VIDEO_SHADER_CONTROLS = [
  { key: "sharpen", labelKey: "settings.videoFilters.sharpen", min: 0, max: 100 },
  { key: "saturation", labelKey: "settings.videoFilters.saturation", min: 0, max: 200 },
  { key: "contrast", labelKey: "settings.videoFilters.contrast", min: 50, max: 150 },
  { key: "brightness", labelKey: "settings.videoFilters.brightness", min: 50, max: 150 },
  { key: "vibrance", labelKey: "settings.videoFilters.vibrance", min: 0, max: 100 },
  { key: "filmGrain", labelKey: "settings.videoFilters.filmGrain", min: 0, max: 100 },
] as const;

interface VideoShaderControlsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
  handlePreview: SettingsChangeHandler;
}

export function VideoShaderControls({
  settings,
  handleChange,
  handlePreview,
}: VideoShaderControlsProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <>
      <SettingToggleRow
        htmlFor="settings-stream-video-filters-enabled"
        label={<>{t("settings.videoFilters.title")}<span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span></>}
        description={settings.streamClientMode === "native" ? t("settings.videoFilters.nativeUnavailable") : t("settings.videoFilters.hint")}
        checked={settings.videoShader.enabled}
        onChange={(checked) => handleChange("videoShader", { ...settings.videoShader, enabled: checked })}
      />
      {settings.videoShader.enabled && (
        <>
          {VIDEO_SHADER_CONTROLS.map((control) => (
            <SettingRow key={control.key} htmlFor={`settings-stream-video-filter-${control.key}`} label={t(control.labelKey)}>
                <span className="settings-value-badge settings-value-badge--control">
                  {settings.videoShader[control.key]}%
                </span>
              <SettingRange
                id={`settings-stream-video-filter-${control.key}`}
                className="settings-slider"
                min={control.min}
                max={control.max}
                step={1}
                value={settings.videoShader[control.key]}
                onPreview={(value) => {
                  handlePreview("videoShader", {
                    ...settings.videoShader,
                    [control.key]: value,
                  });
                }}
                onCommit={(value) => {
                  handleChange("videoShader", {
                    ...settings.videoShader,
                    [control.key]: value,
                  });
                }}
              />
            </SettingRow>
          ))}
          <div className="settings-row settings-row--actions">
            <button
              type="button"
              className="settings-chip"
              onClick={() => {
                handleChange("videoShader", {
                  ...DEFAULT_VIDEO_SHADER_SETTINGS,
                  enabled: true,
                });
              }}
            >
              <span>{t("settings.videoFilters.reset")}</span>
            </button>
          </div>
        </>
      )}
    </>
  );
}
