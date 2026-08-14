import type { GameInfo } from "@shared/gfn";

const PAID_MEMBERSHIP_TIERS = new Set([
  "FOUNDERS",
  "PERFORMANCE",
  "PREMIUM",
  "PRIORITY",
  "ULTIMATE",
]);

export function getRequiredPaidMembershipTier(
  game: Pick<GameInfo, "membershipTierLabel">,
): string | null {
  const label = game.membershipTierLabel?.trim();
  if (!label) return null;

  const tokens = label
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, " ")
    .trim()
    .split(" ");

  return tokens.some((token) => PAID_MEMBERSHIP_TIERS.has(token)) ? label : null;
}
