import { Check, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type JSX } from "react";

import type {
  EntitledResolution,
  Settings,
  StreamRegion,
  SubscriptionInfo,
} from "@shared/gfn";
import { useTranslation } from "../i18n";
import type { CodecTestResult } from "../lib/codecDiagnostics";
import {
  loadCachedEntitledResolutions,
  saveCachedEntitledResolutions,
} from "./settings/settingsFormatters";
import type { SettingsSectionId, SettingsSearchScopeId } from "./settings/settingsTypes";
import { SETTINGS_SCOPE_SEARCH_TERMS } from "./settings/settingsTypes";
import { SettingsNav } from "./settings/SettingsNav";
import { SettingsAccountSection } from "./settings/sections/SettingsAccountSection";
import { SettingsAboutSection } from "./settings/sections/SettingsAboutSection";
import { SettingsAudioSection } from "./settings/sections/SettingsAudioSection";
import { SettingsGameSection } from "./settings/sections/SettingsGameSection";
import { SettingsInputSection } from "./settings/sections/SettingsInputSection";
import { SettingsInterfaceSection } from "./settings/sections/SettingsInterfaceSection";
import { SettingsNativeStreamerSection } from "./settings/sections/SettingsNativeStreamerSection";
import { SettingsStreamSection } from "./settings/sections/SettingsStreamSection";
import { SettingsThanksSection } from "./settings/sections/SettingsThanksSection";

export type { SettingsSectionId } from "./settings/settingsTypes";

interface SettingsPageProps {
  settings: Settings;
  regions: StreamRegion[];
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  onRunCodecTest: () => Promise<void>;
  onSettingPreview: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  onSettingChange: <K extends keyof Settings>(key: K, value: Settings[K]) => Promise<void>;
  onClose: () => void;
  focusSection?: SettingsSectionId;
  /** Called when the user clicks "What's new" in the About section */
  onOpenWhatsNew?: () => void;
  /** Called when the user clicks "Send feedback" in the About section */
  onOpenFeedback?: () => void;
}

