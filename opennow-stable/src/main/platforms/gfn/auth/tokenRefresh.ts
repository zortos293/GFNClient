import type { AuthTokens } from "@shared/gfn";

import { CLIENT_ID, CLIENT_TOKEN_ENDPOINT, TOKEN_ENDPOINT } from "./constants";
import { buildAuthHeadersForClient, toExpiresAt } from "./helpers";

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  client_token?: string;
  expires_in?: number;
}

interface ClientTokenResponse {
  client_token: string;
  expires_in?: number;
}

export async function refreshAuthTokens(
  refreshToken: string,
  authClientId = CLIENT_ID,
): Promise<AuthTokens> {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
    client_id: authClientId,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: buildAuthHeadersForClient(authClientId, {
      contentType: "application/x-www-form-urlencoded; charset=UTF-8",
    }),
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Token refresh failed (${response.status}): ${text.slice(0, 400)}`);
  }

  const payload = (await response.json()) as TokenResponse;
  return {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token ?? refreshToken,
    idToken: payload.id_token,
    expiresAt: toExpiresAt(payload.expires_in),
    authClientId,
  };
}

export async function requestClientToken(
  accessToken: string,
  authClientId = CLIENT_ID,
): Promise<{
  token: string;
  expiresAt: number;
  lifetimeMs: number;
}> {
  const response = await fetch(CLIENT_TOKEN_ENDPOINT, {
    headers: buildAuthHeadersForClient(authClientId, { bearerToken: accessToken }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Client token request failed (${response.status}): ${text.slice(0, 400)}`);
  }

  const payload = (await response.json()) as ClientTokenResponse;
  const expiresAt = toExpiresAt(payload.expires_in);
  return {
    token: payload.client_token,
    expiresAt,
    lifetimeMs: Math.max(0, expiresAt - Date.now()),
  };
}

export async function refreshWithClientToken(
  clientToken: string,
  userId: string,
  authClientId = CLIENT_ID,
): Promise<TokenResponse> {
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:client_token",
    client_token: clientToken,
    client_id: authClientId,
    sub: userId,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: buildAuthHeadersForClient(authClientId, {
      contentType: "application/x-www-form-urlencoded; charset=UTF-8",
    }),
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Client-token refresh failed (${response.status}): ${text.slice(0, 400)}`);
  }

  return (await response.json()) as TokenResponse;
}

export function mergeTokenSnapshot(base: AuthTokens, refreshed: TokenResponse): AuthTokens {
  return {
    accessToken: refreshed.access_token,
    refreshToken: refreshed.refresh_token ?? base.refreshToken,
    idToken: refreshed.id_token,
    expiresAt: toExpiresAt(refreshed.expires_in),
    authClientId: base.authClientId ?? CLIENT_ID,
    clientToken: refreshed.client_token ?? base.clientToken,
    clientTokenExpiresAt: base.clientTokenExpiresAt,
    clientTokenLifetimeMs: base.clientTokenLifetimeMs,
  };
}
