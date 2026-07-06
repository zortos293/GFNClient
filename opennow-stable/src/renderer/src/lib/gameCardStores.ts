import type { GameInfo, GameVariant } from "@shared/gfn";
import { isOwnedVariant, normalizeGameStore } from "@shared/gfn";
import { getResolvedSelectedVariantId } from "./gameCardVariants";

export interface StoreOption {
  storeKey: string;
  store: string;
  variantId: string;
  isOwned: boolean;
  isActive: boolean;
}

function normalizeStoreOptionKey(store: string): string | null {
  const trimmed = store.trim();
  if (!trimmed) {
    return null;
  }

  const key = normalizeGameStore(trimmed);
  return key === "NONE" ? null : key;
}

function getVariantForStore(variants: GameVariant[], activeVariantId?: string): GameVariant | undefined {
  const ownedVariant = variants.find((variant) => isOwnedVariant(variant));

  if (activeVariantId) {
    const activeVariant = variants.find((variant) => variant.id === activeVariantId);
    if (activeVariant && (isOwnedVariant(activeVariant) || !ownedVariant)) {
      return activeVariant;
    }
    return ownedVariant ?? activeVariant ?? variants[0];
  }

  return ownedVariant ?? variants[0];
}

export function getStoreOptions(game: GameInfo, selectedVariantId?: string): StoreOption[] {
  const resolvedSelectedVariantId = getResolvedSelectedVariantId(game, selectedVariantId);
  const variantsByStore = new Map<string, GameVariant[]>();

  for (const variant of game.variants) {
    const storeKey = normalizeStoreOptionKey(variant.store);
    if (!storeKey) {
      continue;
    }

    const existing = variantsByStore.get(storeKey);
    if (existing) {
      existing.push(variant);
    } else {
      variantsByStore.set(storeKey, [variant]);
    }
  }

  return [...variantsByStore.entries()].map(([storeKey, variants]) => {
    const activeVariantId = variants.find((variant) => variant.id === resolvedSelectedVariantId)?.id;
    const preferredVariant = getVariantForStore(variants, activeVariantId);

    return {
      storeKey,
      store: preferredVariant?.store ?? variants[0]?.store ?? storeKey,
      variantId: preferredVariant?.id ?? variants[0]?.id ?? storeKey,
      isOwned: variants.some((variant) => isOwnedVariant(variant)),
      isActive: Boolean(activeVariantId),
    };
  });
}
