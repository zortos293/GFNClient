import { Check, Globe, MapPin, Search, Wifi, X } from "lucide-react";
import { type JSX } from "react";
import type { Settings, StreamRegion } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { MotionSpinner } from "../../MotionSpinner";
import { getRegionPingQuality } from "./regionSelection";
import type { SettingsChangeHandler } from "./streamSettingsTypes";
import { useRegionSelection } from "./useRegionSelection";

interface RegionSelectionSectionProps {
  settings: Settings;
  regions: StreamRegion[];
  showAll: boolean;
  visible: boolean;
  handleChange: SettingsChangeHandler;
}

export function RegionSelectionSection({
  settings,
  regions,
  showAll,
  visible,
  handleChange,
}: RegionSelectionSectionProps): JSX.Element | null {
  const { t } = useTranslation();
  const selection = useRegionSelection({
    regions,
    selectedRegion: settings.region,
    onSelectRegion: (regionUrl) => handleChange("region", regionUrl),
  });
  const selectedRegionName = settings.region
    ? regions.find((region) => region.url === settings.region)?.name ?? settings.region
    : t("settings.region.autoBest");
  const bestRegion = selection.bestRegionUrl
    ? regions.find((region) => region.url === selection.bestRegionUrl)
    : undefined;
  const bestPing = selection.bestRegionUrl
    ? selection.pingResults.get(selection.bestRegionUrl)
    : undefined;
  const selectedPing = settings.region
    ? selection.pingResults.get(settings.region)
    : undefined;

  if (!visible) return null;

  return (
    <section className="settings-section settings-section--dropdown">
      {showAll && <div className="settings-section-context">{t("settings.sections.stream")}</div>}
      <div className="settings-section-header">
        <MapPin size={18} />
        <h2>{t("settings.region.title")}</h2>
      </div>
      <div className="settings-rows">
        <div className="settings-row settings-row--column settings-row--region">
          <div className="region-selector" ref={selection.regionSelectorRef}>
            <button
              ref={selection.regionTriggerRef}
              id="settings-stream-region-trigger"
              className={`region-selected ${selection.regionDropdownOpen ? "open" : ""}`}
              onClick={selection.toggleRegionDropdown}
              onKeyDown={selection.handleRegionTriggerKeyDown}
              type="button"
              aria-haspopup="listbox"
              aria-expanded={selection.regionDropdownOpen}
              aria-controls={selection.listboxId}
            >
              <span className="region-selected-leading">
                <Globe size={15} className="region-selected-icon" />
                <span className="region-selected-name">
                  {selectedRegionName || t("settings.region.autoBest")}
                </span>
              </span>
              {!settings.region && bestRegion && bestPing !== undefined && bestPing !== null && (
                <span className="region-selected-best-info">
                  {bestRegion.name} •{" "}
                  <span
                    className={`region-selected-ping-inline ${getRegionPingQuality(bestPing)}`}
                  >
                    {bestPing}ms
                  </span>
                </span>
              )}
              {settings.region && selectedPing !== undefined && selectedPing !== null && (
                <span className={`region-selected-ping ${getRegionPingQuality(selectedPing)}`}>
                  {selectedPing}ms
                </span>
              )}
              {settings.region && selectedPing === null && (
                <span className="region-selected-ping-unavailable">{t("app.status.failed")}</span>
              )}
              {settings.region && selectedPing === undefined && selection.isPinging && (
                <span className="region-selected-ping-unavailable">{t("app.status.testing")}</span>
              )}
              <svg
                viewBox="0 0 16 16"
                width="14"
                height="14"
                fill="currentColor"
                className={`region-chevron ${selection.regionDropdownOpen ? "flipped" : ""}`}
              >
                <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
              </svg>
            </button>

            {selection.regionDropdownOpen && (
              <div className="region-dropdown">
                <div className="region-dropdown-header">
                  <div className="region-dropdown-search">
                    <Search size={14} className="region-dropdown-search-icon" />
                    <input
                      ref={selection.regionSearchInputRef}
                      type="text"
                      role="combobox"
                      className="region-dropdown-search-input"
                      placeholder={t("settings.region.searchPlaceholder")}
                      value={selection.regionSearch}
                      onChange={(event) => selection.setRegionSearch(event.target.value)}
                      onKeyDown={selection.handleRegionSearchKeyDown}
                      aria-label={t("settings.region.searchPlaceholder")}
                      aria-expanded="true"
                      aria-controls={selection.listboxId}
                      aria-activedescendant={selection.activeRegionOptionId}
                      aria-autocomplete="list"
                    />
                    {selection.regionSearch && (
                      <button
                        className="region-dropdown-clear"
                        onClick={() => {
                          selection.setRegionSearch("");
                          selection.regionSearchInputRef.current?.focus({ preventScroll: true });
                        }}
                        type="button"
                        aria-label="Clear region search"
                      >
                        <X size={12} aria-hidden="true" />
                      </button>
                    )}
                  </div>
                  <button
                    className="region-ping-refresh"
                    onClick={() => {
                      void selection.runPingTest();
                    }}
                    disabled={selection.isPinging}
                    type="button"
                    title={t("settings.region.refreshPing")}
                    aria-label={t("settings.region.refreshPing")}
                  >
                    {selection.isPinging ? (
                      <MotionSpinner size={14} label="Testing regions" />
                    ) : (
                      <Wifi size={14} />
                    )}
                  </button>
                </div>

                <div
                  id={selection.listboxId}
                  className="region-dropdown-list"
                  role="listbox"
                  aria-labelledby="settings-stream-region-trigger"
                >
                  <button
                    id={`${selection.listboxId}-option-0`}
                    className={`region-dropdown-item ${!settings.region ? "active" : ""} ${selection.activeRegionIndex === 0 ? "is-active" : ""}`}
                    onClick={() => selection.selectRegion("")}
                    onMouseEnter={() => selection.setActiveRegionValue("")}
                    type="button"
                    role="option"
                    aria-selected={!settings.region}
                    tabIndex={-1}
                  >
                    <Globe size={14} />
                    <div className="region-auto-best-info">
                      <span>{t("settings.region.autoBest")}</span>
                      {bestRegion && bestPing !== undefined && bestPing !== null && (
                        <span className="region-auto-best-details">
                          {bestRegion.name} • {bestPing}ms
                        </span>
                      )}
                    </div>
                    {!settings.region && <Check size={14} className="region-check" />}
                  </button>

                  {selection.filteredRegions.map((region, index) => {
                    const optionIndex = index + 1;
                    const pingValue = selection.pingResults.get(region.url);
                    return (
                      <button
                        key={region.url}
                        id={`${selection.listboxId}-option-${optionIndex}`}
                        className={`region-dropdown-item ${settings.region === region.url ? "active" : ""} ${selection.activeRegionIndex === optionIndex ? "is-active" : ""}`}
                        onClick={() => selection.selectRegion(region.url)}
                        onMouseEnter={() => selection.setActiveRegionValue(region.url)}
                        type="button"
                        role="option"
                        aria-selected={settings.region === region.url}
                        tabIndex={-1}
                      >
                        <Globe size={14} />
                        <span className="region-name-with-badge">
                          {region.name}
                          {region.url === selection.bestRegionUrl && (
                            <span className="region-best-badge">{t("app.labels.best")}</span>
                          )}
                        </span>
                        <span className="region-ping">
                          {selection.isPinging ? (
                            <span className="region-ping-loading">...</span>
                          ) : pingValue === undefined ? (
                            <span className="region-ping-unavailable">-</span>
                          ) : pingValue === null ? (
                            <span className="region-ping-error">{t("app.status.failed")}</span>
                          ) : (
                            <span
                              className={`region-ping-value ${getRegionPingQuality(pingValue)}`}
                            >
                              {pingValue}ms
                            </span>
                          )}
                        </span>
                        {settings.region === region.url && (
                          <Check size={14} className="region-check" />
                        )}
                      </button>
                    );
                  })}

                  {selection.filteredRegions.length === 0 && regions.length > 0 && (
                    <div className="region-dropdown-empty">
                      {t("settings.region.noRegionsMatch", { query: selection.regionSearch })}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
