export interface RegionSelectionOption {
  name: string;
  url: string;
}

export type RegionPingResults = ReadonlyMap<string, number | null>;

export function findBestRegionUrl(pingResults: RegionPingResults): string | null {
  let bestUrl: string | null = null;
  let bestPing = Infinity;

  pingResults.forEach((pingMs, url) => {
    if (pingMs !== null && pingMs < bestPing) {
      bestPing = pingMs;
      bestUrl = url;
    }
  });

  return bestUrl;
}

export function filterAndSortRegions<T extends RegionSelectionOption>(
  regions: readonly T[],
  query: string,
  pingResults: RegionPingResults,
): T[] {
  const normalizedQuery = query.trim().toLowerCase();
  const filtered = normalizedQuery
    ? regions.filter((region) => region.name.toLowerCase().includes(normalizedQuery))
    : [...regions];

  return filtered.sort((a, b) => {
    const pingA = pingResults.get(a.url);
    const pingB = pingResults.get(b.url);

    if (pingA !== undefined && pingB !== undefined && pingA !== null && pingB !== null) {
      return pingA - pingB;
    }
    if (pingA !== undefined && pingA !== null) return -1;
    if (pingB !== undefined && pingB !== null) return 1;
    return a.name.localeCompare(b.name);
  });
}

export function getRegionPingQuality(pingMs: number): "good" | "medium" | "poor" {
  if (pingMs <= 50) return "good";
  if (pingMs <= 100) return "medium";
  return "poor";
}

