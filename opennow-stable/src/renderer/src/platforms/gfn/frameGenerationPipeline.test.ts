/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  calculateFrameGenerationDimensions,
  FrameGenerationPipeline,
  type FrameGenerationBackend,
  type FrameGenerationFrame,
} from "./frameGenerationPipeline";
import {
  FRAME_GENERATION_HISTORY_TEXTURE_RESERVE,
  FRAME_GENERATION_IN_FLIGHT_TEXTURE_RESERVE,
  FRAME_GENERATION_PRESENTATION_QUEUE_CAPACITY,
  FRAME_GENERATION_TEXTURE_POOL_SIZE,
} from "./frameGenerationCapacity";

test("caps arbitrary aspect ratios to dimensions divisible by sixteen", () => {
  const ultrawide = calculateFrameGenerationDimensions(3440, 1440, 720);
  assert.deepEqual(ultrawide, { width: 1712, height: 720 });
  assert.equal((ultrawide?.width ?? 1) % 16, 0);
  assert.equal((ultrawide?.height ?? 1) % 16, 0);

  assert.deepEqual(
    calculateFrameGenerationDimensions(1280, 800, 1080),
    { width: 1280, height: 800 },
  );
});

test("reserves texture capacity outside the bounded presentation queue", () => {
  assert.equal(
    FRAME_GENERATION_PRESENTATION_QUEUE_CAPACITY,
    FRAME_GENERATION_TEXTURE_POOL_SIZE
      - FRAME_GENERATION_HISTORY_TEXTURE_RESERVE
      - FRAME_GENERATION_IN_FLIGHT_TEXTURE_RESERVE,
  );
  assert.ok(FRAME_GENERATION_PRESENTATION_QUEUE_CAPACITY >= 2);
});

interface SharedFakeFrame {
  label: string;
  references: number;
}

class FakeFrame implements FrameGenerationFrame {
  private released = false;

  constructor(readonly shared: SharedFakeFrame) {
    shared.references++;
  }

  retain(): FrameGenerationFrame {
    assert.equal(this.released, false);
    return new FakeFrame(this.shared);
  }

  release(): void {
    if (this.released) return;
    this.released = true;
    this.shared.references--;
  }
}

class FakeBackend implements FrameGenerationBackend {
  readonly frames: SharedFakeFrame[] = [];
  readonly presented: string[] = [];
  disposed = false;
  failNextPresentation = false;
  private captureIndex = 0;
  private lostCallback: (() => void) | null = null;

  capture(): FrameGenerationFrame {
    const shared = { label: String.fromCharCode(65 + this.captureIndex++), references: 0 };
    this.frames.push(shared);
    return new FakeFrame(shared);
  }

  interpolate(previous: FrameGenerationFrame, current: FrameGenerationFrame): FrameGenerationFrame {
    const a = (previous as FakeFrame).shared.label;
    const b = (current as FakeFrame).shared.label;
    const shared = { label: `mid(${a},${b})`, references: 0 };
    this.frames.push(shared);
    return new FakeFrame(shared);
  }

  present(frame: FrameGenerationFrame): boolean {
    if (this.failNextPresentation) {
      this.failNextPresentation = false;
      return false;
    }
    this.presented.push((frame as FakeFrame).shared.label);
    return true;
  }

  onDeviceLost(callback: () => void): () => void {
    this.lostCallback = callback;
    return () => {
      this.lostCallback = null;
    };
  }

  dispose(): void {
    this.disposed = true;
  }
}

class FakeCanvas {
  readonly style: Record<string, string> = {};
  className = "";
  width = 0;
  height = 0;
  removed = false;

  remove(): void {
    this.removed = true;
  }
}

class FakeVideo {
  readonly style: Record<string, string> = { opacity: "1" };
  videoWidth = 1920;
  videoHeight = 1080;
  readyState = 4;
  insertedCanvas: FakeCanvas | null = null;
  cancelledCallbacks: number[] = [];
  private nextCallbackId = 1;
  private readonly callbacks = new Map<number, VideoFrameRequestCallback>();
  private readonly listeners = new Map<string, Set<() => void>>();

  insertAdjacentElement(_position: string, canvas: Element): void {
    this.insertedCanvas = canvas as unknown as FakeCanvas;
  }

  addEventListener(type: string, listener: () => void): void {
    const listeners = this.listeners.get(type) ?? new Set();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type: string, listener: () => void): void {
    this.listeners.get(type)?.delete(listener);
  }

  getBoundingClientRect(): DOMRect {
    return { width: 1280, height: 720 } as DOMRect;
  }

  requestVideoFrameCallback(callback: VideoFrameRequestCallback): number {
    const id = this.nextCallbackId++;
    this.callbacks.set(id, callback);
    return id;
  }

  cancelVideoFrameCallback(id: number): void {
    this.cancelledCallbacks.push(id);
    this.callbacks.delete(id);
  }

