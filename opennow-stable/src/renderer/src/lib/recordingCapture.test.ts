/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  createRecordingVideoCapture,
  resolveRecordingCaptureFps,
  resolveRecordingDimensions,
} from "./recordingCapture";

test("recording dimensions preserve aspect ratio, avoid upscaling, and stay even", () => {
  assert.deepEqual(resolveRecordingDimensions(3840, 2160, "720p"), { width: 1280, height: 720 });
  assert.deepEqual(resolveRecordingDimensions(1920, 1200, "720p"), { width: 1152, height: 720 });
  assert.deepEqual(resolveRecordingDimensions(640, 360, "1440p"), { width: 640, height: 360 });
  assert.deepEqual(resolveRecordingDimensions(1919, 1079, "1080p"), { width: 1918, height: 1078 });
  assert.deepEqual(resolveRecordingDimensions(3840, 2160, "invalid"), { width: 1280, height: 720 });
  assert.equal(resolveRecordingDimensions(0, 1080, "720p"), null);
});

test("recording capture FPS is restricted to the supported cadence", () => {
  assert.equal(resolveRecordingCaptureFps(30), 30);
  assert.equal(resolveRecordingCaptureFps(60), 60);
  assert.equal(resolveRecordingCaptureFps(120), 60);
  assert.equal(resolveRecordingCaptureFps(0), 30);
  assert.equal(resolveRecordingCaptureFps("invalid"), 30);
});

test("recording capture draws only presented frames at the requested cap and cleans up", () => {
  const callbacks = new Map<number, VideoFrameRequestCallback>();
  const cancelled: number[] = [];
  let nextCallbackId = 1;
  let drawCount = 0;
  let stopCount = 0;

  const track = {
    contentHint: "",
    readyState: "live",
    stop: () => {
      stopCount += 1;
      track.readyState = "ended";
    },
  };
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => ({
      drawImage: () => {
        drawCount += 1;
      },
    }),
    captureStream: (fps: number) => {
      assert.equal(fps, 30);
      return {
        getVideoTracks: () => [track],
        getTracks: () => [track],
      };
    },
  };
  const video = {
    videoWidth: 2560,
    videoHeight: 1440,
    readyState: 4,
    requestVideoFrameCallback: (callback: VideoFrameRequestCallback) => {
      const id = nextCallbackId;
      nextCallbackId += 1;
      callbacks.set(id, callback);
      return id;
    },
    cancelVideoFrameCallback: (id: number) => {
      cancelled.push(id);
      callbacks.delete(id);
    },
  };

  const capture = createRecordingVideoCapture(
    video as unknown as HTMLVideoElement,
    "720p",
    30,
    () => canvas as unknown as HTMLCanvasElement,
  );
  assert.ok(capture);
  assert.deepEqual(capture.dimensions, { width: 1280, height: 720 });

  callbacks.get(1)?.(0, {} as VideoFrameCallbackMetadata);
  callbacks.get(2)?.(10, {} as VideoFrameCallbackMetadata);
  callbacks.get(3)?.(34, {} as VideoFrameCallbackMetadata);
  assert.equal(drawCount, 2);

  capture.dispose();
  capture.dispose();
  assert.deepEqual(cancelled, [4]);
  assert.equal(stopCount, 1);
  assert.equal(track.readyState, "ended");
});
