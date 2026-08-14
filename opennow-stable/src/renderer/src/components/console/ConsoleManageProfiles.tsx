import { useCallback, useEffect, useRef, useState } from "react";
import type { CSSProperties, JSX } from "react";
import type { ConsolePinMutationResult, SavedAccount } from "@shared/gfn";

import { isPinComplete } from "../../lib/consolePinInput";
import { useControllerKeyDown, useControllerNavigation } from "../../hooks/useControllerNavigation";
import { controllerButton } from "../../utils/controllerGamepad";
import { useTranslation } from "../../i18n";
import { ConsoleHintBar } from "./ConsoleHintBar";
import { ConsoleOverlay } from "./ConsoleOverlay";

type PinDialog =
  | { mode: "set"; account: SavedAccount }
  | { mode: "change"; account: SavedAccount }
  | { mode: "clear"; account: SavedAccount }
  | null;

export interface ConsoleManageProfilesProps {
  savedAccounts: SavedAccount[];
  activeUserId: string | null;
  onAddAccount: () => void;
  onProfilesChanged: () => Promise<void> | void;
  onRemoveAccount: (userId: string) => Promise<void> | void;
  onLogoutAll: () => void;
  onBack: () => void;
}

function describeFailure(result: ConsolePinMutationResult, t: (key: string) => string): string {
  switch (result.reason) {
    case "invalid_pin": return t("console.manage.wrongPin");
    case "invalid_format": return t("console.manage.pinFormat");
    case "locked_out": return t("console.manage.lockedOut");
    case "no_pin_set": return t("console.manage.noPinSet");
    default: return t("console.manage.pinFailed");
  }
}

/**
 * Per-profile PIN management. Uses plain text inputs rather than the console
 * keypad because this screen is reachable from a desktop too, and the inputs
 * are `inputMode="numeric"` so a TV keyboard still shows digits.
 */
