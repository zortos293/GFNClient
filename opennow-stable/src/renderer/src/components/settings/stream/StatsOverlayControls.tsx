import { type JSX } from "react";
import type { Settings, StatsOverlayPosition } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
import { SettingRow, SettingToggleRow } from "../SettingRow";
import type { SettingsChangeHandler } from "./streamSettingsTypes";

interface StatsOverlayControlsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
  handlePreview: SettingsChangeHandler;
}

export function StatsOverlayControls({
  settings,
  handleChange,
  handlePreview,
}: StatsOverlayControlsProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <>
      <SettingToggleRow htmlFor="settings-interface-show-stats-on-launch" label={t("settings.interface.showStatsOnStreamLaunch")} description={t("settings.interface.showStatsOnStreamLaunchHint")} checked={settings.showStatsOnLaunch} onChange={(checked) => handleChange("showStatsOnLaunch", checked)} />

      <div className="settings-row">
        <label
          className="settings-label"
          htmlFor="settings-interface-stats-position"
        >
          {t("settings.interface.statsOverlayPosition")}
          <span className="settings-hint">
            {t("settings.interface.statsOverlayPositionHint")}
          </span>
        </label>
        <div className="settings-row-control">
          <SelectDropdown
            id="settings-interface-stats-position"
            value={settings.statsOverlayPosition}
            options={[
              {
                value: "bottom-left",
                label: t("settings.interface.posBottomLeft"),
              },
              {
                value: "bottom-right",
                label: t("settings.interface.posBottomRight"),
              },
              { value: "top-left", label: t("settings.interface.posTopLeft") },
              {
                value: "top-right",
                label: t("settings.interface.posTopRight"),
              },
            ]}
            onChange={(value) =>
              handleChange(
                "statsOverlayPosition",
                value as StatsOverlayPosition,
              )
            }
          />
        </div>
      </div>

      <SettingToggleRow htmlFor="settings-interface-show-session-time-remaining" label={t("settings.interface.showSessionTimeRemainingInStatsOverlay")} description={t("settings.interface.showSessionTimeRemainingInStatsOverlayHint")} checked={settings.showSessionTimeRemainingInStatsOverlay} onChange={(checked) => handleChange("showSessionTimeRemainingInStatsOverlay", checked)} />
      <SettingToggleRow htmlFor="settings-interface-session-counter" label={t("settings.interface.sessionElapsedCounter")} description={t("settings.interface.sessionElapsedCounterHint")} checked={settings.sessionCounterEnabled} onChange={(checked) => handleChange("sessionCounterEnabled", checked)} />

      {settings.sessionCounterEnabled && (
        <>
          <SettingRow htmlFor="settings-interface-session-timer-reappear" label={t("settings.interface.sessionTimerReappear")} description={t("settings.interface.sessionTimerReappearHint")}>
              <span className="settings-value-badge settings-value-badge--control">
                {settings.sessionClockShowEveryMinutes === 0
                  ? t("settings.interface.off")
                  : t("settings.interface.everyMinutes", {
                      count: settings.sessionClockShowEveryMinutes,
                    })}
              </span>
            <SettingRange
              id="settings-interface-session-timer-reappear"
              className="settings-slider"
              min={0}
              max={120}
              step={5}
              value={settings.sessionClockShowEveryMinutes}
              onPreview={(value) =>
                handlePreview("sessionClockShowEveryMinutes", value)
              }
              onCommit={(value) =>
                handleChange("sessionClockShowEveryMinutes", value)
              }
            />
          </SettingRow>

          <SettingRow htmlFor="settings-interface-session-timer-visible-time" label={t("settings.interface.sessionTimerVisibleTime")} description={t("settings.interface.sessionTimerVisibleTimeHint")}>
              <span className="settings-value-badge settings-value-badge--control">
                {t("app.units.seconds", {
                  value: settings.sessionClockShowDurationSeconds,
                })}
              </span>
            <SettingRange
              id="settings-interface-session-timer-visible-time"
              className="settings-slider"
              min={5}
              max={120}
              step={5}
              value={settings.sessionClockShowDurationSeconds}
              onPreview={(value) =>
                handlePreview("sessionClockShowDurationSeconds", value)
              }
              onCommit={(value) =>
                handleChange("sessionClockShowDurationSeconds", value)
              }
            />
          </SettingRow>
        </>
      )}
    </>
  );
}
