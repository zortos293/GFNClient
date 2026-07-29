import { useCallback, useEffect, useState } from "react";
import type { ClipboardEvent, JSX, KeyboardEvent } from "react";
import { normalizeShortcut, shortcutFromKeyboardEvent } from "../../../shortcuts";
import { getShortcutConflictError } from "../streamRuntimeHelpers";

export interface StreamShortcutBindings {
  toggleStats: string;
  togglePointerLock: string;
  toggleFullscreen: string;
  stopStream: string;
  toggleAntiAfk: string;
  toggleMicrophone?: string;
  screenshot: string;
  recording: string;
}

interface UseStreamQuickMenuShortcutsOptions {
  shortcuts: StreamShortcutBindings;
  isMacClient: boolean;
  onScreenshotShortcutChange: (value: string) => void;
  onRecordingShortcutChange: (value: string) => void;
}

export function useStreamQuickMenuShortcuts({
  shortcuts,
  isMacClient,
  onScreenshotShortcutChange,
  onRecordingShortcutChange,
}: UseStreamQuickMenuShortcutsOptions) {
  const [screenshotShortcutInput, setScreenshotShortcutInput] = useState(shortcuts.screenshot);
  const [screenshotShortcutError, setScreenshotShortcutError] = useState<string | null>(null);
  const [recordingShortcutInput, setRecordingShortcutInput] = useState(shortcuts.recording);
  const [recordingShortcutError, setRecordingShortcutError] = useState<string | null>(null);

  useEffect(() => {
    setScreenshotShortcutInput(shortcuts.screenshot);
    setScreenshotShortcutError(null);
  }, [shortcuts.screenshot]);

  useEffect(() => {
    setRecordingShortcutInput(shortcuts.recording);
    setRecordingShortcutError(null);
  }, [shortcuts.recording]);

  const getScreenshotShortcutError = useCallback((rawValue: string): string | null => {
    return getShortcutConflictError(rawValue, [
      shortcuts.toggleStats,
      shortcuts.togglePointerLock,
      shortcuts.stopStream,
      shortcuts.toggleAntiAfk,
      shortcuts.toggleMicrophone,
      shortcuts.recording,
      ...(isMacClient ? ["Meta+G"] : ["Ctrl+G", "Ctrl+Shift+G"]),
    ]);
  }, [
    isMacClient,
    shortcuts.recording,
    shortcuts.stopStream,
    shortcuts.toggleAntiAfk,
    shortcuts.toggleMicrophone,
    shortcuts.togglePointerLock,
    shortcuts.toggleStats,
  ]);

  const getRecordingShortcutError = useCallback((rawValue: string): string | null => {
    return getShortcutConflictError(rawValue, [
      shortcuts.toggleStats,
      shortcuts.togglePointerLock,
      shortcuts.stopStream,
      shortcuts.toggleAntiAfk,
      shortcuts.toggleMicrophone,
      shortcuts.screenshot,
      ...(isMacClient ? ["Meta+G"] : ["Ctrl+G", "Ctrl+Shift+G"]),
    ]);
  }, [
    isMacClient,
    shortcuts.screenshot,
    shortcuts.stopStream,
    shortcuts.toggleAntiAfk,
    shortcuts.toggleMicrophone,
    shortcuts.togglePointerLock,
    shortcuts.toggleStats,
  ]);

  const applyScreenshotShortcutFromCapture = useCallback((canonical: string) => {
    const error = getScreenshotShortcutError(canonical);
    if (error) {
      setScreenshotShortcutError(error);
      return;
    }
    const normalized = normalizeShortcut(canonical.trim());
    if (!normalized.valid) {
      setScreenshotShortcutError("Invalid shortcut format.");
      return;
    }
    setScreenshotShortcutError(null);
    setScreenshotShortcutInput(normalized.canonical);
    if (normalized.canonical !== shortcuts.screenshot) {
      onScreenshotShortcutChange(normalized.canonical);
    }
  }, [getScreenshotShortcutError, onScreenshotShortcutChange, shortcuts.screenshot]);

  const applyRecordingShortcutFromCapture = useCallback((canonical: string) => {
    const error = getRecordingShortcutError(canonical);
    if (error) {
      setRecordingShortcutError(error);
      return;
    }
    const normalized = normalizeShortcut(canonical.trim());
    if (!normalized.valid) {
      setRecordingShortcutError("Invalid shortcut format.");
      return;
    }
    setRecordingShortcutError(null);
    setRecordingShortcutInput(normalized.canonical);
    if (normalized.canonical !== shortcuts.recording) {
      onRecordingShortcutChange(normalized.canonical);
    }
  }, [getRecordingShortcutError, onRecordingShortcutChange, shortcuts.recording]);

  const handleScreenshotShortcutKeyDown = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.currentTarget.blur();
      return;
    }
    if (event.key === "Enter" || event.key === "Tab") {
      return;
    }
    const captured = shortcutFromKeyboardEvent(event.nativeEvent);
    if (!captured) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    applyScreenshotShortcutFromCapture(captured);
  };

  const handleRecordingShortcutKeyDown = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.currentTarget.blur();
      return;
    }
    if (event.key === "Enter" || event.key === "Tab") {
      return;
    }
    const captured = shortcutFromKeyboardEvent(event.nativeEvent);
    if (!captured) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    applyRecordingShortcutFromCapture(captured);
  };

  const handleScreenshotShortcutPaste = (event: ClipboardEvent<HTMLInputElement>): void => {
    const text = event.clipboardData.getData("text/plain").trim();
    if (!text) {
      return;
    }
    event.preventDefault();
    applyScreenshotShortcutFromCapture(text);
  };

  const handleRecordingShortcutPaste = (event: ClipboardEvent<HTMLInputElement>): void => {
    const text = event.clipboardData.getData("text/plain").trim();
    if (!text) {
      return;
    }
    event.preventDefault();
    applyRecordingShortcutFromCapture(text);
  };

  return {
    screenshotShortcutInput,
    setScreenshotShortcutInput,
    screenshotShortcutError,
    setScreenshotShortcutError,
    recordingShortcutInput,
    setRecordingShortcutInput,
    recordingShortcutError,
    setRecordingShortcutError,
    getScreenshotShortcutError,
    getRecordingShortcutError,
    handleScreenshotShortcutKeyDown,
    handleRecordingShortcutKeyDown,
    handleScreenshotShortcutPaste,
    handleRecordingShortcutPaste,
  };
}