export function ConsoleManageProfiles({
  savedAccounts,
  activeUserId,
  onAddAccount,
  onProfilesChanged,
  onRemoveAccount,
  onLogoutAll,
  onBack,
}: ConsoleManageProfilesProps): JSX.Element {
  const { t } = useTranslation();
  const [dialog, setDialog] = useState<PinDialog>(null);
  const [currentPin, setCurrentPin] = useState("");
  const [nextPin, setNextPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [focusedControlIndex, setFocusedControlIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement | null>(null);

  const closeDialog = useCallback(() => {
    setDialog(null);
    setCurrentPin("");
    setNextPin("");
    setError(null);
  }, []);

  const submit = useCallback(async () => {
    if (!dialog || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = dialog.mode === "clear"
        ? await window.openNow.clearConsolePin({ userId: dialog.account.userId, currentPin })
        : await window.openNow.setConsolePin({
          userId: dialog.account.userId,
          pin: nextPin,
          currentPin: dialog.mode === "change" ? currentPin : undefined,
        });

      if (!result.ok) {
        setError(describeFailure(result, t));
        setCurrentPin("");
        setNextPin("");
        return;
      }
      await onProfilesChanged();
      closeDialog();
    } catch {
      setError(t("console.manage.pinFailed"));
    } finally {
      setBusy(false);
    }
  }, [busy, closeDialog, currentPin, dialog, nextPin, onProfilesChanged, t]);

  const canSubmit = dialog !== null && !busy && (dialog.mode === "clear"
    ? isPinComplete(currentPin)
    : isPinComplete(nextPin) && (dialog.mode === "set" || isPinComplete(currentPin)));

  const getControls = useCallback(() => Array.from(
    rootRef.current?.querySelectorAll<HTMLElement>("[data-console-manage-control]:not(:disabled)") ?? [],
  ), []);

  const focusControl = useCallback((index: number) => {
    const controls = getControls();
    if (controls.length === 0) return;
    const nextIndex = Math.max(0, Math.min(index, controls.length - 1));
    setFocusedControlIndex(nextIndex);
    controls[nextIndex]?.focus({ preventScroll: true });
    controls[nextIndex]?.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
  }, [getControls]);

  const moveControl = useCallback((delta: -1 | 1) => {
    const controls = getControls();
    const activeIndex = controls.findIndex((control) => control === document.activeElement || control.contains(document.activeElement));
    focusControl((activeIndex >= 0 ? activeIndex : focusedControlIndex) + delta);
  }, [focusControl, focusedControlIndex, getControls]);

  const activateFocusedControl = useCallback(() => {
    const controls = getControls();
    const active = controls.find((control) => control === document.activeElement || control.contains(document.activeElement));
    (active ?? controls[focusedControlIndex])?.click();
  }, [focusedControlIndex, getControls]);

  useEffect(() => {
    setFocusedControlIndex(0);
    const frame = window.requestAnimationFrame(() => focusControl(0));
    return () => window.cancelAnimationFrame(frame);
  }, [dialog, focusControl]);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    const handleFocusIn = (event: FocusEvent): void => {
      const control = event.target instanceof Element
        ? event.target.closest<HTMLElement>("[data-console-manage-control]")
        : null;
      if (!control) return;
      const index = getControls().indexOf(control);
      if (index >= 0) setFocusedControlIndex(index);
    };
    root.addEventListener("focusin", handleFocusIn);
    return () => root.removeEventListener("focusin", handleFocusIn);
  }, [getControls]);

  useControllerKeyDown(true, (event) => {
    if (event.key === "Escape" || event.key.toLowerCase() === "b") {
      event.preventDefault();
      if (dialog) closeDialog();
      else onBack();
      return;
    }
    if (["ArrowLeft", "ArrowUp"].includes(event.key)) {
      event.preventDefault();
      moveControl(-1);
      return;
    }
    if (["ArrowRight", "ArrowDown"].includes(event.key)) {
      event.preventDefault();
      moveControl(1);
      return;
    }
    if (event.key === "Enter" && event.target instanceof HTMLInputElement) {
      event.preventDefault();
      if (canSubmit) void submit();
    }
  });

  useControllerNavigation({
    enabled: true,
    onFrame: ({ pressed }) => {
      if (pressed & controllerButton.east) {
        if (dialog) closeDialog();
        else onBack();
        return;
      }
      if (pressed & (controllerButton.left | controllerButton.up)) moveControl(-1);
      if (pressed & (controllerButton.right | controllerButton.down)) moveControl(1);
      if (pressed & controllerButton.south) activateFocusedControl();
    },
  });

  return (
    <div className="console-manage" ref={rootRef}>
      <h1 className="console-gate-title">{t("console.manage.title")}</h1>
      <p className="console-gate-subtitle">{t("console.manage.subtitle")}</p>

      <div className="console-manage-list">
        {savedAccounts.map((account, index) => (
          <div key={account.userId} className="console-manage-row" style={{ "--i": index } as CSSProperties}>
            <span className="console-manage-avatar">
              {account.avatarUrl
                ? <img src={account.avatarUrl} alt="" />
                : account.displayName.slice(0, 1).toUpperCase()}
            </span>
            <span className="console-manage-identity">
              <span className="console-manage-name">{account.displayName}</span>
              <span className="console-manage-detail">
                {account.email ?? account.providerCode}
                {account.hasPin ? ` · ${t("console.manage.pinOn")}` : ""}
                {account.userId === activeUserId ? ` · ${t("console.profiles.signedIn")}` : ""}
              </span>
            </span>
            <span className="console-manage-actions">
              {account.hasPin ? (
                <>
                  <button
                    type="button"
                    className="console-action console-action--secondary"
                    data-console-manage-control
                    onClick={() => { closeDialog(); setDialog({ mode: "change", account }); }}
                  >
                    {t("console.manage.changePin")}
                  </button>
                  <button
                    type="button"
                    className="console-action console-action--secondary"
                    data-console-manage-control
                    onClick={() => { closeDialog(); setDialog({ mode: "clear", account }); }}
                  >
                    {t("console.manage.removePin")}
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="console-action console-action--secondary"
                  data-console-manage-control
                  onClick={() => { closeDialog(); setDialog({ mode: "set", account }); }}
                >
                  {t("console.manage.setPin")}
                </button>
              )}
              {savedAccounts.length > 1 && account.userId !== activeUserId && (
                <button
                  type="button"
                  className="console-action console-action--danger"
                  data-console-manage-control
                  onClick={() => {
                    void Promise.resolve(onRemoveAccount(account.userId)).catch(() => setError(t("console.manage.pinFailed")));
                  }}
                >
                  {t("auth.accounts.removeAccount")}
                </button>
              )}
            </span>
          </div>
        ))}
      </div>

      <div className="console-overlay-actions">
        <button type="button" className="console-action console-action--secondary" data-console-manage-control onClick={onAddAccount}>
          {t("auth.accounts.addAccount")}
        </button>
        <button type="button" className="console-action console-action--danger" data-console-manage-control onClick={onLogoutAll}>
          {t("auth.accounts.signOutAllAccounts")}
        </button>
      </div>

      {/* The PIN separates profiles on a shared TV; it is not encryption, and
          saying so up front is better than implying a guarantee we cannot make. */}
      <p className="console-manage-note">{t("console.manage.securityNote")}</p>

      <ConsoleHintBar hints={[{ glyph: "b", label: t("app.actions.back"), onSelect: onBack }]} />

      {dialog && (
        <ConsoleOverlay
          label={t("console.manage.title")}
          eyebrow={dialog.account.displayName}
          title={dialog.mode === "clear"
            ? t("console.manage.removePin")
            : dialog.mode === "change" ? t("console.manage.changePin") : t("console.manage.setPin")}
        >
          {dialog.mode !== "set" && (
            <input
              className="console-search-input"
              data-console-manage-control
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={4}
              value={currentPin}
              placeholder={t("console.manage.currentPin")}
              aria-label={t("console.manage.currentPin")}
              onChange={(event) => setCurrentPin(event.target.value.replace(/\D/g, ""))}
            />
          )}
          {dialog.mode !== "clear" && (
            <input
              className="console-search-input"
              data-console-manage-control
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={4}
              value={nextPin}
              placeholder={t("console.manage.newPin")}
              aria-label={t("console.manage.newPin")}
              onChange={(event) => setNextPin(event.target.value.replace(/\D/g, ""))}
            />
          )}
          {error && <p className="console-gate-error">{error}</p>}
          <div className="console-overlay-actions">
            <button
              type="button"
              className="console-action console-action--primary"
              disabled={!canSubmit}
              data-console-manage-control
              onClick={() => void submit()}
            >
              {t("app.actions.save")}
            </button>
            <button type="button" className="console-action console-action--secondary" data-console-manage-control onClick={closeDialog}>
              {t("app.actions.back")}
            </button>
          </div>
        </ConsoleOverlay>
      )}
    </div>
  );
}
