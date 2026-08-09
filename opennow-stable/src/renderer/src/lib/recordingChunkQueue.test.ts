/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  finishRecordingAfterQueuedChunks,
  RecordingChunkQueue,
} from "./recordingChunkQueue";

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

test("unmount during finalization preserves the captured queue until finish", async () => {
  let releaseChunk!: () => void;
  let markChunkStarted!: () => void;
  const chunkReleased = new Promise<void>((resolve) => {
    releaseChunk = resolve;
  });
  const chunkStarted = new Promise<void>((resolve) => {
    markChunkStarted = resolve;
  });
  const sent: string[] = [];
  const queue = new RecordingChunkQueue(async (buffer) => {
    markChunkStarted();
    await chunkReleased;
    sent.push(new TextDecoder().decode(buffer));
  });
  queue.enqueue(new Blob(["penultimate chunk"]));
  queue.enqueue(new Blob(["last chunk"]));

  let recordingIdRef: string | null = "recording-1";
  let chunkQueueRef: RecordingChunkQueue | null = queue;
  let thumbnailRef: string | null = "data:image/jpeg;base64,thumbnail";
  let finalizationInFlight = true;
  let abortCalls = 0;
  const finishCalls: Array<{ recordingId: string; thumbnail: string | null }> = [];

  const settledRecordingId = recordingIdRef;
  const settledChunkQueue = chunkQueueRef;
  const settledThumbnail = thumbnailRef;
  recordingIdRef = null;
  chunkQueueRef = null;
  thumbnailRef = null;

  const finalization = finishRecordingAfterQueuedChunks(
    settledChunkQueue,
    async () => {
      finishCalls.push({
        recordingId: settledRecordingId,
        thumbnail: settledThumbnail,
      });
    },
  ).finally(() => {
    finalizationInFlight = false;
  });

  await chunkStarted;
  const unmount = (): void => {
    if (finalizationInFlight) return;
    abortCalls += 1;
  };
  unmount();
  assert.equal(abortCalls, 0);
  assert.deepEqual(finishCalls, []);

  releaseChunk();
  await finalization;
  assert.deepEqual(sent, ["penultimate chunk", "last chunk"]);
  assert.deepEqual(finishCalls, [{
    recordingId: "recording-1",
    thumbnail: "data:image/jpeg;base64,thumbnail",
  }]);
});
