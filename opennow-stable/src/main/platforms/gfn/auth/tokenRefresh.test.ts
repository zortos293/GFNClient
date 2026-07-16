/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { mergeTokenSnapshot } from "./tokenRefresh";

test("mergeTokenSnapshot keeps the prior id_token when refresh omits it", () => {
  const merged = mergeTokenSnapshot(
    {
      accessToken: "old-access",
      refreshToken: "refresh",
      idToken: "prior-id-token",
      expiresAt: Date.now() + 60_000,
      clientToken: "client",
      clientTokenExpiresAt: Date.now() + 120_000,
      clientTokenLifetimeMs: 120_000,
      authClientId: "client-id",
    },
    {
      access_token: "new-access",
      refresh_token: "refresh",
      expires_in: 3600,
    },
  );

  assert.equal(merged.accessToken, "new-access");
  assert.equal(merged.idToken, "prior-id-token");
  assert.equal(merged.clientToken, "client");
  assert.equal(typeof merged.clientTokenExpiresAt, "number");
});

test("mergeTokenSnapshot clears client-token expiry when client_token rotates", () => {
  const merged = mergeTokenSnapshot(
    {
      accessToken: "old-access",
      refreshToken: "refresh",
      idToken: "prior-id-token",
      expiresAt: Date.now() + 60_000,
      clientToken: "old-client",
      clientTokenExpiresAt: Date.now() + 120_000,
      clientTokenLifetimeMs: 120_000,
    },
    {
      access_token: "new-access",
      id_token: "new-id-token",
      client_token: "new-client",
      expires_in: 3600,
    },
  );

  assert.equal(merged.idToken, "new-id-token");
  assert.equal(merged.clientToken, "new-client");
  assert.equal(merged.clientTokenExpiresAt, undefined);
  assert.equal(merged.clientTokenLifetimeMs, undefined);
});

test("mergeTokenSnapshot keeps client-token expiry when client_token is unchanged", () => {
  const clientTokenExpiresAt = Date.now() + 120_000;
  const merged = mergeTokenSnapshot(
    {
      accessToken: "old-access",
      refreshToken: "refresh",
      idToken: "prior-id-token",
      expiresAt: Date.now() + 60_000,
      clientToken: "same-client",
      clientTokenExpiresAt,
      clientTokenLifetimeMs: 120_000,
    },
    {
      access_token: "new-access",
      client_token: "same-client",
      expires_in: 3600,
    },
  );

  assert.equal(merged.clientToken, "same-client");
  assert.equal(merged.clientTokenExpiresAt, clientTokenExpiresAt);
  assert.equal(merged.clientTokenLifetimeMs, 120_000);
  assert.equal(merged.idToken, "prior-id-token");
});
