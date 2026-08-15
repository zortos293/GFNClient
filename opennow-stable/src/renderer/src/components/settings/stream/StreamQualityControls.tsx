import { useCallback, useEffect, useMemo, type JSX } from "react";
import type {
  CodecPreference,
  ColorQuality,
  EntitledResolution,
  FallbackCodecPreference,
  Settings,
} from "@shared/gfn";
import {
  CODEC_PREFERENCE_OPTIONS,
  colorQualityRequiresHevc,
  expandEntitledStreamResolutions,
  FALLBACK_CODEC_PREFERENCE_OPTIONS,
  getSafeFallbackEntitledResolutions,
  resolveEntitledStreamProfile,
} from "@shared/gfn";
import {
  getCodecDecodeBadgeState,
  isCodecUsableForStream,
  resolveEffectiveCodec,
  type CodecTestResult,
} from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import { MotionSpinner } from "../../MotionSpinner";
import {
  SelectDropdown,
  type SelectDropdownOption,
} from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
import {
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
    const baseResolutions =
      entitledResolutions.length > 0
      ? entitledResolutions
      : subscriptionInfoLoaded
        ? getSafeFallbackEntitledResolutions()
        : [];
    return expandEntitledStreamResolutions(baseResolutions);
  }, [entitledResolutions, subscriptionInfoLoaded]);
  const useEntitledStreamOptions = effectiveEntitledResolutions.length > 0;
  const resolutionGroups = useMemo(
    () =>
      useEntitledStreamOptions
        ? groupResolutions(effectiveEntitledResolutions)
        : [],
    [effectiveEntitledResolutions, useEntitledStreamOptions],
  );
  const dynamicFpsOptions = useMemo(
    () =>
      useEntitledStreamOptions
      ? getFpsForResolution(effectiveEntitledResolutions, settings.resolution)
      : [],
    [
      effectiveEntitledResolutions,
      settings.resolution,
      useEntitledStreamOptions,
    ],
  );
  const resolvedEntitledProfile = useMemo(
    () =>
      resolveEntitledStreamProfile(effectiveEntitledResolutions, {
      resolution: settings.resolution,
      fps: settings.fps,
    }),
    [effectiveEntitledResolutions, settings.fps, settings.resolution],
  );
  const resolutionOptions = useMemo<SelectDropdownOption[]>(
    () =>
      useEntitledStreamOptions
        ? resolutionGroups.flatMap((group) =>
            group.resolutions.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
          group: group.category,
            })),
          )
      : STATIC_RESOLUTION_PRESETS.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
        })),
    [resolutionGroups, useEntitledStreamOptions],
  );

  const handleResolutionChange = useCallback(
    (resolution: string): void => {
    handleChange("resolution", resolution);
    const aspectRatio = inferAspectRatioFromResolution(resolution);
    if (settings.aspectRatio !== aspectRatio) {
      handleChange("aspectRatio", aspectRatio);
    }
    },
    [handleChange, settings.aspectRatio],
  );

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

  const handleColorQualityChange = useCallback(
    (colorQuality: ColorQuality): void => {
    if (colorQualityRequiresHevc(colorQuality) && settings.codec === "H264") {
      handleChange("codec", "H265");
    }
    handleChange("colorQuality", colorQuality);
    },
    [handleChange, settings.codec],
  );

  const handleCodecChange = useCallback(
    (codec: CodecPreference): void => {
    handleChange("codec", codec);
    if (codec === "H264" && settings.colorQuality !== "8bit_420") {
      handleChange("colorQuality", "8bit_420");
    }
    },
    [handleChange, settings.colorQuality],
  );

  const autoCodec = useMemo(
    () => resolveEffectiveCodec("auto", codecResults),
    [codecResults],
  );
  const codecOptions = useMemo<SelectDropdownOption[]>(() => {
    const options = CODEC_PREFERENCE_OPTIONS.map(
      (preference): SelectDropdownOption => {
        if (preference === "auto") {
          return {
            value: preference,
            label: t("settings.video.codecAutoPick", { codec: autoCodec }),
          };
        }

        const badge = getCodecDecodeBadgeState(
          preference,
          codecResults,
          codecTesting,
        );
        const usable =
          !codecResults || isCodecUsableForStream(preference, codecResults);
        const label =
          badge === "gpu"
          ? `${preference} · ${t("settings.video.gpu")}`
          : badge === "cpu"
            ? `${preference} · ${t("settings.video.cpu")}`
            : preference;
        return {
          value: preference,
          label,
          disabled: !usable,
          group: usable ? undefined : t("settings.video.codecUnsupported"),
        };
      },
    );
      return [
        ...options.filter((option) => !option.disabled),
        ...options.filter((option) => option.disabled),
      ];
  }, [autoCodec, codecResults, codecTesting, t]);
  const fallbackCodecOptions = useMemo<SelectDropdownOption[]>(() => {
    const options = FALLBACK_CODEC_PREFERENCE_OPTIONS.map(
      (preference): SelectDropdownOption => {
        if (preference === "auto") {
          return { value: preference, label: t("app.labels.auto") };
        }
        const usable =
          !codecResults || isCodecUsableForStream(preference, codecResults);
        return {
          value: preference,
          label: preference,
          disabled: !usable,
          group: usable ? undefined : t("settings.video.codecUnsupported"),
        };
      },
    );
      return [
        ...options.filter((option) => !option.disabled),
        ...options.filter((option) => option.disabled),
      ];
  }, [codecResults, t]);

  return (
    <>
      <div className="settings-group">
        <div className="settings-group-header">
          <h3>{t("settings.video.profile")}</h3>
          <p>{t("settings.video.profileHint")}</p>
        </div>
        <div className="settings-group-rows">
      <div className="settings-row settings-row--simple">
            <label
              className="settings-label"
              htmlFor="settings-stream-resolution"
            >
          <span className="settings-label-title">
            {t("settings.video.resolution")}
                {subscriptionLoading && (
                  <MotionSpinner size={12} className="settings-loading-icon" />
                )}
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

      <div className="settings-row settings-row--simple">
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

          <div className="settings-row settings-row--range">
            <div className="settings-row-top">
              <label
                className="settings-label"
                htmlFor="settings-stream-max-bitrate"
              >
                {t("settings.video.maxBitrate")}
              </label>
              <span className="settings-value-badge">
                {settings.maxBitrateMbps} Mbps
              </span>
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
        </div>
      </div>

      <div className="settings-group">
        <div className="settings-group-header">
          <h3>{t("settings.video.codecAndColor")}</h3>
          <p>{t("settings.video.codecAndColorHint")}</p>
        </div>
        <div className="settings-group-rows">
      <div className="settings-row settings-row--simple">
        <label className="settings-label" htmlFor="settings-stream-codec">
          {t("settings.video.codec")}
        </label>
        <div className="settings-row-control">
          <SelectDropdown
            id="settings-stream-codec"
            value={settings.codec}
            options={codecOptions}
                onChange={(value) =>
                  handleCodecChange(value as CodecPreference)
                }
            ariaLabel={t("settings.video.codec")}
            menuClassName="select-dropdown__menu--grouped"
          />
          <span className="settings-subtle-hint">
            {settings.codec === "auto"
              ? t("settings.video.codecAutoHint")
              : t("settings.video.codecManualHint")}
          </span>
        </div>
      </div>

      <div className="settings-row settings-row--simple">
            <label
              className="settings-label"
              htmlFor="settings-stream-fallback-codec"
            >
          {t("settings.video.fallbackCodec")}
        </label>
        <div className="settings-row-control">
          <SelectDropdown
            id="settings-stream-fallback-codec"
            value={settings.fallbackCodec}
            options={fallbackCodecOptions}
                onChange={(value) =>
                  handleChange(
                    "fallbackCodec",
                    value as FallbackCodecPreference,
                  )
                }
            ariaLabel={t("settings.video.fallbackCodec")}
            menuClassName="select-dropdown__menu--grouped"
          />
          <span className="settings-subtle-hint">
            {t("settings.video.fallbackCodecHint")}
          </span>
        </div>
      </div>

      <div className="settings-row settings-row--simple">
            <label className="settings-label">
              {t("settings.video.colorDepth")}
            </label>
        <div className="settings-row-control">
          <div className="settings-chip-row">
            {colorQualityOptions.map((option) => {
              const needsHevc = colorQualityRequiresHevc(option.value);
                  const colorDescription =
                    option.value === "8bit_420"
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
                      title={
                        needsHevc
                    ? t("settings.colorQuality.requiresH265OrAv1Title", {
                        description: colorDescription,
                      })
                          : colorDescription
                      }
                >
                  <span>{option.label}</span>
                </button>
              );
            })}
          </div>
              {colorQualityRequiresHevc(settings.colorQuality) &&
                settings.codec === "H264" && (
            <span className="settings-input-hint">
              {t("settings.video.requiresH265OrAv1")}
            </span>
          )}
        </div>
      </div>
        </div>
      </div>
    </>
  );
}
