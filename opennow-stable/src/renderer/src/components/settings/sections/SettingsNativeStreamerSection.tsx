import { AlertTriangle, Cpu, ExternalLink, Keyboard, Monitor, RefreshCcw } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type JSX } from "react";
import { m } from "motion/react";
import type { NativeStreamerStatus, Settings } from "@shared/gfn";
import {
  createUnsupportedNativeStreamerStatus,
  isNativeDirectXBackendSupported,
  isNativeExternalRendererSupported,
  isNativeStreamerSupportedPlatform,
  NATIVE_STREAMER_UNSUPPORTED_PLATFORM_MESSAGE,
} from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import {
  formatGstreamerRuntimeLabel,
  formatNativeVideoBackendName,
  getAvailableNativeCodecLabels,
  getGstreamerRuntimeBadgeClass,
  nativeVideoBackendOptions,
  NATIVE_STREAMER_ENABLE_PROMPT_EXIT_MS,
} from "../settingsFormatters";
import { dialogMotion, overlayMotion } from "../../MotionProvider";
import { MotionSpinner } from "../../MotionSpinner";

const nativePlatformHint = `${navigator.platform} ${navigator.userAgent}`;
const isNativeStreamerPlatform = isNativeStreamerSupportedPlatform(nativePlatformHint);
const supportsNativeExternalRenderer = isNativeExternalRendererSupported(nativePlatformHint);
const supportsNativeDirectXBackend = isNativeDirectXBackendSupported(nativePlatformHint);

