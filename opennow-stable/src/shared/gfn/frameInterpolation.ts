/**
 * Client-side neural frame interpolation (Framegen WGSL/WebGPU runtime).
 * Web client mode only — native streamer renders outside Chromium.
 *
 * Runtime code: MIT (framegen npm package).
 * Bundled/fetched model weights: non-commercial research/personal use only
 * (see Framegen WEIGHTS_LICENSE). Commercial weight use requires separate terms.
 */

export type FrameInterpolationFactor = 2 | 3 | 4;
export type FrameInterpolationQuality = 360 | 480 | 720;

export const FRAME_INTERPOLATION_FACTOR_OPTIONS: readonly FrameInterpolationFactor[] = [2, 3, 4];
export const FRAME_INTERPOLATION_QUALITY_OPTIONS: readonly FrameInterpolationQuality[] = [360, 480, 720];

export interface FrameInterpolationSettings {
  /** Master toggle for the WebGPU frame-interpolation pipeline */
  enabled: boolean;
  /** How many output frames per source pair (2×–4×) */
  factor: FrameInterpolationFactor;
  /**
   * Internal model resolution height. Source frames are scaled to this height
   * (width keeps aspect, rounded to multiples of 16). Lower = less GPU cost.
   */
  quality: FrameInterpolationQuality;
}

export const DEFAULT_FRAME_INTERPOLATION_SETTINGS: Readonly<FrameInterpolationSettings> = Object.freeze({
  enabled: false,
  factor: 2,
  quality: 480,
});

const FACTORS = new Set(FRAME_INTERPOLATION_FACTOR_OPTIONS);
const QUALITIES = new Set(FRAME_INTERPOLATION_QUALITY_OPTIONS);

function clampChoice<T extends number>(raw: unknown, allowed: Set<T>, fallback: T): T {
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    return fallback;
  }
  const rounded = Math.round(value) as T;
  return allowed.has(rounded) ? rounded : fallback;
}

/** Normalize persisted/user-provided frame-interpolation settings. */
export function normalizeFrameInterpolationSettings(raw: unknown): FrameInterpolationSettings {
  const defaults = DEFAULT_FRAME_INTERPOLATION_SETTINGS;
  if (typeof raw !== "object" || raw === null) {
    return { ...defaults };
  }
  const candidate = raw as Partial<Record<keyof FrameInterpolationSettings, unknown>>;
  return {
    enabled: candidate.enabled === true,
    factor: clampChoice(candidate.factor, FACTORS, defaults.factor),
    quality: clampChoice(candidate.quality, QUALITIES, defaults.quality),
  };
}

export function frameInterpolationIsActive(settings: FrameInterpolationSettings): boolean {
  return settings.enabled === true;
}
