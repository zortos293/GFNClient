import type { EntitledResolution } from "./entitlements";

export interface StorageAddon {
  type: "PERMANENT_STORAGE";
  sizeGb?: number;
  usedGb?: number;
  regionName?: string;
  regionCode?: string;
}

export interface SubscriptionInfo {
  membershipTier: string;
  subscriptionType?: string;
  subscriptionSubType?: string;
  allottedHours: number;
  purchasedHours: number;
  rolledOverHours: number;
  usedHours: number;
  remainingHours: number;
  totalHours: number;
  firstEntitlementStartDateTime?: string;
  serverRegionId?: string;
  currentSpanStartDateTime?: string;
  currentSpanEndDateTime?: string;
  notifyUserWhenTimeRemainingInMinutes?: number;
  notifyUserOnSessionWhenRemainingTimeInMinutes?: number;
  state?: string;
  isGamePlayAllowed?: boolean;
  isUnlimited: boolean;
  storageAddon?: StorageAddon;
  entitledResolutions: EntitledResolution[];
}
