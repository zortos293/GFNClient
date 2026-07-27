import { useCallback, useEffect } from "react";

import {
  buildRuntimeSnapshot,
  clearRuntimeSnapshot,
  saveRuntimeSnapshot,
} from "../../lib/runtimeSnapshot";
import type { StreamRuntimeState } from "./useStreamRuntimeState";

export function useRuntimeSnapshotPersistence(runtime: StreamRuntimeState) {
  const {
    appUnloadingRef,
    launchError,
    navbarActiveSession,
    queuePosition,
    runtimeSnapshotRef,
    session,
    sessionRef,
    signalingRecoveryRef,
    streamingGame,
    streamingStore,
    streamStatus,
    streamStatusRef,
  } = runtime;

  useEffect(() => {
    sessionRef.current = session;
  }, [session, sessionRef]);

  useEffect(() => {
    const snapshot = buildRuntimeSnapshot({
      streamStatus,
      session,
      navbarSession: navbarActiveSession,
      streamingGameId: streamingGame?.id ?? null,
      streamingStore,
      recoveryAppId: signalingRecoveryRef.current.appId,
    });
    runtimeSnapshotRef.current = snapshot;
    if (snapshot) {
      saveRuntimeSnapshot(snapshot);
    } else {
      clearRuntimeSnapshot();
    }
  }, [
    navbarActiveSession,
    runtimeSnapshotRef,
    session,
    signalingRecoveryRef,
    streamingGame?.id,
    streamingStore,
    streamStatus,
  ]);

  const persistRuntimeSnapshotNow = useCallback((): void => {
    const snapshot = buildRuntimeSnapshot({
      streamStatus: streamStatusRef.current,
      session: sessionRef.current,
      navbarSession: navbarActiveSession,
      streamingGameId: streamingGame?.id ?? null,
      streamingStore,
      recoveryAppId: signalingRecoveryRef.current.appId,
    });
    runtimeSnapshotRef.current = snapshot;
    if (snapshot) {
      saveRuntimeSnapshot(snapshot);
    } else {
      clearRuntimeSnapshot();
    }
  }, [
    navbarActiveSession,
    runtimeSnapshotRef,
    sessionRef,
    signalingRecoveryRef,
    streamingGame?.id,
    streamingStore,
    streamStatusRef,
  ]);

  useEffect(() => {
    const onBeforeUnload = (): void => {
      appUnloadingRef.current = true;
      persistRuntimeSnapshotNow();
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [appUnloadingRef, persistRuntimeSnapshotNow]);

  useEffect(() => {
    streamStatusRef.current = streamStatus;
  }, [streamStatus, streamStatusRef]);

  useEffect(() => {
    const detail = {
      status: streamStatus,
      queuePosition,
      launchError: launchError
        ? {
          title: launchError.title,
          description: launchError.description,
          stage: launchError.stage,
          codeLabel: launchError.codeLabel,
        }
        : null,
      gameTitle: streamingGame?.title ?? null,
      gameCover: streamingGame?.imageUrl ?? null,
      platformStore: streamingStore,
    };
    try {
      window.dispatchEvent(new CustomEvent("opennow:session-update", { detail }));
    } catch {
      // Ignore event delivery failures during teardown.
    }
  }, [launchError, queuePosition, streamingGame, streamingStore, streamStatus]);

  return { persistRuntimeSnapshotNow };
}
