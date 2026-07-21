import { useRef, type JSX } from "react";
import { useTranslation } from "../i18n";
import { ModalSurface } from "./ui/ModalSurface";

export interface ErrorReportingConsentModalProps {
  open: boolean;
  onAccept: () => void;
  onDecline: () => void;
  onExitComplete?: () => void;
}

export function ErrorReportingConsentModal({
  open,
  onAccept,
  onDecline,
  onExitComplete,
}: ErrorReportingConsentModalProps): JSX.Element {
  const { t } = useTranslation();
  const primaryActionRef = useRef<HTMLButtonElement | null>(null);

  return (
    <ModalSurface
      open={open}
      onClose={onDecline}
      onExitComplete={onExitComplete}
      motion="compact"
      overlayClassName="rh-overlay telemetry-consent-overlay"
      backdropClassName="rh-backdrop"
      panelClassName="rh-card telemetry-consent-card"
      ariaLabel={t("telemetryConsent.title")}
      backdropLabel={t("app.actions.close")}
      initialFocusRef={primaryActionRef}
      closeOnBackdrop={false}
      closeOnEscape={false}
    >
      <div className="rh-header">
        <div className="rh-kicker">{t("telemetryConsent.kicker")}</div>
        <h2 className="rh-title">{t("telemetryConsent.title")}</h2>
      </div>

      <div className="rh-body telemetry-consent-body">
        <p>{t("telemetryConsent.body")}</p>
        <ul className="telemetry-consent-list">
          <li>{t("telemetryConsent.sendsErrors")}</li>
          <li>{t("telemetryConsent.sendsAppOpens")}</li>
          <li>{t("telemetryConsent.sendsAnonymousId")}</li>
          <li>{t("telemetryConsent.neverSendsAccount")}</li>
          <li>{t("telemetryConsent.neverSendsGameplay")}</li>
        </ul>
        <p className="telemetry-consent-footnote">{t("telemetryConsent.changeLater")}</p>
      </div>

      <div className="rh-footer">
        <button type="button" className="rh-btn-secondary" onClick={onDecline}>
          {t("telemetryConsent.decline")}
        </button>
        <button
          type="button"
          className="rh-btn-primary"
          onClick={onAccept}
          ref={primaryActionRef}
        >
          {t("telemetryConsent.accept")}
        </button>
      </div>
    </ModalSurface>
  );
}
