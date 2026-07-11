import type { StreamClientMode } from "./stream";

export type NativeStreamerBackend = "stub" | "gstreamer";
export type NativeStreamerBackendPreference = "auto" | NativeStreamerBackend;
export type NativeStreamerFeatureMode = "auto" | "disabled" | "forced";
export type NativeVideoBackendPreference = "auto" | "d3d11" | "d3d12";

export const NATIVE_STREAMER_WINDOWS_ONLY_MESSAGE = "experimental feature: Windows only. Mac and Linux support is being worked on";

export function isNativeStreamerSupportedPlatform(platform: string): boolean {
  const normalized = platform.toLowerCase();
  return normalized === "win32" || normalized.startsWith("win") || normalized.includes("windows");
}

export function normalizeStreamClientModeForPlatform(mode: StreamClientMode, platform: string): StreamClientMode {
  return mode === "native" && !isNativeStreamerSupportedPlatform(platform) ? "web" : mode;
}

export function nativeStreamerFeatureModeToEnvValue(mode: NativeStreamerFeatureMode): "auto" | "0" | "1" {
  switch (mode) {
    case "disabled":
      return "0";
    case "forced":
      return "1";
    default:
      return "auto";
  }
}

export type NativeGstreamerRuntimeSource = "bundled" | "system" | "missing" | "unknown";

export interface NativeGstreamerInstallInstruction {
  distro: string;
  command: string;
  note?: string;
}

export interface NativeGstreamerRuntimeStatus {
  source: NativeGstreamerRuntimeSource;
  bundled: boolean;
  path?: string;
  message: string;
  installInstructions?: NativeGstreamerInstallInstruction[];
}

export interface NativeStreamerStatus {
  detected: boolean;
  gstreamerAvailable: boolean;
  supportsOfferAnswer: boolean;
  backend?: NativeStreamerBackend;
  fallbackReason?: string;
  videoBackends?: NativeVideoBackendCapability[];
  activeVideoBackend?: NativeVideoBackendCapability;
  codecSummary?: string;
  zeroCopySummary?: string;
  gstreamerRuntime: NativeGstreamerRuntimeStatus;
  message: string;
}

export function createUnsupportedNativeStreamerStatus(): NativeStreamerStatus {
  return {
    detected: false,
    gstreamerAvailable: false,
    supportsOfferAnswer: false,
    gstreamerRuntime: {
      source: "unknown",
      bundled: false,
      message: NATIVE_STREAMER_WINDOWS_ONLY_MESSAGE,
    },
    message: NATIVE_STREAMER_WINDOWS_ONLY_MESSAGE,
  };
}

export type NativeVideoBackendId =
  | "d3d12"
  | "d3d11"
  | "videotoolbox"
  | "vaapi"
  | "v4l2"
  | "vulkan"
  | "software"
  | string;

export interface NativeVideoCodecCapability {
  codec: "h264" | "h265" | "av1" | string;
  available: boolean;
  decoder?: string;
  parser?: string;
  depayloader?: string;
  reason?: string;
}

export interface NativeVideoBackendCapability {
  backend: NativeVideoBackendId;
  platform: "windows" | "macos" | "linux" | "cross-platform" | "other" | string;
  codecs: NativeVideoCodecCapability[];
  zeroCopyModes: string[];
  sink?: string;
  available: boolean;
  reason?: string;
}
