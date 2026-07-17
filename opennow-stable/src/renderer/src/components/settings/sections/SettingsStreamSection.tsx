import {
  Check, Globe, Heart, MapPin, Monitor, SlidersHorizontal, Wifi, Zap, Search, X, Cpu,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type JSX, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { m } from "motion/react";
import type {
  ColorQuality,
  EntitledResolution,
  Settings,
  StreamRegion,
  VideoCodec,
} from "@shared/gfn";
import {
  colorQualityRequiresHevc,
  DEFAULT_VIDEO_SHADER_SETTINGS,
  expandEntitledStreamResolutions,
  getSafeFallbackEntitledResolutions,
  resolveEntitledStreamProfile,
} from "@shared/gfn";
import { isZortosCommunityProxyUrl, ZORTOS_GITHUB_SPONSORS_URL } from "@shared/communityProxy";
import { getCodecDecodeBadgeState, shouldShowLinuxHardwareCodecHint, type CodecTestResult } from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import {
  clearStoredRegionPingResults,
  loadStoredRegionPingResults,
  saveStoredRegionPingResults,
} from "../../../utils/pingResultsStorage";
import {
  accelerationOptions,
  codecOptions,
  colorQualityOptions,
  extractRemoteInvokeErrorMessage,
  getFpsForResolution,
  groupResolutions,
  inferAspectRatioFromResolution,
  NATIVE_STREAMER_ENABLE_PROMPT_EXIT_MS,
  STATIC_FPS_PRESETS,
  STATIC_RESOLUTION_PRESETS,
} from "../settingsFormatters";
import { dialogMotion, overlayMotion } from "../../MotionProvider";
import { MotionSpinner } from "../../MotionSpinner";
import { SelectDropdown, type SelectDropdownOption } from "../../ui/SelectDropdown";

export interface SettingsStreamSectionProps {
  settings: Settings;
  regions: StreamRegion[];
  showAll: boolean;
  showStreamRegion: boolean;
  showStreamVideo: boolean;
  showStreamCodecDiagnostics: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
  entitledResolutions: EntitledResolution[];
  subscriptionInfoLoaded: boolean;
  subscriptionLoading: boolean;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

export function SettingsStreamSection({
  settings,
  regions,
  showAll,
  showStreamRegion,
  showStreamVideo,
  showStreamCodecDiagnostics,
  handleChange,
  codecResults,
  codecTesting,
  onRunCodecTest,
  entitledResolutions,
  subscriptionInfoLoaded,
  subscriptionLoading,
  onBlockingOverlayChange,
}: SettingsStreamSectionProps): JSX.Element {
  const { locale, t } = useTranslation();
  const [regionSearch, setRegionSearch] = useState("");
  const [regionDropdownOpen, setRegionDropdownOpen] = useState(false);
  const [activeRegionValue, setActiveRegionValue] = useState("");
  const regionSelectorRef = useRef<HTMLDivElement | null>(null);
  const regionTriggerRef = useRef<HTMLButtonElement | null>(null);
  const regionSearchInputRef = useRef<HTMLInputElement | null>(null);
  const regionListboxId = "settings-stream-region-listbox";
  const codecTestOpen = codecResults !== null || codecTesting;
  const [codecAdvancedOpen, setCodecAdvancedOpen] = useState(false);

  const initialPingResults = useMemo(() => loadStoredRegionPingResults(), []);
  const [pingResults, setPingResults] = useState<Map<string, number | null>>(initialPingResults ?? new Map());
  const [isPinging, setIsPinging] = useState(false);
  const [bestRegionUrl, setBestRegionUrl] = useState<string | null>(() => {
    if (!initialPingResults) return null;
    let bestUrl: string | null = null;
    let bestPing = Infinity;
    initialPingResults.forEach((pingMs, url) => {
      if (pingMs !== null && pingMs < bestPing) {
        bestPing = pingMs;
        bestUrl = url;
      }
    });
    return bestUrl;
  });

  const [zortosCommunityProxyPromptOpen, setZortosCommunityProxyPromptOpen] = useState(false);
  const [zortosCommunityProxyPromptClosing, setZortosCommunityProxyPromptClosing] = useState(false);
  const [zortosCommunityProxyProvisioning, setZortosCommunityProxyProvisioning] = useState(false);
  const [zortosCommunityProxyError, setZortosCommunityProxyError] = useState<string | null>(null);
  const zortosCommunityProxyPromptRef = useRef<HTMLDivElement | null>(null);
  const zortosCommunityProxyPromptContinueRef = useRef<HTMLButtonElement | null>(null);
  const zortosCommunityProxyPromptPreviousFocusRef = useRef<HTMLElement | null>(null);
  const zortosCommunityProxyPromptCloseTimerRef = useRef<number | null>(null);
  const zortosCommunityProxyPromptVisible =
    zortosCommunityProxyPromptOpen || zortosCommunityProxyPromptClosing;
  const isUsingZortosCommunityProxy = useMemo(
    () => settings.sessionProxyEnabled && isZortosCommunityProxyUrl(settings.sessionProxyUrl),
    [settings.sessionProxyEnabled, settings.sessionProxyUrl],
  );

  useEffect(() => {
    onBlockingOverlayChange?.(zortosCommunityProxyPromptVisible);
    return () => onBlockingOverlayChange?.(false);
  }, [zortosCommunityProxyPromptVisible, onBlockingOverlayChange]);

  const runPingTest = useCallback(async () => {
    if (regions.length === 0) return;
    setIsPinging(true);
    try {
      const results = await window.openNow.pingRegions(regions);
      const pingMap = new Map<string, number | null>();
      let bestUrl: string | null = null;
      let bestPing = Infinity;

      for (const result of results) {
        pingMap.set(result.url, result.pingMs);
        if (result.pingMs !== null && result.pingMs < bestPing) {
          bestPing = result.pingMs;
          bestUrl = result.url;
        }
      }

      setPingResults(pingMap);
      setBestRegionUrl(bestUrl);
      saveStoredRegionPingResults(pingMap);
    } catch (err) {
      console.error("Ping test failed:", err);
    } finally {
      setIsPinging(false);
    }
  }, [regions]);

  useEffect(() => {
    if (regions.length > 0 && pingResults.size > 0) {
      const allRegionsCached = regions.every(r => pingResults.has(r.url));
      if (!allRegionsCached) {
        setPingResults(new Map());
        setBestRegionUrl(null);
        clearStoredRegionPingResults();
      }
    }
  }, [regions, pingResults]);

  useEffect(() => {
    if (regions.length > 0 && pingResults.size === 0 && !isPinging) {
      runPingTest();
    }
  }, [regions, pingResults.size, isPinging, runPingTest]);

  const effectiveEntitledResolutions = useMemo(
    () => {
      const baseResolutions = entitledResolutions.length > 0
        ? entitledResolutions
        : subscriptionInfoLoaded
          ? getSafeFallbackEntitledResolutions()
          : [];
      return expandEntitledStreamResolutions(baseResolutions);
    },
    [entitledResolutions, subscriptionInfoLoaded],
  );
  const useEntitledStreamOptions = effectiveEntitledResolutions.length > 0;

  const resolutionGroups = useMemo(
    () => (useEntitledStreamOptions ? groupResolutions(effectiveEntitledResolutions) : []),
    [effectiveEntitledResolutions, useEntitledStreamOptions]
  );

  const dynamicFpsOptions = useMemo(
    () => (useEntitledStreamOptions ? getFpsForResolution(effectiveEntitledResolutions, settings.resolution) : []),
    [effectiveEntitledResolutions, settings.resolution, useEntitledStreamOptions]
  );
  const resolvedEntitledProfile = useMemo(
    () => resolveEntitledStreamProfile(effectiveEntitledResolutions, {
      resolution: settings.resolution,
      fps: settings.fps,
    }),
    [effectiveEntitledResolutions, settings.fps, settings.resolution],
  );

  const resolutionOptions = useMemo<SelectDropdownOption[]>(
    () => useEntitledStreamOptions
      ? resolutionGroups.flatMap((group) => group.resolutions.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
          group: group.category,
        })))
      : STATIC_RESOLUTION_PRESETS.map((resolution) => ({
          value: resolution.value,
          label: resolution.label,
        })),
    [resolutionGroups, useEntitledStreamOptions],
  );

  const handleResolutionChange = useCallback((resolution: string) => {
    handleChange("resolution", resolution);
    const aspectRatio = inferAspectRatioFromResolution(resolution);
    if (settings.aspectRatio !== aspectRatio) {
      handleChange("aspectRatio", aspectRatio);
    }
  }, [handleChange, settings.aspectRatio]);

  useEffect(() => {
    if (!useEntitledStreamOptions || !resolvedEntitledProfile) {
      return;
    }

    if (resolvedEntitledProfile.resolution !== settings.resolution) {
      handleResolutionChange(resolvedEntitledProfile.resolution);
    }
    if (resolvedEntitledProfile.fps !== settings.fps) {
      handleChange("fps", resolvedEntitledProfile.fps);
    }
  }, [
    handleChange,
    handleResolutionChange,
    resolvedEntitledProfile,
    settings.fps,
    settings.resolution,
    useEntitledStreamOptions,
  ]);

  const handleColorQualityChange = useCallback(
    (cq: ColorQuality) => {
      if (colorQualityRequiresHevc(cq) && settings.codec === "H264") {
        handleChange("codec", "H265");
      }
      handleChange("colorQuality", cq);
    },
    [handleChange, settings.codec]
  );

  const handleCodecChange = useCallback(
    (codec: VideoCodec) => {
      handleChange("codec", codec);
      if (codec === "H264" && settings.colorQuality !== "8bit_420") {
        handleChange("colorQuality", "8bit_420");
      }
    },
    [handleChange, settings.colorQuality]
  );

  const filteredRegions = useMemo(() => {
    const q = regionSearch.trim().toLowerCase();
    const filtered = q
      ? regions.filter((r) => r.name.toLowerCase().includes(q))
      : [...regions];

    filtered.sort((a, b) => {
      const pingA = pingResults.get(a.url);
      const pingB = pingResults.get(b.url);

      if (pingA !== undefined && pingB !== undefined && pingA !== null && pingB !== null) {
        return pingA - pingB;
      }
      if (pingA !== undefined && pingA !== null) return -1;
      if (pingB !== undefined && pingB !== null) return 1;
      return a.name.localeCompare(b.name);
    });

    return filtered;
  }, [regions, regionSearch, pingResults]);

  const selectedRegionName = useMemo(() => {
    if (!settings.region) return t("settings.region.autoBest");
    const found = regions.find((r) => r.url === settings.region);
    return found?.name ?? settings.region;
  }, [settings.region, regions, locale, t]);

  const regionOptionValues = useMemo(
    () => ["", ...filteredRegions.map((region) => region.url)],
    [filteredRegions],
  );
  const activeRegionIndex = Math.max(0, regionOptionValues.indexOf(activeRegionValue));
  const activeRegionOptionId = regionDropdownOpen && regionOptionValues[activeRegionIndex] !== undefined
    ? `${regionListboxId}-option-${activeRegionIndex}`
    : undefined;

  const openRegionDropdown = useCallback((preferredIndex?: number): void => {
    const selectedIndex = regionOptionValues.indexOf(settings.region);
    const nextIndex = Math.max(
      0,
      Math.min(regionOptionValues.length - 1, preferredIndex ?? (selectedIndex >= 0 ? selectedIndex : 0)),
    );
    setActiveRegionValue(regionOptionValues[nextIndex] ?? "");
    setRegionDropdownOpen(true);
  }, [regionOptionValues, settings.region]);

  const selectRegion = useCallback((regionUrl: string): void => {
    handleChange("region", regionUrl);
    setActiveRegionValue(regionUrl);
    setRegionDropdownOpen(false);
    setRegionSearch("");
    regionTriggerRef.current?.focus({ preventScroll: true });
  }, [handleChange]);

  const handleRegionTriggerKeyDown = useCallback((event: ReactKeyboardEvent<HTMLButtonElement>): void => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const selectedIndex = regionOptionValues.indexOf(settings.region);
      const fallbackIndex = event.key === "ArrowDown" ? 0 : regionOptionValues.length - 1;
      openRegionDropdown(selectedIndex >= 0 ? selectedIndex : fallbackIndex);
    } else if (event.key === "Escape" && regionDropdownOpen) {
      event.preventDefault();
      event.stopPropagation();
      setRegionDropdownOpen(false);
      setRegionSearch("");
    }
  }, [openRegionDropdown, regionDropdownOpen, regionOptionValues, settings.region]);

  const handleRegionSearchKeyDown = useCallback((event: ReactKeyboardEvent<HTMLInputElement>): void => {
    if (regionOptionValues.length === 0) return;

    const currentIndex = Math.max(0, regionOptionValues.indexOf(activeRegionValue));
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setActiveRegionValue(regionOptionValues[(currentIndex + 1) % regionOptionValues.length] ?? "");
        break;
      case "ArrowUp":
        event.preventDefault();
        setActiveRegionValue(regionOptionValues[(currentIndex - 1 + regionOptionValues.length) % regionOptionValues.length] ?? "");
        break;
      case "Home":
        event.preventDefault();
        setActiveRegionValue(regionOptionValues[0] ?? "");
        break;
      case "End":
        event.preventDefault();
        setActiveRegionValue(regionOptionValues.at(-1) ?? "");
        break;
      case "Enter":
        event.preventDefault();
        selectRegion(regionOptionValues.includes(activeRegionValue) ? activeRegionValue : (regionOptionValues[0] ?? ""));
        break;
      case "Escape":
        event.preventDefault();
        event.stopPropagation();
        setRegionDropdownOpen(false);
        setRegionSearch("");
        regionTriggerRef.current?.focus({ preventScroll: true });
        break;
      default:
        break;
    }
  }, [activeRegionValue, regionOptionValues, selectRegion]);

  useEffect(() => {
    if (!regionDropdownOpen) return;

    const focusFrame = window.requestAnimationFrame(() => {
      regionSearchInputRef.current?.focus({ preventScroll: true });
    });
    const closeRegionDropdownWhenOutside = (target: EventTarget | null): void => {
      if (!regionSelectorRef.current?.contains(target as Node)) {
        setRegionDropdownOpen(false);
        setRegionSearch("");
      }
    };
    const handlePointerDown = (event: PointerEvent): void => {
      closeRegionDropdownWhenOutside(event.target);
    };
    const handleFocusIn = (event: FocusEvent): void => {
      closeRegionDropdownWhenOutside(event.target);
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    document.addEventListener("focusin", handleFocusIn);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("pointerdown", handlePointerDown, true);
      document.removeEventListener("focusin", handleFocusIn);
    };
  }, [regionDropdownOpen]);

  useEffect(() => {
    if (!regionDropdownOpen) return;
    if (regionOptionValues.includes(activeRegionValue)) return;
    setActiveRegionValue(regionOptionValues.includes(settings.region) ? settings.region : (regionOptionValues[0] ?? ""));
  }, [activeRegionValue, regionDropdownOpen, regionOptionValues, settings.region]);

  useEffect(() => {
    if (!regionDropdownOpen || !activeRegionOptionId) return;
    document.getElementById(activeRegionOptionId)?.scrollIntoView({ block: "nearest" });
  }, [activeRegionOptionId, regionDropdownOpen]);

  const openZortosCommunityProxyPrompt = useCallback((): void => {
    if (zortosCommunityProxyPromptCloseTimerRef.current !== null) {
      window.clearTimeout(zortosCommunityProxyPromptCloseTimerRef.current);
      zortosCommunityProxyPromptCloseTimerRef.current = null;
    }

    setZortosCommunityProxyError(null);
    setZortosCommunityProxyProvisioning(false);
    setZortosCommunityProxyPromptClosing(false);
    setZortosCommunityProxyPromptOpen(true);
  }, []);

  const closeZortosCommunityProxyPrompt = useCallback((): void => {
    if (zortosCommunityProxyPromptCloseTimerRef.current !== null || zortosCommunityProxyProvisioning) {
      return;
    }

    setZortosCommunityProxyPromptOpen(false);
    setZortosCommunityProxyPromptClosing(true);
    zortosCommunityProxyPromptCloseTimerRef.current = window.setTimeout(() => {
      zortosCommunityProxyPromptCloseTimerRef.current = null;
      setZortosCommunityProxyPromptClosing(false);
      setZortosCommunityProxyError(null);
    }, NATIVE_STREAMER_ENABLE_PROMPT_EXIT_MS);
  }, [zortosCommunityProxyProvisioning]);

  const handleOpenZortosSponsors = useCallback((): void => {
    void window.openNow.openExternalUrl(ZORTOS_GITHUB_SPONSORS_URL).catch((error) => {
      console.error("[Settings] Failed to open GitHub Sponsors:", error);
    });
  }, []);

  const confirmZortosCommunityProxyPrompt = useCallback(async (): Promise<void> => {
    if (zortosCommunityProxyProvisioning) {
      return;
    }

    setZortosCommunityProxyProvisioning(true);
    setZortosCommunityProxyError(null);

    try {
      const result = await window.openNow.provisionZortosCommunityProxy();
      handleChange("sessionProxyUrl", result.proxyUrl);
      handleChange("sessionProxyEnabled", true);
      setZortosCommunityProxyProvisioning(false);
      closeZortosCommunityProxyPrompt();
    } catch (error) {
      console.error("[Settings] Failed to provision Zortos community proxy:", error);
      setZortosCommunityProxyError(
        extractRemoteInvokeErrorMessage(error, t("settings.video.zortosCommunityProxy.provisionFailed")),
      );
    } finally {
      setZortosCommunityProxyProvisioning(false);
    }
  }, [closeZortosCommunityProxyPrompt, handleChange, t, zortosCommunityProxyProvisioning]);

  useEffect(() => {
    return () => {
      if (zortosCommunityProxyPromptCloseTimerRef.current !== null) {
        window.clearTimeout(zortosCommunityProxyPromptCloseTimerRef.current);
        zortosCommunityProxyPromptCloseTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!zortosCommunityProxyPromptVisible) {
      return;
    }

    zortosCommunityProxyPromptPreviousFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape" && !zortosCommunityProxyProvisioning) {
        event.preventDefault();
        closeZortosCommunityProxyPrompt();
      }
    };

    const focusFrame = window.requestAnimationFrame(() => {
      zortosCommunityProxyPromptContinueRef.current?.focus({ preventScroll: true });
    });

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown);
      const previousFocus = zortosCommunityProxyPromptPreviousFocusRef.current;
      zortosCommunityProxyPromptPreviousFocusRef.current = null;
      if (previousFocus?.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
    };
  }, [closeZortosCommunityProxyPrompt, zortosCommunityProxyPromptVisible, zortosCommunityProxyProvisioning]);

  return (
    <>
      <>
        {/* ── Region ── */}
        {showStreamRegion && (
        <section className="settings-section settings-section--dropdown">
          {showAll && <div className="settings-section-context">{t("settings.sections.stream")}</div>}
          <div className="settings-section-header">
            <MapPin size={18} />
            <h2>{t("settings.region.title")}</h2>
          </div>
          <div className="settings-rows">
            <div className="settings-row settings-row--column settings-row--region">
            <div className="region-selector" ref={regionSelectorRef}>
      <button
        ref={regionTriggerRef}
        id="settings-stream-region-trigger"
        className={`region-selected ${regionDropdownOpen ? "open" : ""}`}
        onClick={() => {
          if (regionDropdownOpen) {
            setRegionDropdownOpen(false);
            setRegionSearch("");
          } else {
            openRegionDropdown();
          }
        }}
        onKeyDown={handleRegionTriggerKeyDown}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={regionDropdownOpen}
        aria-controls={regionListboxId}
      >
        <span className="region-selected-leading">
          <Globe size={15} className="region-selected-icon" />
          <span className="region-selected-name">{selectedRegionName || t("settings.region.autoBest")}</span>
        </span>
        {!settings.region && bestRegionUrl && (
          (() => {
            const bestRegion = regions.find(r => r.url === bestRegionUrl);
            const pingValue = pingResults.get(bestRegionUrl);
            if (bestRegion && pingValue !== undefined && pingValue !== null) {
              return (
                <span className="region-selected-best-info">
                  {bestRegion.name} • <span className={`region-selected-ping-inline ${pingValue <= 50 ? 'good' : pingValue <= 100 ? 'medium' : 'poor'}`}>{pingValue}ms</span>
                </span>
              );
            }
            return null;
          })()
        )}
        {settings.region && (
          (() => {
            const pingValue = pingResults.get(settings.region);
            if (pingValue !== undefined && pingValue !== null) {
              return (
                <span className={`region-selected-ping ${pingValue <= 50 ? 'good' : pingValue <= 100 ? 'medium' : 'poor'}`}>
                  {pingValue}ms
                </span>
              );
            } else if (pingValue === null) {
      	                      return <span className="region-selected-ping-unavailable">{t("app.status.failed")}</span>;
            } else if (isPinging) {
              return <span className="region-selected-ping-unavailable">{t("app.status.testing")}</span>;
            }
            return null;
          })()
        )}
        <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`region-chevron ${regionDropdownOpen ? "flipped" : ""}`}>
          <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
        </svg>
      </button>

      {regionDropdownOpen && (
        <div className="region-dropdown">
          <div className="region-dropdown-header">
            <div className="region-dropdown-search">
              <Search size={14} className="region-dropdown-search-icon" />
              <input
                ref={regionSearchInputRef}
                type="text"
                role="combobox"
                className="region-dropdown-search-input"
                placeholder={t("settings.region.searchPlaceholder")}
                value={regionSearch}
                onChange={(e) => setRegionSearch(e.target.value)}
                onKeyDown={handleRegionSearchKeyDown}
                aria-label={t("settings.region.searchPlaceholder")}
                aria-expanded="true"
                aria-controls={regionListboxId}
                aria-activedescendant={activeRegionOptionId}
                aria-autocomplete="list"
              />
              {regionSearch && (
                <button
                  className="region-dropdown-clear"
                  onClick={() => {
                    setRegionSearch("");
                    regionSearchInputRef.current?.focus({ preventScroll: true });
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
              onClick={runPingTest}
              disabled={isPinging}
              type="button"
              title={t("settings.region.refreshPing")}
              aria-label={t("settings.region.refreshPing")}
            >
              {isPinging ? (
                <MotionSpinner size={14} label="Testing regions" />
              ) : (
                <Wifi size={14} />
              )}
            </button>
          </div>

          <div
            id={regionListboxId}
            className="region-dropdown-list"
            role="listbox"
            aria-labelledby="settings-stream-region-trigger"
          >
            <button
              id={`${regionListboxId}-option-0`}
              className={`region-dropdown-item ${!settings.region ? "active" : ""} ${activeRegionIndex === 0 ? "is-active" : ""}`}
              onClick={() => selectRegion("")}
              onMouseEnter={() => setActiveRegionValue("")}
              type="button"
              role="option"
              aria-selected={!settings.region}
              tabIndex={-1}
            >
              <Globe size={14} />
              <div className="region-auto-best-info">
                <span>{t("settings.region.autoBest")}</span>
                {bestRegionUrl && (() => {
                  const bestRegion = regions.find(r => r.url === bestRegionUrl);
                  const bestPing = pingResults.get(bestRegionUrl);
                  if (bestRegion && bestPing !== undefined && bestPing !== null) {
                    return (
                      <span className="region-auto-best-details">
                        {bestRegion.name} • {bestPing}ms
                      </span>
                    );
                  }
                  return null;
                })()}
              </div>
              {!settings.region && <Check size={14} className="region-check" />}
            </button>

            {filteredRegions.map((region, index) => {
              const optionIndex = index + 1;
              return (
              <button
                key={region.url}
                id={`${regionListboxId}-option-${optionIndex}`}
                className={`region-dropdown-item ${settings.region === region.url ? "active" : ""} ${activeRegionIndex === optionIndex ? "is-active" : ""}`}
                onClick={() => selectRegion(region.url)}
                onMouseEnter={() => setActiveRegionValue(region.url)}
                type="button"
                role="option"
                aria-selected={settings.region === region.url}
                tabIndex={-1}
              >
                <Globe size={14} />
                <span className="region-name-with-badge">
                  {region.name}
                  {region.url === bestRegionUrl && (
                    <span className="region-best-badge">{t("app.labels.best")}</span>
                  )}
                </span>
                <span className="region-ping">
                  {isPinging ? (
                    <span className="region-ping-loading">...</span>
                  ) : (
                    (() => {
                      const pingValue = pingResults.get(region.url);
                      if (pingValue === undefined) {
                        return <span className="region-ping-unavailable">-</span>;
                      } else if (pingValue === null) {
                        return <span className="region-ping-error">{t("app.status.failed")}</span>;
                      } else {
                        return (
                          <span className={`region-ping-value ${pingValue <= 50 ? 'good' : pingValue <= 100 ? 'medium' : 'poor'}`}>
                            {pingValue}ms
                          </span>
                        );
                      }
                    })()
                  )}
                </span>
                {settings.region === region.url && <Check size={14} className="region-check" />}
              </button>
              );
            })}

            {filteredRegions.length === 0 && regions.length > 0 && (
              <div className="region-dropdown-empty">{t("settings.region.noRegionsMatch", { query: regionSearch })}</div>
            )}
          </div>
        </div>
      )}
            </div>
            </div>
      </div>
                  </section>

        )}
                  {showStreamVideo && (
                  <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.stream")}</div>}
      <div className="settings-section-header">
        <Monitor size={18} />
        <h2>{t("settings.video.title")}</h2>
      </div>
      <div className="settings-rows">
        {/* Resolution — grouped dropdown */}
        <div className="settings-row">
          <label className="settings-label" htmlFor="settings-stream-resolution">
            <span className="settings-label-title">
              {t("settings.video.resolution")}
              {subscriptionLoading && <MotionSpinner size={12} className="settings-loading-icon" />}
            </span>
          </label>
          <div className="settings-row-control">
            <SelectDropdown
              id="settings-stream-resolution"
              value={settings.resolution}
              options={resolutionOptions}
              onChange={handleResolutionChange}
              menuClassName="select-dropdown__menu--grouped"
            />
          </div>
        </div>

        {/* FPS — dynamic or static chips */}
        <div className="settings-row">
          <label className="settings-label">{t("settings.video.fps")}</label>
          <div className="settings-row-control">
            <div className="settings-chip-row">
              {(useEntitledStreamOptions ? dynamicFpsOptions.map((v) => ({ value: v })) : STATIC_FPS_PRESETS).map((preset) => (
                <button
                  key={preset.value}
                  className={`settings-chip ${settings.fps === preset.value ? "active" : ""}`}
                  aria-pressed={settings.fps === preset.value}
                  onClick={() => { handleChange("fps", preset.value); }}
                >
                  <span>{preset.value}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Codec */}
        <div className="settings-row">
          <label className="settings-label">{t("settings.video.codec")}</label>
          <div className="settings-row-control">
            <div className="settings-chip-row">
              {codecOptions.map((codec) => {
                const badgeState = getCodecDecodeBadgeState(codec, codecResults, codecTesting);
                return (
                  <button
                    key={codec}
                    className={`settings-chip settings-chip--codec ${settings.codec === codec ? "active" : ""}`}
                    aria-pressed={settings.codec === codec}
                    onClick={() => handleCodecChange(codec)}
                  >
                    <span>{codec}</span>
                    {badgeState && (
                      <span className={`settings-inline-badge settings-inline-badge--codec settings-inline-badge--codec-${badgeState}`}>
                        {badgeState === "gpu" ? t("settings.video.gpu") : badgeState === "cpu" ? t("settings.video.cpu") : t("settings.video.testing")}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Color Quality */}
        <div className="settings-row">
          <label className="settings-label">{t("settings.video.colorDepth")}</label>
          <div className="settings-row-control">
            <div className="settings-chip-row">
              {colorQualityOptions.map((opt) => {
                const needsHevc = colorQualityRequiresHevc(opt.value);
                const colorDescription = opt.value === "8bit_420"
                  ? t("settings.colorQuality.mostCompatible")
                  : opt.value === "8bit_444"
                    ? t("settings.colorQuality.sharperChroma")
                    : opt.value === "10bit_420"
                      ? t("settings.colorQuality.higherBitDepth")
                      : t("settings.colorQuality.highestChromaAndBitDepth");
                return (
                  <button
                    key={opt.value}
                    className={`settings-chip ${settings.colorQuality === opt.value ? "active" : ""}`}
                    aria-pressed={settings.colorQuality === opt.value}
                    onClick={() => handleColorQualityChange(opt.value)}
                    title={needsHevc ? t("settings.colorQuality.requiresH265OrAv1Title", { description: colorDescription }) : colorDescription}
                  >
                    <span>{opt.label}</span>
                  </button>
                );
              })}
            </div>
            {colorQualityRequiresHevc(settings.colorQuality) && settings.codec === "H264" && (
              <span className="settings-input-hint">{t("settings.video.requiresH265OrAv1")}</span>
            )}
          </div>
        </div>

        {/* Bitrate slider */}
        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label" htmlFor="settings-stream-max-bitrate">{t("settings.video.maxBitrate")}</label>
            <span className="settings-value-badge">{settings.maxBitrateMbps} Mbps</span>
          </div>
          <input
            id="settings-stream-max-bitrate"
            type="range"
            className="settings-slider"
            min={5}
            max={150}
            step={5}
            value={settings.maxBitrateMbps}
            onChange={(e) => handleChange("maxBitrateMbps", parseInt(e.target.value, 10))}
          />
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label" htmlFor="settings-stream-recording-bitrate">{t("settings.video.recordingBitrate")}</label>
            <span className="settings-value-badge">
              {settings.recordingBitrateMbps === null
                ? t("app.labels.auto")
                : `${settings.recordingBitrateMbps} Mbps`}
            </span>
          </div>
          <div className="settings-chip-row">
            <button
              type="button"
              className={`settings-chip ${settings.recordingBitrateMbps === null ? "active" : ""}`}
              aria-pressed={settings.recordingBitrateMbps === null}
              onClick={() => handleChange("recordingBitrateMbps", null)}
            >
              <span>{t("app.labels.auto")}</span>
            </button>
            <button
              type="button"
              className={`settings-chip ${settings.recordingBitrateMbps !== null ? "active" : ""}`}
              aria-pressed={settings.recordingBitrateMbps !== null}
              onClick={() => handleChange("recordingBitrateMbps", settings.recordingBitrateMbps ?? 75)}
            >
              <span>{t("settings.video.customBitrate")}</span>
            </button>
          </div>
          <input
            id="settings-stream-recording-bitrate"
            type="range"
            className="settings-slider"
            min={5}
            max={200}
            step={5}
            value={settings.recordingBitrateMbps ?? 75}
            disabled={settings.recordingBitrateMbps === null}
            onChange={(e) => handleChange("recordingBitrateMbps", parseInt(e.target.value, 10))}
          />
          <span className="settings-subtle-hint">{t("settings.video.recordingBitrateHint")}</span>
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap" htmlFor="settings-stream-session-proxy">
              <span className="settings-label-title">
                {t("settings.video.sessionProxy")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
                id="settings-stream-session-proxy"
                type="checkbox"
                checked={settings.sessionProxyEnabled}
                onChange={(e) => handleChange("sessionProxyEnabled", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">
            {t("settings.video.sessionProxyHint")}
          </span>
          <div className="settings-community-proxy-row">
            {isUsingZortosCommunityProxy ? (
              <span className="settings-inline-badge settings-inline-badge--beta">
                {t("settings.video.zortosCommunityProxy.enabledBadge")}
              </span>
            ) : (
              <button
                type="button"
                className="settings-chip settings-community-proxy-btn"
                onClick={openZortosCommunityProxyPrompt}
              >
                <Heart size={14} />
                <span>{t("settings.video.zortosCommunityProxy.useButton")}</span>
              </button>
            )}
          </div>
          {settings.sessionProxyEnabled && (
            <input
              type="text"
              className="settings-text-input"
              placeholder="http://127.0.0.1:8080"
              value={settings.sessionProxyUrl}
              onChange={(e) => handleChange("sessionProxyUrl", e.target.value)}
            />
          )}
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap" htmlFor="settings-stream-enable-l4s">
              <span className="settings-label-title">
                {t("settings.video.experimentalL4SRequest")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
                id="settings-stream-enable-l4s"
                type="checkbox"
                checked={settings.enableL4S}
                onChange={(e) => handleChange("enableL4S", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">
            {t("settings.video.experimentalL4SRequestHint")}
          </span>
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap" htmlFor="settings-stream-launch-console-mode">
              <span className="settings-label-title">
                {t("settings.video.launchInConsoleMode")}
              </span>
            </label>
            <label className="settings-toggle">
              <input
                id="settings-stream-launch-console-mode"
                type="checkbox"
                checked={settings.launchInConsoleMode}
                onChange={(e) => handleChange("launchInConsoleMode", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">
            {t("settings.video.launchInConsoleModeHint")}
          </span>
        </div>

        {/* Video filters (client-side GPU shaders) */}
        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap" htmlFor="settings-stream-video-filters-enabled">
              <span className="settings-label-title">
                {t("settings.videoFilters.title")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
                id="settings-stream-video-filters-enabled"
                type="checkbox"
                checked={settings.videoShader.enabled}
                onChange={(e) => handleChange("videoShader", { ...settings.videoShader, enabled: e.target.checked })}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">
            {settings.streamClientMode === "native"
              ? t("settings.videoFilters.nativeUnavailable")
              : t("settings.videoFilters.hint")}
          </span>
          {settings.videoShader.enabled && (
            <>
              {([
                { key: "sharpen", labelKey: "settings.videoFilters.sharpen", min: 0, max: 100 },
                { key: "saturation", labelKey: "settings.videoFilters.saturation", min: 0, max: 200 },
                { key: "contrast", labelKey: "settings.videoFilters.contrast", min: 50, max: 150 },
                { key: "brightness", labelKey: "settings.videoFilters.brightness", min: 50, max: 150 },
                { key: "vibrance", labelKey: "settings.videoFilters.vibrance", min: 0, max: 100 },
                { key: "filmGrain", labelKey: "settings.videoFilters.filmGrain", min: 0, max: 100 },
              ] as const).map((control) => (
                <div key={control.key} className="settings-row settings-row--column">
                  <div className="settings-row-top">
                    <label className="settings-label" htmlFor={`settings-stream-video-filter-${control.key}`}>{t(control.labelKey)}</label>
                    <span className="settings-value-badge">{settings.videoShader[control.key]}%</span>
                  </div>
                  <input
                    id={`settings-stream-video-filter-${control.key}`}
                    type="range"
                    className="settings-slider"
                    min={control.min}
                    max={control.max}
                    step={1}
                    value={settings.videoShader[control.key]}
                    onChange={(e) => {
                      const next = parseInt(e.target.value, 10);
                      if (Number.isFinite(next)) {
                        handleChange("videoShader", { ...settings.videoShader, [control.key]: next });
                      }
                    }}
                  />
                </div>
              ))}
              <div className="settings-chip-row">
                <button
                  type="button"
                  className="settings-chip"
                  onClick={() => handleChange("videoShader", { ...DEFAULT_VIDEO_SHADER_SETTINGS, enabled: true })}
                >
                  <span>{t("settings.videoFilters.reset")}</span>
                </button>
              </div>
            </>
          )}
        </div>
      </div>
                  </section>

                  )}
                  {(showStreamVideo || showStreamCodecDiagnostics) && (
                  <div className="settings-advanced-wrap">
      <button
        type="button"
        className="settings-advanced-toggle"
        onClick={() => setCodecAdvancedOpen(v => !v)}
      >
        <SlidersHorizontal size={14} />
        Advanced
        <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor" className={`settings-advanced-chevron ${codecAdvancedOpen ? "flipped" : ""}`}>
          <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
        </svg>
      </button>
      {codecAdvancedOpen && (
        <section className="settings-section">
          {showAll && <div className="settings-section-context">{t("settings.sections.stream")}</div>}
          <div className="settings-section-header">
            <Cpu size={18} />
            <h2>Advanced</h2>
          </div>
          <div className="settings-rows">
            {showStreamVideo && (
              <>
                <div className="settings-row">
                  <label className="settings-label">{t("settings.video.decoder")}</label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`decoder-${option.value}`}
                          className={`settings-chip ${settings.decoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.decoderPreference === option.value}
                          onClick={() => handleChange("decoderPreference", option.value)}
                        >
                          {option.value === "auto"
                            ? t("app.labels.auto")
                            : option.value === "hardware"
                              ? t("app.labels.hardware")
                              : t("settings.video.softwareCpu")}
                        </button>
                      ))}
                    </div>
                    <span className="settings-subtle-hint">{t("settings.video.appliesAfterRestart")}</span>
                  </div>
                </div>

                <div className="settings-row">
                  <label className="settings-label">{t("settings.video.encoder")}</label>
                  <div className="settings-row-control">
                    <div className="settings-chip-row">
                      {accelerationOptions.map((option) => (
                        <button
                          key={`encoder-${option.value}`}
                          className={`settings-chip ${settings.encoderPreference === option.value ? "active" : ""}`}
                          aria-pressed={settings.encoderPreference === option.value}
                          onClick={() => handleChange("encoderPreference", option.value)}
                        >
                          {option.value === "auto"
                            ? t("app.labels.auto")
                            : option.value === "hardware"
                              ? t("app.labels.hardware")
                              : t("settings.video.softwareCpu")}
                        </button>
                      ))}
                    </div>
                    <span className="settings-subtle-hint">{t("settings.video.appliesAfterRestart")}</span>
                  </div>
                </div>
              </>
            )}

            {showStreamCodecDiagnostics && (
              <>
            <div className="settings-row codec-test-row">
              <label className="settings-label codec-test-description">
                {t("settings.codecDiagnostics.description")}
              </label>
              <button
                className="codec-test-btn"
                onClick={() => { void onRunCodecTest(); }}
                disabled={codecTesting}
                type="button"
              >
                {codecTesting ? (
                  <>
                    <MotionSpinner size={16} className="settings-loading-icon" />
                    {t("settings.video.testing")}
                  </>
                ) : (
                  <>
                    <Zap size={16} />
                    {codecResults ? t("settings.codecDiagnostics.retest") : t("settings.codecDiagnostics.testCodecs")}
                  </>
                )}
              </button>
            </div>
            {codecTestOpen && codecResults && (
              <div className="codec-results">
                {shouldShowLinuxHardwareCodecHint(codecResults) ? (
                  <div className="codec-result-hint">
                    {t("settings.codecDiagnostics.linuxHardwareHint")}
                  </div>
                ) : null}
                {codecResults.map((result) => (
                  <div key={result.codec} className="codec-result-card">
                    <div className="codec-result-header">
                      <span className="codec-result-name">{result.codec}</span>
                      <span className={`codec-result-badge ${result.webrtcSupported ? "supported" : "unsupported"}`}>
                        {result.webrtcSupported ? t("settings.codecDiagnostics.webrtcReady") : t("settings.codecDiagnostics.notInWebrtc")}
                      </span>
                    </div>
                    <div className="codec-result-rows">
                      <div className="codec-result-row">
                        <span className="codec-result-direction">{t("settings.codecDiagnostics.decode")}</span>
                        <span className={`codec-result-status ${result.decodeSupported ? (result.hwAccelerated ? "hw" : "sw") : "none"}`}>
                          {result.decodeSupported ? (result.hwAccelerated ? t("settings.video.gpu") : t("settings.video.cpu")) : t("app.labels.no")}
                        </span>
                        <span className="codec-result-via">{result.decodeVia}</span>
                      </div>
                      <div className="codec-result-row">
                        <span className="codec-result-direction">{t("settings.codecDiagnostics.encode")}</span>
                        <span className={`codec-result-status ${result.encodeSupported ? (result.encodeHwAccelerated ? "hw" : "sw") : "none"}`}>
                          {result.encodeSupported ? (result.encodeHwAccelerated ? t("settings.video.gpu") : t("settings.video.cpu")) : t("app.labels.no")}
                        </span>
                        <span className="codec-result-via">{result.encodeVia}</span>
                      </div>
                    </div>
                    {result.profiles.length > 0 && (
                      <div className="codec-result-profiles">
                        <span className="codec-result-profiles-label">{t("settings.codecDiagnostics.profiles")}</span>
                        <div className="codec-result-profiles-list">
                          {result.profiles.map((p, i) => (
                            <code key={i} className="codec-result-profile">{p}</code>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
              </>
            )}
          </div>
        </section>
      )}
                  </div>
                  )}
                </>
      {zortosCommunityProxyPromptVisible && (
        <m.div
          className={`native-streamer-warning ${zortosCommunityProxyPromptClosing ? "native-streamer-warning--closing" : ""}`}
          role="dialog"
          aria-modal="true"
          aria-labelledby="zortos-community-proxy-title"
          aria-describedby="zortos-community-proxy-copy"
          initial={overlayMotion.initial}
          animate={zortosCommunityProxyPromptClosing ? overlayMotion.exit : overlayMotion.animate}
          transition={overlayMotion.transition}
        >
          <m.button
            type="button"
            className="native-streamer-warning-backdrop"
            aria-label={t("app.actions.cancel")}
            aria-hidden="true"
            tabIndex={-1}
            disabled={zortosCommunityProxyProvisioning}
            onClick={closeZortosCommunityProxyPrompt}
            initial={overlayMotion.initial}
            animate={zortosCommunityProxyPromptClosing ? overlayMotion.exit : overlayMotion.animate}
            transition={overlayMotion.transition}
          />
          <m.div
            ref={zortosCommunityProxyPromptRef}
            className="native-streamer-warning-card"
            tabIndex={-1}
            initial={dialogMotion.initial}
            animate={zortosCommunityProxyPromptClosing ? dialogMotion.exit : dialogMotion.animate}
            transition={dialogMotion.transition}
          >
            <div className="native-streamer-warning-kicker">
              <Heart size={14} />
              {t("settings.video.zortosCommunityProxy.enablePromptKicker")}
            </div>
            <h3 id="zortos-community-proxy-title" className="native-streamer-warning-title">
              {t("settings.video.zortosCommunityProxy.enablePromptTitle")}
            </h3>
            <p id="zortos-community-proxy-copy" className="native-streamer-warning-text">
              {t("settings.video.zortosCommunityProxy.enablePromptBody")}
            </p>
            <p className="native-streamer-warning-text">
              {t("settings.video.zortosCommunityProxy.enablePromptCostHint")}
            </p>
            {zortosCommunityProxyError && (
              <p className="settings-community-proxy-error">{zortosCommunityProxyError}</p>
            )}
            <div className="native-streamer-warning-actions">
              <button
                type="button"
                className="native-streamer-warning-btn native-streamer-warning-btn--primary native-streamer-warning-btn--with-icon"
                onClick={handleOpenZortosSponsors}
              >
                <Heart size={15} />
                {t("settings.video.zortosCommunityProxy.enablePromptDonate")}
              </button>
              <button
                type="button"
                className="native-streamer-warning-btn native-streamer-warning-btn--secondary"
                onClick={() => {
                  void confirmZortosCommunityProxyPrompt();
                }}
                ref={zortosCommunityProxyPromptContinueRef}
                disabled={zortosCommunityProxyProvisioning}
                autoFocus
              >
                {zortosCommunityProxyProvisioning
                  ? t("settings.video.zortosCommunityProxy.provisioning")
                  : t("settings.video.zortosCommunityProxy.enablePromptContinue")}
              </button>
            </div>
            <div className="native-streamer-warning-hint">
              <kbd>Esc</kbd> {t("settings.video.zortosCommunityProxy.enablePromptEsc")}
            </div>
          </m.div>
        </m.div>
      )}
    </>
  );
}
