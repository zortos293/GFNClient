import type { AuthSession, LoginProvider, SubscriptionInfo } from "@shared/gfn";

import { buildGfnLcarsHeaders } from "../clientHeaders";
import { fetchDynamicRegions, fetchSubscription } from "../subscription";

interface ServerInfoResponse {
  requestStatus?: {
    serverId?: string;
  };
}

interface EnrichmentCacheDependencies {
  getSession: () => AuthSession | null;
  getSelectedProvider: () => LoginProvider;
  ensureValidSession: () => Promise<AuthSession | null>;
  updateSession: (session: AuthSession) => void;
}

export class SubscriptionVpcEnrichmentCaches {
  private cachedSubscription: SubscriptionInfo | null = null;
  private cachedVpcId: string | null = null;

  constructor(private readonly dependencies: EnrichmentCacheDependencies) {}

  async getSubscription(): Promise<SubscriptionInfo | null> {
    if (this.cachedSubscription) {
      return this.cachedSubscription;
    }

    const session = await this.dependencies.ensureValidSession();
    if (!session) {
      return null;
    }

    const token = session.tokens.idToken ?? session.tokens.accessToken;
    const { vpcId } = await fetchDynamicRegions(token, session.provider.streamingServiceUrl);
    const subscription = await fetchSubscription(
      token,
      session.user.userId,
      vpcId ?? undefined,
    );
    this.cachedSubscription = subscription;
    return subscription;
  }

  clearSubscription(): void {
    this.cachedSubscription = null;
  }

  getCachedSubscription(): SubscriptionInfo | null {
    return this.cachedSubscription;
  }

  async getVpcId(explicitToken?: string): Promise<string | null> {
    if (this.cachedVpcId) {
      return this.cachedVpcId;
    }

    const provider = this.dependencies.getSelectedProvider();
    const base = provider.streamingServiceUrl.endsWith("/")
      ? provider.streamingServiceUrl
      : `${provider.streamingServiceUrl}/`;

    let token = explicitToken;
    if (!token) {
      const session = await this.dependencies.ensureValidSession();
      token = session ? session.tokens.idToken ?? session.tokens.accessToken : undefined;
    }

    const headers = buildGfnLcarsHeaders({
      token,
      clientType: "BROWSER",
      clientStreamer: "WEBRTC",
      includeUserAgent: true,
    });

    try {
      const response = await fetch(`${base}v2/serverInfo`, { headers });
      if (!response.ok) {
        return null;
      }

      const payload = (await response.json()) as ServerInfoResponse;
      const vpcId = payload.requestStatus?.serverId ?? null;
      if (vpcId) {
        this.cachedVpcId = vpcId;
      }
      return vpcId;
    } catch {
      return null;
    }
  }

  clearVpc(): void {
    this.cachedVpcId = null;
  }

  getCachedVpcId(): string | null {
    return this.cachedVpcId;
  }

  clearAll(): void {
    this.clearSubscription();
    this.clearVpc();
  }

  async enrichUserTier(): Promise<void> {
    const session = this.dependencies.getSession();
    if (!session) {
      return;
    }

    try {
      const subscription = await this.getSubscription();
      if (subscription?.membershipTier) {
        this.dependencies.updateSession({
          ...session,
          user: {
            ...session.user,
            membershipTier: subscription.membershipTier,
          },
        });
        console.log(`Resolved membership tier: ${subscription.membershipTier}`);
      }
    } catch (error) {
      console.warn("Failed to fetch subscription tier, keeping fallback:", error);
    }
  }
}
