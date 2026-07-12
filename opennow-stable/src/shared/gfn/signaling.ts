import type { NativeQueueMode } from "./stream";
import type { SessionInfo, StreamSettings } from "./session";

export interface SignalingConnectRequest {
  sessionId: string;
  signalingServer: string;
  signalingUrl?: string;
  nativeStreamer?: NativeStreamerSessionContext;
}

export interface IceCandidatePayload {
  candidate: string;
  sdpMid?: string | null;
  sdpMLineIndex?: number | null;
  usernameFragment?: string | null;
}

export interface SendAnswerRequest {
  sdp: string;
  nvstSdp?: string;
}

export type NativeStreamerShortcutAction =
  | "toggleStats"
  | "togglePointerLock"
  | "toggleFullscreen"
  | "stopStream"
  | "toggleAntiAfk"
  | "toggleMicrophone"
  | "screenshot"
  | "toggleRecording";

export interface NativeStreamerShortcutBindings {
  toggleStats: string;
  togglePointerLock: string;
  toggleFullscreen: string;
  stopStream: string;
  toggleAntiAfk: string;
  toggleMicrophone: string;
  screenshot: string;
  toggleRecording: string;
}

export interface NativeStreamerSessionContext {
  session: SessionInfo;
  settings: StreamSettings;
  shortcuts: NativeStreamerShortcutBindings;
  nvstVideo?: NvstVideoSession;
}

export interface NvstVideoSession {
  clientUdpPort: number;
  videoPeerIp: string;
  videoPeerPort: number;
  srtpAesKeyHex: string;
  srtpKeyId: number;
  pingPayload?: string;
  codec?: string;
}

export function buildNativeStreamerSessionContext(
  session: SessionInfo,
  settings: StreamSettings,
  shortcuts: NativeStreamerShortcutBindings,
  nvstVideo?: NvstVideoSession,
): NativeStreamerSessionContext {
  const negotiatedStreamProfile = session.negotiatedStreamProfile
    ? {
      ...session.negotiatedStreamProfile,
      codec: session.negotiatedStreamProfile.codec ?? settings.codec,
    }
    : { codec: settings.codec };

  return {
    session: {
      ...session,
      negotiatedStreamProfile,
    },
    settings: {
      ...settings,
      enableCloudGsync:
        session.negotiatedStreamProfile?.enableCloudGsync ?? settings.enableCloudGsync,
    },
    shortcuts,
    ...(nvstVideo ? { nvstVideo } : {}),
  };
}

export interface NativeVideoTransition {
  transitionType: string;
  source: string;
  atMs: number;
  oldCaps?: string;
  newCaps?: string;
  oldFramerate?: string;
  newFramerate?: string;
  oldMemoryMode?: string;
  newMemoryMode?: string;
  renderGapMs?: number;
  requestedFps?: number;
  capsFramerate?: string;
  highFpsRisk?: boolean;
  queueMode?: NativeQueueMode;
  summary?: string;
}

export interface NativeInputPacket {
  payload: ArrayBuffer | Uint8Array | number[];
  partiallyReliable?: boolean;
}

export interface NativeRenderSurfaceRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface NativeRenderSurfaceUpdate {
  rect: NativeRenderSurfaceRect | null;
  visible: boolean;
  deviceScaleFactor: number;
  showStats?: boolean;
}

export interface NativeRenderSurface extends NativeRenderSurfaceUpdate {
  windowHandle?: string;
}

export interface KeyframeRequest {
  reason: string;
  backlogFrames: number;
  attempt: number;
}

export type MainToRendererSignalingEvent =
  | { type: "connected" }
  | { type: "disconnected"; reason: string }
  | { type: "offer"; sdp: string }
  | { type: "remote-ice"; candidate: IceCandidatePayload }
  | { type: "native-shortcut"; action: NativeStreamerShortcutAction }
  | { type: "native-clipboard-paste" }
  | { type: "native-input-capture-changed"; captured: boolean }
  | { type: "native-stream-started"; message?: string }
  | { type: "native-stream-stopped"; reason?: string }
  | { type: "native-stream-stats"; stats: NativeStreamStats }
  | { type: "native-stream-transition"; transition: NativeVideoTransition }
  | { type: "native-input-ready"; protocolVersion: number }
  | { type: "error"; message: string }
  | { type: "log"; message: string };

export interface NativeStreamStats {
  codec: string;
  resolution: string;
  hardwareAcceleration: string;
  memoryMode?: string;
  zeroCopy?: boolean;
  requestedFps?: number;
  capsFramerate?: string;
  bitrateKbps: number;
  targetBitrateKbps: number;
  bitratePerformancePercent: number;
  decodedFps: number;
  renderFps: number;
  framesDecoded: number;
  framesRendered: number;
  framesPendingToPresent?: number;
  sinkRendered?: number;
  sinkDropped?: number;
  zeroCopyD3D11: boolean;
  zeroCopyD3D12: boolean;
  queueMode?: NativeQueueMode;
  queueDepthChanges?: number;
  presentPacingChanges?: number;
  partialFlushCount?: number;
  completeFlushCount?: number;
  lastTransitionType?: string;
  lastTransitionAtMs?: number;
  lastTransitionSummary?: string;
  requestedStreamingFeaturesSummary?: string;
  finalizedStreamingFeaturesSummary?: string;
}

