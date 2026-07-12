import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { randomBytes } from "node:crypto";

import { shell } from "electron";

import type {
  AuthLoginRequest,
  AuthDeviceLoginAttemptRequest,
  AuthDeviceLoginChallenge,
  AuthDeviceLoginPollRequest,
  AuthDeviceLoginPollResult,
  AuthDeviceLoginStartRequest,
  AuthSession,
  AuthSessionResult,
  AuthTokens,
  AuthUser,
  LoginProvider,
  SavedAccount,
  StreamRegion,
  SubscriptionInfo,
} from "@shared/gfn";
import { buildGfnLcarsHeaders, GFN_USER_AGENT } from "./clientHeaders";
import { fetchSubscription, fetchDynamicRegions } from "./subscription";
import {
  CLIENT_TOKEN_REFRESH_WINDOW_MS,
  DEFAULT_IDP_ID,
  SERVICE_URLS_ENDPOINT,
  TOKEN_REFRESH_WINDOW_MS,
} from "./auth/constants";
import { isExpired, isNearExpiry } from "./auth/helpers";
import {
  buildAuthUrl,
  exchangeAuthorizationCode,
  findAvailablePort,
  generatePkce,
  waitForAuthorizationCode,
} from "./auth/oauthFlow";
import { exchangeDeviceCode, requestDeviceAuthorization } from "./auth/deviceLogin";
import {
  mergeTokenSnapshot,
  refreshAuthTokens,
  refreshWithClientToken,
  requestClientToken,
} from "./auth/tokenRefresh";
import { fetchUserInfo } from "./auth/userInfo";

interface PersistedAuthState {
  sessions: AuthSession[];
  activeUserId: string | null;
  selectedProvider: LoginProvider | null;
}

interface ServiceUrlsResponse {
  requestStatus?: {
    statusCode?: number;
  };
  gfnServiceInfo?: {
    gfnServiceEndpoints?: Array<{
      idpId: string;
      loginProviderCode: string;
      loginProviderDisplayName: string;
      streamingServiceUrl: string;
      loginProviderPriority?: number;
    }>;
  };
}

interface ServerInfoResponse {
  requestStatus?: {
    serverId?: string;
  };
  metaData?: Array<{
    key: string;
    value: string;
  }>;
}

interface DeviceLoginAttempt {
  provider: LoginProvider;
  deviceCode: string;
  expiresAt: number;
}

function defaultProvider(): LoginProvider {
  return {
    idpId: DEFAULT_IDP_ID,
    code: "NVIDIA",
    displayName: "NVIDIA",
    streamingServiceUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/",
    priority: 0,
  };
}

function normalizeProvider(provider: LoginProvider): LoginProvider {
  return {
    ...provider,
    streamingServiceUrl: provider.streamingServiceUrl.endsWith("/")
      ? provider.streamingServiceUrl
      : `${provider.streamingServiceUrl}/`,
  };
}

export class AuthService {
  private providers: LoginProvider[] = [];
  private sessions = new Map<string, AuthSession>();
  private activeUserId: string | null = null;
  private selectedProvider: LoginProvider = defaultProvider();
  private cachedSubscription: SubscriptionInfo | null = null;
  private cachedVpcId: string | null = null;
  private deviceLoginAttempts = new Map<string, DeviceLoginAttempt>();
  private pendingDeviceLoginSessions = new Map<string, AuthSession>();

  constructor(private readonly statePath: string) {}

