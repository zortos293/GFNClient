import type { JSX } from "react";
import {
  Camera,
  Clock3,
  Gamepad2,
  Maximize,
  Mic,
  Minimize,
  MousePointer2,
} from "lucide-react";
import type { SubscriptionInfo } from "@shared/gfn";
import { RemainingPlaytimeIndicator } from "../../ElapsedSessionIndicators";
import { useTranslation } from "../../../i18n";

interface StreamQuickMenuSessionPageProps {
  gameTitle: string;
  platformName: string;
  PlatformIcon: (() => JSX.Element) | null;
  subscriptionInfo: SubscriptionInfo | null;
  sessionStartedAtMs: number | null;
  isStreaming: boolean;
  sessionTimeRemainingText: string | null;
  isFullscreen: boolean;
  isPointerLocked: boolean;
  onToggleFullscreen: () => void;
  onTogglePointerLock: () => void;
  onToggleMicrophone?: () => void;
  onCaptureScreenshot: () => void;
  isSavingScreenshot: boolean;
  screenshotApiAvailable: boolean;
  showSessionTimeRemainingInStatsOverlay: boolean;
  onShowSessionTimeRemainingInStatsOverlayChange: (value: boolean) => void;
  sidebarToggleShortcutDisplay: string;
  controllerSidebarShortcutDisplay: string;
}

export function StreamQuickMenuSessionPage({
  gameTitle,
  platformName,
  PlatformIcon,
  subscriptionInfo,
  sessionStartedAtMs,
  isStreaming,
  sessionTimeRemainingText,
  isFullscreen,
  isPointerLocked,
  onToggleFullscreen,
  onTogglePointerLock,
  onToggleMicrophone,
  onCaptureScreenshot,
  isSavingScreenshot,
  screenshotApiAvailable,
  showSessionTimeRemainingInStatsOverlay,
  onShowSessionTimeRemainingInStatsOverlayChange,
  sidebarToggleShortcutDisplay,
  controllerSidebarShortcutDisplay,
}: StreamQuickMenuSessionPageProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <div className="sidebar-page sidebar-page--session" role="tabpanel">
      <section className="sidebar-session-card" aria-label="Current stream session">
        <div className="sidebar-session-card-head">
          <span className="sidebar-session-kicker">Now streaming</span>
          <strong className="sidebar-session-title">{gameTitle}</strong>
          {PlatformIcon && platformName && (
            <span className="sidebar-session-platform" title={platformName}>
              <span className="sidebar-session-platform-icon"><PlatformIcon /></span>
              <span>{platformName}</span>
            </span>
          )}
        </div>
      </section>
      <section className="sidebar-session-metrics" aria-label="Session time">
        <div className="sidebar-metric">
          <span>Total playtime left</span>
          <RemainingPlaytimeIndicator
            subscriptionInfo={subscriptionInfo}
            startedAtMs={sessionStartedAtMs}
            active={isStreaming}
            className="sidebar-metric-value"
          />
        </div>
        {sessionTimeRemainingText !== null && (
          <div className="sidebar-metric">
            <span>{t("sidebar.sessionTimeRemaining")}</span>
            <strong className="sidebar-metric-value">
              <Clock3 size={14} />
              {sessionTimeRemainingText}
            </strong>
          </div>
        )}
      </section>
      <section className="sidebar-section">
        <div className="sidebar-section-header">
          <span>Session controls</span>
          <span className="sidebar-section-sub">Manage the active stream.</span>
        </div>
        <div className="sidebar-quick-actions">
          <button type="button" className="sidebar-action-card" onClick={onToggleFullscreen}>
            {isFullscreen ? <Minimize size={16} /> : <Maximize size={16} />}
            <span>{isFullscreen ? "Windowed" : "Fullscreen"}</span>
          </button>
          <button type="button" className="sidebar-action-card" onClick={onTogglePointerLock}>
            <MousePointer2 size={16} />
            <span>{isPointerLocked ? "Release mouse" : "Capture mouse"}</span>
          </button>
          {onToggleMicrophone && (
            <button type="button" className="sidebar-action-card" onClick={onToggleMicrophone}>
              <Mic size={16} />
              <span>Toggle mic</span>
            </button>
          )}
          <button
            type="button"
            className="sidebar-action-card"
            onClick={onCaptureScreenshot}
            disabled={isSavingScreenshot || !screenshotApiAvailable}
          >
            <Camera size={16} />
            <span>{isSavingScreenshot ? "Capturing" : "Screenshot"}</span>
          </button>
        </div>
      </section>
      {sessionTimeRemainingText !== null && (
        <label className="sidebar-setting-card sidebar-mini-toggle" tabIndex={0}>
          <span>
            <strong>Show time in stats</strong>
            <small>Keep session time visible in the performance overlay.</small>
          </span>
          <input
            type="checkbox"
            name="show-session-time-in-stats"
            checked={showSessionTimeRemainingInStatsOverlay}
            aria-label={t("sidebar.showSessionTimeRemainingInStatsOverlay")}
            onChange={(event) => onShowSessionTimeRemainingInStatsOverlayChange(event.target.checked)}
          />
          <span className="sidebar-mini-toggle-track" />
        </label>
      )}
      <div className="sidebar-open-shortcuts">
        <span><kbd>{sidebarToggleShortcutDisplay}</kbd> Keyboard</span>
        <span><Gamepad2 size={14} /> {controllerSidebarShortcutDisplay}</span>
      </div>
    </div>
  );
}
