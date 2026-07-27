/// <reference types="node" />

import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import type { AuthSession } from "@shared/gfn";

import { AccountManager } from "./accountManager";
import { PersistedAccountState } from "./persistedAccountState";

function session(userId: string): AuthSession {
  return {
    provider: {
      idpId: "nvidia",
      code: "NVIDIA",
      displayName: "NVIDIA",
      streamingServiceUrl: "https://provider.example/",
      priority: 0,
    },
    tokens: {
      accessToken: `${userId}-access`,
      expiresAt: Date.now() + 60_000,
    },
    user: {
      userId,
      displayName: userId,
      membershipTier: "FREE",
    },
  };
}

test("AccountManager removes an incomplete switched account and restores the prior account", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "opennow-auth-account-"));
  t.after(() => rm(directory, { recursive: true, force: true }));

  const statePath = join(directory, "auth.json");
  const state = new PersistedAccountState(statePath);
  const first = session("first");
  const second = session("second");
  state.accounts.setSession(first);
  state.accounts.setSession(second);
  state.accounts.setActiveAccount("first");

  let cacheClears = 0;
  const manager = new AccountManager(state, () => {
    cacheClears += 1;
  });

  await assert.rejects(
    () => manager.switchAccount("second", async () => ({
      session: second,
      refresh: {
        attempted: true,
        forced: true,
        outcome: "missing_refresh_token",
        message: "No refresh token available.",
      },
    })),
    /Saved login for this account is incomplete/,
  );

  assert.equal(state.accounts.getSession()?.user.userId, "first");
  assert.equal(state.accounts.hasAccount("second"), false);
  assert.equal(cacheClears, 3);

  const persisted = JSON.parse(await readFile(statePath, "utf8")) as {
    activeUserId: string | null;
    sessions: AuthSession[];
  };
  assert.equal(persisted.activeUserId, "first");
  assert.deepEqual(persisted.sessions.map((saved) => saved.user.userId), ["first"]);
});
