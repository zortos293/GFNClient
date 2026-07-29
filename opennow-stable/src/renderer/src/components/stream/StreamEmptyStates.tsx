import { useEffect } from "react";
import type { JSX } from "react";
import { AnimatePresence, m } from "motion/react";
import type { StreamDiagnosticsStore } from "../../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsSelector } from "../../utils/streamDiagnosticsStore";
import { useTranslation } from "../../i18n";
import { MotionSpinner } from "../MotionSpinner";

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

  return (
    <AnimatePresence>
      {!hasVisibleVideo && (
        <m.div
          className="sv-empty"
          initial={{ opacity: 1, scale: 1 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 1.03 }}
          transition={{ duration: 0.7, ease: "easeOut" }}
        >
          <div className="sv-empty-grad" />
        </m.div>
      )}
    </AnimatePresence>
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

  return (
    <AnimatePresence>
      {!isConnecting && waitingForFirstFrame && (
        <m.div
          className="sv-warm"
          role="status"
          aria-live="polite"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.28 }}
        >
          <MotionSpinner className="sv-warm-spin" size={34} label="Preparing stream" />
          <p className="sv-warm-text">{t("stream.stats.waitingForVideo")}</p>
        </m.div>
      )}
    </AnimatePresence>
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
