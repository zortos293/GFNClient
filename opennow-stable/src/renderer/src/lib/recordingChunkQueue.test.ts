/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { RecordingChunkQueue } from "./recordingChunkQueue";

test("recording finalization waits for queued chunks in order", async () => {
  const sent: string[] = [];
  let releaseFirst: (() => void) | undefined;
  const firstSend = new Promise<void>((resolve) => {
    releaseFirst = resolve;
  });
  const queue = new RecordingChunkQueue(async (buffer) => {
    const value = new TextDecoder().decode(buffer);
    if (value === "first") {
      await firstSend;
    }
    sent.push(value);
  });

  queue.enqueue(new Blob(["first"]));
  queue.enqueue(new Blob(["second"]));
  let flushed = false;
  const flush = queue.flush().then(() => {
    flushed = true;
  });
  await Promise.resolve();
  assert.equal(flushed, false);
  assert.deepEqual(sent, []);

  releaseFirst?.();
  await flush;
  assert.deepEqual(sent, ["first", "second"]);
});

test("recording finalization surfaces a failed chunk write", async () => {
  const expected = new Error("write failed");
  const queue = new RecordingChunkQueue(async () => {
    throw expected;
  });

  queue.enqueue(new Blob(["chunk"]));
  await assert.rejects(queue.flush(), expected);
});
