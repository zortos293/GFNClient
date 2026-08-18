import crypto from "node:crypto";

import type { SessionCreateRequest, StreamSettings } from "@shared/gfn";
import {
  colorQualityBitDepth,
  colorQualityChromaFormat,
} from "@shared/gfn";

import type { CloudMatchRequest } from "./types";
import { resolveGfnDeviceIdentity } from "./deviceIdentity";
import { getStableDeviceId } from "./deviceId";
import {
  appLaunchModeWireValue,
  buildRequestedStreamingFeatures,
  shouldEnableInGameSettingsPersistence,
} from "./cloudmatchFeatures";

export function parseResolution(input: string): { width: number; height: number } {
  const [rawWidth, rawHeight] = input.split("x");
  const width = Number.parseInt(rawWidth ?? "", 10);
  const height = Number.parseInt(rawHeight ?? "", 10);

  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return { width: 1920, height: 1080 };
  }

  return { width, height };
}

export function timezoneOffsetMs(): number {
  return -new Date().getTimezoneOffset() * 60 * 1000;
}

export function sessionMetadata(
  width: number,
  height: number,
  transportMode: StreamSettings["transportMode"],
): Array<{ key: string; value: string }> {
  return [
    { key: "SubSessionId", value: crypto.randomUUID() },
    { key: "wssignaling", value: "1" },
    ...(transportMode === "nvst" ? [] : [{ key: "GSStreamerType", value: "WebRTC" }]),
    { key: "networkType", value: "Unknown" },
    { key: "ClientImeSupport", value: "0" },
    {
      key: "clientPhysicalResolution",
      value: JSON.stringify({ horizontalPixels: width, verticalPixels: height }),
    },
    { key: "surroundAudioInfo", value: "2" },
  ];
}

export function buildSessionRequestBody(
  input: SessionCreateRequest,
  deviceHashId: string,
  networkTestSessionId: string | null = null,
): CloudMatchRequest {
  const { width, height } = parseResolution(input.settings.resolution);
  const cq = input.settings.colorQuality;
  // IMPORTANT: hdrEnabled is a SEPARATE toggle from color quality.
  // The Rust reference (cloudmatch.rs) uses settings.hdr_enabled independently.
  // 10-bit color depth does NOT mean HDR — you can have 10-bit SDR.
  // Conflating them caused the server to set up an HDR pipeline, which
  // dynamically downscaled resolution to ~540p.
  const hdrEnabled = false; // No HDR toggle implemented yet; hardcode off like claim body
  const bitDepth = colorQualityBitDepth(cq);
  const chromaFormat = colorQualityChromaFormat(cq);
  const accountLinked = input.accountLinked ?? true;
  const useClassicStreamer = input.settings.transportMode === "nvst";

  return {
    sessionRequestData: {
      appId: input.appId,
      internalTitle: input.internalTitle || null,
      availableSupportedControllers: [],
      networkTestSessionId,
      parentSessionId: null,
      clientIdentification: "GFN-PC",
      // Keep device identity stable across create -> reconnect/resume flows.
      // The official client preserves this identity, and resume reliability depends on it.
      deviceHashId,
      clientVersion: "30.0",
      sdkVersion: "1.0",
      streamerVersion: 1,
      clientPlatformName: resolveGfnDeviceIdentity().clientPlatformName,
      clientRequestMonitorSettings: [
        {
          monitorId: 0,
          positionX: 0,
          positionY: 0,
          widthInPixels: width,
          heightInPixels: height,
          framesPerSecond: input.settings.fps,
          sdrHdrMode: hdrEnabled ? 1 : 0,
          displayData: hdrEnabled
            ? {
                desiredContentMaxLuminance: 1000,
                desiredContentMinLuminance: 0,
                desiredContentMaxFrameAverageLuminance: 500,
              }
            : {},
          hdr10PlusGamingData: null,
          dpi: 0,
        },
      ],
      useOps: true,
      audioMode: 2,
      metaData: sessionMetadata(width, height, input.settings.transportMode),
      sdrHdrMode: hdrEnabled ? 1 : 0,
      clientDisplayHdrCapabilities: hdrEnabled
        ? {
            version: 1,
            hdrEdrSupportedFlagsInUint32: 1,
            staticMetadataDescriptorId: 0,
          }
        : null,
      surroundAudioInfo: 0,
      remoteControllersBitmap: 0,
      clientTimezoneOffset: timezoneOffsetMs(),
      enhancedStreamMode: 1,
      appLaunchMode: appLaunchModeWireValue(input.settings.appLaunchMode),
      secureRTSPSupported: useClassicStreamer,
      partnerCustomData: "",
      accountLinked,
      enablePersistingInGameSettings: shouldEnableInGameSettingsPersistence(input),
      userAge: 26,
      requestedStreamingFeatures: buildRequestedStreamingFeatures(
        input.settings,
        bitDepth,
        chromaFormat,
        hdrEnabled,
        input.supportedCodecs,
        input.settings.transportMode,
      ),
    },
  };
}

/**
 * Build claim/resume request payload
 */
export function buildClaimRequestBody(
  sessionId: string,
  appId: string,
  settings: StreamSettings,
  sessionAppLaunchMode?: number,
  enablePersistingInGameSettings = false,
): unknown {
  // For RESUME claims, we must NOT attempt to renegotiate streaming parameters.
  // The session is already configured on the server side. Sending different fps, resolution,
  // codec, etc. causes HTTP 400 from the server because those parameters are immutable for
  // an already-streaming session. Only send the action and minimal required fields.
  const deviceId = getStableDeviceId();
  const subSessionId = crypto.randomUUID();
  const timezoneMs = timezoneOffsetMs();
  const useClassicStreamer = settings.transportMode === "nvst";

  return {
    action: 2,
    data: "RESUME",
    sessionRequestData: {
      // Minimal fields required for resume - NO streaming parameter renegotiation
      audioMode: 2,
      remoteControllersBitmap: 0,
      sdrHdrMode: 0,
      networkTestSessionId: null,
      availableSupportedControllers: [],
      clientVersion: "30.0",
      deviceHashId: deviceId,
      internalTitle: null,
      clientPlatformName: resolveGfnDeviceIdentity().clientPlatformName,
      metaData: [
        { key: "SubSessionId", value: subSessionId },
        { key: "wssignaling", value: "1" },
        ...(useClassicStreamer ? [] : [{ key: "GSStreamerType", value: "WebRTC" }]),
        { key: "networkType", value: "Unknown" },
        { key: "ClientImeSupport", value: "0" },
        { key: "surroundAudioInfo", value: "2" },
      ],
      surroundAudioInfo: 0,
      clientTimezoneOffset: timezoneMs,
      clientIdentification: "GFN-PC",
      parentSessionId: null,
      appId: parseInt(appId, 10),
      streamerVersion: 1,
      // Resume must not renegotiate session parameters: prefer the wire value the
      // session was created with over whatever the UI toggles currently say.
      appLaunchMode: sessionAppLaunchMode ?? appLaunchModeWireValue(settings.appLaunchMode),
      sdkVersion: "1.0",
      enhancedStreamMode: 1,
      useOps: true,
      clientDisplayHdrCapabilities: null,
      accountLinked: true,
      partnerCustomData: "",
      enablePersistingInGameSettings,
      secureRTSPSupported: useClassicStreamer,
      userAge: 26,
    },
    metaData: [],
  };
}
