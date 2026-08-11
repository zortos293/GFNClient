import { useCallback, useMemo, type JSX } from "react";
import type { AppAccentColor, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { SettingRange } from "../SettingRange";
import { SettingRow, SettingToggleRow } from "../SettingRow";
import {
  accentColorOptions,
  getAppLanguageLabel,
  POSTER_SIZE_MAX,
  POSTER_SIZE_MIN,
  POSTER_SIZE_STEP,
} from "../settingsFormatters";

export interface SettingsInterfaceSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  handlePreview: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  onSaved: () => void;
}

export function SettingsInterfaceSection({ settings, showAll, handleChange, handlePreview, onSaved }: SettingsInterfaceSectionProps): JSX.Element {
  const { locale, availableLocales, setLocale, t } = useTranslation();
  const posterSizePercent = Math.round(settings.posterSizeScale * 100);

  const appLanguageOptions = useMemo(
    () => availableLocales.map((value) => ({ value, label: getAppLanguageLabel(value) })),
    [availableLocales],
  );

  const accentDropdownOptions = useMemo(
    () => accentColorOptions.map((option) => ({
      value: option.value,
      label: (
        <span className="settings-accent-option">
          <span
            className="settings-accent-swatch"
            aria-hidden="true"
            style={{
              background: option.hex,
              boxShadow: `0 0 0 1px color-mix(in srgb, ${option.hex} 48%, rgba(255, 255, 255, 0.22))`,
            }}
          />
          <span>{t(option.labelKey)}</span>
        </span>
      ),
    })),
    [locale, t],
  );

  const handleAppLanguageChange = useCallback((nextLocale: string): void => {
    void setLocale(nextLocale)
      .then(onSaved)
      .catch((error) => {
        console.warn("[Settings] Failed to change app language:", error);
      });
  }, [onSaved, setLocale]);

  return (
    <>
      {/* ── Appearance ── */}
      <section className="settings-section">
        {showAll && <div className="settings-section-context">{t("settings.sections.interface")}</div>}
        <div className="settings-section-header settings-section-header--with-copy">
          <div>
            <h2>{t("settings.interface.appearance")}</h2>
            <p className="settings-section-description">{t("settings.interface.description")}</p>
          </div>
        </div>
        <div className="settings-rows">
          <div className="settings-row">
            <label className="settings-label" htmlFor="settings-interface-app-language">
              {t("settings.interface.appLanguage")}
              <span className="settings-hint">{t("settings.interface.appLanguageHint")}</span>
            </label>
            <div className="settings-row-control">
              <SelectDropdown
                id="settings-interface-app-language"
                value={locale}
                options={appLanguageOptions}
                onChange={handleAppLanguageChange}
              />
            </div>
          </div>

          <div className="settings-row">
            <label className="settings-label" htmlFor="appTheme">
              {t("settings.interface.theme") || "Theme"}
              <span className="settings-hint">{t("settings.interface.themeHint") || "Choose a light, dark, or system-matching theme."}</span>
            </label>
            <div className="settings-row-control">
              <SelectDropdown
                id="appTheme"
                value={settings.appTheme}
                options={[
                  { value: "auto", label: t("settings.interface.themeAuto") || "Auto" },
                  { value: "light", label: t("settings.interface.themeLight") || "Light" },
                  { value: "dark", label: t("settings.interface.themeDark") || "Dark" },
                ]}
                onChange={(value) => handleChange("appTheme", value as any)}
                ariaLabel={t("settings.interface.theme") || "Theme"}
              />
            </div>
          </div>

          <SettingToggleRow
            htmlFor="settings-interface-translucent-ui"
            label={t("settings.interface.translucentUI") || "Translucent UI"}
            description={t("settings.interface.translucentUIHint") || "Enable glassmorphism and translucent overlays."}
            checked={settings.translucentUI}
            onChange={(checked) => handleChange("translucentUI", checked)}
          />

          <div className="settings-row">
            <label className="settings-label" htmlFor="settings-interface-accent-color">
              {t("settings.interface.accentColor")}
              <span className="settings-hint">{t("settings.interface.accentColorHint")}</span>
            </label>
            <div className="settings-row-control">
              <SelectDropdown
                id="settings-interface-accent-color"
                value={settings.appAccentColor}
                options={accentDropdownOptions}
                onChange={(value) => handleChange("appAccentColor", value as AppAccentColor)}
              />
            </div>
          </div>

          <SettingToggleRow htmlFor="settings-interface-hide-stream-buttons" label={t("settings.interface.hideStreamOverlayButtons")} description={t("settings.interface.hideStreamOverlayButtonsHint")} checked={settings.hideStreamButtons} onChange={(checked) => handleChange("hideStreamButtons", checked)} />
          <SettingToggleRow htmlFor="settings-interface-hide-server-selector" label={t("settings.interface.hideServerSelector")} description={t("settings.interface.hideServerSelectorHint")} checked={settings.hideServerSelector} onChange={(checked) => handleChange("hideServerSelector", checked)} />
          <SettingToggleRow htmlFor="settings-interface-show-anti-afk-indicator" label={t("settings.interface.showAntiAfkIndicator")} description={t("settings.interface.showAntiAfkIndicatorHint")} checked={settings.showAntiAfkIndicator} onChange={(checked) => handleChange("showAntiAfkIndicator", checked)} />

          <SettingRow htmlFor="settings-interface-anti-afk-reminder-interval" label={t("settings.interface.antiAfkReminderInterval")} description={t("settings.interface.antiAfkReminderIntervalHint")}>
              <span className="settings-value-badge settings-value-badge--control">
                {settings.antiAfkReminderEveryMinutes === 0
                  ? t("settings.interface.off")
                  : t("settings.interface.everyMinutes", { count: settings.antiAfkReminderEveryMinutes })}
              </span>
              <SettingRange
              id="settings-interface-anti-afk-reminder-interval"
              className="settings-slider"
              min={0}
              max={120}
              step={5}
              value={settings.antiAfkReminderEveryMinutes}
              onPreview={(value) => handlePreview("antiAfkReminderEveryMinutes", value)}
              onCommit={(value) => handleChange("antiAfkReminderEveryMinutes", value)}
            />
          </SettingRow>

          {settings.antiAfkReminderEveryMinutes > 0 && (
            <SettingRow htmlFor="settings-interface-anti-afk-reminder-duration" label={t("settings.interface.antiAfkReminderDuration")} description={t("settings.interface.antiAfkReminderDurationHint")}>
                <span className="settings-value-badge settings-value-badge--control">
                  {t("app.units.seconds", { value: settings.antiAfkReminderDurationSeconds })}
                </span>
                <SettingRange
                id="settings-interface-anti-afk-reminder-duration"
                className="settings-slider"
                min={5}
                max={120}
                step={5}
                value={settings.antiAfkReminderDurationSeconds}
                onPreview={(value) => handlePreview("antiAfkReminderDurationSeconds", value)}
                onCommit={(value) => handleChange("antiAfkReminderDurationSeconds", value)}
              />
            </SettingRow>
          )}

          <SettingToggleRow htmlFor="settings-interface-auto-fullscreen" label={t("settings.interface.autoFullScreen")} description={t("settings.interface.autoFullScreenHint")} checked={settings.autoFullScreen} onChange={(checked) => handleChange("autoFullScreen", checked)} />
          <SettingToggleRow htmlFor="settings-interface-escape-exits-fullscreen" label={t("settings.interface.escapeExitsFullscreen")} description={t("settings.interface.escapeExitsFullscreenHint")} checked={Boolean(settings.allowEscapeToExitFullscreen)} onChange={(checked) => handleChange("allowEscapeToExitFullscreen", checked)} />
          <SettingToggleRow htmlFor="settings-interface-discord-rich-presence" label={t("settings.interface.discordRichPresence")} description={t("settings.interface.discordRichPresenceHint")} checked={settings.discordRichPresence} onChange={(checked) => handleChange("discordRichPresence", checked)} />

          <SettingRow htmlFor="settings-interface-poster-size" label={t("settings.interface.posterSize")} description={t("settings.interface.posterSizeHint")}>
            <span className="settings-value-badge settings-value-badge--control">{posterSizePercent}%</span>
            <SettingRange
              id="settings-interface-poster-size"
              className="settings-slider"
              min={POSTER_SIZE_MIN}
              max={POSTER_SIZE_MAX}
              step={POSTER_SIZE_STEP}
              value={posterSizePercent}
              normalize={(value) => value / 100}
              onPreview={(value) => handlePreview("posterSizeScale", value)}
              onCommit={(value) => handleChange("posterSizeScale", value)}
            />
          </SettingRow>

        </div>
      </section>

    </>
  );
}