export interface SettingsNativeStreamerSectionProps {
  settings: Settings;
  showAll: boolean;
  showSection?: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

export function SettingsNativeStreamerSection({
  settings,
  showAll,
  showSection = true,
  handleChange,
  onBlockingOverlayChange,
}: SettingsNativeStreamerSectionProps): JSX.Element {
  const { t } = useTranslation();
  const [nativeStreamerStatus, setNativeStreamerStatus] = useState<NativeStreamerStatus | null>(null);
  const [nativeStreamerStatusLoading, setNativeStreamerStatusLoading] = useState(false);
  const [nativeStreamerEnablePromptOpen, setNativeStreamerEnablePromptOpen] = useState(false);
  const [nativeStreamerEnablePromptClosing, setNativeStreamerEnablePromptClosing] = useState(false);
  const nativeStreamerEnablePromptRef = useRef<HTMLDivElement | null>(null);
  const nativeStreamerEnablePromptConfirmRef = useRef<HTMLButtonElement | null>(null);
  const nativeStreamerEnablePromptPreviousFocusRef = useRef<HTMLElement | null>(null);
  const nativeStreamerEnablePromptCloseTimerRef = useRef<number | null>(null);
  const nativeStreamerEnablePromptVisible =
    nativeStreamerEnablePromptOpen || nativeStreamerEnablePromptClosing;

  const refreshNativeStreamerStatus = useCallback(async () => {
    if (!isNativeStreamerPlatform) {
      setNativeStreamerStatus(createUnsupportedNativeStreamerStatus());
      setNativeStreamerStatusLoading(false);
      return;
    }

    setNativeStreamerStatusLoading(true);
    try {
      setNativeStreamerStatus(await window.openNow.getNativeStreamerStatus());
    } catch (error) {
      console.warn("[Settings] Failed to detect native streamer:", error);
      setNativeStreamerStatus({
        detected: false,
        gstreamerAvailable: false,
        supportsOfferAnswer: false,
        gstreamerRuntime: {
          source: "unknown",
          bundled: false,
          message: "GStreamer runtime could not be checked.",
        },
        message: "Native streamer status could not be checked.",
      });
    } finally {
      setNativeStreamerStatusLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshNativeStreamerStatus();
  }, [refreshNativeStreamerStatus]);

  useEffect(() => {
    onBlockingOverlayChange?.(nativeStreamerEnablePromptVisible);
    return () => onBlockingOverlayChange?.(false);
  }, [nativeStreamerEnablePromptVisible, onBlockingOverlayChange]);

  const openNativeStreamerEnablePrompt = useCallback((): void => {
    if (nativeStreamerEnablePromptCloseTimerRef.current !== null) {
      window.clearTimeout(nativeStreamerEnablePromptCloseTimerRef.current);
      nativeStreamerEnablePromptCloseTimerRef.current = null;
    }

    setNativeStreamerEnablePromptClosing(false);
    setNativeStreamerEnablePromptOpen(true);
  }, []);

  const closeNativeStreamerEnablePrompt = useCallback((): void => {
    if (nativeStreamerEnablePromptCloseTimerRef.current !== null) {
      return;
    }

    setNativeStreamerEnablePromptOpen(false);
    setNativeStreamerEnablePromptClosing(true);
    nativeStreamerEnablePromptCloseTimerRef.current = window.setTimeout(() => {
      nativeStreamerEnablePromptCloseTimerRef.current = null;
      setNativeStreamerEnablePromptClosing(false);
    }, NATIVE_STREAMER_ENABLE_PROMPT_EXIT_MS);
  }, []);

  const confirmNativeStreamerEnablePrompt = useCallback((): void => {
    handleChange("streamClientMode", "native");
    closeNativeStreamerEnablePrompt();
  }, [closeNativeStreamerEnablePrompt, handleChange]);

  const handleNativeStreamerToggleChange = useCallback((checked: boolean): void => {
    if (!checked) {
      handleChange("streamClientMode", "web");
      return;
    }

    if (settings.streamClientMode === "native") {
      return;
    }

    openNativeStreamerEnablePrompt();
  }, [handleChange, openNativeStreamerEnablePrompt, settings.streamClientMode]);

  const setNativeFramePacing = useCallback((mode: "low-latency" | "smooth") => {
    if (mode === "low-latency") {
      handleChange("enableCloudGsync", false);
      handleChange("nativeCloudGsyncMode", "disabled");
      handleChange("nativeD3dFullscreenMode", "disabled");
      return;
    }

    handleChange("enableCloudGsync", true);
    handleChange("nativeCloudGsyncMode", "auto");
    handleChange("nativeD3dFullscreenMode", "auto");
  }, [handleChange]);

  useEffect(() => {
    return () => {
      if (nativeStreamerEnablePromptCloseTimerRef.current !== null) {
        window.clearTimeout(nativeStreamerEnablePromptCloseTimerRef.current);
        nativeStreamerEnablePromptCloseTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!nativeStreamerEnablePromptVisible) {
      return;
    }

    nativeStreamerEnablePromptPreviousFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const getFocusableElements = (): HTMLElement[] => {
      const dialog = nativeStreamerEnablePromptRef.current;
      if (!dialog) {
        return [];
      }

      return Array.from(
        dialog.querySelectorAll<HTMLElement>(
          [
            "a[href]",
            "button:not([disabled])",
            "input:not([disabled])",
            "select:not([disabled])",
            "textarea:not([disabled])",
            '[tabindex]:not([tabindex="-1"])',
          ].join(","),
        ),
      ).filter((element) => element.tabIndex >= 0 && element.getAttribute("aria-hidden") !== "true");
    };

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeNativeStreamerEnablePrompt();
        return;
      }

      if (event.key !== "Tab") {
        return;
      }

      const dialog = nativeStreamerEnablePromptRef.current;
      const focusableElements = getFocusableElements();
      if (!dialog || focusableElements.length === 0) {
        event.preventDefault();
        dialog?.focus({ preventScroll: true });
        return;
      }

      const activeElement = document.activeElement;
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const focusIsOnDialog = activeElement === dialog;

      if (event.shiftKey && (focusIsOnDialog || activeElement === firstElement || !dialog.contains(activeElement))) {
        event.preventDefault();
        lastElement.focus({ preventScroll: true });
        return;
      }

      if (!event.shiftKey && (focusIsOnDialog || activeElement === lastElement || !dialog.contains(activeElement))) {
        event.preventDefault();
        firstElement.focus({ preventScroll: true });
      }
    };

    const focusFrame = window.requestAnimationFrame(() => {
      nativeStreamerEnablePromptConfirmRef.current?.focus({ preventScroll: true });
    });

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown);

      const previousFocus = nativeStreamerEnablePromptPreviousFocusRef.current;
      nativeStreamerEnablePromptPreviousFocusRef.current = null;
      if (previousFocus?.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
    };
  }, [closeNativeStreamerEnablePrompt, nativeStreamerEnablePromptVisible]);

