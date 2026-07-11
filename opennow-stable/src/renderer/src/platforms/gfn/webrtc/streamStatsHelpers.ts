/**
 * Extract codec name from codecId string (e.g., "VP09" -> "VP9", "AV1X" -> "AV1")
 */
export function normalizeCodecName(codecId: string): string {
  const upper = codecId.toUpperCase();

  if (upper.startsWith("H264") || upper === "H264") {
    return "H264";
  }
  if (upper.startsWith("H265") || upper === "H265" || upper.startsWith("HEVC")) {
    return "H265";
  }
  if (upper.startsWith("AV1")) {
    return "AV1";
  }
  if (upper.startsWith("VP9") || upper.startsWith("VP09")) {
    return "VP9";
  }
  if (upper.startsWith("VP8")) {
    return "VP8";
  }

  return codecId;
}

/** Map WebRTC codec mimeType (or codecId fallback) to a display codec label. */
export function codecLabelFromMimeType(mimeType: string, codecId?: string): string {
  if (mimeType.includes("H264")) {
    return "H264";
  }
  if (mimeType.includes("H265") || mimeType.includes("HEVC")) {
    return "H265";
  }
  if (mimeType.includes("AV1")) {
    return "AV1";
  }
  if (mimeType.includes("VP9")) {
    return "VP9";
  }
  if (mimeType.includes("VP8")) {
    return "VP8";
  }
  if (codecId) {
    return normalizeCodecName(codecId);
  }
  return mimeType || "Unknown";
}

/**
 * Detect GPU type using browser APIs
 * Uses WebGL renderer string to identify GPU vendor/model
 */
export function detectGpuType(): string {
  try {
    const canvas = document.createElement("canvas");
    const gl = canvas.getContext("webgl2") || canvas.getContext("webgl");
    if (!gl) {
      return "Unknown";
    }

    const debugInfo = gl.getExtension("WEBGL_debug_renderer_info");
    if (debugInfo) {
      const vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
      const renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);

      // Clean up renderer string - extract main GPU name
      let gpuName = renderer;

      // Remove common prefixes/suffixes for cleaner display
      gpuName = gpuName
        .replace(/\(R\)/g, "")
        .replace(/\(TM\)/g, "")
        .replace(/NVIDIA /i, "")
        .replace(/AMD /i, "")
        .replace(/Intel /i, "")
        .replace(/Microsoft Corporation - /i, "")
        .replace(/D3D12 /i, "")
        .replace(/Direct3D11 /i, "")
        .replace(/OpenGL Engine/i, "")
        .trim();

      // Limit length
      if (gpuName.length > 30) {
        gpuName = gpuName.substring(0, 27) + "...";
      }

      return gpuName || vendor || "Unknown";
    }
    return "Unknown";
  } catch {
    return "Unknown";
  }
}

/** Average jitter buffer delay in ms from cumulative WebRTC inbound-rtp counters. */
export function averageJitterBufferDelayMs(
  jitterBufferDelaySeconds: number,
  jitterBufferEmittedCount: number,
): number | null {
  if (jitterBufferEmittedCount <= 0) {
    return null;
  }
  return Math.round((jitterBufferDelaySeconds / jitterBufferEmittedCount) * 1000 * 10) / 10;
}