  async initialize(): Promise<void> {
    try {
      await access(this.statePath);
    } catch {
      await mkdir(dirname(this.statePath), { recursive: true });
      await this.persist();
      return;
    }

    try {
      const raw = await readFile(this.statePath, "utf8");
      const parsed = JSON.parse(raw) as Partial<PersistedAuthState> & {
        session?: AuthSession | null;
      };
      if (parsed.selectedProvider) {
        this.selectedProvider = normalizeProvider(parsed.selectedProvider);
      }

      this.sessions.clear();
      if (Array.isArray(parsed.sessions)) {
        for (const persistedSession of parsed.sessions) {
          if (!persistedSession?.user?.userId) {
            continue;
          }
          this.sessions.set(persistedSession.user.userId, {
            ...persistedSession,
            provider: normalizeProvider(persistedSession.provider),
          });
        }
      } else if (parsed.session?.user?.userId) {
        this.sessions.set(parsed.session.user.userId, {
          ...parsed.session,
          provider: normalizeProvider(parsed.session.provider),
        });
      }

      if (typeof parsed.activeUserId === "string" && this.sessions.has(parsed.activeUserId)) {
        this.activeUserId = parsed.activeUserId;
      } else {
        this.activeUserId = this.sessions.keys().next().value ?? null;
      }

      const restoredSession = this.getSession();
      if (restoredSession) {
        this.selectedProvider = restoredSession.provider;
        await this.enrichUserTier();
        await this.persist();
      }
    } catch {
      this.sessions.clear();
      this.activeUserId = null;
      this.selectedProvider = defaultProvider();
      await this.persist();
    }
  }

  private async persist(): Promise<void> {
    const payload: PersistedAuthState = {
      sessions: Array.from(this.sessions.values()),
      activeUserId: this.activeUserId,
      selectedProvider: this.selectedProvider,
    };

    await mkdir(dirname(this.statePath), { recursive: true });
    await writeFile(this.statePath, JSON.stringify(payload, null, 2), "utf8");
  }

  private async ensureClientToken(tokens: AuthTokens, userId: string): Promise<AuthTokens> {
    const hasUsableClientToken =
      Boolean(tokens.clientToken) &&
      !isNearExpiry(tokens.clientTokenExpiresAt, CLIENT_TOKEN_REFRESH_WINDOW_MS);
    if (hasUsableClientToken) {
      return tokens;
    }

    if (isExpired(tokens.expiresAt)) {
      return tokens;
    }

    const clientToken = await requestClientToken(tokens.accessToken, tokens.authClientId);
    return {
      ...tokens,
      clientToken: clientToken.token,
      clientTokenExpiresAt: clientToken.expiresAt,
      clientTokenLifetimeMs: clientToken.lifetimeMs,
    };
  }

  async getProviders(): Promise<LoginProvider[]> {
    if (this.providers.length > 0) {
      return this.providers;
    }

    let response: Response;
    try {
      response = await fetch(SERVICE_URLS_ENDPOINT, {
        headers: {
          Accept: "application/json",
          "User-Agent": GFN_USER_AGENT,
        },
      });
    } catch (error) {
      console.warn("Failed to fetch providers, using default:", error);
      this.providers = [defaultProvider()];
      return this.providers;
    }

    if (!response.ok) {
      console.warn(`Providers fetch failed with status ${response.status}, using default`);
      this.providers = [defaultProvider()];
      return this.providers;
    }

    try {
      const payload = (await response.json()) as ServiceUrlsResponse;
      const endpoints = payload.gfnServiceInfo?.gfnServiceEndpoints ?? [];

      const providers = endpoints
        .map<LoginProvider>((entry) => ({
          idpId: entry.idpId,
          code: entry.loginProviderCode,
          displayName:
            entry.loginProviderCode === "BPC" ? "bro.game" : entry.loginProviderDisplayName,
          streamingServiceUrl: entry.streamingServiceUrl,
          priority: entry.loginProviderPriority ?? 0,
        }))
        .sort((a, b) => a.priority - b.priority)
        .map(normalizeProvider);

      this.providers = providers.length > 0 ? providers : [defaultProvider()];
      console.log(`Loaded ${this.providers.length} providers`);
      return this.providers;
    } catch (error) {
      console.warn("Failed to parse providers response, using default:", error);
      this.providers = [defaultProvider()];
      return this.providers;
    }
  }

  setSession(session: AuthSession | null): void {
    if (!session) {
      this.sessions.clear();
      this.activeUserId = null;
      this.selectedProvider = defaultProvider();
      this.clearSubscriptionCache();
      this.clearVpcCache();
      void this.persist();
      return;
    }

    const normalized: AuthSession = {
      ...session,
      provider: normalizeProvider(session.provider),
    };
    this.sessions.set(normalized.user.userId, normalized);
    this.activeUserId = normalized.user.userId;
    this.selectedProvider = normalized.provider;
    this.clearSubscriptionCache();
    this.clearVpcCache();
    void this.persist();
  }

