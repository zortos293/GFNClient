/**
 * GeForce NOW public endpoint helpers shared by main and renderer.
 * Keep NVIDIA host construction here so UI and session code do not re-declare
 * zone/url patterns independently.
 */

export const GFN_PLAY_ORIGIN = "https://play.geforcenow.com";
export const GFN_PLAY_REFERER = "https://play.geforcenow.com/";

export const GFN_CLOUDMATCH_BETA_HOST_SUFFIX = "cloudmatchbeta.nvidiagrid.net";

export const GFN_STORAGE_MANAGER_URL = "https://www.nvidia.com/en-us/account/gfn/manage-storage/";

/**
 * Build the direct CloudMatch streaming base URL for a standard NVIDIA zone.
 * "NP-AMS-08" → "https://np-ams-08.cloudmatchbeta.nvidiagrid.net/"
 */
export function buildGfnZoneStreamingBaseUrl(zoneId: string): string {
  return `https://${zoneId.toLowerCase()}.${GFN_CLOUDMATCH_BETA_HOST_SUFFIX}/`;
}

/** True for standard NVIDIA zones (NP-*), excluding alliance NPA-* zones. */
export function isStandardGfnZone(zoneId: string): boolean {
  return zoneId.startsWith("NP-") && !zoneId.startsWith("NPA-");
}
