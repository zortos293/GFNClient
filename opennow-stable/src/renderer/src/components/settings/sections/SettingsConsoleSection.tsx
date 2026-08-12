import { Gamepad2 } from "lucide-react";
import type { JSX } from "react";
import type { Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";

export interface SettingsConsoleSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsConsoleSection({ settings, showAll, handleChange }: SettingsConsoleSectionProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.console")}</div>}
      <div className="settings-section-header">
        <Gamepad2 />
        <h2>{t("settings.console.title")}</h2>
      </div>
      <div className="settings-rows settings-rows--grouped">
        <div className="settings-group">
          <div className="settings-group-header">
            <h3>{t("settings.console.appShell")}</h3>
            <p>{t("settings.console.appShellDescription")}</p>
          </div>
          <div className="settings-group-rows">
            <div className="settings-row settings-row--toggle">
              <div className="settings-row-top settings-row-top--compact">
                <label className="settings-label settings-label--wrap" htmlFor="settings-console-controller-mode">
                  <span className="settings-label-title">{t("settings.console.controllerMode")}</span>
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
              <span className="settings-subtle-hint">{t("settings.console.controllerModeHint")}</span>
            </div>

            <div className="settings-row settings-row--toggle">
              <div className="settings-row-top settings-row-top--compact">
                <label className="settings-label settings-label--wrap" htmlFor="settings-console-profile-picker">
                  <span className="settings-label-title">{t("settings.console.profilePicker")}</span>
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
              <span className="settings-subtle-hint">{t("settings.console.profilePickerHint")}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
