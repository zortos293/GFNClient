import {
  normalizeRecordingFps,
  normalizeRecordingResolution,
  type RecordingFps,
  type RecordingResolution,
} from "@shared/gfn";

const RECORDING_BOUNDS: Record<RecordingResolution, { width: number; height: number }> = {
  "720p": { width: 1280, height: 720 },
  "1080p": { width: 1920, height: 1080 },
  "1440p": { width: 2560, height: 1440 },
};

export interface RecordingDimensions {
  width: number;
  height: number;
}

export interface RecordingVideoCapture {
  track: MediaStreamTrack;
  dimensions: RecordingDimensions;
  fps: RecordingFps;
  dispose: () => void;
}

type CanvasFactory = () => HTMLCanvasElement;

export function resolveRecordingDimensions(
  sourceWidth: number,
  sourceHeight: number,
  resolution: unknown,
): RecordingDimensions | null {
  if (
    !Number.isFinite(sourceWidth) ||
    !Number.isFinite(sourceHeight) ||
    sourceWidth < 2 ||
    sourceHeight < 2
  ) {
    return null;
  }

  const bounds = RECORDING_BOUNDS[normalizeRecordingResolution(resolution)];
  const scale = Math.min(1, bounds.width / sourceWidth, bounds.height / sourceHeight);
  const evenDimension = (value: number): number => Math.max(2, Math.floor(value / 2) * 2);
  return {
    width: evenDimension(sourceWidth * scale),
    height: evenDimension(sourceHeight * scale),
  };
}

export function resolveRecordingCaptureFps(fps: unknown): RecordingFps {
  return normalizeRecordingFps(fps);
}

export function createRecordingVideoCapture(
  video: HTMLVideoElement,
  resolution: unknown,
  requestedFps: unknown,
  createCanvas: CanvasFactory = () => document.createElement("canvas"),
): RecordingVideoCapture | null {
  const dimensions = resolveRecordingDimensions(video.videoWidth, video.videoHeight, resolution);
  if (!dimensions) {
    return null;
  }

  const fps = resolveRecordingCaptureFps(requestedFps);
  const canvas = createCanvas();
  canvas.width = dimensions.width;
  canvas.height = dimensions.height;
  const context = canvas.getContext("2d", { alpha: false });
  if (!context) {
    return null;
  }

  const captureStream = canvas.captureStream(fps);
  const track = captureStream.getVideoTracks()[0];
  if (!track) {
    captureStream.getTracks().forEach((candidate) => candidate.stop());
    return null;
  }
  track.contentHint = "detail";

  let disposed = false;
  let callbackId: number | undefined;
  let intervalId: number | undefined;
  let lastDrawMs = Number.NEGATIVE_INFINITY;
  const frameIntervalMs = 1_000 / fps;

  const drawFrame = (now: number): void => {
    if (
      disposed ||
      video.readyState < 2 ||
      video.videoWidth < 2 ||
      video.videoHeight < 2 ||
      now - lastDrawMs < frameIntervalMs
    ) {
      return;
    }
    lastDrawMs = now;
    context.drawImage(video, 0, 0, dimensions.width, dimensions.height);
  };

  if (typeof video.requestVideoFrameCallback === "function") {
    const onVideoFrame: VideoFrameRequestCallback = (now) => {
      callbackId = undefined;
      if (disposed) return;
      drawFrame(now);
      callbackId = video.requestVideoFrameCallback(onVideoFrame);
    };
    callbackId = video.requestVideoFrameCallback(onVideoFrame);
  } else {
    intervalId = window.setInterval(() => drawFrame(performance.now()), frameIntervalMs);
  }

  const dispose = (): void => {
    if (disposed) return;
    disposed = true;
    if (callbackId !== undefined) {
      video.cancelVideoFrameCallback?.(callbackId);
      callbackId = undefined;
    }
    if (intervalId !== undefined) {
      window.clearInterval(intervalId);
      intervalId = undefined;
    }
    captureStream.getTracks().forEach((candidate) => {
      if (candidate.readyState !== "ended") {
        candidate.stop();
      }
    });
  };

  return { track, dimensions, fps, dispose };
}
