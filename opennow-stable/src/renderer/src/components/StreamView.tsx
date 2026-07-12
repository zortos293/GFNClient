import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, m } from "motion/react";
import type { JSX } from "react";
import { Maximize, Minimize, LogOut, Clock3, AlertTriangle, Mic, Camera, ChevronLeft, ChevronRight, Save, Trash2, X, Circle, Square, Video, FolderOpen, Gamepad2, Gauge, Images, Keyboard, MousePointer2, SlidersHorizontal } from "lucide-react";
import SideBar from "./SideBar";
import { SessionStartedSplash } from "./SessionStartedSplash";
import { StreamStatsHud } from "./StreamStatsHud";
import type { StreamDiagnosticsStore } from "../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsSelector } from "../utils/streamDiagnosticsStore";
import { getStoreDisplayName, getStoreIconComponent } from "./GameCard";
import { RemainingPlaytimeIndicator, SessionElapsedIndicator } from "./ElapsedSessionIndicators";
import type { MicrophoneMode, ScreenshotEntry, RecordingEntry, SubscriptionInfo, VideoShaderSettings } from "@shared/gfn";
import { DEFAULT_VIDEO_SHADER_SETTINGS } from "@shared/gfn";
import { VideoShaderPipeline } from "../platforms/gfn/videoShaderPipeline";
import { formatShortcutForDisplay, isShortcutMatch, normalizeShortcut, shortcutFromKeyboardEvent } from "../shortcuts";
import { addStreamShortcutActionListener } from "../streamShortcutActions";
import { useMicMeter } from "../hooks/useMicMeter";
import { formatElapsed } from "../utils/timeFormat";
import { useTranslation } from "../i18n";
import { controllerButton, readControllerGamepadButtons } from "../utils/controllerGamepad";
import { formatFileSize, formatSessionTimeRemaining, formatWarningSeconds } from "./stream/streamFormatters";
import { AntiAfkIndicator, MicrophoneIndicator, RecordingIndicator } from "./stream/StreamIndicators";
import { StreamTitleBar } from "./stream/StreamTitleBar";
import {
  hasVisibleStreamVideo,
  SidebarMicMutedBadge,
  StreamEmptyState,
  StreamWaitingForVideo,
  VideoFocusOnReady,
} from "./stream/StreamEmptyStates";
import { MotionSpinner } from "./MotionSpinner";

const ANTI_AFK_TOGGLE_ACK_MS = 5000;
const CONTROLLER_MENU_REPEAT_MS = 180;
const CONTROLLER_SIDEBAR_SHORTCUT_DISPLAY = "View + Menu";
const STREAM_MENU_TABS = ["session", "controls", "media", "shortcuts"] as const;
type StreamMenuTab = (typeof STREAM_MENU_TABS)[number];

interface StreamViewProps {
  videoRef: React.Ref<HTMLVideoElement>;
  audioRef: React.Ref<HTMLAudioElement>;
  diagnosticsStore: StreamDiagnosticsStore;
  showStats: boolean;
  showNativeStats?: boolean;
  nativeInputCaptureActive?: boolean;
  gstreamerEnabled: boolean;
  nativeExternalRenderer?: boolean;
  shortcuts: {
    toggleStats: string;
    togglePointerLock: string;
    toggleFullscreen: string;
    stopStream: string;
    toggleAntiAfk: string;
    toggleMicrophone?: string;
    screenshot: string;
    recording: string;
  };
  hideStreamButtons?: boolean;
  serverRegion?: string;
  antiAfkEnabled: boolean;
  antiAfkAckNonce: number;
  showAntiAfkIndicator: boolean;
  exitPrompt: {
    open: boolean;
    gameTitle: string;
  };
  sessionStartedAtMs: number | null;
  isStreaming: boolean;
  sessionCounterEnabled: boolean;
  showSessionTimeRemainingInStatsOverlay: boolean;
  sessionTimeRemainingSeconds: number | null;
  sessionClockShowEveryMinutes: number;
  sessionClockShowDurationSeconds: number;
  streamWarning: {
    code: 1 | 2 | 3;
    message: string;
    tone: "warn" | "critical";
    secondsLeft?: number;
  } | null;
  isFullscreen: boolean;
  isConnecting: boolean;
  gameTitle: string;
  recordingBitrateMbps: number | null;
  platformStore?: string;
  onToggleFullscreen: () => void;
  onConfirmExit: () => void;
  onCancelExit: () => void;
  onEndSession: () => void;
  onToggleMicrophone?: () => void;
  mouseSensitivity: number;
  onMouseSensitivityChange: (value: number) => void;
  mouseAcceleration: number;
  onMouseAccelerationChange: (value: number) => void;
  onRequestPointerLock?: () => void;
  onReleasePointerLock?: () => void;
  onNativeInputPaused?: (paused: boolean) => void;
  microphoneMode: MicrophoneMode;
  onMicrophoneModeChange: (value: MicrophoneMode) => void;
  onScreenshotShortcutChange: (value: string) => void;
  onRecordingShortcutChange: (value: string) => void;
  onShowSessionTimeRemainingInStatsOverlayChange: (value: boolean) => void;
  subscriptionInfo: SubscriptionInfo | null;
  micTrack?: MediaStreamTrack | null;
  className?: string;
  allowEscapeToExitFullscreen?: boolean;
  videoShader: VideoShaderSettings;
  onVideoShaderChange: (value: VideoShaderSettings) => void;
}

