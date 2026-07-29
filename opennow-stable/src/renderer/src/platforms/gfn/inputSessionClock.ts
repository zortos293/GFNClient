let inputSessionStartedAtMs = 0;

/** Reset session-relative input clock when the input handshake completes. */
export function startInputSessionClock(nowMs: number = performance.now()): void {
  inputSessionStartedAtMs = nowMs;
}

/** Session-relative capture timestamp for inner event payloads (official GFN Or()). */
export function captureTimestampUs(sourceTimestampMs?: number): bigint {
  const baseMs =
    typeof sourceTimestampMs === "number" && Number.isFinite(sourceTimestampMs) && sourceTimestampMs >= 0
      ? sourceTimestampMs - inputSessionStartedAtMs
      : performance.now() - inputSessionStartedAtMs;
  return BigInt(Math.max(0, Math.floor(baseMs * 1000)));
}

/** Send-time session clock for v3 outer headers (official GFN ed()). */
export function sendTimestampUs(nowMs: number = performance.now()): bigint {
  return captureTimestampUs(nowMs);
}

export function writeSessionTimestamp(view: DataView, offset: number, timestampUs: bigint): void {
  const clamped = timestampUs < 0n ? 0n : timestampUs;
  const lo = Number(clamped & 0xFFFFFFFFn);
  const hi = Number(clamped >> 32n);
  view.setUint32(offset, hi, false);
  view.setUint32(offset + 4, lo, false);
}
