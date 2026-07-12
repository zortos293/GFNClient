import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";

import type {
  AuthDeviceLoginChallenge,
  AuthSession,
  LoginProvider,
  SavedAccount,
  Settings,
} from "@shared/gfn";

import { getEnabledSessionProxyUrl } from "../lib/sessionProxy";
import type { RuntimeSnapshot } from "../lib/runtimeSnapshot";
import { loadRuntimeSnapshot } from "../lib/runtimeSnapshot";
import { VARIANT_SELECTION_LOCALSTORAGE_KEY } from "../lib/catalogPreferences";
import type { CatalogClearMode, ClearSessionCatalogOptions } from "./useCatalogData";

type TranslateFunction = typeof import("../i18n").t;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

export interface UseAuthSessionInput {
  t: TranslateFunction;
  loadSessionRuntimeData: (
    session: AuthSession,
    options?: { background?: boolean; proxyUrl?: string },
  ) => Promise<void>;
  hydrateCatalogSnapshot: (session: AuthSession, proxyUrl?: string) => string | null;
  clearSessionCatalog: (mode: CatalogClearMode, options?: ClearSessionCatalogOptions) => void;
  resetLaunchRuntime: (options?: {
    keepLaunchError?: boolean;
    keepStreamingContext?: boolean;
  }) => void;
  refreshNavbarActiveSession: (sessionOverride?: AuthSession) => Promise<void>;
  onBootstrapSettings: (settings: Settings, sessionProxyUrl: string | undefined) => void;
  onBootstrapVariantSelections: (selections: Record<string, string>) => void;
  onBootstrapRuntimeSnapshot: (snapshot: RuntimeSnapshot | null) => void;
  setCurrentPage: Dispatch<SetStateAction<"home" | "library" | "settings">>;
  setNavbarActiveSession: Dispatch<SetStateAction<import("@shared/gfn").ActiveSessionInfo | null>>;
  setIsResumingNavbarSession: Dispatch<SetStateAction<boolean>>;
}

export interface AuthSessionApi {
  authSession: AuthSession | null;
  setAuthSession: Dispatch<SetStateAction<AuthSession | null>>;
  savedAccounts: SavedAccount[];
  setSavedAccounts: Dispatch<SetStateAction<SavedAccount[]>>;
  providers: LoginProvider[];
  providerIdpId: string;
  setProviderIdpId: Dispatch<SetStateAction<string>>;
  isLoggingIn: boolean;
  activeLoginMode: "oauth" | "qr" | null;
  loginError: string | null;
  setLoginError: Dispatch<SetStateAction<string | null>>;
  qrLoginChallenge: AuthDeviceLoginChallenge | null;
  isInitializing: boolean;
  startupStatusMessage: string;
  startupRefreshNotice: { tone: "success" | "warn"; text: string } | null;
  setStartupRefreshNotice: Dispatch<SetStateAction<{ tone: "success" | "warn"; text: string } | null>>;
  accountToRemove: string | null;
  setAccountToRemove: Dispatch<SetStateAction<string | null>>;
  removeAccountConfirmOpen: boolean;
  setRemoveAccountConfirmOpen: Dispatch<SetStateAction<boolean>>;
  logoutConfirmOpen: boolean;
  setLogoutConfirmOpen: Dispatch<SetStateAction<boolean>>;
  selectedProvider: LoginProvider | null;
  refreshSavedAccounts: () => Promise<SavedAccount[]>;
  handleLogin: () => Promise<void>;
  handleQrLogin: () => Promise<void>;
  handleCancelQrLogin: () => void;
  handleSwitchAccount: (userId: string) => Promise<void>;
  handleRemoveAccount: (userId: string) => void;
  confirmRemoveAccount: () => Promise<void>;
  handleAddAccount: () => void;
  confirmLogout: () => Promise<void>;
  handleLogout: () => void;
  accountToRemoveDisplayName: string;
}

