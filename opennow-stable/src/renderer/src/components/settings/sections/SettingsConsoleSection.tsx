import type { JSX } from "react";
import type { Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";

export interface SettingsConsoleSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsConsoleSection({
  settings,
  showAll,
  handleChange,
}: SettingsConsoleSectionProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.console")}</div>}
      <div className="settings-section-header settings-section-header--with-copy">
        <div>
          <h2>{t("settings.console.title")}</h2>
          <p className="settings-section-description">{t("settings.console.description")}</p>
        </div>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label" htmlFor="settings-console-controller-mode">
            {t("settings.console.controllerMode")}
            <span className="settings-hint">{t("settings.console.controllerModeHint")}</span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-console-controller-mode"
              type="checkbox"
              checked={settings.controllerMode}
              onChange={(event) => handleChange("controllerMode", event.target.checked)}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>

        <div className="settings-row">
          <label className="settings-label" htmlFor="settings-console-profile-picker">
            {t("settings.console.profilePicker")}
            <span className="settings-hint">{t("settings.console.profilePickerHint")}</span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-console-profile-picker"
              type="checkbox"
              checked={settings.consoleProfilePickerOnLaunch}
              onChange={(event) => handleChange("consoleProfilePickerOnLaunch", event.target.checked)}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>

        <div className="settings-row">
          <label className="settings-label" htmlFor="settings-console-launch-mode">
            {t("settings.console.launchMode")}
            <span className="settings-hint">{t("settings.console.launchModeHint")}</span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-console-launch-mode"
              type="checkbox"
              checked={settings.launchInConsoleMode}
              onChange={(event) => handleChange("launchInConsoleMode", event.target.checked)}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>
      </div>
    </section>
  );
}
