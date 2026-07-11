import { useEffect } from "react";
import type { JSX } from "react";
import { Loader2 } from "lucide-react";
import type { StreamDiagnosticsStore } from "../../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsSelector } from "../../utils/streamDiagnosticsStore";
import { useTranslation } from "../../i18n";

export function hasVisibleStreamVideo(stats: {
  nativeRendererActive: boolean;
  framesDecoded: number;
  resolution: string;
}): boolean {
  if (stats.nativeRendererActive) {
    return true;
  }
  return stats.framesDecoded > 0;
}

export function StreamEmptyState({
  diagnosticsStore,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
}): JSX.Element | null {
  const hasVisibleVideo = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => hasVisibleStreamVideo(stats),
  );

  if (hasVisibleVideo) {
    return null;
  }

  return (
    <div className="sv-empty">
      <div className="sv-empty-grad" />
    </div>
  );
}

export function StreamWaitingForVideo({
  diagnosticsStore,
  isConnecting,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  isConnecting: boolean;
}): JSX.Element | null {
  const { t } = useTranslation();
  const waitingForFirstFrame = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => {
      if (stats.nativeRendererActive || stats.framesDecoded > 0) {
        return false;
      }
      return stats.connectionState === "connected" || stats.resolution !== "";
    },
  );

  if (isConnecting || !waitingForFirstFrame) {
    return null;
  }

  return (
    <div className="sv-warm" role="status" aria-live="polite">
      <Loader2 className="sv-warm-spin" size={34} />
      <p className="sv-warm-text">{t("stream.stats.waitingForVideo")}</p>
    </div>
  );
}

export function SidebarMicMutedBadge({
  diagnosticsStore,
  micTrack,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  micTrack?: MediaStreamTrack | null;
}): JSX.Element | null {
  const micEnabled = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.micEnabled ?? false,
  );

  if (!micTrack || micEnabled) {
    return null;
  }

  return <span className="settings-value-badge">Muted</span>;
}

export function VideoFocusOnReady({
  diagnosticsStore,
  isConnecting,
  videoRef,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  isConnecting: boolean;
  videoRef: React.RefObject<HTMLVideoElement | null>;
}): null {
  const shouldFocusVideo = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.resolution !== "" && !stats.nativeRendererActive,
  );

  useEffect(() => {
    if (!isConnecting && videoRef.current && shouldFocusVideo) {
      const timer = window.setTimeout(() => {
        if (videoRef.current && document.activeElement !== videoRef.current) {
          videoRef.current.focus({ preventScroll: true });
          console.log("[StreamView] Focused video element");
        }
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [isConnecting, shouldFocusVideo, videoRef]);

  return null;
}
