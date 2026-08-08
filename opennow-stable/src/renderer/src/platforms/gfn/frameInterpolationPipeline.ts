import type { FrameInterpolationSettings } from "@shared/gfn";
import { frameInterpolationIsActive } from "@shared/gfn";
import { createRT, type RT } from "framegen";

/**
 * WebGPU neural frame-interpolation overlay for the embedded WebRTC stream.
 *
 * Uses the Framegen WGSL runtime (`framegen` npm, MIT code) to synthesize
 * intermediate frames between consecutive decoded <video> frames and present
 * them on an overlay canvas (object-fit: contain). Native streamer mode is
 * unsupported — video is rendered outside Chromium.
 *
 * Model weights: non-commercial research/personal use
 * (Framegen WEIGHTS_LICENSE). Loaded from app public assets when present,
 * otherwise from the pinned jsDelivr npm CDN mirror.
 */

const FRAMEGEN_WEIGHTS_VERSION = "1.4.0";
const FRAMEGEN_WEIGHTS_CDN =
  `https://cdn.jsdelivr.net/npm/framegen@${FRAMEGEN_WEIGHTS_VERSION}/weights`;

type GpuDevice = GPUDevice;
type GpuTexture = GPUTexture;

interface LoadedWeights {
  bin: ArrayBuffer;
  manifest: Record<string, { offset: number; shape: number[] }>;
}

interface FramegenRuntime {
  w: number;
  h: number;
  rt: RT;
  texA: GpuTexture;
  texB: GpuTexture;
  midTexs: GpuTexture[];
  scaleCanvas: OffscreenCanvas | HTMLCanvasElement;
  scaleCtx: OffscreenCanvasRenderingContext2D | CanvasRenderingContext2D;
}

function align16(value: number): number {
  return Math.max(16, Math.floor(value / 16) * 16);
}

function resolveModelSize(
  videoWidth: number,
  videoHeight: number,
  quality: FrameInterpolationSettings["quality"],
): { w: number; h: number } {
  const scale = quality / Math.max(1, videoHeight);
  const h = align16(quality);
  const w = align16(Math.round(videoWidth * scale));
  return { w: Math.max(16, w), h: Math.max(16, h) };
}

function createRgbaTexture(device: GpuDevice, w: number, h: number, storage: boolean): GpuTexture {
  return device.createTexture({
    size: { width: w, height: h },
    format: "rgba8unorm",
    usage:
      GPUTextureUsage.TEXTURE_BINDING
      | GPUTextureUsage.COPY_DST
      | GPUTextureUsage.COPY_SRC
      | (storage ? GPUTextureUsage.STORAGE_BINDING : 0)
      | GPUTextureUsage.RENDER_ATTACHMENT,
  });
}

let weightsPromise: Promise<LoadedWeights> | null = null;

async function fetchWeightsFromBase(base: string): Promise<LoadedWeights> {
  const [binRes, manifestRes] = await Promise.all([
    fetch(`${base}/rt_v7s.bin`),
    fetch(`${base}/rt_v7s.json`),
  ]);
  if (!binRes.ok || !manifestRes.ok) {
    throw new Error(`weights fetch failed (bin=${binRes.status}, manifest=${manifestRes.status}) from ${base}`);
  }
  const bin = await binRes.arrayBuffer();
  const manifest = (await manifestRes.json()) as LoadedWeights["manifest"];
  return { bin, manifest };
}

/** Clear a rejected cache entry so a later toggle/retry can succeed. */
function resetWeightsCache(): void {
  weightsPromise = null;
}

