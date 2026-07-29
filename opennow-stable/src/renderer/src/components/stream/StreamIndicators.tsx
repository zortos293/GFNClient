import type { JSX } from "react";
import { Mic, MicOff } from "lucide-react";
import { m } from "motion/react";
import type { StreamDiagnosticsStore } from "../../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsSelector } from "../../utils/streamDiagnosticsStore";
import type { MicState } from "../../platforms/gfn/microphoneManager";
import { formatElapsed } from "../../utils/timeFormat";

type MicBadgeState = {
  connectedGamepads: number;
  micState: MicState;
  micEnabled: boolean;
};

function isMicBadgeStateEqual(prev: MicBadgeState, next: MicBadgeState): boolean {
  return (
    prev.connectedGamepads === next.connectedGamepads &&
    prev.micState === next.micState &&
    prev.micEnabled === next.micEnabled
  );
}

export function MicrophoneIndicator({
  diagnosticsStore,
  showAntiAfkIndicator,
  hideStreamButtons,
  isConnecting,
  onToggleMicrophone,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  showAntiAfkIndicator: boolean;
  hideStreamButtons: boolean;
  isConnecting: boolean;
  onToggleMicrophone?: () => void;
}): JSX.Element | null {
  const { connectedGamepads, micState, micEnabled } = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats): MicBadgeState => ({
      connectedGamepads: stats.connectedGamepads,
      micState: stats.micState ?? "uninitialized",
      micEnabled: stats.micEnabled ?? false,
    }),
    isMicBadgeStateEqual,
  );
  const hasMicrophone = micState === "started" || micState === "stopped";
  const showMicIndicator = hasMicrophone && !isConnecting && !hideStreamButtons;

  if (!showMicIndicator || !onToggleMicrophone) {
    return null;
  }

  return (
    <button
      type="button"
      className={`sv-mic${connectedGamepads > 0 || showAntiAfkIndicator ? " sv-mic--stacked" : ""}`}
      onClick={onToggleMicrophone}
      data-enabled={micEnabled}
      title={micEnabled ? "Mute microphone" : "Unmute microphone"}
      aria-label={micEnabled ? "Mute microphone" : "Unmute microphone"}
      aria-pressed={micEnabled}
    >
      {micEnabled ? <Mic size={18} /> : <MicOff size={18} />}
    </button>
  );
}

export function AntiAfkIndicator({
  diagnosticsStore,
  antiAfkEnabled,
  showAntiAfkIndicator,
  isConnecting,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  antiAfkEnabled: boolean;
  showAntiAfkIndicator: boolean;
  isConnecting: boolean;
}): JSX.Element | null {
  const hasGamepad = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.connectedGamepads > 0,
  );

  if (!antiAfkEnabled || !showAntiAfkIndicator || isConnecting) {
    return null;
  }

  return (
    <div className={`sv-afk${hasGamepad ? " sv-afk--stacked" : ""}`} title="Anti-AFK is enabled">
      <span className="sv-afk-dot" />
      <span className="sv-afk-label">ANTI-AFK ON</span>
    </div>
  );
}

export function RecordingIndicator({
  diagnosticsStore,
  showAntiAfkIndicator,
  hideStreamButtons,
  isConnecting,
  isRecording,
  onToggleMicrophone,
  recordingDurationMs,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  showAntiAfkIndicator: boolean;
  hideStreamButtons: boolean;
  isConnecting: boolean;
  isRecording: boolean;
  onToggleMicrophone?: () => void;
  recordingDurationMs: number;
}): JSX.Element | null {
  const { connectedGamepads, micState } = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => ({
      connectedGamepads: stats.connectedGamepads,
      micState: stats.micState ?? "uninitialized",
    }),
    (prev, next) => prev.connectedGamepads === next.connectedGamepads && prev.micState === next.micState,
  );
  const hasMicrophone = micState === "started" || micState === "stopped";
  const showMicIndicator = hasMicrophone && !isConnecting && !hideStreamButtons && Boolean(onToggleMicrophone);
  const stackedBadges = [connectedGamepads > 0, showAntiAfkIndicator, showMicIndicator].filter(Boolean).length;

  if (!isRecording || isConnecting) {
    return null;
  }

  return (
    <div
      className="sv-rec"
      style={{ top: 14 + 42 * stackedBadges }}
      title={`Recording · ${formatElapsed(Math.round(recordingDurationMs / 1000))}`}
    >
      <m.span
        className="sv-rec-dot"
        animate={{ opacity: [0.45, 1, 0.45], scale: [0.85, 1, 0.85] }}
        transition={{ duration: 1.2, repeat: Infinity, ease: "easeInOut" }}
      />
      <span className="sv-rec-label">REC {formatElapsed(Math.round(recordingDurationMs / 1000))}</span>
    </div>
  );
}
