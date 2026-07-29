import { Heart } from "lucide-react";
import { type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { isZortosCommunityProxyUrl } from "@shared/communityProxy";
import { useTranslation } from "../../../i18n";
import { ModalSurface } from "../../ui/ModalSurface";
import type { SettingsChangeHandler } from "./streamSettingsTypes";
import { useCommunityProxyProvisioning } from "./useCommunityProxyProvisioning";

interface SessionProxySettingsProps {
  settings: Settings;
  handleChange: SettingsChangeHandler;
  onBlockingOverlayChange?: (blocking: boolean) => void;
}

export function SessionProxySettings({
  settings,
  handleChange,
  onBlockingOverlayChange,
}: SessionProxySettingsProps): JSX.Element {
  const { t } = useTranslation();
  const communityProxy = useCommunityProxyProvisioning({
    handleChange,
    onBlockingOverlayChange,
  });
  const isUsingZortosCommunityProxy = settings.sessionProxyEnabled
    && isZortosCommunityProxyUrl(settings.sessionProxyUrl);

  return (
    <>
      <div className="settings-row settings-row--column">
        <div className="settings-row-top settings-row-top--compact">
          <label
            className="settings-label settings-label--wrap"
            htmlFor="settings-stream-session-proxy"
          >
            <span className="settings-label-title">
              {t("settings.video.sessionProxy")}
              <span className="settings-inline-badge settings-inline-badge--beta">
                {t("app.labels.beta")}
              </span>
            </span>
          </label>
          <label className="settings-toggle">
            <input
              id="settings-stream-session-proxy"
              type="checkbox"
              checked={settings.sessionProxyEnabled}
              onChange={(event) => {
                handleChange("sessionProxyEnabled", event.target.checked);
              }}
            />
            <span className="settings-toggle-track" />
          </label>
        </div>
        <span className="settings-subtle-hint">{t("settings.video.sessionProxyHint")}</span>
        <div className="settings-community-proxy-row">
          {isUsingZortosCommunityProxy ? (
            <span className="settings-inline-badge settings-inline-badge--beta">
              {t("settings.video.zortosCommunityProxy.enabledBadge")}
            </span>
          ) : (
            <button
              type="button"
              className="settings-chip settings-community-proxy-btn"
              onClick={communityProxy.openPrompt}
            >
              <Heart size={14} />
              <span>{t("settings.video.zortosCommunityProxy.useButton")}</span>
            </button>
          )}
        </div>
        {settings.sessionProxyEnabled && (
          <input
            type="text"
            className="settings-text-input"
            placeholder="http://127.0.0.1:8080"
            value={settings.sessionProxyUrl}
            onChange={(event) => handleChange("sessionProxyUrl", event.target.value)}
          />
        )}
      </div>

      <ModalSurface
        open={communityProxy.promptOpen}
        onClose={communityProxy.closePrompt}
        onExitComplete={communityProxy.handlePromptExit}
        motion="compact"
        overlayClassName="native-streamer-warning"
        backdropClassName="native-streamer-warning-backdrop"
        panelClassName="native-streamer-warning-card"
        ariaLabelledBy="zortos-community-proxy-title"
        ariaDescribedBy="zortos-community-proxy-copy"
        backdropLabel={t("app.actions.cancel")}
        initialFocusRef={communityProxy.continueRef}
        closeOnBackdrop={!communityProxy.provisioning}
        closeOnEscape={!communityProxy.provisioning}
      >
        <div className="native-streamer-warning-kicker">
          <Heart size={14} />
          {t("settings.video.zortosCommunityProxy.enablePromptKicker")}
        </div>
        <h3 id="zortos-community-proxy-title" className="native-streamer-warning-title">
          {t("settings.video.zortosCommunityProxy.enablePromptTitle")}
        </h3>
        <p id="zortos-community-proxy-copy" className="native-streamer-warning-text">
          {t("settings.video.zortosCommunityProxy.enablePromptBody")}
        </p>
        <p className="native-streamer-warning-text">
          {t("settings.video.zortosCommunityProxy.enablePromptCostHint")}
        </p>
        {communityProxy.error && (
          <p className="settings-community-proxy-error">{communityProxy.error}</p>
        )}
        <div className="native-streamer-warning-actions">
          <button
            type="button"
            className="native-streamer-warning-btn native-streamer-warning-btn--primary native-streamer-warning-btn--with-icon"
            onClick={communityProxy.openSponsors}
          >
            <Heart size={15} />
            {t("settings.video.zortosCommunityProxy.enablePromptDonate")}
          </button>
          <button
            type="button"
            className="native-streamer-warning-btn native-streamer-warning-btn--secondary"
            onClick={() => {
              void communityProxy.confirmPrompt();
            }}
            ref={communityProxy.continueRef}
            disabled={communityProxy.provisioning}
          >
            {communityProxy.provisioning
              ? t("settings.video.zortosCommunityProxy.provisioning")
              : t("settings.video.zortosCommunityProxy.enablePromptContinue")}
          </button>
        </div>
        <div className="native-streamer-warning-hint">
          <kbd>Esc</kbd> {t("settings.video.zortosCommunityProxy.enablePromptEsc")}
        </div>
      </ModalSurface>
    </>
  );
}
