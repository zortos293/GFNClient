/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { PeerMediaLifecycleController } from "./peerMediaLifecycleController";

class FakeMediaStream {
  private readonly tracks: MediaStreamTrack[] = [];

  get active(): boolean {
    return this.tracks.length > 0;
  }

  addTrack(track: MediaStreamTrack): void {
    this.tracks.push(track);
  }

  removeTrack(track: MediaStreamTrack): void {
    const index = this.tracks.indexOf(track);
    if (index >= 0) {
      this.tracks.splice(index, 1);
    }
  }

  getTracks(): MediaStreamTrack[] {
    return [...this.tracks];
  }

  getAudioTracks(): MediaStreamTrack[] {
    return this.tracks.filter((track) => track.kind === "audio");
  }

  getVideoTracks(): MediaStreamTrack[] {
    return this.tracks.filter((track) => track.kind === "video");
  }
}

class FakeAudioNode {
  disconnectCalls = 0;
  connectedTo: unknown = null;

  connect(destination: unknown): void {
    this.connectedTo = destination;
  }

  disconnect(): void {
    this.disconnectCalls++;
    this.connectedTo = null;
  }
}

class FakeGainNode extends FakeAudioNode {
  readonly gain = { value: 1 };
}

class FakeAudioContext {
  readonly source = new FakeAudioNode();
  readonly gain = new FakeGainNode();
  readonly destination = {};
  readonly baseLatency = 0.004;
  readonly sampleRate = 48000;
  closeCalls = 0;
  resumeCalls = 0;

  constructor(
    public state: AudioContextState,
    private readonly resumeResult: () => Promise<void> = async () => {},
  ) {}

  createMediaStreamSource(): MediaStreamAudioSourceNode {
    return this.source as unknown as MediaStreamAudioSourceNode;
  }

  createGain(): GainNode {
    return this.gain as unknown as GainNode;
  }

  async resume(): Promise<void> {
    this.resumeCalls++;
    await this.resumeResult();
  }

  async close(): Promise<void> {
    this.closeCalls++;
    this.state = "closed";
  }
}

interface AudioElementFake {
  muted: boolean;
  volume: number;
  srcObject: MediaProvider | null;
  playCalls: number;
  pauseCalls: number;
  play: () => Promise<void>;
  pause: () => void;
}

interface VideoElementFake {
  srcObject: MediaProvider | null;
  paused: boolean;
  readyState: number;
  videoWidth: number;
  videoHeight: number;
  play: () => Promise<void>;
  requestVideoFrameCallback: (callback: VideoFrameRequestCallback) => number;
  cancelVideoFrameCallback: (id: number) => void;
}

function createHarness(contexts: FakeAudioContext[], onRenderFrame = () => {}) {
  const logs: string[] = [];
  const audioElement: AudioElementFake = {
    muted: false,
    volume: 0,
    srcObject: null,
    playCalls: 0,
    pauseCalls: 0,
    play: async () => {
      audioElement.playCalls++;
    },
    pause: () => {
      audioElement.pauseCalls++;
    },
  };
  const videoElement: VideoElementFake = {
    srcObject: null,
    paused: true,
    readyState: 0,
    videoWidth: 0,
    videoHeight: 0,
    play: async () => {},
    requestVideoFrameCallback: () => 0,
    cancelVideoFrameCallback: () => {},
  };
  let contextIndex = 0;
  const controller = new PeerMediaLifecycleController({
    videoElement: videoElement as unknown as HTMLVideoElement,
    audioElement: audioElement as unknown as HTMLAudioElement,
    onRenderFrame,
    log: (message) => logs.push(message),
    createAudioContext: () => contexts[contextIndex++] as unknown as AudioContext,
  });
  return { audioElement, controller, logs, videoElement };
}

function audioTrack(id: string): MediaStreamTrack {
  return { id, kind: "audio" } as MediaStreamTrack;
}

function videoTrack(id: string): MediaStreamTrack {
  return { id, kind: "video" } as MediaStreamTrack;
}

function installMediaStreamFake(): () => void {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "MediaStream");
  Object.defineProperty(globalThis, "MediaStream", {
    configurable: true,
    writable: true,
    value: FakeMediaStream,
  });
  return () => {
    if (descriptor) {
      Object.defineProperty(globalThis, "MediaStream", descriptor);
    } else {
      Reflect.deleteProperty(globalThis, "MediaStream");
    }
  };
}

async function flushPromises(): Promise<void> {
  await new Promise<void>((resolve) => setImmediate(resolve));
}

function deferred<T>(): {
  promise: Promise<T>;
  reject: (reason: unknown) => void;
} {
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((_resolve, rejectPromise) => {
    reject = rejectPromise;
  });
  return { promise, reject };
}

test("running Web Audio stays on the low-latency route without direct playback", () => {
  const restoreMediaStream = installMediaStreamFake();
  try {
    const context = new FakeAudioContext("running");
    const { audioElement, controller, logs } = createHarness([context]);

    controller.attachTrack(audioTrack("running"));

    assert.equal(context.resumeCalls, 0);
    assert.equal(audioElement.muted, true);
    assert.equal(audioElement.playCalls, 0);
    assert.equal(context.source.connectedTo, context.gain);
    assert.equal(context.gain.connectedTo, context.destination);
    assert.ok(logs.some((message) => message.includes("Audio routed through AudioContext")));
  } finally {
    restoreMediaStream();
  }
});

