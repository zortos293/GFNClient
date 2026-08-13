import assert from "node:assert/strict";
import test from "node:test";

import { getRequiredPaidMembershipTier } from "./premiumMembership";

test("detects known paid GFN membership requirements", () => {
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: "Performance" }), "Performance");
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: "Ultimate membership" }), "Ultimate membership");
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: " premium " }), "premium");
});

test("does not claim a restriction for free, missing, or unknown tier metadata", () => {
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: "Free" }), null);
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: "" }), null);
  assert.equal(getRequiredPaidMembershipTier({}), null);
  assert.equal(getRequiredPaidMembershipTier({ membershipTierLabel: "Special Access" }), null);
});
