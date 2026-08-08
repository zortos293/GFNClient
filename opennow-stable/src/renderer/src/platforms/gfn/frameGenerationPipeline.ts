import type { FrameGenerationQuality, FrameGenerationSettings } from "@shared/gfn";

const DEFAULT_SOURCE_FRAME_MS = 1000 / 60;
const MIN_SOURCE_FRAME_MS = 1000 / 240;
const MAX_SOURCE_FRAME_MS = 100;
const MAX_PRESENTATION_QUEUE = 6;

export interface FrameGenerationFrame {
  retain(): FrameGenerationFrame;
  release(): void;
}

export interface FrameGenerationBackend {
  capture(video: HTMLVideoElement): FrameGenerationFrame;
  interpolate(
    previous: FrameGenerationFrame,
    current: FrameGenerationFrame,
  ): FrameGenerationFrame;
  present(frame: FrameGenerationFrame): boolean;
  onDeviceLost(callback: () => void): () => void;
  dispose(): void;
}

export interface FrameGenerationDimensions {
  width: number;
  height: number;
}

export type FrameGenerationBackendFactory = (
  canvas: HTMLCanvasElement,
  dimensions: FrameGenerationDimensions,
) => Promise<FrameGenerationBackend>;

export interface FrameGenerationPipelineDependencies {
  createBackend?: FrameGenerationBackendFactory;
  requestAnimationFrame?: (callback: FrameRequestCallback) => number;
  cancelAnimationFrame?: (id: number) => void;
}

interface PresentationItem {
  dueMs: number;
  frame: FrameGenerationFrame;
}

async function createDefaultBackend(
  canvas: HTMLCanvasElement,
  dimensions: FrameGenerationDimensions,
): Promise<FrameGenerationBackend> {
  if (typeof navigator === "undefined" || !navigator.gpu) {
    throw new Error("WebGPU is unavailable");
  }
  const { createWebGpuFrameGenerationBackend } = await import("./frameGenerationWebGpu");
  return createWebGpuFrameGenerationBackend(canvas, dimensions);
}

export function calculateFrameGenerationDimensions(
  sourceWidth: number,
  sourceHeight: number,
  quality: FrameGenerationQuality,
): FrameGenerationDimensions | null {
  if (sourceWidth < 16 || sourceHeight < 16) {
    return null;
  }

  const scale = Math.min(1, quality / sourceHeight);
  const width = Math.floor((sourceWidth * scale) / 16) * 16;
  const height = Math.floor((sourceHeight * scale) / 16) * 16;
  if (width < 16 || height < 16) {
    return null;
  }
  return { width, height };
}

/**
 * Owns decoded-frame capture and 2x presentation pacing for the embedded
 * WebRTC video. The raw video remains untouched and is only covered after a
 * generated canvas frame has been successfully submitted.
 */
export class FrameGenerationPipeline {
  private readonly canvas: HTMLCanvasElement;
  private readonly createBackend: FrameGenerationBackendFactory;
  private readonly requestRaf: (callback: FrameRequestCallback) => number;
  private readonly cancelRaf: (id: number) => void;
  private readonly presentationQueue: PresentationItem[] = [];

  private settings: FrameGenerationSettings;
  private backend: FrameGenerationBackend | null = null;
  private currentFrame: FrameGenerationFrame | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private removeDeviceLostHandler: (() => void) | null = null;
  private frameCallbackId: number | null = null;
  private captureRafId: number | null = null;
  private presentationRafId: number | null = null;
  private lifecycleGeneration = 0;
  private initializationPromise: Promise<void> = Promise.resolve();
  private initializedDimensions: FrameGenerationDimensions | null = null;
  private initializedQuality: FrameGenerationQuality | null = null;
  private disposed = false;
  private hasPresentedFrame = false;
  private lastMediaTimeSeconds: number | null = null;
  private sourceFrameDurationMs = DEFAULT_SOURCE_FRAME_MS;

  constructor(
    private readonly videoElement: HTMLVideoElement,
    initialSettings: FrameGenerationSettings,
    dependencies: FrameGenerationPipelineDependencies = {},
  ) {
    this.settings = { ...initialSettings };
    this.createBackend = dependencies.createBackend ?? createDefaultBackend;
    this.requestRaf = dependencies.requestAnimationFrame ?? requestAnimationFrame;
    this.cancelRaf = dependencies.cancelAnimationFrame ?? cancelAnimationFrame;

    this.canvas = document.createElement("canvas");
    this.canvas.className = "sv-frame-generation-canvas";
    this.canvas.style.position = "absolute";
    this.canvas.style.zIndex = "4";
    this.canvas.style.pointerEvents = "none";
    this.canvas.style.display = "none";
    this.canvas.style.background = "black";
    videoElement.insertAdjacentElement("afterend", this.canvas);

    videoElement.addEventListener("loadeddata", this.onSourceReady);
    videoElement.addEventListener("resize", this.onSourceResize);
    if (typeof ResizeObserver !== "undefined") {
      this.resizeObserver = new ResizeObserver(this.syncCanvasLayout);
      this.resizeObserver.observe(videoElement);
    }

    this.applyActivation();
  }

