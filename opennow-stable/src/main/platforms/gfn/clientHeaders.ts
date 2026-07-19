import crypto from "node:crypto";

import { GFN_PLAY_ORIGIN as SHARED_GFN_PLAY_ORIGIN, GFN_PLAY_REFERER as SHARED_GFN_PLAY_REFERER } from "@shared/gfn/endpoints";

import {
  resolveGfnDeviceIdentity,
  type GfnDeviceIdentity,
  type GfnDeviceOs,
} from "./deviceIdentity";

const GFN_WINDOWS_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173";
const GFN_MACOS_USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 GFN-PC/2.0.80.173";

export const GFN_USER_AGENT = process.platform === "darwin" ? GFN_MACOS_USER_AGENT : GFN_WINDOWS_USER_AGENT;
export const GFN_CLIENT_VERSION = "2.0.80.173";
export const LCARS_CLIENT_ID = "ec7e38d4-03af-4b58-b131-cfb0495903ab";

export const GFN_PLAY_ORIGIN = SHARED_GFN_PLAY_ORIGIN;
export const GFN_PLAY_REFERER = SHARED_GFN_PLAY_REFERER;
export const NVIDIA_FILE_ORIGIN = "https://nvfile";
export const NVIDIA_FILE_REFERER = "https://nvfile/";

export type GfnClientStreamer = "NVIDIA-CLASSIC" | "WEBRTC";
export type GfnClientType = "NATIVE" | "BROWSER";
export type { GfnDeviceOs };

export function gfnJwtAuthorization(token: string): string {
  return `GFNJWT ${token}`;
}

export function bearerAuthorization(token: string): string {
  return `Bearer ${token}`;
}

export function platformToGfnDeviceOs(platform: NodeJS.Platform = process.platform): GfnDeviceOs {
  return resolveGfnDeviceIdentity({ identifyAsSteamDeck: false, platform }).deviceOs;
}

function applyDeviceIdentityHeaders(
  headers: Record<string, string>,
  identity: GfnDeviceIdentity,
  options?: { includeMakeModel?: boolean },
): void {
  headers["nv-device-os"] = identity.deviceOs;
  headers["nv-device-type"] = identity.deviceType;
  if (options?.includeMakeModel !== false) {
    headers["nv-device-make"] = identity.deviceMake;
    headers["nv-device-model"] = identity.deviceModel;
  }
}

export interface NvidiaAuthHeadersOptions {
  bearerToken?: string;
  accept?: string;
  contentType?: string;
  includeReferer?: boolean;
}

export function buildNvidiaAuthHeaders(options: NvidiaAuthHeadersOptions = {}): Record<string, string> {
  const headers: Record<string, string> = {};

  if (options.bearerToken !== undefined) {
    headers.Authorization = bearerAuthorization(options.bearerToken);
  }
  if (options.contentType) {
    headers["Content-Type"] = options.contentType;
  }

  headers.Origin = NVIDIA_FILE_ORIGIN;
  if (options.includeReferer) {
    headers.Referer = NVIDIA_FILE_REFERER;
  }
  headers.Accept = options.accept ?? "application/json, text/plain, */*";
  headers["User-Agent"] = GFN_USER_AGENT;

  return headers;
}

export interface GfnLcarsHeadersOptions {
  token?: string;
  clientId?: string;
  clientType: GfnClientType;
  clientStreamer: GfnClientStreamer;
  accept?: string;
  deviceOs?: GfnDeviceOs;
  identifyAsSteamDeck?: boolean;
  includeUserAgent?: boolean;
  includeEmptyTokenAuthorization?: boolean;
}