export function SettingsPage({
  settings,
  regions,
  onSettingPreview,
  onSettingChange,
  codecResults,
  codecTesting,
  onRunCodecTest,
  onClose,
  focusSection,
  onOpenWhatsNew,
  onOpenFeedback,
}: SettingsPageProps): JSX.Element {
  const { t } = useTranslation();
  const [savedIndicator, setSavedIndicator] = useState(false);
  const [activeSection, setActiveSection] = useState<SettingsSectionId>("stream");
  const [settingsSearch, setSettingsSearch] = useState("");
  const settingsSearchShowsAll = settingsSearch.trim().length > 0;
  const settingsContentRef = useRef<HTMLDivElement | null>(null);
  const [nativeOverlayBlocking, setNativeOverlayBlocking] = useState(false);
  const [streamOverlayBlocking, setStreamOverlayBlocking] = useState(false);
  const saveRequestRef = useRef(0);
  const savedIndicatorTimerRef = useRef<number | null>(null);

  const [entitledResolutions, setEntitledResolutions] = useState<EntitledResolution[]>([]);
  const [subscriptionInfo, setSubscriptionInfo] = useState<SubscriptionInfo | null>(null);
  const [subscriptionLoading, setSubscriptionLoading] = useState(true);
  const identifyAsSteamDeckRef = useRef(settings.identifyAsSteamDeck);
  const subscriptionLoadIdRef = useRef(0);
  identifyAsSteamDeckRef.current = settings.identifyAsSteamDeck;

  useEffect(() => {
    settingsContentRef.current?.scrollTo({ top: 0 });
  }, [activeSection, settingsSearchShowsAll]);

  useEffect(() => {
    if (!focusSection) return;
    setActiveSection(focusSection);
    setSettingsSearch("");
  }, [focusSection]);

  const showSavedIndicator = useCallback((requestId: number): void => {
    if (requestId !== saveRequestRef.current) return;
    if (savedIndicatorTimerRef.current !== null) {
      window.clearTimeout(savedIndicatorTimerRef.current);
    }
    setSavedIndicator(true);
    savedIndicatorTimerRef.current = window.setTimeout(() => {
      savedIndicatorTimerRef.current = null;
      if (requestId === saveRequestRef.current) {
        setSavedIndicator(false);
      }
    }, 1500);
  }, []);

  const handlePreview = useCallback(
    <K extends keyof Settings>(key: K, value: Settings[K]): void => {
      onSettingPreview(key, value);
    },
    [onSettingPreview],
  );

  const markSaved = useCallback(() => {
    showSavedIndicator(++saveRequestRef.current);
  }, [showSavedIndicator]);

  useEffect(() => () => {
    if (savedIndicatorTimerRef.current !== null) {
      window.clearTimeout(savedIndicatorTimerRef.current);
    }
  }, []);

  const loadSubscriptionData = useCallback(async (
    options?: { identifyAsSteamDeck?: boolean; preferCache?: boolean },
  ): Promise<void> => {
    const loadId = ++subscriptionLoadIdRef.current;
    const isCurrentLoad = (): boolean => subscriptionLoadIdRef.current === loadId;
    setSubscriptionLoading(true);

    try {
      const sessionResult = await window.openNow.getAuthSession();
      const session = sessionResult.session;
      if (!isCurrentLoad()) {
        return;
      }
      if (!session) {
        setEntitledResolutions([]);
        setSubscriptionInfo(null);
        return;
      }

      const userId = session.user.userId;
      const identifyAsSteamDeck = options?.identifyAsSteamDeck ?? identifyAsSteamDeckRef.current;
      if (options?.preferCache !== false) {
        const cached = loadCachedEntitledResolutions(identifyAsSteamDeck);
        if (
          cached &&
          cached.userId === userId &&
          cached.membershipTier === session.user.membershipTier &&
          isCurrentLoad()
        ) {
          setEntitledResolutions(cached.entitledResolutions);
        }
      }

      const sub = await window.openNow.fetchSubscription({
        userId,
      });

      if (!isCurrentLoad()) {
        return;
      }

      setSubscriptionInfo(sub);
      setEntitledResolutions(sub.entitledResolutions);
      saveCachedEntitledResolutions({
        userId,
        membershipTier: sub.membershipTier,
        identifyAsSteamDeck,
        entitledResolutions: sub.entitledResolutions,
      });
    } catch (err) {
      console.warn("Failed to fetch subscription for settings:", err);
      if (isCurrentLoad()) {
        setSubscriptionInfo(null);
      }
    } finally {
      if (isCurrentLoad()) {
        setSubscriptionLoading(false);
      }
    }
  }, []);

  const handleChange = useCallback(
    <K extends keyof Settings>(key: K, value: Settings[K]): void => {
      const requestId = ++saveRequestRef.current;
      if (savedIndicatorTimerRef.current !== null) {
        window.clearTimeout(savedIndicatorTimerRef.current);
        savedIndicatorTimerRef.current = null;
      }
      setSavedIndicator(false);
      void onSettingChange(key, value)
        .then(async () => {
          showSavedIndicator(requestId);
          if (key === "identifyAsSteamDeck") {
            // Refresh after the setting is persisted so MES sees the new identity.
            // Keep the previous resolution list until the matching cache/MES result arrives.
            await loadSubscriptionData({
              identifyAsSteamDeck: value as boolean,
              preferCache: true,
            });
          }
        })
        .catch((error) => {
          if (requestId === saveRequestRef.current) {
            console.warn(`[Settings] Failed to save ${String(key)}:`, error);
          }
        });
    },
    [loadSubscriptionData, onSettingChange, showSavedIndicator],
  );

  useEffect(() => {
    void loadSubscriptionData();
    return () => {
      subscriptionLoadIdRef.current += 1;
    };
  }, [loadSubscriptionData]);

  const normalizedSettingsSearch = settingsSearch.trim().toLowerCase();
  const showAll = settingsSearchShowsAll;
  const tokenMatchesWord = (token: string, word: string): boolean => token === word || word.startsWith(token);
  const scopeMatchesSearch = (scopeId: SettingsSearchScopeId): boolean => {
    if (!showAll) {
      return true;
    }
    const terms = SETTINGS_SCOPE_SEARCH_TERMS[scopeId];
    const searchTokens = normalizedSettingsSearch.split(/[^a-z0-9]+/).filter((token) => token.length > 0);
    if (searchTokens.length === 0) {
      return true;
    }
    const searchableWords = Array.from(
      new Set(
        terms
          .join(" ")
          .toLowerCase()
          .split(/[^a-z0-9]+/)
          .filter((word) => word.length > 0),
      ),
    );
    return searchTokens.every((token) => searchableWords.some((word) => tokenMatchesWord(token, word)));
  };

  const showAccountStorage = showAll ? scopeMatchesSearch("account-storage") : activeSection === "account";
  const showAccount = showAccountStorage;
  const showStreamRegion = showAll ? scopeMatchesSearch("stream-region") : activeSection === "stream";
  const showStreamVideo = showAll ? scopeMatchesSearch("stream-video") : activeSection === "stream";
  const showStreamCodecDiagnostics = showAll ? scopeMatchesSearch("stream-codec-diagnostics") : activeSection === "stream";
  const showStream = showStreamRegion || showStreamVideo || showStreamCodecDiagnostics;
  const showNativeStreamer = showAll ? scopeMatchesSearch("native-streamer") : activeSection === "native-streamer";
  const showGame = showAll ? scopeMatchesSearch("game") : activeSection === "game";
  const showAudio = showAll ? scopeMatchesSearch("audio") : activeSection === "audio";
  const showInput = showAll ? scopeMatchesSearch("input") : activeSection === "input";
  const showInterface = showAll ? scopeMatchesSearch("interface") : activeSection === "interface";
  const showAbout = showAll ? scopeMatchesSearch("about") : activeSection === "about";
  const showThanks = showAll ? scopeMatchesSearch("thanks") : activeSection === "thanks";
  const hasAnySearchMatches = showAccount || showStream || showNativeStreamer || showGame || showAudio || showInput || showInterface || showAbout || showThanks;
  const shouldRenderSettingsSections = showAll || activeSection !== "thanks";

  return (
    <>
      <header className="settings-modal-header">
        <h1>{t("settings.title")}</h1>
        <div className="settings-modal-header-actions">
          <div className={`settings-saved ${savedIndicator ? "visible" : ""}`}>
            <Check size={14} />
            {t("settings.saved")}
          </div>
          <button
            type="button"
            className="settings-modal-close"
            onClick={onClose}
            title={t("app.actions.close")}
            aria-label={t("app.actions.close")}
          >
            <X size={18} />
          </button>
        </div>
      </header>

      <div className="settings-layout">
        <SettingsNav
          activeSection={activeSection}
          settingsSearch={settingsSearch}
          showAll={showAll}
          onSearchChange={setSettingsSearch}
          onSectionChange={setActiveSection}
        />

        <div ref={settingsContentRef} className="settings-content">
          {showAll && !hasAnySearchMatches ? (
            <section className="settings-section">
              <div className="settings-thanks-state settings-thanks-state--muted">
                <span>{t("settings.noMatches", { query: settingsSearch.trim() })}</span>
              </div>
            </section>
          ) : (
            <>
              {showThanks && <SettingsThanksSection />}
              {shouldRenderSettingsSections && (
                <>
                  {showAccount && (
                    <SettingsAccountSection
                      settings={settings}
                      showAll={showAll}
                      subscriptionInfo={subscriptionInfo}
                      subscriptionLoading={subscriptionLoading}
                    />
                  )}
                  {(showStream || streamOverlayBlocking) && (
                    <SettingsStreamSection
                      settings={settings}
                      regions={regions}
                      showAll={showAll}
                      showStreamRegion={showStream && showStreamRegion}
                      showStreamVideo={showStream && showStreamVideo}
                      showStreamCodecDiagnostics={showStream && showStreamCodecDiagnostics}
                      handleChange={handleChange}
                      handlePreview={handlePreview}
                      codecResults={codecResults}
                      codecTesting={codecTesting}
                      onRunCodecTest={onRunCodecTest}
                      entitledResolutions={entitledResolutions}
                      subscriptionInfoLoaded={subscriptionInfo !== null}
                      subscriptionLoading={subscriptionLoading}
                      onBlockingOverlayChange={setStreamOverlayBlocking}
                    />
                  )}
                  {(showNativeStreamer || nativeOverlayBlocking) && (
                    <SettingsNativeStreamerSection
                      settings={settings}
                      showAll={showAll}
                      showSection={showNativeStreamer}
                      handleChange={handleChange}
                      onBlockingOverlayChange={setNativeOverlayBlocking}
                    />
                  )}
                  {showGame && (
                    <SettingsGameSection
                      settings={settings}
                      showAll={showAll}
                      handleChange={handleChange}
                    />
                  )}
                  {showAudio && (
                    <SettingsAudioSection
                      settings={settings}
                      showAll={showAll}
                      handleChange={handleChange}
                    />
                  )}
                  {showInput && (
                    <SettingsInputSection
                      settings={settings}
                      showAll={showAll}
                      handleChange={handleChange}
                      handlePreview={handlePreview}
                    />
                  )}
                  {showInterface && (
                    <SettingsInterfaceSection
                      settings={settings}
                      showAll={showAll}
                      handleChange={handleChange}
                      handlePreview={handlePreview}
                      onSaved={markSaved}
                    />
                  )}
                  {showAbout && (
                    <SettingsAboutSection
                      settings={settings}
                      showAll={showAll}
                      handleChange={handleChange}
                      onOpenWhatsNew={onOpenWhatsNew}
                      onOpenFeedback={onOpenFeedback}
                    />
                  )}
                </>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
}