interface StreamQuickMenuShortcutsPageProps extends UseStreamQuickMenuShortcutsOptions {
  sidebarToggleShortcutDisplay: string;
  controllerSidebarShortcutDisplay: string;
  editor: ReturnType<typeof useStreamQuickMenuShortcuts>;
}

export function StreamQuickMenuShortcutsPage({
  shortcuts,
  sidebarToggleShortcutDisplay,
  controllerSidebarShortcutDisplay,
  onScreenshotShortcutChange,
  onRecordingShortcutChange,
  editor,
}: StreamQuickMenuShortcutsPageProps): JSX.Element {
  const {
    screenshotShortcutInput,
    setScreenshotShortcutInput,
    screenshotShortcutError,
    setScreenshotShortcutError,
    recordingShortcutInput,
    setRecordingShortcutInput,
    recordingShortcutError,
    setRecordingShortcutError,
    getScreenshotShortcutError,
    getRecordingShortcutError,
    handleScreenshotShortcutKeyDown,
    handleRecordingShortcutKeyDown,
    handleScreenshotShortcutPaste,
    handleRecordingShortcutPaste,
  } = editor;

  return (
    <div className="sidebar-page" role="tabpanel">
      <section className="sidebar-section">
        <div className="sidebar-section-header">
          <span>Shortcut Bindings</span>
          <span className="sidebar-section-sub">Edit screenshot keybind here</span>
        </div>
        <div className="sidebar-row sidebar-row--column">
          <div className="sidebar-row-top">
            <span className="sidebar-label">Screenshot Shortcut</span>
          </div>
          <input
            type="text"
            name="screenshot-shortcut"
            aria-label="Screenshot shortcut"
            className={`settings-text-input settings-shortcut-input sidebar-shortcut-input ${screenshotShortcutError ? "error" : ""}`}
            value={screenshotShortcutInput}
            readOnly
            onFocus={(event) => event.target.select()}
            onPaste={handleScreenshotShortcutPaste}
            onBlur={() => {
              const error = getScreenshotShortcutError(screenshotShortcutInput);
              if (error) {
                setScreenshotShortcutError(error);
                return;
              }
              const normalized = normalizeShortcut(screenshotShortcutInput.trim());
              if (!normalized.valid) {
                setScreenshotShortcutError("Invalid shortcut format.");
                return;
              }
              setScreenshotShortcutError(null);
              setScreenshotShortcutInput(normalized.canonical);
              if (normalized.canonical !== shortcuts.screenshot) {
                onScreenshotShortcutChange(normalized.canonical);
              }
            }}
            onKeyDown={handleScreenshotShortcutKeyDown}
            placeholder="Click, then press a key"
            title="Focus and press the key combination to bind"
            spellCheck={false}
          />
        </div>
        {screenshotShortcutError && (
          <span className="sidebar-hint sidebar-hint--error">{screenshotShortcutError}</span>
        )}
        <div className="sidebar-row sidebar-row--column">
          <div className="sidebar-row-top">
            <span className="sidebar-label">Recording Shortcut</span>
          </div>
          <input
            type="text"
            name="recording-shortcut"
            aria-label="Recording shortcut"
            className={`settings-text-input settings-shortcut-input sidebar-shortcut-input ${recordingShortcutError ? "error" : ""}`}
            value={recordingShortcutInput}
            readOnly
            onFocus={(event) => event.target.select()}
            onPaste={handleRecordingShortcutPaste}
            onBlur={() => {
              const error = getRecordingShortcutError(recordingShortcutInput);
              if (error) {
                setRecordingShortcutError(error);
                return;
              }
              const normalized = normalizeShortcut(recordingShortcutInput.trim());
              if (!normalized.valid) {
                setRecordingShortcutError("Invalid shortcut format.");
                return;
              }
              setRecordingShortcutError(null);
              setRecordingShortcutInput(normalized.canonical);
              if (normalized.canonical !== shortcuts.recording) {
                onRecordingShortcutChange(normalized.canonical);
              }
            }}
            onKeyDown={handleRecordingShortcutKeyDown}
            placeholder="Click, then press a key"
            title="Focus and press the key combination to bind"
            spellCheck={false}
          />
        </div>
        {recordingShortcutError && (
          <span className="sidebar-hint sidebar-hint--error">{recordingShortcutError}</span>
        )}
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label">Toggle Stats</span>
          <span className="settings-value-badge">{shortcuts.toggleStats}</span>
        </div>
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label">Mouse Lock</span>
          <span className="settings-value-badge">{shortcuts.togglePointerLock}</span>
        </div>
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label">Stop Stream</span>
          <span className="settings-value-badge">{shortcuts.stopStream}</span>
        </div>
        {shortcuts.toggleMicrophone && (
          <div className="sidebar-row sidebar-row--aligned">
            <span className="sidebar-label">Toggle Microphone</span>
            <span className="settings-value-badge">{shortcuts.toggleMicrophone}</span>
          </div>
        )}
        <div className="sidebar-row sidebar-row--aligned">
          <span className="sidebar-label">Toggle Sidebar</span>
          <span className="sidebar-shortcut-stack">
            <span className="settings-value-badge">{sidebarToggleShortcutDisplay}</span>
            <span className="settings-value-badge">{controllerSidebarShortcutDisplay}</span>
          </span>
        </div>
      </section>
    </div>
  );
}