export function buildGfnLcarsHeaders(options: GfnLcarsHeadersOptions): Record<string, string> {
  const identity = resolveGfnDeviceIdentity({ identifyAsSteamDeck: options.identifyAsSteamDeck });
  const headers: Record<string, string> = {
    Accept: options.accept ?? "application/json",
  };

  if (options.token || (options.includeEmptyTokenAuthorization && options.token !== undefined)) {
    headers.Authorization = gfnJwtAuthorization(options.token);
  }

  headers["nv-client-id"] = options.clientId ?? LCARS_CLIENT_ID;
  headers["nv-client-type"] = options.clientType;
  headers["nv-client-version"] = GFN_CLIENT_VERSION;
  headers["nv-client-streamer"] = options.clientStreamer;
  applyDeviceIdentityHeaders(headers, {
    ...identity,
    deviceOs: options.deviceOs ?? identity.deviceOs,
  });

  if (options.includeUserAgent) {
    headers["User-Agent"] = GFN_USER_AGENT;
  }

  return headers;
}

export function buildGfnGraphQlHeaders(
  token?: string,
  options?: { identifyAsSteamDeck?: boolean },
): Record<string, string> {
  const identity = resolveGfnDeviceIdentity(options);
  const headers: Record<string, string> = {
    Accept: "application/json, text/plain, */*",
    "Content-Type": "application/json",
    Origin: GFN_PLAY_ORIGIN,
    Referer: GFN_PLAY_REFERER,
    ...(token ? { Authorization: gfnJwtAuthorization(token) } : {}),
    "nv-client-id": LCARS_CLIENT_ID,
    "nv-client-type": "NATIVE",
    "nv-client-version": GFN_CLIENT_VERSION,
    "nv-client-streamer": "NVIDIA-CLASSIC",
    "nv-browser-type": "CHROME",
    "User-Agent": GFN_USER_AGENT,
  };
  applyDeviceIdentityHeaders(headers, identity);
  return headers;
}

export interface GfnCloudMatchHeadersOptions {
  token: string;
  clientId?: string;
  deviceId?: string;
  includeOrigin?: boolean;
  identifyAsSteamDeck?: boolean;
}

function resolveCloudMatchIdentity(options: GfnCloudMatchHeadersOptions): { clientId: string; deviceId: string } {
  return {
    clientId: options.clientId ?? crypto.randomUUID(),
    deviceId: options.deviceId ?? crypto.randomUUID(),
  };
}

export function buildGfnCloudMatchHeaders(options: GfnCloudMatchHeadersOptions): Record<string, string> {
  const { clientId, deviceId } = resolveCloudMatchIdentity(options);
  const identity = resolveGfnDeviceIdentity({ identifyAsSteamDeck: options.identifyAsSteamDeck });
  const headers: Record<string, string> = {
    "User-Agent": GFN_USER_AGENT,
    Authorization: gfnJwtAuthorization(options.token),
    "Content-Type": "application/json",
    "nv-browser-type": "CHROME",
    "nv-client-id": clientId,
    "nv-client-streamer": "NVIDIA-CLASSIC",
    "nv-client-type": "NATIVE",
    "nv-client-version": GFN_CLIENT_VERSION,
    "x-device-id": deviceId,
  };
  applyDeviceIdentityHeaders(headers, identity);

  if (options.includeOrigin !== false) {
    headers.Origin = GFN_PLAY_ORIGIN;
    headers.Referer = GFN_PLAY_REFERER;
  }

  return headers;
}

export function buildGfnCloudMatchClaimHeaders(options: GfnCloudMatchHeadersOptions): Record<string, string> {
  const { clientId, deviceId } = resolveCloudMatchIdentity(options);
  const identity = resolveGfnDeviceIdentity({ identifyAsSteamDeck: options.identifyAsSteamDeck });
  const headers: Record<string, string> = {
    "User-Agent": GFN_USER_AGENT,
    Authorization: gfnJwtAuthorization(options.token),
    "Content-Type": "application/json",
    Origin: GFN_PLAY_ORIGIN,
    Referer: GFN_PLAY_REFERER,
    "nv-client-id": clientId,
    "nv-client-streamer": "NVIDIA-CLASSIC",
    "nv-client-type": "NATIVE",
    "nv-client-version": GFN_CLIENT_VERSION,
    "x-device-id": deviceId,
  };
  applyDeviceIdentityHeaders(headers, identity);
  return headers;
}