  invokeFrame(id: number, now: number, mediaTime: number): void {
    const callback = this.callbacks.get(id);
    this.callbacks.delete(id);
    callback?.(now, { mediaTime } as VideoFrameCallbackMetadata);
  }
}

class FakeAnimationFrames {
  cancelled: number[] = [];
  private nextId = 1;
  private readonly callbacks = new Map<number, FrameRequestCallback>();

  request = (callback: FrameRequestCallback): number => {
    const id = this.nextId++;
    this.callbacks.set(id, callback);
    return id;
  };

  cancel = (id: number): void => {
    this.cancelled.push(id);
    this.callbacks.delete(id);
  };

  invoke(id: number, now: number): void {
    const callback = this.callbacks.get(id);
    this.callbacks.delete(id);
    callback?.(now);
  }
}

function replaceGlobal(name: "document" | "navigator" | "ResizeObserver", value: unknown): () => void {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
  Object.defineProperty(globalThis, name, {
    configurable: true,
    writable: true,
    value,
  });
  return () => {
    if (descriptor) {
      Object.defineProperty(globalThis, name, descriptor);
    } else {
      Reflect.deleteProperty(globalThis, name);
    }
  };
}

test("presents source, neural midpoint, and next source at half-frame pacing", async () => {
  const canvas = new FakeCanvas();
  const video = new FakeVideo();
  const backend = new FakeBackend();
  const animationFrames = new FakeAnimationFrames();
  const restoreGlobals = [
    replaceGlobal("document", { createElement: () => canvas }),
    replaceGlobal("ResizeObserver", undefined),
  ];

  try {
    const pipeline = new FrameGenerationPipeline(
      video as unknown as HTMLVideoElement,
      { enabled: true, quality: 720 },
      {
        createBackend: async () => backend,
        requestAnimationFrame: animationFrames.request,
        cancelAnimationFrame: animationFrames.cancel,
      },
    );
    await pipeline.waitUntilSettled();

    assert.equal(canvas.width, 1280);
    assert.equal(canvas.height, 720);
    assert.equal(canvas.style.display, "none");
    assert.equal(video.style.opacity, "1");

    video.invokeFrame(1, 0, 0);
    assert.deepEqual(backend.presented, ["A"]);
    assert.equal(canvas.style.display, "block");

    video.invokeFrame(2, 1000 / 60, 1 / 60);
    assert.deepEqual(backend.presented, ["A"]);

    animationFrames.invoke(1, 25);
    assert.deepEqual(backend.presented, ["A", "mid(A,B)"]);

    animationFrames.invoke(2, 1000 / 30);
    assert.deepEqual(backend.presented, ["A", "mid(A,B)", "B"]);

    video.invokeFrame(3, 1000 / 30, 2 / 60);
    assert.equal(
      backend.frames.filter((frame) => frame.label.startsWith("mid(")).length,
      2,
    );
    animationFrames.invoke(3, 1000 / 24 + 0.1);
    animationFrames.invoke(4, 50.1);
    assert.deepEqual(
      backend.presented,
      ["A", "mid(A,B)", "B", "mid(B,C)", "C"],
    );
    assert.equal(pipeline.getCanvas(), canvas as unknown as HTMLCanvasElement);

    pipeline.updateSettings({ enabled: false, quality: 720 });
    assert.equal(pipeline.isActive(), false);
    assert.equal(canvas.style.display, "none");
    assert.equal(backend.disposed, true);
    assert.deepEqual(video.cancelledCallbacks, [4]);
    assert.ok(backend.frames.every((frame) => frame.references === 0));
  } finally {
    for (const restore of restoreGlobals.reverse()) restore();
  }
});

test("degrades to current-source passthrough after detecting a 60 Hz display cadence", async () => {
  const canvas = new FakeCanvas();
  const video = new FakeVideo();
  const backend = new FakeBackend();
  const animationFrames = new FakeAnimationFrames();
  const restoreGlobals = [
    replaceGlobal("document", { createElement: () => canvas }),
    replaceGlobal("ResizeObserver", undefined),
  ];

  try {
    const pipeline = new FrameGenerationPipeline(
      video as unknown as HTMLVideoElement,
      { enabled: true, quality: 720 },
      {
        createBackend: async () => backend,
        requestAnimationFrame: animationFrames.request,
        cancelAnimationFrame: animationFrames.cancel,
      },
    );
    await pipeline.waitUntilSettled();

    video.invokeFrame(1, 0, 0);
    assert.deepEqual(backend.presented, ["A"]);

    video.invokeFrame(2, 1000 / 60, 1 / 60);
    animationFrames.invoke(1, 1000 / 30 + 0.1);
    video.invokeFrame(3, 1000 / 30, 2 / 60);
    animationFrames.invoke(2, 50.1);

    const midpointCountAfterDetection = backend.frames
      .filter((frame) => frame.label.startsWith("mid("))
      .length;
    assert.equal(midpointCountAfterDetection, 2);
    assert.deepEqual(backend.presented, ["A", "B", "C"]);

    video.invokeFrame(4, 51, 3 / 60);
    assert.equal(
      backend.frames.filter((frame) => frame.label.startsWith("mid(")).length,
      midpointCountAfterDetection,
    );
    animationFrames.invoke(3, 55);
    assert.deepEqual(backend.presented, ["A", "B", "C", "D"]);

    video.invokeFrame(5, 1000 / 15, 4 / 60);
    animationFrames.invoke(4, 1000 / 12);
    assert.deepEqual(backend.presented, ["A", "B", "C", "D", "E"]);
    assert.equal(pipeline.isActive(), true);
    assert.equal(backend.disposed, false);
    assert.ok(
      backend.frames
        .filter((frame) => frame.label.startsWith("mid("))
        .every((frame) => frame.references === 0),
    );

    pipeline.dispose();
    assert.ok(backend.frames.every((frame) => frame.references === 0));
  } finally {
    for (const restore of restoreGlobals.reverse()) restore();
  }
});

