import type { ActiveSessionInfo, GfnSessionQueueState, SessionInfo } from "@shared/gfn";
import { isGfnSessionInQueue } from "@shared/gfn";
import type { DiscordActivityKind, DiscordActivityUpdate } from "@shared/discord";

export interface DiscordPresenceSessionState extends GfnSessionQueueState {
  appId?: number | string;
}

export type DiscordCurrentActivity = Omit<DiscordActivityUpdate, "startTimestampMs"> & {
  startTimestamp?: Date;
};

export type DiscordMonitorActivityDecision =
  | { action: "none" }
  | { action: "clear" }
  | { action: "set"; activity: DiscordActivityUpdate; startTimestamp?: Date };

export function discordActivityKindForSession(session: DiscordPresenceSessionState): DiscordActivityKind | null {
  if (session.status === 3) {
    return "streaming";
  }
  if (isGfnSessionInQueue(session)) {
    return "queued";
  }
  if (session.status === 1 || session.status === 2) {
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
  current: DiscordCurrentActivity | null,
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

export function discordMonitorActivityDecision(
  current: DiscordCurrentActivity | null,
  activeSession: (ActiveSessionInfo & DiscordPresenceSessionState) | null,
): DiscordMonitorActivityDecision {
  if (!activeSession) {
    return current ? { action: "clear" } : { action: "none" };
  }

  const sessionAppId = activeSession.appId.toString();
  if (current?.kind === "streaming" && current.appId === sessionAppId) {
    return { action: "none" };
  }

  const gameName = current?.appId === sessionAppId ? current.gameName : sessionAppId;
  const nextActivity = discordActivityFromSession(activeSession, gameName);
  if (!nextActivity) {
    return current?.appId === sessionAppId ? { action: "clear" } : { action: "none" };
  }

  const startTimestamp =
    nextActivity.kind === "streaming" && current?.kind === "streaming" && current.appId === sessionAppId
      ? current.startTimestamp
      : nextActivity.startTimestampMs
        ? new Date(nextActivity.startTimestampMs)
        : undefined;

  if (isSameDiscordActivity(current, nextActivity)) {
    return { action: "none" };
  }

  return { action: "set", activity: nextActivity, startTimestamp };
}
