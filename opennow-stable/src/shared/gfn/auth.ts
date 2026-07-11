export interface LoginProvider {
  idpId: string;
  code: string;
  displayName: string;
  streamingServiceUrl: string;
  priority: number;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresAt: number;
  authClientId?: string;
  clientToken?: string;
  clientTokenExpiresAt?: number;
  clientTokenLifetimeMs?: number;
}

export interface AuthUser {
  userId: string;
  displayName: string;
  email?: string;
  avatarUrl?: string;
  membershipTier: string;
}

export interface AuthSession {
  provider: LoginProvider;
  tokens: AuthTokens;
  user: AuthUser;
}

export interface SavedAccount {
  userId: string;
  displayName: string;
  email?: string;
  avatarUrl?: string;
  membershipTier: string;
  providerCode: string;
}

export interface AuthLoginRequest {
  providerIdpId?: string;
}

export interface AuthDeviceLoginStartRequest {
  providerIdpId?: string;
}

export interface AuthDeviceLoginChallenge {
  attemptId: string;
  deviceCode: string;
  userCode: string;
  verificationUri: string;
  verificationUriComplete: string;
  expiresAt: number;
  intervalSeconds: number;
}

export interface AuthDeviceLoginPollRequest {
  attemptId: string;
  deviceCode: string;
}

export interface AuthDeviceLoginAttemptRequest {
  attemptId: string;
}

export type AuthDeviceLoginPollStatus =
  | "pending"
  | "slow_down"
  | "expired"
  | "access_denied"
  | "authorized"
  | "error";

export interface AuthDeviceLoginPollResult {
  status: AuthDeviceLoginPollStatus;
  session?: AuthSession;
  error?: string;
  intervalSeconds?: number;
}

export interface AuthSessionRequest {
  forceRefresh?: boolean;
}

export type AuthRefreshOutcome = "not_attempted" | "refreshed" | "failed" | "missing_refresh_token";

export interface AuthRefreshStatus {
  attempted: boolean;
  forced: boolean;
  outcome: AuthRefreshOutcome;
  message: string;
  error?: string;
}

export interface AuthSessionResult {
  session: AuthSession | null;
  refresh: AuthRefreshStatus;
}
