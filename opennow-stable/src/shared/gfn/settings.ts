import type {
  ColorQuality,
  NativeTransitionDiagnostics,
  StreamClientMode,
  VideoAccelerationPreference,
  VideoCodec,
} from "./stream";
import type {
  NativeStreamerBackendPreference,
  NativeStreamerFeatureMode,
  NativeVideoBackendPreference,
  StreamTransportMode,
} from "./nativeStreamer";
import type { GameLanguage, KeyboardLayout } from "./keyboard";
import type { VideoShaderSettings } from "./videoShader";
import type { UpdateChannel } from "./updater";
import { normalizeStreamPreferences } from "./stream";

export type AppAccentColor = "green" | "blue" | "violet" | "amber" | "rose";
export type AppTheme = "light" | "dark" | "auto";
export type MicrophoneMode = "disabled" | "push-to-talk" | "voice-activity";
export type AspectRatio = "16:9" | "16:10" | "21:9" | "32:9";
export type RuntimePlatform =
  | "aix"
  | "android"
  | "cygwin"
  | "darwin"
  | "freebsd"
  | "haiku"
  | "linux"
  | "netbsd"
  | "openbsd"
  | "sunos"
  | "win32"
  | "unknown";

export type MacOsMicrophoneAccessStatus = "not-determined" | "granted" | "denied" | "restricted" | "unknown";

export interface MicrophonePermissionResult {
  platform: RuntimePlatform;
  isMacOs: boolean;
  status: MacOsMicrophoneAccessStatus | "not-applicable";
  granted: boolean;
  canRequest: boolean;
  shouldUseBrowserApi: boolean;
}

export interface Settings {
  resolution: string;
  aspectRatio: AspectRatio;
  posterSizeScale: number;
  fps: number;
  maxBitrateMbps: number;
  /** Recording video bitrate in Mbps; null means let MediaRecorder choose automatically */
  recordingBitrateMbps: number | null;
  streamClientMode: StreamClientMode;
  nativeStreamerBackend: NativeStreamerBackendPreference;
  nativeVideoBackend: NativeVideoBackendPreference;
  nativeStreamerExecutablePath: string;
  nativeCloudGsyncMode: NativeStreamerFeatureMode;
  nativeD3dFullscreenMode: NativeStreamerFeatureMode;
  nativeExternalRenderer: boolean;
  transportMode: StreamTransportMode;
  showNativeStreamerStats: boolean;
  codec: VideoCodec;
  decoderPreference: VideoAccelerationPreference;
  encoderPreference: VideoAccelerationPreference;
  colorQuality: ColorQuality;
  region: string;
  sessionProxyEnabled: boolean;
  sessionProxyUrl: string;
  clipboardPaste: boolean;
  /** Enable experimental gyroscope controller input mapping */
  enableGyroscopeControls: boolean;
  /** macOS-only workaround that restores Chromium's older HID path for Steam Controller compatibility */
  steamControllerCompatibilityMode: boolean;
  /** Use the WebRTC cursor_channel overlay instead of leaving cursor rendering to the stream. */
  nativeCursorOverlay: boolean;
  mouseSensitivity: number;
  mouseAcceleration: number;
  shortcutToggleStats: string;
  shortcutTogglePointerLock: string;
  shortcutToggleFullscreen: string;
  shortcutStopStream: string;
  shortcutToggleAntiAfk: string;
  shortcutToggleMicrophone: string;
  shortcutScreenshot: string;
  shortcutToggleRecording: string;
  microphoneMode: MicrophoneMode;
  microphoneDeviceId: string;
  hideStreamButtons: boolean;
  showAntiAfkIndicator: boolean;
  showStatsOnLaunch: boolean;
  /** Skip the free-tier queue server selection modal and launch with default routing */
  hideServerSelector: boolean;
  /** Desktop UI accent preset */
  appAccentColor: AppAccentColor;
  /** UI Theme */
  appTheme: AppTheme;
  /** Use translucent overlays for settings and navbars */
  translucentUI: boolean;
  /** Use the large-screen controller-oriented shell and library layout */
  controllerMode: boolean;
  /** Launch fullscreen with Controller Mode enabled, like GeForce NOW's TV mode */
  launchInConsoleMode: boolean;
  autoFullScreen: boolean;
  favoriteGameIds: string[];
  sessionCounterEnabled: boolean;
  /** Also show the session-limit countdown in the stats overlay while streaming */
  showSessionTimeRemainingInStatsOverlay: boolean;
  sessionClockShowEveryMinutes: number;
  sessionClockShowDurationSeconds: number;
  windowWidth: number;
  windowHeight: number;
  /** Keyboard layout for mapping physical keys inside the remote session */
  keyboardLayout: KeyboardLayout;
  /** In-game language setting (sent to GFN servers via languageCode parameter) */
  gameLanguage: GameLanguage;
  /** User opt-in for NVIDIA's per-game in-game graphics/settings persistence. */
  enablePersistingInGameSettings: boolean;
  /** Experimental request for Low Latency, Low Loss, Scalable throughput on new sessions */
  enableL4S: boolean;
  /** Request Cloud G-Sync / Variable Refresh Rate on new sessions */
  enableCloudGsync: boolean;
  /** Hidden diagnostics for native transition recovery and 240 FPS server-side stream changes */
  nativeTransitionDiagnostics?: NativeTransitionDiagnostics;
  /** Show the currently streaming game as Discord Rich Presence activity */
  discordRichPresence: boolean;
  /** Automatically check GitHub Releases for app updates in the background */
  autoCheckForUpdates: boolean;
  /** Release channel used for application updates */
  updateChannel: UpdateChannel;
  /** When true, pressing Escape will exit fullscreen; when false Escape is sent to the game while pointer-locked */
  allowEscapeToExitFullscreen?: boolean;
  /** Last version for which the release highlights modal was acknowledged (empty = never) */
  lastSeenReleaseHighlightsVersion: string;
  /** Client-side GPU post-processing shaders applied to the stream (web client mode) */
  videoShader: VideoShaderSettings;
}

export const DEFAULT_STREAM_PREFERENCES: Readonly<Pick<Settings, "codec" | "colorQuality">> = Object.freeze({
  codec: "H264",
  colorQuality: "8bit_420",
});

export function getDefaultStreamPreferences(): Pick<Settings, "codec" | "colorQuality"> {
  const normalized = normalizeStreamPreferences(
    DEFAULT_STREAM_PREFERENCES.codec,
    DEFAULT_STREAM_PREFERENCES.colorQuality,
  );
  return {
    codec: normalized.codec,
    colorQuality: normalized.colorQuality,
  };
}
