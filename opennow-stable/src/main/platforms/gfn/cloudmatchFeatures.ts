import type { AppLaunchMode, SessionCreateRequest, StreamSettings } from "@shared/gfn";
import { DEFAULT_MINIMUM_FPS_FOR_REFLEX_WITHOUT_VRR } from "@shared/cloudGsync";

import type { CloudMatchRequest } from "./types";

// Wire values used by cloudmatch session requests. Matches the official
// client's mapping: Default -> 1, GamepadFriendly -> 2, TouchFriendly -> 3.
const APP_LAUNCH_MODE_WIRE_VALUES: Record<AppLaunchMode, number> = {
  default: 1,
  gamepadFriendly: 2,
  touchFriendly: 3,
};

export function appLaunchModeWireValue(mode: AppLaunchMode | undefined): number {
  return APP_LAUNCH_MODE_WIRE_VALUES[mode ?? "default"];
}

export function buildRequestedStreamingFeatures(
  settings: StreamSettings,
  bitDepth: number,
  chromaFormat: number,
  _hdrEnabled: boolean,
): CloudMatchRequest["sessionRequestData"]["requestedStreamingFeatures"] {
  const cloudGsync = settings.enableCloudGsync;

  return {
    reflex: shouldRequestReflex(settings),
    bitDepth,
    cloudGsync,
    enabledL4S: settings.enableL4S,
    supportedHidDevices: 0,
    profile: 0,
    fallbackToLogicalResolution: false,
    chromaFormat,
    prefilterMode: 0,
    prefilterSharpness: 0,
    prefilterNoiseReduction: 0,
    hudStreamingMode: 0,
  };
}

export function shouldRequestReflex(settings: StreamSettings): boolean {
  if (typeof settings.cloudGsyncResolution?.reflexEnabled === "boolean") {
    return settings.cloudGsyncResolution.reflexEnabled;
  }

  const reflexMinimum =
    settings.cloudGsyncResolution?.capabilities.minimumFpsForReflexWithoutVrr
    ?? DEFAULT_MINIMUM_FPS_FOR_REFLEX_WITHOUT_VRR;
  return settings.enableCloudGsync || settings.fps >= reflexMinimum;
}

export function shouldEnableInGameSettingsPersistence(
  input: Pick<SessionCreateRequest, "enablePersistingInGameSettings" | "supportsInGameSettingsPersistence">,
): boolean {
  return (
    input.enablePersistingInGameSettings === true &&
    input.supportsInGameSettingsPersistence === true
  );
}
