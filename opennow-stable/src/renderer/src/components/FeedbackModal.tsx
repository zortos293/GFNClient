import { Bug, ChevronLeft, FileJson2, FileText, MessageSquareText, ShieldCheck, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type JSX } from "react";
import type { Settings } from "@shared/gfn";
import {
  DESKTOP_BUG_REPORT_MAX_DESCRIPTION_CHARS,
  DESKTOP_BUG_REPORT_MAX_TITLE_CHARS,
  DESKTOP_BUG_REPORT_MIN_MEANINGFUL_CHARS,
  desktopBugReportDescriptionError,
  desktopBugReportMeaningfulCharacterCount,
  desktopBugReportTitleError,
  type DesktopSessionReport,
} from "@shared/bugReport";
import { FEEDBACK_CATEGORIES, type FeedbackCategory } from "@shared/telemetry";
import { getLogCapture } from "@shared/logger";
import { useTranslation } from "../i18n";
import { captureFeedback, isTelemetryConfigured } from "../telemetry/posthog";
import { ModalSurface } from "./ui/ModalSurface";
import { SelectDropdown } from "./ui/SelectDropdown";

export interface FeedbackModalProps {
  open: boolean;
  settings: Settings;
  initialCategory?: FeedbackCategory;
  sessionReport?: DesktopSessionReport | null;
  onClose: () => void;
  onExitComplete?: () => void;
}

