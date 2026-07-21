import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, JSX } from "react";
import { AnimatePresence, m, useReducedMotion } from "motion/react";

import type {
  ActiveSessionInfo,
  AuthSession,
  DirectLaunchRequest,
  ExistingSessionStrategy,
  GameInfo,
  LoginProvider,
  MainToRendererSignalingEvent,
  NativeStreamerShortcutAction,
  ReleaseHighlightsPayload,
  SessionInfo,
  SessionStopRequest,
  Settings,
  SubscriptionInfo,
  SignalingConnectRequest,
  StreamSettings,
  StreamRegion,
  PrintedWasteQueueData,
  VideoShaderSettings,
} from "@shared/gfn";
import {
  buildNativeStreamerSessionContext,
  DEFAULT_KEYBOARD_LAYOUT,
  DEFAULT_VIDEO_SHADER_SETTINGS,
  getDefaultStreamPreferences,
  isSessionAdsRequired,
  resolveEntitledStreamProfile,
  SAFE_FALLBACK_STREAM_PROFILE,
} from "@shared/gfn";
import { GfnWebRtcClient } from "./platforms/gfn/webrtcClient";
import { formatShortcutForDisplay, isShortcutMatch, normalizeShortcut } from "./shortcuts";
import { dispatchStreamShortcutAction } from "./streamShortcutActions";
import { useElapsedSeconds } from "./utils/useElapsedSeconds";
import { useAuthSession } from "./hooks/useAuthSession";
import { useCatalogData } from "./hooks/useCatalogData";
import {
  ICE_DISCONNECTED_RECOVERY_GRACE_MS,
  RECOVERABLE_STREAM_STATUSES,
  SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS,
  SIGNALING_RECOVERY_STABLE_RESET_DELAY_MS,
  SIGNALING_REMOTE_ICE_GRACE_MS,
  isExpectedNativeSessionClose,
  readStreamClipboardText,
  sendStreamClipboardPaste,
  sleep,
  type SignalingRecoveryState,
} from "./hooks/useStreamSession";
import { useQueueAdRuntime } from "./hooks/useQueueAdRuntime";
import { usePlaytime } from "./utils/usePlaytime";
import { createStreamDiagnosticsStore, useStreamDiagnosticsSelector } from "./utils/streamDiagnosticsStore";
import type {
  LaunchErrorState,
  LocalSessionTimerWarningState,
  StreamLoadingStatus,
  StreamStatus,
  StreamWarningState,
} from "./lib/appTypes";
import { loadStoredCodecResults, saveStoredCodecResults, testCodecSupport, type CodecTestResult } from "./lib/codecDiagnostics";
import {
  createSyntheticDirectLaunchGame,
  findDirectLaunchTarget,
} from "./lib/directLaunch";
import {
  defaultVariantId,
  findSessionContextForAppId,
  getSelectedVariant,
  isNumericId,
  matchesGameSearch,
  mergeVariantSelections,
  parseNumericId,
  sortLibraryGames,
} from "./lib/gameCatalog";
import { chooseAccountLinked, getEpicOwnershipLaunchError, resolveInstallToPlayStorageRegionUrl } from "./lib/launchOwnership";
import { hasAnyEligiblePrintedWasteZone, isAllianceStreamingBaseUrl } from "./lib/printedWaste";
import {
  mergePolledSessionState,
  normalizeMembershipTier,
  shouldUseQueueAdPolling,
} from "./lib/queueAds";
import { clearRuntimeSnapshot, loadRuntimeSnapshot, saveRuntimeSnapshot, type RuntimeSnapshot } from "./lib/runtimeSnapshot";
import { getEnabledSessionProxyUrl } from "./lib/sessionProxy";
import {
  getSessionLimitSecondsForTier,
  getLocalSessionTimerWarning,
  hasCrossedWarningThreshold,
  shouldShowFreeTierSessionWarnings,
  warningMessage,
  warningTone,
} from "./lib/sessionWarnings";
import {
  isSessionInQueue,
  isSessionReadyForConnect,
  isStreamVideoReady,
  streamStatusToLoadingStage,
  toLaunchErrorState,
  toLoadingStatus,
} from "./lib/sessionState";
import { defaultDiagnostics, mergeNativeStreamStats } from "./lib/streamDiagnostics";
import { applyAccentColor, applyTheme, applyTranslucentUI } from "./lib/uiCustomization";
import { useTranslation } from "./i18n";

// UI Components
import { LoginScreen } from "./components/LoginScreen";
import { Navbar } from "./components/Navbar";
import { HomePage } from "./components/HomePage";
import { LibraryPage } from "./components/LibraryPage";
import { PageErrorBoundary } from "./components/PageErrorBoundary";
import { SettingsPage } from "./components/SettingsPage";
import { SettingsModalHost } from "./components/SettingsModalHost";
import { StreamLoading } from "./components/StreamLoading";
import { StreamView } from "./components/StreamView";
import { QueueServerSelectModal } from "./components/QueueServerSelectModal";
import { ReleaseHighlightsModal } from "./components/ReleaseHighlightsModal";
import { ErrorReportingConsentModal } from "./components/ErrorReportingConsentModal";
import { FeedbackModal } from "./components/FeedbackModal";
import { ModalSurface } from "./components/ui/ModalSurface";
import { overlayMotion, pageTransition, streamRevealTransition } from "./components/MotionProvider";
import { LazyShaderAtmosphere } from "./components/LazyShaderAtmosphere";
import { syncRendererTelemetry } from "./telemetry/posthog";

const DEFAULT_STREAM_PREFERENCES = getDefaultStreamPreferences();

type AppStyle = CSSProperties & {
  "--game-poster-scale"?: string;
};

function getAppStyle(posterSizeScale: number): AppStyle {
  return {
    "--game-poster-scale": String(posterSizeScale),
  };
}

function isNvidiaProvider(provider: LoginProvider | null | undefined): boolean {
  return (provider?.code ?? "").trim().toUpperCase() === "NVIDIA";
}

const SESSION_READY_POLL_INTERVAL_MS = 2000;
const SESSION_AD_POLL_INTERVAL_MS = 30000;
const PLAYTIME_RESYNC_INTERVAL_MS = 5 * 60 * 1000;
const FREE_TIER_30_MIN_WARNING_SECONDS = 30 * 60;
const FREE_TIER_15_MIN_WARNING_SECONDS = 15 * 60;
const FREE_TIER_FINAL_MINUTE_WARNING_SECONDS = 60;
const STREAM_WARNING_VISIBILITY_MS = 15 * 1000;

type AppPage = "home" | "library" | "settings";
type ExitPromptState = { open: boolean; gameTitle: string };

const isMac = navigator.platform.toLowerCase().includes("mac");

const DEFAULT_SHORTCUTS = {
  shortcutToggleStats: "F3",
  shortcutTogglePointerLock: "F8",
  shortcutToggleFullscreen: "F10",
  shortcutStopStream: "Ctrl+Shift+Q",
  shortcutToggleAntiAfk: "Ctrl+Shift+K",
  shortcutToggleMicrophone: "Ctrl+Shift+M",
  shortcutScreenshot: "F11",
  shortcutToggleRecording: "F12",
} as const;

