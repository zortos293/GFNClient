/**
 * Shared PostHog telemetry contracts for OpenNOW.
 *
 * Privacy posture:
 * - No session replay / autocapture / product funnels
 * - Error capture + anonymous app opens are gated by `errorReportingConsent`
 * - Feedback is an explicit user action and may send without error consent
 * - Distinct ID is an anonymous install UUID (never GFN account data)
 *
 * Project: OpenNOW-Desktop (EY Services org, EU cloud).
 * {@link POSTHOG_PROJECT_TOKEN} is the public write-only project API key.
 */

export const POSTHOG_PROJECT_TOKEN = "phc_nLL3oLahaD3KbFGokZ6CZgMWjLano5nJfnXM4ghCVRVw";
export const POSTHOG_HOST = "https://eu.i.posthog.com";
export const POSTHOG_PROJECT_ID = 229668;

export const APP_OPENED_EVENT_NAME = "app_opened";
export const FEEDBACK_EVENT_NAME = "feedback_submitted";

export type FeedbackCategory = "bug" | "idea" | "other";

export interface FeedbackPayload {
  category: FeedbackCategory;
  message: string;
  includeSystemInfo: boolean;
  /** Attach redacted main + renderer console logs (truncated for PostHog). */
  includeLogs: boolean;
}

/** Max characters of combined redacted logs attached to feedback events. */
export const FEEDBACK_LOGS_MAX_CHARS = 24_000;

export function isPostHogConfigured(): boolean {
  return POSTHOG_PROJECT_TOKEN.startsWith("phc_");
}

export const FEEDBACK_CATEGORIES: readonly FeedbackCategory[] = ["bug", "idea", "other"];