export function FeedbackModal({
  open,
  settings,
  initialCategory = "bug",
  sessionReport = null,
  onClose,
  onExitComplete,
}: FeedbackModalProps): JSX.Element {
  const { t } = useTranslation();
  const messageRef = useRef<HTMLTextAreaElement | null>(null);
  const [category, setCategory] = useState<FeedbackCategory>(initialCategory);
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [includeSystemInfo, setIncludeSystemInfo] = useState(true);
  const [includeLogs, setIncludeLogs] = useState(true);
  const [includeSessionReport, setIncludeSessionReport] = useState(Boolean(sessionReport));
  const [consentChecked, setConsentChecked] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [reference, setReference] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setCategory(initialCategory);
    setTitle("");
    setMessage("");
    setIncludeSystemInfo(true);
    setIncludeLogs(true);
    setIncludeSessionReport(Boolean(sessionReport));
    setConsentChecked(false);
    setReviewing(false);
    setSubmitting(false);
    setError(null);
    setSubmitted(false);
    setReference(null);
  }, [initialCategory, open, sessionReport]);

  const validateBugReport = (): boolean => {
    const titleError = desktopBugReportTitleError(title);
    if (titleError) {
      setError(titleError);
      return false;
    }
    const descriptionError = desktopBugReportDescriptionError(message);
    if (descriptionError) {
      setError(descriptionError);
      return false;
    }
    return true;
  };

  const handleSubmit = async (event: FormEvent): Promise<void> => {
    event.preventDefault();
    const trimmed = message.trim();
    if (category === "bug") {
      if (!validateBugReport()) return;
      setError(null);
      setReviewing(true);
      return;
    }
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

  const submitBugReport = async (): Promise<void> => {
    if (!consentChecked || !validateBugReport()) {
      if (!consentChecked) setError(t("feedback.bug.consentRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const receipt = await window.openNow.submitBugReport({
        title: title.trim(),
        description: message.trim(),
        includeSystemInfo,
        ...(includeLogs ? {
          rendererLogs: getLogCapture()?.exportRedacted() ?? "[renderer logs unavailable]",
        } : {}),
        ...(includeSessionReport && sessionReport ? { sessionReport } : {}),
        locale: navigator.language,
        clientPlatform: navigator.platform,
        userAgent: navigator.userAgent,
      });
      setReference(receipt.reference);
      setSubmitted(true);
      setReviewing(false);
    } catch (submitError) {
      console.error("[BugReport] Failed to send bug report:", submitError);
      setError(normalizeUploadError(submitError, t("feedback.errors.sendFailed")));
    } finally {
      setSubmitting(false);
    }
  };

  const bugMode = category === "bug";

  return (
    <ModalSurface
      open={open}
      onClose={onClose}
      onExitComplete={onExitComplete}
      motion="compact"
      overlayClassName="rh-overlay telemetry-feedback-overlay"
      backdropClassName="rh-backdrop"
      panelClassName="rh-card telemetry-feedback-card"
      ariaLabel={bugMode ? t("feedback.bug.title") : t("feedback.title")}
      backdropLabel={t("app.actions.close")}
      initialFocusRef={messageRef}
    >
      <div className="rh-header">
        <div>
          <div className="rh-kicker">{bugMode ? t("feedback.bug.kicker") : t("feedback.kicker")}</div>
          <h2 className="rh-title">{bugMode ? t("feedback.bug.title") : t("feedback.title")}</h2>
        </div>
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
            <p className="telemetry-feedback-success">
              {bugMode ? t("feedback.bug.success") : t("feedback.success")}
            </p>
            {bugMode && reference ? (
              <p className="telemetry-feedback-reference">
                {t("feedback.bug.reference", { reference })}
              </p>
            ) : null}
          </div>
          <div className="rh-footer">
            <button type="button" className="rh-btn-primary" onClick={onClose}>
              {t("feedback.close")}
            </button>
          </div>
        </>
      ) : reviewing && bugMode ? (
        <BugReportReview
          title={title.trim()}
          description={message.trim()}
          includeSystemInfo={includeSystemInfo}
          includeLogs={includeLogs}
          sessionReport={includeSessionReport ? sessionReport : null}
          consentChecked={consentChecked}
          submitting={submitting}
          error={error}
          onConsentChange={setConsentChecked}
          onBack={() => {
            setError(null);
            setReviewing(false);
          }}
          onSubmit={() => void submitBugReport()}
        />
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
              onChange={(value) => {
                setCategory(value as FeedbackCategory);
                setError(null);
              }}
              ariaLabel={t("feedback.category")}
            />

            {bugMode ? (
              <>
                <label className="telemetry-feedback-label" htmlFor="feedback-title">
                  {t("feedback.bug.issueTitle")}
                </label>
                <input
                  id="feedback-title"
                  className="telemetry-feedback-input"
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  maxLength={DESKTOP_BUG_REPORT_MAX_TITLE_CHARS}
                  placeholder={t("feedback.bug.issueTitlePlaceholder")}
                  disabled={submitting}
                />
              </>
            ) : null}

            <label className="telemetry-feedback-label" htmlFor="feedback-message">
              {bugMode ? t("feedback.bug.description") : t("feedback.message")}
            </label>
            <textarea
              id="feedback-message"
              ref={messageRef}
              className="telemetry-feedback-textarea"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              rows={bugMode ? 7 : 6}
              maxLength={bugMode ? DESKTOP_BUG_REPORT_MAX_DESCRIPTION_CHARS : 4_000}
              placeholder={bugMode ? t("feedback.bug.descriptionPlaceholder") : t("feedback.messagePlaceholder")}
              disabled={submitting}
            />
            {bugMode ? (
              <p className="telemetry-feedback-hint">
                {t("feedback.bug.meaningfulCharacters", {
                  count: desktopBugReportMeaningfulCharacterCount(message),
                  minimum: DESKTOP_BUG_REPORT_MIN_MEANINGFUL_CHARS,
                })}
              </p>
            ) : null}

            <label className="telemetry-feedback-checkbox">
              <input
                type="checkbox"
                checked={includeSystemInfo}
                onChange={(event) => setIncludeSystemInfo(event.target.checked)}
                disabled={submitting}
              />
              <span>{bugMode ? t("feedback.bug.includeSystemInfo") : t("feedback.includeSystemInfo")}</span>
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

            {bugMode && sessionReport ? (
              <label className="telemetry-feedback-checkbox">
                <input
                  type="checkbox"
                  checked={includeSessionReport}
                  onChange={(event) => setIncludeSessionReport(event.target.checked)}
                  disabled={submitting}
                />
                <span>{t("feedback.bug.includeSessionReport")}</span>
              </label>
            ) : null}

            {bugMode ? (
              <p className="telemetry-feedback-disclosure">
                <ShieldCheck size={15} />
                <span>{t("feedback.bug.disclosure")}</span>
              </p>
            ) : null}

            {error ? <p className="telemetry-feedback-error" role="alert">{error}</p> : null}
          </div>

          <div className="rh-footer">
            <button type="button" className="rh-btn-secondary" onClick={onClose} disabled={submitting}>
              {t("feedback.cancel")}
            </button>
            <button type="submit" className="rh-btn-primary" disabled={submitting}>
              {bugMode ? <Bug size={14} /> : <MessageSquareText size={14} />}
              {submitting
                ? t("feedback.sending")
                : bugMode
                  ? t("feedback.bug.review")
                  : t("feedback.send")}
            </button>
          </div>
        </form>
      )}
    </ModalSurface>
  );
}

