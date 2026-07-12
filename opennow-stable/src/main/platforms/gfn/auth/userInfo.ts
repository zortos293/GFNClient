import { createHash } from "node:crypto";

import type { AuthTokens, AuthUser } from "@shared/gfn";

import { USERINFO_ENDPOINT } from "./constants";
import { buildAuthHeadersForClient } from "./helpers";

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4;
  const padded = padding === 0 ? normalized : `${normalized}${"=".repeat(4 - padding)}`;
  return Buffer.from(padded, "base64").toString("utf8");
}

function parseJwtPayload<T>(token: string): T | null {
  const parts = token.split(".");
  if (parts.length !== 3) {
    return null;
  }
  try {
    const payload = decodeBase64Url(parts[1]);
    return JSON.parse(payload) as T;
  } catch {
    return null;
  }
}

export function gravatarUrl(email: string, size = 80): string {
  const normalized = email.trim().toLowerCase();
  const hash = createHash("md5").update(normalized).digest("hex");
  return `https://www.gravatar.com/avatar/${hash}?s=${size}&d=identicon`;
}

export async function fetchUserInfo(tokens: AuthTokens): Promise<AuthUser> {
  const jwtToken = tokens.idToken ?? tokens.accessToken;
  const parsed = parseJwtPayload<{
    sub?: string;
    email?: string;
    preferred_username?: string;
    gfn_tier?: string;
    picture?: string;
  }>(jwtToken);

  if (parsed?.sub) {
    const emailFromToken = parsed.email;
    const pictureFromToken = parsed.picture;
    if (emailFromToken || pictureFromToken) {
      const avatar = pictureFromToken ?? (emailFromToken ? gravatarUrl(emailFromToken) : undefined);
      return {
        userId: parsed.sub,
        displayName: parsed.preferred_username ?? emailFromToken?.split("@")[0] ?? "User",
        email: emailFromToken,
        avatarUrl: avatar,
        membershipTier: parsed.gfn_tier ?? "FREE",
      };
    }
  }

  const response = await fetch(USERINFO_ENDPOINT, {
    headers: buildAuthHeadersForClient(tokens.authClientId, {
      bearerToken: tokens.accessToken,
      accept: "application/json",
    }),
  });

  if (!response.ok) {
    throw new Error(`User info failed (${response.status})`);
  }

  const payload = (await response.json()) as {
    sub: string;
    preferred_username?: string;
    email?: string;
    picture?: string;
  };

  const email = payload.email;
  const avatar = payload.picture ?? (email ? gravatarUrl(email) : undefined);

  return {
    userId: payload.sub,
    displayName: payload.preferred_username ?? email?.split("@")[0] ?? "User",
    email,
    avatarUrl: avatar,
    membershipTier: "FREE",
  };
}
