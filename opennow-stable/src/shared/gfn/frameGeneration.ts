export type FrameGenerationQuality = 480 | 720 | 1080;

/**
 * Experimental neural interpolation for the embedded web stream.
 * The MVP always inserts one midpoint, producing a 2x presentation cadence.
 */
export interface FrameGenerationSettings {
  enabled: boolean;
  quality: FrameGenerationQuality;
}

export const FRAME_GENERATION_FACTOR = 2;

export const DEFAULT_FRAME_GENERATION_SETTINGS: Readonly<FrameGenerationSettings> = Object.freeze({
  enabled: false,
  quality: 720,
});

const FRAME_GENERATION_QUALITIES: readonly FrameGenerationQuality[] = [480, 720, 1080];

function normalizeQuality(raw: unknown): FrameGenerationQuality {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return DEFAULT_FRAME_GENERATION_SETTINGS.quality;
  }

  return FRAME_GENERATION_QUALITIES.reduce((closest, quality) => (
    Math.abs(quality - value) < Math.abs(closest - value) ? quality : closest
  ));
}

export function normalizeFrameGenerationSettings(raw: unknown): FrameGenerationSettings {
  if (typeof raw !== "object" || raw === null) {
    return { ...DEFAULT_FRAME_GENERATION_SETTINGS };
  }

  const candidate = raw as Partial<Record<keyof FrameGenerationSettings, unknown>>;
  return {
    enabled: candidate.enabled === true,
    quality: normalizeQuality(candidate.quality),
  };
}
