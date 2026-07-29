/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import { shouldRefreshSession } from "./sessionValidity";

test("shouldRefreshSession only coordinates refresh inside the token refresh window", () => {
  assert.equal(
    shouldRefreshSession({
      accessToken: "valid",
      expiresAt: Date.now() + 60 * 60 * 1000,
    }),
    false,
  );
  assert.equal(
    shouldRefreshSession({
      accessToken: "near-expiry",
      expiresAt: Date.now() + 30_000,
    }),
    true,
  );
  assert.equal(
    shouldRefreshSession({
      accessToken: "expired",
      expiresAt: Date.now() - 1,
    }),
    true,
  );
});
