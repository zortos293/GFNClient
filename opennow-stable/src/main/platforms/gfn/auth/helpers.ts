import { createHash } from "node:crypto";
import os from "node:os";

import {
  buildNvidiaAuthHeaders,
  GFN_PLAY_ORIGIN,
  GFN_PLAY_REFERER,
} from "../clientHeaders";
import { CLIENT_ID, STEAM_DECK_CLIENT_ID, STEAM_DECK_USER_AGENT } from "./constants";

export function toExpiresAt(expiresInSeconds: number | undefined, defaultSeconds = 86400): number {
  return Date.now() + (expiresInSeconds ?? defaultSeconds) * 1000;
}

export function isExpired(expiresAt: number | undefined): boolean {
  if (!expiresAt) {
    return true;
  }
  return expiresAt <= Date.now();
}

export function isNearExpiry(expiresAt: number | undefined, windowMs: number): boolean {
  if (!expiresAt) {
    return true;
  }
  return expiresAt - Date.now() < windowMs;
}

export function generateDeviceId(): string {
  const host = os.hostname();
  const username = os.userInfo().username;
  return createHash("sha256").update(`${host}:${username}:opennow-stable`).digest("hex");
}

export function buildAuthHeadersForClient(
  authClientId = CLIENT_ID,
  options: {
    bearerToken?: string;
    accept?: string;
    contentType?: string;
    includeReferer?: boolean;
  } = {},
): Record<string, string> {
  if (authClientId !== STEAM_DECK_CLIENT_ID) {
    return buildNvidiaAuthHeaders(options);
  }

  const headers: Record<string, string> = {
    Accept: options.accept ?? "application/json, text/plain, */*",
    Origin: GFN_PLAY_ORIGIN,
    Referer: GFN_PLAY_REFERER,
    "User-Agent": STEAM_DECK_USER_AGENT,
  };

  if (options.bearerToken !== undefined) {
    headers.Authorization = `Bearer ${options.bearerToken}`;
  }
  if (options.contentType) {
    headers["Content-Type"] = options.contentType;
  }

  return headers;
}
