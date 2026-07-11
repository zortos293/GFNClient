import { Check, Mic } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type JSX } from "react";
import type { MicrophoneMode, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { getMicrophonePermissionError, microphoneModeOptions } from "../settingsFormatters";

export interface SettingsAudioSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
}

export function SettingsAudioSection({ settings, showAll, handleChange }: SettingsAudioSectionProps): JSX.Element {
  const { locale, t } = useTranslation();
  const [microphoneDevices, setMicrophoneDevices] = useState<MediaDeviceInfo[]>([]);
  const [microphonePermissionError, setMicrophonePermissionError] = useState<string | null>(null);
  const [microphoneModeDropdownOpen, setMicrophoneModeDropdownOpen] = useState(false);
  const [microphoneDeviceDropdownOpen, setMicrophoneDeviceDropdownOpen] = useState(false);
  const microphoneModeDropdownRef = useRef<HTMLDivElement | null>(null);
  const microphoneDeviceDropdownRef = useRef<HTMLDivElement | null>(null);
  const latestMicrophoneDeviceIdRef = useRef(settings.microphoneDeviceId);

  useEffect(() => {
    latestMicrophoneDeviceIdRef.current = settings.microphoneDeviceId;
  }, [settings.microphoneDeviceId]);

  useEffect(() => {
    if (settings.microphoneMode === "disabled") {
      setMicrophoneDevices([]);
      setMicrophonePermissionError(null);
      return;
    }

    let cancelled = false;

    async function enumerateDevices(): Promise<void> {
      const applyDeviceList = (audioInputs: MediaDeviceInfo[]): void => {
        if (cancelled) {
          return;
        }

        setMicrophoneDevices(audioInputs);
        setMicrophonePermissionError(null);

        if (
          latestMicrophoneDeviceIdRef.current
          && !audioInputs.some((device) => device.deviceId === latestMicrophoneDeviceIdRef.current)
        ) {
          handleChange("microphoneDeviceId", "");
        }
      };

      try {
        if (typeof window.openNow?.getMicrophonePermission === "function") {
          const permission = await window.openNow.getMicrophonePermission();
          if (cancelled) {
            return;
          }

          if (permission.isMacOs && !permission.granted) {
            setMicrophoneDevices([]);
            setMicrophonePermissionError(getMicrophonePermissionError(permission));
            return;
          }
        }

        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach((track) => track.stop());

        const devices = await navigator.mediaDevices.enumerateDevices();
        applyDeviceList(devices.filter((device) => device.kind === "audioinput"));
      } catch (err) {
        console.error("[SettingsPage] Failed to enumerate microphone devices:", err);

        try {
          const devices = await navigator.mediaDevices.enumerateDevices();
          if (cancelled) {
            return;
          }

          const audioInputs = devices.filter((device) => device.kind === "audioinput");
          if (audioInputs.length > 0) {
            setMicrophoneDevices(audioInputs);
            setMicrophonePermissionError("Microphone access is required to show device names and use voice chat. Allow access and try again.");
            if (
              latestMicrophoneDeviceIdRef.current
              && !audioInputs.some((device) => device.deviceId === latestMicrophoneDeviceIdRef.current)
            ) {
              handleChange("microphoneDeviceId", "");
            }
            return;
          }
        } catch {
          // Ignore secondary enumerate failure and fall through to stable error state.
        }

        if (!cancelled) {
          const message = err instanceof DOMException && err.name === "NotAllowedError"
            ? "Microphone access was denied. Allow access for OpenNOW and try again."
            : "Unable to access microphone devices right now.";
          setMicrophonePermissionError(message);
          setMicrophoneDevices([]);
        }
      }
    }

    void enumerateDevices();
    return () => { cancelled = true; };
  }, [handleChange, settings.microphoneMode]);

  const getMicrophoneModeLabel = useCallback((mode: MicrophoneMode): string => {
    switch (mode) {
      case "push-to-talk":
        return t("settings.audio.pushToTalk");
      case "voice-activity":
        return t("settings.audio.voiceActivity");
      case "disabled":
      default:
        return t("settings.audio.disabled");
    }
  }, [locale, t]);

  const selectedMicrophoneModeName = useMemo(() => {
    return getMicrophoneModeLabel(settings.microphoneMode);
  }, [settings.microphoneMode, getMicrophoneModeLabel]);

  const selectedMicrophoneDeviceName = useMemo(() => {
    if (!settings.microphoneDeviceId) return t("app.labels.defaultDevice");
    const found = microphoneDevices.find((device) => device.deviceId === settings.microphoneDeviceId);
    return found?.label || t("settings.audio.selectedDevice");
  }, [settings.microphoneDeviceId, microphoneDevices, locale, t]);

  useEffect(() => {
    if (settings.microphoneMode === "disabled") {
      setMicrophoneDeviceDropdownOpen(false);
    }
  }, [settings.microphoneMode]);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent): void => {
      const target = event.target as Node;
      if (microphoneModeDropdownRef.current && !microphoneModeDropdownRef.current.contains(target)) {
        setMicrophoneModeDropdownOpen(false);
      }
      if (microphoneDeviceDropdownRef.current && !microphoneDeviceDropdownRef.current.contains(target)) {
        setMicrophoneDeviceDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.audio")}</div>}
      <div className="settings-section-header">
        <h2>{t("settings.audio.title")}</h2>
      </div>
        <div className="settings-rows">
          <div className="settings-row">
            <label className="settings-label">
              {t("settings.audio.microphone")}
              <span className="settings-hint">{t("settings.audio.microphoneHint")}</span>
            </label>
            <div className="settings-dropdown" ref={microphoneModeDropdownRef}>
              <button
                type="button"
                className={`settings-dropdown-selected ${microphoneModeDropdownOpen ? "open" : ""}`}
                onClick={() => {
                  setMicrophoneModeDropdownOpen((open) => !open);
                  setMicrophoneDeviceDropdownOpen(false);
                }}
              >
                <span className="settings-dropdown-selected-name">{selectedMicrophoneModeName}</span>
                <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`settings-dropdown-chevron ${microphoneModeDropdownOpen ? "flipped" : ""}`}>
                  <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
                </svg>
              </button>
              {microphoneModeDropdownOpen && (
                <div className="settings-dropdown-menu">
                  {microphoneModeOptions.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`settings-dropdown-item ${settings.microphoneMode === option.value ? "active" : ""}`}
                      onClick={() => {
                        handleChange("microphoneMode", option.value);
                        setMicrophoneModeDropdownOpen(false);
                      }}
                    >
                      <span>{getMicrophoneModeLabel(option.value)}</span>
                      {settings.microphoneMode === option.value && <Check size={14} className="settings-dropdown-check" />}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {settings.microphoneMode !== "disabled" && (
            <div className="settings-row">
              <label className="settings-label">
                <div className="flex items-center gap-2">
                  <Mic size={14} />
                  {t("settings.audio.microphoneDevice")}
                </div>
                <span className="settings-hint">{t("settings.audio.microphoneDeviceHint")}</span>
              </label>
              <div className="settings-mic-device-wrap">
                <div className="settings-dropdown" ref={microphoneDeviceDropdownRef}>
                  <button
                    type="button"
                    className={`settings-dropdown-selected ${microphoneDeviceDropdownOpen ? "open" : ""}`}
                    onClick={() => {
                      if (microphoneDevices.length === 0) return;
                      setMicrophoneDeviceDropdownOpen((open) => !open);
                      setMicrophoneModeDropdownOpen(false);
                    }}
                    disabled={microphoneDevices.length === 0}
                  >
                    <span className="settings-dropdown-selected-name">{selectedMicrophoneDeviceName}</span>
                    <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" className={`settings-dropdown-chevron ${microphoneDeviceDropdownOpen ? "flipped" : ""}`}>
                      <path d="M4.47 5.97a.75.75 0 0 1 1.06 0L8 8.44l2.47-2.47a.75.75 0 1 1 1.06 1.06l-3 3a.75.75 0 0 1-1.06 0l-3-3a.75.75 0 0 1 0-1.06Z" />
                    </svg>
                  </button>
                  {microphoneDeviceDropdownOpen && (
                    <div className="settings-dropdown-menu settings-dropdown-menu--tall">
                      <button
                        type="button"
                        className={`settings-dropdown-item ${settings.microphoneDeviceId === "" ? "active" : ""}`}
                        onClick={() => {
                          handleChange("microphoneDeviceId", "");
                          setMicrophoneDeviceDropdownOpen(false);
                        }}
                      >
                        <span>{t("app.labels.defaultDevice")}</span>
                        {settings.microphoneDeviceId === "" && <Check size={14} className="settings-dropdown-check" />}
                      </button>
                      {microphoneDevices.map((device, index) => (
                        <button
                          key={device.deviceId}
                          type="button"
                          className={`settings-dropdown-item ${settings.microphoneDeviceId === device.deviceId ? "active" : ""}`}
                          onClick={() => {
                            handleChange("microphoneDeviceId", device.deviceId);
                            setMicrophoneDeviceDropdownOpen(false);
                          }}
                        >
                          <span>{device.label || t("settings.audio.microphoneIndexed", { index: index + 1 })}</span>
                          {settings.microphoneDeviceId === device.deviceId && <Check size={14} className="settings-dropdown-check" />}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                {microphonePermissionError && (
                  <span className="text-red-400 text-xs mt-1">{microphonePermissionError}</span>
                )}
                {microphoneDevices.length === 0 && !microphonePermissionError && (
                  <span className="text-yellow-400 text-xs mt-1">{t("settings.audio.noMicrophoneDevicesFound")}</span>
                )}
              </div>
            </div>
          )}
        </div>
      </section>
  );
}
