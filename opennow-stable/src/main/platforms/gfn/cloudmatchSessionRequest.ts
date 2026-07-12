import crypto from "node:crypto";
import { createHash } from "node:crypto";

import type { SessionCreateRequest, StreamSettings } from "@shared/gfn";
import {
  colorQualityBitDepth,
  colorQualityChromaFormat,
} from "@shared/gfn";

import type { CloudMatchRequest } from "./types";
import { buildGfnCloudMatchHeaders } from "./clientHeaders";
import { getStableDeviceId } from "./deviceId";
import {
  appLaunchModeWireValue,
  buildRequestedStreamingFeatures,
  shouldEnableInGameSettingsPersistence,
} from "./cloudmatchFeatures";
import {
  fetchCloudMatch,
  formatErrorForLog,
} from "./cloudmatchTransport";

const NETWORK_TEST_SESSION_TIMEOUT_MS = 8_000;
const NETWORK_TEST_SESSION_CACHE_TTL_MS = 30 * 60 * 1000;

const networkTestSessionCache = new Map<string, { sessionId: string; expiresAt: number }>();

interface NetworkTestSessionResponse {
  requestStatus?: {
    statusCode?: number;
    statusDescription?: string;
    serverId?: string;
  };
  netTestSession?: {
    sessionId?: string;
    connectionInfo?: Array<{
      ip?: string;
      port?: number;
      appLevelProtocol?: number;
    }>;
    netTestThresholds?: {
      recommendedBandwidthMBPS?: number;
      requiredBandwidthMBPS?: number;
      recommendedLatencyMS?: number;
      requiredLatencyMS?: number;
      recommendedPacketLossPct?: number;
      requiredPacketLossPct?: number;
    };
    serverId?: string;
  };
}

export function parseResolution(input: string): { width: number; height: number } {
  const [rawWidth, rawHeight] = input.split("x");
  const width = Number.parseInt(rawWidth ?? "", 10);
  const height = Number.parseInt(rawHeight ?? "", 10);

  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return { width: 1920, height: 1080 };
  }

  return { width, height };
}

function networkTestSessionCacheKey(base: string, settings: StreamSettings, token: string, proxyUrl?: string): string {
  const { width, height } = parseResolution(settings.resolution);
  const identityHash = createHash("sha256")
    .update(token)
    .update("\0")
    .update(proxyUrl ?? "")
    .digest("hex")
    .slice(0, 16);
  return `${base}\0${width}x${height}@${settings.fps}\0${identityHash}`;
}

export function getCachedNetworkTestSessionId(base: string, settings: StreamSettings, token: string, proxyUrl?: string): string | null {
  const cacheKey = networkTestSessionCacheKey(base, settings, token, proxyUrl);
  const cached = networkTestSessionCache.get(cacheKey);
  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    networkTestSessionCache.delete(cacheKey);
    return null;
  }

  return cached.sessionId;
}

export function cacheNetworkTestSessionId(
  base: string,
  settings: StreamSettings,
  token: string,
  sessionId: string,
  proxyUrl?: string,
): void {
  networkTestSessionCache.set(networkTestSessionCacheKey(base, settings, token, proxyUrl), {
    sessionId,
    expiresAt: Date.now() + NETWORK_TEST_SESSION_CACHE_TTL_MS,
  });
}

export async function createNetworkTestSession(input: {
  base: string;
  token: string;
  clientId: string;
  deviceId: string;
  settings: StreamSettings;
  proxyUrl?: string;
}): Promise<string | null> {
  const cached = getCachedNetworkTestSessionId(input.base, input.settings, input.token, input.proxyUrl);
  if (cached) {
    return cached;
  }

  const { width, height } = parseResolution(input.settings.resolution);
  const body = {
    netTestRequestData: {
      clientPlatformName: "windows",
      netTestProfile: {
        widthInPixels: width,
        heightInPixels: height,
        framesPerSecond: input.settings.fps,
      },
    },
  };

  try {
    const response = await fetchCloudMatch(`${input.base}/v2/nettestsession`, {
      method: "POST",
      headers: buildGfnCloudMatchHeaders({
        token: input.token,
        clientId: input.clientId,
        deviceId: input.deviceId,
        includeOrigin: true,
      }),
      body: JSON.stringify(body),
    }, {
      proxyUrl: input.proxyUrl,
      timeoutMs: NETWORK_TEST_SESSION_TIMEOUT_MS,
      retries: 0,
    });

    if (!response.ok) {
      console.warn(`[CloudMatch] nettestsession failed HTTP ${response.status}: ${(await response.text()).slice(0, 200)}`);
      return null;
    }

    const payload = (await response.json()) as NetworkTestSessionResponse;
    if (payload.requestStatus?.statusCode !== 1) {
      console.warn(
        `[CloudMatch] nettestsession API error: ${payload.requestStatus?.statusCode ?? "unknown"} ` +
        `${payload.requestStatus?.statusDescription ?? ""}`.trim(),
      );
      return null;
    }

    const sessionId = payload.netTestSession?.sessionId?.trim();
    if (!sessionId) {
      console.warn("[CloudMatch] nettestsession response did not include a sessionId");
      return null;
    }

    cacheNetworkTestSessionId(input.base, input.settings, input.token, sessionId, input.proxyUrl);
    return sessionId;
  } catch (error) {
    console.warn(`[CloudMatch] nettestsession creation failed: ${formatErrorForLog(error)}`);
    return null;
  }
}

export function timezoneOffsetMs(): number {
  return -new Date().getTimezoneOffset() * 60 * 1000;
}

export function webRtcSessionMetadata(width: number, height: number): Array<{ key: string; value: string }> {
  return [
    { key: "SubSessionId", value: crypto.randomUUID() },
    { key: "wssignaling", value: "1" },
    { key: "GSStreamerType", value: "WebRTC" },
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
      clientPlatformName: "windows",
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
      metaData: webRtcSessionMetadata(width, height),
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
      secureRTSPSupported: false,
      partnerCustomData: "",
      accountLinked,
      enablePersistingInGameSettings: shouldEnableInGameSettingsPersistence(input),
      userAge: 26,
      requestedStreamingFeatures: buildRequestedStreamingFeatures(
        input.settings,
        bitDepth,
        chromaFormat,
        hdrEnabled,
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
      clientPlatformName: "windows",
      metaData: [
        { key: "SubSessionId", value: subSessionId },
        { key: "wssignaling", value: "1" },
        { key: "GSStreamerType", value: "WebRTC" },
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
      secureRTSPSupported: false,
      userAge: 26,
    },
    metaData: [],
  };
}