async function loadWeights(): Promise<LoadedWeights> {
  if (!weightsPromise) {
    weightsPromise = (async () => {
      // Vite/electron-vite copies `public/framegen-weights` to the app origin root.
      const baseUrl = import.meta.env.BASE_URL || "/";
      const localBase = `${baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`}framegen-weights`;
      try {
        return await fetchWeightsFromBase(localBase);
      } catch {
        // Fall through to CDN when local assets were not copied at install time.
      }
      return fetchWeightsFromBase(FRAMEGEN_WEIGHTS_CDN);
    })().catch((error) => {
      // Do not sticky-cache a rejected promise across retries.
      weightsPromise = null;
      throw error;
    });
  }
  return weightsPromise;
}

export class FrameInterpolationPipeline {
  private readonly canvas: HTMLCanvasElement;
  private readonly presentCtx: CanvasRenderingContext2D | null;
  private resizeObserver: ResizeObserver | null = null;

  private settings: FrameInterpolationSettings;
  private active = false;
  private disposed = false;
  private initFailed = false;
  private initInFlight: Promise<void> | null = null;
  private frameCallbackId: number | null = null;
  private presentRafId: number | null = null;
  private hasRenderedFrame = false;
  private statusMessage = "";
  private stepInFlight = false;
  /** Bumped when runtime/device ownership changes; async steps abort if stale. */
  private generation = 0;

  private device: GpuDevice | null = null;
  private runtime: FramegenRuntime | null = null;
  private hasPrevFrame = false;
  private presentQueue: Array<{ bitmap: ImageBitmap; holdUntilMs: number }> = [];
  private lastSourceAtMs = 0;
  private sourceIntervalMs = 16.67;

  constructor(
    private readonly videoElement: HTMLVideoElement,
    initialSettings: FrameInterpolationSettings,
  ) {
    this.settings = { ...initialSettings };
    this.canvas = document.createElement("canvas");
    this.canvas.className = "sv-framegen-canvas";
    this.canvas.style.position = "absolute";
    this.canvas.style.inset = "0";
    this.canvas.style.width = "100%";
    this.canvas.style.height = "100%";
    // Above video + WebGL filters (z=5), below cursor overlay (z=200)
    this.canvas.style.zIndex = "6";
    this.canvas.style.pointerEvents = "none";
    this.canvas.style.display = "none";
    videoElement.insertAdjacentElement("afterend", this.canvas);
    this.presentCtx = this.canvas.getContext("2d", { alpha: false });

    if (typeof ResizeObserver !== "undefined") {
      this.resizeObserver = new ResizeObserver(() => this.syncCanvasSize());
      this.resizeObserver.observe(videoElement);
    }

    void this.applyActivation();
  }

  public getCanvas(): HTMLCanvasElement | null {
    return this.active && this.hasRenderedFrame ? this.canvas : null;
  }

  public isActive(): boolean {
    return this.active;
  }

  public getStatusMessage(): string {
    return this.statusMessage;
  }

  public updateSettings(settings: FrameInterpolationSettings): void {
    const prev = this.settings;
    this.settings = { ...settings };
    // Allow retry after a previous hard failure if the user toggles the feature.
    if (settings.enabled && !prev.enabled) {
      this.initFailed = false;
      this.statusMessage = "";
    }
    if (prev.quality !== settings.quality || prev.factor !== settings.factor) {
      this.generation += 1;
      this.destroyRuntime();
    }
    void this.applyActivation();
  }

  public dispose(): void {
    this.disposed = true;
    this.generation += 1;
    this.stopLoops();
    this.destroyRuntime();
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.device?.destroy();
    this.device = null;
    this.canvas.remove();
  }

  private async applyActivation(): Promise<void> {
    const shouldRun =
      !this.disposed
      && !this.initFailed
      && frameInterpolationIsActive(this.settings);

    if (!shouldRun) {
      this.active = false;
      this.stopLoops();
      this.canvas.style.display = "none";
      this.hasRenderedFrame = false;
      this.statusMessage = "";
      return;
    }

    if (!this.device) {
      if (this.initInFlight) {
        await this.initInFlight;
      } else {
        this.initInFlight = this.initWebGpu().finally(() => {
          this.initInFlight = null;
        });
        await this.initInFlight;
      }
    }

    if (this.disposed || this.initFailed || !this.device) {
      this.active = false;
      return;
    }

    this.active = true;
    this.syncCanvasSize();
    this.startLoops();
  }

  private async initWebGpu(): Promise<void> {
    const gpu = navigator.gpu;
    if (!gpu) {
      console.warn("[FrameInterpolation] WebGPU unavailable; pipeline disabled");
      this.statusMessage = "WebGPU unavailable";
      this.initFailed = true;
      return;
    }

    try {
      const adapter = await gpu.requestAdapter({ powerPreference: "high-performance" });
      if (!adapter) {
        throw new Error("No WebGPU adapter");
      }
      const requiredFeatures: GPUFeatureName[] = [];
      if (adapter.features.has("shader-f16")) {
        requiredFeatures.push("shader-f16");
      }
      this.device = await adapter.requestDevice({
        requiredFeatures,
      });
      void this.device.lost.then((info) => {
        console.warn("[FrameInterpolation] GPU device lost:", info.message);
        this.initFailed = true;
        this.device = null;
        this.destroyRuntime();
        void this.applyActivation();
      });
      this.statusMessage = "";
    } catch (error) {
      console.warn(
        "[FrameInterpolation] WebGPU init failed:",
        error instanceof Error ? error.message : String(error),
      );
      this.statusMessage = "WebGPU init failed";
      this.initFailed = true;
      this.device = null;
    }
  }

  private async ensureRuntime(videoWidth: number, videoHeight: number): Promise<FramegenRuntime | null> {
    if (!this.device || this.disposed || this.initFailed) {
      return null;
    }
    const { w, h } = resolveModelSize(videoWidth, videoHeight, this.settings.quality);
    if (this.runtime && this.runtime.w === w && this.runtime.h === h) {
      return this.runtime;
    }

    const gen = this.generation;
    this.destroyRuntime();

    try {
      const weights = await loadWeights();
      if (this.disposed || gen !== this.generation || !this.device) {
        return null;
      }
      const rt = await createRT(this.device, {
        w,
        h,
        weightsBin: weights.bin,
        weightsManifest: weights.manifest,
        textureInput: true,
        textureOutput: true,
      });
      if (this.disposed || gen !== this.generation || !this.device) {
        rt.destroy();
        return null;
      }

      const texA = createRgbaTexture(this.device, w, h, false);
      const texB = createRgbaTexture(this.device, w, h, false);
      const midCount = Math.max(1, this.settings.factor - 1);
      const midTexs = Array.from({ length: midCount }, () =>
        createRgbaTexture(this.device!, w, h, true),
      );

      const scaleCanvas =
        typeof OffscreenCanvas !== "undefined"
          ? new OffscreenCanvas(w, h)
          : Object.assign(document.createElement("canvas"), { width: w, height: h });
      const scaleCtx = scaleCanvas.getContext("2d", { willReadFrequently: false });
      if (!scaleCtx) {
        throw new Error("2D context unavailable for frame scaling");
      }

      if (this.disposed || gen !== this.generation) {
        for (const tex of midTexs) tex.destroy();
        texA.destroy();
        texB.destroy();
        rt.destroy();
        return null;
      }

      this.runtime = { w, h, rt, texA, texB, midTexs, scaleCanvas, scaleCtx };
      this.hasPrevFrame = false;
      this.statusMessage = "";
      return this.runtime;
    } catch (error) {
      console.warn(
        "[FrameInterpolation] Runtime create failed:",
        error instanceof Error ? error.message : String(error),
      );
      this.statusMessage = "Framegen runtime failed";
      this.initFailed = true;
      resetWeightsCache();
      this.destroyRuntime();
      this.stopLoops();
      this.active = false;
      return null;
    }
  }

  private destroyRuntime(): void {
    for (const item of this.presentQueue) {
      item.bitmap.close();
    }
    this.presentQueue = [];
    if (this.runtime) {
      for (const tex of this.runtime.midTexs) {
        tex.destroy();
      }
      this.runtime.texA.destroy();
      this.runtime.texB.destroy();
      this.runtime.rt.destroy();
      this.runtime = null;
    }
    this.hasPrevFrame = false;
  }

  private syncCanvasSize(): void {
    if (!this.active) return;
    const rect = this.videoElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = Math.max(1, Math.round(rect.width * dpr));
    const height = Math.max(1, Math.round(rect.height * dpr));
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width;
      this.canvas.height = height;
    }
  }

  private startLoops(): void {
    this.stopLoops();
    const video = this.videoElement;
    if (typeof video.requestVideoFrameCallback === "function") {
      const onFrame = (): void => {
        if (!this.active || this.disposed) return;
        void this.onSourceFrame();
        this.frameCallbackId = video.requestVideoFrameCallback(onFrame);
      };
      this.frameCallbackId = video.requestVideoFrameCallback(onFrame);
    }
    const onPresent = (): void => {
      if (!this.active || this.disposed) return;
      this.presentQueued();
      this.presentRafId = requestAnimationFrame(onPresent);
    };
    this.presentRafId = requestAnimationFrame(onPresent);
  }

  private stopLoops(): void {
    if (this.frameCallbackId !== null) {
      this.videoElement.cancelVideoFrameCallback?.(this.frameCallbackId);
      this.frameCallbackId = null;
    }
    if (this.presentRafId !== null) {
      cancelAnimationFrame(this.presentRafId);
      this.presentRafId = null;
    }
  }

  private async onSourceFrame(): Promise<void> {
    if (this.stepInFlight || this.initFailed) {
      return;
    }
    const device = this.device;
    const video = this.videoElement;
    const ctx = this.presentCtx;
    if (!device || !ctx || this.disposed || !this.active) return;

    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;
    if (videoWidth === 0 || videoHeight === 0 || video.readyState < 2) {
      return;
    }

    const gen = this.generation;
    this.stepInFlight = true;
    try {
      const now = performance.now();
      if (this.lastSourceAtMs > 0) {
        const delta = now - this.lastSourceAtMs;
        if (delta > 1 && delta < 200) {
          this.sourceIntervalMs = this.sourceIntervalMs * 0.85 + delta * 0.15;
        }
      }
      this.lastSourceAtMs = now;

      const runtime = await this.ensureRuntime(videoWidth, videoHeight);
      if (
        !runtime
        || this.disposed
        || !this.active
        || gen !== this.generation
        || this.runtime !== runtime
        || this.device !== device
      ) {
        return;
      }

      runtime.scaleCanvas.width = runtime.w;
      runtime.scaleCanvas.height = runtime.h;
      runtime.scaleCtx.drawImage(video, 0, 0, runtime.w, runtime.h);

      if (!this.hasPrevFrame) {
        if (gen !== this.generation || this.runtime !== runtime) return;
        device.queue.copyExternalImageToTexture(
          { source: runtime.scaleCanvas },
          { texture: runtime.texA },
          { width: runtime.w, height: runtime.h },
        );
        this.hasPrevFrame = true;
        this.drawVideoLetterboxed(ctx, video);
        return;
      }

      if (gen !== this.generation || this.runtime !== runtime) return;
      device.queue.copyExternalImageToTexture(
        { source: runtime.scaleCanvas },
        { texture: runtime.texB },
        { width: runtime.w, height: runtime.h },
      );

      runtime.rt.prepPair(runtime.texA, runtime.texB);
      const midCount = runtime.midTexs.length;
      for (let i = 0; i < midCount; i++) {
        const t = (i + 1) / (midCount + 1);
        runtime.rt.runT(t, runtime.midTexs[i]!);
      }

      const midBitmaps: ImageBitmap[] = [];
      for (const midTex of runtime.midTexs) {
        if (gen !== this.generation || this.runtime !== runtime) {
          for (const bitmap of midBitmaps) bitmap.close();
          return;
        }
        const bitmap = await this.textureToBitmap(device, midTex, runtime.w, runtime.h);
        if (bitmap) {
          midBitmaps.push(bitmap);
        }
      }

      if (gen !== this.generation || this.runtime !== runtime || this.device !== device) {
        for (const bitmap of midBitmaps) bitmap.close();
        return;
      }

      // Current source becomes the next previous frame.
      device.queue.copyExternalImageToTexture(
        { source: runtime.scaleCanvas },
        { texture: runtime.texA },
        { width: runtime.w, height: runtime.h },
      );

      const slot = Math.max(2, this.sourceIntervalMs / (midCount + 1));
      let hold = now;
      for (const bitmap of midBitmaps) {
        hold += slot;
        this.presentQueue.push({ bitmap, holdUntilMs: hold });
      }
      this.presentQueue.push({
        bitmap: await createImageBitmap(video),
        holdUntilMs: hold + slot,
      });

      if (gen !== this.generation) {
        // Queue was built under a stale generation; drop it.
        for (const item of this.presentQueue) item.bitmap.close();
        this.presentQueue = [];
        return;
      }

      const maxQueued = (midCount + 1) * 2;
      while (this.presentQueue.length > maxQueued) {
        const dropped = this.presentQueue.shift();
        dropped?.bitmap.close();
      }
    } catch (error) {
      if (this.disposed || gen !== this.generation) {
        return;
      }
      console.warn(
        "[FrameInterpolation] Frame step failed:",
        error instanceof Error ? error.message : String(error),
      );
    } finally {
      this.stepInFlight = false;
    }
  }

  private async textureToBitmap(
    device: GpuDevice,
    texture: GpuTexture,
    w: number,
    h: number,
  ): Promise<ImageBitmap | null> {
    try {
      const bytesPerRow = Math.ceil((w * 4) / 256) * 256;
      const buffer = device.createBuffer({
        size: bytesPerRow * h,
        usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ,
      });
      const encoder = device.createCommandEncoder();
      encoder.copyTextureToBuffer(
        { texture },
        { buffer, bytesPerRow },
        { width: w, height: h },
      );
      device.queue.submit([encoder.finish()]);
      await buffer.mapAsync(GPUMapMode.READ);
      const packed = new Uint8ClampedArray(w * h * 4);
      const mapped = new Uint8Array(buffer.getMappedRange());
      for (let y = 0; y < h; y++) {
        packed.set(mapped.subarray(y * bytesPerRow, y * bytesPerRow + w * 4), y * w * 4);
      }
      buffer.unmap();
      buffer.destroy();
      const imageData = new ImageData(packed, w, h);
      return await createImageBitmap(imageData);
    } catch {
      return null;
    }
  }

  private presentQueued(): void {
    const ctx = this.presentCtx;
    if (!ctx || !this.active) return;
    const now = performance.now();

    let readyIndex = -1;
    for (let i = 0; i < this.presentQueue.length; i++) {
      if (this.presentQueue[i]!.holdUntilMs <= now) {
        readyIndex = i;
      } else {
        break;
      }
    }
    if (readyIndex < 0) {
      if (this.presentQueue.length === 0 && this.hasPrevFrame) {
        this.drawVideoLetterboxed(ctx, this.videoElement);
      }
      return;
    }

    for (let i = 0; i < readyIndex; i++) {
      this.presentQueue[i]!.bitmap.close();
    }
    const frame = this.presentQueue[readyIndex]!;
    this.presentQueue = this.presentQueue.slice(readyIndex + 1);
    this.drawBitmapLetterboxed(ctx, frame.bitmap);
    frame.bitmap.close();
  }

  private drawVideoLetterboxed(
    ctx: CanvasRenderingContext2D,
    video: HTMLVideoElement,
  ): void {
    this.syncCanvasSize();
    const canvasWidth = this.canvas.width;
    const canvasHeight = this.canvas.height;
    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;
    if (videoWidth === 0 || videoHeight === 0) return;

    const scale = Math.min(canvasWidth / videoWidth, canvasHeight / videoHeight);
    const drawWidth = Math.round(videoWidth * scale);
    const drawHeight = Math.round(videoHeight * scale);
    const offsetX = Math.floor((canvasWidth - drawWidth) / 2);
    const offsetY = Math.floor((canvasHeight - drawHeight) / 2);

    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, canvasWidth, canvasHeight);
    ctx.drawImage(video, offsetX, offsetY, drawWidth, drawHeight);

    if (!this.hasRenderedFrame) {
      this.hasRenderedFrame = true;
      this.canvas.style.display = "block";
    }
  }

  private drawBitmapLetterboxed(
    ctx: CanvasRenderingContext2D,
    bitmap: ImageBitmap,
  ): void {
    this.syncCanvasSize();
    const canvasWidth = this.canvas.width;
    const canvasHeight = this.canvas.height;
    const scale = Math.min(canvasWidth / bitmap.width, canvasHeight / bitmap.height);
    const drawWidth = Math.round(bitmap.width * scale);
    const drawHeight = Math.round(bitmap.height * scale);
    const offsetX = Math.floor((canvasWidth - drawWidth) / 2);
    const offsetY = Math.floor((canvasHeight - drawHeight) / 2);

    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, canvasWidth, canvasHeight);
    ctx.drawImage(bitmap, offsetX, offsetY, drawWidth, drawHeight);

    if (!this.hasRenderedFrame) {
      this.hasRenderedFrame = true;
      this.canvas.style.display = "block";
    }
  }
}
