import type {
  AuthSession,
  AuthSessionResult,
  AuthTokens,
  AuthUser,
} from "@shared/gfn";

import { CLIENT_TOKEN_REFRESH_WINDOW_MS, TOKEN_REFRESH_WINDOW_MS } from "./constants";
import type { SubscriptionVpcEnrichmentCaches } from "./enrichmentCaches";
import { isExpired, isNearExpiry } from "./helpers";
import type { PersistedAccountState } from "./persistedAccountState";
import {
  mergeTokenSnapshot,
  refreshAuthTokens,
  refreshWithClientToken,
  requestClientToken,
} from "./tokenRefresh";
import { fetchUserInfo } from "./userInfo";

interface SessionValidityDependencies {
  state: PersistedAccountState;
  enrichmentCaches: SubscriptionVpcEnrichmentCaches;
  logout: () => Promise<void>;
}

export function shouldRefreshSession(tokens: AuthTokens): boolean {
  return isNearExpiry(tokens.expiresAt, TOKEN_REFRESH_WINDOW_MS);
}

export class SessionValidityCoordinator {
  constructor(private readonly dependencies: SessionValidityDependencies) {}

  async ensureClientToken(tokens: AuthTokens): Promise<AuthTokens> {
    const hasUsableClientToken =
      Boolean(tokens.clientToken) &&
      !isNearExpiry(tokens.clientTokenExpiresAt, CLIENT_TOKEN_REFRESH_WINDOW_MS);
    if (hasUsableClientToken || isExpired(tokens.expiresAt)) {
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

  async ensureValidSessionWithStatus(
    forceRefresh = false,
    expectedUserId?: string,
  ): Promise<AuthSessionResult> {
    const { accounts } = this.dependencies.state;
    const currentSession = accounts.getSession();
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

    if (!tokens.clientToken && !isExpired(tokens.expiresAt)) {
      try {
        const withClientToken = await this.ensureClientToken(tokens);
        if (withClientToken.clientToken && withClientToken.clientToken !== tokens.clientToken) {
          accounts.updateSession({
            ...currentSession,
            tokens: withClientToken,
          });
          tokens = withClientToken;
          await this.dependencies.state.persist();
        }
      } catch (error) {
        console.warn("Unable to bootstrap client token from saved session:", error);
      }
    }

    if (!forceRefresh && !shouldRefreshSession(tokens)) {
      return {
        session: accounts.getSession(),
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
      const latestSession = accounts.getSession() ?? currentSession;
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

      accounts.updateSession({
        provider: baseSession.provider,
        tokens: refreshedTokens,
        user: resolvedUser,
      });
      this.dependencies.enrichmentCaches.clearSubscription();
      await this.dependencies.enrichmentCaches.enrichUserTier();
      await this.dependencies.state.persist();

      const sourceText = source === "client_token" ? "client token" : "refresh token";
      return {
        session: accounts.getSession(),
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
        const refreshedFromClientToken = await refreshWithClientToken(
          tokens.clientToken,
          userId,
          tokens.authClientId,
        );
        let refreshedTokens = mergeTokenSnapshot(tokens, refreshedFromClientToken);
        refreshedTokens = await this.ensureClientToken(refreshedTokens);
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
          idToken: refreshedOAuth.idToken ?? tokens.idToken,
          clientToken: tokens.clientToken,
          clientTokenExpiresAt: tokens.clientTokenExpiresAt,
          clientTokenLifetimeMs: tokens.clientTokenLifetimeMs,
          authClientId: refreshedOAuth.authClientId ?? tokens.authClientId,
        };
        refreshedTokens = await this.ensureClientToken(refreshedTokens);
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
        await this.dependencies.logout();
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
        session: accounts.getSession(),
        refresh: {
          attempted: true,
          forced: forceRefresh,
          outcome: "missing_refresh_token",
          message: "No refresh token available. Using saved session token.",
        },
      };
    }

    if (expired) {
      await this.dependencies.logout();
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
      session: accounts.getSession(),
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
}
