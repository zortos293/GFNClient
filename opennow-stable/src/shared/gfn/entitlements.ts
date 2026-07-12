export interface EntitledResolution {
  width: number;
  height: number;
  fps: number;
}

export interface EntitledStreamProfile {
  resolution: string;
  fps: number;
}

const STREAM_MODE_PRESETS: ReadonlyArray<Readonly<{
  width: number;
  height: number;
  fps: readonly number[];
}>> = Object.freeze([
  { width: 3840, height: 2160, fps: [120, 60, 30] },
  { width: 3456, height: 2160, fps: [120, 60, 30] },
  { width: 3840, height: 1600, fps: [120, 60, 30] },
  { width: 3440, height: 1440, fps: [120, 60, 30] },
  { width: 3840, height: 1080, fps: [120, 60, 30] },
  { width: 2560, height: 1600, fps: [120, 60, 30] },
  { width: 2560, height: 1440, fps: [120, 60, 30] },
  { width: 2560, height: 1080, fps: [120, 60, 30] },
  { width: 1920, height: 1200, fps: [240, 120, 60, 30] },
  { width: 1920, height: 1080, fps: [240, 120, 60, 30] },
  { width: 1600, height: 1200, fps: [120, 60, 30] },
  { width: 1680, height: 1050, fps: [120, 60, 30] },
  { width: 1600, height: 900, fps: [120, 60, 30] },
  { width: 1280, height: 1024, fps: [120, 60, 30] },
  { width: 1440, height: 900, fps: [120, 60, 30] },
  { width: 1680, height: 720, fps: [120, 60, 30] },
  { width: 1366, height: 768, fps: [120, 60, 30] },
  { width: 1280, height: 800, fps: [120, 60, 30] },
  { width: 1112, height: 834, fps: [120, 60, 30] },
  { width: 1280, height: 720, fps: [120, 60, 30] },
  { width: 1376, height: 640, fps: [120, 60, 30] },
  { width: 1024, height: 768, fps: [120, 60, 30] },
]);

export const SAFE_FALLBACK_STREAM_PROFILE: Readonly<EntitledStreamProfile> = Object.freeze({
  resolution: "1920x1080",
  fps: 60,
});

export function getSafeFallbackEntitledResolutions(): EntitledResolution[] {
  return [{ width: 1920, height: 1080, fps: 60 }];
}

function parseResolutionValue(resolution: string): { width: number; height: number } | null {
  const [widthText, heightText] = resolution.split("x");
  const width = Number.parseInt(widthText ?? "", 10);
  const height = Number.parseInt(heightText ?? "", 10);
  return Number.isFinite(width) && width > 0 && Number.isFinite(height) && height > 0
    ? { width, height }
    : null;
}

function isValidEntitledResolution(resolution: EntitledResolution): boolean {
  return Number.isFinite(resolution.width)
    && resolution.width > 0
    && Number.isFinite(resolution.height)
    && resolution.height > 0
    && Number.isFinite(resolution.fps)
    && resolution.fps > 0;
}

function compareEntitledResolutionDescending(a: EntitledResolution, b: EntitledResolution): number {
  const pixelDelta = b.width * b.height - a.width * a.height;
  if (pixelDelta !== 0) return pixelDelta;
  if (b.width !== a.width) return b.width - a.width;
  if (b.height !== a.height) return b.height - a.height;
  return b.fps - a.fps;
}

function normalizeEntitledResolution(resolution: EntitledResolution): EntitledResolution {
  return {
    width: Math.trunc(resolution.width),
    height: Math.trunc(resolution.height),
    fps: Math.trunc(resolution.fps),
  };
}

function isModeCoveredByEntitlement(
  entitlements: readonly EntitledResolution[],
  width: number,
  height: number,
  fps: number,
): boolean {
  return entitlements.some(
    (entitlement) =>
      entitlement.width >= width &&
      entitlement.height >= height &&
      entitlement.fps >= fps,
  );
}

export function expandEntitledStreamResolutions(
  entitledResolutions: readonly EntitledResolution[],
): EntitledResolution[] {
  const validEntitlements = entitledResolutions
    .filter(isValidEntitledResolution)
    .map(normalizeEntitledResolution);
  const byKey = new Map<string, EntitledResolution>();

  const addResolution = (resolution: EntitledResolution): void => {
    byKey.set(
      `${resolution.width}x${resolution.height}@${resolution.fps}`,
      resolution,
    );
  };

  for (const resolution of validEntitlements) {
    addResolution(resolution);
  }

  for (const mode of STREAM_MODE_PRESETS) {
    for (const fps of mode.fps) {
      if (isModeCoveredByEntitlement(validEntitlements, mode.width, mode.height, fps)) {
        addResolution({ width: mode.width, height: mode.height, fps });
      }
    }
  }

  return [...byKey.values()].sort(compareEntitledResolutionDescending);
}

export function resolveEntitledStreamProfile(
  entitledResolutions: readonly EntitledResolution[],
  requested: EntitledStreamProfile,
): EntitledStreamProfile | null {
  const validEntitlements = expandEntitledStreamResolutions(entitledResolutions);
  if (validEntitlements.length === 0) {
    return null;
  }

  const requestedResolution = parseResolutionValue(requested.resolution);
  const matchingResolutionEntries = requestedResolution
    ? validEntitlements.filter(
      (resolution) =>
        resolution.width === requestedResolution.width &&
        resolution.height === requestedResolution.height,
    )
    : [];
  const fallbackResolution = [...validEntitlements].sort(compareEntitledResolutionDescending)[0];
  const selectedResolutionEntries = matchingResolutionEntries.length > 0
    ? matchingResolutionEntries
    : validEntitlements.filter(
      (resolution) =>
        resolution.width === fallbackResolution.width &&
        resolution.height === fallbackResolution.height,
    );
  const fpsOptions = [...new Set(selectedResolutionEntries.map((resolution) => Math.trunc(resolution.fps)))]
    .sort((a, b) => a - b);
  const requestedFps = Number.isFinite(requested.fps) && requested.fps > 0
    ? Math.trunc(requested.fps)
    : undefined;
  const fps = requestedFps && fpsOptions.includes(requestedFps)
    ? requestedFps
    : [...fpsOptions].reverse().find((option) => requestedFps !== undefined && option <= requestedFps) ?? fpsOptions[0];
  const selectedResolution = selectedResolutionEntries[0];

  return {
    resolution: `${selectedResolution.width}x${selectedResolution.height}`,
    fps,
  };
}
