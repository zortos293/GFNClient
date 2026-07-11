/**
 * Client-side GPU post-processing applied to the decoded stream (web client mode only).
 * All values use UI-facing ranges; the renderer normalizes them for the shader.
 */
export interface VideoShaderSettings {
  /** Master toggle for the WebGL post-processing pipeline */
  enabled: boolean;
  /** Contrast-adaptive sharpening strength, 0-100 (0 = off) */
  sharpen: number;
  /** Color saturation percentage, 0-200 (100 = neutral) */
  saturation: number;
  /** Contrast percentage, 50-150 (100 = neutral) */
  contrast: number;
  /** Brightness percentage, 50-150 (100 = neutral) */
  brightness: number;
  /** Vibrance boost for muted colors, 0-100 (0 = off) */
  vibrance: number;
  /** Animated film grain amount, 0-100 (0 = off) */
  filmGrain: number;
}

export const DEFAULT_VIDEO_SHADER_SETTINGS: Readonly<VideoShaderSettings> = Object.freeze({
  enabled: false,
  sharpen: 40,
  saturation: 100,
  contrast: 100,
  brightness: 100,
  vibrance: 0,
  filmGrain: 0,
});

function clampShaderValue(raw: unknown, min: number, max: number, fallback: number): number {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, Math.round(value)));
}

/** Normalize persisted/user-provided shader settings into safe UI ranges. */
export function normalizeVideoShaderSettings(raw: unknown): VideoShaderSettings {
  const defaults = DEFAULT_VIDEO_SHADER_SETTINGS;
  if (typeof raw !== "object" || raw === null) {
    return { ...defaults };
  }
  const candidate = raw as Partial<Record<keyof VideoShaderSettings, unknown>>;
  return {
    enabled: candidate.enabled === true,
    sharpen: clampShaderValue(candidate.sharpen, 0, 100, defaults.sharpen),
    saturation: clampShaderValue(candidate.saturation, 0, 200, defaults.saturation),
    contrast: clampShaderValue(candidate.contrast, 50, 150, defaults.contrast),
    brightness: clampShaderValue(candidate.brightness, 50, 150, defaults.brightness),
    vibrance: clampShaderValue(candidate.vibrance, 0, 100, defaults.vibrance),
    filmGrain: clampShaderValue(candidate.filmGrain, 0, 100, defaults.filmGrain),
  };
}

/** True when the shader pipeline would visibly change the image. */
export function videoShaderHasVisibleEffect(settings: VideoShaderSettings): boolean {
  return (
    settings.enabled &&
    (settings.sharpen > 0 ||
      settings.saturation !== 100 ||
      settings.contrast !== 100 ||
      settings.brightness !== 100 ||
      settings.vibrance > 0 ||
      settings.filmGrain > 0)
  );
}
