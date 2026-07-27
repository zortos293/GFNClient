import type { ActiveSessionInfo, SessionInfo } from "@shared/gfn";

import type { StreamStatus } from "./appTypes";

export const RUNTIME_SNAPSHOT_LOCALSTORAGE_KEY = "opennow.runtimeSnapshot.v1";

export interface RuntimeSnapshot {
  version: 1;
  updatedAt: number;
  streamStatus: StreamStatus;
  sessionId: string | null;
  sessionAppId: number | null;
  streamingGameId: string | null;
  streamingStore: string | null;
  recoveryAppId: number | null;
  resumeContext: {
    sessionId: string;
    serverIp: string;
    streamingBaseUrl?: string;
    signalingServer?: string;
    signalingUrl?: string;
    appId?: number;
    /** Wire appLaunchMode the session was created with, for session-stable claims */
    appLaunchMode?: number;
    /** Wire in-game settings persistence value the session was created with, for session-stable claims */
    enablePersistingInGameSettings?: boolean;
    clientId?: string;
    deviceId?: string;
  } | null;
}

export interface RuntimeSnapshotInput {
  streamStatus: StreamStatus;
  session: SessionInfo | null;
  navbarSession: ActiveSessionInfo | null;
  streamingGameId: string | null;
  streamingStore: string | null;
  recoveryAppId: number | null;
  updatedAt?: number;
}

export function buildRuntimeSnapshot({
  streamStatus,
  session,
  navbarSession,
  streamingGameId,
  streamingStore,
  recoveryAppId,
  updatedAt = Date.now(),
}: RuntimeSnapshotInput): RuntimeSnapshot | null {
  if (streamStatus === "idle" && session === null && navbarSession === null) {
    return null;
  }

  const validRecoveryAppId = Number.isFinite(recoveryAppId ?? NaN) ? recoveryAppId : null;
  return {
    version: 1,
    updatedAt,
    streamStatus,
    sessionId: session?.sessionId ?? navbarSession?.sessionId ?? null,
    sessionAppId: validRecoveryAppId ?? navbarSession?.appId ?? null,
    streamingGameId,
    streamingStore,
    recoveryAppId,
    resumeContext: session
      ? {
        sessionId: session.sessionId,
        serverIp: session.serverIp,
        streamingBaseUrl: session.streamingBaseUrl,
        signalingServer: session.signalingServer,
        signalingUrl: session.signalingUrl,
        appId: validRecoveryAppId ?? undefined,
        appLaunchMode: session.appLaunchMode,
        enablePersistingInGameSettings: session.enablePersistingInGameSettings,
        clientId: session.clientId,
        deviceId: session.deviceId,
      }
      : navbarSession?.sessionId && navbarSession.serverIp
        ? {
          sessionId: navbarSession.sessionId,
          serverIp: navbarSession.serverIp,
          streamingBaseUrl: navbarSession.streamingBaseUrl,
          signalingUrl: navbarSession.signalingUrl,
          appId: Number.isFinite(navbarSession.appId) ? navbarSession.appId : undefined,
          appLaunchMode: navbarSession.appLaunchMode,
          enablePersistingInGameSettings: navbarSession.enablePersistingInGameSettings,
        }
        : null,
  };
}

export function loadRuntimeSnapshot(): RuntimeSnapshot | null {
  try {
    const raw = localStorage.getItem(RUNTIME_SNAPSHOT_LOCALSTORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<RuntimeSnapshot>;
    if (parsed.version !== 1) return null;
    return {
      version: 1,
      updatedAt: typeof parsed.updatedAt === "number" ? parsed.updatedAt : Date.now(),
      streamStatus: (typeof parsed.streamStatus === "string" ? parsed.streamStatus : "idle") as StreamStatus,
      sessionId: typeof parsed.sessionId === "string" ? parsed.sessionId : null,
      sessionAppId: typeof parsed.sessionAppId === "number" ? parsed.sessionAppId : null,
      streamingGameId: typeof parsed.streamingGameId === "string" ? parsed.streamingGameId : null,
      streamingStore: typeof parsed.streamingStore === "string" ? parsed.streamingStore : null,
      recoveryAppId: typeof parsed.recoveryAppId === "number" ? parsed.recoveryAppId : null,
      resumeContext:
        parsed.resumeContext &&
        typeof parsed.resumeContext === "object" &&
        typeof parsed.resumeContext.sessionId === "string" &&
        typeof parsed.resumeContext.serverIp === "string"
          ? {
            sessionId: parsed.resumeContext.sessionId,
            serverIp: parsed.resumeContext.serverIp,
            streamingBaseUrl:
              typeof parsed.resumeContext.streamingBaseUrl === "string"
                ? parsed.resumeContext.streamingBaseUrl
                : undefined,
            signalingServer:
              typeof parsed.resumeContext.signalingServer === "string"
                ? parsed.resumeContext.signalingServer
                : undefined,
            signalingUrl:
              typeof parsed.resumeContext.signalingUrl === "string"
                ? parsed.resumeContext.signalingUrl
                : undefined,
            appId:
              typeof parsed.resumeContext.appId === "number" && Number.isFinite(parsed.resumeContext.appId)
                ? parsed.resumeContext.appId
                : undefined,
            appLaunchMode:
              typeof parsed.resumeContext.appLaunchMode === "number" &&
                Number.isFinite(parsed.resumeContext.appLaunchMode)
                ? parsed.resumeContext.appLaunchMode
                : undefined,
            enablePersistingInGameSettings:
              typeof parsed.resumeContext.enablePersistingInGameSettings === "boolean"
                ? parsed.resumeContext.enablePersistingInGameSettings
                : undefined,
            clientId:
              typeof parsed.resumeContext.clientId === "string"
                ? parsed.resumeContext.clientId
                : undefined,
            deviceId:
              typeof parsed.resumeContext.deviceId === "string"
                ? parsed.resumeContext.deviceId
                : undefined,
          }
          : null,
    };
  } catch {
    return null;
  }
}

export function saveRuntimeSnapshot(snapshot: RuntimeSnapshot): void {
  try {
    localStorage.setItem(RUNTIME_SNAPSHOT_LOCALSTORAGE_KEY, JSON.stringify(snapshot));
  } catch {
    // ignore
  }
}

export function clearRuntimeSnapshot(): void {
  try {
    localStorage.removeItem(RUNTIME_SNAPSHOT_LOCALSTORAGE_KEY);
  } catch {
    // ignore
  }
}
