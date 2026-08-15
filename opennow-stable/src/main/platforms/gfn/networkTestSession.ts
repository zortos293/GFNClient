import { createHash } from "node:crypto";

import type { StreamSettings } from "@shared/gfn";

import { buildGfnCloudMatchHeaders } from "./clientHeaders";
import { resolveGfnDeviceIdentity } from "./deviceIdentity";
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
  };
  netTestSession?: {
    sessionId?: string;
  };
}

function parseResolution(input: string): { width: number; height: number } {
  const [rawWidth, rawHeight] = input.split("x");
  const width = Number.parseInt(rawWidth ?? "", 10);
  const height = Number.parseInt(rawHeight ?? "", 10);

  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return { width: 1920, height: 1080 };
  }

  return { width, height };
}

function cacheKey(base: string, settings: StreamSettings, token: string, proxyUrl?: string): string {
  const { width, height } = parseResolution(settings.resolution);
  const identityHash = createHash("sha256")
    .update(token)
    .update("\0")
    .update(proxyUrl ?? "")
    .digest("hex")
    .slice(0, 16);
  return `${base}\0${width}x${height}@${settings.fps}\0${identityHash}`;
}

export async function createNetworkTestSession(input: {
  base: string;
  token: string;
  clientId: string;
  deviceId: string;
  settings: StreamSettings;
  proxyUrl?: string;
}): Promise<string | null> {
  const key = cacheKey(input.base, input.settings, input.token, input.proxyUrl);
  const cached = networkTestSessionCache.get(key);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.sessionId;
  }
  networkTestSessionCache.delete(key);

  const { width, height } = parseResolution(input.settings.resolution);
  const body = {
    netTestRequestData: {
      clientPlatformName: resolveGfnDeviceIdentity().clientPlatformName,
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
      console.warn(
        `[CloudMatch] nettestsession failed HTTP ${response.status}: ${(await response.text()).slice(0, 200)}`,
      );
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

    networkTestSessionCache.set(key, {
      sessionId,
      expiresAt: Date.now() + NETWORK_TEST_SESSION_CACHE_TTL_MS,
    });
    return sessionId;
  } catch (error) {
    console.warn(`[CloudMatch] nettestsession creation failed: ${formatErrorForLog(error)}`);
    return null;
  }
}
