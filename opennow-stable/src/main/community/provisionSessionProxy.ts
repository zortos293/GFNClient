import { app } from "electron";

import {
  buildZortosCommunityProxyUrl,
  type CommunityProxyProvisionResult,
  ZORTOS_COMMUNITY_PROXY_PROVISION_URL,
} from "@shared/communityProxy";
import { getStableDeviceId } from "../gfn/deviceId";
import { normalizeSessionProxyUrl } from "../gfn/proxyUrl";
import { fetchWithTimeout } from "../services/requestTimeout";

const PROVISION_TIMEOUT_MS = 15_000;

interface ProvisionResponseBody {
  proxyUrl?: unknown;
  username?: unknown;
  password?: unknown;
  message?: unknown;
}

export async function provisionZortosCommunityProxy(): Promise<CommunityProxyProvisionResult> {
  const clientId = getStableDeviceId();
  const response = await fetchWithTimeout(
    ZORTOS_COMMUNITY_PROXY_PROVISION_URL,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "User-Agent": `OpenNOW-DesktopClient/${app.getVersion()}`,
      },
      body: JSON.stringify({ clientId }),
    },
    PROVISION_TIMEOUT_MS,
    "Zortos community proxy provision",
  );

  if (!response.ok) {
    let detail = "";
    try {
      const errorBody = await response.json() as ProvisionResponseBody;
      if (typeof errorBody.message === "string" && errorBody.message.trim()) {
        detail = errorBody.message.trim();
      }
    } catch {
      // Ignore malformed error bodies.
    }
    throw new Error(detail || `Community proxy provision failed (${response.status})`);
  }

  const payload = await response.json() as ProvisionResponseBody;
  let proxyUrl: string | null = null;

  if (typeof payload.proxyUrl === "string" && payload.proxyUrl.trim()) {
    proxyUrl = payload.proxyUrl.trim();
  } else if (
    typeof payload.username === "string"
    && typeof payload.password === "string"
  ) {
    proxyUrl = buildZortosCommunityProxyUrl(payload.username, payload.password);
  }

  const normalizedProxyUrl = normalizeSessionProxyUrl(proxyUrl ?? undefined);
  if (!normalizedProxyUrl) {
    throw new Error("Community proxy provision returned an invalid proxy URL.");
  }

  return { proxyUrl: normalizedProxyUrl };
}
