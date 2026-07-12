import { Heart, Users, ExternalLink } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type JSX } from "react";
import type { ThankYouContributor, ThankYouDataResult, ThankYouSupporter } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import type { ThanksLoadState } from "../settingsTypes";
import { MotionSpinner } from "../../MotionSpinner";

let thanksDataCache: ThankYouDataResult | null = null;

export function SettingsThanksSection(): JSX.Element {
  const { t } = useTranslation();
  const [thanksData, setThanksData] = useState<ThankYouDataResult | null>(thanksDataCache);
  const [thanksLoadState, setThanksLoadState] = useState<ThanksLoadState>(thanksDataCache ? "loaded" : "idle");
  const [thanksFetchError, setThanksFetchError] = useState<string | null>(null);
  const thanksRequestIdRef = useRef(0);
  const thanksMountedRef = useRef(true);

  useEffect(() => {
    thanksMountedRef.current = true;
    return () => {
      thanksMountedRef.current = false;
      thanksRequestIdRef.current += 1;
    };
  }, []);

  useEffect(() => {
    if (thanksData || thanksLoadState !== "idle") {
      return;
    }

    const requestId = ++thanksRequestIdRef.current;
    let requestPromise: Promise<ThankYouDataResult>;

    try {
      const getThanksData = window.openNow?.getThanksData;
      if (typeof getThanksData !== "function") {
        throw new Error("openNow.getThanksData is unavailable");
      }
      requestPromise = getThanksData();
    } catch (error) {
      console.error("[SettingsPage] Failed to start thanks data request:", error);
      setThanksData(null);
      setThanksFetchError("Unable to load community acknowledgements right now.");
      setThanksLoadState("error");
      return;
    }

    setThanksLoadState("loading");
    setThanksFetchError(null);

    void requestPromise.then(
      (data) => {
        if (!thanksMountedRef.current || requestId !== thanksRequestIdRef.current) {
          return;
        }
        thanksDataCache = data;
        setThanksData(data);
        setThanksLoadState("loaded");
      },
      () => {
        if (!thanksMountedRef.current || requestId !== thanksRequestIdRef.current) {
          return;
        }
        setThanksData(null);
        setThanksFetchError("Unable to load community acknowledgements right now.");
        setThanksLoadState("error");
      },
    );
  }, [thanksData, thanksLoadState]);

  const renderPersonLink = useCallback((person: ThankYouContributor | ThankYouSupporter, content: JSX.Element) => {
    if (!person.profileUrl) {
      return <div className="settings-person-card">{content}</div>;
    }

    return (
      <a className="settings-person-card settings-person-card--link" href={person.profileUrl} target="_blank" rel="noreferrer">
        {content}
      </a>
    );
  }, []);

  const thanksContributors = thanksData?.contributors ?? [];
  const thanksSupporters = thanksData?.supporters ?? [];
  const hasThanksError = Boolean(thanksFetchError || thanksData?.contributorsError || thanksData?.supportersError);

  const handleRetryThanks = useCallback(() => {
    thanksRequestIdRef.current += 1;
    thanksDataCache = null;
    setThanksData(null);
    setThanksFetchError(null);
    setThanksLoadState("idle");
  }, []);

  const renderContributorCard = useCallback((contributor: ThankYouContributor) => {
    return renderPersonLink(
      contributor,
      <>
        <img className="settings-person-avatar" src={contributor.avatarUrl} alt={contributor.login} loading="lazy" />
        <div className="settings-person-body">
          <div className="settings-person-title-row">
            <span className="settings-person-name">{contributor.login}</span>
            <span className="settings-person-badge">{t("settings.thanks.contributor")}</span>
          </div>
          <div className="settings-person-meta">
            <span>{contributor.contributions} contribution{contributor.contributions === 1 ? "" : "s"}</span>
            <ExternalLink size={14} />
          </div>
        </div>
      </>,
    );
  }, [renderPersonLink, t]);

  const renderSupporterCard = useCallback((supporter: ThankYouSupporter) => {
    const sourceLabel =
      supporter.isPrivate || supporter.source === "private"
        ? t("settings.thanks.privateSponsor")
        : supporter.source === "custom"
          ? t("settings.thanks.projectSponsor")
          : t("settings.thanks.githubSponsors");

    return renderPersonLink(
      supporter,
      <>
        <div className={`settings-person-avatar settings-person-avatar--fallback ${supporter.avatarUrl ? "" : "is-placeholder"}`.trim()}>
          {supporter.avatarUrl ? (
            <img className="settings-person-avatar" src={supporter.avatarUrl} alt={supporter.name} loading="lazy" />
          ) : (
            <Heart size={18} />
          )}
        </div>
        <div className="settings-person-body">
          <div className="settings-person-title-row">
            <span className="settings-person-name">{supporter.name || "Private"}</span>
            <span className="settings-person-badge settings-person-badge--supporter">{t("settings.thanks.supporter")}</span>
          </div>
          <div className="settings-person-meta">
            <span>{sourceLabel}</span>
            {supporter.profileUrl && <ExternalLink size={14} />}
          </div>
        </div>
      </>,
    );
  }, [renderPersonLink, t]);

  return (
    <div className="settings-thanks-layout">
      <section className="settings-section settings-thanks-hero">
        <div className="settings-thanks-hero-icon">
          <Heart size={18} />
        </div>
        <div className="settings-thanks-hero-copy">
          <h2>{t("settings.thanks.title")}</h2>
          <p>{t("settings.thanks.subtitle")}</p>
        </div>
      </section>

      {thanksFetchError && (
        <section className="settings-section settings-thanks-status settings-thanks-status--error">
          <strong>{t("settings.thanks.communityDataUnavailable")}</strong>
          <span>{thanksFetchError}</span>
          <div className="settings-thanks-actions">
            <button type="button" className="settings-chip settings-thanks-retry-btn" onClick={handleRetryThanks}>
              {t("app.actions.retry")}
            </button>
          </div>
        </section>
      )}

      <div className="settings-thanks-grid">
        <section className="settings-section">
          <div className="settings-section-header settings-section-header--thanks">
            <Users size={18} />
            <div>
              <h2>{t("settings.thanks.contributorsTitle")}</h2>
              <p className="settings-section-subtitle">{t("settings.thanks.contributorsSubtitle")}</p>
            </div>
          </div>
          {thanksLoadState === "loading" && !thanksData ? (
            <div className="settings-thanks-state">
              <MotionSpinner size={16} className="settings-loading-icon" />
              <span>{t("settings.thanks.loadingContributors")}</span>
            </div>
          ) : thanksContributors.length > 0 ? (
            <div className="settings-people-grid">
              {thanksContributors.map((contributor) => (
                <div key={contributor.login}>{renderContributorCard(contributor)}</div>
              ))}
            </div>
          ) : (
            <div className="settings-thanks-state settings-thanks-state--muted">
              <span>{thanksData?.contributorsError ?? t("settings.thanks.noContributors")}</span>
            </div>
          )}
        </section>

        <section className="settings-section">
          <div className="settings-section-header settings-section-header--thanks">
            <Heart size={18} />
            <div>
              <h2>{t("settings.thanks.supportersTitle")}</h2>
              <p className="settings-section-subtitle">{t("settings.thanks.supportersSubtitle")}</p>
            </div>
          </div>
          {thanksLoadState === "loading" && !thanksData ? (
            <div className="settings-thanks-state">
              <MotionSpinner size={16} className="settings-loading-icon" />
              <span>{t("settings.thanks.loadingSupporters")}</span>
            </div>
          ) : thanksSupporters.length > 0 ? (
            <div className="settings-people-grid">
              {thanksSupporters.map((supporter, index) => (
                <div key={`${supporter.name}-${supporter.profileUrl ?? index}`}>{renderSupporterCard(supporter)}</div>
              ))}
            </div>
          ) : (
            <div className="settings-thanks-state settings-thanks-state--muted">
              <span>{thanksData?.supportersError ?? t("settings.thanks.noSupporters")}</span>
            </div>
          )}
        </section>
      </div>

      {hasThanksError && thanksData && (
        <section className="settings-section settings-thanks-status">
          {thanksData.contributorsError && <span>{t("settings.thanks.contributorsTitle")}: {thanksData.contributorsError}</span>}
          {thanksData.supportersError && <span>{t("settings.thanks.supportersTitle")}: {thanksData.supportersError}</span>}
        </section>
      )}
    </div>
  );
}
