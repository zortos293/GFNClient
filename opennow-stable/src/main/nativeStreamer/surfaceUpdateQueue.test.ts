import assert from "node:assert/strict";
import test from "node:test";

import type { NativeRenderSurface } from "@shared/gfn";
import { NativeSurfaceUpdateQueue } from "./surfaceUpdateQueue";

function surface(width: number): NativeRenderSurface {
  return {
    rect: { x: 0, y: 0, width, height: 720 },
    visible: true,
    deviceScaleFactor: 1,
  };
}

function deferred(): { promise: Promise<void>; resolve(): void } {
  let resolve!: () => void;
  const promise = new Promise<void>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

test("holds the latest surface until the native handshake is ready", async () => {
  const sent: NativeRenderSurface[] = [];
  const queue = new NativeSurfaceUpdateQueue(async (value) => {
    sent.push(value);
  }, (error) => assert.fail(String(error)));

  queue.update(surface(1280));
  queue.update(surface(1920));
  assert.deepEqual(sent, []);

  await queue.markReady();
  assert.deepEqual(sent, [surface(1920)]);
});

test("coalesces updates without losing one that arrives during a send", async () => {
  const firstSend = deferred();
  const sent: NativeRenderSurface[] = [];
  const queue = new NativeSurfaceUpdateQueue(async (value) => {
    sent.push(value);
    if (sent.length === 1) {
      await firstSend.promise;
    }
  }, (error) => assert.fail(String(error)));

  queue.update(surface(1280));
  const ready = queue.markReady();
  await Promise.resolve();
  queue.update(surface(1600));
  queue.update(surface(2560));
  firstSend.resolve();
  await ready;

  assert.deepEqual(sent, [surface(1280), surface(2560)]);
});

test("reapplies the current surface after the native process restarts", async () => {
  const sent: NativeRenderSurface[] = [];
  const queue = new NativeSurfaceUpdateQueue(async (value) => {
    sent.push(value);
  }, (error) => assert.fail(String(error)));

  queue.update(surface(2560));
  await queue.markReady();
  queue.markNotReady();
  await queue.markReady();

  assert.deepEqual(sent, [surface(2560), surface(2560)]);
});

test("retries a failed revision when a newer surface arrives", async () => {
  const sent: NativeRenderSurface[] = [];
  const errors: unknown[] = [];
  const queue = new NativeSurfaceUpdateQueue(async (value) => {
    sent.push(value);
    if (sent.length === 1) {
      throw new Error("surface timeout");
    }
  }, (error) => errors.push(error));

  queue.update(surface(1280));
  await queue.markReady();
  queue.update(surface(1920));
  await Promise.resolve();

  assert.equal(errors.length, 1);
  assert.deepEqual(sent, [surface(1280), surface(1920)]);
});
