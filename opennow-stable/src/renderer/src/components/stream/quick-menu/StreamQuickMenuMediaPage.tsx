import type { JSX, RefObject } from "react";
import {
  Camera,
  ChevronLeft,
  ChevronRight,
  Circle,
  FolderOpen,
  LoaderCircle,
  Square,
  Trash2,
  Video,
} from "lucide-react";
import {
  DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS,
  MAX_RECORDING_BITRATE_MBPS,
  RECORDING_FPS_OPTIONS,
  RECORDING_RESOLUTION_OPTIONS,
  type RecordingEntry,
  type RecordingFps,
  type RecordingResolution,
  type ScreenshotEntry,
} from "@shared/gfn";
import type { RecordingStatus } from "../../../hooks/useStreamRecorder";
import { useTranslation } from "../../../i18n";
import { SettingRange } from "../../settings/SettingRange";
import { formatElapsed } from "../../../utils/timeFormat";
import { formatFileSize } from "../streamFormatters";

interface StreamQuickMenuMediaPageProps {
  screenshotShortcut: string;
  screenshots: ScreenshotEntry[];
  isSavingScreenshot: boolean;
  screenshotApiAvailable: boolean;
  galleryError: string | null;
  galleryStripRef: RefObject<HTMLDivElement | null>;
  onCaptureScreenshot: () => void;
  onSelectScreenshot: (id: string) => void;
  onScrollGallery: (direction: "left" | "right") => void;
  recordingShortcut: string;
  recordings: RecordingEntry[];
  isRecording: boolean;
  recordingDurationMs: number;
  recordingError: string | null;
  recordingApiAvailable: boolean;
  usedMimeType: string | null;
  recordingStatus: RecordingStatus;
  recordingBitrateMbps: number | null;
  recordingResolution: RecordingResolution;
  recordingFps: RecordingFps;
  onRecordingResolutionChange: (value: RecordingResolution) => void;
  onRecordingFpsChange: (value: RecordingFps) => void;
  onRecordingBitrateMbpsChange: (value: number | null) => void;
  recCarouselRef: RefObject<HTMLDivElement | null>;
  onToggleRecording: () => void;
  onDeleteRecording: (id: string) => void;
  onScrollRecordings: (direction: "left" | "right") => void;
}

