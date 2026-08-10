import { createServer } from "node:http";
import { createHash, randomBytes } from "node:crypto";
import net from "node:net";

import type { AuthTokens, LoginProvider } from "@shared/gfn";

import {
  AUTH_ENDPOINT,
  CLIENT_ID,
  REDIRECT_PORTS,
  SCOPES,
  TOKEN_ENDPOINT,
} from "./constants";
import { buildAuthHeadersForClient, generateDeviceId, toExpiresAt } from "./helpers";
import { isSecondaryInstance, orderPortsForAppInstance } from "../../../appInstance";

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  client_token?: string;
  expires_in?: number;
}

export function generatePkce(): { verifier: string; challenge: string } {
  const verifier = randomBytes(64)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "")
    .slice(0, 86);

  const challenge = createHash("sha256")
    .update(verifier)
    .digest("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");

  return { verifier, challenge };
}

export function buildAuthUrl(provider: LoginProvider, challenge: string, port: number): string {
  const redirectUri = `http://localhost:${port}`;
  const nonce = randomBytes(16).toString("hex");
  const params = new URLSearchParams({
    response_type: "code",
    device_id: generateDeviceId(),
    scope: SCOPES,
    client_id: CLIENT_ID,
    redirect_uri: redirectUri,
    ui_locales: "en_US",
    nonce,
    prompt: "select_account",
    code_challenge: challenge,
    code_challenge_method: "S256",
    idp_id: provider.idpId,
  });
  return `${AUTH_ENDPOINT}?${params.toString()}`;
}

async function isPortAvailable(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once("error", () => resolve(false));
    server.once("listening", () => {
      server.close(() => resolve(true));
    });
    server.listen(port, "127.0.0.1");
  });
}

export async function findAvailablePort(): Promise<number> {
  const candidatePorts = orderPortsForAppInstance(
    REDIRECT_PORTS,
    isSecondaryInstance(process.argv),
  );
  for (const port of candidatePorts) {
    if (await isPortAvailable(port)) {
      return port;
    }
  }

  throw new Error("No available OAuth callback ports");
}

export async function waitForAuthorizationCode(
  port: number,
  timeoutMs: number,
  signal?: AbortSignal,
): Promise<string> {
  return new Promise((resolve, reject) => {
    let timer: NodeJS.Timeout | null = null;
    let listenPending = true;
    let pendingResult: string | Error | undefined;
    let settlementStarted = false;
    const server = createServer((request, response) => {
      const url = new URL(request.url ?? "/", `http://localhost:${port}`);
      const code = url.searchParams.get("code");
      const error = url.searchParams.get("error");

      const html = `<!doctype html><html><head><meta charset="utf-8"><title>OpenNOW Login</title></head><body style="font-family:Segoe UI,Arial,sans-serif;background:#0b1220;color:#dbe7ff;display:flex;justify-content:center;align-items:center;height:100vh"><div style="background:#111a2c;padding:24px 28px;border:1px solid #30425f;border-radius:12px;max-width:460px"><h2 style="margin-top:0">OpenNOW Login</h2><p>${
        code
          ? "Login complete. You can close this window and return to OpenNOW Stable."
          : "Login failed or was cancelled. You can close this window and return to OpenNOW Stable."
      }</p></div></body></html>`;

      response.statusCode = 200;
      response.setHeader("Content-Type", "text/html; charset=utf-8");
      response.setHeader("Connection", "close");
      response.end(html, () => {
        finish(code ?? new Error(error ?? "Authorization failed"));
      });
    });

    const settle = (result: string | Error): void => {
      if (typeof result === "string") {
        resolve(result);
      } else {
        reject(result);
      }
    };

    const closeAndSettle = (): void => {
      if (settlementStarted || pendingResult === undefined) return;
      settlementStarted = true;
      const result = pendingResult;
      if (!server.listening) {
        settle(result);
        return;
      }

      try {
        server.close(() => settle(result));
        server.closeAllConnections();
      } catch {
        settle(result);
      }
    };

    const finish = (result: string | Error): void => {
      if (pendingResult !== undefined) return;
      pendingResult = result;
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      signal?.removeEventListener("abort", handleAbort);
      if (!listenPending) {
        closeAndSettle();
      }
    };

    const handleAbort = (): void => {
      finish(new Error("OAuth login was cancelled."));
    };

    server.once("error", (error) => {
      listenPending = false;
      finish(error);
      closeAndSettle();
    });
    server.listen(port, "127.0.0.1", () => {
      listenPending = false;
      if (pendingResult !== undefined) {
        closeAndSettle();
        return;
      }
      timer = setTimeout(() => {
        finish(new Error("Timed out waiting for OAuth callback"));
      }, timeoutMs);
    });

    if (signal?.aborted) {
      handleAbort();
    } else {
      signal?.addEventListener("abort", handleAbort, { once: true });
    }
  });
}

export async function openAuthorizationUrlAndWaitForCode(
  authUrl: string,
  port: number,
  timeoutMs: number,
  openExternal: (url: string) => Promise<void>,
): Promise<string> {
  const abortController = new AbortController();
  const resultPromise = waitForAuthorizationCode(
    port,
    timeoutMs,
    abortController.signal,
  ).then(
    (code) => ({ code }),
    (error: unknown) => ({ error }),
  );

  try {
    await openExternal(authUrl);
  } catch (error) {
    abortController.abort();
    await resultPromise;
    throw error;
  }

  const result = await resultPromise;
  if ("error" in result) {
    throw result.error;
  }
  return result.code;
}

export async function exchangeAuthorizationCode(
  code: string,
  verifier: string,
  port: number,
): Promise<AuthTokens> {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: `http://localhost:${port}`,
    code_verifier: verifier,
  });

  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: buildAuthHeadersForClient(CLIENT_ID, {
      contentType: "application/x-www-form-urlencoded; charset=UTF-8",
      includeReferer: true,
    }),
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Token exchange failed (${response.status}): ${text.slice(0, 400)}`);
  }

  const payload = (await response.json()) as TokenResponse;
  return {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token,
    idToken: payload.id_token,
    expiresAt: toExpiresAt(payload.expires_in),
    authClientId: CLIENT_ID,
  };
}
