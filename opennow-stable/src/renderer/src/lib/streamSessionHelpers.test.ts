/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import type { SessionInfo } from "@shared/gfn";
import { disposeSessionCreatedAfterAbort } from "./streamSessionHelpers";

const session = { sessionId: "late-session" } as SessionInfo;

test("a session created after launch cancellation is stopped exactly once", async () => {
  const stopped: SessionInfo[] = [];
  const disposed = await disposeSessionCreatedAfterAbort(true, session, async (target) => {
    stopped.push(target);
    return true;
  });

  assert.equal(disposed, true);
  assert.deepEqual(stopped, [session]);
});

test("an active launch keeps its newly created session", async () => {
  let stopCalls = 0;
  const disposed = await disposeSessionCreatedAfterAbort(false, session, async () => {
    stopCalls++;
    return true;
  });

  assert.equal(disposed, false);
  assert.equal(stopCalls, 0);
});
