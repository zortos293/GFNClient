export type DiscordActivityKind = "queued" | "starting" | "streaming";

export interface DiscordActivityUpdate {
  gameName: string;
  kind: DiscordActivityKind;
  appId?: string;
  queuePosition?: number;
  startTimestampMs?: number;
}
