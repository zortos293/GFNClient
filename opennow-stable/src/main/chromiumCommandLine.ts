import type { VideoAccelerationPreference } from "@shared/gfn";
import {
  buildVideoAccelerationCommandLine,
  isAccelerationPreference,
  type VideoAccelerationCommandLine,
} from "./videoAcceleration";

export const MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE =
  "XboxUseGameControllerDataFetcherMac";

export interface BootstrapChromiumPreferences {
  decoderPreference: VideoAccelerationPreference;
  encoderPreference: VideoAccelerationPreference;
  steamControllerCompatibilityMode: boolean;
}

export function normalizeBootstrapChromiumPreferences(raw: unknown): BootstrapChromiumPreferences {
  const defaults: BootstrapChromiumPreferences = {
    decoderPreference: "auto",
    encoderPreference: "auto",
    steamControllerCompatibilityMode: false,
  };
  if (!raw || typeof raw !== "object") {
    return defaults;
  }

  const parsed = raw as Partial<BootstrapChromiumPreferences>;
  return {
    decoderPreference: isAccelerationPreference(parsed.decoderPreference)
      ? parsed.decoderPreference
      : defaults.decoderPreference,
    encoderPreference: isAccelerationPreference(parsed.encoderPreference)
      ? parsed.encoderPreference
      : defaults.encoderPreference,
    steamControllerCompatibilityMode: parsed.steamControllerCompatibilityMode === true,
  };
}

export function buildChromiumCommandLine(
  preferences: BootstrapChromiumPreferences,
  platform: NodeJS.Platform,
  arch: NodeJS.Architecture,
): VideoAccelerationCommandLine {
  const commandLine = buildVideoAccelerationCommandLine(preferences, platform, arch);
  if (platform !== "darwin" || !preferences.steamControllerCompatibilityMode) {
    return commandLine;
  }

  return {
    ...commandLine,
    disableFeatures: [
      ...commandLine.disableFeatures,
      MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE,
    ],
  };
}
