import type { JSX } from "react";
import type { Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SettingToggleRow } from "../SettingRow";

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
        <SettingToggleRow
          htmlFor="settings-console-controller-mode"
          label={t("settings.console.controllerMode")}
          description={t("settings.console.controllerModeHint")}
          checked={settings.controllerMode}
          onChange={(checked) => handleChange("controllerMode", checked)}
        />
        <SettingToggleRow
          htmlFor="settings-console-profile-picker"
          label={t("settings.console.profilePicker")}
          description={t("settings.console.profilePickerHint")}
          checked={settings.consoleProfilePickerOnLaunch}
          onChange={(checked) => handleChange("consoleProfilePickerOnLaunch", checked)}
        />
        <SettingToggleRow
          htmlFor="settings-console-launch-mode"
          label={t("settings.console.launchMode")}
          description={t("settings.console.launchModeHint")}
          checked={settings.launchInConsoleMode}
          onChange={(checked) => handleChange("launchInConsoleMode", checked)}
        />
      </div>
    </section>
  );
}
