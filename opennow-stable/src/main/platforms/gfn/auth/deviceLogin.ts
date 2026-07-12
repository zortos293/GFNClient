import type { AuthDeviceLoginChallenge, AuthTokens, LoginProvider } from "@shared/gfn";

import {
  DEVICE_AUTHORIZE_ENDPOINT,
  SCOPES,
  STEAM_DECK_CLIENT_ID,
  TOKEN_ENDPOINT,
} from "./constants";
import { buildAuthHeadersForClient, generateDeviceId, toExpiresAt } from "./helpers";

interface DeviceAuthorizationResponse {
  device_code?: string;
  user_code?: string;
  verification_uri?: string;
  verification_uri_complete?: string;
  expires_in?: number;
  interval?: number;
}

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  client_token?: string;
  expires_in?: number;
}

export interface DeviceTokenErrorResponse {
  error?: string;
  error_description?: string;
}

export async function requestDeviceAuthorization(
  provider: LoginProvider,
): Promise<Omit<AuthDeviceLoginChallenge, "attemptId">> {
  const deviceId = generateDeviceId();
  const body = new URLSearchParams({
    client_id: STEAM_DECK_CLIENT_ID,
    scope: SCOPES,
    device_id: deviceId,
    display_name: "OpenNOW",
    idp_id: provider.idpId,
  });

  const response = await fetch(DEVICE_AUTHORIZE_ENDPOINT, {
    method: "POST",
    headers: {
      ...buildAuthHeadersForClient(STEAM_DECK_CLIENT_ID, {
        contentType: "application/x-www-form-urlencoded; charset=UTF-8",
      }),
      "x-device-id": deviceId,
      "nv-client-id": STEAM_DECK_CLIENT_ID,
      "nv-client-streamer": "WEBRTC",
      "nv-client-type": "BROWSER",
      "nv-client-platform-name": "browser",
      "nv-browser-type": "CHROME",
      "nv-device-os": "STEAMOS",
      "nv-device-type": "CONSOLE",
      "nv-device-model": "STEAMDECK",
      "nv-device-make": "VALVE",
    },
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Device authorization failed (${response.status}): ${text.slice(0, 400)}`);
  }

  const payload = (await response.json()) as DeviceAuthorizationResponse;
  if (
    !payload.device_code ||
    !payload.user_code ||
    !payload.verification_uri ||
    !payload.verification_uri_complete
  ) {
    throw new Error("Device authorization response did not include QR login data");
  }

  return {
    deviceCode: payload.device_code,
    userCode: payload.user_code,
    verificationUri: payload.verification_uri,
    verificationUriComplete: payload.verification_uri_complete,
    expiresAt: toExpiresAt(payload.expires_in, 600),
    intervalSeconds: Math.max(1, payload.interval ?? 5),
  };
}

export async function exchangeDeviceCode(
  deviceCode: string,
): Promise<AuthTokens | DeviceTokenErrorResponse> {
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:device_code",
    device_code: deviceCode,
    client_id: STEAM_DECK_CLIENT_ID,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: buildAuthHeadersForClient(STEAM_DECK_CLIENT_ID, {
      contentType: "application/x-www-form-urlencoded; charset=UTF-8",
    }),
    body,
  });

  const payload = (await response.json().catch(() => null)) as
    | TokenResponse
    | DeviceTokenErrorResponse
    | null;
  if (!response.ok) {
    return payload && typeof payload === "object"
      ? (payload as DeviceTokenErrorResponse)
      : {
          error: "device_token_exchange_failed",
          error_description: `Device token exchange failed (${response.status})`,
        };
  }

  const tokenPayload = payload as TokenResponse | null;
  if (!tokenPayload?.access_token) {
    return {
      error: "invalid_token_response",
      error_description: "Device token response did not include access_token",
    };
  }

  return {
    accessToken: tokenPayload.access_token,
    refreshToken: tokenPayload.refresh_token,
    idToken: tokenPayload.id_token,
    expiresAt: toExpiresAt(tokenPayload.expires_in),
    authClientId: STEAM_DECK_CLIENT_ID,
    clientToken: tokenPayload.client_token,
  };
}
