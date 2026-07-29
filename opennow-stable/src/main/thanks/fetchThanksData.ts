import type {
  ThankYouContributor,
  ThankYouDataResult,
  ThankYouSupporter,
} from "@shared/gfn";
import { fetchWithTimeout, withTimeout } from "../services/requestTimeout";

const THANKS_CONTRIBUTORS_URL =
  "https://api.github.com/repos/OpenCloudGaming/OpenNOW/contributors?per_page=100";
const THANKS_SUPPORTERS_URL = "https://github.com/sponsors/zortos293";
const THANKS_REQUEST_HEADERS = {
  Accept: "application/vnd.github+json",
  "User-Agent": "OpenNOW-DesktopClient",
} as const;
const THANKS_EXCLUDED_PATTERN = /(copilot|claude|cappy)/i;
const THANKS_FETCH_TIMEOUT_MS = 8000;
const THANKS_CUSTOM_SUPPORTERS: readonly ThankYouSupporter[] = [
  {
    name: "DarkevilPT",
    avatarUrl: "https://github.com/DarkevilPT.png?size=96",
    profileUrl: "https://github.com/DarkevilPT",
    isPrivate: false,
    source: "custom",
  },
] as const;

interface GitHubContributorResponse {
  login?: string;
  avatar_url?: string;
  html_url?: string;
  contributions?: number;
  type?: string;
  name?: string | null;
}