  getSession(): AuthSession | null {
    if (!this.activeUserId) {
      return null;
    }
    return this.sessions.get(this.activeUserId) ?? null;
  }

  private setActiveAccount(userId: string | null): void {
    this.activeUserId = userId && this.sessions.has(userId) ? userId : null;
    this.selectedProvider = this.getSession()?.provider ?? defaultProvider();
    this.clearSubscriptionCache();
    this.clearVpcCache();
  }

  getSavedAccounts(): SavedAccount[] {
    return Array.from(this.sessions.values()).map((session) => ({
      userId: session.user.userId,
      displayName: session.user.displayName,
      email: session.user.email,
      avatarUrl: session.user.avatarUrl,
      membershipTier: session.user.membershipTier,
      providerCode: session.provider.code,
    }));
  }

  async switchAccount(userId: string): Promise<AuthSession> {
    const target = this.sessions.get(userId);
    if (!target) {
      throw new Error("Saved account not found");
    }

    const previousActiveUserId = this.activeUserId;
    const previousSelectedProvider = this.selectedProvider;

    this.activeUserId = userId;
    this.selectedProvider = target.provider;
    this.clearSubscriptionCache();
    this.clearVpcCache();

    const result = await this.ensureValidSessionWithStatus(true, userId);
    const missingRefreshToken = result.refresh.outcome === "missing_refresh_token";
    const refreshFailed = result.refresh.outcome === "failed";
    const switchedUserMismatch = result.session?.user.userId !== userId;
    if (!result.session || refreshFailed || missingRefreshToken || switchedUserMismatch) {
      const fallbackMessage = "Failed to switch account due to an invalid or expired session.";

      if (missingRefreshToken) {
        await this.removeAccount(userId);
        this.setActiveAccount(previousActiveUserId);
        await this.persist();
        throw new Error("Saved login for this account is incomplete. Please log in to this account again.");
      }

      this.activeUserId = previousActiveUserId;
      this.selectedProvider = previousActiveUserId && this.sessions.has(previousActiveUserId)
        ? previousSelectedProvider
        : this.getSession()?.provider ?? defaultProvider();
      this.clearSubscriptionCache();
      this.clearVpcCache();
      await this.persist();

      if (switchedUserMismatch) {
        throw new Error("Switched session did not match the selected account.");
      }
      throw new Error(result.refresh.message || fallbackMessage);
    }
    return result.session;
  }

  async removeAccount(userId: string): Promise<void> {
    const removed = this.sessions.delete(userId);
    if (!removed) {
      return;
    }
    if (this.activeUserId === userId) {
      this.setActiveAccount(this.sessions.keys().next().value ?? null);
    } else {
      this.clearSubscriptionCache();
      this.clearVpcCache();
    }
    await this.persist();
  }

  async logoutAll(): Promise<void> {
    this.sessions.clear();
    this.activeUserId = null;
    this.selectedProvider = defaultProvider();
    this.cachedSubscription = null;
    this.clearVpcCache();
    await this.persist();
  }

  getSelectedProvider(): LoginProvider {
    return this.getSession()?.provider ?? this.selectedProvider;
  }

  private async selectLoginProvider(providerIdpId?: string): Promise<LoginProvider> {
    const providers = await this.getProviders();
    const selected =
      providers.find((provider) => provider.idpId === providerIdpId) ??
      this.selectedProvider ??
      providers[0] ??
      defaultProvider();
    this.selectedProvider = normalizeProvider(selected);
    return this.selectedProvider;
  }

  private async buildLoginSession(initialTokens: AuthTokens, provider: LoginProvider): Promise<AuthSession> {
    const user = await fetchUserInfo(initialTokens);
    console.debug("auth: fetched user info during login", { userId: user.userId, email: user.email, avatarUrl: user.avatarUrl });
    let tokens = initialTokens;
    try {
      tokens = await this.ensureClientToken(initialTokens, user.userId);
    } catch (error) {
      console.warn("Unable to fetch client token after login. Falling back to OAuth token only:", error);
    }

    return {
      provider: normalizeProvider(provider),
      tokens,
      user,
    };
  }

