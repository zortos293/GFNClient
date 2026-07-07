import { Client } from "discord-rpc";
import type { DiscordActivityUpdate } from "@shared/discord";

/**
 * Discord Application ID for OpenNOW.
 * Register an application at https://discord.com/developers/applications
 * and paste its Client ID here.
 */
const DISCORD_CLIENT_ID = "1479944467112001669";

let rpcClient: Client | null = null;
let connected = false;
type DiscordRpcActivity = Omit<DiscordActivityUpdate, "startTimestampMs"> & {
  startTimestamp?: Date;
};

let lastActivity: DiscordRpcActivity | null = null;
let pendingActivity: DiscordRpcActivity | null = null;

/**
 * Initialise and connect the Discord RPC client.
 * Errors are swallowed so that a missing or closed Discord installation
 * never crashes or blocks the rest of the application.
 */
export async function connectDiscordRpc(): Promise<void> {
  if (rpcClient) return;

  const client = new Client({ transport: "ipc" });

  client.on("disconnected", () => {
    connected = false;
    rpcClient = null;
    console.log("[DiscordRPC] Disconnected.");
  });

  try {
    await client.login({ clientId: DISCORD_CLIENT_ID });
    rpcClient = client;
    connected = true;
    console.log("[DiscordRPC] Connected.");

    if (pendingActivity) {
      await setActivity(pendingActivity);
      // Consume reconnect replay so failed attempts are not reprocessed forever.
      pendingActivity = null;
    } else if (lastActivity) {
      await setActivity(lastActivity);
    } else {
      // Upon app start/connection, explicitly clear any stale status from previous runs
      await client.clearActivity().catch(() => {});
    }
  } catch (err) {
    console.warn("[DiscordRPC] Failed to connect (Discord may not be running):", (err as Error).message);
    rpcClient = null;
    connected = false;
  }
}

/**
 * Get the currently active game name and start timestamp.
 */
export function getCurrentActivity(): DiscordRpcActivity | null {
  return lastActivity;
}

/**
 * Check if the Discord RPC client is currently connected.
 */
export function isDiscordRpcConnected(): boolean {
  return connected && rpcClient !== null;
}

/**
 * Update the Discord "Now Playing" activity to show the given game name and
 * how long the user has been playing.
 */
function activityState(activity: DiscordRpcActivity): string {
  switch (activity.kind) {
    case "queued":
      return activity.queuePosition ? `In queue (#${activity.queuePosition})` : "In queue";
    case "starting":
      return "Starting stream";
    case "streaming":
      return "Streaming via OpenNow";
  }
}

export async function setActivity(activity: DiscordRpcActivity): Promise<void> {
  pendingActivity = activity;

  if (!connected || !rpcClient) {
    return;
  }

  try {
    const rpcActivity = {
      details: activity.gameName,
      state: activityState(activity),
      ...(activity.startTimestamp ? { startTimestamp: activity.startTimestamp } : {}),
      instance: false,
    };
    await rpcClient.setActivity({
      ...rpcActivity,
    });
    lastActivity = pendingActivity;
    pendingActivity = null;
  } catch (err) {
    pendingActivity = null;
    console.warn("[DiscordRPC] setActivity failed:", (err as Error).message);
  }
}

/**
 * Clear the Discord activity (call when a stream ends or the app quits).
 */
export async function clearActivity(): Promise<void> {
  lastActivity = null;
  pendingActivity = null;

  if (!connected || !rpcClient) return;

  try {
    await rpcClient.clearActivity();
  } catch (err) {
    console.warn("[DiscordRPC] clearActivity failed:", (err as Error).message);
  }
}

/**
 * Destroy the RPC connection gracefully (call on app quit).
 */
export async function destroyDiscordRpc(): Promise<void> {
  lastActivity = null;
  pendingActivity = null;

  if (!rpcClient) return;

  try {
    await rpcClient.destroy();
  } catch {
    // Ignore errors during teardown
  } finally {
    rpcClient = null;
    connected = false;
  }
}
