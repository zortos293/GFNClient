import type { ActiveSessionInfo, GfnSessionQueueState, SessionInfo } from "@shared/gfn";
import { isGfnSessionInQueue } from "@shared/gfn";
import type { DiscordActivityKind, DiscordActivityUpdate } from "@shared/discord";

export interface DiscordPresenceSessionState extends GfnSessionQueueState {
  appId?: number | string;
}

export function discordActivityKindForSession(session: DiscordPresenceSessionState): DiscordActivityKind | null {
  if (session.status === 3) {
    return "streaming";
  }
  if (isGfnSessionInQueue(session) || session.status === 1) {
    return "queued";
  }
  if (session.status === 2) {
    return "starting";
  }
  return null;
}

export function discordActivityFromSession(
  session: (SessionInfo | ActiveSessionInfo) & DiscordPresenceSessionState,
  gameName: string,
): DiscordActivityUpdate | null {
  const kind = discordActivityKindForSession(session);
  if (!kind) {
    return null;
  }

  const appId = typeof session.appId === "number" ? session.appId.toString() : session.appId;
  return {
    gameName,
    kind,
    appId,
    queuePosition: session.queuePosition,
    startTimestampMs: kind === "streaming" ? Date.now() : undefined,
  };
}

export function isSameDiscordActivity(
  current: DiscordActivityUpdate | null,
  next: DiscordActivityUpdate,
): boolean {
  if (!current || current.kind !== next.kind || current.queuePosition !== next.queuePosition) {
    return false;
  }
  if (current.appId || next.appId) {
    return current.appId === next.appId;
  }
  return current.gameName === next.gameName;
}
