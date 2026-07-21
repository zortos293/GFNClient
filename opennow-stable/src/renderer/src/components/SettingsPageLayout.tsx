import { autoAnimate } from "@formkit/auto-animate";
import { Check, Cpu, Globe, Heart, Info, Keyboard, Mic, Monitor, Search, Wifi, X } from "lucide-react";
import { useEffect, useRef, type JSX, type ReactNode } from "react";

type SettingsSectionId = "stream" | "native-streamer" | "game" | "audio" | "input" | "interface" | "about" | "thanks";

interface SettingsPageLayoutProps {
  savedLabel: string;
  savedIndicator: boolean;
  searchPlaceholder: string;
  settingsSearch: string;
  onSettingsSearchChange: (value: string) => void;
  activeSection: SettingsSectionId;
  showAll: boolean;
  onSectionSelect: (section: SettingsSectionId) => void;
  navItems: ReadonlyArray<{ id: SettingsSectionId; label: string; icon: ReactNode }>;
  children: ReactNode;
}

export function SettingsPageLayout({
  savedLabel,
  savedIndicator,
  searchPlaceholder,
  settingsSearch,
  onSettingsSearchChange,
  activeSection,
  showAll,
  onSectionSelect,
  navItems,
  children,
}: SettingsPageLayoutProps): JSX.Element {
  const settingsContentRef = useRef<HTMLDivElement | null>(null);
  const settingsNavRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (settingsContentRef.current) {
      autoAnimate(settingsContentRef.current, { duration: 220, easing: "ease-out" });
    }
  }, []);

  useEffect(() => {
    if (settingsNavRef.current) {
      autoAnimate(settingsNavRef.current, { duration: 180, easing: "ease-out" });
    }
  }, []);

  return (
    <div className="settings-page settings-page--glass">
      <div className={`settings-saved settings-saved--floating ${savedIndicator ? "visible" : ""}`}>
        <Check size={14} />
        {savedLabel}
      </div>

      <div className="settings-layout">
        <nav className="settings-sidebar">
          <div className="settings-search-wrap">
            <Search size={13} className="settings-search-icon" />
            <input
              type="text"
              className="settings-search-input"
              placeholder={searchPlaceholder}
              value={settingsSearch}
              onChange={(event) => onSettingsSearchChange(event.target.value)}
            />
            {settingsSearch ? (
              <button type="button" className="settings-search-clear" onClick={() => onSettingsSearchChange("")}>
                <X size={11} />
              </button>
            ) : null}
          </div>
          <nav className="settings-nav" ref={settingsNavRef}>
            {navItems.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`settings-nav-item ${!showAll && activeSection === item.id ? "active" : ""}`}
                onClick={() => onSectionSelect(item.id)}
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </nav>
        </nav>

        <div className="settings-content settings-content-host" ref={settingsContentRef}>
          <div className="settings-content-stack">{children}</div>
        </div>
      </div>
    </div>
  );
}

export const settingsNavIcons = {
  stream: <Wifi size={15} />,
  nativeStreamer: <Cpu size={15} />,
  game: <Globe size={15} />,
  audio: <Mic size={15} />,
  input: <Keyboard size={15} />,
  interface: <Monitor size={15} />,
  about: <Info size={15} />,
  thanks: <Heart size={15} />,
};