test("fails open if current-source passthrough presentation fails", async () => {
  const canvas = new FakeCanvas();
  const video = new FakeVideo();
  const backend = new FakeBackend();
  const animationFrames = new FakeAnimationFrames();
  const restoreGlobals = [
    replaceGlobal("document", { createElement: () => canvas }),
    replaceGlobal("ResizeObserver", undefined),
  ];
  const originalWarn = console.warn;
  console.warn = () => undefined;

  try {
    const pipeline = new FrameGenerationPipeline(
      video as unknown as HTMLVideoElement,
      { enabled: true, quality: 720 },
      {
        createBackend: async () => backend,
        requestAnimationFrame: animationFrames.request,
        cancelAnimationFrame: animationFrames.cancel,
      },
    );
    await pipeline.waitUntilSettled();

    video.invokeFrame(1, 0, 0);
    video.invokeFrame(2, 1000 / 60, 1 / 60);
    animationFrames.invoke(1, 1000 / 30 + 0.1);
    video.invokeFrame(3, 1000 / 30, 2 / 60);
    animationFrames.invoke(2, 50.1);

    backend.failNextPresentation = true;
    video.invokeFrame(4, 51, 3 / 60);
    animationFrames.invoke(3, 55);

    assert.equal(pipeline.isActive(), false);
    assert.equal(pipeline.getCanvas(), null);
    assert.equal(canvas.style.display, "none");
    assert.equal(backend.disposed, true);
    assert.ok(backend.frames.every((frame) => frame.references === 0));
  } finally {
    console.warn = originalWarn;
    for (const restore of restoreGlobals.reverse()) restore();
  }
});

test("unsupported WebGPU fails open without changing or covering the raw video", async () => {
  const canvas = new FakeCanvas();
  const video = new FakeVideo();
  const animationFrames = new FakeAnimationFrames();
  const restoreGlobals = [
    replaceGlobal("document", { createElement: () => canvas }),
    replaceGlobal("navigator", {}),
    replaceGlobal("ResizeObserver", undefined),
  ];
  const originalWarn = console.warn;
  console.warn = () => undefined;

  try {
    const pipeline = new FrameGenerationPipeline(
      video as unknown as HTMLVideoElement,
      { enabled: true, quality: 720 },
      {
        requestAnimationFrame: animationFrames.request,
        cancelAnimationFrame: animationFrames.cancel,
      },
    );
    await pipeline.waitUntilSettled();

    assert.equal(pipeline.isActive(), false);
    assert.equal(pipeline.getCanvas(), null);
    assert.equal(canvas.style.display, "none");
    assert.equal(video.style.opacity, "1");
    pipeline.dispose();
    assert.equal(canvas.removed, true);
  } finally {
    console.warn = originalWarn;
    for (const restore of restoreGlobals.reverse()) restore();
  }
});

test("dispose cancels decoded-frame and presentation callbacks", async () => {
  const canvas = new FakeCanvas();
  const video = new FakeVideo();
  const backend = new FakeBackend();
  const animationFrames = new FakeAnimationFrames();
  const restoreGlobals = [
    replaceGlobal("document", { createElement: () => canvas }),
    replaceGlobal("ResizeObserver", undefined),
  ];

  try {
    const pipeline = new FrameGenerationPipeline(
      video as unknown as HTMLVideoElement,
      { enabled: true, quality: 720 },
      {
        createBackend: async () => backend,
        requestAnimationFrame: animationFrames.request,
        cancelAnimationFrame: animationFrames.cancel,
      },
    );
    await pipeline.waitUntilSettled();
    video.invokeFrame(1, 0, 0);
    video.invokeFrame(2, 1000 / 60, 1 / 60);

    pipeline.dispose();

    assert.equal(backend.disposed, true);
    assert.equal(canvas.removed, true);
    assert.deepEqual(video.cancelledCallbacks, [3]);
    assert.deepEqual(animationFrames.cancelled, [1]);
    assert.ok(backend.frames.every((frame) => frame.references === 0));
  } finally {
    for (const restore of restoreGlobals.reverse()) restore();
  }
});
