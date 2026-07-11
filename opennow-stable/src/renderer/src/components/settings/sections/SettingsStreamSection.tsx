import {
  Check, Globe, Heart, Loader, MapPin, Monitor, ScanLine, Gauge, Film, SlidersHorizontal, HardDrive, Sparkles, Wifi, Zap, Search, X, Cpu,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type JSX } from "react";
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
  const codecTestOpen = codecResults !== null || codecTesting;
  const [codecAdvancedOpen, setCodecAdvancedOpen] = useState(false);
  const [resolutionDropdownOpen, setResolutionDropdownOpen] = useState(false);
  const resolutionDropdownRef = useRef<HTMLDivElement | null>(null);

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

  const selectedResolutionLabel = useMemo(() => {
    if (useEntitledStreamOptions) {
      for (const group of resolutionGroups) {
        const found = group.resolutions.find(r => r.value === settings.resolution);
        if (found) return found.label;
      }
      return settings.resolution || "Select";
    }
    const found = STATIC_RESOLUTION_PRESETS.find(r => r.value === settings.resolution);
    return found ? found.label : settings.resolution || "Select";
  }, [settings.resolution, useEntitledStreamOptions, resolutionGroups]);

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
      handleChange("colorQuality", cq);
      if (colorQualityRequiresHevc(cq) && settings.codec === "H264") {
        handleChange("codec", "H265");
      }
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

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent): void => {
      const target = event.target as Node;
      if (resolutionDropdownRef.current && !resolutionDropdownRef.current.contains(target)) {
        setResolutionDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

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
            <div className="region-selector">
      <button
        className={`region-selected ${regionDropdownOpen ? "open" : ""}`}
        onClick={() => setRegionDropdownOpen(!regionDropdownOpen)}
        type="button"
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
                type="text"
                className="region-dropdown-search-input"
                placeholder={t("settings.region.searchPlaceholder")}
                value={regionSearch}
                onChange={(e) => setRegionSearch(e.target.value)}
                autoFocus
              />
              {regionSearch && (
                <button className="region-dropdown-clear" onClick={() => setRegionSearch("")} type="button">
                  <X size={12} />
                </button>
              )}
            </div>
            <button
              className="region-ping-refresh"
              onClick={runPingTest}
              disabled={isPinging}
              type="button"
              title={t("settings.region.refreshPing")}
            >
              {isPinging ? (
                <Loader size={14} className="spin" />
              ) : (
                <Wifi size={14} />
              )}
            </button>
          </div>

          <div className="region-dropdown-list">
            <button
              className={`region-dropdown-item ${!settings.region ? "active" : ""}`}
              onClick={() => {
                handleChange("region", "");
                setRegionDropdownOpen(false);
                setRegionSearch("");
              }}
              type="button"
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

            {filteredRegions.map((region) => (
              <button
                key={region.url}
                className={`region-dropdown-item ${settings.region === region.url ? "active" : ""}`}
                onClick={() => {
                  handleChange("region", region.url);
                  setRegionDropdownOpen(false);
                  setRegionSearch("");
                }}
                type="button"
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
            ))}

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
        <div className="settings-row settings-row--column">
          <label className="settings-label settings-label--with-icon">
            <ScanLine size={15} className="settings-label-icon" />
            {t("settings.video.resolution")}
            {subscriptionLoading && <Loader size={12} className="settings-loading-icon" />}
          </label>
          <div className="settings-dropdown settings-resolution-dropdown" ref={resolutionDropdownRef}>
            <button
              type="button"
              className={`settings-dropdown-selected ${resolutionDropdownOpen ? "open" : ""}`}
              onClick={() => setResolutionDropdownOpen(o => !o)}
            >
              <span className="settings-dropdown-selected-name">{selectedResolutionLabel}</span>
              <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`settings-dropdown-chevron ${resolutionDropdownOpen ? "flipped" : ""}`}>
                <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
              </svg>
            </button>
            {resolutionDropdownOpen && (
              <div className="settings-dropdown-menu settings-dropdown-menu--grouped">
                {(useEntitledStreamOptions ? resolutionGroups : [{ category: "All", resolutions: STATIC_RESOLUTION_PRESETS.map(p => ({ ...p, width: 0, height: 0 })) }]).map(group => (
                  <div key={group.category} className="settings-dropdown-group">
                    <div className="settings-dropdown-group-label">{group.category}</div>
                    {group.resolutions.map(res => (
                      <button
                        key={res.value}
                        type="button"
                        className={`settings-dropdown-item ${settings.resolution === res.value ? "active" : ""}`}
                        onClick={() => { handleResolutionChange(res.value); setResolutionDropdownOpen(false); }}
                      >
                        <span>{res.label}</span>
                        {settings.resolution === res.value && <Check size={14} className="settings-dropdown-check" />}
                      </button>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* FPS — dynamic or static chips */}
        <div className="settings-row">
          <label className="settings-label settings-label--with-icon">
            <Gauge size={15} className="settings-label-icon" />
            {t("settings.video.fps")}
          </label>
          <div className="settings-chip-row">
            {(useEntitledStreamOptions ? dynamicFpsOptions.map((v) => ({ value: v })) : STATIC_FPS_PRESETS).map((preset) => (
              <button
                key={preset.value}
                className={`settings-chip ${settings.fps === preset.value ? "active" : ""}`}
                onClick={() => { handleChange("fps", preset.value); }}
              >
                <span>{preset.value}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Codec */}
        <div className="settings-row">
          <label className="settings-label settings-label--with-icon">
            <Film size={15} className="settings-label-icon" />
            {t("settings.video.codec")}
          </label>
          <div className="settings-chip-row">
            {codecOptions.map((codec) => {
              const badgeState = getCodecDecodeBadgeState(codec, codecResults, codecTesting);
              return (
                <button
                  key={codec}
                  className={`settings-chip settings-chip--codec ${settings.codec === codec ? "active" : ""}`}
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

        {/* Color Quality */}
        <div className="settings-row settings-row--column">
          <label className="settings-label settings-label--with-icon">
            <SlidersHorizontal size={15} className="settings-label-icon" />
            {t("settings.video.colorDepth")}
          </label>
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

        {/* Bitrate slider */}
        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label settings-label--with-icon">
              <HardDrive size={15} className="settings-label-icon" />
              {t("settings.video.maxBitrate")}
            </label>
            <span className="settings-value-badge">{settings.maxBitrateMbps} Mbps</span>
          </div>
          <input
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
            <label className="settings-label">{t("settings.video.recordingBitrate")}</label>
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
              onClick={() => handleChange("recordingBitrateMbps", null)}
            >
              <span>{t("app.labels.auto")}</span>
            </button>
            <button
              type="button"
              className={`settings-chip ${settings.recordingBitrateMbps !== null ? "active" : ""}`}
              onClick={() => handleChange("recordingBitrateMbps", settings.recordingBitrateMbps ?? 75)}
            >
              <span>{t("settings.video.customBitrate")}</span>
            </button>
          </div>
          <input
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
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.video.sessionProxy")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
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
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.video.experimentalL4SRequest")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
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
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.video.launchInConsoleMode")}
              </span>
            </label>
            <label className="settings-toggle">
              <input
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
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                <Sparkles size={15} className="settings-label-icon" />
                {t("settings.videoFilters.title")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
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
                    <label className="settings-label">{t(control.labelKey)}</label>
                    <span className="settings-value-badge">{settings.videoShader[control.key]}%</span>
                  </div>
                  <input
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
                <div className="settings-row settings-row--column">
                  <label className="settings-label settings-label--with-icon">
                    <Cpu size={15} className="settings-label-icon" />
                    {t("settings.video.decoder")}
                  </label>
                  <div className="settings-chip-row">
                    {accelerationOptions.map((option) => (
                      <button
                        key={`decoder-${option.value}`}
                        className={`settings-chip ${settings.decoderPreference === option.value ? "active" : ""}`}
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

                <div className="settings-row settings-row--column">
                  <label className="settings-label settings-label--with-icon">
                    <Film size={15} className="settings-label-icon" />
                    {t("settings.video.encoder")}
                  </label>
                  <div className="settings-chip-row">
                    {accelerationOptions.map((option) => (
                      <button
                        key={`encoder-${option.value}`}
                        className={`settings-chip ${settings.encoderPreference === option.value ? "active" : ""}`}
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
              </>
            )}

            {showStreamCodecDiagnostics && (
              <>
            <div className="settings-row codec-test-row">
              <label className="settings-label codec-test-description settings-label--with-icon">
                <Zap size={15} className="settings-label-icon" />
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
                    <Loader size={16} className="settings-loading-icon" />
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
        <div
          className={`native-streamer-warning ${zortosCommunityProxyPromptClosing ? "native-streamer-warning--closing" : ""}`}
          role="dialog"
          aria-modal="true"
          aria-labelledby="zortos-community-proxy-title"
          aria-describedby="zortos-community-proxy-copy"
        >
          <button
            type="button"
            className="native-streamer-warning-backdrop"
            aria-label={t("app.actions.cancel")}
            aria-hidden="true"
            tabIndex={-1}
            disabled={zortosCommunityProxyProvisioning}
            onClick={closeZortosCommunityProxyPrompt}
          />
          <div ref={zortosCommunityProxyPromptRef} className="native-streamer-warning-card" tabIndex={-1}>
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
          </div>
        </div>
      )}
    </>
  );
}
