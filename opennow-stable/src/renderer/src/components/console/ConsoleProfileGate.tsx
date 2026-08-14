import { useCallback, useEffect, useRef, useState } from "react";
import type { JSX } from "react";
import type { SavedAccount } from "@shared/gfn";

import {
  appendPinDigit,
  deletePinDigit,
  getLockoutSecondsRemaining,
  isPinComplete,
  PIN_RECOVERY_HOLD_MS,
} from "../../lib/consolePinInput";
import { clampRowFocus, moveRowFocus, type RowFocusDirection } from "../../lib/consoleRowFocus";
import { isControllerKeyboardActivationTarget } from "../../lib/controllerKeyboard";
import { useControllerKeyDown, useControllerNavigation } from "../../hooks/useControllerNavigation";
import type { ConsoleShell } from "../../hooks/useConsoleShell";
import { controllerButton } from "../../utils/controllerGamepad";
import { useTranslation } from "../../i18n";
import { ConsoleManageProfiles } from "./ConsoleManageProfiles";
import { ConsoleOverlay } from "./ConsoleOverlay";
import { ConsolePinPad, PIN_KEYPAD_ROWS } from "./ConsolePinPad";
import { ConsoleProfilePicker } from "./ConsoleProfilePicker";
import { ConsoleSplash } from "./ConsoleSplash";

/** Picker tiles wrap at this many per row, matching the CSS grid. */
const PICKER_COLUMNS = 5;

export interface ConsoleProfileGateProps {
  shell: ConsoleShell;
  savedAccounts: SavedAccount[];
  activeUserId: string | null;
  onAddAccount: () => void;
  onProfilesChanged: () => Promise<void> | void;
  onRemoveAccount: (userId: string) => Promise<void> | void;
  onLogoutAll: () => void;
}

function buildPickerRowLengths(total: number): number[] {
  const rows: number[] = [];
  for (let remaining = total; remaining > 0; remaining -= PICKER_COLUMNS) {
    rows.push(Math.min(remaining, PICKER_COLUMNS));
  }
  return rows.length > 0 ? rows : [0];
}

/**
 * Stage router for the console profile gate, and the single owner of gamepad
 * navigation while it is open. The app shell behind it is `inert` (see the
 * `shellBlocked` wiring in App.tsx), so exactly one poller is ever live.
 *
 * Every gamepad action has a keyboard equivalent — the screens are otherwise
 * unverifiable, since CDP cannot synthesize Gamepad API input.
 */