  private async saveLoginSession(session: AuthSession): Promise<AuthSession> {
    this.sessions.set(session.user.userId, session);
    this.activeUserId = session.user.userId;
    this.selectedProvider = session.provider;
    this.clearSubscriptionCache();
    this.clearVpcCache();

    // Fetch real membership tier from MES subscription API
    // (JWT does not contain gfn_tier, so fetchUserInfo always falls back to "FREE")
    await this.enrichUserTier();

    await this.persist();
    return this.getSession() as AuthSession;
  }

  private pruneExpiredDeviceLogins(now = Date.now(), skipAttemptId?: string): void {
    for (const [attemptId, attempt] of this.deviceLoginAttempts) {
      if (attemptId === skipAttemptId) {
        continue;
      }
      if (attempt.expiresAt <= now) {
        this.deviceLoginAttempts.delete(attemptId);
        this.pendingDeviceLoginSessions.delete(attemptId);
      }
    }
  }

  async getRegions(explicitToken?: string): Promise<StreamRegion[]> {
    const provider = this.getSelectedProvider();
    const base = provider.streamingServiceUrl.endsWith("/")
      ? provider.streamingServiceUrl
      : `${provider.streamingServiceUrl}/`;

    let token = explicitToken;
    if (!token) {
      const session = await this.ensureValidSession();
      token = session ? session.tokens.idToken ?? session.tokens.accessToken : undefined;
    }

    const headers = buildGfnLcarsHeaders({
      token,
      clientType: "BROWSER",
      clientStreamer: "WEBRTC",
      includeUserAgent: true,
    });

    let response: Response;
    try {
      response = await fetch(`${base}v2/serverInfo`, {
        headers,
      });
    } catch {
      return [];
    }

    if (!response.ok) {
      return [];
    }

    const payload = (await response.json()) as ServerInfoResponse;
    const regions = (payload.metaData ?? [])
      .filter((entry) => entry.value.startsWith("https://"))
      .filter((entry) => entry.key !== "gfn-regions" && !entry.key.startsWith("gfn-"))
      .map<StreamRegion>((entry) => ({
        name: entry.key,
        url: entry.value.endsWith("/") ? entry.value : `${entry.value}/`,
      }))
      .sort((a, b) => a.name.localeCompare(b.name));

    return regions;
  }

  async login(input: AuthLoginRequest): Promise<AuthSession> {
    const provider = await this.selectLoginProvider(input.providerIdpId);

    const { verifier, challenge } = generatePkce();
    const port = await findAvailablePort();
    const authUrl = buildAuthUrl(provider, challenge, port);

    const codePromise = waitForAuthorizationCode(port, 120000);
    await shell.openExternal(authUrl);
    const code = await codePromise;

    const initialTokens = await exchangeAuthorizationCode(code, verifier, port);
    const session = await this.buildLoginSession(initialTokens, provider);
    return this.saveLoginSession(session);
  }

  async startDeviceLogin(input: AuthDeviceLoginStartRequest): Promise<AuthDeviceLoginChallenge> {
    this.pruneExpiredDeviceLogins();
    const provider = await this.selectLoginProvider(input.providerIdpId);
    const challenge = await requestDeviceAuthorization(provider);
    const attemptId = randomBytes(16).toString("hex");
    this.deviceLoginAttempts.set(attemptId, {
      provider,
      deviceCode: challenge.deviceCode,
      expiresAt: challenge.expiresAt,
    });
    return { ...challenge, attemptId };
  }

  async pollDeviceLogin(input: AuthDeviceLoginPollRequest): Promise<AuthDeviceLoginPollResult> {
    this.pruneExpiredDeviceLogins();
    if (!input.attemptId || !input.deviceCode) {
      return { status: "error", error: "Missing device code" };
    }

    const attempt = this.deviceLoginAttempts.get(input.attemptId);
    if (!attempt || attempt.deviceCode !== input.deviceCode) {
      return { status: "expired", error: "QR login was cancelled or expired" };
    }
    if (Date.now() >= attempt.expiresAt) {
      this.cancelDeviceLogin(input);
      return { status: "expired", error: "QR login expired" };
    }

    const result = await exchangeDeviceCode(input.deviceCode);
    if (!this.deviceLoginAttempts.has(input.attemptId)) {
      return { status: "expired", error: "QR login was cancelled" };
    }

    if ("accessToken" in result) {
      const session = await this.buildLoginSession(result, attempt.provider);
      if (!this.deviceLoginAttempts.has(input.attemptId)) {
        return { status: "expired", error: "QR login was cancelled" };
      }
      this.pendingDeviceLoginSessions.set(input.attemptId, session);
      return { status: "authorized" };
    }

    switch (result.error) {
      case "authorization_pending":
        return { status: "pending", error: result.error_description };
      case "slow_down":
        return { status: "slow_down", error: result.error_description };
      case "expired_token":
        this.cancelDeviceLogin(input);
        return { status: "expired", error: result.error_description ?? "QR login expired" };
      case "access_denied":
        this.cancelDeviceLogin(input);
        return { status: "access_denied", error: result.error_description ?? "QR login was denied" };
      default:
        this.cancelDeviceLogin(input);
        return { status: "error", error: result.error_description ?? result.error ?? "QR login failed" };
    }
  }

