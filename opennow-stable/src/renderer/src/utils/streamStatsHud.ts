export type StatsOverlayMode = "off" | "compact" | "full";

export const PACKET_LOSS_ALERT_PERCENT = 0.15;
export const RTT_SPIKE_MIN_MS = 80;
export const RTT_SPIKE_MULTIPLIER = 2;

export function nextStatsOverlayMode(mode: StatsOverlayMode): StatsOverlayMode {
  if (mode === "off") return "compact";
  if (mode === "compact") return "full";
  return "off";
}

export function isRttSpike(previousRttMs: number, currentRttMs: number): boolean {
  return (
    previousRttMs > 0
    && currentRttMs >= RTT_SPIKE_MIN_MS
    && currentRttMs >= previousRttMs * RTT_SPIKE_MULTIPLIER
  );
}

export function formatOptionalBitrate(kbps: number, unknown = false): string {
  if (unknown || !Number.isFinite(kbps) || kbps <= 0) return "--";
  if (kbps >= 1000) return `${(kbps / 1000).toFixed(1)} Mbps`;
  return `${kbps.toFixed(0)} kbps`;
}

export function formatServerGameFps(
  stats: Readonly<{
    gameFps?: number;
    decodeFps: number;
    renderFps: number;
  }>,
): string {
  return typeof stats.gameFps === "number"
    && Number.isFinite(stats.gameFps)
    && stats.gameFps > 0
    ? String(stats.gameFps)
    : "--";
}
