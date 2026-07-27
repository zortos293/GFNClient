import type { ActiveSessionInfo } from "@shared/gfn";

import type { StreamStatus } from "./appTypes";
import type { RuntimeSnapshot } from "./runtimeSnapshot";
import { isExpectedNativeSessionClose } from "./streamSessionHelpers";

export type SignalingDisconnectDecision =
  | "ignore-app-unloading"
  | "expected-session-close"
  | "ignore-active-ice"
  | "fail-before-remote-ice"
  | "ignore-controlled-disconnect"
  | "recover";

export interface SignalingDisconnectInput {
  appUnloading: boolean;
  streamStatus: StreamStatus;
  reason: string;
  hasConfirmedRemoteIce: boolean;
  iceState: RTCIceConnectionState;
  pendingControlledDisconnects: number;
}

export function decideSignalingDisconnect({
  appUnloading,
  streamStatus,
  reason,
  hasConfirmedRemoteIce,
  iceState,
  pendingControlledDisconnects,
}: SignalingDisconnectInput): SignalingDisconnectDecision {
  if (appUnloading) return "ignore-app-unloading";
  if (streamStatus !== "idle" && isExpectedNativeSessionClose(reason)) {
    return "expected-session-close";
  }
  if (
    (hasConfirmedRemoteIce && iceState === "new")
    || iceState === "connected"
    || iceState === "completed"
    || iceState === "checking"
  ) {
    return "ignore-active-ice";
  }
  if (!hasConfirmedRemoteIce) return "fail-before-remote-ice";
  if (pendingControlledDisconnects > 0) return "ignore-controlled-disconnect";
  return "recover";
}

export interface RecoveryCandidateResult {
  candidate: ActiveSessionInfo | null;
  source: "active-session" | "persisted-resume-context" | null;
  hasQueueOnlyMatch: boolean;
}

export function selectRecoveryCandidate(
  activeSessions: readonly ActiveSessionInfo[],
  currentSessionId: string,
  previousAppId: number | null,
  persisted: RuntimeSnapshot["resumeContext"],
): RecoveryCandidateResult {
  const isReady = (entry: ActiveSessionInfo): boolean =>
    Boolean(entry.serverIp) && (entry.status === 2 || entry.status === 3);
  const sameSession = activeSessions.find(
    (entry) => entry.sessionId === currentSessionId && isReady(entry),
  );
  const sameApp = previousAppId === null
    ? undefined
    : activeSessions.find((entry) => (
      entry.appId === previousAppId
      && entry.sessionId === currentSessionId
      && isReady(entry)
    )) ?? activeSessions.find((entry) => entry.appId === previousAppId && isReady(entry));
  const candidate = sameSession ?? sameApp;

  if (candidate) {
    return { candidate, source: "active-session", hasQueueOnlyMatch: false };
  }
  if (persisted?.sessionId === currentSessionId && persisted.serverIp) {
    return {
      candidate: {
        sessionId: persisted.sessionId,
        appId: Number.isFinite(persisted.appId ?? NaN) ? persisted.appId as number : previousAppId ?? 0,
        appLaunchMode: persisted.appLaunchMode,
        enablePersistingInGameSettings: persisted.enablePersistingInGameSettings,
        status: 2,
        serverIp: persisted.serverIp,
        streamingBaseUrl: persisted.streamingBaseUrl,
        signalingUrl: persisted.signalingUrl,
      },
      source: "persisted-resume-context",
      hasQueueOnlyMatch: false,
    };
  }
  return {
    candidate: null,
    source: null,
    hasQueueOnlyMatch: activeSessions.some(
      (entry) => entry.sessionId === currentSessionId && entry.status === 1,
    ),
  };
}
