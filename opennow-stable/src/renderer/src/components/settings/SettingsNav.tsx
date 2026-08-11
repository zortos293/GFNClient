import { Search, X } from "lucide-react";
import { type JSX } from "react";
import { useTranslation } from "../../i18n";
import type { SettingsNavItem, SettingsSectionId } from "./settingsTypes";

export interface SettingsNavProps {
  activeSection: SettingsSectionId;
  settingsSearch: string;
  showAll: boolean;
  items: readonly SettingsNavItem[];
  onSearchChange: (value: string) => void;
  onSectionChange: (section: SettingsSectionId) => void;
}

export function SettingsNav({
  activeSection,
  settingsSearch,
  showAll,
  items,
  onSearchChange,
  onSectionChange,
}: SettingsNavProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <nav className="settings-sidebar">
      <div className="settings-search-wrap">
        <Search size={13} className="settings-search-icon" />
        <input
          type="text"
          className="settings-search-input"
          placeholder={t("settings.searchPlaceholder")}
          aria-label={t("settings.searchPlaceholder")}
          value={settingsSearch}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        {settingsSearch && (
          <button
            type="button"
            className="settings-search-clear"
            onClick={() => onSearchChange("")}
            aria-label={t("settings.clearSearch")}
          >
            <X size={11} />
          </button>
        )}
      </div>
      <div className="settings-nav">
        {items.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`settings-nav-item ${!showAll && activeSection === item.id ? "active" : ""}`}
            onClick={() => { onSectionChange(item.id); onSearchChange(""); }}
            aria-current={!showAll && activeSection === item.id ? "page" : undefined}
          >
            {item.icon}
            <span className="settings-nav-item-label">{item.label}</span>
          </button>
        ))}
      </div>
    </nav>
  );
}
