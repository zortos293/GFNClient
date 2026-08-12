import { type JSX } from "react";
import type { Settings, StatsOverlayPosition } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
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
      <div className="settings-row settings-row--toggle">
        <div className="settings-row-top settings-row-top--compact">
          <label
            className="settings-label settings-label--wrap"
            htmlFor="settings-interface-show-stats-on-launch"
          >
            <span className="settings-label-title">
              {t("settings.interface.showStatsOnStreamLaunch")}
            </span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-interface-show-stats-on-launch"
              type="checkbox"
              checked={settings.showStatsOnLaunch}
              onChange={(event) =>
                handleChange("showStatsOnLaunch", event.target.checked)
              }
            />
            <span className="settings-toggle-track" />
          </label>
        </div>
        <span className="settings-subtle-hint">
          {t("settings.interface.showStatsOnStreamLaunchHint")}
        </span>
      </div>

      <div className="settings-row settings-row--simple">
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

      <div className="settings-row settings-row--toggle">
        <div className="settings-row-top settings-row-top--compact">
          <label
            className="settings-label settings-label--wrap"
            htmlFor="settings-interface-show-session-time-remaining"
          >
            <span className="settings-label-title">
              {t("settings.interface.showSessionTimeRemainingInStatsOverlay")}
            </span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-interface-show-session-time-remaining"
              type="checkbox"
              checked={settings.showSessionTimeRemainingInStatsOverlay}
              onChange={(event) =>
                handleChange(
                  "showSessionTimeRemainingInStatsOverlay",
                  event.target.checked,
                )
              }
            />
            <span className="settings-toggle-track" />
          </label>
        </div>
        <span className="settings-subtle-hint">
          {t("settings.interface.showSessionTimeRemainingInStatsOverlayHint")}
        </span>
      </div>

      <div className="settings-row settings-row--toggle">
        <div className="settings-row-top settings-row-top--compact">
          <label
            className="settings-label settings-label--wrap"
            htmlFor="settings-interface-session-counter"
          >
            <span className="settings-label-title">
              {t("settings.interface.sessionElapsedCounter")}
            </span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-interface-session-counter"
              type="checkbox"
              checked={settings.sessionCounterEnabled}
              onChange={(event) =>
                handleChange("sessionCounterEnabled", event.target.checked)
              }
            />
            <span className="settings-toggle-track" />
          </label>
        </div>
        <span className="settings-subtle-hint">
          {t("settings.interface.sessionElapsedCounterHint")}
        </span>
      </div>

      {settings.sessionCounterEnabled && (
        <>
          <div className="settings-row settings-row--toggle">
            <div className="settings-row-top">
              <label
                className="settings-label"
                htmlFor="settings-interface-session-timer-reappear"
              >
                {t("settings.interface.sessionTimerReappear")}
              </label>
              <span className="settings-value-badge">
                {settings.sessionClockShowEveryMinutes === 0
                  ? t("settings.interface.off")
                  : t("settings.interface.everyMinutes", {
                      count: settings.sessionClockShowEveryMinutes,
                    })}
              </span>
            </div>
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
            <span className="settings-subtle-hint">
              {t("settings.interface.sessionTimerReappearHint")}
            </span>
          </div>

          <div className="settings-row settings-row--toggle">
            <div className="settings-row-top">
              <label
                className="settings-label"
                htmlFor="settings-interface-session-timer-visible-time"
              >
                {t("settings.interface.sessionTimerVisibleTime")}
              </label>
              <span className="settings-value-badge">
                {t("app.units.seconds", {
                  value: settings.sessionClockShowDurationSeconds,
                })}
              </span>
            </div>
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
            <span className="settings-subtle-hint">
              {t("settings.interface.sessionTimerVisibleTimeHint")}
            </span>
          </div>
        </>
      )}
    </>
  );
}
