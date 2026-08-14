import { Activity, Users, Wifi, Cpu, Globe, Mic, Keyboard, Gamepad2, Monitor, Info, Heart } from "lucide-react";
import { useMemo, type JSX } from "react";
import { useTranslation } from "../../i18n";
import type { SettingsNavItem, SettingsSectionId } from "./settingsTypes";

export interface SettingsNavProps {
  activeSection: SettingsSectionId;
  showAll: boolean;
  onSectionChange: (section: SettingsSectionId) => void;
}

export function SettingsNav({
  activeSection,
  showAll,
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

  return (
    <nav className="settings-sidebar" aria-label={t("settings.title")}>
      <div className="settings-nav">
        {settingsNavItems.map((item) => {
          const isActive = !showAll && activeSection === item.id;
          const isExperimental = item.id === "native-streamer";
          const accessibleLabel = isExperimental
            ? `${item.label} · ${t("app.labels.experimental")}`
            : item.label;

          return (
            <button
              key={item.id}
              type="button"
              className={`settings-nav-item ${isActive ? "active" : ""}`}
              onClick={() => onSectionChange(item.id)}
              aria-label={accessibleLabel}
              aria-current={isActive ? "page" : undefined}
              title={accessibleLabel}
            >
              {item.icon}
              <span className="settings-nav-item-label">{item.label}</span>
              {isExperimental && <span className="settings-nav-badge" aria-hidden="true" />}
            </button>
          );
        })}
      </div>
    </nav>
  );
}
