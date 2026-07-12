import { Check } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { gameLanguageOptions } from "../settingsFormatters";

export interface SettingsGameSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsGameSection({ settings, showAll, handleChange }: SettingsGameSectionProps): JSX.Element {
  const { t } = useTranslation();
  const [gameLanguageDropdownOpen, setGameLanguageDropdownOpen] = useState(false);
  const gameLanguageDropdownRef = useRef<HTMLDivElement | null>(null);

  const selectedGameLanguageName = useMemo(() => {
    return gameLanguageOptions.find((option) => option.value === settings.gameLanguage)?.label ?? "English (US)";
  }, [settings.gameLanguage]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent): void => {
      const target = event.target as Node;
      if (gameLanguageDropdownRef.current && !gameLanguageDropdownRef.current.contains(target)) {
        setGameLanguageDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.game")}</div>}
      <div className="settings-section-header">
        <h2>{t("settings.game.title")}</h2>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label">
            {t("settings.game.language")}
            <span className="settings-hint">{t("settings.game.inGameLanguageHint")}</span>
          </label>
          <div className="settings-dropdown" ref={gameLanguageDropdownRef}>
            <button
              type="button"
              className={`settings-dropdown-selected ${gameLanguageDropdownOpen ? "open" : ""}`}
              onClick={() => setGameLanguageDropdownOpen((open) => !open)}
            >
              <span className="settings-dropdown-selected-name">{selectedGameLanguageName}</span>
              <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`settings-dropdown-chevron ${gameLanguageDropdownOpen ? "flipped" : ""}`}>
                <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
              </svg>
            </button>
            {gameLanguageDropdownOpen && (
              <div className="settings-dropdown-menu settings-dropdown-menu--tall">
                {gameLanguageOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={`settings-dropdown-item ${settings.gameLanguage === option.value ? "active" : ""}`}
                    onClick={() => {
                      handleChange("gameLanguage", option.value);
                      setGameLanguageDropdownOpen(false);
                    }}
                  >
                    <span>{option.label}</span>
                    {settings.gameLanguage === option.value && <Check size={14} className="settings-dropdown-check" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.game.persistInGameSettings")}
              </span>
            </label>
            <label className="settings-toggle">
              <input
                type="checkbox"
                checked={settings.enablePersistingInGameSettings}
                onChange={(e) => handleChange("enablePersistingInGameSettings", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">
            {t("settings.game.persistInGameSettingsHint")}
          </span>
        </div>
      </div>
    </section>
  );
}
