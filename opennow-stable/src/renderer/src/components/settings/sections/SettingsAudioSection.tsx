import { Mic } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type JSX } from "react";
import type { MicrophoneMode, Settings } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
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
  const latestMicrophoneDeviceIdRef = useRef(settings.microphoneDeviceId);
  const microphoneModeId = "settings-audio-microphone-mode";
  const microphoneDeviceId = "settings-audio-microphone-device";

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

  const localizedMicrophoneModeOptions = useMemo(
    () => microphoneModeOptions.map((option) => ({
      value: option.value,
      label: getMicrophoneModeLabel(option.value),
    })),
    [getMicrophoneModeLabel],
  );

  const microphoneDeviceOptions = useMemo(
    () => [
      { value: "", label: t("app.labels.defaultDevice") },
      ...microphoneDevices.map((device, index) => ({
        value: device.deviceId,
        label: device.label || t("settings.audio.microphoneIndexed", { index: index + 1 }),
      })),
    ],
    [locale, microphoneDevices, t],
  );

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.audio")}</div>}
      <div className="settings-section-header">
        <h2>{t("settings.audio.title")}</h2>
      </div>
      <div className="settings-rows">
        <div className="settings-row">
          <label className="settings-label" htmlFor={microphoneModeId}>
            {t("settings.audio.microphone")}
            <span className="settings-hint">{t("settings.audio.microphoneHint")}</span>
          </label>
          <div className="settings-row-control">
            <SelectDropdown
              id={microphoneModeId}
              value={settings.microphoneMode}
              options={localizedMicrophoneModeOptions}
              onChange={(value) => handleChange("microphoneMode", value as MicrophoneMode)}
            />
          </div>
        </div>

        {settings.microphoneMode !== "disabled" && (
          <div className="settings-row">
            <label className="settings-label" htmlFor={microphoneDeviceId}>
              <span className="settings-label--with-icon">
                <Mic size={14} aria-hidden="true" />
                {t("settings.audio.microphoneDevice")}
              </span>
              <span className="settings-hint">{t("settings.audio.microphoneDeviceHint")}</span>
            </label>
            <div className="settings-mic-device-wrap">
              <SelectDropdown
                id={microphoneDeviceId}
                value={settings.microphoneDeviceId}
                options={microphoneDeviceOptions}
                onChange={(value) => handleChange("microphoneDeviceId", value)}
                disabled={microphoneDevices.length === 0}
                menuClassName="select-dropdown__menu--tall"
              />
              {microphonePermissionError && (
                <span className="settings-input-hint">{microphonePermissionError}</span>
              )}
              {microphoneDevices.length === 0 && !microphonePermissionError && (
                <span className="settings-subtle-hint">{t("settings.audio.noMicrophoneDevicesFound")}</span>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
