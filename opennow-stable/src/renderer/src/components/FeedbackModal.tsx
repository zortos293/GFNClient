import { Bug, CheckCircle2, Lightbulb, MessageSquareText, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import { FEEDBACK_CATEGORIES, type FeedbackCategory } from "@shared/telemetry";
import { useTranslation } from "../i18n";
import { captureFeedback, isTelemetryConfigured } from "../telemetry/posthog";
import { ModalSurface } from "./ui/ModalSurface";

export interface FeedbackModalProps {
  open: boolean;
  settings: Settings;
  onClose: () => void;
  onExitComplete?: () => void;
}

const CATEGORY_ICONS: Record<FeedbackCategory, typeof Bug> = {
  bug: Bug,
  idea: Lightbulb,
  other: MessageSquareText,
};

const MESSAGE_MAX_LENGTH = 4000;

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
      overlayClassName="feedback-modal"
      backdropClassName="feedback-modal-backdrop"
      panelClassName="feedback-modal-card"
      ariaLabel={t("feedback.title")}
      backdropLabel={t("app.actions.close")}
      initialFocusRef={messageRef}
    >
      <button
        type="button"
        className="feedback-modal-close"
        onClick={onClose}
        aria-label={t("app.actions.close")}
      >
        <X size={16} className="shrink-0" />
      </button>

      {submitted ? (
        <div className="feedback-modal-success">
          <CheckCircle2 size={28} className="feedback-modal-success-icon shrink-0" aria-hidden="true" />
          <div className="feedback-modal-kicker">{t("feedback.kicker")}</div>
          <h2 className="feedback-modal-title">{t("feedback.successTitle")}</h2>
          <p className="feedback-modal-lead">{t("feedback.success")}</p>
          <div className="feedback-modal-actions">
            <button type="button" className="feedback-modal-btn feedback-modal-btn-primary" onClick={onClose}>
              {t("feedback.close")}
            </button>
          </div>
        </div>
      ) : (
        <form className="feedback-modal-form" onSubmit={(event) => void handleSubmit(event)}>
          <div className="feedback-modal-kicker">{t("feedback.kicker")}</div>
          <h2 className="feedback-modal-title">{t("feedback.title")}</h2>
          <p className="feedback-modal-lead">{t("feedback.description")}</p>

          <fieldset className="feedback-modal-fieldset">
            <legend className="feedback-modal-label">{t("feedback.category")}</legend>
            <div className="feedback-modal-categories" role="radiogroup" aria-label={t("feedback.category")}>
              {FEEDBACK_CATEGORIES.map((value) => {
                const Icon = CATEGORY_ICONS[value];
                const selected = category === value;
                return (
                  <button
                    key={value}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    className={`feedback-modal-category${selected ? " is-selected" : ""}`}
                    onClick={() => setCategory(value)}
                    disabled={submitting}
                  >
                    <Icon size={14} className="shrink-0" aria-hidden="true" />
                    <span>{t(`feedback.categories.${value}`)}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <label className="feedback-modal-label" htmlFor="feedback-message">
            {t("feedback.message")}
          </label>
          <textarea
            id="feedback-message"
            name="message"
            ref={messageRef}
            className="feedback-modal-textarea"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            rows={5}
            maxLength={MESSAGE_MAX_LENGTH}
            placeholder={t("feedback.messagePlaceholder")}
            disabled={submitting}
          />
          <div className="feedback-modal-meta">
            <span>
              {message.length}/{MESSAGE_MAX_LENGTH}
            </span>
          </div>

          <div className="feedback-modal-attach">
            <label className="feedback-modal-check" htmlFor="feedback-include-system">
              <span className="feedback-modal-check-control">
                <input
                  id="feedback-include-system"
                  name="includeSystemInfo"
                  type="checkbox"
                  checked={includeSystemInfo}
                  onChange={(event) => setIncludeSystemInfo(event.target.checked)}
                  disabled={submitting}
                />
                <span className="feedback-modal-check-box" aria-hidden="true" />
              </span>
              <span className="feedback-modal-check-copy">
                <span className="feedback-modal-check-title">{t("feedback.includeSystemInfo")}</span>
              </span>
            </label>

            <label className="feedback-modal-check" htmlFor="feedback-include-logs">
              <span className="feedback-modal-check-control">
                <input
                  id="feedback-include-logs"
                  name="includeLogs"
                  type="checkbox"
                  checked={includeLogs}
                  onChange={(event) => setIncludeLogs(event.target.checked)}
                  disabled={submitting}
                />
                <span className="feedback-modal-check-box" aria-hidden="true" />
              </span>
              <span className="feedback-modal-check-copy">
                <span className="feedback-modal-check-title">{t("feedback.includeLogs")}</span>
                <span className="feedback-modal-check-hint">{t("feedback.includeLogsHint")}</span>
              </span>
            </label>
          </div>

          {error ? (
            <p className="feedback-modal-error" role="alert">
              {error}
            </p>
          ) : null}

          <div className="feedback-modal-actions">
            <button
              type="button"
              className="feedback-modal-btn feedback-modal-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              {t("feedback.cancel")}
            </button>
            <button
              type="submit"
              className="feedback-modal-btn feedback-modal-btn-primary"
              disabled={submitting}
            >
              <MessageSquareText size={14} className="shrink-0" aria-hidden="true" />
              {submitting ? t("feedback.sending") : t("feedback.send")}
            </button>
          </div>
        </form>
      )}
    </ModalSurface>
  );
}
