/**
 * NVIDIA stats_channel messages use byte 0 as a frame type:
 * type 3 prefixes the payload with the type byte, while type 4 is the
 * unprefixed version-4 payload. Protocol version 5 is therefore carried by
 * type 3 with the version at byte 1.
 */
export interface StatsChannelGameFps {
  version: number;
  fps: number;
}

export function parseStatsChannelGameFps(buffer: ArrayBuffer): StatsChannelGameFps | null {
  const bytes = new Uint8Array(buffer);
  if (bytes.length === 0) {
    return null;
  }

  let payloadOffset = 0;
  if (bytes[0] === 3) {
    payloadOffset = 1;
  } else if (bytes[0] !== 4) {
    return null;
  }

  if (bytes.length - payloadOffset < 33) {
    return null;
  }

  try {
    const view = new DataView(buffer);
    const version = view.getUint8(payloadOffset);
    if (version < 4) {
      return null;
    }

    const averageGameFps = view.getFloat64(payloadOffset + 25, true);
    if (!Number.isFinite(averageGameFps) || averageGameFps <= 0 || averageGameFps > 360) {
      return null;
    }

    return {
      version,
      fps: Math.round(averageGameFps),
    };
  } catch {
    return null;
  }
}
