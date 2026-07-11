import { Search, X, Users, Wifi, Cpu, Globe, Mic, Keyboard, Monitor, Info, Heart } from "lucide-react";
import { useMemo, type JSX } from "react";
import { useTranslation } from "../../i18n";
import type { SettingsNavGroup, SettingsSectionId } from "./settingsTypes";

export interface SettingsNavProps {
  activeSection: SettingsSectionId;
  settingsSearch: string;
  showAll: boolean;
  onSearchChange: (value: string) => void;
  onSectionChange: (section: SettingsSectionId) => void;
}

export function SettingsNav({
  activeSection,
  settingsSearch,
  showAll,
  onSearchChange,
  onSectionChange,
}: SettingsNavProps): JSX.Element {
  const { locale, t } = useTranslation();

  const settingsNavGroups = useMemo<SettingsNavGroup[]>(() => [
    {
      label: "Account",
      items: [
        { id: "account", label: t("settings.sections.account"), icon: <Users size={15} /> },
      ],
    },
    {
      label: "Streaming",
      items: [
        { id: "stream", label: t("settings.sections.stream"), icon: <Wifi size={15} /> },
        { id: "native-streamer", label: t("settings.sections.nativeStreamer"), icon: <Cpu size={15} /> },
      ],
    },
    {
      label: "Controls",
      items: [
        { id: "game", label: t("settings.sections.game"), icon: <Globe size={15} /> },
        { id: "audio", label: t("settings.sections.audio"), icon: <Mic size={15} /> },
        { id: "input", label: t("settings.sections.input"), icon: <Keyboard size={15} /> },
      ],
    },
    {
      label: "App",
      items: [
        { id: "interface", label: t("settings.sections.interface"), icon: <Monitor size={15} /> },
        { id: "about", label: t("settings.sections.about"), icon: <Info size={15} /> },
        { id: "thanks", label: t("settings.sections.thanks"), icon: <Heart size={15} /> },
      ],
    },
  ], [t, locale]);

  return (
    <nav className="settings-sidebar">
      <div className="settings-search-wrap">
        <Search size={13} className="settings-search-icon" />
        <input
          type="text"
          className="settings-search-input"
          placeholder={t("settings.searchPlaceholder")}
          value={settingsSearch}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        {settingsSearch && (
          <button type="button" className="settings-search-clear" onClick={() => onSearchChange("")}>
            <X size={11} />
          </button>
        )}
      </div>
      <div className="settings-nav">
        {settingsNavGroups.map((group) => (
          <div key={group.label} className="settings-nav-group">
            <div className="settings-nav-group-label">{group.label}</div>
            {group.items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`settings-nav-item ${!showAll && activeSection === item.id ? "active" : ""}`}
                onClick={() => { onSectionChange(item.id); onSearchChange(""); }}
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </div>
        ))}
      </div>
    </nav>
  );
}