  return (
    <>
      {showSection && (
      <section className="settings-section">
        {showAll && <div className="settings-section-context">{t("settings.sections.nativeStreamer")}</div>}
        <div className="settings-section-header">
          <h2>{t("settings.nativeStreamer.title")}</h2>
        </div>
        <div className="settings-rows">
          {!isNativeStreamerPlatform ? (
            <div className="settings-row settings-row--column">
              <div className="settings-row-top settings-row-top--compact">
                <label className="settings-label settings-label--wrap">
                  <span className="settings-label-title">
                    {t("settings.nativeStreamer.nativeStreaming")}
                    <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span>
                  </span>
                </label>
              </div>
              <span className="settings-input-hint">
                {NATIVE_STREAMER_UNSUPPORTED_PLATFORM_MESSAGE}
              </span>
            </div>
          ) : (
            <>
              <div className="settings-row settings-row--column">
                <div className="settings-row-top settings-row-top--compact">
                  <label className="settings-label settings-label--wrap">
                    <span className="settings-label-title">
                      {t("settings.nativeStreamer.nativeStreaming")}
                      <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span>
                    </span>
                  </label>
                  <label className="settings-toggle">
                    <input
                      type="checkbox"
                      checked={settings.streamClientMode === "native"}
                      onChange={(e) => handleNativeStreamerToggleChange(e.target.checked)}
                    />
                    <span className="settings-toggle-track" />
                  </label>
                </div>
                <span className="settings-subtle-hint">
                  {t("settings.nativeStreamer.nativeStreamingHint")}
                </span>
                <div className="settings-chip-row">
                  <a className="settings-chip" href="https://github.com/OpenCloudGaming/OpenNOW/issues" target="_blank" rel="noreferrer">
                    <span>{t("settings.nativeStreamer.reportOnGithubIssues")}</span>
                    <ExternalLink size={13} />
                  </a>
                  <a className="settings-chip" href="https://discord.gg/8EJYaJcNfD" target="_blank" rel="noreferrer">
                    <span>{t("settings.nativeStreamer.reportOnDiscord")}</span>
                    <ExternalLink size={13} />
                  </a>
                </div>
              </div>

              <div className="settings-row">
                <label className="settings-label">
                  {t("settings.nativeStreamer.showNativeStreamerStats")}
                  <span className="settings-hint">{t("settings.nativeStreamer.showNativeStreamerStatsHint")}</span>
                </label>
                <label className="settings-toggle">
                  <input
                    type="checkbox"
                    checked={settings.showNativeStreamerStats}
                    onChange={(e) => handleChange("showNativeStreamerStats", e.target.checked)}
                  />
                  <span className="settings-toggle-track" />
                </label>
              </div>

              <div className="settings-row settings-row--column">
                <div className="settings-row-top settings-row-top--compact">
                  <label className="settings-label settings-label--wrap">
                    <span className="settings-label-title">{t("settings.nativeStreamer.streamerStatus")}</span>
                  </label>
                  <button
                    type="button"
                    className="settings-icon-button"
                    onClick={() => void refreshNativeStreamerStatus()}
                    disabled={nativeStreamerStatusLoading}
                    title={t("settings.nativeStreamer.checkNativeStreamer")}
                    aria-label={t("settings.nativeStreamer.checkNativeStreamer")}
                  >
                    {nativeStreamerStatusLoading ? <MotionSpinner size={15} label="Checking streamer status" /> : <RefreshCcw size={15} />}
                  </button>
                </div>
                <div className="settings-chip-row">
                  <span
                    className={`settings-inline-badge ${
                      nativeStreamerStatusLoading
                        ? "settings-inline-badge--codec-testing"
                        : nativeStreamerStatus?.gstreamerAvailable
                          ? "settings-inline-badge--codec-gpu"
                          : "settings-inline-badge--updater-error"
                    }`}
                  >
                    {nativeStreamerStatusLoading
                      ? t("app.status.checking")
                      : nativeStreamerStatus?.gstreamerAvailable
                        ? t("settings.nativeStreamer.gstreamerReady")
                        : t("settings.nativeStreamer.notReady")}
                  </span>
                </div>
                <span className="settings-subtle-hint">
                  {nativeStreamerStatus?.message ?? t("settings.nativeStreamer.statusDefaultHint")}
                </span>
              </div>

              <div className="settings-row settings-row--column">
                <label className="settings-label">{t("settings.nativeStreamer.gstreamerRuntime")}</label>
                <div className="settings-chip-row">
                  <span className={`settings-inline-badge ${getGstreamerRuntimeBadgeClass(nativeStreamerStatus)}`}>
                    {formatGstreamerRuntimeLabel(nativeStreamerStatus)}
                  </span>
                  {nativeStreamerStatus?.gstreamerRuntime.path ? (
                    <span className="settings-inline-badge settings-inline-badge--codec">
                      {t("settings.nativeStreamer.bundledPathDetected")}
                    </span>
                  ) : null}
                </div>
                <span className="settings-subtle-hint">
                  {nativeStreamerStatus?.gstreamerRuntime.message ?? t("settings.nativeStreamer.runtimeDefaultHint")}
                </span>
                {!nativeStreamerStatus?.gstreamerAvailable && nativeStreamerStatus?.gstreamerRuntime.installInstructions?.length ? (
                  <div className="settings-install-steps">
                    <span className="settings-subtle-hint">
                      {t("settings.nativeStreamer.linuxRuntimeHint")}
                    </span>
                    {nativeStreamerStatus.gstreamerRuntime.installInstructions.map((instruction) => (
                      <div key={instruction.distro} className="settings-install-step">
                        <span className="settings-install-step-title">{instruction.distro}</span>
                        <code>{instruction.command}</code>
                        {instruction.note ? <span className="settings-subtle-hint">{instruction.note}</span> : null}
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>

              <div className="settings-row settings-row--column">
                <label className="settings-label">{t("settings.nativeStreamer.videoPath")}</label>
                <div className="settings-chip-row">
                  <span
                    className={`settings-inline-badge ${
                      nativeStreamerStatus?.activeVideoBackend?.available
                        ? nativeStreamerStatus.activeVideoBackend.backend === "software"
                          ? "settings-inline-badge--codec-testing"
                          : "settings-inline-badge--codec-gpu"
                        : "settings-inline-badge--updater-error"
                    }`}
                  >
                    {formatNativeVideoBackendName(nativeStreamerStatus?.activeVideoBackend?.backend)}
                  </span>
                  {getAvailableNativeCodecLabels(nativeStreamerStatus?.activeVideoBackend).map((codec) => (
                    <span key={codec} className="settings-inline-badge settings-inline-badge--codec">
                      {codec}
                    </span>
                  ))}
                </div>
                <span className="settings-subtle-hint">
                  {nativeStreamerStatus?.gstreamerAvailable
                    ? `${nativeStreamerStatus.codecSummary ?? t("settings.nativeStreamer.codecSupportUnknown")}. ${nativeStreamerStatus.zeroCopySummary ?? t("settings.nativeStreamer.memoryPathUnknown")}.`
                    : nativeStreamerStatus?.activeVideoBackend?.reason
                      ?? t("settings.nativeStreamer.videoPathDefaultHint")}
                </span>
              </div>

              {supportsNativeDirectXBackend && <div className="settings-row settings-row--column">
                <label className="settings-label">{t("settings.nativeStreamer.directxBackend")}</label>
                <div className="settings-chip-row">
                  {nativeVideoBackendOptions.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`settings-chip ${settings.nativeVideoBackend === option.value ? "active" : ""}`}
                      onClick={() => handleChange("nativeVideoBackend", option.value)}
                      title={option.description}
                    >
                      <span>{option.label}</span>
                    </button>
                  ))}
                </div>
                <span className="settings-subtle-hint">
                  {t("settings.nativeStreamer.directxBackendHint")}
                </span>
              </div>}

              <div className="settings-row settings-row--column">
                <label className="settings-label">{t("settings.nativeStreamer.framePacing")}</label>
                <div className="settings-chip-row">
                  <button
                    type="button"
                    className={`settings-chip ${!settings.enableCloudGsync ? "active" : ""}`}
                    onClick={() => setNativeFramePacing("low-latency")}
                  >
                    <span>{t("settings.nativeStreamer.lowestLatency")}</span>
                  </button>
                  <button
                    type="button"
                    className={`settings-chip ${settings.enableCloudGsync ? "active" : ""}`}
                    onClick={() => setNativeFramePacing("smooth")}
                  >
                    <span>{t("settings.nativeStreamer.smoothGsync")}</span>
                  </button>
                </div>
                <span className="settings-subtle-hint">
                  {t("settings.nativeStreamer.framePacingHint")}
                </span>
              </div>

              {supportsNativeExternalRenderer ? (
                <div className="settings-row settings-row--column">
                  <label className="settings-label">{t("settings.nativeStreamer.renderMode")}</label>
                  <div className="settings-chip-row">
                    <button type="button" className={`settings-chip ${!settings.nativeExternalRenderer ? "active" : ""}`} onClick={() => handleChange("nativeExternalRenderer", false)}>
                      <span>{t("settings.nativeStreamer.renderModeInternal")}</span>
                    </button>
                    <button type="button" className={`settings-chip ${settings.nativeExternalRenderer ? "active" : ""}`} onClick={() => handleChange("nativeExternalRenderer", true)}>
                      <span>{t("settings.nativeStreamer.renderModeExternal")}</span>
                    </button>
                  </div>
                  <span className="settings-subtle-hint">{t("settings.nativeStreamer.renderModeHint")}</span>
                </div>
              ) : (
                <div className="settings-row settings-row--column">
                  <label className="settings-label">{t("settings.nativeStreamer.renderMode")}</label>
                  <div className="settings-chip-row"><span className="settings-inline-badge">{t("settings.nativeStreamer.renderModeInternal")}</span></div>
                  <span className="settings-subtle-hint">{t("settings.nativeStreamer.renderModeInternalOnlyHint")}</span>
                </div>
              )}

            </>
          )}
        </div>
      </section>
      )}
      {nativeStreamerEnablePromptVisible && (
        <m.div
          className={`native-streamer-warning ${nativeStreamerEnablePromptClosing ? "native-streamer-warning--closing" : ""}`}
          role="dialog"
          aria-modal="true"
          aria-labelledby="native-streamer-warning-title"
          aria-describedby="native-streamer-warning-copy"
          initial={overlayMotion.initial}
          animate={nativeStreamerEnablePromptClosing ? overlayMotion.exit : overlayMotion.animate}
          transition={overlayMotion.transition}
        >
          <m.button
            type="button"
            className="native-streamer-warning-backdrop"
            aria-label={t("app.actions.cancel")}
            aria-hidden="true"
            tabIndex={-1}
            onClick={closeNativeStreamerEnablePrompt}
            initial={overlayMotion.initial}
            animate={nativeStreamerEnablePromptClosing ? overlayMotion.exit : overlayMotion.animate}
            transition={overlayMotion.transition}
          />
          <m.div
            ref={nativeStreamerEnablePromptRef}
            className="native-streamer-warning-card"
            tabIndex={-1}
            initial={dialogMotion.initial}
            animate={nativeStreamerEnablePromptClosing ? dialogMotion.exit : dialogMotion.animate}
            transition={dialogMotion.transition}
          >
            <div className="native-streamer-warning-kicker">
              <AlertTriangle size={14} />
              {t("settings.nativeStreamer.enablePromptKicker")}
            </div>
            <h3 id="native-streamer-warning-title" className="native-streamer-warning-title">
              {t("settings.nativeStreamer.enablePromptTitle")}
            </h3>
            <p id="native-streamer-warning-copy" className="native-streamer-warning-text">
              {t("settings.nativeStreamer.enablePromptBody")}
            </p>

            <div className="native-streamer-warning-list">
              <div className="native-streamer-warning-list-item">
                <Cpu size={16} />
                <span>{t("settings.nativeStreamer.enablePromptNewSessions")}</span>
              </div>
              <div className="native-streamer-warning-list-item">
                <Keyboard size={16} />
                <span>{t("settings.nativeStreamer.enablePromptShortcuts")}</span>
              </div>
              <div className="native-streamer-warning-list-item">
                <Monitor size={16} />
                <span>{t("settings.nativeStreamer.enablePromptAltTab")}</span>
              </div>
            </div>

            <div className="native-streamer-warning-actions">
              <button
                type="button"
                className="native-streamer-warning-btn native-streamer-warning-btn--secondary"
                onClick={closeNativeStreamerEnablePrompt}
              >
                {t("settings.nativeStreamer.enablePromptCancel")}
              </button>
              <button
                type="button"
                className="native-streamer-warning-btn native-streamer-warning-btn--primary"
                onClick={confirmNativeStreamerEnablePrompt}
                ref={nativeStreamerEnablePromptConfirmRef}
                autoFocus
              >
                {t("settings.nativeStreamer.enablePromptEnable")}
              </button>
            </div>
            <div className="native-streamer-warning-hint">
              <kbd>Esc</kbd> {t("settings.nativeStreamer.enablePromptEsc")}
            </div>
          </m.div>
        </m.div>
      )}
    </>
  );
}