export function StreamQuickMenuMediaPage({
  screenshotShortcut,
  screenshots,
  isSavingScreenshot,
  screenshotApiAvailable,
  galleryError,
  galleryStripRef,
  onCaptureScreenshot,
  onSelectScreenshot,
  onScrollGallery,
  recordingShortcut,
  recordings,
  isRecording,
  recordingDurationMs,
  recordingError,
  recordingApiAvailable,
  usedMimeType,
  recordingStatus,
  recordingBitrateMbps,
  recordingResolution,
  recordingFps,
  onRecordingResolutionChange,
  onRecordingFpsChange,
  onRecordingBitrateMbpsChange,
  recCarouselRef,
  onToggleRecording,
  onDeleteRecording,
  onScrollRecordings,
}: StreamQuickMenuMediaPageProps): JSX.Element {
  const { t } = useTranslation();
  const recordingSettingsDisabled = recordingStatus === "starting"
    || recordingStatus === "recording"
    || recordingStatus === "stopping";
  const recordingActionDisabled = !recordingApiAvailable
    || recordingStatus === "starting"
    || recordingStatus === "stopping";
  const recordingActionLabel = recordingStatus === "starting"
    ? t("stream.recordings.starting")
    : recordingStatus === "stopping"
      ? t("stream.recordings.finalizing")
      : isRecording
        ? t("stream.recordings.stop")
        : t("stream.recordings.start");

  return (
    <div className="sidebar-page" role="tabpanel">
      <section className="sidebar-section">
        <div className="sidebar-section-header">
          <span>{t("stream.recordings.settingsTitle")}</span>
          <span className="sidebar-section-sub">{t("stream.recordings.settingsNextRecording")}</span>
        </div>
        <div className="sidebar-row sidebar-row--column">
          <div className="sidebar-row-top">
            <span className="sidebar-label">{t("stream.recordings.resolution")}</span>
            <span className="settings-value-badge">{recordingResolution}</span>
          </div>
          <div className="sidebar-chip-row" role="group" aria-label={t("stream.recordings.resolution")}>
            {RECORDING_RESOLUTION_OPTIONS.map((resolution) => (
              <button
                key={resolution}
                type="button"
                className={`sidebar-chip${recordingResolution === resolution ? " sidebar-chip--active" : ""}`}
                aria-pressed={recordingResolution === resolution}
                disabled={recordingSettingsDisabled}
                onClick={() => onRecordingResolutionChange(resolution)}
              >
                <span>{resolution}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="sidebar-row sidebar-row--column">
          <div className="sidebar-row-top">
            <span className="sidebar-label">{t("stream.recordings.frameRate")}</span>
            <span className="settings-value-badge">{recordingFps} FPS</span>
          </div>
          <div className="sidebar-chip-row" role="group" aria-label={t("stream.recordings.frameRate")}>
            {RECORDING_FPS_OPTIONS.map((fps) => (
              <button
                key={fps}
                type="button"
                className={`sidebar-chip${recordingFps === fps ? " sidebar-chip--active" : ""}`}
                aria-pressed={recordingFps === fps}
                disabled={recordingSettingsDisabled}
                onClick={() => onRecordingFpsChange(fps)}
              >
                <span>{fps}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="sidebar-row sidebar-row--column">
          <div className="sidebar-row-top">
            <label className="sidebar-label" htmlFor="quick-menu-recording-bitrate">
              {t("stream.recordings.bitrate")}
            </label>
            <span className="settings-value-badge">
              {recordingBitrateMbps === null ? t("app.labels.auto") : `${recordingBitrateMbps} Mbps`}
            </span>
          </div>
          <div className="sidebar-chip-row" role="group" aria-label={t("stream.recordings.bitrate")}>
            <button
              type="button"
              className={`sidebar-chip${recordingBitrateMbps === null ? " sidebar-chip--active" : ""}`}
              aria-pressed={recordingBitrateMbps === null}
              disabled={recordingSettingsDisabled}
              onClick={() => onRecordingBitrateMbpsChange(null)}
            >
              <span>{t("app.labels.auto")}</span>
            </button>
            <button
              type="button"
              className={`sidebar-chip${recordingBitrateMbps !== null ? " sidebar-chip--active" : ""}`}
              aria-pressed={recordingBitrateMbps !== null}
              disabled={recordingSettingsDisabled}
              onClick={() => onRecordingBitrateMbpsChange(
                recordingBitrateMbps ?? DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS
              )}
            >
              <span>{t("settings.video.customBitrate")}</span>
            </button>
          </div>
          <SettingRange
            id="quick-menu-recording-bitrate"
            className="settings-slider"
            min={1}
            max={MAX_RECORDING_BITRATE_MBPS}
            step={1}
            value={recordingBitrateMbps ?? DEFAULT_CUSTOM_RECORDING_BITRATE_MBPS}
            disabled={recordingSettingsDisabled || recordingBitrateMbps === null}
            aria-label={t("stream.recordings.bitrate")}
            onPreview={onRecordingBitrateMbpsChange}
            onCommit={onRecordingBitrateMbpsChange}
          />
          <span className="sidebar-hint">{t("stream.recordings.performanceHint")}</span>
        </div>
      </section>
      <div className="sidebar-separator" aria-hidden="true" />
      <section className="sidebar-section">
        <div className="sidebar-section-header">
          <span>Gallery</span>
          <span className="sidebar-section-sub">Screenshot key: {screenshotShortcut}</span>
        </div>
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label">Screenshots</span>
          <button
            type="button"
            className="sidebar-button sidebar-screenshot-button"
            onClick={onCaptureScreenshot}
            disabled={isSavingScreenshot || !screenshotApiAvailable}
          >
            <Camera size={14} />
            <span>{isSavingScreenshot ? "Capturing..." : "Capture"}</span>
          </button>
        </div>
        <div className="sidebar-gallery-row">
          <button
            type="button"
            className="sidebar-gallery-arrow"
            onClick={() => onScrollGallery("left")}
            aria-label="Scroll gallery left"
          >
            <ChevronLeft size={16} />
          </button>
          <div className="sidebar-gallery-strip" ref={galleryStripRef}>
            {screenshots.map((shot) => (
              <button
                key={shot.id}
                type="button"
                className="sidebar-gallery-item"
                onClick={() => onSelectScreenshot(shot.id)}
                title={new Date(shot.createdAtMs).toLocaleString()}
              >
                <img src={shot.dataUrl} alt={`Screenshot ${shot.fileName}`} />
              </button>
            ))}
          </div>
          <button
            type="button"
            className="sidebar-gallery-arrow"
            onClick={() => onScrollGallery("right")}
            aria-label="Scroll gallery right"
          >
            <ChevronRight size={16} />
          </button>
        </div>
        {screenshots.length === 0 && (
          <span className="sidebar-hint">No screenshots yet. Press {screenshotShortcut} to capture one.</span>
        )}
        {galleryError && <span className="sidebar-hint sidebar-hint--error">{galleryError}</span>}
      </section>
      <div className="sidebar-separator" aria-hidden="true" />
      <section className="sidebar-section">
        <div className="sidebar-section-header">
          <span>Recordings</span>
          <span className="sidebar-section-sub">Record key: {recordingShortcut}</span>
        </div>
        {usedMimeType && (
          <span className="sidebar-hint sidebar-hint--codec">
            {t("stream.recordings.codec", { codec: usedMimeType })}
          </span>
        )}
        <span className="sidebar-hint sidebar-hint--codec">
          {t("stream.recordings.activeProfile", {
            resolution: recordingResolution,
            fps: recordingFps,
            bitrate: recordingBitrateMbps === null ? t("app.labels.auto") : `${recordingBitrateMbps} Mbps`,
          })}
        </span>
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label" role="status" aria-live="polite">
            {recordingStatus === "starting"
              ? t("stream.recordings.starting")
              : recordingStatus === "stopping"
                ? t("stream.recordings.finalizing")
                : isRecording
                  ? t("stream.recordings.recording", {
                      duration: formatElapsed(Math.round(recordingDurationMs / 1000)),
                    })
                  : t("stream.recordings.record")}
          </span>
          <button
            type="button"
            className={`sidebar-button sidebar-screenshot-button recording-action recording-action--${recordingStatus}`}
            onClick={onToggleRecording}
            disabled={recordingActionDisabled}
            aria-busy={recordingStatus === "starting" || recordingStatus === "stopping"}
            aria-label={recordingActionLabel}
          >
            {recordingStatus === "starting" || recordingStatus === "stopping"
              ? <LoaderCircle className="recording-action-spinner" size={14} />
              : isRecording
                ? <Square size={14} />
                : <Circle size={14} />}
            <span>{recordingActionLabel}</span>
          </button>
        </div>
        {recordingError && (
          <span className="sidebar-hint sidebar-hint--error" role="alert">{recordingError}</span>
        )}
        {recordings.length === 0 ? (
          <span className="sidebar-hint">No recordings yet. Press {recordingShortcut} to record.</span>
        ) : (
          <div className="sidebar-gallery-row">
            <button
              type="button"
              className="sidebar-gallery-arrow"
              onClick={() => onScrollRecordings("left")}
              aria-label="Scroll recordings left"
            >
              <ChevronLeft size={16} />
            </button>
            <div className="sidebar-rec-strip" ref={recCarouselRef}>
              {recordings.map((recording) => (
                <div key={recording.id} className="sidebar-rec-card">
                  {recording.thumbnailDataUrl ? (
                    <img
                      className="sidebar-rec-card-thumb"
                      src={recording.thumbnailDataUrl}
                      alt=""
                    />
                  ) : (
                    <div className="sidebar-rec-card-thumb sidebar-rec-card-thumb--placeholder">
                      <Video size={20} />
                    </div>
                  )}
                  <div className="sidebar-rec-card-meta">
                    <span className="sidebar-rec-card-title">{recording.gameTitle ?? "Untitled"}</span>
                    <span className="sidebar-rec-card-detail">
                      {formatElapsed(Math.round(recording.durationMs / 1000))} · {formatFileSize(recording.sizeBytes)}
                    </span>
                  </div>
                  <div className="sidebar-rec-card-actions">
                    <button
                      type="button"
                      className="sidebar-rec-card-action"
                      aria-label="Show in folder"
                      title="Show in folder"
                      onClick={() => { void window.openNow.showRecordingInFolder(recording.id); }}
                      disabled={typeof window.openNow?.showRecordingInFolder !== "function"}
                    >
                      <FolderOpen size={11} />
                    </button>
                    <button
                      type="button"
                      className="sidebar-rec-card-action sidebar-rec-card-action--danger"
                      aria-label="Delete recording"
                      title="Delete"
                      onClick={() => onDeleteRecording(recording.id)}
                    >
                      <Trash2 size={11} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <button
              type="button"
              className="sidebar-gallery-arrow"
              onClick={() => onScrollRecordings("right")}
              aria-label="Scroll recordings right"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