export function useAuthSession({
  t,
  loadSessionRuntimeData,
  hydrateCatalogSnapshot,
  clearSessionCatalog,
  resetLaunchRuntime,
  refreshNavbarActiveSession,
  onBootstrapSettings,
  onBootstrapVariantSelections,
  onBootstrapRuntimeSnapshot,
  setCurrentPage,
  setNavbarActiveSession,
  setIsResumingNavbarSession,
}: UseAuthSessionInput): AuthSessionApi {
  const [authSession, setAuthSession] = useState<AuthSession | null>(null);
  const [savedAccounts, setSavedAccounts] = useState<SavedAccount[]>([]);
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [providerIdpId, setProviderIdpId] = useState("");
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [activeLoginMode, setActiveLoginMode] = useState<"oauth" | "qr" | null>(null);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [qrLoginChallenge, setQrLoginChallenge] = useState<AuthDeviceLoginChallenge | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const [startupStatusMessage, setStartupStatusMessage] = useState(() => t("auth.status.restoringSavedSession"));
  const [startupRefreshNotice, setStartupRefreshNotice] = useState<{
    tone: "success" | "warn";
    text: string;
  } | null>(null);
  const [accountToRemove, setAccountToRemove] = useState<string | null>(null);
  const [removeAccountConfirmOpen, setRemoveAccountConfirmOpen] = useState(false);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  const hasInitializedRef = useRef(false);
  const qrLoginAttemptRef = useRef(0);
  const completingQrLoginRef = useRef(false);

  const selectedProvider = useMemo(() => {
    return providers.find((p) => p.idpId === providerIdpId) ?? authSession?.provider ?? null;
  }, [providers, providerIdpId, authSession]);

  const refreshSavedAccounts = useCallback(async (): Promise<SavedAccount[]> => {
    const accounts = await window.openNow.getSavedAccounts();
    setSavedAccounts(accounts);
    return accounts;
  }, []);

  useEffect(() => {
    if (hasInitializedRef.current) return;
    hasInitializedRef.current = true;

    const initialize = async () => {
      try {
        const loadedSettings = await window.openNow.getSettings();
        const loadedSessionProxyUrl = getEnabledSessionProxyUrl(loadedSettings);
        onBootstrapSettings(loadedSettings, loadedSessionProxyUrl);

        setStartupStatusMessage(t("auth.status.restoringSavedSession"));
        const [providerList, sessionResult] = await Promise.all([
          window.openNow.getLoginProviders(),
          window.openNow.getAuthSession(),
        ]);
        const accounts = await window.openNow.getSavedAccounts();
        const persistedSession = sessionResult.session;

        if (sessionResult.refresh.outcome === "refreshed") {
          setStartupRefreshNotice({
            tone: "success",
            text: t("auth.status.sessionRestoredTokenRefreshed"),
          });
          setStartupStatusMessage(t("auth.status.tokenRefreshedLoadingAccount"));
        } else if (sessionResult.refresh.outcome === "failed") {
          setStartupRefreshNotice({
            tone: "warn",
            text: t("auth.status.tokenRefreshFailedUsingSaved"),
          });
          setStartupStatusMessage(t("auth.status.tokenRefreshFailedContinuing"));
        } else if (sessionResult.refresh.outcome === "missing_refresh_token") {
          setStartupStatusMessage(t("auth.status.missingRefreshTokenContinuing"));
        } else if (persistedSession) {
          setStartupStatusMessage(t("auth.status.sessionRestored"));
        } else {
          setStartupStatusMessage(t("auth.status.noSavedSessionFound"));
        }

        try {
          const raw = localStorage.getItem(VARIANT_SELECTION_LOCALSTORAGE_KEY);
          if (raw) {
            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === "object") {
              onBootstrapVariantSelections(parsed as Record<string, string>);
            }
          }
        } catch {
          // ignore parse/storage errors
        }

        const persistedRuntimeSnapshot = loadRuntimeSnapshot();
        onBootstrapRuntimeSnapshot(persistedRuntimeSnapshot);

        setProviders(providerList);
        setAuthSession(persistedSession);
        setSavedAccounts(accounts);

        const activeProviderId = persistedSession?.provider?.idpId ?? providerList[0]?.idpId ?? "";
        setProviderIdpId(activeProviderId);

        if (persistedSession) {
          const hydrated = hydrateCatalogSnapshot(persistedSession, loadedSessionProxyUrl);
          void loadSessionRuntimeData(persistedSession, { background: hydrated !== null, proxyUrl: loadedSessionProxyUrl });
        } else {
          clearSessionCatalog("no-session");
        }

        setIsInitializing(false);
      } catch (error) {
        console.error("Initialization failed:", error);
        setStartupStatusMessage(t("auth.status.sessionRestoreFailed"));
        setIsInitializing(false);
      }
    };

    void initialize();
  }, [
    clearSessionCatalog,
    hydrateCatalogSnapshot,
    loadSessionRuntimeData,
    onBootstrapRuntimeSnapshot,
    onBootstrapSettings,
    onBootstrapVariantSelections,
    t,
  ]);

  const handleLogin = useCallback(async () => {
    setIsLoggingIn(true);
    setActiveLoginMode("oauth");
    setLoginError(null);
    if (qrLoginChallenge) {
      void window.openNow.cancelDeviceLogin({ attemptId: qrLoginChallenge.attemptId });
    }
    setQrLoginChallenge(null);
    try {
      const session = await window.openNow.login({ providerIdpId: providerIdpId || undefined });
      setAuthSession(session);
      setProviderIdpId(session.provider.idpId);
      await refreshSavedAccounts();
      await loadSessionRuntimeData(session);
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : t("errors.loginFailed"));
    } finally {
      setIsLoggingIn(false);
      setActiveLoginMode(null);
    }
  }, [loadSessionRuntimeData, providerIdpId, qrLoginChallenge, refreshSavedAccounts, t]);

  const handleCancelQrLogin = useCallback(() => {
    if (completingQrLoginRef.current) {
      return;
    }
    qrLoginAttemptRef.current += 1;
    if (qrLoginChallenge) {
      void window.openNow.cancelDeviceLogin({ attemptId: qrLoginChallenge.attemptId });
    }
    setQrLoginChallenge(null);
    setIsLoggingIn(false);
    setActiveLoginMode(null);
    setLoginError(null);
  }, [qrLoginChallenge]);

  const handleQrLogin = useCallback(async () => {
    const attemptId = qrLoginAttemptRef.current + 1;
    qrLoginAttemptRef.current = attemptId;
    completingQrLoginRef.current = false;
    setIsLoggingIn(true);
    setActiveLoginMode("qr");
    setLoginError(null);
    if (qrLoginChallenge) {
      void window.openNow.cancelDeviceLogin({ attemptId: qrLoginChallenge.attemptId });
    }
    setQrLoginChallenge(null);

    try {
      const challenge = await window.openNow.startDeviceLogin({ providerIdpId: providerIdpId || undefined });
      if (qrLoginAttemptRef.current !== attemptId) {
        void window.openNow.cancelDeviceLogin({ attemptId: challenge.attemptId });
        return;
      }

      setQrLoginChallenge(challenge);
      let intervalSeconds = Math.max(1, challenge.intervalSeconds);

      while (Date.now() < challenge.expiresAt) {
        await sleep(intervalSeconds * 1000);
        if (qrLoginAttemptRef.current !== attemptId) {
          return;
        }

        const result = await window.openNow.pollDeviceLogin({
          attemptId: challenge.attemptId,
          deviceCode: challenge.deviceCode,
        });
        if (qrLoginAttemptRef.current !== attemptId) {
          return;
        }

        if (result.status === "authorized") {
          completingQrLoginRef.current = true;
          setQrLoginChallenge(null);
          setActiveLoginMode(null);
          const session = await window.openNow.completeDeviceLogin({ attemptId: challenge.attemptId });
          if (qrLoginAttemptRef.current !== attemptId) {
            return;
          }
          setAuthSession(session);
          setProviderIdpId(session.provider.idpId);
          await refreshSavedAccounts();
          await loadSessionRuntimeData(session);
          return;
        }

        if (result.status === "pending") {
          continue;
        }

        if (result.status === "slow_down") {
          intervalSeconds += 5;
          continue;
        }

        throw new Error(result.error ?? t("errors.loginFailed"));
      }

      throw new Error(t("auth.qr.expired"));
    } catch (error) {
      if (qrLoginAttemptRef.current === attemptId) {
        setLoginError(error instanceof Error ? error.message : t("errors.loginFailed"));
      }
    } finally {
      if (qrLoginAttemptRef.current === attemptId) {
        setQrLoginChallenge(null);
        setIsLoggingIn(false);
        setActiveLoginMode(null);
        completingQrLoginRef.current = false;
      }
    }
  }, [loadSessionRuntimeData, providerIdpId, qrLoginChallenge, refreshSavedAccounts, t]);

  const handleSwitchAccount = useCallback(async (userId: string) => {
    try {
      const session = await window.openNow.switchAccount(userId);
      setAuthSession(session);
      setProviderIdpId(session.provider.idpId);
      await refreshSavedAccounts();
      await loadSessionRuntimeData(session);
      await refreshNavbarActiveSession(session);
    } catch (error) {
      console.warn("Failed to switch account:", error);
      setLoginError(error instanceof Error ? error.message : t("errors.switchAccountFailed"));
      try {
        await refreshSavedAccounts();
        const sessionResult = await window.openNow.getAuthSession();
        setAuthSession(sessionResult.session);
        if (sessionResult.session) {
          setProviderIdpId(sessionResult.session.provider.idpId);
          await loadSessionRuntimeData(sessionResult.session);
          await refreshNavbarActiveSession(sessionResult.session);
        } else {
          clearSessionCatalog("no-session");
          setNavbarActiveSession(null);
        }
      } catch (recoveryError) {
        console.warn("Failed to recover account state after switch failure:", recoveryError);
      }
    }
  }, [
    clearSessionCatalog,
    loadSessionRuntimeData,
    refreshNavbarActiveSession,
    refreshSavedAccounts,
    setNavbarActiveSession,
    t,
  ]);

  const handleRemoveAccount = useCallback((userId: string) => {
    setAccountToRemove(userId);
    setRemoveAccountConfirmOpen(true);
  }, []);

  const confirmRemoveAccount = useCallback(async () => {
    if (!accountToRemove) return;
    const targetUserId = accountToRemove;
    setRemoveAccountConfirmOpen(false);
    setAccountToRemove(null);

    await window.openNow.removeAccount(targetUserId);
    const [accounts, sessionResult] = await Promise.all([
      window.openNow.getSavedAccounts(),
      window.openNow.getAuthSession(),
    ]);
    setSavedAccounts(accounts);
    setAuthSession(sessionResult.session);
    if (sessionResult.session) {
      setProviderIdpId(sessionResult.session.provider.idpId);
      await loadSessionRuntimeData(sessionResult.session);
      await refreshNavbarActiveSession(sessionResult.session);
      return;
    }
    clearSessionCatalog("no-session", { clearFeatured: true });
    setNavbarActiveSession(null);
  }, [
    accountToRemove,
    clearSessionCatalog,
    loadSessionRuntimeData,
    refreshNavbarActiveSession,
    setNavbarActiveSession,
  ]);

  const handleAddAccount = useCallback(() => {
    setAuthSession(null);
    setLoginError(null);
  }, []);

  const confirmLogout = useCallback(async () => {
    setLogoutConfirmOpen(false);
    clearSessionCatalog("logout");
    await window.openNow.logoutAll();
    setAuthSession(null);
    setSavedAccounts([]);
    resetLaunchRuntime();
    setNavbarActiveSession(null);
    setIsResumingNavbarSession(false);
    setCurrentPage("home");
  }, [
    clearSessionCatalog,
    resetLaunchRuntime,
    setCurrentPage,
    setIsResumingNavbarSession,
    setNavbarActiveSession,
  ]);

  const handleLogout = useCallback(() => {
    setLogoutConfirmOpen(true);
  }, []);

  const accountToRemoveDisplayName = useMemo(() => (
    savedAccounts.find((account) => account.userId === accountToRemove)?.displayName ?? t("auth.accounts.thisAccount")
  ), [accountToRemove, savedAccounts, t]);

  useEffect(() => {
    if (!startupRefreshNotice) return;
    const timer = window.setTimeout(() => setStartupRefreshNotice(null), 7000);
    return () => window.clearTimeout(timer);
  }, [startupRefreshNotice]);

  return {
    authSession,
    setAuthSession,
    savedAccounts,
    setSavedAccounts,
    providers,
    providerIdpId,
    setProviderIdpId,
    isLoggingIn,
    activeLoginMode,
    loginError,
    setLoginError,
    qrLoginChallenge,
    isInitializing,
    startupStatusMessage,
    startupRefreshNotice,
    setStartupRefreshNotice,
    accountToRemove,
    setAccountToRemove,
    removeAccountConfirmOpen,
    setRemoveAccountConfirmOpen,
    logoutConfirmOpen,
    setLogoutConfirmOpen,
    selectedProvider,
    refreshSavedAccounts,
    handleLogin,
    handleQrLogin,
    handleCancelQrLogin,
    handleSwitchAccount,
    handleRemoveAccount,
    confirmRemoveAccount,
    handleAddAccount,
    confirmLogout,
    handleLogout,
    accountToRemoveDisplayName,
  };
}
