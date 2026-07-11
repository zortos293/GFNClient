import type { JSX } from "react";
import type { StreamDiagnosticsStore } from "../../utils/streamDiagnosticsStore";
import { useStreamDiagnosticsSelector } from "../../utils/streamDiagnosticsStore";

export function StreamTitleBar({
  diagnosticsStore,
  gameTitle,
  platformName,
  PlatformIcon,
  showHints,
}: {
  diagnosticsStore: StreamDiagnosticsStore;
  gameTitle: string;
  platformName: string;
  PlatformIcon: (() => JSX.Element) | null;
  showHints: boolean;
}): JSX.Element | null {
  const hasResolution = useStreamDiagnosticsSelector(
    diagnosticsStore,
    (stats) => stats.nativeRendererActive || stats.resolution !== "",
  );

  if (!hasResolution || !showHints) {
    return null;
  }

  return (
    <div className="sv-title-bar">
      <span className="sv-title-game">{gameTitle}</span>
      {PlatformIcon && (
        <span className="sv-title-platform" title={platformName}>
          <span className="sv-title-platform-icon">
            <PlatformIcon />
          </span>
          <span>{platformName}</span>
        </span>
      )}
    </div>
  );
}
