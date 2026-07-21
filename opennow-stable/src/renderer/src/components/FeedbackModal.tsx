import { MessageSquareText, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { FEEDBACK_CATEGORIES, type FeedbackCategory } from "@shared/telemetry";
import { useTranslation } from "../i18n";
import { captureFeedback, isTelemetryConfigured } from "../telemetry/posthog";
import { ModalSurface } from "./ui/ModalSurface";
import { SelectDropdown } from "./ui/SelectDropdown";

export interface FeedbackModalProps {
  open: boolean;
  settings: Settings;
  onClose: () => void;
  onExitComplete?: () => void;
}

export function FeedbackModal({
  open,
  settings,
  onClose,
  onExitComplete,
}: FeedbackModalProps): JSX.Element {
  const { t } = useTranslation();
  const messageRef = useRef<HTMLTextAreaElement | null>(null);
  const [category, setCategory] = useState<FeedbackCategory>("bug");
  const [message, setMessage] = useState("");
  const [includeSystemInfo, setIncludeSystemInfo] = useState(true);
  const [includeLogs, setIncludeLogs] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setCategory("bug");
    setMessage("");
    setIncludeSystemInfo(true);
    setIncludeLogs(true);
    setSubmitting(false);
    setError(null);
    setSubmitted(false);
  }, [open]);

  const handleSubmit = async (event: FormEvent): Promise<void> => {
    event.preventDefault();
    const trimmed = message.trim();
    if (!trimmed) {
      setError(t("feedback.errors.empty"));
      return;
    }
    if (!isTelemetryConfigured()) {
      setError(t("feedback.errors.notConfigured"));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const ok = await captureFeedback(settings, {
        category,
        message: trimmed,
        includeSystemInfo,
        includeLogs,
      });
      if (!ok) {
        setError(t("feedback.errors.sendFailed"));
        return;
      }
      setSubmitted(true);
    } catch (submitError) {
      console.error("[Feedback] Failed to send feedback:", submitError);
      setError(t("feedback.errors.sendFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ModalSurface
      open={open}
      onClose={onClose}
      onExitComplete={onExitComplete}
      motion="compact"
      overlayClassName="rh-overlay telemetry-feedback-overlay"
      backdropClassName="rh-backdrop"
      panelClassName="rh-card telemetry-feedback-card"
      ariaLabel={t("feedback.title")}
      backdropLabel={t("app.actions.close")}
      initialFocusRef={messageRef}
    >
      <div className="rh-header">
        <div className="rh-kicker">{t("feedback.kicker")}</div>
        <h2 className="rh-title">{t("feedback.title")}</h2>
        <button
          type="button"
          className="rh-close-btn"
          onClick={onClose}
          aria-label={t("app.actions.close")}
        >
          <X size={18} />
        </button>
      </div>

      {submitted ? (
        <>
          <div className="rh-body telemetry-feedback-body">
            <p className="telemetry-feedback-success">{t("feedback.success")}</p>
          </div>
          <div className="rh-footer">
            <button type="button" className="rh-btn-primary" onClick={onClose}>
              {t("feedback.close")}
            </button>
          </div>
        </>
      ) : (
        <form
          className="telemetry-feedback-form"
          onSubmit={(event) => void handleSubmit(event)}
        >
          <div className="rh-body telemetry-feedback-body">
            <label className="telemetry-feedback-label" htmlFor="feedback-category">
              {t("feedback.category")}
            </label>
            <SelectDropdown
              id="feedback-category"
              value={category}
              options={FEEDBACK_CATEGORIES.map((value) => ({
                value,
                label: t(`feedback.categories.${value}`),
              }))}
              onChange={(value) => setCategory(value as FeedbackCategory)}
              ariaLabel={t("feedback.category")}
            />

            <label className="telemetry-feedback-label" htmlFor="feedback-message">
              {t("feedback.message")}
            </label>
            <textarea
              id="feedback-message"
              ref={messageRef}
              className="telemetry-feedback-textarea"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              rows={6}
              maxLength={4000}
              placeholder={t("feedback.messagePlaceholder")}
              disabled={submitting}
            />

            <label className="telemetry-feedback-checkbox">
              <input
                type="checkbox"
                checked={includeSystemInfo}
                onChange={(event) => setIncludeSystemInfo(event.target.checked)}
                disabled={submitting}
              />
              <span>{t("feedback.includeSystemInfo")}</span>
            </label>

            <label className="telemetry-feedback-checkbox">
              <input
                type="checkbox"
                checked={includeLogs}
                onChange={(event) => setIncludeLogs(event.target.checked)}
                disabled={submitting}
              />
              <span>{t("feedback.includeLogs")}</span>
            </label>
            <p className="telemetry-feedback-hint">{t("feedback.includeLogsHint")}</p>

            {error ? <p className="telemetry-feedback-error" role="alert">{error}</p> : null}
          </div>

          <div className="rh-footer">
            <button type="button" className="rh-btn-secondary" onClick={onClose} disabled={submitting}>
              {t("feedback.cancel")}
            </button>
            <button type="submit" className="rh-btn-primary" disabled={submitting}>
              <MessageSquareText size={14} />
              {submitting ? t("feedback.sending") : t("feedback.send")}
            </button>
          </div>
        </form>
      )}
    </ModalSurface>
  );
}
