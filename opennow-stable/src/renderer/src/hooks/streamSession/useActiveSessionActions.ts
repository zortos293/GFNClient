import { useCallback } from "react";
import type { ActiveSessionInfo, GameInfo, GameVariant, SessionStopRequest } from "@shared/gfn";

import type { StreamLoadingStatus } from "../../lib/appTypes";
import { toLaunchErrorState } from "../../lib/sessionState";
import type { StreamRuntimeState } from "./useStreamRuntimeState";

type TranslateFunction = typeof import("../../i18n").t;
type SessionContext = { game: GameInfo; variant?: GameVariant } | null;
type ResetLaunchRuntime = (options?: {
  keepLaunchError?: boolean;
  keepStreamingContext?: boolean;
}) => void;
type StopSession = (
  target: Pick<
    SessionStopRequest,
    "sessionId" | "zone" | "streamingBaseUrl" | "serverIp" | "clientId" | "deviceId"
  > | null | undefined,
) => Promise<boolean>;

type ActiveSessionRuntime = Pick<
  StreamRuntimeState,
  | "clientRef"
  | "isResumingNavbarSession"
  | "isTerminatingNavbarSession"
  | "launchInFlightRef"
  | "navbarActiveSession"
  | "navbarSessionActionInFlightRef"
  | "setIsResumingNavbarSession"
  | "setIsTerminatingNavbarSession"
  | "setLaunchError"
  | "setLocalSessionTimerWarning"
  | "setNavbarActiveSession"
  | "setQueuePosition"
  | "setRemoteStreamWarning"
  | "setSessionStartedAtMs"
  | "setStreamingGame"
  | "setStreamingStore"
  | "setStreamStatus"
  | "signalingRecoveryRef"
  | "streamStatus"
>;

export interface ActiveSessionActionsOptions {
  runtime: ActiveSessionRuntime;
  canResume: boolean;
  claimAndConnectSession: (session: ActiveSessionInfo) => Promise<void>;
  disconnectSignalingControlled: () => Promise<void>;
  effectiveStreamingBaseUrl: string;
  findGameContextForSession: (session: ActiveSessionInfo) => SessionContext;
  gameTitleByAppId: ReadonlyMap<number, string>;
  refreshNavbarActiveSession: () => Promise<void>;
  resetLaunchRuntime: ResetLaunchRuntime;
  resetSignalingRecoveryState: () => void;
  resetStatsOverlayToPreference: () => void;
  stopSessionByTarget: StopSession;
  t: TranslateFunction;
}

