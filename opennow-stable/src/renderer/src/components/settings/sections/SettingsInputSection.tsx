import { Check } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { keyboardLayoutOptions } from "@shared/gfn";
import { formatShortcutForDisplay, normalizeShortcut, shortcutFromKeyboardEvent } from "../../../shortcuts";
import { useTranslation } from "../../../i18n";
import {
  getShortcutConflictMessage,
  isMac,
  SIDEBAR_TOGGLE_SHORTCUT_RAW,
  SHORTCUT_SETTING_KEYS,
  shortcutDefaults,
  type ShortcutSettingKey,
} from "../settingsFormatters";

export interface SettingsInputSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsInputSection({ settings, showAll, handleChange }: SettingsInputSectionProps): JSX.Element {
  const { t } = useTranslation();
  const [toggleStatsInput, setToggleStatsInput] = useState(settings.shortcutToggleStats);
  const [togglePointerLockInput, setTogglePointerLockInput] = useState(settings.shortcutTogglePointerLock);
  const [toggleFullscreenInput, setToggleFullscreenInput] = useState(settings.shortcutToggleFullscreen);
  const [stopStreamInput, setStopStreamInput] = useState(settings.shortcutStopStream);
  const [toggleAntiAfkInput, setToggleAntiAfkInput] = useState(settings.shortcutToggleAntiAfk);
  const [toggleMicrophoneInput, setToggleMicrophoneInput] = useState(settings.shortcutToggleMicrophone);
  const [screenshotInput, setScreenshotInput] = useState(settings.shortcutScreenshot);
  const [recordingInput, setRecordingInput] = useState(settings.shortcutToggleRecording);
  const [toggleStatsError, setToggleStatsError] = useState<string | null>(null);
  const [togglePointerLockError, setTogglePointerLockError] = useState<string | null>(null);
  const [toggleFullscreenError, setToggleFullscreenError] = useState<string | null>(null);
  const [stopStreamError, setStopStreamError] = useState<string | null>(null);
  const [toggleAntiAfkError, setToggleAntiAfkError] = useState<string | null>(null);
  const [toggleMicrophoneError, setToggleMicrophoneError] = useState<string | null>(null);
  const [screenshotError, setScreenshotError] = useState<string | null>(null);
  const [recordingError, setRecordingError] = useState<string | null>(null);
  const [keyboardLayoutDropdownOpen, setKeyboardLayoutDropdownOpen] = useState(false);
  const keyboardLayoutDropdownRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => { setToggleStatsInput(settings.shortcutToggleStats); }, [settings.shortcutToggleStats]);
  useEffect(() => { setTogglePointerLockInput(settings.shortcutTogglePointerLock); }, [settings.shortcutTogglePointerLock]);
  useEffect(() => { setToggleFullscreenInput(settings.shortcutToggleFullscreen); }, [settings.shortcutToggleFullscreen]);
  useEffect(() => { setStopStreamInput(settings.shortcutStopStream); }, [settings.shortcutStopStream]);
  useEffect(() => { setToggleAntiAfkInput(settings.shortcutToggleAntiAfk); }, [settings.shortcutToggleAntiAfk]);
  useEffect(() => { setToggleMicrophoneInput(settings.shortcutToggleMicrophone); }, [settings.shortcutToggleMicrophone]);
  useEffect(() => { setScreenshotInput(settings.shortcutScreenshot); }, [settings.shortcutScreenshot]);
  useEffect(() => { setRecordingInput(settings.shortcutToggleRecording); }, [settings.shortcutToggleRecording]);

  const selectedKeyboardLayoutName = useMemo(() => {
    return keyboardLayoutOptions.find((option) => option.value === settings.keyboardLayout)?.label ?? "English (US)";
  }, [settings.keyboardLayout]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent): void => {
      const target = event.target as Node;
      if (keyboardLayoutDropdownRef.current && !keyboardLayoutDropdownRef.current.contains(target)) {
        setKeyboardLayoutDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  const handleShortcutBlur = (key: ShortcutSettingKey, rawValue: string): void => {
    const trimmed = rawValue.trim();
    if (!trimmed) {
      const msg = "Shortcut cannot be empty.";
      switch (key) {
        case "shortcutToggleStats": setToggleStatsError(msg); break;
        case "shortcutTogglePointerLock": setTogglePointerLockError(msg); break;
        case "shortcutToggleFullscreen": setToggleFullscreenError(msg); break;
        case "shortcutStopStream": setStopStreamError(msg); break;
        case "shortcutToggleAntiAfk": setToggleAntiAfkError(msg); break;
        case "shortcutToggleMicrophone": setToggleMicrophoneError(msg); break;
        case "shortcutScreenshot": setScreenshotError(msg); break;
        case "shortcutToggleRecording": setRecordingError(msg); break;
      }
      return;
    }

    const normalized = normalizeShortcut(trimmed);
    if (!normalized.valid) {
      const msg = "Invalid shortcut format.";
      switch (key) {
        case "shortcutToggleStats": setToggleStatsError(msg); break;
        case "shortcutTogglePointerLock": setTogglePointerLockError(msg); break;
        case "shortcutToggleFullscreen": setToggleFullscreenError(msg); break;
        case "shortcutStopStream": setStopStreamError(msg); break;
        case "shortcutToggleAntiAfk": setToggleAntiAfkError(msg); break;
        case "shortcutToggleMicrophone": setToggleMicrophoneError(msg); break;
        case "shortcutScreenshot": setScreenshotError(msg); break;
        case "shortcutToggleRecording": setRecordingError(msg); break;
      }
      return;
    }

    const conflict = getShortcutConflictMessage(key, normalized.canonical, settings);
    if (conflict) {
      switch (key) {
        case "shortcutToggleStats": setToggleStatsError(conflict); break;
        case "shortcutTogglePointerLock": setTogglePointerLockError(conflict); break;
        case "shortcutToggleFullscreen": setToggleFullscreenError(conflict); break;
        case "shortcutStopStream": setStopStreamError(conflict); break;
        case "shortcutToggleAntiAfk": setToggleAntiAfkError(conflict); break;
        case "shortcutToggleMicrophone": setToggleMicrophoneError(conflict); break;
        case "shortcutScreenshot": setScreenshotError(conflict); break;
        case "shortcutToggleRecording": setRecordingError(conflict); break;
      }
      return;
    }

    switch (key) {
      case "shortcutToggleStats": setToggleStatsError(null); break;
      case "shortcutTogglePointerLock": setTogglePointerLockError(null); break;
      case "shortcutToggleFullscreen": setToggleFullscreenError(null); break;
      case "shortcutStopStream": setStopStreamError(null); break;
      case "shortcutToggleAntiAfk": setToggleAntiAfkError(null); break;
      case "shortcutToggleMicrophone": setToggleMicrophoneError(null); break;
      case "shortcutScreenshot": setScreenshotError(null); break;
      case "shortcutToggleRecording": setRecordingError(null); break;
    }

    switch (key) {
      case "shortcutToggleStats": setToggleStatsInput(normalized.canonical); break;
      case "shortcutTogglePointerLock": setTogglePointerLockInput(normalized.canonical); break;
      case "shortcutToggleFullscreen": setToggleFullscreenInput(normalized.canonical); break;
      case "shortcutStopStream": setStopStreamInput(normalized.canonical); break;
      case "shortcutToggleAntiAfk": setToggleAntiAfkInput(normalized.canonical); break;
      case "shortcutToggleMicrophone": setToggleMicrophoneInput(normalized.canonical); break;
      case "shortcutScreenshot": setScreenshotInput(normalized.canonical); break;
      case "shortcutToggleRecording": setRecordingInput(normalized.canonical); break;
    }

    if (settings[key] !== normalized.canonical) {
      handleChange(key, normalized.canonical);
    }
  };

  const applyShortcutCapture = (key: ShortcutSettingKey, canonical: string): void => {
    const conflict = getShortcutConflictMessage(key, canonical, settings);
    if (conflict) {
      switch (key) {
        case "shortcutToggleStats": setToggleStatsError(conflict); break;
        case "shortcutTogglePointerLock": setTogglePointerLockError(conflict); break;
        case "shortcutToggleFullscreen": setToggleFullscreenError(conflict); break;
        case "shortcutStopStream": setStopStreamError(conflict); break;
        case "shortcutToggleAntiAfk": setToggleAntiAfkError(conflict); break;
        case "shortcutToggleMicrophone": setToggleMicrophoneError(conflict); break;
        case "shortcutScreenshot": setScreenshotError(conflict); break;
        case "shortcutToggleRecording": setRecordingError(conflict); break;
      }
      return;
    }

    switch (key) {
      case "shortcutToggleStats": setToggleStatsError(null); break;
      case "shortcutTogglePointerLock": setTogglePointerLockError(null); break;
      case "shortcutToggleFullscreen": setToggleFullscreenError(null); break;
      case "shortcutStopStream": setStopStreamError(null); break;
      case "shortcutToggleAntiAfk": setToggleAntiAfkError(null); break;
      case "shortcutToggleMicrophone": setToggleMicrophoneError(null); break;
      case "shortcutScreenshot": setScreenshotError(null); break;
      case "shortcutToggleRecording": setRecordingError(null); break;
    }

    switch (key) {
      case "shortcutToggleStats": setToggleStatsInput(canonical); break;
      case "shortcutTogglePointerLock": setTogglePointerLockInput(canonical); break;
      case "shortcutToggleFullscreen": setToggleFullscreenInput(canonical); break;
      case "shortcutStopStream": setStopStreamInput(canonical); break;
      case "shortcutToggleAntiAfk": setToggleAntiAfkInput(canonical); break;
      case "shortcutToggleMicrophone": setToggleMicrophoneInput(canonical); break;
      case "shortcutScreenshot": setScreenshotInput(canonical); break;
      case "shortcutToggleRecording": setRecordingInput(canonical); break;
    }

    if (settings[key] !== canonical) {
      handleChange(key, canonical);
    }
  };

  const handleShortcutCaptureKeyDown = (key: ShortcutSettingKey, e: React.KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === "Escape") {
      e.preventDefault();
      e.currentTarget.blur();
      return;
    }
    if (e.key === "Enter" || e.key === "Tab") {
      return;
    }

    const captured = shortcutFromKeyboardEvent(e.nativeEvent);
    if (!captured) {
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    applyShortcutCapture(key, captured);
  };

  const handleShortcutPaste = (key: ShortcutSettingKey, e: React.ClipboardEvent<HTMLInputElement>): void => {
    const text = e.clipboardData.getData("text/plain").trim();
    if (!text) {
      return;
    }
    e.preventDefault();
    const normalized = normalizeShortcut(text);
    if (!normalized.valid) {
      const msg = "Invalid shortcut format.";
      switch (key) {
        case "shortcutToggleStats": setToggleStatsError(msg); break;
        case "shortcutTogglePointerLock": setTogglePointerLockError(msg); break;
        case "shortcutToggleFullscreen": setToggleFullscreenError(msg); break;
        case "shortcutStopStream": setStopStreamError(msg); break;
        case "shortcutToggleAntiAfk": setToggleAntiAfkError(msg); break;
        case "shortcutToggleMicrophone": setToggleMicrophoneError(msg); break;
        case "shortcutScreenshot": setScreenshotError(msg); break;
        case "shortcutToggleRecording": setRecordingError(msg); break;
      }
      return;
    }
    applyShortcutCapture(key, normalized.canonical);
  };

  const areShortcutsDefault = useMemo(
    () =>
      settings.shortcutToggleStats === shortcutDefaults.shortcutToggleStats
      && settings.shortcutTogglePointerLock === shortcutDefaults.shortcutTogglePointerLock
      && settings.shortcutToggleFullscreen === shortcutDefaults.shortcutToggleFullscreen
      && settings.shortcutStopStream === shortcutDefaults.shortcutStopStream
      && settings.shortcutToggleAntiAfk === shortcutDefaults.shortcutToggleAntiAfk
      && settings.shortcutToggleMicrophone === shortcutDefaults.shortcutToggleMicrophone
      && settings.shortcutScreenshot === shortcutDefaults.shortcutScreenshot
      && settings.shortcutToggleRecording === shortcutDefaults.shortcutToggleRecording,
    [
      settings.shortcutToggleStats,
      settings.shortcutTogglePointerLock,
      settings.shortcutToggleFullscreen,
      settings.shortcutStopStream,
      settings.shortcutToggleAntiAfk,
      settings.shortcutToggleMicrophone,
      settings.shortcutScreenshot,
      settings.shortcutToggleRecording,
    ]
  );

  const handleResetShortcuts = useCallback(() => {
    setToggleStatsInput(shortcutDefaults.shortcutToggleStats);
    setTogglePointerLockInput(shortcutDefaults.shortcutTogglePointerLock);
    setToggleFullscreenInput(shortcutDefaults.shortcutToggleFullscreen);
    setStopStreamInput(shortcutDefaults.shortcutStopStream);
    setToggleAntiAfkInput(shortcutDefaults.shortcutToggleAntiAfk);
    setToggleMicrophoneInput(shortcutDefaults.shortcutToggleMicrophone);
    setScreenshotInput(shortcutDefaults.shortcutScreenshot);
    setRecordingInput(shortcutDefaults.shortcutToggleRecording);
    setToggleStatsError(null);
    setTogglePointerLockError(null);
    setToggleFullscreenError(null);
    setStopStreamError(null);
    setToggleAntiAfkError(null);
    setToggleMicrophoneError(null);
    setScreenshotError(null);
    setRecordingError(null);

    for (const key of SHORTCUT_SETTING_KEYS) {
      const value = shortcutDefaults[key];
      if (settings[key] !== value) {
        handleChange(key, value);
      }
    }
  }, [handleChange, settings]);


  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.input")}</div>}
      <div className="settings-section-header">
        <h2>{t("settings.input.title")}</h2>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label">{t("settings.input.clipboardPaste")}</label>
          <label className="settings-toggle">
            <input
              type="checkbox"
              checked={settings.clipboardPaste}
              onChange={(e) => handleChange("clipboardPaste", e.target.checked)}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.input.gyroscopeControls")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
                type="checkbox"
                checked={settings.enableGyroscopeControls}
                onChange={(e) => handleChange("enableGyroscopeControls", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">{t("settings.input.gyroscopeControlsHint")}</span>
        </div>

        {isMac && (
          <div className="settings-row settings-row--column">
            <div className="settings-row-top settings-row-top--compact">
              <label className="settings-label settings-label--wrap">
                <span className="settings-label-title">
                  {t("settings.input.steamControllerCompatibilityMode")}
                  <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.experimental")}</span>
                </span>
              </label>
              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={settings.steamControllerCompatibilityMode}
                  onChange={(e) => handleChange("steamControllerCompatibilityMode", e.target.checked)}
                />
                <span className="settings-toggle-track" />
              </label>
            </div>
            <span className="settings-subtle-hint">{t("settings.input.steamControllerCompatibilityModeHint")}</span>
          </div>
        )}

        <div className="settings-row settings-row--column">
          <div className="settings-row-top settings-row-top--compact">
            <label className="settings-label settings-label--wrap">
              <span className="settings-label-title">
                {t("settings.input.nativeCursorOverlay")}
                <span className="settings-inline-badge settings-inline-badge--beta">{t("app.labels.beta")}</span>
              </span>
            </label>
            <label className="settings-toggle">
              <input
                type="checkbox"
                checked={settings.nativeCursorOverlay}
                onChange={(e) => handleChange("nativeCursorOverlay", e.target.checked)}
              />
              <span className="settings-toggle-track" />
            </label>
          </div>
          <span className="settings-subtle-hint">{t("settings.input.nativeCursorOverlayHint")}</span>
        </div>

        <div className="settings-row settings-row--top-aligned">
          <label className="settings-label settings-label--wrap">
            {t("settings.game.keyboardLayout")}
            <span className="settings-hint">{t("settings.input.keyboardLayoutHint")}</span>
          </label>
          <div className="settings-dropdown settings-dropdown--constrained" ref={keyboardLayoutDropdownRef}>
            <button
              type="button"
              className={`settings-dropdown-selected ${keyboardLayoutDropdownOpen ? "open" : ""}`}
              onClick={() => setKeyboardLayoutDropdownOpen((open) => !open)}
            >
              <span className="settings-dropdown-selected-name">{selectedKeyboardLayoutName}</span>
              <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`settings-dropdown-chevron ${keyboardLayoutDropdownOpen ? "flipped" : ""}`}>
                <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
              </svg>
            </button>
            {keyboardLayoutDropdownOpen && (
              <div className="settings-dropdown-menu settings-dropdown-menu--tall">
                {keyboardLayoutOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={`settings-dropdown-item ${settings.keyboardLayout === option.value ? "active" : ""}`}
                    onClick={() => {
                      handleChange("keyboardLayout", option.value);
                      setKeyboardLayoutDropdownOpen(false);
                    }}
                  >
                    <span>{option.label}</span>
                    {settings.keyboardLayout === option.value && <Check size={14} className="settings-dropdown-check" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Mouse Sensitivity */}
        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label">{t("settings.input.mouseSensitivity")}</label>
            <span className="settings-value-badge">{settings.mouseSensitivity.toFixed(2)}x</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <input
              type="range"
              className="settings-slider"
              min={0.1}
              max={4}
              step={0.01}
              value={settings.mouseSensitivity}
              onChange={(e) => handleChange("mouseSensitivity", parseFloat(e.target.value))}
            />
            <input
              type="number"
              className="settings-number-input"
              style={{ width: 80 }}
              min={0.1}
              max={4}
              step={0.01}
              value={Number(settings.mouseSensitivity.toFixed(2))}
              onChange={(e) => {
                const v = parseFloat(e.target.value || "0");
                if (Number.isFinite(v)) handleChange("mouseSensitivity", Math.max(0.1, Math.min(4, v)));
              }}
            />
          </div>
          <span className="settings-subtle-hint">{t("settings.input.mouseSensitivityHint")}</span>
        </div>

        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label">{t("settings.input.mouseAccelerator")}</label>
            <span className="settings-value-badge">{Math.round(settings.mouseAcceleration)}%</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <input
              type="range"
              className="settings-slider"
              min={1}
              max={150}
              step={1}
              value={Math.round(settings.mouseAcceleration)}
              onChange={(e) => handleChange("mouseAcceleration", Math.max(1, Math.min(150, Math.round(Number(e.target.value) || 1))))}
            />
            <input
              type="number"
              className="settings-number-input"
              style={{ width: 80 }}
              min={1}
              max={150}
              step={1}
              value={Math.round(settings.mouseAcceleration)}
              onChange={(e) => {
                const v = Number(e.target.value || "1");
                if (Number.isFinite(v)) {
                  handleChange("mouseAcceleration", Math.max(1, Math.min(150, Math.round(v))));
                }
              }}
            />
          </div>
          <span className="settings-subtle-hint">{t("settings.input.mouseAcceleratorHint")}</span>
        </div>

        {/* Shortcuts */}
        <div className="settings-row settings-row--column">
          <div className="settings-row-top">
            <label className="settings-label">{t("settings.input.shortcuts")}</label>
            <div className="settings-shortcut-actions">
              <span className="settings-value-badge">{t("settings.input.editable")}</span>
              <button
                type="button"
                className="settings-shortcut-reset-btn"
                onClick={handleResetShortcuts}
                disabled={areShortcutsDefault}
              >
                {t("settings.input.resetToDefaults")}
              </button>
            </div>
          </div>

          <div className="settings-shortcut-grid">
        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-toggle-stats-label">{t("settings.input.toggleStats")}</span>
          <input
            type="text"
            id="shortcut-toggle-stats"
            aria-labelledby="shortcut-toggle-stats-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${toggleStatsError ? "error" : ""}`}
            value={toggleStatsInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutToggleStats", toggleStatsInput)}
            onPaste={(e) => handleShortcutPaste("shortcutToggleStats", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutToggleStats", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-pointer-lock-label">{t("settings.input.togglePointerLock")}</span>
          <input
            type="text"
            id="shortcut-pointer-lock"
            aria-labelledby="shortcut-pointer-lock-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${togglePointerLockError ? "error" : ""}`}
            value={togglePointerLockInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutTogglePointerLock", togglePointerLockInput)}
            onPaste={(e) => handleShortcutPaste("shortcutTogglePointerLock", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutTogglePointerLock", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-fullscreen-label">{t("settings.input.toggleFullscreen")}</span>
          <input
            type="text"
            id="shortcut-fullscreen"
            aria-labelledby="shortcut-fullscreen-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${toggleFullscreenError ? "error" : ""}`}
            value={toggleFullscreenInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutToggleFullscreen", toggleFullscreenInput)}
            onPaste={(e) => handleShortcutPaste("shortcutToggleFullscreen", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutToggleFullscreen", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-stop-stream-label">{t("settings.input.stopStream")}</span>
          <input
            type="text"
            id="shortcut-stop-stream"
            aria-labelledby="shortcut-stop-stream-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${stopStreamError ? "error" : ""}`}
            value={stopStreamInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutStopStream", stopStreamInput)}
            onPaste={(e) => handleShortcutPaste("shortcutStopStream", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutStopStream", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-anti-afk-label">{t("settings.input.toggleAntiAfk")}</span>
          <input
            type="text"
            id="shortcut-anti-afk"
            aria-labelledby="shortcut-anti-afk-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${toggleAntiAfkError ? "error" : ""}`}
            value={toggleAntiAfkInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutToggleAntiAfk", toggleAntiAfkInput)}
            onPaste={(e) => handleShortcutPaste("shortcutToggleAntiAfk", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutToggleAntiAfk", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-mic-label">{t("settings.input.toggleMicrophone")}</span>
          <input
            type="text"
            id="shortcut-mic"
            aria-labelledby="shortcut-mic-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${toggleMicrophoneError ? "error" : ""}`}
            value={toggleMicrophoneInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutToggleMicrophone", toggleMicrophoneInput)}
            onPaste={(e) => handleShortcutPaste("shortcutToggleMicrophone", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutToggleMicrophone", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-screenshot-label">{t("settings.input.screenshot")}</span>
          <input
            type="text"
            id="shortcut-screenshot"
            aria-labelledby="shortcut-screenshot-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${screenshotError ? "error" : ""}`}
            value={screenshotInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutScreenshot", screenshotInput)}
            onPaste={(e) => handleShortcutPaste("shortcutScreenshot", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutScreenshot", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-recording-label">{t("settings.input.recording")}</span>
          <input
            type="text"
            id="shortcut-recording"
            aria-labelledby="shortcut-recording-label"
            readOnly
            className={`settings-text-input settings-shortcut-input ${recordingError ? "error" : ""}`}
            value={recordingInput}
            onFocus={(e) => e.target.select()}
            onBlur={() => handleShortcutBlur("shortcutToggleRecording", recordingInput)}
            onPaste={(e) => handleShortcutPaste("shortcutToggleRecording", e)}
            onKeyDown={(e) => handleShortcutCaptureKeyDown("shortcutToggleRecording", e)}
            placeholder={t("stream.shortcuts.clickHereThenPress")}
            title={t("stream.shortcuts.focusAndPress")}
            spellCheck={false}
          />
        </div>

        <div className="settings-shortcut-row">
          <span className="settings-shortcut-label" id="shortcut-sidebar-label">{t("settings.input.toggleStreamSidebar")}</span>
          <input
            type="text"
            id="shortcut-sidebar"
            aria-labelledby="shortcut-sidebar-label"
            value={formatShortcutForDisplay(SIDEBAR_TOGGLE_SHORTCUT_RAW, isMac)}
            className="settings-text-input settings-shortcut-input settings-shortcut-input--static"
            readOnly
            tabIndex={-1}
          />
        </div>
      </div>

      {(toggleStatsError || togglePointerLockError || toggleFullscreenError || stopStreamError || toggleAntiAfkError || toggleMicrophoneError || screenshotError || recordingError) && (
        <span className="settings-input-hint">
          {toggleStatsError
            || togglePointerLockError
            || toggleFullscreenError
            || stopStreamError
            || toggleAntiAfkError
            || toggleMicrophoneError
            || screenshotError
            || recordingError}
        </span>
      )}

      {!toggleStatsError && !togglePointerLockError && !toggleFullscreenError && !stopStreamError && !toggleAntiAfkError && !toggleMicrophoneError && !screenshotError && !recordingError && (
        <span className="settings-shortcut-hint">
          {t("settings.input.shortcutHint", {
            examples: t("stream.shortcuts.examples"),
            fullscreen: formatShortcutForDisplay(settings.shortcutToggleFullscreen, isMac),
            stop: formatShortcutForDisplay(settings.shortcutStopStream, isMac),
            mic: formatShortcutForDisplay(settings.shortcutToggleMicrophone, isMac),
            screenshot: formatShortcutForDisplay(settings.shortcutScreenshot, isMac),
            recording: formatShortcutForDisplay(settings.shortcutToggleRecording, isMac),
          })}
        </span>
      )}
        </div>
      </div>
    </section>
  );
}
