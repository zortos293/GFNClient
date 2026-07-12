/**
 * Cloud streaming platform identity and capability contracts.
 *
 * OpenNOW is multi-platform-ready: GeForce NOW is the first implemented
 * provider. Future providers (e.g. Xbox Cloud, other alliances) should add a
 * new id here and a matching `platforms/<id>/` implementation under main and
 * renderer without leaking provider protocol details into app shell code.
 */

export const CLOUD_PLATFORM_IDS = ["gfn"] as const;

export type CloudPlatformId = (typeof CLOUD_PLATFORM_IDS)[number];

export const DEFAULT_CLOUD_PLATFORM_ID: CloudPlatformId = "gfn";

export interface CloudPlatformDescriptor {
  id: CloudPlatformId;
  /** Short product name shown in UI copy */
  displayName: string;
  /** Vendor / alliance label */
  vendor: string;
}

export const CLOUD_PLATFORM_DESCRIPTORS: Record<CloudPlatformId, CloudPlatformDescriptor> = {
  gfn: {
    id: "gfn",
    displayName: "GeForce NOW",
    vendor: "NVIDIA",
  },
};

/**
 * High-level capability flags a platform adapter can advertise.
 * Keep this intentionally small and serializable so renderer/main can branch
 * without importing provider-specific modules.
 */
export interface CloudPlatformCapabilities {
  supportsDeviceLogin: boolean;
  supportsAccountLinking: boolean;
  supportsPersistentStorage: boolean;
  supportsQueueAds: boolean;
  supportsNativeStreamer: boolean;
}

export const GFN_PLATFORM_CAPABILITIES: CloudPlatformCapabilities = {
  supportsDeviceLogin: true,
  supportsAccountLinking: true,
  supportsPersistentStorage: true,
  supportsQueueAds: true,
  supportsNativeStreamer: true,
};

export function getCloudPlatformDescriptor(
  id: CloudPlatformId = DEFAULT_CLOUD_PLATFORM_ID,
): CloudPlatformDescriptor {
  return CLOUD_PLATFORM_DESCRIPTORS[id];
}

export function getCloudPlatformCapabilities(
  id: CloudPlatformId = DEFAULT_CLOUD_PLATFORM_ID,
): CloudPlatformCapabilities {
  switch (id) {
    case "gfn":
      return GFN_PLATFORM_CAPABILITIES;
    default: {
      const _exhaustive: never = id;
      return _exhaustive;
    }
  }
}
