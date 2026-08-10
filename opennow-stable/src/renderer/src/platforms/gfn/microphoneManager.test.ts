/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { MicrophoneManager, type MicStateChange } from "./microphoneManager";

test("disposing during microphone acquisition stops the late capture stream", async () => {
  const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  let resolveCapture!: (stream: MediaStream) => void;
  const capture = new Promise<MediaStream>((resolve) => {
    resolveCapture = resolve;
  });
  let stopCalls = 0;
  const track = {
    kind: "audio",
    label: "Late microphone",
    stop: () => {
      stopCalls++;
    },
  } as MediaStreamTrack;
  const stream = {
    getTracks: () => [track],
    getAudioTracks: () => [track],
    addEventListener: () => {},
    removeEventListener: () => {},
  } as unknown as MediaStream;

  Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: {
      mediaDevices: {
        getUserMedia: () => capture,
        enumerateDevices: async () => [],
      },
    },
  });

  try {
    const states: MicStateChange[] = [];
    const manager = new MicrophoneManager();
    manager.setOnStateChange((state) => states.push(state));

    const initialization = manager.initialize();
    await Promise.resolve();
    manager.dispose();
    resolveCapture(stream);

    assert.equal(await initialization, false);
    assert.equal(stopCalls, 1);
    assert.equal(states.some((state) => state.state === "started"), false);
  } finally {
    if (navigatorDescriptor) {
      Object.defineProperty(globalThis, "navigator", navigatorDescriptor);
    } else {
      Reflect.deleteProperty(globalThis, "navigator");
    }
  }
});
