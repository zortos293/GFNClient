/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import type { AuthSession, LoginProvider } from "@shared/gfn";

import { AccountState } from "./persistedAccountState";

function provider(code: string, streamingServiceUrl = `https://${code.toLowerCase()}.example`): LoginProvider {
  return {
    idpId: `${code}-idp`,
    code,
    displayName: code,
    streamingServiceUrl,
    priority: 0,
  };
}

function session(userId: string, loginProvider = provider("NVIDIA")): AuthSession {
  return {
    provider: loginProvider,
    tokens: {
      accessToken: `${userId}-access`,
      refreshToken: `${userId}-refresh`,
      expiresAt: Date.now() + 60_000,
    },
    user: {
      userId,
      displayName: userId,
      membershipTier: "FREE",
    },
  };
}

test("AccountState restores the legacy single-session format without changing identity", () => {
  const state = new AccountState();
  state.restore({ session: session("legacy-user") });

  assert.equal(state.getSession()?.user.userId, "legacy-user");
  assert.equal(state.getSession()?.provider.streamingServiceUrl, "https://nvidia.example/");
  assert.equal(state.snapshot().activeUserId, "legacy-user");
  assert.equal(state.snapshot().sessions.length, 1);
});

test("AccountState falls back to the first saved account when activeUserId is stale", () => {
  const state = new AccountState();
  state.restore({
    sessions: [session("first"), session("second", provider("BPC"))],
    activeUserId: "removed-user",
    selectedProvider: provider("NVIDIA"),
  });

  assert.equal(state.getSession()?.user.userId, "first");
  state.setActiveAccount("second");
  assert.equal(state.getSession()?.user.userId, "second");
  assert.equal(state.getSelectedProvider().code, "BPC");
});

test("AccountState preserves insertion order while removing and replacing accounts", () => {
  const state = new AccountState();
  state.setSession(session("first"));
  state.setSession(session("second", provider("BPC")));

  assert.equal(state.firstUserId(), "first");
  assert.equal(state.removeAccount("second"), true);
  state.setActiveAccount(state.firstUserId());

  assert.equal(state.getSession()?.user.userId, "first");
  assert.deepEqual(
    state.getSavedAccounts().map((account) => account.userId),
    ["first"],
  );
});