export function ConsoleProfileGate({
  shell,
  savedAccounts,
  activeUserId,
  onAddAccount,
  onProfilesChanged,
  onRemoveAccount,
  onLogoutAll,
}: ConsoleProfileGateProps): JSX.Element | null {
  const { t } = useTranslation();
  const { stage, pickerEntries, pendingAccount, verifyResult } = shell;

  const [pickerIndex, setPickerIndex] = useState(0);
  const [pinEntry, setPinEntry] = useState("");
  const [pinFocus, setPinFocus] = useState({ rowIndex: 0, columnIndex: 0 });
  const [pinError, setPinError] = useState(false);
  const [recoveryProgress, setRecoveryProgress] = useState(0);
  const [recoveryArmed, setRecoveryArmed] = useState(false);
  const [recoveryActionIndex, setRecoveryActionIndex] = useState(0);
  const [recoveryBusy, setRecoveryBusy] = useState(false);
  const [recoveryError, setRecoveryError] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const recoveryStartedAtRef = useRef<number | null>(null);
  const submittingRef = useRef(false);

  const lockedUntilMs = verifyResult?.lockedUntilMs ?? null;
  const lockoutSeconds = getLockoutSecondsRemaining(lockedUntilMs, nowMs);
  const isLocked = lockoutSeconds > 0;

  // Only tick while a lockout is actually counting down.
  useEffect(() => {
    if (stage !== "pin" || lockedUntilMs === null) return undefined;
    const interval = window.setInterval(() => setNowMs(Date.now()), 500);
    return () => window.clearInterval(interval);
  }, [lockedUntilMs, stage]);

  useEffect(() => {
    setPinEntry("");
    setPinError(false);
    setPinFocus({ rowIndex: 0, columnIndex: 0 });
    setRecoveryProgress(0);
    setRecoveryArmed(false);
    setRecoveryActionIndex(0);
    setRecoveryBusy(false);
    setRecoveryError(null);
    recoveryStartedAtRef.current = null;
  }, [pendingAccount?.userId, stage]);

  useEffect(() => {
    setPickerIndex((index) => Math.max(0, Math.min(index, pickerEntries.length - 1)));
  }, [pickerEntries.length]);

  const submitPin = useCallback(async (entry: string) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    try {
      const ok = await shell.verifyPin(entry);
      if (!ok) {
        setPinError(true);
        setPinEntry("");
        setNowMs(Date.now());
      }
    } finally {
      submittingRef.current = false;
    }
  }, [shell]);

  const pressPinKey = useCallback((key: string) => {
    if (key === "cancel") {
      shell.cancelPin();
      return;
    }
    if (isLocked) return;
    if (key === "delete") {
      setPinEntry((entry) => deletePinDigit(entry));
      setPinError(false);
      return;
    }

    setPinError(false);
    setPinEntry((entry) => {
      const next = appendPinDigit(entry, key);
      if (isPinComplete(next)) void submitPin(next);
      return next;
    });
  }, [isLocked, shell, submitPin]);

  const movePinFocus = useCallback((direction: RowFocusDirection) => {
    setPinFocus((focus) => moveRowFocus(PIN_KEYPAD_ROWS.map((row) => row.length), focus, direction));
  }, []);

  const pressFocusedPinKey = useCallback(() => {
    const key = PIN_KEYPAD_ROWS[pinFocus.rowIndex]?.[pinFocus.columnIndex];
    if (key) pressPinKey(key);
  }, [pinFocus, pressPinKey]);

  const movePickerFocus = useCallback((direction: RowFocusDirection) => {
    const rowLengths = buildPickerRowLengths(pickerEntries.length);
    const current = clampRowFocus(rowLengths, {
      rowIndex: Math.floor(pickerIndex / PICKER_COLUMNS),
      columnIndex: pickerIndex % PICKER_COLUMNS,
    });
    const next = moveRowFocus(rowLengths, current, direction);
    setPickerIndex(Math.min(next.rowIndex * PICKER_COLUMNS + next.columnIndex, pickerEntries.length - 1));
  }, [pickerEntries.length, pickerIndex]);

  const beginRecoveryHold = useCallback(() => {
    if (recoveryStartedAtRef.current === null) recoveryStartedAtRef.current = performance.now();
  }, []);

  const endRecoveryHold = useCallback(() => {
    recoveryStartedAtRef.current = null;
    setRecoveryProgress(0);
  }, []);

  const confirmRecovery = useCallback(async () => {
    if (recoveryBusy) return;
    setRecoveryBusy(true);
    setRecoveryError(null);
    try {
      await shell.removePendingProfile();
    } catch {
      setRecoveryError(t("console.manage.pinFailed"));
    } finally {
      setRecoveryBusy(false);
    }
  }, [recoveryBusy, shell, t]);

  useControllerKeyDown(stage !== "shell" && stage !== "manage", (event) => {
    if ((event.key === "Enter" || event.key === " ") && isControllerKeyboardActivationTarget(event.target)) return;
    if (stage === "splash") {
      event.preventDefault();
      shell.skipSplash();
      return;
    }

    if (stage === "picker") {
      if (event.key === "ArrowLeft") { event.preventDefault(); movePickerFocus("left"); }
      else if (event.key === "ArrowRight") { event.preventDefault(); movePickerFocus("right"); }
      else if (event.key === "ArrowUp") { event.preventDefault(); movePickerFocus("up"); }
      else if (event.key === "ArrowDown") { event.preventDefault(); movePickerFocus("down"); }
      else if (shell.canClosePicker && (event.key === "Escape" || event.key.toLowerCase() === "b")) {
        event.preventDefault();
        shell.closeToShell();
      }
      else if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        void shell.selectPickerEntry(pickerIndex);
      }
      return;
    }

    if (stage === "pin") {
      if (recoveryArmed) {
        if (event.key === "Escape" || event.key.toLowerCase() === "b") {
          event.preventDefault();
          setRecoveryArmed(false);
        } else if (["ArrowLeft", "ArrowUp", "ArrowRight", "ArrowDown"].includes(event.key)) {
          event.preventDefault();
          setRecoveryActionIndex((index) => index === 0 ? 1 : 0);
        } else if (event.key === "Enter") {
          event.preventDefault();
          if (recoveryActionIndex === 0 && !recoveryBusy) {
            void confirmRecovery();
          } else {
            setRecoveryArmed(false);
          }
        }
        return;
      }
      if (/^[0-9]$/.test(event.key)) { event.preventDefault(); pressPinKey(event.key); }
      else if (event.key === "Backspace") { event.preventDefault(); pressPinKey("delete"); }
      else if (event.key === "Escape") { event.preventDefault(); shell.cancelPin(); }
      else if (event.key === "ArrowLeft") { event.preventDefault(); movePinFocus("left"); }
      else if (event.key === "ArrowRight") { event.preventDefault(); movePinFocus("right"); }
      else if (event.key === "ArrowUp") { event.preventDefault(); movePinFocus("up"); }
      else if (event.key === "ArrowDown") { event.preventDefault(); movePinFocus("down"); }
      else if (event.key === "Enter") { event.preventDefault(); pressFocusedPinKey(); }
      return;
    }

  });

  useControllerNavigation({
    enabled: stage !== "shell" && stage !== "manage",
    onFrame: ({ buttons, pressed }) => {
      if (stage === "splash") {
        if (pressed) shell.skipSplash();
        return;
      }

      if (stage === "picker") {
        if (pressed & controllerButton.left) movePickerFocus("left");
        if (pressed & controllerButton.right) movePickerFocus("right");
        if (pressed & controllerButton.up) movePickerFocus("up");
        if (pressed & controllerButton.down) movePickerFocus("down");
        if (shell.canClosePicker && (pressed & controllerButton.east)) shell.closeToShell();
        if (pressed & controllerButton.south) void shell.selectPickerEntry(pickerIndex);
        return;
      }

      if (stage === "pin") {
        if (recoveryArmed) {
          if (pressed & (controllerButton.left | controllerButton.up | controllerButton.right | controllerButton.down)) {
            setRecoveryActionIndex((index) => index === 0 ? 1 : 0);
          }
          if (pressed & controllerButton.east) setRecoveryArmed(false);
          if (pressed & controllerButton.south) {
            if (recoveryActionIndex === 0 && !recoveryBusy) {
              void confirmRecovery();
            } else {
              setRecoveryArmed(false);
            }
          }
          return;
        }
        // Hold B for the recovery option; a tap cancels. Progress is derived
        // from the live hold rather than a timer so releasing resets it.
        if (buttons & controllerButton.east) {
          beginRecoveryHold();
          const startedAt = recoveryStartedAtRef.current;
          if (startedAt !== null) {
            const progress = Math.min(1, (performance.now() - startedAt) / PIN_RECOVERY_HOLD_MS);
            setRecoveryProgress(progress);
            if (progress >= 1) {
              recoveryStartedAtRef.current = null;
              setRecoveryProgress(0);
              setRecoveryActionIndex(0);
              setRecoveryArmed(true);
            }
          }
        } else if (recoveryStartedAtRef.current !== null) {
          const wasArmed = recoveryArmed;
          endRecoveryHold();
          if (!wasArmed) shell.cancelPin();
        }

        if (pressed & controllerButton.left) movePinFocus("left");
        if (pressed & controllerButton.right) movePinFocus("right");
        if (pressed & controllerButton.up) movePinFocus("up");
        if (pressed & controllerButton.down) movePinFocus("down");
        if (pressed & controllerButton.south) pressFocusedPinKey();
        return;
      }
    },
  });

  if (stage === "shell") return null;

  return (
    <div className="console-gate" role="dialog" aria-modal="true" aria-label={t("console.profiles.whosPlaying")}>
      {stage === "splash" && <ConsoleSplash />}

      {stage === "picker" && (
        <ConsoleProfilePicker
          entries={pickerEntries}
          focusedIndex={pickerIndex}
          activeUserId={activeUserId}
          errorMessage={shell.errorMessage ?? undefined}
          onFocus={setPickerIndex}
          onSelect={(index) => void shell.selectPickerEntry(index)}
          onBack={shell.canClosePicker ? shell.closeToShell : undefined}
        />
      )}

      {stage === "pin" && pendingAccount && (
        <>
          <ConsolePinPad
            profileName={pendingAccount.displayName}
            entry={pinEntry}
            isError={pinError}
            errorMessage={t("console.pin.incorrect", { count: verifyResult?.remainingAttempts ?? 0 })}
            lockedMessage={t("console.pin.lockedOut", { seconds: lockoutSeconds })}
            isLocked={isLocked}
            focusedRow={pinFocus.rowIndex}
            focusedColumn={pinFocus.columnIndex}
            onFocusKey={(rowIndex, columnIndex) => setPinFocus({ rowIndex, columnIndex })}
            onPressKey={pressPinKey}
            recoveryProgress={recoveryProgress}
            recoveryHint={recoveryArmed ? t("console.pin.recoveryReady") : t("console.pin.recoveryHint")}
          />
          <button
            type="button"
            className="console-action console-action--secondary console-pin-recovery-action"
            onClick={() => { setRecoveryActionIndex(0); setRecoveryArmed(true); }}
          >
            {t("console.pin.forgotPin")}
          </button>
          {recoveryArmed && (
            <ConsoleOverlay label={t("console.pin.removeProfile")} title={t("console.pin.recoveryPrompt")}>
              {recoveryError && <p className="console-gate-error">{recoveryError}</p>}
              <div className="console-overlay-actions">
                <button
                  type="button"
                  className={`console-action console-action--danger${recoveryActionIndex === 0 ? " is-focused" : ""}`}
                  disabled={recoveryBusy}
                  onFocus={() => setRecoveryActionIndex(0)}
                  onClick={() => void confirmRecovery()}
                >
                  {t("console.pin.removeProfile")}
                </button>
                <button
                  type="button"
                  className={`console-action console-action--secondary${recoveryActionIndex === 1 ? " is-focused" : ""}`}
                  onFocus={() => setRecoveryActionIndex(1)}
                  onClick={() => { setRecoveryArmed(false); endRecoveryHold(); }}
                >
                  {t("app.actions.back")}
                </button>
              </div>
            </ConsoleOverlay>
          )}
        </>
      )}

      {stage === "manage" && (
        <ConsoleManageProfiles
          savedAccounts={savedAccounts}
          activeUserId={activeUserId}
          onAddAccount={onAddAccount}
          onProfilesChanged={onProfilesChanged}
          onRemoveAccount={onRemoveAccount}
          onLogoutAll={onLogoutAll}
          onBack={shell.openPicker}
        />
      )}
    </div>
  );
}