function BugReportReview({
  title,
  description,
  includeSystemInfo,
  includeLogs,
  sessionReport,
  consentChecked,
  submitting,
  error,
  onConsentChange,
  onBack,
  onSubmit,
}: {
  title: string;
  description: string;
  includeSystemInfo: boolean;
  includeLogs: boolean;
  sessionReport: DesktopSessionReport | null;
  consentChecked: boolean;
  submitting: boolean;
  error: string | null;
  onConsentChange: (checked: boolean) => void;
  onBack: () => void;
  onSubmit: () => void;
}): JSX.Element {
  const { t } = useTranslation();
  return (
    <>
      <div className="rh-body telemetry-feedback-body bug-report-review">
        <p className="telemetry-feedback-hint">{t("feedback.bug.reviewHint")}</p>
        <section className="bug-report-review-copy">
          <strong>{title}</strong>
          <p>{description}</p>
        </section>
        <div className="bug-report-attachment-list">
          {includeLogs ? <span><FileText size={15} /> opennow.log</span> : null}
          {sessionReport ? (
            <span>
              <FileJson2 size={15} />
              opennow-session-report.json · {sessionReport.gameTitle} · {sessionReport.score}/100
            </span>
          ) : null}
          <span><ShieldCheck size={15} /> {t("feedback.bug.appVersionRequired")}</span>
          {includeSystemInfo ? <span><ShieldCheck size={15} /> {t("feedback.bug.systemMetadata")}</span> : null}
          {!includeLogs && !sessionReport && !includeSystemInfo
            ? <span>{t("feedback.bug.noAttachments")}</span>
            : null}
        </div>
        <label className="telemetry-feedback-checkbox bug-report-consent">
          <input
            type="checkbox"
            checked={consentChecked}
            onChange={(event) => onConsentChange(event.target.checked)}
            disabled={submitting}
          />
          <span>{t("feedback.bug.consent")}</span>
        </label>
        {error ? <p className="telemetry-feedback-error" role="alert">{error}</p> : null}
      </div>
      <div className="rh-footer">
        <button type="button" className="rh-btn-secondary" onClick={onBack} disabled={submitting}>
          <ChevronLeft size={14} />
          {t("feedback.bug.back")}
        </button>
        <button type="button" className="rh-btn-primary" onClick={onSubmit} disabled={submitting}>
          <Bug size={14} />
          {submitting ? t("feedback.sending") : t("feedback.bug.send")}
        </button>
      </div>
    </>
  );
}

function normalizeUploadError(error: unknown, fallback: string): string {
  if (!(error instanceof Error) || !error.message.trim()) return fallback;
  return error.message
    .replace(/^Error invoking remote method '[^']+':\s*/i, "")
    .replace(/^Error:\s*/i, "")
    .trim()
    .slice(0, 320) || fallback;
}
