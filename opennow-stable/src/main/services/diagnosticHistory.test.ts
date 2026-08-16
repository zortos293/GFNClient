/// <reference types="node" />

import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test, { type TestContext } from "node:test";

import {
  boundDiagnosticSnapshot,
  DiagnosticHistoryStore,
} from "./diagnosticHistory";

async function temporaryDirectory(t: TestContext): Promise<string> {
  const directory = await mkdtemp(join(tmpdir(), "opennow-diagnostics-"));
  t.after(async () => {
    await rm(directory, { recursive: true, force: true });
  });
  return directory;
}

test("promotes the latest current-process snapshot on the next app run", async (t) => {
  const directory = await temporaryDirectory(t);
  const store = new DiagnosticHistoryStore(directory, () => 1234);

  assert.equal(await store.beginAppRun(), null);
  await store.saveCurrent("OpenNOW Desktop diagnostics\nevent.1 stream failed");

  assert.deepEqual(await store.beginAppRun(), {
    capturedAt: 1234,
    text: "OpenNOW Desktop diagnostics\nevent.1 stream failed",
  });
});

test("a corrupt current snapshot preserves the last readable run", async (t) => {
  const directory = await temporaryDirectory(t);
  const store = new DiagnosticHistoryStore(directory, () => 10);
  await store.saveCurrent("readable previous run");
  await store.beginAppRun();
  await writeFile(join(directory, "diagnostic-history", "current.txt.gz"), "not gzip");

  assert.deepEqual(await store.beginAppRun(), {
    capturedAt: 10,
    text: "readable previous run",
  });
});

test("bounded snapshots keep both startup context and the latest failure", () => {
  const original = `header-${"x".repeat(500)}-latest`;
  const bounded = boundDiagnosticSnapshot(original, 256);

  assert.equal(bounded.startsWith("header-"), true);
  assert.equal(bounded.endsWith("-latest"), true);
  assert.match(bounded, /persisted diagnostic snapshot truncated/);
  assert.ok(bounded.length <= 256);
});
