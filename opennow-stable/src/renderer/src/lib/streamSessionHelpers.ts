import type { StreamStatus } from "./appTypes";
import type { GfnWebRtcClient } from "../platforms/gfn/webrtcClient";

export type SignalingRecoveryState = {
  attemptCount: number;
  inFlight: Promise<boolean> | null;
  explicitShutdown: boolean;
  appId: number | null;
  generation: number;
};

export const RECOVERABLE_STREAM_STATUSES: readonly StreamStatus[] = ["streaming"];
export const SIGNALING_RECOVERY_ATTEMPT_DELAYS_MS = [0, 3000] as const;
export const SIGNALING_RECOVERY_STABLE_RESET_DELAY_MS = 15000;
export const SIGNALING_REMOTE_ICE_GRACE_MS = 5000;
export const ICE_DISCONNECTED_RECOVERY_GRACE_MS = 7000;

export function isExpectedNativeSessionClose(reason: string): boolean {
  const normalized = reason.trim().toLowerCase();
  return normalized === "bye" ||
    normalized === "peerremoved" ||
    normalized === "peer removed" ||
    normalized === "socket closed" ||
    normalized === "signaling disconnected: socket closed";
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

export async function readStreamClipboardText(): Promise<string> {
  try {
    const browserClipboard = navigator.clipboard;
    if (browserClipboard?.readText) {
      const text = await browserClipboard.readText();
      if (text) {
        return text;
      }
    }
  } catch {
    // Electron main-process clipboard is the reliable fallback on Linux.
  }

  return window.openNow.readClipboardText();
}

export async function sendStreamClipboardPaste(
  client: GfnWebRtcClient | null,
): Promise<void> {
  if (!client) {
    return;
  }

  const sentOfficialPaste = await client.pasteClipboardText();
  if (sentOfficialPaste) {
    return;
  }

  try {
    const text = await readStreamClipboardText();
    if (text) {
      client.sendText(text);
    }
    return;
  } catch (error) {
    console.warn("Clipboard read failed, falling back to paste shortcut:", error);
  }

  client.sendPasteShortcut(false);
}
