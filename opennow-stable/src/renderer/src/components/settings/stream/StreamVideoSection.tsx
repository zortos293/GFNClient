import { Monitor } from "lucide-react";
import { type JSX } from "react";
import type { EntitledResolution, Settings } from "@shared/gfn";
import type { CodecTestResult } from "../../../lib/codecDiagnostics";
import { useTranslation } from "../../../i18n";
import type { SettingsChangeHandler } from "./streamSettingsTypes";
import { StreamQualityControls } from "./StreamQualityControls";
import { SessionProxySettings } from "./SessionProxySettings";
import { VideoShaderControls } from "./VideoShaderControls";
import { StreamSessionOptions } from "./StreamSessionOptions";

interface StreamVideoSectionProps {
  settings: Settings;
  showAll: boolean;
  handleChange: SettingsChangeHandler;
  handlePreview: SettingsChangeHandler;
  codecResults: CodecTestResult[] | null;
  codecTesting: boolean;
  entitledResolutions: EntitledResolution[];
  subscriptionInfoLoaded: boolean;
  subscriptionLoading: boolean;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

export function StreamVideoSection({
  settings,
  showAll,
  handleChange,
  handlePreview,
  codecResults,
  codecTesting,
  entitledResolutions,
  subscriptionInfoLoaded,
  subscriptionLoading,
  onBlockingOverlayChange,
}: StreamVideoSectionProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <section className="settings-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.stream")}</div>}
      <div className="settings-section-header">
        <Monitor size={18} />
        <h2>{t("settings.video.title")}</h2>
      </div>
      <div className="settings-rows settings-rows--grouped">
        <StreamQualityControls
          settings={settings}
          handleChange={handleChange}
          handlePreview={handlePreview}
          codecResults={codecResults}
          codecTesting={codecTesting}
          entitledResolutions={entitledResolutions}
          subscriptionInfoLoaded={subscriptionInfoLoaded}
          subscriptionLoading={subscriptionLoading}
        />
        <div className="settings-group">
          <div className="settings-group-header">
            <h3>{t("settings.video.session")}</h3>
            <p>{t("settings.video.sessionHint")}</p>
          </div>
          <div className="settings-group-rows">
            <SessionProxySettings
              settings={settings}
              handleChange={handleChange}
              onBlockingOverlayChange={onBlockingOverlayChange}
            />
            <StreamSessionOptions settings={settings} handleChange={handleChange} />
          </div>
        </div>
        <div className="settings-group">
          <div className="settings-group-header">
            <h3>{t("settings.video.processing")}</h3>
            <p>{t("settings.video.processingHint")}</p>
          </div>
          <div className="settings-group-rows">
            <VideoShaderControls
              settings={settings}
              handleChange={handleChange}
              handlePreview={handlePreview}
            />
          </div>
        </div>
      </div>
    </section>
  );
}
