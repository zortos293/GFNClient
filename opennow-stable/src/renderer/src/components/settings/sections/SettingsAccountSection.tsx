import { HardDrive, Users, ExternalLink, RefreshCcw, Trash2, AlertTriangle, Check } from "lucide-react";
import { useCallback, useEffect, useState, type JSX } from "react";
import type { GameAccountConnection, Settings, SubscriptionInfo } from "@shared/gfn";
import { GFN_STORAGE_MANAGER_URL } from "@shared/gfn";
import { useTranslation } from "../../../i18n";
import { SelectDropdown } from "../../ui/SelectDropdown";
import { formatGameAccountSyncDate, formatStorageGb } from "../settingsFormatters";
import type { GameAccountBusyAction, StorageResetState } from "../settingsTypes";
import { MotionSpinner } from "../../MotionSpinner";

export interface SettingsAccountSectionProps {
  settings: Settings;
  showAll: boolean;
  subscriptionInfo: SubscriptionInfo | null;
  subscriptionLoading: boolean;
}

export function SettingsAccountSection({
  settings,
  showAll,
  subscriptionInfo,
  subscriptionLoading,
}: SettingsAccountSectionProps): JSX.Element {
  const { t } = useTranslation();
  const [storageResetState, setStorageResetState] = useState<StorageResetState>("idle");
  const [storageResetMessage, setStorageResetMessage] = useState<string | null>(null);
  const [gameAccounts, setGameAccounts] = useState<GameAccountConnection[]>([]);
  const [gameAccountsLoading, setGameAccountsLoading] = useState(true);
  const [gameAccountsError, setGameAccountsError] = useState<string | null>(null);
  const [gameAccountStatusMessage, setGameAccountStatusMessage] = useState<string | null>(null);
  const [gameAccountBusy, setGameAccountBusy] = useState<{
    provider: string;
    action: GameAccountBusyAction;
  } | null>(null);

  const persistentStorage = subscriptionInfo?.storageAddon;
  const persistentStorageSizeGb = typeof persistentStorage?.sizeGb === "number" ? persistentStorage.sizeGb : null;
  const persistentStorageUsedGb = typeof persistentStorage?.usedGb === "number" ? persistentStorage.usedGb : null;
  const persistentStorageRemainingGb =
    persistentStorageSizeGb !== null && persistentStorageUsedGb !== null
      ? Math.max(0, persistentStorageSizeGb - persistentStorageUsedGb)
      : null;
  const persistentStorageUsagePercent =
    persistentStorageSizeGb !== null && persistentStorageSizeGb > 0 && persistentStorageUsedGb !== null
      ? Math.max(0, Math.min(100, (persistentStorageUsedGb / persistentStorageSizeGb) * 100))
      : null;
  const persistentStorageSizeLabel = formatStorageGb(persistentStorage?.sizeGb);
  const persistentStorageUsedLabel = formatStorageGb(persistentStorage?.usedGb);
  const persistentStorageRemainingLabel = formatStorageGb(persistentStorageRemainingGb ?? undefined);
  const persistentStorageUsageLabel = persistentStorage
    ? persistentStorageUsedLabel && persistentStorageSizeLabel
      ? t("settings.persistentStorage.usage", {
        used: persistentStorageUsedLabel,
        total: persistentStorageSizeLabel,
      })
      : t("settings.persistentStorage.usageUnavailable")
    : subscriptionLoading
      ? t("app.status.loading")
      : t("settings.persistentStorage.notDetected");
  const persistentStorageRegionLabel =
    persistentStorage?.regionName ??
    persistentStorage?.regionCode ??
    (persistentStorage ? t("settings.persistentStorage.regionUnavailable") : null);
  const persistentStorageDetails = [
    persistentStorageRegionLabel
      ? t("settings.persistentStorage.region", { region: persistentStorageRegionLabel })
      : null,
    persistentStorageRemainingLabel
      ? t("settings.persistentStorage.remaining", { value: persistentStorageRemainingLabel })
      : null,
  ].filter((value): value is string => Boolean(value));
  const currentStorageLocationOptionLabel = persistentStorageRegionLabel
    ? t("settings.persistentStorage.currentLocationOption", { region: persistentStorageRegionLabel })
    : t("settings.persistentStorage.currentLocationUnavailable");
  const storageResetTargetLabel = persistentStorageRegionLabel ?? t("settings.persistentStorage.regionUnavailable");
  const storageResetTargetHint = t("settings.persistentStorage.resetRequiresBrowserHint", {
    region: storageResetTargetLabel,
  });

  const handleOpenPersistentStorageManager = useCallback(async (): Promise<void> => {
    try {
      await window.openNow.openExternalUrl(GFN_STORAGE_MANAGER_URL);
    } catch (error) {
      console.error("[Settings] Failed to open NVIDIA Storage Manager:", error);
      setStorageResetState("error");
      setStorageResetMessage(t("settings.persistentStorage.openManagerFailed"));
    }
  }, [t]);

  const handleResetPersistentStorage = useCallback(async (): Promise<void> => {
    if (!persistentStorage) {
      return;
    }

    setStorageResetState("idle");
    setStorageResetMessage(t("settings.persistentStorage.resetRequiresBrowser"));
    await handleOpenPersistentStorageManager();
  }, [handleOpenPersistentStorageManager, persistentStorage, t]);

  const loadGameAccounts = useCallback(async (isCancelled: () => boolean = () => false): Promise<void> => {
    setGameAccountsLoading(true);
    setGameAccountsError(null);
    setGameAccountStatusMessage(null);

    try {
      const result = await window.openNow.fetchGameAccountConnections();
      if (!isCancelled()) {
        setGameAccounts(result.accounts);
      }
    } catch (error) {
      console.warn("[Settings] Failed to fetch game account connections:", error);
      if (!isCancelled()) {
        setGameAccounts([]);
        setGameAccountsError(
          error instanceof Error && error.message.includes("No authenticated session")
            ? t("settings.accountConnections.signInRequired")
            : t("settings.accountConnections.loadFailed"),
        );
      }
    } finally {
      if (!isCancelled()) {
        setGameAccountsLoading(false);
      }
    }
  }, [t]);

  useEffect(() => {
    let cancelled = false;
    void loadGameAccounts(() => cancelled);
    return () => { cancelled = true; };
  }, [loadGameAccounts]);

  const handleGameAccountAction = useCallback(async (
    account: GameAccountConnection,
    action: GameAccountBusyAction,
  ): Promise<void> => {
    if (gameAccountBusy) {
      return;
    }

    if (
      action === "unlink" &&
      !window.confirm(t("settings.accountConnections.unlinkConfirm", { provider: account.label }))
    ) {
      return;
    }

    setGameAccountBusy({ provider: account.provider, action });
    setGameAccountStatusMessage(null);
    setGameAccountsError(null);

    try {
      const payload = {
        provider: account.provider,
        proxyUrl: settings.sessionProxyEnabled ? settings.sessionProxyUrl.trim() || undefined : undefined,
      };
      const result =
        action === "link"
          ? await window.openNow.linkGameAccount(payload)
          : action === "unlink"
            ? await window.openNow.unlinkGameAccount(payload)
            : await window.openNow.resyncGameAccount(payload);
      setGameAccounts(result.accounts);
      setGameAccountStatusMessage(
        result.message ??
        (action === "link"
          ? t("settings.accountConnections.linkSuccess", { provider: account.label })
          : action === "unlink"
            ? t("settings.accountConnections.unlinkSuccess", { provider: account.label })
            : t("settings.accountConnections.resyncSuccess", { provider: account.label })),
      );
    } catch (error) {
      console.error(`[Settings] Game account ${action} failed:`, error);
      setGameAccountsError(
        error instanceof Error && error.message
          ? error.message
          : t("settings.accountConnections.actionFailed"),
      );
    } finally {
      setGameAccountBusy(null);
    }
  }, [gameAccountBusy, settings.sessionProxyEnabled, settings.sessionProxyUrl, t]);

  const renderGameAccountCard = useCallback((account: GameAccountConnection) => {
    const busyAction = gameAccountBusy?.provider === account.provider ? gameAccountBusy.action : null;
    const isBusy = Boolean(gameAccountBusy);
    const syncDateLabel = formatGameAccountSyncDate(account.syncDate);
    const statusLabel =
      account.status === "expired"
        ? t("settings.accountConnections.statusExpired")
        : account.status === "sync_error"
          ? t("settings.accountConnections.statusSyncError")
          : account.isConnected
            ? t("settings.accountConnections.statusConnected")
            : t("settings.accountConnections.statusNotConnected");
    const statusClass =
      account.status === "expired" || account.status === "sync_error"
        ? "settings-inline-badge--updater-error"
        : account.isConnected
          ? "settings-inline-badge--codec-gpu"
          : "settings-inline-badge--codec-testing";
    const canLink = account.supportsLinking && (!account.isConnected || account.status === "expired");
    const canUnlink = account.isConnected && account.status !== "expired";
    const canSyncOnly = account.supportsSync && !account.supportsLinking && !account.isConnected;
    const primaryAction: GameAccountBusyAction =
      canUnlink ? "unlink" : canLink ? "link" : "resync";
    const primaryLabel =
      primaryAction === "unlink"
        ? t("settings.accountConnections.unlink")
        : primaryAction === "resync"
          ? t("settings.accountConnections.sync")
          : account.status === "expired"
            ? t("settings.accountConnections.reconnect")
            : t("settings.accountConnections.connect");
    const primaryIcon =
      primaryAction === "unlink"
        ? <Trash2 size={15} />
        : primaryAction === "resync"
          ? <RefreshCcw size={15} />
          : <ExternalLink size={15} />;
    const hasPrimaryAction = canUnlink || canLink || canSyncOnly;

    return (
      <div key={account.provider} className="settings-game-account-card">
        <div className="settings-game-account-main">
          <div className="settings-game-account-icon">
            {account.iconUrl ? (
              <img src={account.iconUrl} alt="" loading="lazy" />
            ) : (
              <Users size={18} />
            )}
          </div>
          <div className="settings-game-account-copy">
            <div className="settings-game-account-title-row">
              <h3>{account.label}</h3>
              <span className={`settings-inline-badge settings-inline-badge--codec ${statusClass}`}>
                {statusLabel}
              </span>
            </div>
            <p>
              {account.isConnected
                ? account.displayName || t("settings.accountConnections.connectedAccount")
                : account.supportsSync && !account.supportsLinking
                  ? t("settings.accountConnections.syncOnlyDescription")
                  : t("settings.accountConnections.notConnectedDescription")}
            </p>
            <div className="settings-game-account-features">
              {account.supportsLinking && <span>{t("settings.accountConnections.ssoSupported")}</span>}
              {account.supportsSync && <span>{t("settings.accountConnections.syncSupported")}</span>}
              {account.isRequired && <span>{t("settings.accountConnections.requiredForSomeGames")}</span>}
            </div>
            {account.supportsSync && account.isConnected ? (
              <p className="settings-game-account-sync">
                {account.syncState === "SYNC_SUCCESS"
                  ? t("settings.accountConnections.syncSummary", {
                    count: account.syncedGames,
                    date: syncDateLabel ?? t("settings.accountConnections.recently"),
                  })
                  : t("settings.accountConnections.syncState", {
                    state: account.syncState ?? t("settings.accountConnections.syncUnknown"),
                  })}
              </p>
            ) : null}
          </div>
        </div>
        <div className="settings-game-account-actions">
          {account.supportsSync && account.isConnected ? (
            <button
              type="button"
              className="settings-chip settings-game-account-action"
              disabled={isBusy}
              onClick={() => {
                void handleGameAccountAction(account, "resync");
              }}
            >
              {busyAction === "resync" ? <MotionSpinner size={15} label="Resyncing" /> : <RefreshCcw size={15} />}
              {busyAction === "resync"
                ? t("settings.accountConnections.resyncing")
                : t("settings.accountConnections.resync")}
            </button>
          ) : null}
          {hasPrimaryAction ? (
            <button
              type="button"
              className={`settings-game-account-action ${
                primaryAction === "unlink" ? "settings-delete-cache-btn" : "settings-chip"
              }`}
              disabled={isBusy}
              onClick={() => {
                void handleGameAccountAction(account, primaryAction);
              }}
            >
              {busyAction === primaryAction ? <MotionSpinner size={15} label="Updating" /> : primaryIcon}
              {busyAction === primaryAction
                ? primaryAction === "unlink"
                  ? t("settings.accountConnections.unlinking")
                  : primaryAction === "resync"
                    ? t("settings.accountConnections.syncing")
                    : t("settings.accountConnections.connecting")
                : primaryLabel}
            </button>
          ) : null}
        </div>
      </div>
    );
  }, [gameAccountBusy, handleGameAccountAction, t]);


  return (
    <>
    <section className="settings-section settings-storage-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.account")}</div>}
      <div className="settings-section-header settings-section-header--with-copy">
        <HardDrive size={18} />
        <div>
          <h2>{t("settings.persistentStorage.title")}</h2>
          <p className="settings-section-subtitle">{t("settings.persistentStorage.description")}</p>
        </div>
      </div>
      <div className="settings-storage-card">
        <div className="settings-storage-summary">
          <div className="settings-storage-headline">
            <HardDrive size={18} />
            {persistentStorage ? (
              persistentStorageUsedLabel && persistentStorageSizeLabel ? (
                <span>
                  <strong>{persistentStorageUsedLabel}</strong>{" "}
                  {t("settings.persistentStorage.usedOutOf", { total: persistentStorageSizeLabel })}
                </span>
              ) : (
                <span>{t("settings.persistentStorage.usageUnavailable")}</span>
              )
            ) : (
              <span>{persistentStorageUsageLabel}</span>
            )}
          </div>
          {persistentStorage ? (
            <span className="settings-inline-badge settings-inline-badge--codec settings-inline-badge--codec-gpu">
              {t("settings.persistentStorage.detected")}
            </span>
          ) : null}
        </div>

        {persistentStorageUsagePercent !== null ? (
          <div
            className="settings-storage-meter"
            role="progressbar"
            aria-label={t("settings.persistentStorage.meterLabel")}
            aria-valuemin={0}
            aria-valuemax={persistentStorageSizeGb ?? 100}
            aria-valuenow={persistentStorageUsedGb ?? 0}
          >
            <div
              className="settings-storage-meter-used"
              style={{ width: `${persistentStorageUsagePercent}%` }}
            />
          </div>
        ) : (
          <div className="settings-storage-meter settings-storage-meter--empty" />
        )}

        <div className="settings-storage-legend">
          <span>
            <span className="settings-storage-legend-dot settings-storage-legend-dot--used" />
            {persistentStorageUsedLabel
              ? t("settings.persistentStorage.usedLegend", { value: persistentStorageUsedLabel })
              : t("settings.persistentStorage.usedLegendUnavailable")}
          </span>
          <span>
            <span className="settings-storage-legend-dot settings-storage-legend-dot--available" />
            {persistentStorageRemainingLabel
              ? t("settings.persistentStorage.availableLegend", { value: persistentStorageRemainingLabel })
              : t("settings.persistentStorage.availableLegendUnavailable")}
          </span>
        </div>

        <div className="settings-storage-location-control">
          <label className="settings-storage-location-copy" htmlFor="persistent-storage-reset-region">
            <span>{t("settings.persistentStorage.locationTitle")}</span>
            <span>{storageResetTargetHint}</span>
          </label>
          <SelectDropdown
            id="persistent-storage-reset-region"
            className="settings-storage-select-dropdown"
            value=""
            options={[{ value: "", label: currentStorageLocationOptionLabel }]}
            onChange={() => {}}
            disabled
            ariaLabel={t("settings.persistentStorage.locationTitle")}
          />
        </div>

        <div className="settings-storage-footer">
          <div className="settings-storage-meta">
            {persistentStorageDetails.length > 0 ? (
              <span>{persistentStorageDetails.join(" / ")}</span>
            ) : null}
            <span>{t("settings.persistentStorage.resetHint")}</span>
            {storageResetMessage ? (
              <span className={`settings-storage-message settings-storage-message--${storageResetState}`}>
                {storageResetMessage}
              </span>
            ) : null}
          </div>
          <div className="settings-storage-actions">
            <button
              type="button"
              className="settings-export-logs-btn settings-storage-manager-btn"
              disabled={!persistentStorage || subscriptionLoading}
              onClick={() => {
                void handleResetPersistentStorage();
              }}
            >
              <ExternalLink size={16} />
              {t("settings.persistentStorage.openManager")}
            </button>
          </div>
        </div>
      </div>
    </section>
    <section className="settings-section settings-game-accounts-section">
      {showAll && <div className="settings-section-context">{t("settings.sections.account")}</div>}
      <div className="settings-section-header settings-section-header--with-copy settings-game-accounts-header">
        <Users size={18} />
        <div>
          <h2>{t("settings.accountConnections.title")}</h2>
          <p className="settings-section-subtitle">{t("settings.accountConnections.description")}</p>
        </div>
        <button
          type="button"
          className="settings-chip settings-game-account-refresh"
          disabled={gameAccountsLoading || Boolean(gameAccountBusy)}
          onClick={() => {
            void loadGameAccounts();
          }}
        >
          {gameAccountsLoading ? <MotionSpinner size={15} label="Refreshing accounts" /> : <RefreshCcw size={15} />}
          {t("settings.accountConnections.refresh")}
        </button>
      </div>
      <div className="settings-game-accounts-card">
        {gameAccountsError ? (
          <div className="settings-game-account-state settings-game-account-state--error">
            <AlertTriangle size={16} />
            <span>{gameAccountsError}</span>
          </div>
        ) : null}
        {gameAccountStatusMessage ? (
          <div className="settings-game-account-state settings-game-account-state--success">
            <Check size={16} />
            <span>{gameAccountStatusMessage}</span>
          </div>
        ) : null}
        {gameAccountsLoading ? (
          <div className="settings-game-account-state settings-game-account-state--muted">
            <MotionSpinner size={16} />
            <span>{t("settings.accountConnections.loading")}</span>
          </div>
        ) : gameAccounts.length > 0 ? (
          <div className="settings-game-account-grid">
            {gameAccounts.map((account) => renderGameAccountCard(account))}
          </div>
        ) : (
          <div className="settings-game-account-state settings-game-account-state--muted">
            <Users size={16} />
            <span>{t("settings.accountConnections.empty")}</span>
          </div>
        )}
      </div>
    </section>
    </>
  );
}