export function useActiveSessionActions({
  runtime,
  canResume,
  claimAndConnectSession,
  disconnectSignalingControlled,
  effectiveStreamingBaseUrl,
  findGameContextForSession,
  gameTitleByAppId,
  refreshNavbarActiveSession,
  resetLaunchRuntime,
  resetSignalingRecoveryState,
  resetStatsOverlayToPreference,
  stopSessionByTarget,
  t,
}: ActiveSessionActionsOptions) {
  const {
    clientRef,
    isResumingNavbarSession,
    isTerminatingNavbarSession,
    launchInFlightRef,
    navbarActiveSession,
    navbarSessionActionInFlightRef,
    setIsResumingNavbarSession,
    setIsTerminatingNavbarSession,
    setLaunchError,
    setLocalSessionTimerWarning,
    setNavbarActiveSession,
    setQueuePosition,
    setRemoteStreamWarning,
    setSessionStartedAtMs,
    setStreamingGame,
    setStreamingStore,
    setStreamStatus,
    signalingRecoveryRef,
    streamStatus,
  } = runtime;

  const handleResumeFromNavbar = useCallback(async (): Promise<void> => {
    if (
      !canResume
      || !navbarActiveSession
      || isResumingNavbarSession
      || isTerminatingNavbarSession
      || navbarSessionActionInFlightRef.current
      || launchInFlightRef.current
      || streamStatus !== "idle"
    ) {
      return;
    }

    navbarSessionActionInFlightRef.current = "resume";
    launchInFlightRef.current = true;
    resetSignalingRecoveryState();
    setIsResumingNavbarSession(true);
    let loadingStep: StreamLoadingStatus = "setup";
    const updateLoadingStep = (next: StreamLoadingStatus): void => {
      loadingStep = next;
      setStreamStatus(next);
    };

    setLaunchError(null);
    setQueuePosition(undefined);
    setSessionStartedAtMs(null);
    setRemoteStreamWarning(null);
    setLocalSessionTimerWarning(null);
    resetStatsOverlayToPreference();
    const matchedContext = findGameContextForSession(navbarActiveSession);
    let resumeGameContext: GameInfo | null = null;
    if (matchedContext) {
      resumeGameContext = matchedContext.game;
      setStreamingGame(matchedContext.game);
      setStreamingStore(matchedContext.variant?.store ?? null);
    } else {
      setStreamingStore(null);
    }
    updateLoadingStep("setup");

    try {
      signalingRecoveryRef.current.appId = navbarActiveSession.appId;
      await claimAndConnectSession(navbarActiveSession);
      setNavbarActiveSession(null);
    } catch (error) {
      console.error("Navbar resume failed:", error);
      setLaunchError(toLaunchErrorState(t, error, loadingStep, resumeGameContext));
      await disconnectSignalingControlled();
      clientRef.current?.dispose();
      clientRef.current = null;
      resetLaunchRuntime({ keepLaunchError: true });
      void refreshNavbarActiveSession();
    } finally {
      navbarSessionActionInFlightRef.current = null;
      launchInFlightRef.current = false;
      setIsResumingNavbarSession(false);
    }
  }, [
    canResume,
    claimAndConnectSession,
    clientRef,
    disconnectSignalingControlled,
    findGameContextForSession,
    isResumingNavbarSession,
    isTerminatingNavbarSession,
    launchInFlightRef,
    navbarActiveSession,
    navbarSessionActionInFlightRef,
    refreshNavbarActiveSession,
    resetLaunchRuntime,
    resetSignalingRecoveryState,
    resetStatsOverlayToPreference,
    setIsResumingNavbarSession,
    setLaunchError,
    setLocalSessionTimerWarning,
    setNavbarActiveSession,
    setQueuePosition,
    setRemoteStreamWarning,
    setSessionStartedAtMs,
    setStreamingGame,
    setStreamingStore,
    setStreamStatus,
    signalingRecoveryRef,
    streamStatus,
    t,
  ]);

  const handleTerminateNavbarSession = useCallback(async (): Promise<void> => {
    if (
      !navbarActiveSession
      || isResumingNavbarSession
      || isTerminatingNavbarSession
      || navbarSessionActionInFlightRef.current
      || launchInFlightRef.current
      || streamStatus !== "idle"
    ) {
      return;
    }

    const activeSessionTitle = gameTitleByAppId.get(navbarActiveSession.appId)?.trim()
      || t("session.thisSession");
    if (!window.confirm(t("session.terminateConfirmation", { title: activeSessionTitle }))) {
      return;
    }

    navbarSessionActionInFlightRef.current = "terminate";
    setIsTerminatingNavbarSession(true);
    try {
      await stopSessionByTarget({
        sessionId: navbarActiveSession.sessionId,
        zone: "",
        streamingBaseUrl: navbarActiveSession.streamingBaseUrl ?? (effectiveStreamingBaseUrl || undefined),
        serverIp: navbarActiveSession.serverIp,
      });
      setNavbarActiveSession(null);
    } catch (error) {
      console.error("Navbar terminate failed:", error);
    } finally {
      navbarSessionActionInFlightRef.current = null;
      setIsTerminatingNavbarSession(false);
      void refreshNavbarActiveSession();
    }
  }, [
    effectiveStreamingBaseUrl,
    gameTitleByAppId,
    isResumingNavbarSession,
    isTerminatingNavbarSession,
    launchInFlightRef,
    navbarActiveSession,
    navbarSessionActionInFlightRef,
    refreshNavbarActiveSession,
    setIsTerminatingNavbarSession,
    setNavbarActiveSession,
    stopSessionByTarget,
    streamStatus,
    t,
  ]);

  return { handleResumeFromNavbar, handleTerminateNavbarSession };
}
