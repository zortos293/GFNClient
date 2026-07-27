import { useCallback, useEffect, useMemo, type JSX } from "react";
import type {
  ColorQuality,
  EntitledResolution,
  Settings,
  VideoCodec,
} from "@shared/gfn";
import {
  colorQualityRequiresHevc,
  expandEntitledStreamResolutions,
  getSafeFallbackEntitledResolutions,
  resolveEntitledStreamProfile,
} from "@shared/gfn";
import { getCodecDecodeBadgeState, type CodecTestResult } from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import { MotionSpinner } from "../../MotionSpinner";
import { SelectDropdown, type SelectDropdownOption } from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
import {
  codecOptions,
  colorQualityOptions,
  getFpsForResolution,
  groupResolutions,
  inferAspectRatioFromResolution,
  STATIC_FPS_PRESETS,
  STATIC_RESOLUTION_PRESETS,
} from "../settingsFormatters";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface StreamQualityControlsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
  handlePreview: SettingsChangeHandler;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  entitledResolutions: EntitledResolution[];
  subscriptionInfoLoaded: boolean;
  subscriptionLoading: boolean;
}

export function StreamQualityControls({
  settings,
  handleChange,
  handlePreview,
  codecResults,
  codecTesting,
  entitledResolutions,
  subscriptionInfoLoaded,
  subscriptionLoading,
}: StreamQualityControlsProps): JSX.Element {
  const { t } = useTranslation();
  const effectiveEntitledResolutions = useMemo(() => {
    const baseResolutions = entitledResolutions.length > 0
      ? entitledResolutions
      : subscriptionInfoLoaded
        ? getSafeFallbackEntitledResolutions()
        : [];
    return expandEntitledStreamResolutions(baseResolutions);
  }, [entitledResolutions, subscriptionInfoLoaded]);
  const useEntitledStreamOptions = effectiveEntitledResolutions.length > 0;
  const resolutionGroups = useMemo(
    () => useEntitledStreamOptions ? groupResolutions(effectiveEntitledResolutions) : [],
    [effectiveEntitledResolutions, useEntitledStreamOptions],
  );
  const dynamicFpsOptions = useMemo(
    () => useEntitledStreamOptions
      ? getFpsForResolution(effectiveEntitledResolutions, settings.resolution)
      : [],
    [effectiveEntitledResolutions, settings.resolution, useEntitledStreamOptions],
  );
  const resolvedEntitledProfile = useMemo(
    () => resolveEntitledStreamProfile(effectiveEntitledResolutions, {
      resolution: settings.resolution,
      fps: settings.fps,
    }),
    [effectiveEntitledResolutions, settings.fps, settings.resolution],
  );
  const resolutionOptions = useMemo<SelectDropdownOption[]>(
    () => useEntitledStreamOptions
      ? resolutionGroups.flatMap((group) => group.resolutions.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
          group: group.category,
        })))
      : STATIC_RESOLUTION_PRESETS.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
        })),
    [resolutionGroups, useEntitledStreamOptions],
  );

  const handleResolutionChange = useCallback((resolution: string): void => {
    handleChange("resolution", resolution);
    const aspectRatio = inferAspectRatioFromResolution(resolution);
    if (settings.aspectRatio !== aspectRatio) {
      handleChange("aspectRatio", aspectRatio);
    }
  }, [handleChange, settings.aspectRatio]);

  useEffect(() => {
    if (!useEntitledStreamOptions || !resolvedEntitledProfile) return;

    if (resolvedEntitledProfile.resolution !== settings.resolution) {
      handleResolutionChange(resolvedEntitledProfile.resolution);
    }
    if (resolvedEntitledProfile.fps !== settings.fps) {
      handleChange("fps", resolvedEntitledProfile.fps);
    }
  }, [
    handleChange,
    handleResolutionChange,
    resolvedEntitledProfile,
    settings.fps,
    settings.resolution,
    useEntitledStreamOptions,
  ]);

  const handleColorQualityChange = useCallback((colorQuality: ColorQuality): void => {
    if (colorQualityRequiresHevc(colorQuality) && settings.codec === "H264") {
      handleChange("codec", "H265");
    }
    handleChange("colorQuality", colorQuality);
  }, [handleChange, settings.codec]);

  const handleCodecChange = useCallback((codec: VideoCodec): void => {
    handleChange("codec", codec);
    if (codec === "H264" && settings.colorQuality !== "8bit_420") {
      handleChange("colorQuality", "8bit_420");
    }
  }, [handleChange, settings.colorQuality]);

  return (
    <>
      <div className="settings-row">
        <label className="settings-label" htmlFor="settings-stream-resolution">
          <span className="settings-label-title">
            {t("settings.video.resolution")}
            {subscriptionLoading && <MotionSpinner size={12} className="settings-loading-icon" />}
          </span>
        </label>
        <div className="settings-row-control">
          <SelectDropdown
            id="settings-stream-resolution"
            value={settings.resolution}
            options={resolutionOptions}
            onChange={handleResolutionChange}
            menuClassName="select-dropdown__menu--grouped"
          />
        </div>
      </div>

      <div className="settings-row">
        <label className="settings-label">{t("settings.video.fps")}</label>
        <div className="settings-row-control">
          <div className="settings-chip-row">
            {(useEntitledStreamOptions
              ? dynamicFpsOptions.map((value) => ({ value }))
              : STATIC_FPS_PRESETS
            ).map((preset) => (
              <button
                key={preset.value}
                className={`settings-chip ${settings.fps === preset.value ? "active" : ""}`}
                aria-pressed={settings.fps === preset.value}
                onClick={() => {
                  handleChange("fps", preset.value);
                }}
              >
                <span>{preset.value}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="settings-row">
        <label className="settings-label">{t("settings.video.codec")}</label>
        <div className="settings-row-control">
          <div className="settings-chip-row">
            {codecOptions.map((codec) => {
              const badgeState = getCodecDecodeBadgeState(codec, codecResults, codecTesting);
              return (
                <button
                  key={codec}
                  className={`settings-chip settings-chip--codec ${settings.codec === codec ? "active" : ""}`}
                  aria-pressed={settings.codec === codec}
                  onClick={() => handleCodecChange(codec)}
                >
                  <span>{codec}</span>
                  {badgeState && (
                    <span className={`settings-inline-badge settings-inline-badge--codec settings-inline-badge--codec-${badgeState}`}>
                      {badgeState === "gpu"
                        ? t("settings.video.gpu")
                        : badgeState === "cpu"
                          ? t("settings.video.cpu")
                          : t("settings.video.testing")}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="settings-row">
        <label className="settings-label">{t("settings.video.colorDepth")}</label>
        <div className="settings-row-control">
          <div className="settings-chip-row">
            {colorQualityOptions.map((option) => {
              const needsHevc = colorQualityRequiresHevc(option.value);
              const colorDescription = option.value === "8bit_420"
                ? t("settings.colorQuality.mostCompatible")
                : option.value === "8bit_444"
                  ? t("settings.colorQuality.sharperChroma")
                  : option.value === "10bit_420"
                    ? t("settings.colorQuality.higherBitDepth")
                    : t("settings.colorQuality.highestChromaAndBitDepth");
              return (
                <button
                  key={option.value}
                  className={`settings-chip ${settings.colorQuality === option.value ? "active" : ""}`}
                  aria-pressed={settings.colorQuality === option.value}
                  onClick={() => handleColorQualityChange(option.value)}
                  title={needsHevc
                    ? t("settings.colorQuality.requiresH265OrAv1Title", {
                        description: colorDescription,
                      })
                    : colorDescription}
                >
                  <span>{option.label}</span>
                </button>
              );
            })}
          </div>
          {colorQualityRequiresHevc(settings.colorQuality) && settings.codec === "H264" && (
            <span className="settings-input-hint">
              {t("settings.video.requiresH265OrAv1")}
            </span>
          )}
        </div>
      </div>

      <div className="settings-row settings-row--column">
        <div className="settings-row-top">
          <label className="settings-label" htmlFor="settings-stream-max-bitrate">
            {t("settings.video.maxBitrate")}
          </label>
          <span className="settings-value-badge">{settings.maxBitrateMbps} Mbps</span>
        </div>
        <SettingRange
          id="settings-stream-max-bitrate"
          className="settings-slider"
          min={5}
          max={150}
          step={5}
          value={settings.maxBitrateMbps}
          onPreview={(value) => handlePreview("maxBitrateMbps", value)}
          onCommit={(value) => handleChange("maxBitrateMbps", value)}
        />
      </div>

      <div className="settings-row settings-row--column">
        <div className="settings-row-top">
          <label className="settings-label" htmlFor="settings-stream-recording-bitrate">
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
              handleChange("recordingBitrateMbps", settings.recordingBitrateMbps ?? 75);
            }}
          >
            <span>{t("settings.video.customBitrate")}</span>
          </button>
        </div>
        <SettingRange
          id="settings-stream-recording-bitrate"
          className="settings-slider"
          min={5}
          max={200}
          step={5}
          value={settings.recordingBitrateMbps ?? 75}
          disabled={settings.recordingBitrateMbps === null}
          onPreview={(value) => handlePreview("recordingBitrateMbps", value)}
          onCommit={(value) => handleChange("recordingBitrateMbps", value)}
        />
        <span className="settings-subtle-hint">
          {t("settings.video.recordingBitrateHint")}
        </span>
      </div>
    </>
  );
}
