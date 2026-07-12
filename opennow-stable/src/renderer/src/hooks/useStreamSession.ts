/**
 * Stream session extraction status
 * --------------------------------
 * Full `useStreamSession` was deferred: launch/claim/recovery/signaling remain in App.tsx
 * because they are tightly coupled to App-owned refs and callbacks:
 *
 * - clientRef / videoRef / audioRef
 * - launchInFlightRef / launchAbortRef / claimResumePromisesRef
 * - signalingRecoveryRef + recovery timer refs (stableRecovery, remoteIce, iceDisconnected)
 * - sessionRef / streamStatusRef / runtimeSnapshotRef
 * - resetLaunchRuntime, buildCurrentStreamSettings, applyClaimedSessionAndConnect
 * - Discord activity, native streamer bridge, queue-ad coordination
 *
 * This module re-exports the pure helpers/constants that were safe to extract.
 * A future hook can own recovery timers + claim/connect once those refs are grouped
 * into a dedicated stream-runtime context or ref bag.
 */

export {
  ICE_DISCONNECTED_RECOVERY_GRACE_MS,
  RECOVERABLE_STREAM_STATUSES,
  SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS,
  SIGNALING_RECOVERY_STABLE_RESET_DELAY_MS,
  SIGNALING_REMOTE_ICE_GRACE_MS,
  isExpectedNativeSessionClose,
  readStreamClipboardText,
  sendStreamClipboardPaste,
  sleep,
  type SignalingRecoveryState,
} from "../lib/streamSessionHelpers";
