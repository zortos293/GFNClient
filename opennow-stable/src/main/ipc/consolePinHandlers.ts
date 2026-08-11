import { ipcMain } from "electron";

import type {
  ConsolePinClearRequest,
  ConsolePinMutationResult,
  ConsolePinSetRequest,
  ConsolePinStatus,
  ConsolePinVerifyRequest,
  ConsolePinVerifyResult,
} from "@shared/gfn";
import { IPC_CHANNELS } from "@shared/ipc";

import type { ConsoleProfileStore } from "../platforms/gfn/auth/consoleProfileStore";
import { PIN_MAX_ATTEMPTS } from "../platforms/gfn/auth/pinPolicy";

export interface ConsolePinIpcDependencies {
  getConsoleProfiles: () => ConsoleProfileStore;
  isSavedAccount: (userId: string) => boolean;
}

/**
 * Verify-only PIN surface for the renderer.
 *
 * Nothing here ever returns a hash, salt, or KDF parameter — the renderer only
 * learns whether a PIN exists, whether a guess was right, and how long it is
 * locked out for.
 */
export function registerConsolePinIpcHandlers(deps: ConsolePinIpcDependencies): void {
  ipcMain.handle(
    IPC_CHANNELS.CONSOLE_PIN_GET_STATUS,
    (_event, userId: string): ConsolePinStatus => deps.isSavedAccount(userId)
      ? deps.getConsoleProfiles().getStatus(userId, Date.now())
      : { userId, hasPin: false, lockedUntilMs: null, remainingAttempts: PIN_MAX_ATTEMPTS },
  );

  ipcMain.handle(
    IPC_CHANNELS.CONSOLE_PIN_SET,
    (_event, input: ConsolePinSetRequest): Promise<ConsolePinMutationResult> => deps.isSavedAccount(input.userId)
      ? deps.getConsoleProfiles().setPin(input.userId, input.pin, input.currentPin)
      : Promise.resolve({ ok: false, reason: "unknown_account", hasPin: false }),
  );

  ipcMain.handle(
    IPC_CHANNELS.CONSOLE_PIN_CLEAR,
    (_event, input: ConsolePinClearRequest): Promise<ConsolePinMutationResult> => deps.isSavedAccount(input.userId)
      ? deps.getConsoleProfiles().clearPin(input.userId, input.currentPin)
      : Promise.resolve({ ok: false, reason: "unknown_account", hasPin: false }),
  );

  ipcMain.handle(
    IPC_CHANNELS.CONSOLE_PIN_VERIFY,
    (_event, input: ConsolePinVerifyRequest): Promise<ConsolePinVerifyResult> => deps.isSavedAccount(input.userId)
      ? deps.getConsoleProfiles().verifyPin(input.userId, input.pin)
      : Promise.resolve({
        ok: false,
        reason: "unknown_account",
        remainingAttempts: 0,
        lockedUntilMs: null,
      }),
  );
}