export function App(): JSX.Element {
  const { locale, t } = useTranslation();
  const reducedMotion = useReducedMotion();

  // Navigation / settings / stream state below; auth + catalog come from hooks after deps are ready.

  // Navigation
  const [currentPage, setCurrentPage] = useState<AppPage>("home");
  const [pageBeforeSettings, setPageBeforeSettings] = useState<AppPage>("home");
  const [sessionFullscreen, setSessionFullscreenState] = useState(false);

  // Settings State
  const [settings, setSettings] = useState<Settings>({
    resolution: "1920x1080",
    aspectRatio: "16:9",
    posterSizeScale: 1.05,
    fps: 60,
    maxBitrateMbps: 75,
    recordingBitrateMbps: null,
    streamClientMode: "web",
    nativeStreamerBackend: "gstreamer",
    nativeVideoBackend: "auto",
    nativeStreamerExecutablePath: "",
    nativeCloudGsyncMode: "auto",
    nativeD3dFullscreenMode: "auto",
    nativeExternalRenderer: false,
    transportMode: "webrtc",
    showNativeStreamerStats: false,
    codec: DEFAULT_STREAM_PREFERENCES.codec,
    decoderPreference: "auto",
    encoderPreference: "auto",
    colorQuality: DEFAULT_STREAM_PREFERENCES.colorQuality,
    region: "",
    sessionProxyEnabled: false,
    sessionProxyUrl: "",
    clipboardPaste: false,
    enableGyroscopeControls: false,
    steamControllerCompatibilityMode: false,
    nativeCursorOverlay: true,
    mouseSensitivity: 1,
    mouseAcceleration: 1,
    shortcutToggleStats: DEFAULT_SHORTCUTS.shortcutToggleStats,
    shortcutTogglePointerLock: DEFAULT_SHORTCUTS.shortcutTogglePointerLock,
    shortcutToggleFullscreen: DEFAULT_SHORTCUTS.shortcutToggleFullscreen,
    shortcutStopStream: DEFAULT_SHORTCUTS.shortcutStopStream,
    shortcutToggleAntiAfk: DEFAULT_SHORTCUTS.shortcutToggleAntiAfk,
    shortcutToggleMicrophone: DEFAULT_SHORTCUTS.shortcutToggleMicrophone,
    shortcutScreenshot: DEFAULT_SHORTCUTS.shortcutScreenshot,
    shortcutToggleRecording: DEFAULT_SHORTCUTS.shortcutToggleRecording,
    microphoneMode: "disabled",
    microphoneDeviceId: "",
    hideStreamButtons: false,
    showAntiAfkIndicator: true,
    showStatsOnLaunch: false,
    hideServerSelector: false,
    appAccentColor: "green",
    appTheme: "auto",
    translucentUI: false,
    controllerMode: false,
    launchInConsoleMode: false,
    autoFullScreen: false,
    favoriteGameIds: [],
    sessionCounterEnabled: false,
    showSessionTimeRemainingInStatsOverlay: false,
    sessionClockShowEveryMinutes: 60,
    sessionClockShowDurationSeconds: 30,
    windowWidth: 1400,
    windowHeight: 900,
    keyboardLayout: DEFAULT_KEYBOARD_LAYOUT,
    gameLanguage: "en_US",
    enablePersistingInGameSettings: false,
    enableL4S: false,
    identifyAsSteamDeck: false,
    enableCloudGsync: false,
    discordRichPresence: false,
    autoCheckForUpdates: true,
    updateChannel: "stable",
    lastSeenReleaseHighlightsVersion: "",
    videoShader: { ...DEFAULT_VIDEO_SHADER_SETTINGS },
    errorReportingConsent: "unset",
    telemetryInstallId: "",
  });
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [releaseHighlightsPayload, setReleaseHighlightsPayload] = useState<ReleaseHighlightsPayload | null>(null);
  const [releaseHighlightsIsAuto, setReleaseHighlightsIsAuto] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [feedbackSurfacePresent, setFeedbackSurfacePresent] = useState(false);
  const [consentSurfacePresent, setConsentSurfacePresent] = useState(false);
  const activeSessionProxyUrl = useMemo(
    () => getEnabledSessionProxyUrl(settings),
    [settings.sessionProxyEnabled, settings.sessionProxyUrl],
  );
  const [codecResults, setCodecResults] = useState<CodecTestResult[] | null>(() => loadStoredCodecResults());
  const [codecTesting, setCodecTesting] = useState(false);
  const diagnosticsStoreRef = useRef<ReturnType<typeof createStreamDiagnosticsStore> | null>(null);
  const diagnosticsStore =
    diagnosticsStoreRef.current ?? (diagnosticsStoreRef.current = createStreamDiagnosticsStore(defaultDiagnostics()));
  const diagnosticsVideoReady = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.nativeRendererActive || stats.framesDecoded > 0,
  );

  // Stream State
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [streamStatus, setStreamStatus] = useState<StreamStatus>("idle");
  const [showStatsOverlay, setShowStatsOverlay] = useState(false);
  const [antiAfkEnabled, setAntiAfkEnabled] = useState(false);
  const [antiAfkAckNonce, setAntiAfkAckNonce] = useState(0);
  const [nativeInputCaptureActive, setNativeInputCaptureActive] = useState(false);
  const [nativeInputBridgeReady, setNativeInputBridgeReady] = useState(false);
  const [exitPrompt, setExitPrompt] = useState<ExitPromptState>({ open: false, gameTitle: t("app.labels.game") });
  const [streamingGame, setStreamingGame] = useState<GameInfo | null>(null);
  const [streamingStore, setStreamingStore] = useState<string | null>(null);
  const [queuePosition, setQueuePosition] = useState<number | undefined>();
  const [navbarActiveSession, setNavbarActiveSession] = useState<ActiveSessionInfo | null>(null);
  const [isResumingNavbarSession, setIsResumingNavbarSession] = useState(false);
  const [isTerminatingNavbarSession, setIsTerminatingNavbarSession] = useState(false);
  const [launchError, setLaunchError] = useState<LaunchErrorState | null>(null);
  const [settingsFocusSection, setSettingsFocusSection] = useState<"account" | undefined>();
  const [pendingDirectLaunchRequest, setPendingDirectLaunchRequest] = useState<DirectLaunchRequest | null>(null);
  // Argument-driven launches always use the console (big picture) experience for this run,
  // without persisting the user's Controller Mode setting.
  const [directLaunchConsoleMode, setDirectLaunchConsoleMode] = useState(false);
  const [queueModalGame, setQueueModalGame] = useState<GameInfo | null>(null);
  const [queueModalData, setQueueModalData] = useState<PrintedWasteQueueData | null>(null);
  const [sessionStartedAtMs, setSessionStartedAtMs] = useState<number | null>(null);
  const [remoteStreamWarning, setRemoteStreamWarning] = useState<StreamWarningState | null>(null);
  const [localSessionTimerWarning, setLocalSessionTimerWarning] = useState<LocalSessionTimerWarningState | null>(null);
  const previousFreeTierRemainingSecondsRef = useRef<number | null>(null);

  const { playtime, startSession: startPlaytimeSession, endSession: endPlaytimeSession } = usePlaytime();
  const sessionElapsedSeconds = useElapsedSeconds(sessionStartedAtMs, streamStatus === "streaming");
  const isStreaming = streamStatus === "streaming";
  // freeTier/session-limit derived state is computed after auth/catalog hooks


  const codecTestPromiseRef = useRef<Promise<CodecTestResult[] | null> | null>(null);
  const codecStartupTestAttemptedRef = useRef(false);
  const navbarSessionActionInFlightRef = useRef<"resume" | "terminate" | null>(null);
  const nativeStreamingRef = useRef(false);
  const handleStreamShortcutActionRef = useRef<((action: NativeStreamerShortcutAction) => void) | null>(null);
  const streamingGameRef = useRef<GameInfo | null>(null);

  useEffect(() => {
    streamingGameRef.current = streamingGame;
  }, [streamingGame]);

  const resetStatsOverlayToPreference = useCallback((): void => {
    setShowStatsOverlay(settings.showStatsOnLaunch);
  }, [settings.showStatsOnLaunch]);

  const runCodecTest = useCallback(async (): Promise<void> => {
    if (codecTestPromiseRef.current) {
      await codecTestPromiseRef.current;
      return;
    }

    const testPromise = (async (): Promise<CodecTestResult[] | null> => {
      setCodecTesting(true);
      try {
        const results = await testCodecSupport();
        setCodecResults(results);
        saveStoredCodecResults(results);
        return results;
      } catch (error) {
        console.error("Codec test failed:", error);
        return null;
      } finally {
        setCodecTesting(false);
        codecTestPromiseRef.current = null;
      }
    })();

    codecTestPromiseRef.current = testPromise;
    await testPromise;
  }, []);

  const [streamVolume, setStreamVolume] = useState(1);
  const [streamMicLevel, setStreamMicLevel] = useState(1);
  // Refs
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const accountConfirmRestoreFocusRef = useRef<HTMLElement | null>(null);
  const logoutConfirmCancelRef = useRef<HTMLButtonElement | null>(null);
  const removeAccountConfirmCancelRef = useRef<HTMLButtonElement | null>(null);
  const [videoElementHasFrame, setVideoElementHasFrame] = useState(false);
  const [streamRevealPhase, setStreamRevealPhase] = useState<"covered" | "revealing" | "revealed">("covered");
  const [streamSurfacePresent, setStreamSurfacePresent] = useState(false);
  const [launchSurfacePresent, setLaunchSurfacePresent] = useState(false);
  const [settingsSurfacePresent, setSettingsSurfacePresent] = useState(false);
  const [navbarOverlayBlocking, setNavbarOverlayBlocking] = useState(false);
  const [logoutConfirmSurfacePresent, setLogoutConfirmSurfacePresent] = useState(false);
  const [removeAccountConfirmSurfacePresent, setRemoveAccountConfirmSurfacePresent] = useState(false);
  const [releaseHighlightsSurfacePresent, setReleaseHighlightsSurfacePresent] = useState(false);
  const streamRevealComplete = streamRevealPhase === "revealed";
  const clientRef = useRef<GfnWebRtcClient | null>(null);
  const isStreamingRef = useRef(streamStatus === "streaming");

  useEffect(() => {
    isStreamingRef.current = streamStatus === "streaming";
  }, [streamStatus]);

  useEffect(() => {
    if (streamStatus !== "streaming") {
      setVideoElementHasFrame(false);
      setStreamRevealPhase("covered");
    }

    if (streamStatus === "idle") return undefined;

    const video = videoRef.current;
    if (!video) return undefined;

    const syncVideoFrame = (): void => {
      setVideoElementHasFrame(video.videoWidth > 0 && video.videoHeight > 0);
    };

    syncVideoFrame();
    video.addEventListener("loadeddata", syncVideoFrame);
    video.addEventListener("playing", syncVideoFrame);
    video.addEventListener("resize", syncVideoFrame);
    return () => {
      video.removeEventListener("loadeddata", syncVideoFrame);
      video.removeEventListener("playing", syncVideoFrame);
      video.removeEventListener("resize", syncVideoFrame);
    };
  }, [streamStatus]);

  const streamVideoReady = isStreamVideoReady(streamStatus, diagnosticsVideoReady, videoElementHasFrame);

  useEffect(() => {
    if (streamStatus === "idle" || !streamVideoReady || streamRevealPhase !== "covered") return;
    setStreamRevealPhase(reducedMotion ? "revealed" : "revealing");
  }, [reducedMotion, streamRevealPhase, streamStatus, streamVideoReady]);

  useEffect(() => {
    if (streamStatus !== "idle") setStreamSurfacePresent(true);
  }, [streamStatus]);

  useEffect(() => {
    if (streamStatus !== "idle" || launchError) setLaunchSurfacePresent(true);
  }, [launchError, streamStatus]);

  useEffect(() => {
    if (currentPage === "settings") setSettingsSurfacePresent(true);
  }, [currentPage]);

  useEffect(() => {
    if (releaseHighlightsPayload) setReleaseHighlightsSurfacePresent(true);
  }, [releaseHighlightsPayload]);

  useEffect(() => {
    if (feedbackOpen) setFeedbackSurfacePresent(true);
  }, [feedbackOpen]);

  useEffect(() => {
    if (settingsLoaded && settings.errorReportingConsent === "unset") {
      setConsentSurfacePresent(true);
    }
  }, [settings.errorReportingConsent, settingsLoaded]);

  useEffect(() => {
    if (!settingsLoaded) {
      return;
    }
    void syncRendererTelemetry(settings).catch((error) => {
      console.warn("[Telemetry] Failed to sync renderer PostHog:", error);
    });
    // Only re-sync when consent or install identity changes — not on every settings keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional narrow deps
  }, [settings.errorReportingConsent, settings.telemetryInstallId, settingsLoaded]);

  useEffect(() => {
    if (streamStatus === "streaming" && audioRef.current) {
      setStreamVolume(audioRef.current.volume);
    }
  }, [streamStatus]);
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = streamVolume;
    }
    clientRef.current?.setOutputVolume(streamVolume);
  }, [streamVolume]);
  const sessionRef = useRef<SessionInfo | null>(null);
  const regionsRequestRef = useRef(0);
  const launchInFlightRef = useRef(false);
  const directLaunchAttemptIdRef = useRef<string | null>(null);
  const handledDirectLaunchIdsRef = useRef<Set<string>>(new Set());
  const runtimeSnapshotRef = useRef<RuntimeSnapshot | null>(loadRuntimeSnapshot());
  /** Joins concurrent claim/resume calls for the same Cloud session id (single CloudMatch RESUME + signaling). */
  const claimResumePromisesRef = useRef<Map<string, Promise<void>>>(new Map());
  const launchAbortRef = useRef(false);
  const discordStreamingActivitySessionRef = useRef<string | null>(null);
  const streamStatusRef = useRef<StreamStatus>(streamStatus);
  const nativeInputProtocolVersionRef = useRef<number | null>(null);
  const stableRecoveryResetTimerRef = useRef<number | null>(null);
  const remoteIceGraceTimerRef = useRef<number | null>(null);
  const remoteIceSeenForSessionRef = useRef<string | null>(null);
  const remoteIceRecoveryGenerationRef = useRef<number | null>(null);
  const awaitingRecoveryRemoteIceRef = useRef(false);
  const appUnloadingRef = useRef(false);
  const hasConfirmedRemoteIceRef = useRef(false);
  const latestIceConnectionStateRef = useRef<RTCIceConnectionState>("new");
  const iceDisconnectedRecoveryTimerRef = useRef<number | null>(null);
  const pendingControlledDisconnectsRef = useRef(0);
  const signalingRecoveryRef = useRef<SignalingRecoveryState>({
    attemptCount: 0,
    inFlight: null,
    explicitShutdown: false,
    appId: null,
    generation: 0,
  });
  const exitPromptResolverRef = useRef<((confirmed: boolean) => void) | null>(null);



  type CatalogOps = {
    hydrateCatalogSnapshot: (session: AuthSession, proxyUrl?: string) => string | null;
    loadSessionRuntimeData: (session: AuthSession, options?: { background?: boolean; proxyUrl?: string }) => Promise<void>;
    clearSessionCatalog: (mode: "logout" | "no-session", options?: { clearFeatured?: boolean }) => void;
    resetStorePanels: () => void;
    setVariantByGameId: (value: Record<string, string> | ((prev: Record<string, string>) => Record<string, string>)) => void;
  };
  const catalogOpsRef = useRef<CatalogOps>({
    hydrateCatalogSnapshot: () => null,
    loadSessionRuntimeData: async () => {},
    clearSessionCatalog: () => {},
    resetStorePanels: () => {},
    setVariantByGameId: (() => {}) as CatalogOps['setVariantByGameId'],
  });
  const resetLaunchRuntimeRef = useRef<(options?: { keepLaunchError?: boolean; keepStreamingContext?: boolean }) => void>(() => {});
  const refreshNavbarActiveSessionRef = useRef<(sessionOverride?: AuthSession) => Promise<void>>(async () => {});

  const onBootstrapSettings = useCallback((loadedSettings: Settings, _sessionProxyUrl: string | undefined) => {
    setSettings(loadedSettings);
    setShowStatsOverlay(loadedSettings.showStatsOnLaunch);
    setSettingsLoaded(true);
  }, []);

  const onBootstrapVariantSelections = useCallback((selections: Record<string, string>) => {
    catalogOpsRef.current.setVariantByGameId(selections);
  }, []);

  const onBootstrapRuntimeSnapshot = useCallback((snapshot: RuntimeSnapshot | null) => {
    runtimeSnapshotRef.current = snapshot;
    if (snapshot?.recoveryAppId !== null && snapshot?.recoveryAppId !== undefined) {
      signalingRecoveryRef.current.appId = snapshot.recoveryAppId;
    }
  }, []);

  const {
    authSession,
    savedAccounts,
    providers,
    providerIdpId,
    setProviderIdpId,
    isLoggingIn,
    activeLoginMode,
    loginError,
    setLoginError,
    qrLoginChallenge,
    isInitializing,
    startupStatusMessage,
    startupRefreshNotice,
    removeAccountConfirmOpen,
    setRemoveAccountConfirmOpen,
    logoutConfirmOpen,
    setLogoutConfirmOpen,
    selectedProvider,
    handleLogin,
    handleQrLogin,
    handleCancelQrLogin,
    handleSwitchAccount,
    handleRemoveAccount,
    confirmRemoveAccount,
    handleAddAccount,
    confirmLogout,
    handleLogout,
    accountToRemoveDisplayName,
    setAccountToRemove,
  } = useAuthSession({
    t,
    loadSessionRuntimeData: (session, options) => catalogOpsRef.current.loadSessionRuntimeData(session, options),
    hydrateCatalogSnapshot: (session, proxyUrl) => catalogOpsRef.current.hydrateCatalogSnapshot(session, proxyUrl),
    clearSessionCatalog: (mode, options) => catalogOpsRef.current.clearSessionCatalog(mode, options),
    resetLaunchRuntime: (options) => resetLaunchRuntimeRef.current(options),
    refreshNavbarActiveSession: (sessionOverride) => refreshNavbarActiveSessionRef.current(sessionOverride),
    onBootstrapSettings,
    onBootstrapVariantSelections,
    onBootstrapRuntimeSnapshot,
    setCurrentPage,
    setNavbarActiveSession,
    setIsResumingNavbarSession,
  });

  useEffect(() => {
    if (logoutConfirmOpen) setLogoutConfirmSurfacePresent(true);
  }, [logoutConfirmOpen]);

  useEffect(() => {
    if (removeAccountConfirmOpen) setRemoveAccountConfirmSurfacePresent(true);
  }, [removeAccountConfirmOpen]);

  const effectiveControllerModeForCatalog = settings.controllerMode || directLaunchConsoleMode;
  const effectiveStreamingBaseUrlForCatalog = settings.region.trim()
    ? settings.region
    : (selectedProvider?.streamingServiceUrl ?? "");

  const {
    games,
    featuredGames,
    storePanels,
    libraryGames,
    searchQuery,
    setSearchQuery,
    selectedGameId,
    setSelectedGameId,
    variantByGameId,
    setVariantByGameId,
    isLoadingCatalog,
    isLoadingLibrary,
    isLoadingStorePanels,
    catalogFilterGroups,
    catalogSortOptions,
    catalogSelectedSortId,
    setCatalogSelectedSortId,
    catalogSelectedFilterIds,
    catalogTotalCount,
    catalogSupportedCount,
    markOwnedInFlightByVariantId,
    catalogActionNotice,
    regions,
    setRegions,
    subscriptionInfo,
    setSubscriptionInfo,
    storePanelGames,
    allKnownGames,
    resetStorePanels,
    hydrateCatalogSnapshot,
    loadSessionRuntimeData,
    clearSessionCatalog,
    handleMarkGameOwned,
    handleSelectGameVariant,
    handleToggleCatalogFilter,
    loadSubscriptionInfo,
  } = useCatalogData({
    authSession,
    activeSessionProxyUrl,
    effectiveStreamingBaseUrl: effectiveStreamingBaseUrlForCatalog,
    currentPage,
    effectiveControllerMode: effectiveControllerModeForCatalog,
    isInitializing,
    t,
  });

  catalogOpsRef.current = {
    hydrateCatalogSnapshot,
    loadSessionRuntimeData,
    clearSessionCatalog,
    resetStorePanels,
    setVariantByGameId,
  };

  const freeTierSessionWarningsActive =
    isStreaming && sessionStartedAtMs !== null && shouldShowFreeTierSessionWarnings(subscriptionInfo);
  const sessionLimitTier = useMemo(() => {
    const subscriptionTier = normalizeMembershipTier(subscriptionInfo?.membershipTier);
    const authTier = normalizeMembershipTier(authSession?.user.membershipTier);
    return subscriptionTier ?? authTier;
  }, [authSession?.user.membershipTier, subscriptionInfo?.membershipTier]);
  const sessionLimitSeconds = getSessionLimitSecondsForTier(sessionLimitTier);
  const sessionTimeRemainingSeconds = isStreaming && sessionStartedAtMs !== null && sessionLimitSeconds !== null
    ? Math.max(0, sessionLimitSeconds - sessionElapsedSeconds)
    : null;
  const freeTierSessionRemainingSeconds = freeTierSessionWarningsActive
    ? sessionTimeRemainingSeconds
    : null;
  const visibleLocalSessionTimerWarning = useMemo(() => {
    if (localSessionTimerWarning === null || freeTierSessionRemainingSeconds === null) {
      return null;
    }

    return getLocalSessionTimerWarning(t, localSessionTimerWarning.stage, freeTierSessionRemainingSeconds);
  }, [freeTierSessionRemainingSeconds, localSessionTimerWarning, locale, t]);
  const streamWarning = useMemo(() => {
    if (visibleLocalSessionTimerWarning?.tone === "critical") {
      return visibleLocalSessionTimerWarning;
    }
    return remoteStreamWarning ?? visibleLocalSessionTimerWarning;
  }, [remoteStreamWarning, visibleLocalSessionTimerWarning]);

  const queueDirectLaunchRequest = useCallback((request: DirectLaunchRequest | null): void => {
    if (!request || handledDirectLaunchIdsRef.current.has(request.id)) return;
    setDirectLaunchConsoleMode(true);
    setPendingDirectLaunchRequest((previous) => previous?.id === request.id ? previous : request);
  }, []);

  useEffect(() => {
    const unsubscribe = window.openNow.onDirectLaunchRequest(queueDirectLaunchRequest);
    void window.openNow.getPendingDirectLaunchRequest()
      .then(queueDirectLaunchRequest)
      .catch((error) => {
        console.warn("Failed to read pending direct launch request:", error);
      });
    return unsubscribe;
  }, [queueDirectLaunchRequest]);

  // Subscribe to automatic release-highlights events pushed from main process
  useEffect(() => {
    const unsubscribe = window.openNow.onReleaseHighlightsShow((payload) => {
      setReleaseHighlightsPayload(payload);
      setReleaseHighlightsIsAuto(true);
    });
    return unsubscribe;
  }, []);

  const resetLaunchRuntime = useCallback((options?: {
    keepLaunchError?: boolean;
    keepStreamingContext?: boolean;
  }): void => {
    if (stableRecoveryResetTimerRef.current !== null) {
      window.clearTimeout(stableRecoveryResetTimerRef.current);
      stableRecoveryResetTimerRef.current = null;
    }
    if (remoteIceGraceTimerRef.current !== null) {
      window.clearTimeout(remoteIceGraceTimerRef.current);
      remoteIceGraceTimerRef.current = null;
    }
    if (iceDisconnectedRecoveryTimerRef.current !== null) {
      window.clearTimeout(iceDisconnectedRecoveryTimerRef.current);
      iceDisconnectedRecoveryTimerRef.current = null;
    }
    remoteIceSeenForSessionRef.current = null;
    remoteIceRecoveryGenerationRef.current = null;
    awaitingRecoveryRemoteIceRef.current = false;
    hasConfirmedRemoteIceRef.current = false;
    latestIceConnectionStateRef.current = "new";
    pendingControlledDisconnectsRef.current = 0;
    discordStreamingActivitySessionRef.current = null;
    signalingRecoveryRef.current.attemptCount = 0;
    signalingRecoveryRef.current.inFlight = null;
    signalingRecoveryRef.current.appId = null;
    setSession(null);
    setStreamStatus("idle");
    setQueuePosition(undefined);
    setSessionStartedAtMs(null);
    setRemoteStreamWarning(null);
    setLocalSessionTimerWarning(null);
    resetStatsOverlayToPreference();
    nativeStreamingRef.current = false;
    window.openNow.notifyNativeInputModeChange(false, false);
    diagnosticsStore.set(defaultDiagnostics());

    if (!options?.keepStreamingContext) {
      setStreamingGame(null);
      setStreamingStore(null);
    }

    if (!options?.keepLaunchError) {
      setLaunchError(null);
    }

    // Clear Discord activity when returning to idle state
    if (settings.discordRichPresence) {
      void window.openNow.clearDiscordActivity();
    }
    runtimeSnapshotRef.current = null;
    clearRuntimeSnapshot();
  }, [diagnosticsStore, resetStatsOverlayToPreference, settings.discordRichPresence]);

  resetLaunchRuntimeRef.current = resetLaunchRuntime;

  const markDiscordStreamStarted = useCallback((): void => {
    if (!settings.discordRichPresence) {
      return;
    }

    const activeSession = sessionRef.current;
    if (!activeSession || discordStreamingActivitySessionRef.current === activeSession.sessionId) {
      return;
    }

    const gameName = (streamingGameRef.current?.title || activeSession.appId || "Game").trim();
    discordStreamingActivitySessionRef.current = activeSession.sessionId;
    void window.openNow.setDiscordActivity({
      gameName,
      kind: "streaming",
      appId: activeSession.appId,
      startTimestampMs: Date.now(),
    });
  }, [settings.discordRichPresence]);

  // Console shell is active when the user enabled Controller Mode or the app was
  // launched with a direct-launch argument (frontend / big picture usage).
  const effectiveControllerMode = settings.controllerMode || directLaunchConsoleMode;

  const buildCurrentStreamSettings = useCallback((subscriptionOverride?: SubscriptionInfo | null): StreamSettings => {
    const currentSubscription = subscriptionOverride === undefined ? subscriptionInfo : subscriptionOverride;
    const entitledProfile = resolveEntitledStreamProfile(currentSubscription?.entitledResolutions ?? [], {
      resolution: settings.resolution,
      fps: settings.fps,
    });
    const streamProfile = entitledProfile ?? SAFE_FALLBACK_STREAM_PROFILE;

    return {
      resolution: streamProfile.resolution,
      fps: streamProfile.fps,
      maxBitrateMbps: settings.maxBitrateMbps,
      codec: settings.codec,
      colorQuality: settings.colorQuality,
      keyboardLayout: settings.keyboardLayout,
      gameLanguage: settings.gameLanguage,
      enableL4S: settings.enableL4S,
      enableCloudGsync: settings.enableCloudGsync,
      clientMode: settings.streamClientMode,
      nativeStreamerBackend: "gstreamer",
      transportMode: "webrtc",
      nativeCloudGsyncMode: settings.nativeCloudGsyncMode,
      nativeTransitionDiagnostics: settings.nativeTransitionDiagnostics,
      appLaunchMode:
        settings.controllerMode || settings.launchInConsoleMode || directLaunchConsoleMode
          ? "gamepadFriendly"
          : "default",
    };
  }, [
    settings.codec,
    settings.colorQuality,
    settings.controllerMode,
    directLaunchConsoleMode,
    settings.enableCloudGsync,
    settings.enableL4S,
    settings.fps,
    settings.gameLanguage,
    settings.keyboardLayout,
    settings.launchInConsoleMode,
    settings.maxBitrateMbps,
    settings.nativeCloudGsyncMode,
    settings.nativeTransitionDiagnostics,
    settings.resolution,
    settings.streamClientMode,
    subscriptionInfo?.entitledResolutions,
  ]);

  const warmNativeStreamerForLaunch = useCallback((): void => {
    if (settings.streamClientMode !== "native") {
      return;
    }

    void window.openNow.getNativeStreamerStatus()
      .then((status) => {
        if (status.detected) {
          console.log("[NativeStreamer] Launch warm-up ready:", status.message);
        } else {
          console.warn("[NativeStreamer] Launch warm-up did not detect native streamer:", status.message);
        }
      })
      .catch((error) => {
        console.warn("[NativeStreamer] Launch warm-up failed:", error);
      });
  }, [settings.streamClientMode]);

  const resetSignalingRecoveryState = useCallback((options?: {
    keepExplicitShutdown?: boolean;
  }): void => {
    if (stableRecoveryResetTimerRef.current !== null) {
      window.clearTimeout(stableRecoveryResetTimerRef.current);
      stableRecoveryResetTimerRef.current = null;
    }
    if (remoteIceGraceTimerRef.current !== null) {
      window.clearTimeout(remoteIceGraceTimerRef.current);
      remoteIceGraceTimerRef.current = null;
    }
    if (iceDisconnectedRecoveryTimerRef.current !== null) {
      window.clearTimeout(iceDisconnectedRecoveryTimerRef.current);
      iceDisconnectedRecoveryTimerRef.current = null;
    }
    remoteIceSeenForSessionRef.current = null;
    remoteIceRecoveryGenerationRef.current = null;
    awaitingRecoveryRemoteIceRef.current = false;
    hasConfirmedRemoteIceRef.current = false;
    latestIceConnectionStateRef.current = "new";
    pendingControlledDisconnectsRef.current = 0;
    signalingRecoveryRef.current.generation += 1;
    signalingRecoveryRef.current.attemptCount = 0;
    signalingRecoveryRef.current.inFlight = null;
    signalingRecoveryRef.current.appId = null;
    if (!options?.keepExplicitShutdown) {
      signalingRecoveryRef.current.explicitShutdown = false;
    }
  }, []);

  const markExplicitSignalingShutdown = useCallback((): void => {
    if (stableRecoveryResetTimerRef.current !== null) {
      window.clearTimeout(stableRecoveryResetTimerRef.current);
      stableRecoveryResetTimerRef.current = null;
    }
    if (remoteIceGraceTimerRef.current !== null) {
      window.clearTimeout(remoteIceGraceTimerRef.current);
      remoteIceGraceTimerRef.current = null;
    }
    if (iceDisconnectedRecoveryTimerRef.current !== null) {
      window.clearTimeout(iceDisconnectedRecoveryTimerRef.current);
      iceDisconnectedRecoveryTimerRef.current = null;
    }
    remoteIceSeenForSessionRef.current = null;
    remoteIceRecoveryGenerationRef.current = null;
    awaitingRecoveryRemoteIceRef.current = false;
    hasConfirmedRemoteIceRef.current = false;
    latestIceConnectionStateRef.current = "new";
    pendingControlledDisconnectsRef.current = 0;
    signalingRecoveryRef.current.generation += 1;
    signalingRecoveryRef.current.explicitShutdown = true;
    signalingRecoveryRef.current.inFlight = null;
  }, []);

  const isRecoveryGenerationCurrent = useCallback((generation: number): boolean => {
    const state = signalingRecoveryRef.current;
    return state.generation === generation && !state.explicitShutdown;
  }, []);

  const scheduleStableRecoveryReset = useCallback((sessionId: string): void => {
    if (stableRecoveryResetTimerRef.current !== null) {
      window.clearTimeout(stableRecoveryResetTimerRef.current);
      stableRecoveryResetTimerRef.current = null;
    }

    stableRecoveryResetTimerRef.current = window.setTimeout(() => {
      stableRecoveryResetTimerRef.current = null;
      const activeSessionId = sessionRef.current?.sessionId;
      if (
        streamStatusRef.current !== "streaming"
        || !activeSessionId
        || activeSessionId !== sessionId
      ) {
        return;
      }
      console.log(
        `[Recovery] Stream remained stable for ${SIGNALING_RECOVERY_STABLE_RESET_DELAY_MS}ms; resetting recovery budget`,
      );
      resetSignalingRecoveryState({ keepExplicitShutdown: true });
    }, SIGNALING_RECOVERY_STABLE_RESET_DELAY_MS);
  }, [resetSignalingRecoveryState]);

  const disconnectSignalingControlled = useCallback(async (): Promise<void> => {
    pendingControlledDisconnectsRef.current += 1;
    await window.openNow.disconnectSignaling().catch(() => {});
  }, []);

  // Session ref sync
  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  useEffect(() => {
    const streamIsActive = streamStatus !== "idle" || session !== null || navbarActiveSession !== null;
    if (!streamIsActive) {
      runtimeSnapshotRef.current = null;
      clearRuntimeSnapshot();
      return;
    }

    const snapshot: RuntimeSnapshot = {
      version: 1,
      updatedAt: Date.now(),
      streamStatus,
      sessionId: session?.sessionId ?? navbarActiveSession?.sessionId ?? null,
      sessionAppId:
        (Number.isFinite(signalingRecoveryRef.current.appId ?? NaN) ? signalingRecoveryRef.current.appId : null) ??
        (navbarActiveSession ? navbarActiveSession.appId : null),
      streamingGameId: streamingGame?.id ?? null,
      streamingStore: streamingStore ?? null,
      recoveryAppId: signalingRecoveryRef.current.appId,
      resumeContext: session
        ? {
          sessionId: session.sessionId,
          serverIp: session.serverIp,
          streamingBaseUrl: session.streamingBaseUrl,
          signalingServer: session.signalingServer,
          signalingUrl: session.signalingUrl,
          appId: Number.isFinite(signalingRecoveryRef.current.appId ?? NaN) ? signalingRecoveryRef.current.appId ?? undefined : undefined,
          appLaunchMode: session.appLaunchMode,
          enablePersistingInGameSettings: session.enablePersistingInGameSettings,
          clientId: session.clientId,
          deviceId: session.deviceId,
        }
        : (navbarActiveSession?.sessionId && navbarActiveSession.serverIp)
          ? {
            sessionId: navbarActiveSession.sessionId,
            serverIp: navbarActiveSession.serverIp,
            streamingBaseUrl: navbarActiveSession.streamingBaseUrl,
            signalingUrl: navbarActiveSession.signalingUrl,
            appId: Number.isFinite(navbarActiveSession.appId) ? navbarActiveSession.appId : undefined,
            appLaunchMode: navbarActiveSession.appLaunchMode,
            enablePersistingInGameSettings: navbarActiveSession.enablePersistingInGameSettings,
          }
          : null,
    };

    runtimeSnapshotRef.current = snapshot;
    saveRuntimeSnapshot(snapshot);
  }, [navbarActiveSession, session, streamStatus, streamingGame?.id, streamingStore]);

  const persistRuntimeSnapshotNow = useCallback((): void => {
    const latestSession = sessionRef.current;
    const latestNavbarSession = navbarActiveSession;
    const hasActiveContext =
      streamStatusRef.current !== "idle" || latestSession !== null || latestNavbarSession !== null;
    if (!hasActiveContext) {
      runtimeSnapshotRef.current = null;
      clearRuntimeSnapshot();
      return;
    }

    const snapshot: RuntimeSnapshot = {
      version: 1,
      updatedAt: Date.now(),
      streamStatus: streamStatusRef.current,
      sessionId: latestSession?.sessionId ?? latestNavbarSession?.sessionId ?? null,
      sessionAppId:
        (Number.isFinite(signalingRecoveryRef.current.appId ?? NaN) ? signalingRecoveryRef.current.appId : null) ??
        (latestNavbarSession ? latestNavbarSession.appId : null),
      streamingGameId: streamingGame?.id ?? null,
      streamingStore: streamingStore ?? null,
      recoveryAppId: signalingRecoveryRef.current.appId,
        resumeContext: latestSession
          ? {
            sessionId: latestSession.sessionId,
            serverIp: latestSession.serverIp,
            streamingBaseUrl: latestSession.streamingBaseUrl,
            signalingServer: latestSession.signalingServer,
            signalingUrl: latestSession.signalingUrl,
            appId: Number.isFinite(signalingRecoveryRef.current.appId ?? NaN) ? signalingRecoveryRef.current.appId ?? undefined : undefined,
            appLaunchMode: latestSession.appLaunchMode,
            enablePersistingInGameSettings: latestSession.enablePersistingInGameSettings,
            clientId: latestSession.clientId,
            deviceId: latestSession.deviceId,
          }
          : (latestNavbarSession?.sessionId && latestNavbarSession.serverIp)
            ? {
              sessionId: latestNavbarSession.sessionId,
              serverIp: latestNavbarSession.serverIp,
              streamingBaseUrl: latestNavbarSession.streamingBaseUrl,
              signalingUrl: latestNavbarSession.signalingUrl,
              appId: Number.isFinite(latestNavbarSession.appId) ? latestNavbarSession.appId : undefined,
              appLaunchMode: latestNavbarSession.appLaunchMode,
              enablePersistingInGameSettings: latestNavbarSession.enablePersistingInGameSettings,
            }
          : null,
    };

    runtimeSnapshotRef.current = snapshot;
    saveRuntimeSnapshot(snapshot);
  }, [navbarActiveSession, streamingGame?.id, streamingStore]);

  useEffect(() => {
    const onBeforeUnload = (): void => {
      appUnloadingRef.current = true;
      persistRuntimeSnapshotNow();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [persistRuntimeSnapshotNow]);

  // Keep a ref copy of `streamStatus` so async callbacks can observe latest value
  useEffect(() => {
    streamStatusRef.current = streamStatus;
  }, [streamStatus]);

  // Broadcast minimal session/loading state for UI listeners.
  useEffect(() => {
    const detail = {
      status: streamStatus,
      queuePosition,
      launchError: launchError ? { title: launchError.title, description: launchError.description, stage: launchError.stage, codeLabel: launchError.codeLabel } : null,
      gameTitle: streamingGame?.title ?? null,
      gameCover: streamingGame?.imageUrl ?? null,
      platformStore: streamingStore ?? null,
    };
    try {
      window.dispatchEvent(new CustomEvent("opennow:session-update", { detail }));
    } catch {
      // ignore
    }
  }, [streamStatus, queuePosition, launchError, streamingGame, streamingStore]);

  // Derived state

  const effectiveStreamingBaseUrl = useMemo(() => {
    if (settings.region.trim()) {
      return settings.region;
    }
    return selectedProvider?.streamingServiceUrl ?? "";
  }, [selectedProvider, settings.region]);

  const resolveSubscriptionInfoForLaunch = useCallback(async (): Promise<SubscriptionInfo | null> => {
    if (subscriptionInfo) {
      return subscriptionInfo;
    }

    const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
    if (!authSession || !token) {
      return null;
    }

    try {
      const subscription = await window.openNow.fetchSubscription({
        token,
        providerStreamingBaseUrl: effectiveStreamingBaseUrl,
        userId: authSession.user.userId,
      });
      setSubscriptionInfo(subscription);
      return subscription;
    } catch (error) {
      console.warn("Failed to resolve subscription before launch; using safe stream profile fallback:", error);
      return null;
    }
  }, [authSession, effectiveStreamingBaseUrl, subscriptionInfo]);

  const resolveInstallToPlayStreamingBaseUrl = useCallback(async (
    game: GameInfo,
    subscription: SubscriptionInfo | null,
    token: string | undefined,
  ): Promise<string | undefined> => {
    let availableRegions: StreamRegion[] = regions;
    if (availableRegions.length === 0) {
      try {
        availableRegions = await window.openNow.getRegions({ token });
        if (availableRegions.length > 0) {
          setRegions(availableRegions);
        }
      } catch (error) {
        console.warn("[I2P] Failed to load regions for persistent storage routing:", error);
      }
    }

    const storageRegionUrl = resolveInstallToPlayStorageRegionUrl(game, subscription, availableRegions);
    if (storageRegionUrl) {
      console.log("[I2P] Routing install-to-play launch to persistent storage region", {
        title: game.title,
        storageRegion: subscription?.storageAddon?.regionName,
        storageRegionUrl,
      });
    } else if (game.playType === "INSTALL_TO_PLAY") {
      console.warn("[I2P] No matching persistent storage region found; using selected/default region", {
        title: game.title,
        storageRegion: subscription?.storageAddon?.regionName,
      });
    }
    return storageRegionUrl ?? undefined;
  }, [regions]);

  const {
    activeQueueAd,
    activeQueueAdMediaUrl,
    effectiveAdState,
    handleQueueAdPlaybackEvent,
    queueAdPlaybackRef,
    queueAdPreviewRef,
  } = useQueueAdRuntime({
    authSession,
    effectiveStreamingBaseUrl,
    session,
    sessionRef,
    setQueuePosition,
    setSession,
    subscriptionInfo,
    t,
  });


  const refreshNavbarActiveSession = useCallback(async (
    sessionOverride?: AuthSession,
    streamingBaseUrlOverride?: string,
  ): Promise<void> => {
    const session = sessionOverride ?? authSession;
    if (!session) {
      setNavbarActiveSession(null);
      return;
    }
    const token = session.tokens.idToken ?? session.tokens.accessToken;
    const streamingBaseUrl = streamingBaseUrlOverride
      ?? (settings.region.trim() ? effectiveStreamingBaseUrl : session.provider.streamingServiceUrl);
    if (!token || !streamingBaseUrl) {
      setNavbarActiveSession(null);
      return;
    }
    try {
      const activeSessions = await window.openNow.getActiveSessions(token, streamingBaseUrl);
      const snapshot = runtimeSnapshotRef.current;
      const resumableSessions = activeSessions.filter((entry) => entry.status === 3 || entry.status === 2);
      const candidate =
        (snapshot?.sessionId
          ? resumableSessions.find((entry) => entry.sessionId === snapshot.sessionId)
          : undefined) ??
        (snapshot?.sessionAppId !== null && snapshot?.sessionAppId !== undefined
          ? resumableSessions.find((entry) => entry.appId === snapshot.sessionAppId)
          : undefined) ??
        resumableSessions[0] ??
        null;
      setNavbarActiveSession(candidate);
    } catch (error) {
      console.warn("Failed to refresh active sessions:", error);
    }
  }, [authSession, effectiveStreamingBaseUrl, settings.region]);

  refreshNavbarActiveSessionRef.current = refreshNavbarActiveSession;

  const gameTitleByAppId = useMemo(() => {
    const titles = new Map<number, string>();

    for (const game of allKnownGames) {
      const idsForGame = new Set<number>();
      const launchId = parseNumericId(game.launchAppId);
      if (launchId !== null) {
        idsForGame.add(launchId);
      }
      for (const variant of game.variants) {
        const variantId = parseNumericId(variant.id);
        if (variantId !== null) {
          idsForGame.add(variantId);
        }
      }
      for (const appId of idsForGame) {
        if (!titles.has(appId)) {
          titles.set(appId, game.title);
        }
      }
    }

    return titles;
  }, [allKnownGames]);

  const findGameContextForSession = useCallback((activeSession: ActiveSessionInfo) => {
    return findSessionContextForAppId(allKnownGames, variantByGameId, activeSession.appId);
  }, [allKnownGames, variantByGameId]);

  const stopSessionByTarget = useCallback(async (
    target: Pick<SessionStopRequest, "sessionId" | "zone" | "streamingBaseUrl" | "serverIp" | "clientId" | "deviceId"> | null | undefined,
  ): Promise<boolean> => {
    if (!target) {
      return false;
    }
    const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
    if (!token) {
      console.warn("Skipping session stop: missing auth token");
      return false;
    }
    await window.openNow.stopSession({
      token,
      streamingBaseUrl: target.streamingBaseUrl,
      serverIp: target.serverIp,
      zone: target.zone,
      sessionId: target.sessionId,
      clientId: target.clientId,
      deviceId: target.deviceId,
    });
    return true;
  }, [authSession]);

  useEffect(() => {
    if (!authSession || streamStatus !== "idle") {
      return;
    }
    void refreshNavbarActiveSession();
    const timer = window.setInterval(() => {
      void refreshNavbarActiveSession();
    }, 10000);
    return () => window.clearInterval(timer);
  }, [authSession, refreshNavbarActiveSession, streamStatus]);

  useEffect(() => {
    saveStoredCodecResults(codecResults);
  }, [codecResults]);


  useEffect(() => {
    if (codecResults || codecTesting || codecStartupTestAttemptedRef.current) {
      return;
    }
    codecStartupTestAttemptedRef.current = true;
    void runCodecTest();
  }, [codecResults, codecTesting, runCodecTest]);

  const shortcuts = useMemo(() => {
    const parseWithFallback = (value: string, fallback: string) => {
      const parsed = normalizeShortcut(value);
      return parsed.valid ? parsed : normalizeShortcut(fallback);
    };
    const toggleStats = parseWithFallback(settings.shortcutToggleStats, DEFAULT_SHORTCUTS.shortcutToggleStats);
    const togglePointerLock = parseWithFallback(settings.shortcutTogglePointerLock, DEFAULT_SHORTCUTS.shortcutTogglePointerLock);
    const toggleFullscreen = parseWithFallback(settings.shortcutToggleFullscreen, DEFAULT_SHORTCUTS.shortcutToggleFullscreen);
    const stopStream = parseWithFallback(settings.shortcutStopStream, DEFAULT_SHORTCUTS.shortcutStopStream);
    const toggleAntiAfk = parseWithFallback(settings.shortcutToggleAntiAfk, DEFAULT_SHORTCUTS.shortcutToggleAntiAfk);
    const toggleMicrophone = parseWithFallback(settings.shortcutToggleMicrophone, DEFAULT_SHORTCUTS.shortcutToggleMicrophone);
    const screenshot = parseWithFallback(settings.shortcutScreenshot, DEFAULT_SHORTCUTS.shortcutScreenshot);
    const recording = parseWithFallback(settings.shortcutToggleRecording, DEFAULT_SHORTCUTS.shortcutToggleRecording);
    return { toggleStats, togglePointerLock, toggleFullscreen, stopStream, toggleAntiAfk, toggleMicrophone, screenshot, recording };
  }, [
    settings.shortcutToggleStats,
    settings.shortcutTogglePointerLock,
    settings.shortcutToggleFullscreen,
    settings.shortcutStopStream,
    settings.shortcutToggleAntiAfk,
    settings.shortcutToggleMicrophone,
    settings.shortcutScreenshot,
    settings.shortcutToggleRecording,
  ]);

  const nativeStreamerShortcuts = useMemo(() => ({
    toggleStats: shortcuts.toggleStats.canonical,
    togglePointerLock: shortcuts.togglePointerLock.canonical,
    toggleFullscreen: shortcuts.toggleFullscreen.canonical,
    stopStream: shortcuts.stopStream.canonical,
    toggleAntiAfk: shortcuts.toggleAntiAfk.canonical,
    toggleMicrophone: shortcuts.toggleMicrophone.canonical,
    screenshot: "",
    toggleRecording: "",
  }), [shortcuts]);

  const buildSignalingConnectRequest = useCallback((activeSession: SessionInfo): SignalingConnectRequest => {
    const streamSettings = buildCurrentStreamSettings();
    return {
      sessionId: activeSession.sessionId,
      signalingServer: activeSession.signalingServer,
      signalingUrl: activeSession.signalingUrl,
      nativeStreamer: buildNativeStreamerSessionContext(activeSession, streamSettings, nativeStreamerShortcuts),
    };
  }, [buildCurrentStreamSettings, nativeStreamerShortcuts]);

  // Propagate shortcut binding changes to native process during active session
  useEffect(() => {
    if (streamStatus !== "streaming" || !session || !nativeStreamingRef.current) {
      return;
    }
    window.openNow.updateNativeShortcuts(nativeStreamerShortcuts);
  }, [nativeStreamerShortcuts, session, streamStatus]);

  const setSessionFullscreen = useCallback(async (nextFullscreen: boolean) => {
    const canUseNativeFullscreen = typeof window.openNow?.setFullscreen === "function";
    if (document.pointerLockElement) {
      clientRef.current?.suppressNextSyntheticEscapeOnPointerLockLoss();
    }

    if (canUseNativeFullscreen) {
      try {
        // Electron owns desktop fullscreen. Stacking HTML fullscreen on top lets
        // Chromium force-exit the DOM layer on Escape before stream input runs.
        await window.openNow.setFullscreen(nextFullscreen);
        setSessionFullscreenState(nextFullscreen);
      } catch (error) {
        console.warn(`Failed to set native fullscreen state (${nextFullscreen ? "enter" : "exit"}):`, error);
      }
      return;
    }

    try {
      if (nextFullscreen) {
        if (!document.fullscreenElement) {
          await document.documentElement.requestFullscreen();
        }
      } else if (document.fullscreenElement) {
        await document.exitFullscreen();
      }
    } catch {}

    setSessionFullscreenState(!!document.fullscreenElement);
  }, []);

  const toggleSessionFullscreen = useCallback(async () => {
    await setSessionFullscreen(!(sessionFullscreen || document.fullscreenElement));
  }, [sessionFullscreen, setSessionFullscreen]);

  useEffect(() => {
    const handleFullscreenChange = () => {
      if (typeof window.openNow?.setFullscreen === "function") {
        return;
      }
      setSessionFullscreenState(!!document.fullscreenElement);
    };

    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, []);

  const requestPointerLockCapture = useCallback(async (target: HTMLVideoElement) => {
    const lockTarget = (target.parentElement as HTMLElement | null) ?? target;
    const requestPointerLockCompat = async (
      options?: { unadjustedMovement?: boolean },
    ): Promise<void> => {
      const maybePromise = lockTarget.requestPointerLock(options as any) as unknown;
      if (maybePromise && typeof (maybePromise as Promise<void>).then === "function") {
        await (maybePromise as Promise<void>);
      }
    };

    if (settings.autoFullScreen && !(sessionFullscreen || document.fullscreenElement)) {
      await setSessionFullscreen(true);
    }

    await requestPointerLockCompat({ unadjustedMovement: true })
      .catch((err: DOMException) => {
        if (err.name === "NotSupportedError") {
          return requestPointerLockCompat();
        }
        throw err;
      })
      .catch(() => {});
  }, [sessionFullscreen, setSessionFullscreen, settings.autoFullScreen]);

  const handleRequestPointerLock = useCallback(() => {
    if (videoRef.current) {
      void requestPointerLockCapture(videoRef.current);
    }
  }, [requestPointerLockCapture]);

  const setNativeInputPaused = useCallback((paused: boolean): void => {
    if (!nativeStreamingRef.current && settings.streamClientMode !== "native") {
      return;
    }
    window.openNow.setNativeInputPaused(paused);
  }, [settings.streamClientMode]);

  const resolveExitPrompt = useCallback((confirmed: boolean) => {
    const resolver = exitPromptResolverRef.current;
    exitPromptResolverRef.current = null;
    setExitPrompt((prev) => (prev.open ? { ...prev, open: false } : prev));
    resolver?.(confirmed);
  }, []);

  const requestExitPrompt = useCallback((gameTitle: string): Promise<boolean> => {
    return new Promise((resolve) => {
      if (exitPromptResolverRef.current) {
        // Close any previous pending prompt to avoid dangling promises.
        exitPromptResolverRef.current(false);
      }
      exitPromptResolverRef.current = resolve;
      setExitPrompt({
        open: true,
        gameTitle: gameTitle || t("session.thisGame"),
      });
    });
  }, [t]);

  const handleExitPromptConfirm = useCallback(() => {
    resolveExitPrompt(true);
  }, [resolveExitPrompt]);

  const handleExitPromptCancel = useCallback(() => {
    resolveExitPrompt(false);
  }, [resolveExitPrompt]);

  useEffect(() => {
    return () => {
      if (exitPromptResolverRef.current) {
        exitPromptResolverRef.current(false);
        exitPromptResolverRef.current = null;
      }
    };
  }, []);

  // Listen for fullscreen toggle from main process.
  useEffect(() => {
    const unsubscribe = window.openNow.onToggleFullscreen(() => {
      void toggleSessionFullscreen();
    });
    return () => unsubscribe();
  }, [toggleSessionFullscreen]);

  // Escape-hold (and other explicit exit requests) from main — never toggle back on.
  useEffect(() => {
    const unsubscribe = window.openNow.onExitFullscreen(() => {
      void setSessionFullscreen(false);
    });
    return () => unsubscribe();
  }, [setSessionFullscreen]);

  const autoFullscreenRequestedRef = useRef(false);

  useEffect(() => {
    const isSessionConnecting = streamStatus === "connecting" || streamStatus === "streaming";
    const isNativeStreamerSession = settings.streamClientMode === "native" || nativeStreamingRef.current;
    if (!settings.autoFullScreen || !isSessionConnecting || isNativeStreamerSession) {
      autoFullscreenRequestedRef.current = false;
      return;
    }

    if (autoFullscreenRequestedRef.current || sessionFullscreen || document.fullscreenElement) {
      return;
    }

    autoFullscreenRequestedRef.current = true;
    void setSessionFullscreen(true);
  }, [sessionFullscreen, setSessionFullscreen, settings.autoFullScreen, settings.streamClientMode, streamStatus]);

  // Anti-AFK interval
  useEffect(() => {
    if (!isStreaming) {
      setAntiAfkAckNonce(0);
    }
  }, [isStreaming]);

  useEffect(() => {
    if (!antiAfkEnabled || streamStatus !== "streaming") return;
    if (nativeStreamingRef.current && !nativeInputBridgeReady) return;

    const interval = window.setInterval(() => {
      clientRef.current?.sendAntiAfkPulse();
    }, 240000); // 4 minutes

    return () => clearInterval(interval);
  }, [antiAfkEnabled, nativeInputBridgeReady, streamStatus]);

  // Periodically re-sync subscription playtime from backend while streaming.
  useEffect(() => {
    if (streamStatus !== "streaming" || !authSession) {
      return;
    }

    let cancelled = false;

    const syncPlaytime = async (): Promise<void> => {
      try {
        await loadSubscriptionInfo(authSession);
      } catch (error) {
        if (!cancelled) {
          console.warn("Failed to re-sync subscription playtime:", error);
        }
      }
    };

    void syncPlaytime();
    const timer = window.setInterval(() => {
      void syncPlaytime();
    }, PLAYTIME_RESYNC_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [authSession, loadSubscriptionInfo, streamStatus]);

  // Restore focus to video element when navigating away from Settings during streaming
  useEffect(() => {
    if (streamStatus === "streaming" && currentPage !== "settings" && videoRef.current) {
      // Small delay to let React finish rendering the new page
      const timer = window.setTimeout(() => {
        if (videoRef.current && document.activeElement !== videoRef.current) {
          videoRef.current.focus();
          console.log("[App] Restored focus to video element after leaving Settings");
        }
      }, 50);
      return () => clearTimeout(timer);
    }
  }, [currentPage, streamStatus]);


  useEffect(() => {
    if (streamStatus !== "streaming" || sessionStartedAtMs !== null) {
      return;
    }

    const evaluate = () => {
      const snapshot = diagnosticsStore.getSnapshot();
      const hasLiveFrames =
        snapshot.framesDecoded > 0 || snapshot.framesReceived > 0 || snapshot.renderFps > 0;
      if (hasLiveFrames) {
        setSessionStartedAtMs(Date.now());
      }
    };

    evaluate();
    const unsubscribe = diagnosticsStore.subscribe(evaluate);
    return unsubscribe;
  }, [sessionStartedAtMs, streamStatus]);

  useEffect(() => {
    if (freeTierSessionRemainingSeconds === null) {
      previousFreeTierRemainingSecondsRef.current = null;
      setLocalSessionTimerWarning(null);
      return;
    }

    const previousSeconds = previousFreeTierRemainingSecondsRef.current;

    if (hasCrossedWarningThreshold(previousSeconds, freeTierSessionRemainingSeconds, FREE_TIER_FINAL_MINUTE_WARNING_SECONDS)) {
      setLocalSessionTimerWarning({ stage: "free-tier-final-minute", shownAtMs: Date.now() });
    } else if (hasCrossedWarningThreshold(previousSeconds, freeTierSessionRemainingSeconds, FREE_TIER_15_MIN_WARNING_SECONDS)) {
      setLocalSessionTimerWarning({ stage: "free-tier-15m", shownAtMs: Date.now() });
    } else if (hasCrossedWarningThreshold(previousSeconds, freeTierSessionRemainingSeconds, FREE_TIER_30_MIN_WARNING_SECONDS)) {
      setLocalSessionTimerWarning({ stage: "free-tier-30m", shownAtMs: Date.now() });
    }

    previousFreeTierRemainingSecondsRef.current = freeTierSessionRemainingSeconds;
  }, [freeTierSessionRemainingSeconds]);

  useEffect(() => {
    if (!localSessionTimerWarning) return;

    const warning = localSessionTimerWarning;
    const remainingMs = Math.max(0, warning.shownAtMs + STREAM_WARNING_VISIBILITY_MS - Date.now());
    const timer = window.setTimeout(() => {
      setLocalSessionTimerWarning((current) => (current === warning ? null : current));
    }, remainingMs);
    return () => window.clearTimeout(timer);
  }, [localSessionTimerWarning]);

  useEffect(() => {
    if (!remoteStreamWarning) return;

    const warning = remoteStreamWarning;
    const timer = window.setTimeout(() => {
      setRemoteStreamWarning((current) => (current === warning ? null : current));
    }, STREAM_WARNING_VISIBILITY_MS);
    return () => window.clearTimeout(timer);
  }, [remoteStreamWarning]);

  useEffect(() => {
    applyAccentColor(settings.appAccentColor);
  }, [settings.appAccentColor]);

  useEffect(() => {
    applyTheme(settings.appTheme);

    if (settings.appTheme === "auto") {
      const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      const handler = () => applyTheme("auto");
      mediaQuery.addEventListener("change", handler);
      return () => mediaQuery.removeEventListener("change", handler);
    }
  }, [settings.appTheme]);

  useEffect(() => {
    applyTranslucentUI(settings.translucentUI);
  }, [settings.translucentUI]);


  const previewSetting = useCallback(<K extends keyof Settings>(key: K, value: Settings[K]): void => {
    setSettings((prev) => (Object.is(prev[key], value) ? prev : { ...prev, [key]: value }));

    // If a running client exists, push supported settings live while controls move.
    if (key === "mouseSensitivity") {
      try {
        (clientRef.current as any)?.setMouseSensitivity?.(value as number);
      } catch {
        // ignore
      }
    }
    if (key === "mouseAcceleration") {
      try {
        (clientRef.current as any)?.setMouseAccelerationPercent?.(value as number);
      } catch {
        // ignore
      }
    }
    if (key === "autoFullScreen") {
      try {
        (clientRef.current as any)?.setAutoFullScreen?.(value as boolean);
      } catch {
        // ignore
      }
    }
    if (key === "clipboardPaste") {
      try {
        (clientRef.current as any)?.setClipboardPasteEnabled?.(value as boolean);
      } catch {
        // ignore
      }
    }
    if (key === "nativeCursorOverlay") {
      try {
        clientRef.current?.setNativeCursorOverlayEnabled(value as boolean);
      } catch {
        // ignore
      }
    }
    if (key === "maxBitrateMbps") {
      try {
        void (clientRef.current as any)?.setMaxBitrateKbps?.((value as number) * 1000);
      } catch {
        // ignore
      }
    }
  }, []);

  const updateSetting = useCallback(async <K extends keyof Settings>(key: K, value: Settings[K]): Promise<void> => {
    previewSetting(key, value);
    if (settingsLoaded) {
      await window.openNow.setSetting(key, value);
    }
    if (key === "identifyAsSteamDeck" && authSession) {
      try {
        await loadSubscriptionInfo(authSession);
      } catch (error) {
        console.warn("Failed to refresh subscription after Steam Deck identity change:", error);
      }
    }
  }, [authSession, loadSubscriptionInfo, previewSetting, settingsLoaded]);

  useEffect(() => {
    if (!settingsLoaded || !subscriptionInfo) {
      return;
    }

    const entitledProfile = resolveEntitledStreamProfile(subscriptionInfo.entitledResolutions, {
      resolution: settings.resolution,
      fps: settings.fps,
    }) ?? SAFE_FALLBACK_STREAM_PROFILE;

    if (entitledProfile.resolution !== settings.resolution) {
      void updateSetting("resolution", entitledProfile.resolution);
    }
    if (entitledProfile.fps !== settings.fps) {
      void updateSetting("fps", entitledProfile.fps);
    }
  }, [
    settings.fps,
    settings.resolution,
    settingsLoaded,
    subscriptionInfo,
    updateSetting,
  ]);

  const handleMouseSensitivityChange = useCallback((value: number) => {
    void updateSetting("mouseSensitivity", value);
  }, [updateSetting]);

  const handleToggleFavoriteGame = useCallback((gameId: string): void => {
    const favorites = settings.favoriteGameIds;
    const exists = favorites.includes(gameId);
    const next = exists ? favorites.filter((id) => id !== gameId) : [...favorites, gameId];
    void updateSetting("favoriteGameIds", next);
  }, [settings.favoriteGameIds, updateSetting]);

  const handleMouseAccelerationChange = useCallback((value: number) => {
    void updateSetting("mouseAcceleration", value);
  }, [updateSetting]);

  const handleVideoShaderChange = useCallback((value: VideoShaderSettings) => {
    void updateSetting("videoShader", value);
  }, [updateSetting]);

  const handleExitApp = useCallback(() => {
    appUnloadingRef.current = true;
    persistRuntimeSnapshotNow();
    void window.openNow.quitApp().catch((error) => {
      console.warn("Failed to quit application:", error);
    });
  }, [persistRuntimeSnapshotNow]);

  // Argument-driven (direct) launches behave like a console frontend session:
  // when the streamed session ends cleanly, close OpenNOW and return to the caller.
  const directLaunchSessionSeenRef = useRef(false);
  useEffect(() => {
    if (!directLaunchConsoleMode) return;
    if (streamStatus !== "idle") {
      directLaunchSessionSeenRef.current = true;
      return;
    }
    if (!directLaunchSessionSeenRef.current) return;
    if (launchError) return; // Keep the app open so the failure stays visible.
    console.log("[DirectLaunch] Session ended; quitting OpenNOW");
    handleExitApp();
  }, [directLaunchConsoleMode, streamStatus, launchError, handleExitApp]);

  const handleMicrophoneModeChange = useCallback((value: import("@shared/gfn").MicrophoneMode) => {
    // Keep UI responsive while still surfacing persistence failures.
    void updateSetting("microphoneMode", value).catch((error) => {
      console.warn("Failed to persist microphone mode setting:", error);
    });
  }, [updateSetting]);

  const resolveSessionClaimAppId = useCallback((existingSession: ActiveSessionInfo): string => {
    const trackedAppId = signalingRecoveryRef.current.appId;
    const persistedAppId = runtimeSnapshotRef.current?.sessionAppId ?? runtimeSnapshotRef.current?.recoveryAppId;
    if (Number.isFinite(existingSession.appId) && existingSession.appId > 0) {
      return String(existingSession.appId);
    }
    if (trackedAppId && Number.isFinite(trackedAppId)) {
      return String(trackedAppId);
    }
    if (persistedAppId && Number.isFinite(persistedAppId)) {
      return String(persistedAppId);
    }
    throw new Error("Active session is missing app metadata required for resume.");
  }, []);

  const resolveResumeIdentity = useCallback((sessionId: string): { clientId?: string; deviceId?: string } => {
    const liveSession = sessionRef.current;
    if (liveSession?.sessionId === sessionId) {
      return {
        clientId: liveSession.clientId,
        deviceId: liveSession.deviceId,
      };
    }
    const persisted = runtimeSnapshotRef.current?.resumeContext;
    if (persisted?.sessionId === sessionId) {
      return {
        clientId: persisted.clientId,
        deviceId: persisted.deviceId,
      };
    }
    return {};
  }, []);

  const applyClaimedSessionAndConnect = useCallback(async (
    claimed: SessionInfo,
    expectedRecoveryGeneration?: number,
  ): Promise<void> => {
    const canProceedWithClaimedReconnect = (): boolean => {
      if (
        expectedRecoveryGeneration !== undefined
        && !isRecoveryGenerationCurrent(expectedRecoveryGeneration)
      ) {
        return false;
      }
      if (signalingRecoveryRef.current.explicitShutdown) {
        return false;
      }
      return true;
    };

    if (
      expectedRecoveryGeneration !== undefined
      && !isRecoveryGenerationCurrent(expectedRecoveryGeneration)
    ) {
      console.log("[Recovery] Skipping claimed session apply due to stale recovery generation");
      return;
    }

    console.log("Claimed session:", {
      sessionId: claimed.sessionId,
      signalingServer: claimed.signalingServer,
      signalingUrl: claimed.signalingUrl,
      status: claimed.status,
    });

    await sleep(1000);
    if (
      expectedRecoveryGeneration !== undefined
      && !isRecoveryGenerationCurrent(expectedRecoveryGeneration)
    ) {
      console.log("[Recovery] Skipping reconnect due to stale recovery generation after delay");
      return;
    }
    if (!canProceedWithClaimedReconnect()) {
      console.log("[Recovery] Skipping claimed session apply due to explicit shutdown");
      return;
    }

    // Mirror attemptSessionRecovery: tear down WebRTC + signaling before connecting to a new edge.
    // Avoids stale PeerConnection/video vs migrated CloudMatch connectionInfo (intermittent black screen on resume).
    const reconnectSource = expectedRecoveryGeneration !== undefined ? "recovery" : "resume";
    console.log(`[Stream] ${reconnectSource}: teardown WebRTC + signaling before reconnect`, {
      sessionId: claimed.sessionId,
      signalingServer: claimed.signalingServer,
      signalingUrl: claimed.signalingUrl,
      mediaConnectionInfo: claimed.mediaConnectionInfo,
    });
    clientRef.current?.dispose();
    clientRef.current = null;
    await disconnectSignalingControlled();
    awaitingRecoveryRemoteIceRef.current = expectedRecoveryGeneration !== undefined;

    setSession(claimed);
    sessionRef.current = claimed;
    nativeInputProtocolVersionRef.current = null;
    setNativeInputBridgeReady(false);
    setNativeInputCaptureActive(false);
    try {
      window.openNow.notifyPointerLockChange(false, true);
    } catch {
      /* best-effort */
    }
    setQueuePosition(undefined);
    setLaunchError(null);
    setStreamStatus("connecting");
    await window.openNow.connectSignaling(buildSignalingConnectRequest(claimed));
  }, [buildSignalingConnectRequest, disconnectSignalingControlled, isRecoveryGenerationCurrent]);

  const claimAndConnectSession = useCallback(async (existingSession: ActiveSessionInfo): Promise<void> => {
    const sid = existingSession.sessionId;
    const inflight = claimResumePromisesRef.current.get(sid);
    if (inflight) {
      console.log("[Resume] claimAndConnectSession: deduped — joining in-flight claim for session", sid);
      await inflight;
      return;
    }

    const resumePromiseHolder: { promise?: Promise<void> } = {};
    resumePromiseHolder.promise = (async (): Promise<void> => {
      try {
        const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
        if (!token) {
          throw new Error("Missing token for session resume");
        }
        if (!existingSession.serverIp) {
          throw new Error("Active session is missing server address. Start the game again to create a new session.");
        }
        warmNativeStreamerForLaunch();

        console.log("[Resume] claimAndConnectSession: invoking claimSession", {
          sessionId: existingSession.sessionId,
          serverIp: existingSession.serverIp,
          status: existingSession.status,
          appId: existingSession.appId,
        });

        const matchedContext = findGameContextForSession(existingSession);
        if (matchedContext) {
          setStreamingGame(matchedContext.game);
          setStreamingStore(matchedContext.variant?.store ?? null);
        } else {
          setStreamingStore(null);
        }

        const launchSubscription = await resolveSubscriptionInfoForLaunch();
        const streamSettings = buildCurrentStreamSettings(launchSubscription);
        const claimed = await window.openNow.claimSession({
          token,
          streamingBaseUrl: effectiveStreamingBaseUrl,
          serverIp: existingSession.serverIp,
          sessionId: existingSession.sessionId,
          ...resolveResumeIdentity(existingSession.sessionId),
          appId: resolveSessionClaimAppId(existingSession),
          appLaunchMode: existingSession.appLaunchMode,
          enablePersistingInGameSettings: existingSession.enablePersistingInGameSettings,
          settings: streamSettings,
        });

        await applyClaimedSessionAndConnect(claimed);
      } finally {
        const map = claimResumePromisesRef.current;
        const p = resumePromiseHolder.promise;
        if (p && map.get(sid) === p) {
          map.delete(sid);
        }
      }
    })();

    claimResumePromisesRef.current.set(sid, resumePromiseHolder.promise);
    await resumePromiseHolder.promise;
  }, [applyClaimedSessionAndConnect, authSession, buildCurrentStreamSettings, effectiveStreamingBaseUrl, findGameContextForSession, resolveResumeIdentity, resolveSessionClaimAppId, resolveSubscriptionInfoForLaunch, warmNativeStreamerForLaunch]);

  const attemptSessionRecovery = useCallback(async (reason: string): Promise<boolean> => {
    const recoveryState = signalingRecoveryRef.current;
    const recoveryGeneration = recoveryState.generation;
    const currentStatus = streamStatusRef.current;
    const currentSession = sessionRef.current;

    if (!isRecoveryGenerationCurrent(recoveryGeneration)) {
      console.log("[Recovery] Skipping signaling recovery after explicit shutdown");
      return false;
    }
    if (!RECOVERABLE_STREAM_STATUSES.includes(currentStatus)) {
      console.log("[Recovery] Stream status is not recoverable:", currentStatus);
      return false;
    }
    if (!currentSession) {
      console.warn("[Recovery] No active session available for signaling recovery");
      return false;
    }
    if (recoveryState.inFlight) {
      console.log("[Recovery] Reusing in-flight signaling recovery attempt");
      return recoveryState.inFlight;
    }

    const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
    if (!token) {
      throw new Error("Connection to the running session was lost and your login token is no longer available for resume.");
    }

    if (recoveryState.attemptCount >= SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS.length) {
      console.warn("[Recovery] Recovery budget exhausted");
      return false;
    }

    const attemptPromise = (async (): Promise<boolean> => {
      clientRef.current?.dispose();
      clientRef.current = null;
      setStreamStatus("connecting");
      await disconnectSignalingControlled();

      let lastError: Error | null = null;
      while (recoveryState.attemptCount < SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS.length) {
        const attemptIndex = recoveryState.attemptCount;
        recoveryState.attemptCount += 1;
        const attemptNumber = recoveryState.attemptCount;
        const attemptDelayMs = SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS[attemptIndex] ?? 0;

        console.warn(
          `[Recovery] Attempt ${attemptNumber}/${SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS.length} after signaling disconnect: ${reason}`,
        );

        if (attemptDelayMs > 0) {
          await sleep(attemptDelayMs);
        }
        if (!isRecoveryGenerationCurrent(recoveryGeneration)) {
          console.log("[Recovery] Aborting attempt after explicit shutdown");
          return false;
        }

        try {
          const activeSessions = await window.openNow.getActiveSessions(token, effectiveStreamingBaseUrl);
          if (!isRecoveryGenerationCurrent(recoveryGeneration)) {
            console.log("[Recovery] Aborting attempt after active session lookup due to stale generation");
            return false;
          }
          const previousAppId = recoveryState.appId;
          const currentSessionId = currentSession.sessionId;
          const sameSessionCandidate =
            activeSessions.find((entry) => entry.sessionId === currentSessionId && entry.serverIp && isSessionReadyForConnect(entry.status)) ??
            null;

          let candidate = sameSessionCandidate;
          if (!candidate && previousAppId !== null) {
            candidate =
              activeSessions.find((entry) => (
                entry.appId === previousAppId &&
                entry.serverIp &&
                isSessionReadyForConnect(entry.status) &&
                entry.sessionId === currentSessionId
              )) ??
              activeSessions.find((entry) => (
                entry.appId === previousAppId &&
                entry.serverIp &&
                isSessionReadyForConnect(entry.status)
              )) ??
              null;
          }

          if (!candidate) {
            const persisted = runtimeSnapshotRef.current?.resumeContext;
            if (
              persisted &&
              persisted.sessionId === currentSessionId &&
              persisted.serverIp
            ) {
              candidate = {
                sessionId: persisted.sessionId,
                appId:
                  Number.isFinite(persisted.appId ?? NaN)
                    ? (persisted.appId as number)
                    : (previousAppId ?? 0),
                appLaunchMode: persisted.appLaunchMode,
                enablePersistingInGameSettings: persisted.enablePersistingInGameSettings,
                status: 2,
                serverIp: persisted.serverIp,
                streamingBaseUrl: persisted.streamingBaseUrl,
                signalingUrl: persisted.signalingUrl,
              };
              console.log("[Recovery] Falling back to persisted resume context", {
                sessionId: persisted.sessionId,
                serverIp: persisted.serverIp,
                appId: persisted.appId ?? previousAppId ?? null,
              });
            }
          }

          if (!candidate) {
            const hasQueueOnlyMatch = activeSessions.some((entry) => entry.sessionId === currentSessionId && entry.status === 1);
            if (hasQueueOnlyMatch) {
              throw new Error("The session is still queued and cannot be reclaimed until the server marks it ready again.");
            }
            throw new Error("The running session could not be found anymore, so resume was not possible.");
          }

          if (!candidate.serverIp) {
            throw new Error("The running session is missing a server address, so resume was not possible.");
          }

          const recoverySubscription = await resolveSubscriptionInfoForLaunch();
          const recoveryStreamSettings = buildCurrentStreamSettings(recoverySubscription);
          const claimed = await window.openNow.claimSession({
            token,
            streamingBaseUrl: effectiveStreamingBaseUrl,
            serverIp: candidate.serverIp,
            sessionId: candidate.sessionId,
            ...resolveResumeIdentity(candidate.sessionId),
            recoveryMode: true,
            appId: resolveSessionClaimAppId(candidate),
            appLaunchMode: candidate.appLaunchMode,
            enablePersistingInGameSettings: candidate.enablePersistingInGameSettings,
            settings: recoveryStreamSettings,
          });
          if (!isRecoveryGenerationCurrent(recoveryGeneration)) {
            console.log("[Recovery] Discarding claimed session due to stale recovery generation");
            return false;
          }

          const matchedContext = findGameContextForSession(candidate);
          if (matchedContext) {
            setStreamingGame(matchedContext.game);
            setStreamingStore(matchedContext.variant?.store ?? null);
          } else {
            setStreamingStore(null);
          }

          await applyClaimedSessionAndConnect(claimed, recoveryGeneration);
          if (!isRecoveryGenerationCurrent(recoveryGeneration)) {
            console.log("[Recovery] Recovery generation changed before connect completed");
            return false;
          }
          return true;
        } catch (error) {
          lastError = error instanceof Error ? error : new Error(String(error));
          console.warn(`[Recovery] Attempt ${attemptNumber} failed:`, lastError.message);
        }
      }

      if (lastError) {
        throw lastError;
      }
      return false;
    })();

    recoveryState.inFlight = attemptPromise;
    try {
      return await attemptPromise;
    } finally {
      if (signalingRecoveryRef.current.inFlight === attemptPromise) {
        signalingRecoveryRef.current.inFlight = null;
      }
    }
  }, [
    applyClaimedSessionAndConnect,
    authSession,
    disconnectSignalingControlled,
    effectiveStreamingBaseUrl,
    findGameContextForSession,
    isRecoveryGenerationCurrent,
    resolveResumeIdentity,
    resolveSessionClaimAppId,
    buildCurrentStreamSettings,
    resolveSubscriptionInfoForLaunch,
  ]);

  const handleExpectedNativeSessionClose = useCallback((reason: string): void => {
    console.log("[Recovery] Treating signaling close as ended session:", reason);
    const activeGameId = streamingGameRef.current?.id;
    if (activeGameId) {
      endPlaytimeSession(activeGameId);
    }
    markExplicitSignalingShutdown();
    clientRef.current?.dispose();
    clientRef.current = null;
    launchInFlightRef.current = false;
    resetLaunchRuntime();
    void refreshNavbarActiveSession();
  }, [endPlaytimeSession, markExplicitSignalingShutdown, refreshNavbarActiveSession, resetLaunchRuntime]);

  // Signaling events
  useEffect(() => {
    const ensureWebRtcClient = (): GfnWebRtcClient | null => {
      if (clientRef.current) {
        return clientRef.current;
      }
      if (!videoRef.current || !audioRef.current) {
        return null;
      }

      clientRef.current = new GfnWebRtcClient({
        videoElement: videoRef.current,
        audioElement: audioRef.current,
        autoFullScreen: settings.autoFullScreen,
        microphoneMode: settings.microphoneMode,
        microphoneDeviceId: settings.microphoneDeviceId || undefined,
        nativeCursorOverlay: settings.nativeCursorOverlay,
        mouseSensitivity: settings.mouseSensitivity,
        mouseAcceleration: settings.mouseAcceleration,
        keyboardLayout: settings.keyboardLayout,
        clipboardPaste: settings.clipboardPaste,
        readClipboardText: readStreamClipboardText,
        onLog: (line: string) => console.log(`[WebRTC] ${line}`),
        onStats: (stats) => diagnosticsStore.set(stats),
        onTimeWarning: (warning) => {
          setRemoteStreamWarning({
            code: warning.code,
            message: warningMessage(t, warning.code),
            tone: warningTone(warning.code),
            secondsLeft: warning.secondsLeft,
          });
        },
        onMicStateChange: (state) => {
          console.log(`[App] Mic state: ${state.state}${state.deviceLabel ? ` (${state.deviceLabel})` : ""}`);
        },
        onControllerMetaPress: () => {
          if (streamStatusRef.current === "streaming") {
            dispatchStreamShortcutAction("toggleSidebar");
          }
        },
        onIceConnectionStateChange: (iceState) => {
          latestIceConnectionStateRef.current = iceState;
          if (iceDisconnectedRecoveryTimerRef.current !== null) {
            window.clearTimeout(iceDisconnectedRecoveryTimerRef.current);
            iceDisconnectedRecoveryTimerRef.current = null;
          }
          if (appUnloadingRef.current) {
            return;
          }
          if (streamStatusRef.current !== "streaming") {
            return;
          }
          if (iceState === "failed") {
            console.warn("[Recovery] ICE failed; attempting targeted recovery");
            void attemptSessionRecovery("ICE failed").catch((error) => {
              console.error("[Recovery] ICE-failed recovery failed:", error);
            });
            return;
          }
          if (iceState === "disconnected") {
            iceDisconnectedRecoveryTimerRef.current = window.setTimeout(() => {
              iceDisconnectedRecoveryTimerRef.current = null;
              if (appUnloadingRef.current || streamStatusRef.current !== "streaming") {
                return;
              }
              if (latestIceConnectionStateRef.current !== "disconnected") {
                return;
              }
              console.warn("[Recovery] ICE remained disconnected; attempting targeted recovery");
              void attemptSessionRecovery("ICE disconnected timeout").catch((error) => {
                console.error("[Recovery] ICE-disconnected recovery failed:", error);
              });
            }, ICE_DISCONNECTED_RECOVERY_GRACE_MS);
          }
        },
      });
      clientRef.current.setOutputVolume(streamVolume);
      clientRef.current.setMicrophoneLevel(streamMicLevel);
      if (settings.microphoneMode !== "disabled") {
        void clientRef.current.startMicrophone();
      }
      return clientRef.current;
    };

    const activateNativeInputForCurrentSession = (protocolVersion?: number): void => {
      const activeSession = sessionRef.current;
      if (!activeSession) {
        console.warn("[App] Received native stream event but no active session in sessionRef!");
        return;
      }
      const client = ensureWebRtcClient();
      if (!client) {
        console.warn("[App] Native stream event received before media elements were ready");
        return;
      }

      nativeStreamingRef.current = true;
      pendingControlledDisconnectsRef.current = 0;
      const isWindowsHost = navigator.platform.toLowerCase().includes("win");
      const electronInputBridge =
        /linux/i.test(`${navigator.platform} ${navigator.userAgent}`)
        || (!settings.nativeExternalRenderer && !isWindowsHost);
      client.activateNativeInput(
        protocolVersion,
        {
          codec: settings.codec,
          colorQuality: settings.colorQuality,
          resolution: settings.resolution,
          fps: settings.fps,
          maxBitrateKbps: settings.maxBitrateMbps * 1000,
        },
        {
          // Windows internal: RawInput on the child HWND (Electron click-through is flaky).
          // Linux: always Electron → IPC (External floating renderer is unsupported).
          // macOS internal: Electron → IPC. External floating window: always OS capture.
          electronInputBridge,
        },
      );
      // The external native window exclusively owns Escape through RawInput.
      // Internal mode leaves Escape with Electron so it can prevent Chromium's
      // fullscreen exit and forward one synthetic tap to the native streamer.
      window.openNow.notifyNativeInputModeChange(
        true,
        isWindowsHost && settings.nativeExternalRenderer,
      );
      setLaunchError(null);
      setStreamStatus("streaming");
      markDiscordStreamStarted();
      scheduleStableRecoveryReset(activeSession.sessionId);
    };

    const unsubscribe = window.openNow.onSignalingEvent(async (event: MainToRendererSignalingEvent) => {
      console.log(`[App] Signaling event: ${event.type}`, event.type === "offer" ? `(SDP ${event.sdp.length} chars)` : "", event.type === "remote-ice" ? event.candidate : "");
      try {
        if (event.type === "offer") {
          pendingControlledDisconnectsRef.current = 0;
          const activeSession = sessionRef.current;
          if (!activeSession) {
            console.warn("[App] Received offer but no active session in sessionRef!");
            return;
          }
          const shouldEnforceRemoteIceGrace = awaitingRecoveryRemoteIceRef.current;
          remoteIceSeenForSessionRef.current = null;
          hasConfirmedRemoteIceRef.current = false;
          if (remoteIceGraceTimerRef.current !== null) {
            window.clearTimeout(remoteIceGraceTimerRef.current);
            remoteIceGraceTimerRef.current = null;
          }
          const expectedSessionId = activeSession.sessionId;
          const recoveryGenerationAtOffer = signalingRecoveryRef.current.generation;
          if (shouldEnforceRemoteIceGrace) {
            remoteIceGraceTimerRef.current = window.setTimeout(() => {
              remoteIceGraceTimerRef.current = null;
              if (sessionRef.current?.sessionId !== expectedSessionId) {
                return;
              }
              if (remoteIceSeenForSessionRef.current === expectedSessionId) {
                return;
              }
              if (remoteIceRecoveryGenerationRef.current === recoveryGenerationAtOffer) {
                return;
              }
              if (!RECOVERABLE_STREAM_STATUSES.includes(streamStatusRef.current)) {
                return;
              }
              awaitingRecoveryRemoteIceRef.current = false;
              remoteIceRecoveryGenerationRef.current = recoveryGenerationAtOffer;
              console.warn(
                `[Recovery] No remote ICE received within ${SIGNALING_REMOTE_ICE_GRACE_MS}ms after offer; forcing targeted recovery`,
              );
              void attemptSessionRecovery("No remote ICE received after offer").catch((error) => {
                console.error("[Recovery] ICE-timeout recovery failed:", error);
              });
            }, SIGNALING_REMOTE_ICE_GRACE_MS);
          }
          console.log("[App] Active session for offer:", JSON.stringify({
            sessionId: activeSession.sessionId,
            serverIp: activeSession.serverIp,
            signalingServer: activeSession.signalingServer,
            mediaConnectionInfo: activeSession.mediaConnectionInfo,
            iceServersCount: activeSession.iceServers?.length,
          }));

          const client = ensureWebRtcClient();

          if (client) {
            await client.handleOffer(event.sdp, activeSession, {
              codec: settings.codec,
              colorQuality: settings.colorQuality,
              resolution: settings.resolution,
              fps: settings.fps,
              maxBitrateKbps: settings.maxBitrateMbps * 1000,
              nativeTransitionDiagnostics: settings.nativeTransitionDiagnostics,
            });
            setLaunchError(null);
            setStreamStatus("streaming");
            markDiscordStreamStarted();
            scheduleStableRecoveryReset(activeSession.sessionId);
            console.log(
              "[Stream] Offer applied; use [WebRTC] logs for ICE/video dimensions. signalingServer=%s media=%s",
              activeSession.signalingServer,
              activeSession.mediaConnectionInfo
                ? `${activeSession.mediaConnectionInfo.ip}:${activeSession.mediaConnectionInfo.port}`
                : "n/a",
            );
          }
        } else if (event.type === "native-stream-started") {
          console.log("[App] Native streamer started:", event.message ?? "");
          activateNativeInputForCurrentSession(nativeInputProtocolVersionRef.current ?? undefined);
        } else if (event.type === "native-input-ready") {
          console.log("[App] Native input protocol ready:", event.protocolVersion);
          nativeInputProtocolVersionRef.current = event.protocolVersion;
          setNativeInputBridgeReady(true);
          clientRef.current?.setNativeInputProtocolVersion(event.protocolVersion);
          if (nativeStreamingRef.current || sessionRef.current) {
            activateNativeInputForCurrentSession(event.protocolVersion);
          }
        } else if (event.type === "native-shortcut") {
          handleStreamShortcutActionRef.current?.(event.action);
        } else if (event.type === "native-clipboard-paste") {
          if (settings.clipboardPaste && (!nativeStreamingRef.current || nativeInputBridgeReady)) {
            void sendStreamClipboardPaste(clientRef.current);
          }
        } else if (event.type === "native-input-capture-changed") {
          setNativeInputCaptureActive(event.captured);
          // Treat OS RawInput capture like pointer lock so main-process Escape
          // interception keeps Chromium from exiting fullscreen on tap.
          try {
            window.openNow.notifyPointerLockChange(event.captured);
          } catch {
            /* best-effort */
          }
        } else if (event.type === "native-stream-stats") {
          diagnosticsStore.set(mergeNativeStreamStats(
            diagnosticsStore.getSnapshot(),
            event.stats,
          ));
        } else if (event.type === "native-stream-transition") {
          diagnosticsStore.set({
            ...diagnosticsStore.getSnapshot(),
            nativeRendererActive: true,
            nativeTransitionSummary: event.transition.summary,
            nativeRequestedFps: event.transition.requestedFps,
            nativeCapsFramerate: event.transition.capsFramerate,
            nativeQueueMode: event.transition.queueMode,
            lagReasonDetail: event.transition.summary ?? "Native video transition detected",
          });
        } else if (event.type === "native-stream-stopped") {
          const reason = event.reason ?? "Native streamer stopped";
          console.warn("[App] Native streamer stopped:", reason);
          nativeStreamingRef.current = false;
          nativeInputProtocolVersionRef.current = null;
          setNativeInputBridgeReady(false);
          setNativeInputCaptureActive(false);
          window.openNow.notifyNativeInputModeChange(false, false);
          try {
            window.openNow.notifyPointerLockChange(false, true);
          } catch {
            /* best-effort */
          }
          clientRef.current?.dispose();
          clientRef.current = null;
          launchInFlightRef.current = false;

          if (appUnloadingRef.current) {
            console.log("[Recovery] Ignoring native streamer stop during app shutdown");
            return;
          }
          if (streamStatusRef.current === "streaming" && isExpectedNativeSessionClose(reason)) {
            handleExpectedNativeSessionClose(reason);
            return;
          }
          if (
            signalingRecoveryRef.current.explicitShutdown
            || !RECOVERABLE_STREAM_STATUSES.includes(streamStatusRef.current)
          ) {
            console.log("[Recovery] Ignoring native streamer stop after explicit shutdown or non-recoverable status");
            return;
          }

          const recovered = await attemptSessionRecovery(reason).catch((error) => {
            console.error("[Recovery] Native streamer recovery failed:", error);
            return false;
          });
          if (!recovered) {
            if (
              signalingRecoveryRef.current.explicitShutdown
              || !RECOVERABLE_STREAM_STATUSES.includes(streamStatusRef.current)
            ) {
              console.log("[Recovery] Ignoring native streamer stop after explicit shutdown or non-recoverable status");
              return;
            }
            setLaunchError({
              stage: streamStatusToLoadingStage(streamStatusRef.current),
              title: t("errors.nativeStreamerStoppedTitle"),
              description: t("errors.nativeStreamerStoppedDescription"),
            });
            resetLaunchRuntime({ keepLaunchError: true, keepStreamingContext: true });
            void refreshNavbarActiveSession();
            launchInFlightRef.current = false;
          }
        } else if (event.type === "remote-ice") {
          remoteIceSeenForSessionRef.current = sessionRef.current?.sessionId ?? null;
          hasConfirmedRemoteIceRef.current = true;
          awaitingRecoveryRemoteIceRef.current = false;
          if (remoteIceGraceTimerRef.current !== null) {
            window.clearTimeout(remoteIceGraceTimerRef.current);
            remoteIceGraceTimerRef.current = null;
          }
          await clientRef.current?.addRemoteCandidate(event.candidate);
        } else if (event.type === "disconnected") {
          if (appUnloadingRef.current) {
            console.log("[Recovery] Ignoring signaling disconnect during app shutdown");
            return;
          }
          if (streamStatusRef.current !== "idle" && isExpectedNativeSessionClose(event.reason)) {
            handleExpectedNativeSessionClose(event.reason);
            return;
          }
          if (
            nativeStreamingRef.current
            && streamStatusRef.current === "streaming"
            && isExpectedNativeSessionClose(event.reason)
          ) {
            handleExpectedNativeSessionClose(event.reason);
            return;
          }
          const iceState = latestIceConnectionStateRef.current;
          if (
            (hasConfirmedRemoteIceRef.current && iceState === "new") ||
            iceState === "connected" ||
            iceState === "completed" ||
            iceState === "checking"
          ) {
            console.log(`[Recovery] Ignoring signaling disconnect while ICE state is ${iceState}`);
            return;
          }
          // Official-style behavior: if the attach never reached a confirmed remote ICE
          // handshake, do not auto-recover. Fail this attempt and require explicit resume.
          if (!hasConfirmedRemoteIceRef.current) {
            console.warn("[Recovery] Skipping auto-recovery: disconnected before remote ICE handshake");
            clientRef.current?.dispose();
            clientRef.current = null;
            setLaunchError({
              stage: streamStatusToLoadingStage(streamStatusRef.current),
              title: t("errors.sessionConnectionLostTitle"),
              description: t("errors.resumeAttachFailedDescription"),
            });
            resetLaunchRuntime({ keepLaunchError: true, keepStreamingContext: true });
            void refreshNavbarActiveSession();
            launchInFlightRef.current = false;
            return;
          }
          if (remoteIceGraceTimerRef.current !== null) {
            window.clearTimeout(remoteIceGraceTimerRef.current);
            remoteIceGraceTimerRef.current = null;
          }
          remoteIceSeenForSessionRef.current = null;
          awaitingRecoveryRemoteIceRef.current = false;
          if (pendingControlledDisconnectsRef.current > 0) {
            pendingControlledDisconnectsRef.current -= 1;
            console.log("[Recovery] Ignoring controlled signaling disconnect");
            return;
          }
          console.warn("Signaling disconnected:", event.reason);
          const recovered = await attemptSessionRecovery(event.reason).catch((error) => {
            console.error("[Recovery] Signaling recovery failed:", error);
            throw error;
          });
          if (!recovered) {
            if (
              signalingRecoveryRef.current.explicitShutdown
              || !RECOVERABLE_STREAM_STATUSES.includes(streamStatusRef.current)
            ) {
              console.log("[Recovery] Ignoring disconnect after explicit shutdown or non-recoverable status");
              return;
            }
            clientRef.current?.dispose();
            clientRef.current = null;
            setLaunchError({
              stage: streamStatusToLoadingStage(streamStatusRef.current),
              title: t("errors.sessionConnectionLostTitle"),
              description: t("errors.sessionConnectionLostDescription"),
            });
            resetLaunchRuntime({ keepLaunchError: true, keepStreamingContext: true });
            void refreshNavbarActiveSession();
            launchInFlightRef.current = false;
          }
        } else if (event.type === "error") {
          console.error("Signaling error:", event.message);
        }
      } catch (error) {
        if (appUnloadingRef.current) {
          console.log("[Recovery] Suppressing signaling handler errors during app shutdown");
          return;
        }
        if (
          signalingRecoveryRef.current.explicitShutdown
          || !RECOVERABLE_STREAM_STATUSES.includes(streamStatusRef.current)
        ) {
          console.log("[Recovery] Suppressing signaling error after explicit shutdown or non-recoverable status");
          return;
        }
        console.error("Signaling event error:", error);
        clientRef.current?.dispose();
        clientRef.current = null;
        const message = error instanceof Error ? error.message : t("errors.sessionResumeFailedDescription");
        setLaunchError({
          stage: streamStatusToLoadingStage(streamStatusRef.current),
          title: t("errors.sessionConnectionLostTitle"),
          description: message,
        });
        resetLaunchRuntime({ keepLaunchError: true, keepStreamingContext: true });
        void refreshNavbarActiveSession();
        launchInFlightRef.current = false;
      }
    });

    return () => unsubscribe();
  }, [attemptSessionRecovery, diagnosticsStore, handleExpectedNativeSessionClose, markDiscordStreamStarted, nativeInputBridgeReady, refreshNavbarActiveSession, resetLaunchRuntime, scheduleStableRecoveryReset, settings, streamMicLevel, streamVolume, t]);

  // Play game handler
  const handlePlayGame = useCallback(async (game: GameInfo, options?: { bypassGuards?: boolean; streamingBaseUrl?: string; variantId?: string }) => {
    if (!selectedProvider) return;

    console.log("handlePlayGame entry", {
      title: game.title,
      launchInFlight: launchInFlightRef.current,
      streamStatus,
      bypass: options?.bypassGuards ?? false,
    });

    if (!options?.bypassGuards && (launchInFlightRef.current || streamStatus !== "idle" || navbarSessionActionInFlightRef.current)) {
      console.warn("Ignoring play request: launch already in progress or stream not idle", {
        inFlight: launchInFlightRef.current,
        streamStatus,
        navbarSessionAction: navbarSessionActionInFlightRef.current,
      });
      return;
    }

    const selectedVariantId = options?.variantId ?? variantByGameId[game.id] ?? defaultVariantId(game);
    const selectedVariant = getSelectedVariant(game, selectedVariantId);
    const epicOwnershipError = getEpicOwnershipLaunchError(selectedVariant);
    if (epicOwnershipError) {
      setStreamingGame(game);
      setStreamingStore(selectedVariant?.store ?? null);
      setLaunchError({
        stage: "queue",
        title: epicOwnershipError.title,
        description: epicOwnershipError.description,
      });
      return;
    }

    launchInFlightRef.current = true;
    launchAbortRef.current = false;
    resetSignalingRecoveryState();
    let loadingStep: StreamLoadingStatus = "queue";
    const updateLoadingStep = (next: StreamLoadingStatus): void => {
      loadingStep = next;
      setStreamStatus(next);
    };

    setSessionStartedAtMs(null);
    setRemoteStreamWarning(null);
    setLocalSessionTimerWarning(null);
    setLaunchError(null);
    resetStatsOverlayToPreference();
    startPlaytimeSession(game.id);
    updateLoadingStep("queue");
    setQueuePosition(undefined);
    warmNativeStreamerForLaunch();
    let launchGameContext: GameInfo = game;

    try {
      const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;

      // Resolve appId
      let appId: string | null = null;
      if (isNumericId(selectedVariantId)) {
        appId = selectedVariantId;
      } else if (isNumericId(game.launchAppId)) {
        appId = game.launchAppId;
      }

      if (!appId && token) {
        try {
          const resolved = await window.openNow.resolveLaunchAppId({
            token,
            providerStreamingBaseUrl: effectiveStreamingBaseUrl,
            proxyUrl: activeSessionProxyUrl,
            appIdOrUuid: game.uuid ?? selectedVariantId,
          });
          if (resolved && isNumericId(resolved)) {
            appId = resolved;
          }
        } catch {
          // Ignore resolution errors
        }
      }

      if (!appId) {
        throw new Error("Could not resolve numeric appId for this game");
      }

      const numericAppId = Number(appId);
      signalingRecoveryRef.current.appId = numericAppId;
      const matchedGameContext = findSessionContextForAppId(allKnownGames, variantByGameId, numericAppId) ?? {
        game,
        variant: selectedVariant,
      };
      const launchVariant = matchedGameContext.variant ?? selectedVariant;
      launchGameContext = matchedGameContext.game;
      setStreamingGame(matchedGameContext.game);
      setStreamingStore(launchVariant?.store ?? null);

      const launchSubscription = await resolveSubscriptionInfoForLaunch();
      const streamSettings = buildCurrentStreamSettings(launchSubscription);
      const i2pStorageRegionBaseUrl = await resolveInstallToPlayStreamingBaseUrl(
        matchedGameContext.game,
        launchSubscription,
        token || undefined,
      );
      const launchStreamingBaseUrl = i2pStorageRegionBaseUrl ?? options?.streamingBaseUrl ?? effectiveStreamingBaseUrl;
      let existingSessionStrategy: ExistingSessionStrategy | undefined;

      // Check for active sessions first
      if (token) {
        try {
          const activeSessions = await window.openNow.getActiveSessions(token, launchStreamingBaseUrl);
          if (activeSessions.length > 0) {
            // Only claim sessions that are already paused/ready (status 2 or 3).
            // Status=1 sessions are still in queue/setup; sending a RESUME claim
            // skips the queue/ad phase entirely. Let them fall through to
            // createSession so the polling loop handles queue position and ads.
            const matchingSession = activeSessions.find((entry) => entry.appId === numericAppId && (entry.status === 2 || entry.status === 3)) ?? null;
            const otherSession = activeSessions.find((s) => s.status === 2 || s.status === 3) ?? null;

            if (matchingSession) {
              await claimAndConnectSession(matchingSession);
              setNavbarActiveSession(null);
              return;
            }

            if (otherSession) {
              const choice = await window.openNow.showSessionConflictDialog();
              if (choice === "cancel") {
                resetLaunchRuntime();
                return;
              }
              if (choice === "resume") {
                await claimAndConnectSession(otherSession);
                setNavbarActiveSession(null);
                return;
              }
              if (choice === "new") {
                existingSessionStrategy = "force-new";
              }
            }
          }
        } catch (error) {
          console.error("Failed to claim/resume session:", error);
          // Continue to create new session
        }
      }

      const sessionProxyUrl = activeSessionProxyUrl;

      // Create new session
      const newSession = await window.openNow.createSession({
        token: token || undefined,
        streamingBaseUrl: launchStreamingBaseUrl,
        appId,
        internalTitle: game.title,
        accountLinked: chooseAccountLinked(game, selectedVariant),
        enablePersistingInGameSettings: settings.enablePersistingInGameSettings,
        supportsInGameSettingsPersistence: launchVariant?.supportsInGameSettingsPersistence === true,
        existingSessionStrategy,
        proxyUrl: sessionProxyUrl,
        zone: "prod",
        settings: streamSettings,
      });

      setSession(newSession);
      setQueuePosition(newSession.queuePosition);

      // Poll for readiness.
      // Queue and setup/starting modes wait indefinitely until the session becomes ready
      // or the launch is explicitly aborted. Some rigs take much longer than 180s.
      let finalSession: SessionInfo | null = null;
      let latestSession = newSession;
      let isInQueueMode = isSessionInQueue(newSession);
      let attempt = 0;

      while (true) {
        attempt++;

        const pollIntervalMs = shouldUseQueueAdPolling(latestSession, subscriptionInfo, authSession)
          ? SESSION_AD_POLL_INTERVAL_MS
          : SESSION_READY_POLL_INTERVAL_MS;

        // Sleep in small ticks during ad-polling intervals so the loop can react
        // quickly when reportSessionAd clears isAdsRequired (which only updates
        // sessionRef, not the local latestSession variable).  Standard 2 s intervals
        // are kept as a single sleep since they're already short.
        if (pollIntervalMs > SESSION_READY_POLL_INTERVAL_MS) {
          const tickMs = 500;
          let elapsed = 0;
          while (elapsed < pollIntervalMs) {
            await sleep(tickMs);
            elapsed += tickMs;
            if (launchAbortRef.current) return;
            // Sync ad-action responses from sessionRef into the local tracking variable
            // so shouldUseQueueAdPolling sees the updated adState immediately.
            const refSession = sessionRef.current;
            if (refSession && refSession.sessionId === latestSession.sessionId) {
              latestSession = mergePolledSessionState(latestSession, refSession);
            }
            // Break out of the sleep early when ads are no longer required.
            if (!shouldUseQueueAdPolling(latestSession, subscriptionInfo, authSession)) {
              break;
            }
          }
        } else {
          await sleep(pollIntervalMs);
        }

        if (shouldUseQueueAdPolling(latestSession, subscriptionInfo, authSession) && queueAdPlaybackRef.current) {
          const graceDeadline = Date.now() + 5000;
          while (queueAdPlaybackRef.current && Date.now() < graceDeadline) {
            await sleep(200);
            if (launchAbortRef.current) {
              return;
            }
          }
        }

        if (launchAbortRef.current) {
          return;
        }

        if (launchAbortRef.current) {
          return;
        }

        const polled = await window.openNow.pollSession({
          token: token || undefined,
          streamingBaseUrl: newSession.streamingBaseUrl ?? effectiveStreamingBaseUrl,
          serverIp: newSession.serverIp,
          zone: newSession.zone,
          sessionId: newSession.sessionId,
          clientId: newSession.clientId,
          deviceId: newSession.deviceId,
          proxyUrl: sessionProxyUrl,
        });

        if (launchAbortRef.current) {
          return;
        }

        const mergedSession = mergePolledSessionState(latestSession, polled);
        latestSession = mergedSession;

        setSession(mergedSession);
        setQueuePosition(mergedSession.queuePosition);

        // Check if queue just cleared so the loading UI can transition to setup mode.
        isInQueueMode = isSessionInQueue(mergedSession);

        console.log(
          `Poll attempt ${attempt}: status=${mergedSession.status}, seatSetupStep=${mergedSession.seatSetupStep ?? "n/a"}, queuePosition=${mergedSession.queuePosition ?? "n/a"}, serverIp=${mergedSession.serverIp}, queueMode=${isInQueueMode}, adsRequired=${isSessionAdsRequired(mergedSession.adState)}`,
        );

        if (isSessionReadyForConnect(mergedSession.status)) {
          finalSession = mergedSession;
          break;
        }

        // Update status based on session state
        if (isInQueueMode) {
          updateLoadingStep("queue");
        } else if (mergedSession.status === 1) {
          updateLoadingStep("setup");
        }

      }

      // finalSession is guaranteed to be set here (we only exit the loop via break when session is ready)

      setQueuePosition(undefined);
      updateLoadingStep("connecting");

      // Use finalSession (the status=2 poll result) as the authoritative source for
      // signaling coordinates — it carries the real server IP resolved at the moment
      // the rig became ready. sessionRef.current may still hold stale zone-LB data
      // from a prior React render cycle.
      const sessionToConnect = finalSession ?? sessionRef.current ?? newSession;
      console.log("Connecting signaling with:", {
        sessionId: sessionToConnect.sessionId,
        signalingServer: sessionToConnect.signalingServer,
        signalingUrl: sessionToConnect.signalingUrl,
        status: sessionToConnect.status,
      });

      await window.openNow.connectSignaling(buildSignalingConnectRequest(sessionToConnect));
    } catch (error) {
      if (launchAbortRef.current) {
        return;
      }
      console.error("Launch failed:", error);
      setLaunchError(toLaunchErrorState(t, error, loadingStep, launchGameContext));
      await disconnectSignalingControlled();
      clientRef.current?.dispose();
      clientRef.current = null;
      resetLaunchRuntime({ keepLaunchError: true, keepStreamingContext: true });
      void refreshNavbarActiveSession();
    } finally {
      launchInFlightRef.current = false;
    }
  }, [
    authSession,
    activeSessionProxyUrl,
    allKnownGames,
    buildCurrentStreamSettings,
    buildSignalingConnectRequest,
    claimAndConnectSession,
    effectiveStreamingBaseUrl,
    refreshNavbarActiveSession,
    resetSignalingRecoveryState,
    resetLaunchRuntime,
    resetStatsOverlayToPreference,
    resolveInstallToPlayStreamingBaseUrl,
    resolveSubscriptionInfoForLaunch,
    selectedProvider,
    settings.enablePersistingInGameSettings,
    streamStatus,
    t,
    variantByGameId,
    warmNativeStreamerForLaunch,
  ]);

  useEffect(() => {
    const request = pendingDirectLaunchRequest;
    if (!request || handledDirectLaunchIdsRef.current.has(request.id)) return;
    if (!authSession || isInitializing || isLoadingCatalog || isLoadingLibrary) return;
    if (
      launchInFlightRef.current ||
      streamStatus !== "idle" ||
      isResumingNavbarSession ||
      isTerminatingNavbarSession ||
      navbarSessionActionInFlightRef.current
    ) {
      return;
    }
    if (directLaunchAttemptIdRef.current === request.id) return;

    directLaunchAttemptIdRef.current = request.id;
    let cancelled = false;

    const launch = async (): Promise<void> => {
      let target = findDirectLaunchTarget(request, allKnownGames, variantByGameId);
      const token = authSession.tokens.idToken ?? authSession.tokens.accessToken;

      if (!target && request.title && token) {
        try {
          const searchResult = await window.openNow.browseCatalog({
            token,
            providerStreamingBaseUrl: effectiveStreamingBaseUrl,
            proxyUrl: activeSessionProxyUrl,
            searchQuery: request.title,
            sortId: "relevance",
            filterIds: [],
            fetchCount: 25,
          });
          target = findDirectLaunchTarget(request, searchResult.games, variantByGameId);
        } catch (error) {
          console.warn("Direct launch catalog search failed:", error);
        }
      }

      const requestedAppId = request.appId && isNumericId(request.appId) ? request.appId : undefined;
      if (!target && requestedAppId) {
        target = {
          game: createSyntheticDirectLaunchGame(request, requestedAppId),
          variantId: requestedAppId,
        };
      }

      if (cancelled) return;

      if (
        launchInFlightRef.current ||
        streamStatusRef.current !== "idle" ||
        navbarSessionActionInFlightRef.current
      ) {
        directLaunchAttemptIdRef.current = null;
        return;
      }

      handledDirectLaunchIdsRef.current.add(request.id);
      directLaunchAttemptIdRef.current = null;
      setPendingDirectLaunchRequest((previous) => previous?.id === request.id ? null : previous);

      if (!target) {
        const requestedName = request.title?.trim() || request.appId || t("app.labels.game");
        setLaunchError({
          stage: "queue",
          title: t("errors.directLaunchNotFoundTitle"),
          description: t("errors.directLaunchNotFoundDescription", { value: requestedName }),
        });
        return;
      }

      void handlePlayGame(target.game, { variantId: target.variantId });
    };

    void launch();

    return () => {
      cancelled = true;
      if (directLaunchAttemptIdRef.current === request.id) {
        directLaunchAttemptIdRef.current = null;
      }
    };
  }, [
    allKnownGames,
    activeSessionProxyUrl,
    authSession,
    effectiveStreamingBaseUrl,
    handlePlayGame,
    isInitializing,
    isLoadingCatalog,
    isLoadingLibrary,
    isResumingNavbarSession,
    isTerminatingNavbarSession,
    pendingDirectLaunchRequest,
    streamStatus,
    t,
    variantByGameId,
  ]);

  // Gate handler: shows queue server modal for FREE-tier users before launching
  const handleInitiatePlay = useCallback(async (game: GameInfo) => {
    const effectiveTier = normalizeMembershipTier(
      subscriptionInfo?.membershipTier ?? authSession?.user.membershipTier,
    );
    const isFreeUser = effectiveTier === "FREE";
    const activeProvider = authSession?.provider ?? selectedProvider;
    const isNvidiaAccount = isNvidiaProvider(activeProvider);
    const isAllianceServer = isAllianceStreamingBaseUrl(effectiveStreamingBaseUrl);
    if (!isNvidiaAccount || isAllianceServer) {
      setQueueModalData(null);
      void handlePlayGame(game);
      return;
    }
    if (settings.hideServerSelector) {
      setQueueModalData(null);
      void handlePlayGame(game);
      return;
    }
    if (isFreeUser && streamStatus === "idle" && !launchInFlightRef.current) {
      try {
        const [queueResult, mappingResult] = await Promise.allSettled([
          window.openNow.fetchPrintedWasteQueue(),
          window.openNow.fetchPrintedWasteServerMapping(),
        ]);

        if (queueResult.status !== "fulfilled" || mappingResult.status !== "fulfilled") {
          console.warn(
            "[QueueServerSelect] PrintedWaste unavailable, skipping queue checks and launching with default routing.",
            {
              queueStatus: queueResult.status,
              mappingStatus: mappingResult.status,
            },
          );
          setQueueModalData(null);
          void handlePlayGame(game);
          return;
        }

        const queueData = queueResult.value;
        if (!queueData || Object.keys(queueData).length === 0) {
          setQueueModalData(null);
          void handlePlayGame(game);
          return;
        }

        if (!hasAnyEligiblePrintedWasteZone(queueData, mappingResult.value)) {
          console.warn(
            "[QueueServerSelect] No eligible non-nuked PrintedWaste zones available, skipping queue checks.",
          );
          setQueueModalData(null);
          void handlePlayGame(game);
          return;
        }

        setQueueModalData(queueData);
        setQueueModalGame(game);
      } catch (error) {
        console.warn("[QueueServerSelect] PrintedWaste queue checks failed, launching without modal.", error);
        setQueueModalData(null);
        void handlePlayGame(game);
      }
      return;
    }
    void handlePlayGame(game);
  }, [subscriptionInfo, authSession, selectedProvider, settings.hideServerSelector, streamStatus, handlePlayGame, effectiveStreamingBaseUrl]);

  const handleQueueModalConfirm = useCallback((zoneUrl: string | null) => {
    const game = queueModalGame;
    setQueueModalGame(null);
    setQueueModalData(null);
    if (game) {
      void handlePlayGame(game, { streamingBaseUrl: zoneUrl ?? undefined });
    }
  }, [queueModalGame, handlePlayGame]);

  const handleQueueModalCancel = useCallback(() => {
    setQueueModalGame(null);
    setQueueModalData(null);
  }, []);

  const handleOpenStoreUrl = useCallback((url: string): void => {
    void window.openNow.openExternalUrl(url).catch((error) => {
      console.error("Failed to open Store URL:", error);
    });
  }, []);

  const handleBuyGame = useCallback((game: GameInfo, selectedVariantId?: string): void => {
    const selectedVariant = getSelectedVariant(game, selectedVariantId ?? defaultVariantId(game));
    const localStoreUrl = selectedVariant?.storeUrl
      ?? game.variants.find((variant) => variant.storeUrl)?.storeUrl;
    if (localStoreUrl) {
      handleOpenStoreUrl(localStoreUrl);
      return;
    }

    const token = authSession?.tokens.idToken ?? authSession?.tokens.accessToken;
    if (!token) return;

    void window.openNow.resolveStoreUrl({
      token,
      providerStreamingBaseUrl: effectiveStreamingBaseUrl,
      proxyUrl: activeSessionProxyUrl,
      appIdOrUuid: game.uuid ?? game.id,
      variantId: selectedVariant?.id ?? selectedVariantId,
      store: selectedVariant?.store,
    }).then((storeUrl) => {
      if (storeUrl) handleOpenStoreUrl(storeUrl);
    }).catch((error) => {
      console.error("Failed to resolve Store URL:", error);
    });
  }, [activeSessionProxyUrl, authSession, effectiveStreamingBaseUrl, handleOpenStoreUrl]);

  const closeRemoveAccountConfirm = (): void => {
    setRemoveAccountConfirmOpen(false);
    setAccountToRemove(null);
  };

  const logoutConfirmModal = (
    <ModalSurface
      open={logoutConfirmOpen}
      onClose={() => setLogoutConfirmOpen(false)}
      onConfirm={() => {
        void confirmLogout();
      }}
      onExitComplete={() => setLogoutConfirmSurfacePresent(false)}
      motion="compact"
      overlayClassName="logout-confirm"
      backdropClassName="logout-confirm-backdrop"
      panelClassName="logout-confirm-card"
      ariaLabel={t("auth.accounts.logOutConfirmation")}
      backdropLabel={t("auth.accounts.cancelLogOut")}
      initialFocusRef={logoutConfirmCancelRef}
      restoreFocusRef={accountConfirmRestoreFocusRef}
    >
      <div className="logout-confirm-kicker">{t("auth.accounts.kicker")}</div>
      <h3 className="logout-confirm-title">{t("auth.accounts.signOutAllTitle")}</h3>
      <p className="logout-confirm-text">
        {t("auth.accounts.signOutAllDescription")}
      </p>
      <p className="logout-confirm-subtext">
        {t("auth.accounts.signOutAllSubtext")}
      </p>
      <div className="logout-confirm-actions">
        <button
          ref={logoutConfirmCancelRef}
          type="button"
          className="logout-confirm-btn logout-confirm-btn-cancel"
          onClick={() => setLogoutConfirmOpen(false)}
        >
          {t("auth.accounts.staySignedIn")}
        </button>
        <button
          type="button"
          className="logout-confirm-btn logout-confirm-btn-confirm"
          onClick={() => {
            void confirmLogout();
          }}
        >
          {t("auth.accounts.signOutAll")}
        </button>
      </div>
      <div className="logout-confirm-hint">
        <kbd>Enter</kbd> {t("app.actions.confirm")} · <kbd>Esc</kbd> {t("app.actions.cancel")}
      </div>
    </ModalSurface>
  );

  const removeAccountConfirmModal = (
    <ModalSurface
      open={removeAccountConfirmOpen}
      onClose={closeRemoveAccountConfirm}
      onConfirm={() => {
        void confirmRemoveAccount();
      }}
      onExitComplete={() => setRemoveAccountConfirmSurfacePresent(false)}
      motion="compact"
      overlayClassName="logout-confirm"
      backdropClassName="logout-confirm-backdrop"
      panelClassName="logout-confirm-card"
      ariaLabel={t("auth.accounts.removeAccountConfirmation")}
      backdropLabel={t("auth.accounts.cancelAccountRemoval")}
      initialFocusRef={removeAccountConfirmCancelRef}
      restoreFocusRef={accountConfirmRestoreFocusRef}
    >
      <div className="logout-confirm-kicker">{t("auth.accounts.kicker")}</div>
      <h3 className="logout-confirm-title">{t("auth.accounts.removeAccountTitle")}</h3>
      <p className="logout-confirm-text">
        {t("auth.accounts.removeAccountDescription", { name: accountToRemoveDisplayName })}
      </p>
      <p className="logout-confirm-subtext">
        {t("auth.accounts.removeAccountSubtext")}
      </p>
      <div className="logout-confirm-actions">
        <button
          ref={removeAccountConfirmCancelRef}
          type="button"
          className="logout-confirm-btn logout-confirm-btn-cancel"
          onClick={closeRemoveAccountConfirm}
        >
          {t("app.actions.cancel")}
        </button>
        <button
          type="button"
          className="logout-confirm-btn logout-confirm-btn-confirm"
          onClick={() => {
            void confirmRemoveAccount();
          }}
        >
          {t("app.actions.remove")}
        </button>
      </div>
      <div className="logout-confirm-hint">
        <kbd>Enter</kbd> {t("app.actions.confirm")} · <kbd>Esc</kbd> {t("app.actions.cancel")}
      </div>
    </ModalSurface>
  );

  const handleResumeFromNavbar = useCallback(async () => {
    if (
      !selectedProvider
      || !navbarActiveSession
      || isResumingNavbarSession
      || isTerminatingNavbarSession
      || navbarSessionActionInFlightRef.current
    ) {
      return;
    }
    if (launchInFlightRef.current || streamStatus !== "idle") {
      return;
    }

    navbarSessionActionInFlightRef.current = "resume";
    launchInFlightRef.current = true;
    resetSignalingRecoveryState();
    setIsResumingNavbarSession(true);
    let loadingStep: StreamLoadingStatus = "setup";
    const updateLoadingStep = (next: StreamLoadingStatus): void => {
      loadingStep = next;
      setStreamStatus(next);
    };

    setLaunchError(null);
    setQueuePosition(undefined);
    setSessionStartedAtMs(null);
    setRemoteStreamWarning(null);
    setLocalSessionTimerWarning(null);
    resetStatsOverlayToPreference();
    const matchedContext = findGameContextForSession(navbarActiveSession);
    let resumeGameContext: GameInfo | null = null;
    if (matchedContext) {
      resumeGameContext = matchedContext.game;
      setStreamingGame(matchedContext.game);
      setStreamingStore(matchedContext.variant?.store ?? null);
    } else {
      setStreamingStore(null);
    }
    updateLoadingStep("setup");

    try {
      signalingRecoveryRef.current.appId = navbarActiveSession.appId;
      await claimAndConnectSession(navbarActiveSession);
      setNavbarActiveSession(null);
    } catch (error) {
      console.error("Navbar resume failed:", error);
      setLaunchError(toLaunchErrorState(t, error, loadingStep, resumeGameContext));
      await disconnectSignalingControlled();
      clientRef.current?.dispose();
      clientRef.current = null;
      resetLaunchRuntime({ keepLaunchError: true });
      void refreshNavbarActiveSession();
    } finally {
      navbarSessionActionInFlightRef.current = null;
      launchInFlightRef.current = false;
      setIsResumingNavbarSession(false);
    }
  }, [
    claimAndConnectSession,
    isTerminatingNavbarSession,
    isResumingNavbarSession,
    navbarActiveSession,
    findGameContextForSession,
    refreshNavbarActiveSession,
    resetSignalingRecoveryState,
    resetLaunchRuntime,
    resetStatsOverlayToPreference,
    selectedProvider,
    streamStatus,
    t,
  ]);

  const handleTerminateNavbarSession = useCallback(async () => {
    if (
      !navbarActiveSession
      || isResumingNavbarSession
      || isTerminatingNavbarSession
      || navbarSessionActionInFlightRef.current
    ) {
      return;
    }
    if (launchInFlightRef.current || streamStatus !== "idle") {
      return;
    }

    const activeSessionTitle = gameTitleByAppId.get(navbarActiveSession.appId)?.trim() || t("session.thisSession");
    if (!window.confirm(t("session.terminateConfirmation", { title: activeSessionTitle }))) {
      return;
    }

    navbarSessionActionInFlightRef.current = "terminate";
    setIsTerminatingNavbarSession(true);
    try {
      await stopSessionByTarget({
        sessionId: navbarActiveSession.sessionId,
        zone: "",
        streamingBaseUrl: navbarActiveSession.streamingBaseUrl ?? (effectiveStreamingBaseUrl || undefined),
        serverIp: navbarActiveSession.serverIp,
      });
      setNavbarActiveSession(null);
    } catch (error) {
      console.error("Navbar terminate failed:", error);
    } finally {
      navbarSessionActionInFlightRef.current = null;
      setIsTerminatingNavbarSession(false);
      void refreshNavbarActiveSession();
    }
  }, [
    effectiveStreamingBaseUrl,
    gameTitleByAppId,
    isResumingNavbarSession,
    isTerminatingNavbarSession,
    navbarActiveSession,
    refreshNavbarActiveSession,
    stopSessionByTarget,
    streamStatus,
    t,
  ]);

  // Stop stream handler
  const handleStopStream = useCallback(async () => {
    try {
      resolveExitPrompt(false);
      const status = streamStatusRef.current;
      if (status !== "idle" && status !== "streaming") {
        launchAbortRef.current = true;
      }
      markExplicitSignalingShutdown();
      await disconnectSignalingControlled();

      const current = sessionRef.current;
      if (current) {
        await stopSessionByTarget({
          streamingBaseUrl: current.streamingBaseUrl,
          serverIp: current.serverIp,
          zone: current.zone,
          sessionId: current.sessionId,
          clientId: current.clientId,
          deviceId: current.deviceId,
        });
      }

      clientRef.current?.dispose();
      clientRef.current = null;
      setNavbarActiveSession(null);
      if (streamingGame) endPlaytimeSession(streamingGame.id);
      resetLaunchRuntime();
      void refreshNavbarActiveSession();
    } catch (error) {
      console.error("Stop failed:", error);
    }
  }, [endPlaytimeSession, markExplicitSignalingShutdown, refreshNavbarActiveSession, resetLaunchRuntime, resolveExitPrompt, stopSessionByTarget, streamingGame]);

  const handleDismissLaunchError = useCallback(async () => {
    markExplicitSignalingShutdown();
    await disconnectSignalingControlled();
    clientRef.current?.dispose();
    clientRef.current = null;
    resetLaunchRuntime();
    void refreshNavbarActiveSession();
  }, [markExplicitSignalingShutdown, refreshNavbarActiveSession, resetLaunchRuntime]);

  const handleLaunchErrorAction = useCallback((): void => {
    if (launchError?.action !== "persistent-storage-settings") return;
    void (async () => {
      try {
        await handleDismissLaunchError();
      } finally {
        setSettingsFocusSection("account");
        if (currentPage !== "settings") {
          setPageBeforeSettings(currentPage);
        }
        setCurrentPage("settings");
      }
    })();
  }, [currentPage, handleDismissLaunchError, launchError?.action]);

  const releasePointerLockIfNeeded = useCallback(async () => {
    if (document.pointerLockElement) {
      clientRef.current?.suppressNextSyntheticEscapeOnPointerLockLoss();
      document.exitPointerLock();
      await sleep(75);
    }
  }, []);

  const handlePromptedStopStream = useCallback(async () => {
    if (streamStatus === "idle") {
      return;
    }

    await releasePointerLockIfNeeded();

    const loadingPhases: StreamStatus[] = ["queue", "setup", "starting", "connecting"];
    if (loadingPhases.includes(streamStatus)) {
      launchAbortRef.current = true;
      await handleStopStream();
      return;
    }

    const gameName = (streamingGame?.title || t("session.thisGame")).trim();
    const shouldExit = await requestExitPrompt(gameName);
    if (!shouldExit) {
      return;
    }

    await handleStopStream();
  }, [handleStopStream, releasePointerLockIfNeeded, requestExitPrompt, streamStatus, streamingGame?.title, t]);

  const handleStreamShortcutAction = useCallback((action: NativeStreamerShortcutAction): void => {
    switch (action) {
      case "toggleStats":
        setShowStatsOverlay((prev) => !prev);
        return;
      case "togglePointerLock":
        if (nativeStreamingRef.current) {
          // Native streamer toggles OS input capture locally in the renderer window.
          return;
        }
        {
          const targetVideo = videoRef.current;
          if (streamStatus === "streaming" && targetVideo) {
            if (document.pointerLockElement === targetVideo) {
              clientRef.current?.suppressNextSyntheticEscapeOnPointerLockLoss();
              document.exitPointerLock();
            } else {
              void requestPointerLockCapture(targetVideo);
            }
          }
        }
        return;
      case "toggleFullscreen":
        if (streamStatus === "connecting" || streamStatus === "streaming") {
          void toggleSessionFullscreen();
        }
        return;
      case "stopStream":
        void handlePromptedStopStream();
        return;
      case "toggleAntiAfk":
        if (streamStatus === "streaming") {
          setAntiAfkEnabled((prev) => !prev);
          setAntiAfkAckNonce((n) => n + 1);
        }
        return;
      case "toggleMicrophone":
        if (streamStatus === "streaming") {
          clientRef.current?.toggleMicrophone();
        }
        return;
      case "screenshot":
      case "toggleRecording":
        if (streamStatus === "streaming" && !nativeStreamingRef.current) {
          dispatchStreamShortcutAction(action);
        }
        return;
    }
  }, [handlePromptedStopStream, requestPointerLockCapture, streamStatus, toggleSessionFullscreen]);

  useEffect(() => {
    handleStreamShortcutActionRef.current = handleStreamShortcutAction;
  }, [handleStreamShortcutAction]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const isTyping = !!target && (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable
      );
      if (isTyping) {
        return;
      }

      if (exitPrompt.open) {
        if (e.key === "Escape") {
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          handleExitPromptCancel();
        } else if (e.key === "Enter") {
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          handleExitPromptConfirm();
        }
        return;
      }

      const isPasteShortcut = e.key.toLowerCase() === "v" && !e.altKey && !e.shiftKey && (e.ctrlKey || (isMac && e.metaKey));
      if (streamStatus === "streaming" && isPasteShortcut) {
        // Always stop local/browser paste behavior while streaming.
        // If clipboard paste is enabled, send clipboard text into the stream.
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();

        if (settings.clipboardPaste) {
          void (async () => {
            await sendStreamClipboardPaste(clientRef.current);
          })();
        }
        return;
      }

      if (isShortcutMatch(e, shortcuts.toggleStats)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        handleStreamShortcutAction("toggleStats");
        return;
      }

      if (isShortcutMatch(e, shortcuts.togglePointerLock)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        handleStreamShortcutAction("togglePointerLock");
        return;
      }

      if (isShortcutMatch(e, shortcuts.toggleFullscreen)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        if (streamStatus === "connecting" || streamStatus === "streaming") {
          void toggleSessionFullscreen();
        }
        return;
      }

      if (isShortcutMatch(e, shortcuts.stopStream)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        void handlePromptedStopStream();
        return;
      }

      if (isShortcutMatch(e, shortcuts.toggleAntiAfk)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        if (streamStatus === "streaming") {
          setAntiAfkEnabled((prev) => !prev);
          setAntiAfkAckNonce((n) => n + 1);
        }
        return;
      }

      if (isShortcutMatch(e, shortcuts.toggleMicrophone)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        if (streamStatus === "streaming") {
          clientRef.current?.toggleMicrophone();
        }
      }
    };

    // Use capture phase so app shortcuts run before stream input capture listeners.
    window.addEventListener("keydown", handleKeyDown, true);
    return () => window.removeEventListener("keydown", handleKeyDown, true);
  }, [
    exitPrompt.open,
    handleExitPromptCancel,
    handleExitPromptConfirm,
    handlePromptedStopStream,
    requestPointerLockCapture,
    settings.clipboardPaste,
    shortcuts,
    streamStatus,
    toggleSessionFullscreen,
  ]);

  const filteredGames = games;

  const filteredLibraryGames = useMemo(() => {
    const query = searchQuery.trim();
    const searched = query ? libraryGames.filter((game) => matchesGameSearch(game, query)) : libraryGames;
    return sortLibraryGames(
      searched,
      catalogSelectedSortId === "relevance" ? "last_played" : catalogSelectedSortId,
      playtime,
    );
  }, [libraryGames, searchQuery, catalogSelectedSortId, playtime]);

  const activeSessionAppIds = useMemo(
    () => (navbarActiveSession ? [navbarActiveSession.appId] : []),
    [navbarActiveSession?.appId],
  );

  const activeSessionGameTitle = useMemo(() => {
    if (!navbarActiveSession) return null;
    const mappedTitle = gameTitleByAppId.get(navbarActiveSession.appId);
    if (mappedTitle) {
      return mappedTitle;
    }
    if (session?.sessionId === navbarActiveSession.sessionId && streamingGame?.title) {
      return streamingGame.title;
    }
    return null;
  }, [gameTitleByAppId, navbarActiveSession, session?.sessionId, streamingGame?.title]);

  const navigateControllerPage = useCallback((direction: -1 | 1): void => {
    const pages: AppPage[] = ["library", "home", "settings"];
    const currentIndex = Math.max(0, pages.indexOf(currentPage));
    const nextIndex = (currentIndex + direction + pages.length) % pages.length;
    const nextPage = pages[nextIndex];
    if (nextPage === "settings" && currentPage !== "settings") {
      setPageBeforeSettings(currentPage);
    }
    setCurrentPage(nextPage);
  }, [currentPage]);

  const handleNavigate = useCallback((page: AppPage): void => {
    if (page === "settings" && currentPage !== "settings") {
      setPageBeforeSettings(currentPage);
    }
    setCurrentPage(page);
  }, [currentPage]);

  const handleCloseSettings = useCallback((): void => {
    setSettingsFocusSection(undefined);
    setCurrentPage(pageBeforeSettings);
  }, [pageBeforeSettings]);

  const handleOpenWhatsNew = useCallback((): void => {
    // Fetch current-version highlights and open modal in manual mode (no auto-ack)
    void window.openNow.getReleaseHighlights()
      .then((payload) => {
        setReleaseHighlightsPayload(payload);
        setReleaseHighlightsIsAuto(false);
      })
      .catch((error) => {
        console.warn("[App] Failed to fetch release highlights:", error);
      });
  }, []);

  const handleDismissReleaseHighlights = useCallback((): void => {
    if (releaseHighlightsIsAuto) {
      void window.openNow.ackReleaseHighlights().catch((err) => {
        console.warn("[App] Failed to ack release highlights:", err);
      });
    }
    setReleaseHighlightsPayload(null);
    setReleaseHighlightsIsAuto(false);
  }, [releaseHighlightsIsAuto]);

  const handleErrorReportingConsent = useCallback(async (granted: boolean): Promise<void> => {
    await updateSetting("errorReportingConsent", granted ? "granted" : "denied");
    try {
      const refreshed = await window.openNow.getSettings();
      setSettings((prev) => ({
        ...prev,
        errorReportingConsent: refreshed.errorReportingConsent,
        telemetryInstallId: refreshed.telemetryInstallId,
      }));
    } catch (error) {
      console.warn("[Telemetry] Failed to refresh settings after consent change:", error);
    }
  }, [updateSetting]);

  const showErrorReportingConsent = settingsLoaded && settings.errorReportingConsent === "unset";

  const mainPage: AppPage = currentPage === "settings" ? pageBeforeSettings : currentPage;

  // Show login screen if not authenticated
  if (!authSession) {
    return (
      <>
        <LoginScreen
          providers={providers}
          selectedProviderId={providerIdpId}
          onProviderChange={setProviderIdpId}
          onLogin={handleLogin}
          onQrLogin={handleQrLogin}
          onCancelQrLogin={handleCancelQrLogin}
          isLoading={isLoggingIn}
          error={loginError}
          isInitializing={isInitializing}
          statusMessage={startupStatusMessage}
          qrLoginChallenge={qrLoginChallenge}
          isQrLoginPending={activeLoginMode === "qr" && !qrLoginChallenge}
        />
        <ReleaseHighlightsModal
          payload={releaseHighlightsPayload}
          onDismiss={handleDismissReleaseHighlights}
          onExitComplete={() => setReleaseHighlightsSurfacePresent(false)}
        />
        <ErrorReportingConsentModal
          open={showErrorReportingConsent}
          onAccept={() => {
            void handleErrorReportingConsent(true);
          }}
          onDecline={() => {
            void handleErrorReportingConsent(false);
          }}
          onExitComplete={() => setConsentSurfacePresent(false)}
        />
        <FeedbackModal
          open={feedbackOpen}
          settings={settings}
          onClose={() => setFeedbackOpen(false)}
          onExitComplete={() => setFeedbackSurfacePresent(false)}
        />
      </>
    );
  }

  const showLaunchOverlay = streamStatus !== "idle" || launchError !== null;
  const hasActiveStreamView = streamStatus !== "idle";
  const showLaunchErrorOverlay = launchError !== null;
  const showDesktopLaunchLoading = showLaunchErrorOverlay
    || (streamStatus !== "idle" && streamRevealPhase !== "revealed");
  const loadingStatus = launchError
    ? launchError.stage
    : streamStatus === "streaming"
      ? "connecting"
      : toLoadingStatus(streamStatus);
  const showCatalogAtmosphere = mainPage === "home" || mainPage === "library";
  const shellBlocked = showLaunchOverlay
    || streamSurfacePresent
    || launchSurfacePresent
    || currentPage === "settings"
    || settingsSurfacePresent
    || navbarOverlayBlocking
    || queueModalGame !== null
    || releaseHighlightsPayload !== null
    || releaseHighlightsSurfacePresent
    || showErrorReportingConsent
    || consentSurfacePresent
    || feedbackOpen
    || feedbackSurfacePresent
    || logoutConfirmOpen
    || logoutConfirmSurfacePresent
    || removeAccountConfirmOpen
    || removeAccountConfirmSurfacePresent;
  const catalogSurfaceActive = !shellBlocked;

  return (
    <div className={`app-container${effectiveControllerMode ? " app-container--controller" : ""}${showCatalogAtmosphere ? " app-container--atmosphere" : ""}`} style={getAppStyle(settings.posterSizeScale)}>
      <div
        className="app-shell"
        inert={shellBlocked ? true : undefined}
        aria-hidden={shellBlocked || undefined}
      >
      {showCatalogAtmosphere && (
        <LazyShaderAtmosphere
          variant="controller"
          className="catalog-atmosphere"
          active={catalogSurfaceActive}
        />
      )}
      <AnimatePresence>
        {startupRefreshNotice && (
          <m.div
            key="startup-refresh-notice"
            className={`auth-refresh-notice auth-refresh-notice--${startupRefreshNotice.tone}`}
            {...overlayMotion}
          >
            {startupRefreshNotice.text}
          </m.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {catalogActionNotice && (
          <m.div
            key="catalog-action-notice"
            className={`auth-refresh-notice auth-refresh-notice--${catalogActionNotice.tone}`}
            {...overlayMotion}
          >
            {catalogActionNotice.text}
          </m.div>
        )}
      </AnimatePresence>
      <Navbar
        currentPage={currentPage}
        onNavigate={handleNavigate}
        user={authSession.user}
        subscription={subscriptionInfo}
        activeSession={navbarActiveSession}
        activeSessionGameTitle={activeSessionGameTitle}
        isResumingSession={isResumingNavbarSession}
        isTerminatingSession={isTerminatingNavbarSession}
        onResumeSession={() => {
          void handleResumeFromNavbar();
        }}
        onTerminateSession={() => {
          void handleTerminateNavbarSession();
        }}
        savedAccounts={savedAccounts}
        onSwitchAccount={handleSwitchAccount}
        onRemoveAccount={(userId, restoreFocusTarget) => {
          accountConfirmRestoreFocusRef.current = restoreFocusTarget ?? null;
          void handleRemoveAccount(userId);
        }}
        onAddAccount={handleAddAccount}
        onLogoutAll={(restoreFocusTarget) => {
          accountConfirmRestoreFocusRef.current = restoreFocusTarget ?? null;
          handleLogout();
        }}
        onOpenFeedback={() => setFeedbackOpen(true)}
        onBlockingOverlayChange={setNavbarOverlayBlocking}
        controllerMode={effectiveControllerMode}
      />

      <main className="main-content">
        <PageErrorBoundary label="main">
        <AnimatePresence mode="wait" initial={false}>
          <m.div
            key={mainPage}
            className="page-transition-surface"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={pageTransition}
          >
            {mainPage === "home" && (
              <HomePage
                games={filteredGames}
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                onPlayGame={handleInitiatePlay}
                isLoading={effectiveControllerMode ? isLoadingStorePanels : isLoadingCatalog}
                selectedGameId={selectedGameId}
                onSelectGame={setSelectedGameId}
                selectedVariantByGameId={variantByGameId}
                onSelectGameVariant={handleSelectGameVariant}
                filterGroups={catalogFilterGroups}
                selectedFilterIds={catalogSelectedFilterIds}
                onToggleFilter={handleToggleCatalogFilter}
                sortOptions={catalogSortOptions}
                selectedSortId={catalogSelectedSortId}
                onSortChange={setCatalogSelectedSortId}
                totalCount={catalogTotalCount}
                supportedCount={catalogSupportedCount}
                controllerMode={effectiveControllerMode}
                surfaceActive={catalogSurfaceActive}
                storePanels={storePanels}
                storeHeroGames={featuredGames}
                activeSessionAppIds={activeSessionAppIds}
                onBuyGame={handleBuyGame}
                onMarkGameOwned={handleMarkGameOwned}
                markOwnedInFlightByVariantId={markOwnedInFlightByVariantId}
                onPreviousControllerPage={() => navigateControllerPage(-1)}
                onNextControllerPage={() => navigateControllerPage(1)}
              />
            )}

            {mainPage === "library" && (
              <PageErrorBoundary label="library">
                <LibraryPage
                  games={filteredLibraryGames}
                  allGames={libraryGames}
                  playtimeData={playtime}
                  searchQuery={searchQuery}
                  onSearchChange={setSearchQuery}
                  onPlayGame={handleInitiatePlay}
                  isLoading={isLoadingLibrary}
                  selectedGameId={selectedGameId}
                  onSelectGame={setSelectedGameId}
                  selectedVariantByGameId={variantByGameId}
                  onSelectGameVariant={handleSelectGameVariant}
                  libraryCount={libraryGames.length}
                  sortOptions={catalogSortOptions.filter((option) => option.id !== "relevance")}
                  selectedSortId={catalogSelectedSortId === "relevance" ? "last_played" : catalogSelectedSortId}
                  onSortChange={setCatalogSelectedSortId}
                  controllerMode={effectiveControllerMode}
                  surfaceActive={catalogSurfaceActive}
                  featuredGames={featuredGames.length > 0 ? featuredGames : games}
                  activeSessionAppIds={activeSessionAppIds}
                  onBuyGame={handleBuyGame}
                  onPreviousControllerPage={() => navigateControllerPage(-1)}
                  onNextControllerPage={() => navigateControllerPage(1)}
                />
              </PageErrorBoundary>
            )}
          </m.div>
        </AnimatePresence>
        </PageErrorBoundary>
      </main>
      </div>

      <AnimatePresence
        initial={false}
        onExitComplete={() => setStreamSurfacePresent(false)}
      >
        {hasActiveStreamView && (
          <m.div
            key="stream-view-layer"
            className="stream-view-layer"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={pageTransition}
          >
            <StreamView
              videoRef={videoRef}
              audioRef={audioRef}
              diagnosticsStore={diagnosticsStore}
              showStats={showStatsOverlay}
              showNativeStats={settings.showNativeStreamerStats}
              nativeInputCaptureActive={nativeInputCaptureActive}
              gstreamerEnabled={settings.streamClientMode === "native"}
              nativeExternalRenderer={settings.nativeExternalRenderer}
              shortcuts={{
                toggleStats: formatShortcutForDisplay(settings.shortcutToggleStats, isMac),
                togglePointerLock: formatShortcutForDisplay(settings.shortcutTogglePointerLock, isMac),
                toggleFullscreen: formatShortcutForDisplay(settings.shortcutToggleFullscreen, isMac),
                stopStream: formatShortcutForDisplay(settings.shortcutStopStream, isMac),
                toggleAntiAfk: shortcuts.toggleAntiAfk.canonical,
                toggleMicrophone: formatShortcutForDisplay(settings.shortcutToggleMicrophone, isMac),
                screenshot: shortcuts.screenshot.canonical,
                recording: shortcuts.recording.canonical,
              }}
              hideStreamButtons={settings.hideStreamButtons}
              serverRegion={session?.serverIp}
              antiAfkEnabled={antiAfkEnabled}
              antiAfkAckNonce={antiAfkAckNonce}
              showAntiAfkIndicator={settings.showAntiAfkIndicator}
              exitPrompt={exitPrompt}
              sessionStartedAtMs={sessionStartedAtMs}
              sessionCounterEnabled={settings.sessionCounterEnabled}
              showSessionTimeRemainingInStatsOverlay={settings.showSessionTimeRemainingInStatsOverlay}
              sessionTimeRemainingSeconds={sessionTimeRemainingSeconds}
              sessionClockShowEveryMinutes={settings.sessionClockShowEveryMinutes}
              sessionClockShowDurationSeconds={settings.sessionClockShowDurationSeconds}
              streamWarning={streamWarning}
              isFullscreen={sessionFullscreen || !!document.fullscreenElement}
              isConnecting={streamStatus === "connecting"}
              streamRevealComplete={streamRevealComplete}
              isStreaming={isStreaming}
              recordingBitrateMbps={settings.recordingBitrateMbps}
              gameTitle={streamingGame?.title ?? t("app.labels.game")}
              platformStore={streamingStore ?? undefined}
              onToggleFullscreen={() => {
                void toggleSessionFullscreen();
              }}
              onConfirmExit={handleExitPromptConfirm}
              onCancelExit={handleExitPromptCancel}
              onEndSession={() => {
                void handlePromptedStopStream();
              }}
              onToggleMicrophone={() => {
                clientRef.current?.toggleMicrophone();
              }}
              mouseSensitivity={settings.mouseSensitivity}
              onMouseSensitivityChange={handleMouseSensitivityChange}
              mouseAcceleration={settings.mouseAcceleration}
              onMouseAccelerationChange={handleMouseAccelerationChange}
              microphoneMode={settings.microphoneMode}
              onMicrophoneModeChange={handleMicrophoneModeChange}
              onScreenshotShortcutChange={(value) => {
                void updateSetting("shortcutScreenshot", value);
              }}
              onRecordingShortcutChange={(value) => {
                void updateSetting("shortcutToggleRecording", value);
              }}
              onShowSessionTimeRemainingInStatsOverlayChange={(value) => {
                void updateSetting("showSessionTimeRemainingInStatsOverlay", value);
              }}
              subscriptionInfo={subscriptionInfo}
              micTrack={clientRef.current?.getMicTrack() ?? null}
              onRequestPointerLock={handleRequestPointerLock}
              onReleasePointerLock={() => {
                void releasePointerLockIfNeeded();
              }}
              onNativeInputPaused={setNativeInputPaused}
              allowEscapeToExitFullscreen={settings.allowEscapeToExitFullscreen}
              videoShader={settings.videoShader}
              onVideoShaderChange={handleVideoShaderChange}
            />
          </m.div>
        )}
      </AnimatePresence>

      <AnimatePresence
        initial={false}
        onExitComplete={() => setLaunchSurfacePresent(false)}
      >
        {showDesktopLaunchLoading && (
          <m.div
            key="stream-loading-transition"
            className={`stream-loading-transition${streamRevealPhase === "revealing" && !launchError ? " stream-loading-transition--warping" : ""}`}
            initial={{ opacity: 1, scale: 1 }}
            animate={streamRevealPhase === "revealing" && !launchError
              ? { opacity: 0, scale: 1.025 }
              : { opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            transition={streamRevealPhase === "revealing" && !launchError
              ? streamRevealTransition
              : { duration: reducedMotion ? 0 : 0.16 }}
            onAnimationComplete={() => {
              if (streamRevealPhase === "revealing" && !launchError) {
                setStreamRevealPhase("revealed");
              }
            }}
          >
            <StreamLoading
              gameTitle={streamingGame?.title ?? t("app.labels.game")}
              gameCover={streamingGame?.imageUrl}
              platformStore={streamingStore ?? undefined}
              status={loadingStatus}
              queuePosition={queuePosition}
              adState={effectiveAdState}
              activeAd={activeQueueAd}
              activeAdMediaUrl={activeQueueAdMediaUrl}
              onAdPlaybackEvent={handleQueueAdPlaybackEvent}
              adPreviewRef={queueAdPreviewRef}
              error={launchError ? {
                title: launchError.title,
                description: launchError.description,
                code: launchError.codeLabel,
                actionLabel: launchError.actionLabel,
              } : undefined}
              onErrorAction={launchError?.action ? handleLaunchErrorAction : undefined}
              onCancel={() => {
                if (launchError) {
                  void handleDismissLaunchError();
                  return;
                }
                void handlePromptedStopStream();
              }}
            />
          </m.div>
        )}
      </AnimatePresence>

      <SettingsModalHost
        open={currentPage === "settings"}
        onClose={handleCloseSettings}
        onExitComplete={() => setSettingsSurfacePresent(false)}
      >
        <SettingsPage
          settings={settings}
          regions={regions}
          codecResults={codecResults}
          codecTesting={codecTesting}
          onRunCodecTest={runCodecTest}
          onSettingPreview={previewSetting}
          onSettingChange={updateSetting}
          onClose={handleCloseSettings}
          focusSection={settingsFocusSection}
          onOpenWhatsNew={handleOpenWhatsNew}
          onOpenFeedback={() => setFeedbackOpen(true)}
        />
      </SettingsModalHost>
      {logoutConfirmModal}
      {removeAccountConfirmModal}
      {queueModalGame && streamStatus === "idle" && (
        <QueueServerSelectModal
          game={queueModalGame}
          initialQueueData={queueModalData}
          onConfirm={handleQueueModalConfirm}
          onCancel={handleQueueModalCancel}
        />
      )}
      <ReleaseHighlightsModal
        payload={releaseHighlightsPayload}
        onDismiss={handleDismissReleaseHighlights}
        onExitComplete={() => setReleaseHighlightsSurfacePresent(false)}
      />
      <ErrorReportingConsentModal
        open={showErrorReportingConsent}
        onAccept={() => {
          void handleErrorReportingConsent(true);
        }}
        onDecline={() => {
          void handleErrorReportingConsent(false);
        }}
        onExitComplete={() => setConsentSurfacePresent(false)}
      />
      <FeedbackModal
        open={feedbackOpen}
        settings={settings}
        onClose={() => setFeedbackOpen(false)}
        onExitComplete={() => setFeedbackSurfacePresent(false)}
      />
    </div>
  );
}
