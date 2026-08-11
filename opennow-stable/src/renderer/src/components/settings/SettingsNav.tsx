import { Activity, Users, Wifi, Cpu, Globe, Mic, Keyboard, Gamepad2, Monitor, Info, Heart } from "lucide-react";
import { useMemo, type JSX, type KeyboardEvent } from "react";
import { useTranslation } from "../../i18n";
import type { SettingsNavItem, SettingsSectionId } from "./settingsTypes";

export interface SettingsNavProps {
  activeSection: SettingsSectionId;
  showAll: boolean;
  onSearchChange: (value: string) => void;
  onSectionChange: (section: SettingsSectionId) => void;
}

export function SettingsNav({
  activeSection,
  showAll,
  onSearchChange,
  onSectionChange,
}: SettingsNavProps): JSX.Element {
  const { locale, t } = useTranslation();

  const settingsNavItems = useMemo<SettingsNavItem[]>(() => [
    { id: "account", label: t("settings.sections.account"), icon: <Users /> },
    { id: "stream", label: t("settings.sections.stream"), icon: <Wifi /> },
    { id: "diagnostics", label: t("settings.sections.diagnostics"), icon: <Activity /> },
    { id: "native-streamer", label: t("settings.sections.nativeStreamer"), icon: <Cpu /> },
    { id: "game", label: t("settings.sections.game"), icon: <Globe /> },
    { id: "audio", label: t("settings.sections.audio"), icon: <Mic /> },
    { id: "input", label: t("settings.sections.input"), icon: <Keyboard /> },
    { id: "console", label: t("settings.sections.console"), icon: <Gamepad2 /> },
    { id: "interface", label: t("settings.sections.interface"), icon: <Monitor /> },
    { id: "about", label: t("settings.sections.about"), icon: <Info /> },
    { id: "thanks", label: t("settings.sections.thanks"), icon: <Heart /> },
  ], [t, locale]);

  const selectTab = (item: SettingsNavItem, button?: HTMLButtonElement): void => {
    onSectionChange(item.id);
    onSearchChange("");
    button?.scrollIntoView({ block: "nearest", inline: "nearest" });
  };

  const handleTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number): void => {
    let nextIndex: number | null = null;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % settingsNavItems.length;
    if (event.key === "ArrowLeft") nextIndex = (index - 1 + settingsNavItems.length) % settingsNavItems.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = settingsNavItems.length - 1;
    if (nextIndex === null) return;

    event.preventDefault();
    const nextButton = event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[role='tab']")[nextIndex];
    selectTab(settingsNavItems[nextIndex], nextButton);
    nextButton?.focus();
  };

  return (
    <nav className="settings-category-nav" aria-label={t("settings.categoryNavigation")}>
      <div className="settings-nav" role="tablist" aria-orientation="horizontal">
        {settingsNavItems.map((item, index) => (
          <button
            key={item.id}
            id={`settings-tab-${item.id}`}
            type="button"
            role="tab"
            className={`settings-nav-item ${!showAll && activeSection === item.id ? "active" : ""}`}
            aria-selected={!showAll && activeSection === item.id}
            aria-controls="settings-category-panel"
            tabIndex={activeSection === item.id ? 0 : -1}
            onClick={(event) => selectTab(item, event.currentTarget)}
            onKeyDown={(event) => handleTabKeyDown(event, index)}
          >
            {item.icon}
            <span className="settings-nav-item-label">{item.label}</span>
            {item.id === "native-streamer" && (
              <span className="settings-nav-badge">{t("app.labels.experimental")}</span>
            )}
          </button>
        ))}
      </div>
    </nav>
  );
}