export function decodeHtmlEntities(value: string): string {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

export function stripHtml(value: string): string {
  return decodeHtmlEntities(value.replace(/<[^>]+>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

export function normalizeUrl(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const decoded = decodeHtmlEntities(value.trim());
  if (!decoded) return undefined;
  if (decoded.startsWith("//")) return `https:${decoded}`;
  if (decoded.startsWith("/")) return `https://github.com${decoded}`;
  return decoded;
}

export function shouldExcludeContributor(
  contributor: GitHubContributorResponse,
): boolean {
  const login = contributor.login?.trim() ?? "";
  const name = contributor.name?.trim() ?? "";
  if (!login || !contributor.avatar_url || !contributor.html_url) return true;
  if (contributor.type === "Bot") return true;
  if (/\[bot\]$/i.test(login)) return true;
  if (THANKS_EXCLUDED_PATTERN.test(login) || THANKS_EXCLUDED_PATTERN.test(name))
    return true;
  return false;
}

export async function fetchThanksContributors(): Promise<ThankYouContributor[]> {
  const response = await fetchWithTimeout(
    THANKS_CONTRIBUTORS_URL,
    { headers: THANKS_REQUEST_HEADERS },
    THANKS_FETCH_TIMEOUT_MS,
    "GitHub contributors request",
  );
  if (!response.ok) {
    throw new Error(`GitHub contributors request failed (${response.status})`);
  }

  const payload = (await withTimeout(
    response.json() as Promise<GitHubContributorResponse[]>,
    THANKS_FETCH_TIMEOUT_MS,
    "GitHub contributors response",
  )) as GitHubContributorResponse[];
  if (!Array.isArray(payload)) {
    throw new Error("GitHub contributors response was not an array");
  }

  const contributors = payload
    .filter((contributor) => !shouldExcludeContributor(contributor))
    .map((contributor) => ({
      login: contributor.login!.trim(),
      avatarUrl: contributor.avatar_url!,
      profileUrl: contributor.html_url!,
      contributions:
        typeof contributor.contributions === "number"
          ? contributor.contributions
          : 0,
    }))
    .sort(
      (a, b) =>
        b.contributions - a.contributions || a.login.localeCompare(b.login),
    );
  return contributors;
}

export function parseSupporterName(entryHtml: string): {
  name: string;
  isPrivate: boolean;
} {
  const privateHrefMatch = entryHtml.match(
    /href="https:\/\/docs\.github\.com\/sponsors\/sponsoring-open-source-contributors\/managing-your-sponsorship#managing-the-privacy-setting-for-your-sponsorship"/i,
  );
  const privateTooltipMatch = entryHtml.match(
    /<tool-tip[^>]*>\s*Private Sponsor\s*<\/tool-tip>/i,
  );
  const privateAriaMatch = entryHtml.match(/aria-label="Private Sponsor"/i);
  if (privateHrefMatch || privateTooltipMatch || privateAriaMatch) {
    return { name: "Private", isPrivate: true };
  }

  const altMatch = entryHtml.match(/<img[^>]+alt="([^"]+)"/i);
  const altText = altMatch ? stripHtml(altMatch[1]) : "";
  const normalizedAlt = altText.replace(/^@/, "").trim();
  if (normalizedAlt) {
    return { name: normalizedAlt, isPrivate: false };
  }

  const ariaMatch = entryHtml.match(/aria-label="([^"]+)"/i);
  const ariaText = ariaMatch ? stripHtml(ariaMatch[1]) : "";
  const normalizedAria = ariaText.replace(/^@/, "").trim();
  if (normalizedAria && !/private sponsor/i.test(normalizedAria)) {
    return { name: normalizedAria, isPrivate: false };
  }

  const hrefMatch = entryHtml.match(/<a[^>]+href="\/([^"/?#]+)"/i);
  const normalizedHref = hrefMatch
    ? decodeHtmlEntities(hrefMatch[1]).trim()
    : "";
  if (normalizedHref && !/sponsors/i.test(normalizedHref)) {
    return { name: normalizedHref.replace(/^@/, ""), isPrivate: false };
  }

  return { name: "Private", isPrivate: true };
}

export function parseSupportersFromHtml(html: string): ThankYouSupporter[] {
  const sponsorsSectionMatch = html.match(
    /<div class="tmp-mt-3 tmp-pb-4" id="sponsors">([\s\S]*?)<\/remote-pagination>/i,
  );
  if (!sponsorsSectionMatch) {
    return [];
  }

  const listHtml = sponsorsSectionMatch[1];
  const entryMatches =
    listHtml.match(/<div class="d-flex mb-1 mr-1"[^>]*>[\s\S]*?<\/div>/gi) ??
    [];
  const supporters: ThankYouSupporter[] = [];
  const seenKeys = new Set<string>();

  for (const entryHtml of entryMatches) {
    const { name, isPrivate } = parseSupporterName(entryHtml);
    const hrefMatch = entryHtml.match(/<a[^>]+href="([^"]+)"/i);
    const profileUrl = isPrivate ? undefined : normalizeUrl(hrefMatch?.[1]);
    const avatarMatch = entryHtml.match(/<img[^>]+src="([^"]+)"/i);
    const avatarUrl = normalizeUrl(avatarMatch?.[1]);
    const dedupeKey = `${name}|${profileUrl ?? ""}|${avatarUrl ?? ""}`;
    if (seenKeys.has(dedupeKey)) continue;
    seenKeys.add(dedupeKey);
    supporters.push({
      name: name || "Private",
      avatarUrl,
      profileUrl,
      isPrivate: isPrivate || !name,
      source: isPrivate || !name ? "private" : "github",
    });
  }

  return supporters;
}

export function getSupporterDedupeKey(supporter: ThankYouSupporter): string {
  const profileUrl = supporter.profileUrl?.trim().toLowerCase();
  if (profileUrl) return `profile:${profileUrl}`;
  return `name:${supporter.name.trim().toLowerCase()}|private:${supporter.isPrivate}`;
}

export function mergeThanksSupporters(
  ...supporterGroups: readonly (readonly ThankYouSupporter[])[]
): ThankYouSupporter[] {
  const supporters: ThankYouSupporter[] = [];
  const seenKeys = new Set<string>();

  for (const group of supporterGroups) {
    for (const supporter of group) {
      const dedupeKey = getSupporterDedupeKey(supporter);
      if (seenKeys.has(dedupeKey)) continue;
      seenKeys.add(dedupeKey);
      supporters.push({ ...supporter });
    }
  }

  return supporters;
}

export async function fetchThanksSupporters(): Promise<ThankYouSupporter[]> {
  const response = await fetchWithTimeout(
    THANKS_SUPPORTERS_URL,
    {
      headers: {
        ...THANKS_REQUEST_HEADERS,
        Accept: "text/html,application/xhtml+xml",
      },
    },
    THANKS_FETCH_TIMEOUT_MS,
    "GitHub sponsors request",
  );
  if (!response.ok) {
    throw new Error(`GitHub sponsors page request failed (${response.status})`);
  }

  const html = await withTimeout(
    response.text(),
    THANKS_FETCH_TIMEOUT_MS,
    "GitHub sponsors response",
  );
  const supporters = parseSupportersFromHtml(html);
  return supporters;
}

export async function fetchThanksData(): Promise<ThankYouDataResult> {
  const result: ThankYouDataResult = {
    contributors: [],
    supporters: [],
  };

  const [contributorsResult, supportersResult] = await Promise.allSettled([
    fetchThanksContributors(),
    fetchThanksSupporters(),
  ]);

  if (contributorsResult.status === "fulfilled") {
    result.contributors = contributorsResult.value;
  } else {
    result.contributorsError =
      contributorsResult.reason instanceof Error
        ? contributorsResult.reason.message
        : "Unable to load contributors right now.";
  }

  if (supportersResult.status === "fulfilled") {
    result.supporters = mergeThanksSupporters(
      THANKS_CUSTOM_SUPPORTERS,
      supportersResult.value,
    );
    if (result.supporters.length === 0) {
      result.supportersError =
        "No public supporters were found on GitHub Sponsors.";
    }
  } else {
    result.supporters = mergeThanksSupporters(THANKS_CUSTOM_SUPPORTERS);
    result.supportersError =
      supportersResult.reason instanceof Error
        ? supportersResult.reason.message
        : "Unable to load supporters right now.";
  }

  return result;
}