  async completeDeviceLogin(input: AuthDeviceLoginAttemptRequest): Promise<AuthSession> {
    this.pruneExpiredDeviceLogins(Date.now(), input.attemptId);
    const session = this.pendingDeviceLoginSessions.get(input.attemptId);
    if (!session || !this.deviceLoginAttempts.has(input.attemptId)) {
      throw new Error("QR login is no longer active");
    }

    this.cancelDeviceLogin(input);
    return this.saveLoginSession(session);
  }

  cancelDeviceLogin(input: AuthDeviceLoginAttemptRequest): void {
    this.deviceLoginAttempts.delete(input.attemptId);
    this.pendingDeviceLoginSessions.delete(input.attemptId);
  }

  async logout(): Promise<void> {
    if (!this.activeUserId) {
      return;
    }
    this.sessions.delete(this.activeUserId);
    this.activeUserId = this.sessions.keys().next().value ?? null;
    this.selectedProvider = this.getSession()?.provider ?? defaultProvider();
    this.cachedSubscription = null;
    this.clearVpcCache();
    await this.persist();
  }

  /**
   * Fetch subscription info for the current user.
   * Uses caching - call clearSubscriptionCache() to force refresh.
   */
  async getSubscription(): Promise<SubscriptionInfo | null> {
    // Return cached subscription if available
    if (this.cachedSubscription) {
      return this.cachedSubscription;
    }

    const session = await this.ensureValidSession();
    if (!session) {
      return null;
    }

    const token = session.tokens.idToken ?? session.tokens.accessToken;
    const userId = session.user.userId;

    // Fetch dynamic regions to get the VPC ID (handles Alliance partners correctly)
    const { vpcId } = await fetchDynamicRegions(token, session.provider.streamingServiceUrl);

    const subscription = await fetchSubscription(token, userId, vpcId ?? undefined);
    this.cachedSubscription = subscription;
    return subscription;
  }

  /**
   * Clear the cached subscription info.
   * Called automatically on logout.
   */
  clearSubscriptionCache(): void {
    this.cachedSubscription = null;
  }

  /**
   * Get the cached subscription without fetching.
   * Returns null if not cached.
   */
  getCachedSubscription(): SubscriptionInfo | null {
    return this.cachedSubscription;
  }

