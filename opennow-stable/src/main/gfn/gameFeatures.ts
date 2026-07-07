const IN_GAME_SETTINGS_PERSISTENCE_FEATURE_KEY = "IN_GAME_SETTINGS_PERSISTENCE_ENABLED";

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object";
}

function normalizeVariantFeatureMetadata(features: unknown): Record<string, unknown>[] {
  if (Array.isArray(features)) {
    return features.filter(isRecord);
  }
  return isRecord(features) ? [features] : [];
}

function isTrueFeatureValue(value: unknown): boolean {
  return value === true || value === "true";
}

function featureValuesInclude(values: unknown, expected: string): boolean {
  return Array.isArray(values) && values.some((value) => value === expected);
}

export function isGfnVariantFeatureSupported(
  variant: { gfn?: { features?: unknown } },
  featureKey: string,
  featureValue?: string,
): boolean {
  return normalizeVariantFeatureMetadata(variant.gfn?.features).some((feature) => {
    if (feature.key !== featureKey) {
      return false;
    }
    return featureValue
      ? featureValuesInclude(feature.values, featureValue)
      : isTrueFeatureValue(feature.value);
  });
}

export function supportsInGameSettingsPersistence(variant: { gfn?: { features?: unknown } }): boolean {
  return isGfnVariantFeatureSupported(variant, IN_GAME_SETTINGS_PERSISTENCE_FEATURE_KEY);
}