test("rejected AudioContext resume falls back after disconnecting Web Audio", async () => {
  const restoreMediaStream = installMediaStreamFake();
  try {
    const context = new FakeAudioContext(
      "suspended",
      async () => Promise.reject(new Error("resume denied")),
    );
    const { audioElement, controller, logs } = createHarness([context]);

    controller.attachTrack(audioTrack("rejected"));
    assert.equal(audioElement.muted, true);
    await flushPromises();

    assert.equal(context.resumeCalls, 1);
    assert.equal(context.source.disconnectCalls, 1);
    assert.equal(context.gain.disconnectCalls, 1);
    assert.equal(context.closeCalls, 1);
    assert.equal(audioElement.muted, false);
    assert.equal(audioElement.playCalls, 1);
    assert.ok(logs.some((message) => message.includes("AudioContext resume failed")));
  } finally {
    restoreMediaStream();
  }
});

test("AudioContext that remains suspended after resume falls back to direct playback", async () => {
  const restoreMediaStream = installMediaStreamFake();
  try {
    const context = new FakeAudioContext("suspended");
    const { audioElement, controller, logs } = createHarness([context]);

    controller.attachTrack(audioTrack("suspended"));
    await flushPromises();

    assert.equal(context.resumeCalls, 1);
    assert.equal(context.closeCalls, 1);
    assert.equal(audioElement.muted, false);
    assert.equal(audioElement.playCalls, 1);
    assert.ok(logs.some((message) => message.includes("remained suspended after resume")));
  } finally {
    restoreMediaStream();
  }
});

test("stale resume failure cannot override a replacement Web Audio route", async () => {
  const restoreMediaStream = installMediaStreamFake();
  try {
    const pendingResume = deferred<void>();
    const oldContext = new FakeAudioContext("suspended", () => pendingResume.promise);
    const newContext = new FakeAudioContext("running");
    const { audioElement, controller } = createHarness([oldContext, newContext]);

    controller.attachTrack(audioTrack("old"));
    controller.attachTrack(audioTrack("new"));
    pendingResume.reject(new Error("late rejection"));
    await flushPromises();

    assert.equal(oldContext.closeCalls, 1);
    assert.equal(newContext.closeCalls, 0);
    assert.equal(audioElement.muted, true);
    assert.equal(audioElement.playCalls, 0);
    assert.equal(newContext.source.connectedTo, newContext.gain);
  } finally {
    restoreMediaStream();
  }
});

test("reset invalidates pending resume fallback and leaves direct audio stopped", async () => {
  const restoreMediaStream = installMediaStreamFake();
  try {
    const pendingResume = deferred<void>();
    const context = new FakeAudioContext("suspended", () => pendingResume.promise);
    const { audioElement, controller } = createHarness([context]);

    controller.attachTrack(audioTrack("pending"));
    controller.reset();
    pendingResume.reject(new Error("late rejection"));
    await flushPromises();

    assert.equal(context.closeCalls, 1);
    assert.equal(audioElement.muted, true);
    assert.equal(audioElement.playCalls, 0);
  } finally {
    restoreMediaStream();
  }
});

test("video track replacement leaves exactly one active frame callback", () => {
  const restoreMediaStream = installMediaStreamFake();
  const windowDescriptor = Object.getOwnPropertyDescriptor(globalThis, "window");
  Object.defineProperty(globalThis, "window", {
    configurable: true,
    value: { setTimeout: () => 0 },
  });
  try {
    const callbacks = new Map<number, VideoFrameRequestCallback>();
    const cancelled: number[] = [];
    let nextCallbackId = 1;
    let renderedFrames = 0;
    const { controller, videoElement } = createHarness([], () => {
      renderedFrames++;
    });
    videoElement.requestVideoFrameCallback = (callback) => {
      const id = nextCallbackId++;
      callbacks.set(id, callback);
      return id;
    };
    videoElement.cancelVideoFrameCallback = (id) => {
      cancelled.push(id);
      callbacks.delete(id);
    };

    controller.attachTrack(videoTrack("old"));
    const staleCallback = callbacks.get(1);
    assert.ok(staleCallback);

    controller.attachTrack(videoTrack("current"));
    assert.deepEqual(cancelled, [1]);
    assert.deepEqual([...callbacks.keys()], [2]);

    staleCallback(0, {} as VideoFrameCallbackMetadata);
    assert.equal(renderedFrames, 0);
    assert.deepEqual([...callbacks.keys()], [2]);

    const currentCallback = callbacks.get(2);
    assert.ok(currentCallback);
    callbacks.delete(2);
    currentCallback(0, {} as VideoFrameCallbackMetadata);
    assert.equal(renderedFrames, 1);
    assert.deepEqual([...callbacks.keys()], [3]);

    controller.clearTracks();
    assert.deepEqual(cancelled, [1, 3]);
  } finally {
    if (windowDescriptor) {
      Object.defineProperty(globalThis, "window", windowDescriptor);
    } else {
      Reflect.deleteProperty(globalThis, "window");
    }
    restoreMediaStream();
  }
});
