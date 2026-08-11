import type { JSX } from "react";
import type { GameLanguage, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { SettingToggleRow } from "../SettingRow";
import { gameLanguageOptions } from "../settingsFormatters";

export interface SettingsGameSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsGameSection({ settings, showAll, handleChange }: SettingsGameSectionProps): JSX.Element {
  const { t } = useTranslation();
  const gameLanguageId = "settings-game-language";
  const persistSettingsId = "settings-game-persist-in-game-settings";

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.game")}</div>}
      <div className="settings-section-header settings-section-header--with-copy">
        <div>
          <h2>{t("settings.game.title")}</h2>
          <p className="settings-section-description">{t("settings.game.description")}</p>
        </div>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label" htmlFor={gameLanguageId}>
            {t("settings.game.language")}
            <span className="settings-hint">{t("settings.game.inGameLanguageHint")}</span>
          </label>
          <div className="settings-row-control">
            <SelectDropdown
              id={gameLanguageId}
              value={settings.gameLanguage}
              options={gameLanguageOptions}
              onChange={(value) => handleChange("gameLanguage", value as GameLanguage)}
              menuClassName="select-dropdown__menu--tall"
            />
          </div>
        </div>
        <SettingToggleRow
          htmlFor={persistSettingsId}
          label={t("settings.game.persistInGameSettings")}
          description={t("settings.game.persistInGameSettingsHint")}
          checked={settings.enablePersistingInGameSettings}
          onChange={(checked) => handleChange("enablePersistingInGameSettings", checked)}
        />
      </div>
    </section>
  );
}