export function StreamView({
  videoRef,
  audioRef,
  diagnosticsStore,
  showStats,
  showNativeStats = false,
  nativeInputCaptureActive = false,
  gstreamerEnabled,
  nativeExternalRenderer = false,
  shortcuts,
  serverRegion,
  antiAfkEnabled,
  antiAfkAckNonce,
  showAntiAfkIndicator,
  exitPrompt,
  sessionStartedAtMs,
  isStreaming,
  sessionCounterEnabled,
  showSessionTimeRemainingInStatsOverlay,
  sessionTimeRemainingSeconds,
  sessionClockShowEveryMinutes,
  sessionClockShowDurationSeconds,
  streamWarning,
  isFullscreen,
  isConnecting,
  gameTitle,
  recordingBitrateMbps,
  platformStore,
  onToggleFullscreen,
  onConfirmExit,
  onCancelExit,
  onEndSession,
  onToggleMicrophone,
  mouseSensitivity,
  onMouseSensitivityChange,
  mouseAcceleration,
  onMouseAccelerationChange,
  onRequestPointerLock,
  onReleasePointerLock,
  onNativeInputPaused,
  microphoneMode,
  onMicrophoneModeChange,
  onScreenshotShortcutChange,
  onRecordingShortcutChange,
  onShowSessionTimeRemainingInStatsOverlayChange,
  subscriptionInfo,
  micTrack,
  hideStreamButtons = false,
  allowEscapeToExitFullscreen,
  className,
  videoShader,
  onVideoShaderChange,
}: StreamViewProps): JSX.Element {
  const { t } = useTranslation();
  const [showHints, setShowHints] = useState(true);
  const [showSessionClock, setShowSessionClock] = useState(false);
  const [antiAfkToggleAck, setAntiAfkToggleAck] = useState<"on" | "off" | null>(null);
  const [showSideBar, setShowSideBar] = useState(false);
  const [isPointerLocked, setIsPointerLocked] = useState(false);
  const [pointerLockHintVisible, setPointerLockHintVisible] = useState(false);
  const pointerLockHintTimerRef = useRef<number | null>(null);
  const [screenshots, setScreenshots] = useState<ScreenshotEntry[]>([]);
  const [isSavingScreenshot, setIsSavingScreenshot] = useState(false);
  const [galleryError, setGalleryError] = useState<string | null>(null);
  const [selectedScreenshotId, setSelectedScreenshotId] = useState<string | null>(null);
  const [screenshotShortcutInput, setScreenshotShortcutInput] = useState(shortcuts.screenshot);
  const [screenshotShortcutError, setScreenshotShortcutError] = useState<string | null>(null);
  const [activeSidebarTab, setActiveSidebarTab] = useState<StreamMenuTab>("session");
  const sidebarRef = useRef<HTMLElement | null>(null);
  const sidebarGamepadFrameRef = useRef<number | null>(null);
  const sidebarGamepadPreviousButtonsRef = useRef(0);
  const sidebarGamepadLastMoveAtRef = useRef(0);
  const exitPromptGamepadFrameRef = useRef<number | null>(null);
  const exitPromptGamepadPreviousButtonsRef = useRef(0);
  const suppressVideoFocusOnSidebarCloseRef = useRef(false);
  const screenshotApiAvailable =
    typeof window.openNow?.saveScreenshot === "function" &&
    typeof window.openNow?.listScreenshots === "function" &&
    typeof window.openNow?.deleteScreenshot === "function" &&
    typeof window.openNow?.saveScreenshotAs === "function";
  const nativeRendererActive = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.nativeRendererActive,
  );
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const shaderPipelineRef = useRef<VideoShaderPipeline | null>(null);
  const streamHasVideo = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => hasVisibleStreamVideo(stats),
  );
  const [videoElementHasFrame, setVideoElementHasFrame] = useState(false);

  useEffect(() => {
    if (isConnecting) {
      setVideoElementHasFrame(false);
      return undefined;
    }

    const video = localVideoRef.current;
    if (!video) {
      return undefined;
    }

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
  }, [isConnecting]);

  const streamVideoReady = streamHasVideo || videoElementHasFrame;
  const [sessionReadySplashVisible, setSessionReadySplashVisible] = useState(false);
  const sessionReadySplashShownRef = useRef(false);
  const showStatsHud = showStats && !nativeRendererActive && !isConnecting;

  useEffect(() => {
    if (isConnecting) {
      sessionReadySplashShownRef.current = false;
      setSessionReadySplashVisible(false);
      return;
    }
    if (nativeRendererActive || !streamVideoReady || sessionReadySplashShownRef.current) {
      return;
    }
    sessionReadySplashShownRef.current = true;
    setSessionReadySplashVisible(true);
  }, [isConnecting, nativeRendererActive, streamVideoReady]);

  const handleSessionReadySplashFinished = useCallback(() => {
    setSessionReadySplashVisible(false);
  }, []);

  // Recording state
  const [isRecording, setIsRecording] = useState(false);
  const [recordings, setRecordings] = useState<RecordingEntry[]>([]);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [recordingError, setRecordingError] = useState<string | null>(null);
  const [usedMimeType, setUsedMimeType] = useState<string | null>(null);
  const [recordingShortcutInput, setRecordingShortcutInput] = useState(shortcuts.recording);
  const [recordingShortcutError, setRecordingShortcutError] = useState<string | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordingIdRef = useRef<string | null>(null);
  const recordingStartTimeRef = useRef<number>(0);
  const recordingTimerRef = useRef<number | undefined>(undefined);
  const thumbnailDataUrlRef = useRef<string | null>(null);
  const recCarouselRef = useRef<HTMLDivElement | null>(null);
  const recordingApiAvailable =
    typeof window.openNow?.beginRecording === "function" &&
    typeof window.openNow?.sendRecordingChunk === "function" &&
    typeof window.openNow?.finishRecording === "function" &&
    typeof window.openNow?.abortRecording === "function" &&
    typeof window.openNow?.listRecordings === "function" &&
    typeof window.openNow?.deleteRecording === "function";

  const microphoneModes = useMemo(
    () => [
      { value: "disabled" as MicrophoneMode, label: "Disabled", description: "No microphone input" },
      { value: "push-to-talk" as MicrophoneMode, label: "Push-to-Talk", description: "Hold a key to talk" },
      { value: "voice-activity" as MicrophoneMode, label: "Voice Activity", description: "Always listen" },
    ],
    []
  );

  const handleFullscreenToggle = useCallback(() => {
    onToggleFullscreen();
  }, [onToggleFullscreen]);

  const handlePointerLockToggle = useCallback(() => {
    if (isPointerLocked) {
      if (onReleasePointerLock) {
        onReleasePointerLock();
        return;
      }
      document.exitPointerLock();
      return;
    }
    if (onRequestPointerLock) {
      onRequestPointerLock();
    }
  }, [isPointerLocked, onReleasePointerLock, onRequestPointerLock]);

  useEffect(() => {
    const timer = setTimeout(() => setShowHints(false), 5000);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!sessionCounterEnabled) {
      setShowSessionClock(false);
      return;
    }

    if (isConnecting) {
      setShowSessionClock(false);
      return;
    }

    const intervalMinutes = Math.max(0, Math.floor(sessionClockShowEveryMinutes || 0));
    const durationSeconds = Math.max(1, Math.floor(sessionClockShowDurationSeconds || 1));
    const intervalMs = intervalMinutes * 60 * 1000;
    const durationMs = durationSeconds * 1000;

    let hideTimer: number | undefined;
    let periodicTimer: number | undefined;

    const showFor = (durationMs: number): void => {
      setShowSessionClock(true);
      if (hideTimer !== undefined) {
        window.clearTimeout(hideTimer);
      }
      hideTimer = window.setTimeout(() => {
        setShowSessionClock(false);
      }, durationMs);
    };

    // Show session clock at stream start.
    showFor(durationMs);

    if (intervalMs > 0) {
      periodicTimer = window.setInterval(() => {
        showFor(durationMs);
      }, intervalMs);
    }

    return () => {
      if (hideTimer !== undefined) {
        window.clearTimeout(hideTimer);
      }
      if (periodicTimer !== undefined) {
        window.clearInterval(periodicTimer);
      }
    };
  }, [isConnecting, sessionClockShowDurationSeconds, sessionClockShowEveryMinutes, sessionCounterEnabled]);

  useEffect(() => {
    if (antiAfkAckNonce === 0 || isConnecting) {
      setAntiAfkToggleAck(null);
      return;
    }

    // Omit transient "on" message when persistent ANTI-AFK badge already shows it
    if (antiAfkEnabled && showAntiAfkIndicator) {
      setAntiAfkToggleAck(null);
      return;
    }

    setAntiAfkToggleAck(antiAfkEnabled ? "on" : "off");

    const hideTimer = window.setTimeout(() => {
      setAntiAfkToggleAck(null);
    }, ANTI_AFK_TOGGLE_ACK_MS);

    return (): void => {
      window.clearTimeout(hideTimer);
    };
  }, [antiAfkAckNonce, antiAfkEnabled, showAntiAfkIndicator, isConnecting]);

  const warningSeconds = formatWarningSeconds(streamWarning?.secondsLeft);
  const sessionTimeRemainingText = formatSessionTimeRemaining(sessionTimeRemainingSeconds);
  const showSessionTimeRemainingInStats =
    sessionTimeRemainingText !== null && showSessionTimeRemainingInStatsOverlay;
  const platformName = platformStore ? getStoreDisplayName(platformStore) : "";
  const PlatformIcon = platformStore ? getStoreIconComponent(platformStore) : null;
  const isMacClient = navigator.platform?.toLowerCase().includes("mac") || navigator.userAgent.includes("Macintosh");

  // Local ref for audio element (game audio stream)
  const localAudioRef = useRef<HTMLAudioElement | null>(null);
  // AudioContext used during an active recording (torn down on stop/error)
  const audioCtxRef = useRef<AudioContext | null>(null);

  // Mic level meter canvas
  const micMeterRef = useRef<HTMLCanvasElement | null>(null);
  const galleryStripRef = useRef<HTMLDivElement | null>(null);
  useMicMeter(micMeterRef, micTrack ?? null, showSideBar && microphoneMode !== "disabled");

  const selectedScreenshot = useMemo(() => {
    if (!selectedScreenshotId) return null;
    return screenshots.find((item) => item.id === selectedScreenshotId) ?? null;
  }, [screenshots, selectedScreenshotId]);

  useEffect(() => {
    setScreenshotShortcutInput(shortcuts.screenshot);
    setScreenshotShortcutError(null);
  }, [shortcuts.screenshot]);

  useEffect(() => {
    setRecordingShortcutInput(shortcuts.recording);
    setRecordingShortcutError(null);
  }, [shortcuts.recording]);

  const getScreenshotShortcutError = useCallback((rawValue: string): string | null => {
    const trimmed = rawValue.trim();
    if (!trimmed) {
      return "Shortcut cannot be empty.";
    }

    const normalized = normalizeShortcut(trimmed);
    if (!normalized.valid) {
      return "Invalid shortcut format.";
    }

    const reserved = [
      shortcuts.toggleStats,
      shortcuts.togglePointerLock,
      shortcuts.stopStream,
      shortcuts.toggleAntiAfk,
      shortcuts.toggleMicrophone,
      shortcuts.recording,
      ...(isMacClient ? ["Meta+G"] : ["Ctrl+G", "Ctrl+Shift+G"]),
    ]
      .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
      .map((value) => normalizeShortcut(value))
      .filter((parsed) => parsed.valid)
      .map((parsed) => parsed.canonical);

    if (reserved.includes(normalized.canonical)) {
      return "Shortcut conflicts with an existing binding.";
    }

    return null;
  }, [isMacClient, shortcuts.recording, shortcuts.stopStream, shortcuts.toggleAntiAfk, shortcuts.toggleMicrophone, shortcuts.togglePointerLock, shortcuts.toggleStats]);

  const getRecordingShortcutError = useCallback((rawValue: string): string | null => {
    const trimmed = rawValue.trim();
    if (!trimmed) {
      return "Shortcut cannot be empty.";
    }

    const normalized = normalizeShortcut(trimmed);
    if (!normalized.valid) {
      return "Invalid shortcut format.";
    }

    const reserved = [
      shortcuts.toggleStats,
      shortcuts.togglePointerLock,
      shortcuts.stopStream,
      shortcuts.toggleAntiAfk,
      shortcuts.toggleMicrophone,
      shortcuts.screenshot,
      ...(isMacClient ? ["Meta+G"] : ["Ctrl+G", "Ctrl+Shift+G"]),
    ]
      .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
      .map((value) => normalizeShortcut(value))
      .filter((parsed) => parsed.valid)
      .map((parsed) => parsed.canonical);

    if (reserved.includes(normalized.canonical)) {
      return "Shortcut conflicts with an existing binding.";
    }

    return null;
  }, [isMacClient, shortcuts.screenshot, shortcuts.stopStream, shortcuts.toggleAntiAfk, shortcuts.toggleMicrophone, shortcuts.togglePointerLock, shortcuts.toggleStats]);

  const SIDEBAR_TOGGLE_RAW = isMacClient ? "Meta+G" : "Ctrl+G";
  const sidebarToggleShortcutDisplay = formatShortcutForDisplay(SIDEBAR_TOGGLE_RAW, isMacClient);

  const applyScreenshotShortcutFromCapture = useCallback(
    (canonical: string) => {
      const error = getScreenshotShortcutError(canonical);
      if (error) {
        setScreenshotShortcutError(error);
        return;
      }
      const normalized = normalizeShortcut(canonical.trim());
      if (!normalized.valid) {
        setScreenshotShortcutError("Invalid shortcut format.");
        return;
      }
      setScreenshotShortcutError(null);
      setScreenshotShortcutInput(normalized.canonical);
      if (normalized.canonical !== shortcuts.screenshot) {
        onScreenshotShortcutChange(normalized.canonical);
      }
    },
    [getScreenshotShortcutError, onScreenshotShortcutChange, shortcuts.screenshot],
  );

  const applyRecordingShortcutFromCapture = useCallback(
    (canonical: string) => {
      const error = getRecordingShortcutError(canonical);
      if (error) {
        setRecordingShortcutError(error);
        return;
      }
      const normalized = normalizeShortcut(canonical.trim());
      if (!normalized.valid) {
        setRecordingShortcutError("Invalid shortcut format.");
        return;
      }
      setRecordingShortcutError(null);
      setRecordingShortcutInput(normalized.canonical);
      if (normalized.canonical !== shortcuts.recording) {
        onRecordingShortcutChange(normalized.canonical);
      }
    },
    [getRecordingShortcutError, onRecordingShortcutChange, shortcuts.recording],
  );

  const handleStreamScreenshotShortcutKeyDown = (e: React.KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === "Escape") {
      e.preventDefault();
      e.currentTarget.blur();
      return;
    }
    if (e.key === "Enter" || e.key === "Tab") {
      return;
    }
    const captured = shortcutFromKeyboardEvent(e.nativeEvent);
    if (!captured) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    applyScreenshotShortcutFromCapture(captured);
  };

  const handleStreamRecordingShortcutKeyDown = (e: React.KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === "Escape") {
      e.preventDefault();
      e.currentTarget.blur();
      return;
    }
    if (e.key === "Enter" || e.key === "Tab") {
      return;
    }
    const captured = shortcutFromKeyboardEvent(e.nativeEvent);
    if (!captured) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    applyRecordingShortcutFromCapture(captured);
  };

  const handleStreamScreenshotShortcutPaste = (e: React.ClipboardEvent<HTMLInputElement>): void => {
    const text = e.clipboardData.getData("text/plain").trim();
    if (!text) {
      return;
    }
    e.preventDefault();
    applyScreenshotShortcutFromCapture(text);
  };

  const handleStreamRecordingShortcutPaste = (e: React.ClipboardEvent<HTMLInputElement>): void => {
    const text = e.clipboardData.getData("text/plain").trim();
    if (!text) {
      return;
    }
    e.preventDefault();
    applyRecordingShortcutFromCapture(text);
  };

  const refreshScreenshots = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    try {
      const items = await window.openNow.listScreenshots();
      setScreenshots(items);
    } catch (error) {
      console.error("[StreamView] Failed to load screenshots:", error);
      setGalleryError("Unable to load screenshot gallery.");
    }
  }, [screenshotApiAvailable]);

  const captureScreenshot = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable capture.");
      return;
    }
    if (isSavingScreenshot) {
      return;
    }

    const video = localVideoRef.current;
    if (!video || video.videoWidth <= 0 || video.videoHeight <= 0) {
      setGalleryError("Stream is not ready for screenshots yet.");
      return;
    }

    setIsSavingScreenshot(true);
    try {
      const canvas = document.createElement("canvas");
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const context = canvas.getContext("2d");
      if (!context) {
        throw new Error("Could not acquire 2D context");
      }

      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL("image/png");
      const saved = await window.openNow.saveScreenshot({ dataUrl, gameTitle });
      setScreenshots((prev) => [saved, ...prev.filter((item) => item.id !== saved.id)].slice(0, 60));
    } catch (error) {
      console.error("[StreamView] Failed to capture screenshot:", error);
      setGalleryError("Screenshot failed. Try again.");
    } finally {
      setIsSavingScreenshot(false);
    }
  }, [gameTitle, isSavingScreenshot, screenshotApiAvailable]);

  const scrollGallery = useCallback((direction: "left" | "right") => {
    const strip = galleryStripRef.current;
    if (!strip) return;
    const delta = Math.max(180, Math.round(strip.clientWidth * 0.7));
    strip.scrollBy({ left: direction === "left" ? -delta : delta, behavior: "smooth" });
  }, []);

  const handleDeleteScreenshot = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    if (!selectedScreenshot) return;

    try {
      await window.openNow.deleteScreenshot({ id: selectedScreenshot.id });
      setScreenshots((prev) => prev.filter((item) => item.id !== selectedScreenshot.id));
      setSelectedScreenshotId(null);
    } catch (error) {
      console.error("[StreamView] Failed to delete screenshot:", error);
      setGalleryError("Unable to delete screenshot.");
    }
  }, [screenshotApiAvailable, selectedScreenshot]);

  const handleSaveScreenshotAs = useCallback(async () => {
    setGalleryError(null);
    if (!screenshotApiAvailable) {
      setGalleryError("Screenshot API unavailable. Restart OpenNOW to enable gallery.");
      return;
    }
    if (!selectedScreenshot) return;

    try {
      await window.openNow.saveScreenshotAs({ id: selectedScreenshot.id });
    } catch (error) {
      console.error("[StreamView] Failed to save screenshot as:", error);
      setGalleryError("Unable to save screenshot.");
    }
  }, [screenshotApiAvailable, selectedScreenshot]);

  const refreshRecordings = useCallback(async () => {
    setRecordingError(null);
    if (!recordingApiAvailable) return;
    try {
      const items = await window.openNow.listRecordings();
      setRecordings(items);
    } catch (error) {
      console.error("[StreamView] Failed to load recordings:", error);
      setRecordingError("Unable to load recordings.");
    }
  }, [recordingApiAvailable]);

  const handleDeleteRecording = useCallback(async (id: string) => {
    setRecordingError(null);
    if (!recordingApiAvailable) return;
    try {
      await window.openNow.deleteRecording({ id });
      setRecordings((prev) => prev.filter((r) => r.id !== id));
    } catch (error) {
      console.error("[StreamView] Failed to delete recording:", error);
      setRecordingError("Unable to delete recording.");
    }
  }, [recordingApiAvailable]);

  const scrollRecCarousel = useCallback((direction: "left" | "right") => {
    const strip = recCarouselRef.current;
    if (!strip) return;
    strip.scrollBy({ left: direction === "left" ? -200 : 200, behavior: "smooth" });
  }, []);

  const toggleRecording = useCallback(async () => {
    setRecordingError(null);

    if (isRecording) {
      mediaRecorderRef.current?.stop();
      return;
    }

    if (!recordingApiAvailable) {
      setRecordingError("Recording API unavailable. Restart OpenNOW to enable recording.");
      return;
    }

    const video = localVideoRef.current;
    if (!video || !video.srcObject) {
      setRecordingError("Stream is not ready for recording yet.");
      return;
    }

    const stream = video.srcObject as MediaStream;
    const mimeTypes = [
      "video/mp4;codecs=avc1.42E01E,mp4a.40.2",
      "video/mp4;codecs=avc1",
      "video/mp4",
      "video/webm;codecs=h264",
      "video/webm;codecs=vp8",
      "video/webm",
    ];
    const mimeType = mimeTypes.find((m) => MediaRecorder.isTypeSupported(m)) ?? "video/webm";
    setUsedMimeType(mimeType);

    // Build a composed MediaStream: video tracks + mixed audio (game + mic)
    const audioCtx = new AudioContext();
    audioCtxRef.current = audioCtx;
    const audioDest = audioCtx.createMediaStreamDestination();

    // Wire game audio (from the <audio> element's srcObject)
    const audioEl = localAudioRef.current;
    const gameAudioStream = audioEl?.srcObject instanceof MediaStream ? audioEl.srcObject : null;
    if (gameAudioStream && gameAudioStream.getAudioTracks().length > 0) {
      audioCtx.createMediaStreamSource(gameAudioStream).connect(audioDest);
    }

    // Wire microphone (if active)
    if (micTrack && micTrack.readyState === "live") {
      const micStream = new MediaStream([micTrack]);
      audioCtx.createMediaStreamSource(micStream).connect(audioDest);
    }

    // Compose: video tracks from the video element + mixed audio destination track
    const composed = new MediaStream([
      ...stream.getVideoTracks(),
      ...audioDest.stream.getAudioTracks(),
    ]);

    let recordingId: string;
    try {
      const result = await window.openNow.beginRecording({ mimeType });
      recordingId = result.recordingId;
    } catch (error) {
      console.error("[StreamView] Failed to begin recording:", error);
      audioCtx.close().catch(() => undefined);
      audioCtxRef.current = null;
      setRecordingError("Could not start recording.");
      return;
    }

    recordingIdRef.current = recordingId;
    thumbnailDataUrlRef.current = null;
    recordingStartTimeRef.current = Date.now();
    setRecordingDurationMs(0);
    setIsRecording(true);

    recordingTimerRef.current = window.setInterval(() => {
      setRecordingDurationMs(Date.now() - recordingStartTimeRef.current);
    }, 500);

    let isFirstChunk = true;
    const recorderOptions: MediaRecorderOptions = { mimeType };
    if (recordingBitrateMbps !== null) {
      recorderOptions.videoBitsPerSecond = Math.max(1, Math.min(200, Math.round(recordingBitrateMbps))) * 1_000_000;
    }
    const recorder = new MediaRecorder(composed, recorderOptions);

    recorder.ondataavailable = (e: BlobEvent) => {
      if (!e.data || e.data.size === 0) return;

      // Capture thumbnail from the first chunk (frame ~2 s in)
      if (isFirstChunk) {
        isFirstChunk = false;
        const vid = localVideoRef.current;
        if (vid && vid.videoWidth > 0 && vid.videoHeight > 0) {
          // create a canvas sized to preserve the video's aspect ratio, but
          // capped at roughly 320×180 so we don't generate unnecessarily large
          // thumbnails when the stream resolution is higher than 16:9.
          const maxW = 320;
          const maxH = 180;
          let w = vid.videoWidth;
          let h = vid.videoHeight;

          // shrink to fit within bounds while keeping aspect ratio
          if (w > maxW) {
            h = Math.round((maxW / w) * h);
            w = maxW;
          }
          if (h > maxH) {
            w = Math.round((maxH / h) * w);
            h = maxH;
          }

          const canvas = document.createElement("canvas");
          canvas.width = w;
          canvas.height = h;
          const ctx2d = canvas.getContext("2d");
          if (ctx2d) {
            ctx2d.drawImage(vid, 0, 0, w, h);
            thumbnailDataUrlRef.current = canvas.toDataURL("image/jpeg", 0.72);
          }
        }
      }

      void e.data.arrayBuffer().then((buf) => {
        const id = recordingIdRef.current;
        if (!id) return;
        window.openNow.sendRecordingChunk({ recordingId: id, chunk: buf }).catch((err: unknown) => {
          console.error("[StreamView] Failed to send recording chunk:", err);
        });
      });
    };

    recorder.onstop = () => {
      window.clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = undefined;
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
      const id = recordingIdRef.current;
      recordingIdRef.current = null;
      setIsRecording(false);

      if (!id) return;

      const durationMs = Date.now() - recordingStartTimeRef.current;
      void window.openNow
        .finishRecording({
          recordingId: id,
          durationMs,
          gameTitle,
          thumbnailDataUrl: thumbnailDataUrlRef.current ?? undefined,
        })
        .then((entry) => {
          setRecordings((prev) => [entry, ...prev].slice(0, 20));
          thumbnailDataUrlRef.current = null;
        })
        .catch((err: unknown) => {
          console.error("[StreamView] Failed to finish recording:", err);
          setRecordingError("Recording could not be saved.");
        });
    };

    recorder.onerror = () => {
      window.clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = undefined;
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
      const id = recordingIdRef.current;
      recordingIdRef.current = null;
      setIsRecording(false);
      thumbnailDataUrlRef.current = null;
      if (id) {
        window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
      }
      setRecordingError("Recording encountered an error.");
    };

    mediaRecorderRef.current = recorder;
    recorder.start(2000);
  }, [gameTitle, isRecording, micTrack, recordingApiAvailable, recordingBitrateMbps]);

  // Cleanup: abort any active recording on unmount
  useEffect(() => {
    return () => {
      window.clearInterval(recordingTimerRef.current);
      const recorder = mediaRecorderRef.current;
      const id = recordingIdRef.current;
      if (recorder && recorder.state !== "inactive") {
        recorder.stop();
      }
      if (id) {
        window.openNow.abortRecording({ recordingId: id }).catch(() => undefined);
        recordingIdRef.current = null;
      }
      audioCtxRef.current?.close().catch(() => undefined);
      audioCtxRef.current = null;
    };
  }, []);

  // Video shader post-processing pipeline (embedded WebRTC path only; the
  // native streamer renders outside Chromium so shaders cannot apply there).
  useEffect(() => {
    const video = localVideoRef.current;
    if (!video) return;
    const effective = gstreamerEnabled || nativeRendererActive
      ? { ...videoShader, enabled: false }
      : videoShader;
    if (!shaderPipelineRef.current) {
      if (!effective.enabled) return;
      shaderPipelineRef.current = new VideoShaderPipeline(video, effective);
    } else {
      shaderPipelineRef.current.updateSettings(effective);
    }
  }, [videoShader, gstreamerEnabled, nativeRendererActive]);

  useEffect(() => () => {
    shaderPipelineRef.current?.dispose();
    shaderPipelineRef.current = null;
  }, []);

  const setVideoRef = useCallback((element: HTMLVideoElement | null) => {
    localVideoRef.current = element;
    if (typeof videoRef === "function") {
      videoRef(element);
    } else if (videoRef && "current" in videoRef) {
      (videoRef as React.MutableRefObject<HTMLVideoElement | null>).current = element;
    }
  }, [videoRef]);

  const setAudioRef = useCallback((element: HTMLAudioElement | null) => {
    localAudioRef.current = element;
    if (typeof audioRef === "function") {
      audioRef(element);
    } else if (audioRef && "current" in audioRef) {
      (audioRef as React.MutableRefObject<HTMLAudioElement | null>).current = element;
    }
  }, [audioRef]);

  useEffect(() => {
    const updateSurface = window.openNow?.updateNativeRenderSurface;
    if (typeof updateSurface !== "function") {
      return undefined;
    }

    let frame = 0;
    const publish = (): void => {
      const element = localVideoRef.current;
      const dpr = window.devicePixelRatio || 1;
      if (!element || document.visibilityState === "hidden") {
        updateSurface({ rect: null, visible: false, deviceScaleFactor: dpr });
        return;
      }

      const rect = element.getBoundingClientRect();
      const width = Math.round(rect.width * dpr);
      const height = Math.round(rect.height * dpr);
      const visible = width >= 2 && height >= 2 && !showSideBar && !exitPrompt.open;
      updateSurface({
        deviceScaleFactor: dpr,
        visible,
        showStats: showStats || showNativeStats,
        rect: visible
          ? {
              x: Math.round(rect.left * dpr),
              y: Math.round(rect.top * dpr),
              width,
              height,
            }
          : null,
      });
    };

    const schedule = (): void => {
      if (frame !== 0) {
        return;
      }
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        publish();
      });
    };

    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(schedule);
    if (observer && localVideoRef.current) {
      observer.observe(localVideoRef.current);
    }

    window.addEventListener("resize", schedule);
    window.addEventListener("fullscreenchange", schedule);
    document.addEventListener("visibilitychange", schedule);
    window.visualViewport?.addEventListener("resize", schedule);
    window.visualViewport?.addEventListener("scroll", schedule);
    schedule();

    return () => {
      if (frame !== 0) {
        window.cancelAnimationFrame(frame);
      }
      observer?.disconnect();
      window.removeEventListener("resize", schedule);
      window.removeEventListener("fullscreenchange", schedule);
      document.removeEventListener("visibilitychange", schedule);
      window.visualViewport?.removeEventListener("resize", schedule);
      window.visualViewport?.removeEventListener("scroll", schedule);
      updateSurface({
        rect: null,
        visible: false,
        deviceScaleFactor: window.devicePixelRatio || 1,
        showStats: false,
      });
    };
  }, [exitPrompt.open, showNativeStats, showSideBar, showStats]);

  useEffect(() => {
    const handlePointerLockChange = () => {
      setIsPointerLocked(
        document.pointerLockElement === localVideoRef.current || nativeInputCaptureActive,
      );
    };
    handlePointerLockChange();
    document.addEventListener("pointerlockchange", handlePointerLockChange);
    return () => document.removeEventListener("pointerlockchange", handlePointerLockChange);
  }, [nativeInputCaptureActive]);

  useEffect(() => {
    // Show a transient HUD hint when pointer lock is acquired
    if (isPointerLocked) {
      setPointerLockHintVisible(true);
      if (pointerLockHintTimerRef.current) {
        window.clearTimeout(pointerLockHintTimerRef.current);
      }
      pointerLockHintTimerRef.current = window.setTimeout(() => {
        pointerLockHintTimerRef.current = null;
        setPointerLockHintVisible(false);
      }, 3000);
    } else {
      if (pointerLockHintTimerRef.current) {
        window.clearTimeout(pointerLockHintTimerRef.current);
        pointerLockHintTimerRef.current = null;
      }
      setPointerLockHintVisible(false);
    }
    return () => {
      if (pointerLockHintTimerRef.current) {
        window.clearTimeout(pointerLockHintTimerRef.current);
        pointerLockHintTimerRef.current = null;
      }
    };
  }, [isPointerLocked]);

  useEffect(() => {
    onNativeInputPaused?.(showSideBar);
    return () => {
      if (showSideBar) {
        onNativeInputPaused?.(false);
      }
    };
  }, [onNativeInputPaused, showSideBar]);

  useEffect(() => {
    if (showSideBar) {
      // Mark sidebar open so input auto-lock code can avoid re-requesting.
      try {
        document.body.dataset.sidebarOpen = "1";
      } catch {}

      if (onReleasePointerLock) {
        void onReleasePointerLock();
      } else {
        document.exitPointerLock();
      }
      void refreshScreenshots();
      void refreshRecordings();
      return () => {
        try {
          delete (document.body.dataset as DOMStringMap).sidebarOpen;
        } catch {}
      };
    }
    if (suppressVideoFocusOnSidebarCloseRef.current) {
      suppressVideoFocusOnSidebarCloseRef.current = false;
      return undefined;
    }
    // Sidebar just closed — restore focus to the video so clicks register
    // immediately. Without this, focus stays on the last sidebar element and
    // mousedown's preventDefault() blocks the browser from re-focusing on click.
    const timer = window.setTimeout(() => {
      if (localVideoRef.current && document.activeElement !== localVideoRef.current) {
        localVideoRef.current.focus({ preventScroll: true });
      }
    }, 50);
    try {
      delete (document.body.dataset as DOMStringMap).sidebarOpen;
    } catch {}
    return () => clearTimeout(timer);
  }, [refreshRecordings, refreshScreenshots, showSideBar]);

  useEffect(() => {
    if (!selectedScreenshotId) return;
    if (!screenshots.some((item) => item.id === selectedScreenshotId)) {
      setSelectedScreenshotId(null);
    }
  }, [screenshots, selectedScreenshotId]);

  const handleToggleSideBar = useCallback(() => {
    setShowSideBar((s) => {
      if (!s && document.pointerLockElement) {
        if (onReleasePointerLock) {
          onReleasePointerLock();
        } else {
          document.exitPointerLock();
        }
      }
      return !s;
    });
  }, [onReleasePointerLock]);

  const handleSidebarExitSession = useCallback(() => {
    suppressVideoFocusOnSidebarCloseRef.current = true;
    setShowSideBar(false);
    onEndSession();
  }, [onEndSession]);

  const selectAdjacentSidebarTab = useCallback((direction: -1 | 1) => {
    setActiveSidebarTab((current) => {
      const index = STREAM_MENU_TABS.indexOf(current);
      return STREAM_MENU_TABS[(index + direction + STREAM_MENU_TABS.length) % STREAM_MENU_TABS.length];
    });
    window.requestAnimationFrame(() => {
      sidebarRef.current?.querySelector<HTMLElement>(".sidebar-tab--active")?.focus({ preventScroll: true });
    });
  }, []);

  useEffect(() => {
    if (!showSideBar || exitPrompt.open) return;

    const getMenuItems = (): HTMLElement[] => {
      const scope = selectedScreenshotId
        ? document.querySelector<HTMLElement>(".sv-shot-modal-card")
        : sidebarRef.current;
      if (!scope) return [];
      return Array.from(scope.querySelectorAll<HTMLElement>(
        "button:not(:disabled), input:not(:disabled):not([type='checkbox']), label.sidebar-mini-toggle, [tabindex='0']",
      )).filter((element) => {
        const style = window.getComputedStyle(element);
        const isInactiveTab = element.getAttribute("role") === "tab" && element.getAttribute("aria-selected") !== "true";
        return !isInactiveTab && style.display !== "none" && style.visibility !== "hidden";
      });
    };

    const focusItem = (direction: -1 | 1): void => {
      const items = getMenuItems();
      if (items.length === 0) return;
      const currentIndex = items.findIndex((item) => item === document.activeElement);
      const nextIndex = currentIndex < 0
        ? 0
        : (currentIndex + direction + items.length) % items.length;
      items[nextIndex]?.focus({ preventScroll: true });
      items[nextIndex]?.scrollIntoView({ block: "nearest" });
    };

    const changeRange = (input: HTMLInputElement, direction: -1 | 1): void => {
      const min = Number(input.min || 0);
      const max = Number(input.max || 100);
      const step = Number(input.step || 1);
      const value = Math.max(min, Math.min(max, Number(input.value) + step * direction));
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set?.call(input, String(value));
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
    };

    const readButtons = (): number => {
      const pad = navigator.getGamepads?.().find((gamepad): gamepad is Gamepad => Boolean(gamepad));
      return readControllerGamepadButtons(pad);
    };

    const handleGamepadFrame = (): void => {
      const buttons = readButtons();
      let pressed = buttons & ~sidebarGamepadPreviousButtonsRef.current;
      const moveMask = controllerButton.up | controllerButton.down | controllerButton.left | controllerButton.right;
      const activeMoves = buttons & moveMask;
      const now = performance.now();
      if (pressed & moveMask) {
        sidebarGamepadLastMoveAtRef.current = now;
      } else if (activeMoves && now - sidebarGamepadLastMoveAtRef.current > CONTROLLER_MENU_REPEAT_MS) {
        pressed |= activeMoves;
        sidebarGamepadLastMoveAtRef.current = now;
      }

      const active = document.activeElement as HTMLElement | null;
      const range = active instanceof HTMLInputElement && active.type === "range" ? active : null;
      if (pressed & controllerButton.up) focusItem(-1);
      if (pressed & controllerButton.down) focusItem(1);
      if (pressed & controllerButton.left) {
        if (range) changeRange(range, -1);
        else if (active?.getAttribute("role") === "tab") selectAdjacentSidebarTab(-1);
        else focusItem(-1);
      }
      if (pressed & controllerButton.right) {
        if (range) changeRange(range, 1);
        else if (active?.getAttribute("role") === "tab") selectAdjacentSidebarTab(1);
        else focusItem(1);
      }
      if (pressed & controllerButton.leftShoulder) selectAdjacentSidebarTab(-1);
      if (pressed & controllerButton.rightShoulder) selectAdjacentSidebarTab(1);
      if (pressed & controllerButton.south) {
        if (active && !range) active.click();
      }
      if (pressed & controllerButton.east) {
        if (selectedScreenshotId) setSelectedScreenshotId(null);
        else setShowSideBar(false);
      }
      if (pressed & controllerButton.menu) setShowSideBar(false);

      sidebarGamepadPreviousButtonsRef.current = buttons;
      sidebarGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };

    const initialFocusTimer = window.setTimeout(() => {
      const initialFocus = selectedScreenshotId
        ? document.querySelector<HTMLElement>(".sv-shot-modal-btn:not(:disabled), .sv-shot-modal-close")
        : sidebarRef.current?.querySelector<HTMLElement>(".sidebar-tab--active");
      initialFocus?.focus({ preventScroll: true });
    }, 0);
    sidebarGamepadPreviousButtonsRef.current = readButtons();
    sidebarGamepadLastMoveAtRef.current = performance.now();
    sidebarGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);

    return () => {
      window.clearTimeout(initialFocusTimer);
      if (sidebarGamepadFrameRef.current !== null) {
        window.cancelAnimationFrame(sidebarGamepadFrameRef.current);
        sidebarGamepadFrameRef.current = null;
      }
      sidebarGamepadPreviousButtonsRef.current = 0;
      sidebarGamepadLastMoveAtRef.current = 0;
    };
  }, [exitPrompt.open, selectAdjacentSidebarTab, selectedScreenshotId, showSideBar]);

  useEffect(() => {
    if (!exitPrompt.open) return;

    const focusExitButton = (confirm: boolean): void => {
      document.querySelector<HTMLButtonElement>(
        confirm ? ".sv-exit-btn-confirm" : ".sv-exit-btn-cancel",
      )?.focus({ preventScroll: true });
    };
    const readButtons = (): number => {
      const pad = navigator.getGamepads?.().find((gamepad): gamepad is Gamepad => Boolean(gamepad));
      return readControllerGamepadButtons(pad);
    };
    const handleGamepadFrame = (): void => {
      const buttons = readButtons();
      const pressed = buttons & ~exitPromptGamepadPreviousButtonsRef.current;
      if (pressed & (controllerButton.left | controllerButton.up)) focusExitButton(false);
      if (pressed & (controllerButton.right | controllerButton.down)) focusExitButton(true);
      if (pressed & controllerButton.south) {
        const active = document.activeElement as HTMLElement | null;
        if (active?.closest(".sv-exit-card")) active.click();
      }
      if (pressed & (controllerButton.east | controllerButton.menu)) onCancelExit();
      exitPromptGamepadPreviousButtonsRef.current = buttons;
      exitPromptGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };
    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancelExit();
      } else if (event.key === "Enter") {
        event.preventDefault();
        onConfirmExit();
      }
    };

    const focusTimer = window.setTimeout(() => focusExitButton(false), 0);
    exitPromptGamepadPreviousButtonsRef.current = readButtons();
    exitPromptGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    window.addEventListener("keydown", handleKeyDown, true);
    return () => {
      window.clearTimeout(focusTimer);
      window.removeEventListener("keydown", handleKeyDown, true);
      if (exitPromptGamepadFrameRef.current !== null) {
        window.cancelAnimationFrame(exitPromptGamepadFrameRef.current);
        exitPromptGamepadFrameRef.current = null;
      }
      exitPromptGamepadPreviousButtonsRef.current = 0;
    };
  }, [exitPrompt.open, onCancelExit, onConfirmExit]);

  useEffect(() => {
    return addStreamShortcutActionListener((action) => {
      if (action === "toggleSidebar") {
        handleToggleSideBar();
        return;
      }
      if (action === "screenshot") {
        void captureScreenshot();
        return;
      }
      if (action === "toggleRecording") {
        void toggleRecording();
      }
    });
  }, [captureScreenshot, handleToggleSideBar, toggleRecording]);

  useEffect(() => {
    const screenshotShortcut = normalizeShortcut(shortcuts.screenshot);
    const recordingShortcut = normalizeShortcut(shortcuts.recording);

    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const isTyping = !!target && (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable
      );
      if (isTyping) {
        return;
      }

      const key = event.key.toLowerCase();
      const isSidebarShortcut = isMacClient
        ? event.metaKey && !event.ctrlKey && !event.altKey && !event.shiftKey && key === "g"
        : event.ctrlKey && !event.altKey && !event.metaKey && key === "g";
      if (isSidebarShortcut) {
        return;
      }

      if (isShortcutMatch(event, screenshotShortcut)) {
        event.preventDefault();
        event.stopPropagation();
        void captureScreenshot();
        return;
      }

      if (isShortcutMatch(event, recordingShortcut)) {
        event.preventDefault();
        event.stopPropagation();
        void toggleRecording();
        return;
      }
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [captureScreenshot, isMacClient, shortcuts.screenshot, shortcuts.recording, toggleRecording]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const isTyping = !!target && (
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable
      );
      if (isTyping) {
        return;
      }

      const key = event.key.toLowerCase();
      if (isMacClient) {
        if (event.metaKey && !event.ctrlKey && !event.shiftKey && key === "g") {
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();
          handleToggleSideBar();
        }
      } else if (event.ctrlKey && !event.altKey && !event.metaKey && key === "g") {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        handleToggleSideBar();
      }
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [handleToggleSideBar, isMacClient]);

  useEffect(() => {
    const blurStreamFocusTarget = (): void => {
      const active = document.activeElement;
      if (active instanceof HTMLElement && active.closest(".sv")) {
        active.blur();
      }
    };

    const hideFocusRingOnAccessKey = (event: KeyboardEvent): void => {
      if (event.key === "Alt" && !event.repeat) {
        blurStreamFocusTarget();
      }
    };

    const restoreStreamVideoFocus = (event: PointerEvent): void => {
      if (showSideBar || isConnecting || exitPrompt.open) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (target?.closest(".sv-sidebar, .sv-exit, .sv-shot-modal, button, a, input, textarea, select")) {
        return;
      }
      const video = localVideoRef.current;
      if (video && document.activeElement !== video) {
        video.focus({ preventScroll: true });
      }
    };

    window.addEventListener("blur", blurStreamFocusTarget);
    window.addEventListener("keydown", hideFocusRingOnAccessKey, true);
    window.addEventListener("pointerdown", restoreStreamVideoFocus, true);
    return () => {
      window.removeEventListener("blur", blurStreamFocusTarget);
      window.removeEventListener("keydown", hideFocusRingOnAccessKey, true);
      window.removeEventListener("pointerdown", restoreStreamVideoFocus, true);
    };
  }, [exitPrompt.open, isConnecting, showSideBar]);

  const nativeInternalHole =
    (nativeRendererActive || gstreamerEnabled) && !nativeExternalRenderer;

  return (
    <div className={["sv", streamVideoReady ? "sv--video-ready" : "sv--video-pending", nativeInternalHole ? "sv--native-hole" : "", className].filter(Boolean).join(" ")}>
      <m.video
        ref={setVideoRef}
        autoPlay
        playsInline
        muted
        tabIndex={-1}
        className={["sv-video", nativeInternalHole ? "sv-video--native-hole" : ""].filter(Boolean).join(" ")}
        initial={false}
        animate={streamVideoReady
          ? { opacity: 1, scale: 1 }
          : { opacity: 0, scale: 1.008 }}
        transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
        onClick={() => {
          if (localVideoRef.current && document.activeElement !== localVideoRef.current) {
            localVideoRef.current.focus({ preventScroll: true });
          }
        }}
      />
      <audio ref={setAudioRef} autoPlay playsInline />
      <VideoFocusOnReady
        diagnosticsStore={diagnosticsStore}
        isConnecting={isConnecting}
        videoRef={localVideoRef}
      />

      {pointerLockHintVisible && (
        <div className="sv-pointerlock-hint" role="status" aria-live="polite">
          <div>Press {shortcuts.toggleFullscreen} to exit fullscreen & release mouse</div>
          <div className="sv-pointerlock-hint-sub">
            {allowEscapeToExitFullscreen
              ? "Press Escape will also exit fullscreen per your settings."
              : "Escape goes to the game while pointer-locked; hold Escape ~1.5s to exit fullscreen."}
          </div>
        </div>
      )}

      <AnimatePresence>
        {showSideBar && (
          <m.div
            key="quick-menu-backdrop"
            className="sv-sidebar-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            onMouseDown={(event) => event.stopPropagation()}
            onClick={() => setShowSideBar(false)}
          />
        )}
      </AnimatePresence>
      <AnimatePresence>
        {showSideBar && (
          <SideBar
            key="quick-menu-sidebar"
            title="Quick menu"
            className="sv-sidebar"
            elementRef={sidebarRef}
            onClose={() => setShowSideBar(false)}
            footer={(
              <>
                <div className="sidebar-controller-hints" aria-hidden="true">
                  <span><kbd>A</kbd> Select</span>
                  <span><kbd>B</kbd> Back</span>
                  <span><kbd>LB</kbd><kbd>RB</kbd> Pages</span>
                </div>
                <button
                  type="button"
                  className="sidebar-exit-session-button"
                  onClick={handleSidebarExitSession}
                >
                  <LogOut size={16} />
                  <span>End session</span>
                </button>
              </>
            )}
          >
            <div className="sidebar-tabs" role="tablist" aria-label="Quick menu pages">
              <button
                type="button"
                role="tab"
                aria-selected={activeSidebarTab === "session"}
                className={`sidebar-tab${activeSidebarTab === "session" ? " sidebar-tab--active" : ""}`}
                onClick={() => setActiveSidebarTab("session")}
              >
                <Gauge size={16} />
                <span>Session</span>
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeSidebarTab === "controls"}
                className={`sidebar-tab${activeSidebarTab === "controls" ? " sidebar-tab--active" : ""}`}
                onClick={() => setActiveSidebarTab("controls")}
              >
                <SlidersHorizontal size={16} />
                <span>Controls</span>
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeSidebarTab === "media"}
                className={`sidebar-tab${activeSidebarTab === "media" ? " sidebar-tab--active" : ""}`}
                onClick={() => setActiveSidebarTab("media")}
              >
                <Images size={16} />
                <span>Media</span>
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeSidebarTab === "shortcuts"}
                className={`sidebar-tab${activeSidebarTab === "shortcuts" ? " sidebar-tab--active" : ""}`}
                onClick={() => setActiveSidebarTab("shortcuts")}
              >
                <Keyboard size={16} />
                <span>Keys</span>
              </button>
            </div>

            {activeSidebarTab === "session" && (
              <div className="sidebar-page sidebar-page--session" role="tabpanel">
                <section className="sidebar-session-card" aria-label="Current stream session">
                  <div className="sidebar-session-card-head">
                    <span className="sidebar-session-kicker">Now streaming</span>
                    <strong className="sidebar-session-title">{gameTitle}</strong>
                    {PlatformIcon && platformName && (
                      <span className="sidebar-session-platform" title={platformName}>
                        <span className="sidebar-session-platform-icon"><PlatformIcon /></span>
                        <span>{platformName}</span>
                      </span>
                    )}
                  </div>
                </section>
                <section className="sidebar-session-metrics" aria-label="Session time">
                  <div className="sidebar-metric">
                    <span>Total playtime left</span>
                    <RemainingPlaytimeIndicator subscriptionInfo={subscriptionInfo} startedAtMs={sessionStartedAtMs} active={isStreaming} className="sidebar-metric-value" />
                  </div>
                  {sessionTimeRemainingText !== null && (
                    <div className="sidebar-metric">
                      <span>{t("sidebar.sessionTimeRemaining")}</span>
                      <strong className="sidebar-metric-value">
                        <Clock3 size={14} />
                        {sessionTimeRemainingText}
                      </strong>
                    </div>
                  )}
                </section>
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Session controls</span>
                    <span className="sidebar-section-sub">Manage the active stream.</span>
                  </div>
                  <div className="sidebar-quick-actions">
                    <button type="button" className="sidebar-action-card" onClick={handleFullscreenToggle}>
                      {isFullscreen ? <Minimize size={16} /> : <Maximize size={16} />}
                      <span>{isFullscreen ? "Windowed" : "Fullscreen"}</span>
                    </button>
                    <button type="button" className="sidebar-action-card" onClick={handlePointerLockToggle}>
                      <MousePointer2 size={16} />
                      <span>{isPointerLocked ? "Release mouse" : "Capture mouse"}</span>
                    </button>
                    {onToggleMicrophone && (
                      <button type="button" className="sidebar-action-card" onClick={onToggleMicrophone}>
                        <Mic size={16} />
                        <span>Toggle mic</span>
                      </button>
                    )}
                    <button
                      type="button"
                      className="sidebar-action-card"
                      onClick={() => { void captureScreenshot(); }}
                      disabled={isSavingScreenshot || !screenshotApiAvailable}
                    >
                      <Camera size={16} />
                      <span>{isSavingScreenshot ? "Capturing" : "Screenshot"}</span>
                    </button>
                  </div>
                </section>
                {sessionTimeRemainingText !== null && (
                  <label className="sidebar-setting-card sidebar-mini-toggle" tabIndex={0}>
                    <span>
                      <strong>Show time in stats</strong>
                      <small>Keep session time visible in the performance overlay.</small>
                    </span>
                    <input
                      type="checkbox"
                      name="show-session-time-in-stats"
                      checked={showSessionTimeRemainingInStatsOverlay}
                      aria-label={t("sidebar.showSessionTimeRemainingInStatsOverlay")}
                      onChange={(event) => onShowSessionTimeRemainingInStatsOverlayChange(event.target.checked)}
                    />
                    <span className="sidebar-mini-toggle-track" />
                  </label>
                )}
                <div className="sidebar-open-shortcuts">
                  <span><kbd>{sidebarToggleShortcutDisplay}</kbd> Keyboard</span>
                  <span><Gamepad2 size={14} /> {CONTROLLER_SIDEBAR_SHORTCUT_DISPLAY}</span>
                </div>
              </div>
            )}

            {activeSidebarTab === "controls" && (
              <div className="sidebar-page" role="tabpanel">
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Mouse Preferences</span>
                    <span className="sidebar-section-sub">Fine-tune cursor movement.</span>
                  </div>
                  <div className="sidebar-row sidebar-row--column">
                    <div className="sidebar-row-top">
                      <span className="sidebar-label">Mouse Sensitivity</span>
                      <span className="settings-value-badge">{mouseSensitivity.toFixed(2)}x</span>
                    </div>
                    <input
                      type="range"
                      name="mouse-sensitivity"
                      aria-label="Mouse sensitivity"
                      className="settings-slider"
                      min={0.1}
                      max={4}
                      step={0.01}
                      value={mouseSensitivity}
                      onChange={(event) => {
                        const next = Number(event.target.value);
                        if (Number.isFinite(next)) {
                          onMouseSensitivityChange(Math.max(0.1, Math.min(4, next)));
                        }
                      }}
                    />
                    <span className="sidebar-hint">Multiplier applied to mouse movement (1.00 = default).</span>
                  </div>
                  <div className="sidebar-row sidebar-row--column">
                    <div className="sidebar-row-top">
                      <span className="sidebar-label">Mouse Accelerator</span>
                      <span className="settings-value-badge">{Math.round(mouseAcceleration)}%</span>
                    </div>
                    <input
                      type="range"
                      name="mouse-acceleration"
                      aria-label="Mouse accelerator"
                      className="settings-slider"
                      min={1}
                      max={150}
                      step={1}
                      value={Math.round(mouseAcceleration)}
                      onChange={(event) => {
                        const next = Number(event.target.value);
                        if (Number.isFinite(next)) {
                          onMouseAccelerationChange(Math.max(1, Math.min(150, Math.round(next))));
                        }
                      }}
                    />
                    <span className="sidebar-hint">Dynamic turn boost strength (1% = off-like, 150% = strongest).</span>
                  </div>
                </section>
                <div className="sidebar-separator" aria-hidden="true" />
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Video Filters</span>
                    <span className="sidebar-section-sub">GPU shaders applied to the stream.</span>
                  </div>
                  {gstreamerEnabled ? (
                    <span className="sidebar-hint">Video filters are unavailable while the native streamer renders the video.</span>
                  ) : (
                    <>
                      <div className="sidebar-row sidebar-row--aligned">
                        <span className="sidebar-label">Enable Filters</span>
                        <label className="sidebar-mini-toggle" title="Enable GPU post-processing filters" tabIndex={0}>
                          <input
                            type="checkbox"
                            name="enable-video-filters"
                            checked={videoShader.enabled}
                            aria-label="Enable video filters"
                            onChange={(event) => onVideoShaderChange({ ...videoShader, enabled: event.target.checked })}
                          />
                          <span className="sidebar-mini-toggle-track" />
                        </label>
                      </div>
                      {videoShader.enabled && (
                        <>
                          {([
                            { key: "sharpen", label: "Sharpen", min: 0, max: 100, neutral: 0, format: (v: number) => `${v}%`, hint: "Contrast-adaptive sharpening. Counters stream compression blur." },
                            { key: "saturation", label: "Saturation", min: 0, max: 200, neutral: 100, format: (v: number) => `${v}%` },
                            { key: "contrast", label: "Contrast", min: 50, max: 150, neutral: 100, format: (v: number) => `${v}%` },
                            { key: "brightness", label: "Brightness", min: 50, max: 150, neutral: 100, format: (v: number) => `${v}%` },
                            { key: "vibrance", label: "Vibrance", min: 0, max: 100, neutral: 0, format: (v: number) => `${v}%`, hint: "Boosts muted colors without oversaturating." },
                            { key: "filmGrain", label: "Film Grain", min: 0, max: 100, neutral: 0, format: (v: number) => `${v}%` },
                          ] as const).map((control) => (
                            <div key={control.key} className="sidebar-row sidebar-row--column">
                              <div className="sidebar-row-top">
                                <span className="sidebar-label">{control.label}</span>
                                <span className="settings-value-badge">{control.format(videoShader[control.key])}</span>
                              </div>
                              <input
                                type="range"
                                name={`video-filter-${control.key}`}
                                aria-label={`${control.label} video filter`}
                                className="settings-slider"
                                min={control.min}
                                max={control.max}
                                step={1}
                                value={videoShader[control.key]}
                                onChange={(event) => {
                                  const next = Number(event.target.value);
                                  if (Number.isFinite(next)) {
                                    onVideoShaderChange({
                                      ...videoShader,
                                      [control.key]: Math.max(control.min, Math.min(control.max, Math.round(next))),
                                    });
                                  }
                                }}
                                onDoubleClick={() => onVideoShaderChange({ ...videoShader, [control.key]: control.neutral })}
                              />
                              {"hint" in control && control.hint && <span className="sidebar-hint">{control.hint}</span>}
                            </div>
                          ))}
                          <div className="sidebar-row sidebar-row--aligned">
                            <span className="sidebar-label">Reset Filters</span>
                            <button
                              type="button"
                              className="sidebar-button"
                              onClick={() => onVideoShaderChange({ ...DEFAULT_VIDEO_SHADER_SETTINGS, enabled: true })}
                            >
                              <span>Reset</span>
                            </button>
                          </div>
                        </>
                      )}
                    </>
                  )}
                </section>
                <div className="sidebar-separator" aria-hidden="true" />
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Audio</span>
                    <span className="sidebar-section-sub">Configure microphone handling.</span>
                  </div>
                  <div className="sidebar-row sidebar-row--column">
                    <div className="sidebar-row-top">
                      <span className="sidebar-label">Microphone Mode</span>
                      <span className="settings-value-badge">
                        {microphoneModes.find((option) => option.value === microphoneMode)?.label ?? microphoneMode}
                      </span>
                    </div>
                    <div className="sidebar-chip-row">
                      {microphoneModes.map((option) => (
                        <button
                          key={option.value}
                          type="button"
                          className={`sidebar-chip${microphoneMode === option.value ? " sidebar-chip--active" : ""}`}
                          onClick={() => onMicrophoneModeChange(option.value)}
                        >
                          <span>{option.label}</span>
                        </button>
                      ))}
                    </div>
                    <span className="sidebar-hint">
                      {microphoneModes.find((option) => option.value === microphoneMode)?.description ?? ""}
                    </span>
                  </div>
                  {microphoneMode !== "disabled" && (
                    <div className="sidebar-row sidebar-row--column">
                      <div className="sidebar-row-top">
                        <span className="sidebar-label">Send level</span>
                        <SidebarMicMutedBadge diagnosticsStore={diagnosticsStore} micTrack={micTrack} />
                      </div>
                      <canvas
                        ref={micMeterRef}
                        className="mic-meter-canvas"
                        aria-label="Microphone send level (what others hear)"
                      />
                      {!micTrack && <span className="sidebar-hint">Mic not active — check mode and permissions.</span>}
                    </div>
                  )}
                </section>
              </div>
            )}

            {activeSidebarTab === "media" && (
              <div className="sidebar-page" role="tabpanel">
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Gallery</span>
                    <span className="sidebar-section-sub">Screenshot key: {shortcuts.screenshot}</span>
                  </div>
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">Screenshots</span>
                    <button
                      type="button"
                      className="sidebar-button sidebar-screenshot-button"
                      onClick={() => {
                        void captureScreenshot();
                      }}
                      disabled={isSavingScreenshot || !screenshotApiAvailable}
                    >
                      <Camera size={14} />
                      <span>{isSavingScreenshot ? "Capturing..." : "Capture"}</span>
                    </button>
                  </div>
                  <div className="sidebar-gallery-row">
                    <button
                      type="button"
                      className="sidebar-gallery-arrow"
                      onClick={() => scrollGallery("left")}
                      aria-label="Scroll gallery left"
                    >
                      <ChevronLeft size={16} />
                    </button>
                    <div className="sidebar-gallery-strip" ref={galleryStripRef}>
                      {screenshots.map((shot) => (
                        <button
                          key={shot.id}
                          type="button"
                          className="sidebar-gallery-item"
                          onClick={() => setSelectedScreenshotId(shot.id)}
                          title={new Date(shot.createdAtMs).toLocaleString()}
                        >
                          <img src={shot.dataUrl} alt={`Screenshot ${shot.fileName}`} />
                        </button>
                      ))}
                    </div>
                    <button
                      type="button"
                      className="sidebar-gallery-arrow"
                      onClick={() => scrollGallery("right")}
                      aria-label="Scroll gallery right"
                    >
                      <ChevronRight size={16} />
                    </button>
                  </div>
                  {screenshots.length === 0 && (
                    <span className="sidebar-hint">No screenshots yet. Press {shortcuts.screenshot} to capture one.</span>
                  )}
                  {galleryError && <span className="sidebar-hint sidebar-hint--error">{galleryError}</span>}
                </section>
                <div className="sidebar-separator" aria-hidden="true" />
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Recordings</span>
                    <span className="sidebar-section-sub">Record key: {shortcuts.recording}</span>
                  </div>
                  {usedMimeType && (
                    <span className="sidebar-hint sidebar-hint--codec">Codec: {usedMimeType}</span>
                  )}
                  <span className="sidebar-hint sidebar-hint--codec">
                    Recording bitrate: {recordingBitrateMbps === null ? "Auto" : `${recordingBitrateMbps} Mbps`}
                  </span>
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">
                      {isRecording ? `Recording ${formatElapsed(Math.round(recordingDurationMs / 1000))}` : "Record"}
                    </span>
                    <button
                      type="button"
                      className="sidebar-button sidebar-screenshot-button"
                      onClick={() => { void toggleRecording(); }}
                      disabled={!recordingApiAvailable}
                    >
                      {isRecording ? <Square size={14} /> : <Circle size={14} />}
                      <span>{isRecording ? "Stop" : "Start"}</span>
                    </button>
                  </div>
                  {recordingError && (
                    <span className="sidebar-hint sidebar-hint--error">{recordingError}</span>
                  )}
                  {recordings.length === 0 ? (
                    <span className="sidebar-hint">No recordings yet. Press {shortcuts.recording} to record.</span>
                  ) : (
                    <div className="sidebar-gallery-row">
                      <button
                        type="button"
                        className="sidebar-gallery-arrow"
                        onClick={() => scrollRecCarousel("left")}
                        aria-label="Scroll recordings left"
                      >
                        <ChevronLeft size={16} />
                      </button>
                      <div className="sidebar-rec-strip" ref={recCarouselRef}>
                        {recordings.map((rec) => (
                          <div key={rec.id} className="sidebar-rec-card">
                            {rec.thumbnailDataUrl ? (
                              <img
                                className="sidebar-rec-card-thumb"
                                src={rec.thumbnailDataUrl}
                                alt=""
                              />
                            ) : (
                              <div className="sidebar-rec-card-thumb sidebar-rec-card-thumb--placeholder">
                                <Video size={20} />
                              </div>
                            )}
                            <div className="sidebar-rec-card-meta">
                              <span className="sidebar-rec-card-title">{rec.gameTitle ?? "Untitled"}</span>
                              <span className="sidebar-rec-card-detail">
                                {formatElapsed(Math.round(rec.durationMs / 1000))} · {formatFileSize(rec.sizeBytes)}
                              </span>
                            </div>
                            <div className="sidebar-rec-card-actions">
                              <button
                                type="button"
                                className="sidebar-rec-card-action"
                                aria-label="Show in folder"
                                title="Show in folder"
                                onClick={() => { void window.openNow.showRecordingInFolder(rec.id); }}
                                disabled={typeof window.openNow?.showRecordingInFolder !== "function"}
                              >
                                <FolderOpen size={11} />
                              </button>
                              <button
                                type="button"
                                className="sidebar-rec-card-action sidebar-rec-card-action--danger"
                                aria-label="Delete recording"
                                title="Delete"
                                onClick={() => { void handleDeleteRecording(rec.id); }}
                              >
                                <Trash2 size={11} />
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                      <button
                        type="button"
                        className="sidebar-gallery-arrow"
                        onClick={() => scrollRecCarousel("right")}
                        aria-label="Scroll recordings right"
                      >
                        <ChevronRight size={16} />
                      </button>
                    </div>
                  )}
                </section>
              </div>
            )}

            {activeSidebarTab === "shortcuts" && (
              <div className="sidebar-page" role="tabpanel">
                <section className="sidebar-section">
                  <div className="sidebar-section-header">
                    <span>Shortcut Bindings</span>
                    <span className="sidebar-section-sub">Edit screenshot keybind here</span>
                  </div>
                  <div className="sidebar-row sidebar-row--column">
                    <div className="sidebar-row-top">
                      <span className="sidebar-label">Screenshot Shortcut</span>
                    </div>
                    <input
                      type="text"
                      name="screenshot-shortcut"
                      aria-label="Screenshot shortcut"
                      className={`settings-text-input settings-shortcut-input sidebar-shortcut-input ${screenshotShortcutError ? "error" : ""}`}
                      value={screenshotShortcutInput}
                      readOnly
                      onFocus={(event) => event.target.select()}
                      onPaste={handleStreamScreenshotShortcutPaste}
                      onBlur={() => {
                        const error = getScreenshotShortcutError(screenshotShortcutInput);
                        if (error) {
                          setScreenshotShortcutError(error);
                          return;
                        }
                        const normalized = normalizeShortcut(screenshotShortcutInput.trim());
                        if (!normalized.valid) {
                          setScreenshotShortcutError("Invalid shortcut format.");
                          return;
                        }
                        setScreenshotShortcutError(null);
                        setScreenshotShortcutInput(normalized.canonical);
                        if (normalized.canonical !== shortcuts.screenshot) {
                          onScreenshotShortcutChange(normalized.canonical);
                        }
                      }}
                      onKeyDown={handleStreamScreenshotShortcutKeyDown}
                      placeholder="Click, then press a key"
                      title="Focus and press the key combination to bind"
                      spellCheck={false}
                    />
                  </div>
                  {screenshotShortcutError && <span className="sidebar-hint sidebar-hint--error">{screenshotShortcutError}</span>}
                  <div className="sidebar-row sidebar-row--column">
                    <div className="sidebar-row-top">
                      <span className="sidebar-label">Recording Shortcut</span>
                    </div>
                    <input
                      type="text"
                      name="recording-shortcut"
                      aria-label="Recording shortcut"
                      className={`settings-text-input settings-shortcut-input sidebar-shortcut-input ${recordingShortcutError ? "error" : ""}`}
                      value={recordingShortcutInput}
                      readOnly
                      onFocus={(event) => event.target.select()}
                      onPaste={handleStreamRecordingShortcutPaste}
                      onBlur={() => {
                        const error = getRecordingShortcutError(recordingShortcutInput);
                        if (error) {
                          setRecordingShortcutError(error);
                          return;
                        }
                        const normalized = normalizeShortcut(recordingShortcutInput.trim());
                        if (!normalized.valid) {
                          setRecordingShortcutError("Invalid shortcut format.");
                          return;
                        }
                        setRecordingShortcutError(null);
                        setRecordingShortcutInput(normalized.canonical);
                        if (normalized.canonical !== shortcuts.recording) {
                          onRecordingShortcutChange(normalized.canonical);
                        }
                      }}
                      onKeyDown={handleStreamRecordingShortcutKeyDown}
                      placeholder="Click, then press a key"
                      title="Focus and press the key combination to bind"
                      spellCheck={false}
                    />
                  </div>
                  {recordingShortcutError && <span className="sidebar-hint sidebar-hint--error">{recordingShortcutError}</span>}
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">Toggle Stats</span>
                    <span className="settings-value-badge">{shortcuts.toggleStats}</span>
                  </div>
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">Mouse Lock</span>
                    <span className="settings-value-badge">{shortcuts.togglePointerLock}</span>
                  </div>
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">Stop Stream</span>
                    <span className="settings-value-badge">{shortcuts.stopStream}</span>
                  </div>
                  {shortcuts.toggleMicrophone && (
                    <div className="sidebar-row sidebar-row--aligned">
                      <span className="sidebar-label">Toggle Microphone</span>
                      <span className="settings-value-badge">{shortcuts.toggleMicrophone}</span>
                    </div>
                  )}
                  <div className="sidebar-row sidebar-row--aligned">
                    <span className="sidebar-label">Toggle Sidebar</span>
                    <span className="sidebar-shortcut-stack">
                      <span className="settings-value-badge">{sidebarToggleShortcutDisplay}</span>
                      <span className="settings-value-badge">{CONTROLLER_SIDEBAR_SHORTCUT_DISPLAY}</span>
                    </span>
                  </div>
                </section>
              </div>
            )}
          </SideBar>
        )}
      </AnimatePresence>

      {selectedScreenshot && (
        <div className="sv-shot-modal" role="dialog" aria-modal="true" aria-label="Screenshot preview">
          <button
            type="button"
            className="sv-shot-modal-backdrop"
            onClick={() => setSelectedScreenshotId(null)}
            aria-label="Close screenshot preview"
          />
          <div className="sv-shot-modal-card">
            <div className="sv-shot-modal-head">
              <h4>Screenshot</h4>
              <button
                type="button"
                className="sv-shot-modal-close"
                onClick={() => setSelectedScreenshotId(null)}
                aria-label="Close screenshot preview"
              >
                <X size={16} />
              </button>
            </div>
            <img
              className="sv-shot-modal-image"
              src={selectedScreenshot.dataUrl}
              alt={`Screenshot ${selectedScreenshot.fileName}`}
            />
            <div className="sv-shot-modal-actions">
              <button
                type="button"
                className="sv-shot-modal-btn"
                onClick={() => {
                  void handleSaveScreenshotAs();
                }}
              >
                <Save size={14} />
                <span>Save</span>
              </button>
              <button
                type="button"
                className="sv-shot-modal-btn sv-shot-modal-btn--danger"
                onClick={() => {
                  void handleDeleteScreenshot();
                }}
              >
                <Trash2 size={14} />
                <span>Delete</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Gradient background when no video */}
      <StreamEmptyState diagnosticsStore={diagnosticsStore} />
      <StreamWaitingForVideo diagnosticsStore={diagnosticsStore} isConnecting={isConnecting} />

      {/* Connecting overlay */}
      {isConnecting && (
        <div className="sv-connect">
          <div className="sv-connect-inner">
            <MotionSpinner className="sv-connect-spin" size={44} label="Connecting to stream" />
            <p className="sv-connect-title">Connecting to {gameTitle}</p>
            {PlatformIcon && (
              <div className="sv-connect-platform" title={platformName}>
                <span className="sv-connect-platform-icon">
                  <PlatformIcon />
                </span>
                <span>{platformName}</span>
              </div>
            )}
            <p className="sv-connect-sub">Setting up stream...</p>
          </div>
        </div>
      )}

      {sessionCounterEnabled && !isConnecting && (
        <div
          className={`sv-session-clock${showSessionClock ? " is-visible" : ""}`}
          title="Current gaming session elapsed time"
          aria-hidden={!showSessionClock}
        >
          <SessionElapsedIndicator startedAtMs={sessionStartedAtMs} active={isStreaming} />
        </div>
      )}

      {streamWarning && !isConnecting && !exitPrompt.open && (
        <div
          className={`sv-time-warning sv-time-warning--${streamWarning.tone}`}
          title="Session time warning"
        >
          <AlertTriangle size={14} />
          <span>
            {streamWarning.message}
            {warningSeconds ? ` · ${warningSeconds} left` : ""}
          </span>
        </div>
      )}

      {antiAfkToggleAck && !isConnecting && (
        <div className={`sv-afk-ack sv-afk-ack--${antiAfkToggleAck}`} role="status" aria-live="polite">
          <span className="sv-afk-ack-dot" aria-hidden />
          <span>{antiAfkToggleAck === "on" ? "Anti-AFK on" : "Anti-AFK off"}</span>
        </div>
      )}

      <SessionStartedSplash
        visible={sessionReadySplashVisible && !isConnecting}
        gameTitle={gameTitle}
        onFinished={handleSessionReadySplashFinished}
      />

      <AnimatePresence>
        {showStatsHud && (
          <StreamStatsHud
            key="stream-stats-hud"
            diagnosticsStore={diagnosticsStore}
            gstreamerEnabled={gstreamerEnabled}
            serverRegion={serverRegion}
            sessionTimeRemainingText={showSessionTimeRemainingInStats ? sessionTimeRemainingText : null}
            hintsVisible={showHints}
          />
        )}
      </AnimatePresence>

      {/* Microphone toggle button */}
      <MicrophoneIndicator
        diagnosticsStore={diagnosticsStore}
        showAntiAfkIndicator={antiAfkEnabled && showAntiAfkIndicator}
        hideStreamButtons={hideStreamButtons}
        isConnecting={isConnecting}
        onToggleMicrophone={onToggleMicrophone}
      />

      {/* Anti-AFK indicator */}
      <AntiAfkIndicator
        diagnosticsStore={diagnosticsStore}
        antiAfkEnabled={antiAfkEnabled}
        showAntiAfkIndicator={showAntiAfkIndicator}
        isConnecting={isConnecting}
      />

      {/* Recording indicator (top-left, stacked below other badges) */}
      <RecordingIndicator
        diagnosticsStore={diagnosticsStore}
        showAntiAfkIndicator={antiAfkEnabled && showAntiAfkIndicator}
        hideStreamButtons={hideStreamButtons}
        isConnecting={isConnecting}
        isRecording={isRecording}
        onToggleMicrophone={onToggleMicrophone}
        recordingDurationMs={recordingDurationMs}
      />

      {exitPrompt.open && !isConnecting && typeof document !== "undefined" && createPortal(
        <div className="sv-exit" role="dialog" aria-modal="true" aria-label="Exit stream confirmation">
          <button
            type="button"
            className="sv-exit-backdrop"
            onClick={onCancelExit}
            aria-label="Cancel exit"
          />
          <div className="sv-exit-card">
            <div className="sv-exit-kicker">Session Control</div>
            <h3 className="sv-exit-title">Exit Stream?</h3>
            <p className="sv-exit-text">
              Do you really want to exit <strong>{exitPrompt.gameTitle}</strong>?
            </p>
            <p className="sv-exit-subtext">Your current cloud gaming session will be closed.</p>
            <div className="sv-exit-actions">
              <button type="button" className="sv-exit-btn sv-exit-btn-cancel" onClick={onCancelExit}>
                Keep Playing
              </button>
              <button type="button" className="sv-exit-btn sv-exit-btn-confirm" onClick={onConfirmExit}>
                Exit Stream
              </button>
            </div>
            <div className="sv-exit-hint">
              <span><kbd>Enter</kbd> confirm · <kbd>Esc</kbd> cancel</span>
              <span><kbd>A</kbd> select · <kbd>B</kbd> cancel</span>
            </div>
          </div>
        </div>,
        document.body,
      )}

      {/* Fullscreen toggle */}
      {!hideStreamButtons && (
        <button
          className="sv-fs"
          onClick={handleFullscreenToggle}
          title={isFullscreen ? "Exit fullscreen" : "Enter fullscreen"}
          aria-label={isFullscreen ? "Exit fullscreen" : "Enter fullscreen"}
        >
          {isFullscreen ? <Minimize size={18} /> : <Maximize size={18} />}
        </button>
      )}

      {/* End session button */}
      {!hideStreamButtons && (
        <button
          className="sv-end"
          onClick={onEndSession}
          title="End session"
          aria-label="End session"
        >
          <LogOut size={18} />
        </button>
      )}

      {/* Keyboard hints */}
      {showHints && !isConnecting && (
        <div className="sv-hints">
          <div className="sv-hint"><kbd>{shortcuts.toggleStats}</kbd><span>Stats</span></div>
          <div className="sv-hint"><kbd>{shortcuts.togglePointerLock}</kbd><span>Mouse lock</span></div>
          <div className="sv-hint"><kbd>{shortcuts.toggleFullscreen}</kbd><span>Full screen</span></div>
          <div className="sv-hint"><kbd>{shortcuts.stopStream}</kbd><span>Stop</span></div>
          <div className="sv-hint"><kbd>{CONTROLLER_SIDEBAR_SHORTCUT_DISPLAY}</kbd><span>Controller menu</span></div>
          {shortcuts.toggleMicrophone && <div className="sv-hint"><kbd>{shortcuts.toggleMicrophone}</kbd><span>Mic</span></div>}
        </div>
      )}

      {/* Game title (bottom-center, fades) */}
      <StreamTitleBar
        diagnosticsStore={diagnosticsStore}
        gameTitle={gameTitle}
        platformName={platformName}
        PlatformIcon={PlatformIcon}
        showHints={showHints}
      />
    </div>
  );
}