  /**
   * Get the VPC ID for the current provider.
   * Returns cached value if available, otherwise fetches from serverInfo endpoint.
   * The VPC ID is used for Alliance partner support and routing to correct data center.
   */
  async getVpcId(explicitToken?: string): Promise<string | null> {
    // Return cached VPC ID if available
    if (this.cachedVpcId) {
      return this.cachedVpcId;
    }

    const provider = this.getSelectedProvider();
    const base = provider.streamingServiceUrl.endsWith("/")
      ? provider.streamingServiceUrl
      : `${provider.streamingServiceUrl}/`;

    let token = explicitToken;
    if (!token) {
      const session = await this.ensureValidSession();
      token = session ? session.tokens.idToken ?? session.tokens.accessToken : undefined;
    }

    const headers = buildGfnLcarsHeaders({
      token,
      clientType: "BROWSER",
      clientStreamer: "WEBRTC",
      includeUserAgent: true,
    });

    try {
      const response = await fetch(`${base}v2/serverInfo`, {
        headers,
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json()) as ServerInfoResponse;
      const vpcId = payload.requestStatus?.serverId ?? null;

      // Cache the VPC ID
      if (vpcId) {
        this.cachedVpcId = vpcId;
      }

      return vpcId;
    } catch {
      return null;
    }
  }

  /**
   * Clear the cached VPC ID.
   * Called automatically on logout.
   */
  clearVpcCache(): void {
    this.cachedVpcId = null;
  }

  /**
   * Get the cached VPC ID without fetching.
   * Returns null if not cached.
   */
  getCachedVpcId(): string | null {
    return this.cachedVpcId;
  }

  /**
   * Enrich the current session's user with the real membership tier from MES API.
   * Falls back silently to the existing tier if the fetch fails.
   */
  private async enrichUserTier(): Promise<void> {
    const session = this.getSession();
    if (!session) return;

    try {
      const subscription = await this.getSubscription();
      if (subscription && subscription.membershipTier) {
        this.sessions.set(session.user.userId, {
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

  private shouldRefresh(tokens: AuthTokens): boolean {
    return isNearExpiry(tokens.expiresAt, TOKEN_REFRESH_WINDOW_MS);
  }

  async ensureValidSessionWithStatus(
    forceRefresh = false,
    expectedUserId?: string,
  ): Promise<AuthSessionResult> {
    const currentSession = this.getSession();
    if (!currentSession) {
      return {
        session: null,
        refresh: {
          attempted: false,
          forced: forceRefresh,
          outcome: "not_attempted",
          message: "No saved session found.",
        },
      };
    }

    const userId = currentSession.user.userId;
    let tokens = currentSession.tokens;

    // Official GFN client flow relies on client_token-based refresh. Bootstrap it
    // for older sessions that were saved before we persisted client tokens.
    if (!tokens.clientToken && !isExpired(tokens.expiresAt)) {
      try {
        const withClientToken = await this.ensureClientToken(tokens, userId);
        if (withClientToken.clientToken && withClientToken.clientToken !== tokens.clientToken) {
          this.sessions.set(userId, {
            ...currentSession,
            tokens: withClientToken,
          });
          tokens = withClientToken;
          await this.persist();
        }
      } catch (error) {
        console.warn("Unable to bootstrap client token from saved session:", error);
      }
    }

    const shouldRefreshNow = forceRefresh || this.shouldRefresh(tokens);
    if (!shouldRefreshNow) {
      return {
        session: this.getSession(),
        refresh: {
          attempted: false,
          forced: forceRefresh,
          outcome: "not_attempted",
          message: "Session token is still valid.",
        },
      };
    }

    const applyRefreshedTokens = async (
      refreshedTokens: AuthTokens,
      source: "client_token" | "refresh_token",
    ): Promise<AuthSessionResult> => {
      const latestSession = this.getSession() ?? currentSession;
      const baseSession = latestSession.user.userId === userId ? latestSession : currentSession;
      const expectedRefreshUserId = expectedUserId ?? userId;
      let refreshedUser: AuthUser | null = null;
      let userInfoError: string | undefined;
      try {
        refreshedUser = await fetchUserInfo(refreshedTokens);
        console.debug("auth: fetched user info on token refresh", {
          userId: refreshedUser.userId,
          email: refreshedUser.email,
          avatarUrl: refreshedUser.avatarUrl,
        });
      } catch (error) {
        console.warn("Token refresh succeeded but user info refresh failed. Keeping cached user:", error);
        userInfoError = error instanceof Error ? error.message : "Unknown error while fetching user info";
      }

      const resolvedUser = refreshedUser ?? baseSession.user;
      if (resolvedUser.userId !== expectedRefreshUserId) {
        return {
          session: baseSession,
          refresh: {
            attempted: true,
            forced: forceRefresh,
            outcome: "failed",
            message: refreshedUser
              ? "Token refresh returned a different account than expected."
              : "Token refresh kept a cached account identity that did not match the expected account.",
            error: refreshedUser
              ? `expected_user_id:${expectedRefreshUserId} actual_user_id:${refreshedUser.userId}`
              : userInfoError
                ? `expected_user_id:${expectedRefreshUserId} cached_user_id:${resolvedUser.userId} user_info_error:${userInfoError}`
                : `expected_user_id:${expectedRefreshUserId} cached_user_id:${resolvedUser.userId}`,
          },
        };
      }

      const updatedSession: AuthSession = {
        provider: baseSession.provider,
        tokens: refreshedTokens,
        user: resolvedUser,
      };
      this.sessions.set(updatedSession.user.userId, updatedSession);

      // Re-fetch real tier after token refresh
      this.clearSubscriptionCache();
      await this.enrichUserTier();
      await this.persist();

      const sourceText = source === "client_token" ? "client token" : "refresh token";
      return {
        session: this.getSession(),
        refresh: {
          attempted: true,
          forced: forceRefresh,
          outcome: "refreshed",
          message: forceRefresh
            ? `Saved session token refreshed via ${sourceText}.`
            : `Session token refreshed via ${sourceText} because it was near expiry.`,
        },
      };
    };

    const refreshErrors: string[] = [];

    if (tokens.clientToken) {
      try {
        const refreshedFromClientToken = await refreshWithClientToken(tokens.clientToken, userId, tokens.authClientId);
        let refreshedTokens = mergeTokenSnapshot(tokens, refreshedFromClientToken);
        refreshedTokens = await this.ensureClientToken(refreshedTokens, userId);
        return applyRefreshedTokens(refreshedTokens, "client_token");
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "Unknown error while refreshing with client token";
        refreshErrors.push(`client_token: ${message}`);
      }
    }

    if (tokens.refreshToken) {
      try {
        const refreshedOAuth = await refreshAuthTokens(tokens.refreshToken, tokens.authClientId);
        let refreshedTokens: AuthTokens = {
          ...tokens,
          ...refreshedOAuth,
          // OAuth refresh does not always return a new client token.
          clientToken: tokens.clientToken,
          clientTokenExpiresAt: tokens.clientTokenExpiresAt,
          clientTokenLifetimeMs: tokens.clientTokenLifetimeMs,
          authClientId: refreshedOAuth.authClientId ?? tokens.authClientId,
        };
        refreshedTokens = await this.ensureClientToken(refreshedTokens, userId);
        return applyRefreshedTokens(refreshedTokens, "refresh_token");
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "Unknown error while refreshing token";
        refreshErrors.push(`refresh_token: ${message}`);
      }
    }

    const errorText = refreshErrors.length > 0 ? refreshErrors.join(" | ") : undefined;
    const expired = isExpired(tokens.expiresAt);

    if (!tokens.clientToken && !tokens.refreshToken) {
      if (expired) {
        await this.logout();
        return {
          session: null,
          refresh: {
            attempted: true,
            forced: forceRefresh,
            outcome: "missing_refresh_token",
            message: "Saved session expired and has no refresh mechanism. Please log in again.",
          },
        };
      }

      return {
        session: this.getSession(),
        refresh: {
          attempted: true,
          forced: forceRefresh,
          outcome: "missing_refresh_token",
          message: "No refresh token available. Using saved session token.",
        },
      };
    }

    if (expired) {
      await this.logout();
      return {
        session: null,
        refresh: {
          attempted: true,
          forced: forceRefresh,
          outcome: "failed",
          message: "Token refresh failed and the saved session expired. Please log in again.",
          error: errorText,
        },
      };
    }

    return {
      session: this.getSession(),
      refresh: {
        attempted: true,
        forced: forceRefresh,
        outcome: "failed",
        message: "Token refresh failed. Using saved session token.",
        error: errorText,
      },
    };
  }

  async ensureValidSession(): Promise<AuthSession | null> {
    const result = await this.ensureValidSessionWithStatus(false);
    return result.session;
  }

  async resolveJwtToken(explicitToken?: string): Promise<string> {
    // Prefer the managed auth session whenever it exists so renderer-side cached
    // tokens cannot bypass refresh logic.
    if (this.getSession()) {
      const session = await this.ensureValidSession();
      if (!session) {
        throw new Error("No authenticated session available");
      }
      return session.tokens.idToken ?? session.tokens.accessToken;
    }

    if (explicitToken && explicitToken.trim()) {
      return explicitToken.trim();
    }

    const session = await this.ensureValidSession();
    if (!session) {
      throw new Error("No authenticated session available");
    }

    return session.tokens.idToken ?? session.tokens.accessToken;
  }
}