  public getCanvas(): HTMLCanvasElement | null {
    return this.hasPresentedFrame ? this.canvas : null;
  }

  public isActive(): boolean {
    return this.backend !== null;
  }

  public updateSettings(settings: FrameGenerationSettings): void {
    const qualityChanged = settings.quality !== this.settings.quality;
    this.settings = { ...settings };
    if (!settings.enabled) {
      this.stopRuntime();
      return;
    }
    if (qualityChanged && (this.backend || this.initializedQuality !== null)) {
      this.restart();
      return;
    }
    this.applyActivation();
  }

  public waitUntilSettled(): Promise<void> {
    return this.initializationPromise;
  }

  public dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.stopRuntime();
    this.videoElement.removeEventListener("loadeddata", this.onSourceReady);
    this.videoElement.removeEventListener("resize", this.onSourceResize);
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.canvas.remove();
  }

  private readonly onSourceReady = (): void => {
    this.applyActivation();
  };

  private readonly onSourceResize = (): void => {
    const dimensions = calculateFrameGenerationDimensions(
      this.videoElement.videoWidth,
      this.videoElement.videoHeight,
      this.settings.quality,
    );
    if (
      this.backend
      && (
        dimensions?.width !== this.initializedDimensions?.width
        || dimensions?.height !== this.initializedDimensions?.height
      )
    ) {
      this.restart();
      return;
    }
    this.syncCanvasLayout();
    this.applyActivation();
  };

  private applyActivation(): void {
    if (this.disposed || !this.settings.enabled || this.backend) {
      return;
    }

    const dimensions = calculateFrameGenerationDimensions(
      this.videoElement.videoWidth,
      this.videoElement.videoHeight,
      this.settings.quality,
    );
    if (!dimensions) {
      return;
    }

    const generation = ++this.lifecycleGeneration;
    this.initializedDimensions = dimensions;
    this.initializedQuality = this.settings.quality;
    this.canvas.width = dimensions.width;
    this.canvas.height = dimensions.height;
    this.syncCanvasLayout();

    this.initializationPromise = this.createBackend(this.canvas, dimensions)
      .then((backend) => {
        if (
          generation !== this.lifecycleGeneration
          || this.disposed
          || !this.settings.enabled
        ) {
          backend.dispose();
          return;
        }

        this.backend = backend;
        this.removeDeviceLostHandler = backend.onDeviceLost(() => {
          if (generation === this.lifecycleGeneration) {
            this.failOpen("WebGPU device was lost");
          }
        });
        this.startCaptureLoop(generation);
      })
      .catch(() => {
        if (generation === this.lifecycleGeneration) {
          this.failOpen("WebGPU frame generation initialization failed");
        }
      });
  }

  private restart(): void {
    this.stopRuntime();
    this.applyActivation();
  }

  private failOpen(message: string): void {
    console.warn(`[FrameGeneration] ${message}; showing the raw stream`);
    this.stopRuntime();
  }

  private stopRuntime(): void {
    this.lifecycleGeneration++;
    this.stopCaptureLoop();
    this.stopPresentationLoop();
    this.clearPresentationQueue();
    this.currentFrame?.release();
    this.currentFrame = null;
    this.removeDeviceLostHandler?.();
    this.removeDeviceLostHandler = null;
    this.backend?.dispose();
    this.backend = null;
    this.initializedDimensions = null;
    this.initializedQuality = null;
    this.lastMediaTimeSeconds = null;
    this.sourceFrameDurationMs = DEFAULT_SOURCE_FRAME_MS;
    this.hasPresentedFrame = false;
    this.canvas.style.display = "none";
  }

  private startCaptureLoop(generation: number): void {
    this.stopCaptureLoop();
    if (typeof this.videoElement.requestVideoFrameCallback === "function") {
      const capture = (now: DOMHighResTimeStamp, metadata: VideoFrameCallbackMetadata): void => {
        if (!this.canContinue(generation)) return;
        this.frameCallbackId = null;
        this.captureFrame(now, metadata.mediaTime);
        if (!this.canContinue(generation)) return;
        this.frameCallbackId = this.videoElement.requestVideoFrameCallback(capture);
      };
      this.frameCallbackId = this.videoElement.requestVideoFrameCallback(capture);
      return;
    }

    const capture = (now: DOMHighResTimeStamp): void => {
      if (!this.canContinue(generation)) return;
      this.captureRafId = null;
      this.captureFrame(now, null);
      if (!this.canContinue(generation)) return;
      this.captureRafId = this.requestRaf(capture);
    };
    this.captureRafId = this.requestRaf(capture);
  }

  private stopCaptureLoop(): void {
    if (this.frameCallbackId !== null) {
      this.videoElement.cancelVideoFrameCallback?.(this.frameCallbackId);
      this.frameCallbackId = null;
    }
    if (this.captureRafId !== null) {
      this.cancelRaf(this.captureRafId);
      this.captureRafId = null;
    }
  }

  private canContinue(generation: number): boolean {
    return (
      generation === this.lifecycleGeneration
      && !this.disposed
      && this.settings.enabled
      && this.backend !== null
    );
  }

  private captureFrame(now: number, mediaTimeSeconds: number | null): void {
    const backend = this.backend;
    if (
      !backend
      || this.videoElement.readyState < 2
      || this.videoElement.videoWidth <= 0
      || this.videoElement.videoHeight <= 0
    ) {
      return;
    }

    const expectedDimensions = calculateFrameGenerationDimensions(
      this.videoElement.videoWidth,
      this.videoElement.videoHeight,
      this.settings.quality,
    );
    if (
      !expectedDimensions
      || expectedDimensions.width !== this.initializedDimensions?.width
      || expectedDimensions.height !== this.initializedDimensions?.height
    ) {
      this.restart();
      return;
    }

    let captured: FrameGenerationFrame;
    try {
      captured = backend.capture(this.videoElement);
    } catch {
      this.failOpen("decoded-frame capture failed");
      return;
    }

    if (!this.currentFrame) {
      let presented = false;
      try {
        presented = backend.present(captured);
      } catch {
        presented = false;
      }
      if (!presented) {
        captured.release();
        this.failOpen("initial canvas presentation failed");
        return;
      }

      this.currentFrame = captured;
      this.lastMediaTimeSeconds = mediaTimeSeconds;
      this.revealCanvas();
      return;
    }

    if (mediaTimeSeconds !== null && this.lastMediaTimeSeconds !== null) {
      const measured = (mediaTimeSeconds - this.lastMediaTimeSeconds) * 1000;
      if (Number.isFinite(measured) && measured > 0) {
        this.sourceFrameDurationMs = Math.max(
          MIN_SOURCE_FRAME_MS,
          Math.min(MAX_SOURCE_FRAME_MS, measured),
        );
      }
    }
    this.lastMediaTimeSeconds = mediaTimeSeconds;

    const previous = this.currentFrame;
    let midpoint: FrameGenerationFrame;
    try {
      midpoint = backend.interpolate(previous, captured);
    } catch {
      captured.release();
      this.failOpen("frame interpolation failed");
      return;
    }

    this.enqueuePresentation(now + this.sourceFrameDurationMs / 2, midpoint);
    this.enqueuePresentation(now + this.sourceFrameDurationMs, captured.retain());

    previous.release();
    this.currentFrame = captured;
    this.startPresentationLoop();
  }

  private enqueuePresentation(dueMs: number, frame: FrameGenerationFrame): void {
    this.presentationQueue.push({ dueMs, frame });
    while (this.presentationQueue.length > MAX_PRESENTATION_QUEUE) {
      this.presentationQueue.shift()?.frame.release();
    }
  }

  private startPresentationLoop(): void {
    if (this.presentationRafId !== null || this.presentationQueue.length === 0) {
      return;
    }
    this.presentationRafId = this.requestRaf(this.presentFrame);
  }

  private readonly presentFrame = (now: DOMHighResTimeStamp): void => {
    this.presentationRafId = null;
    const backend = this.backend;
    const first = this.presentationQueue[0];
    if (!backend || !first) return;

    if (now < first.dueMs) {
      this.startPresentationLoop();
      return;
    }

    let next: PresentationItem | null = null;
    while (this.presentationQueue[0]?.dueMs <= now) {
      next?.frame.release();
      next = this.presentationQueue.shift() ?? null;
    }
    if (!next) {
      this.startPresentationLoop();
      return;
    }

    let presented = false;
    try {
      presented = backend.present(next.frame);
    } catch {
      presented = false;
    } finally {
      next.frame.release();
    }

    if (!presented) {
      this.failOpen("canvas presentation failed");
      return;
    }

    this.revealCanvas();
    this.startPresentationLoop();
  };

  private revealCanvas(): void {
    if (this.hasPresentedFrame) return;
    this.hasPresentedFrame = true;
    this.canvas.style.display = "block";
  }

  private stopPresentationLoop(): void {
    if (this.presentationRafId !== null) {
      this.cancelRaf(this.presentationRafId);
      this.presentationRafId = null;
    }
  }

  private clearPresentationQueue(): void {
    for (const item of this.presentationQueue.splice(0)) {
      item.frame.release();
    }
  }

  private readonly syncCanvasLayout = (): void => {
    const rect = this.videoElement.getBoundingClientRect();
    const sourceWidth = this.videoElement.videoWidth;
    const sourceHeight = this.videoElement.videoHeight;
    if (rect.width <= 0 || rect.height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
      return;
    }

    const scale = Math.min(rect.width / sourceWidth, rect.height / sourceHeight);
    const width = sourceWidth * scale;
    const height = sourceHeight * scale;
    this.canvas.style.inset = "auto";
    this.canvas.style.left = `${(rect.width - width) / 2}px`;
    this.canvas.style.top = `${(rect.height - height) / 2}px`;
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
  };
}
