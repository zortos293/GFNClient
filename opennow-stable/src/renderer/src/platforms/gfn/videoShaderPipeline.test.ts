/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { VideoShaderPipeline } from "./videoShaderPipeline";

class FakeWebGlContext {
  readonly VERTEX_SHADER = 1;
  readonly FRAGMENT_SHADER = 2;
  readonly COMPILE_STATUS = 3;
  readonly LINK_STATUS = 4;
  readonly TEXTURE_2D = 5;
  readonly TEXTURE_MIN_FILTER = 6;
  readonly TEXTURE_MAG_FILTER = 7;
  readonly LINEAR = 8;
  readonly TEXTURE_WRAP_S = 9;
  readonly TEXTURE_WRAP_T = 10;
  readonly CLAMP_TO_EDGE = 11;
  readonly TEXTURE0 = 12;
  readonly RGBA = 13;
  readonly UNSIGNED_BYTE = 14;
  readonly SCISSOR_TEST = 15;
  readonly COLOR_BUFFER_BIT = 16;
  readonly TRIANGLES = 17;

  lost = false;
  programCreations = 0;
  textureCreations = 0;
  drawCalls = 0;

  createShader(): WebGLShader {
    return {} as WebGLShader;
  }

  shaderSource(): void {}
  compileShader(): void {}
  getShaderParameter(): boolean {
    return true;
  }
  getShaderInfoLog(): string {
    return "";
  }
  deleteShader(): void {}

  createProgram(): WebGLProgram {
    this.programCreations++;
    return {} as WebGLProgram;
  }

  attachShader(): void {}
  linkProgram(): void {}
  getProgramParameter(): boolean {
    return true;
  }
  getProgramInfoLog(): string {
    return "";
  }
  deleteProgram(): void {}

  createTexture(): WebGLTexture {
    this.textureCreations++;
    return {} as WebGLTexture;
  }

  bindTexture(): void {}
  texParameteri(): void {}
  useProgram(): void {}
  getUniformLocation(): WebGLUniformLocation {
    return {} as WebGLUniformLocation;
  }
  uniform1i(): void {}
  activeTexture(): void {}
  texImage2D(): void {}
  viewport(): void {}
  disable(): void {}
  clearColor(): void {}
  clear(): void {}
  uniform2f(): void {}
  uniform1f(): void {}

  drawArrays(): void {
    this.drawCalls++;
  }

  deleteTexture(): void {}
  getExtension(): null {
    return null;
  }
  isContextLost(): boolean {
    return this.lost;
  }
}

class FakeCanvas {
  readonly style: Record<string, string> = {};
  className = "";
  width = 0;
  height = 0;
  removed = false;
  private readonly listeners = new Map<string, Set<(event: Event) => void>>();

  constructor(private readonly gl: FakeWebGlContext) {}

  getContext(): WebGL2RenderingContext {
    return this.gl as unknown as WebGL2RenderingContext;
  }

  addEventListener(type: string, listener: (event: Event) => void): void {
    const listeners = this.listeners.get(type) ?? new Set();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type: string, listener: (event: Event) => void): void {
    this.listeners.get(type)?.delete(listener);
  }

  dispatch(type: string): Event {
    const event = new Event(type, { cancelable: true });
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event);
    }
    return event;
  }

  remove(): void {
    this.removed = true;
  }
}

class FakeVideo {
  videoWidth = 1920;
  videoHeight = 1080;
  readyState = 4;
  insertedCanvas: FakeCanvas | null = null;
  requestCount = 0;
  private nextCallbackId = 1;
  private readonly callbacks = new Map<number, VideoFrameRequestCallback>();

  insertAdjacentElement(_position: string, canvas: Element): void {
    this.insertedCanvas = canvas as unknown as FakeCanvas;
  }

  getBoundingClientRect(): DOMRect {
    return { width: 1280, height: 720 } as DOMRect;
  }

  requestVideoFrameCallback(callback: VideoFrameRequestCallback): number {
    const id = this.nextCallbackId++;
    this.requestCount++;
    this.callbacks.set(id, callback);
    return id;
  }

  cancelVideoFrameCallback(): void {}

  invokeCallback(id: number): void {
    this.callbacks.get(id)?.(0, {} as VideoFrameCallbackMetadata);
  }
}

function replaceGlobal(name: "document" | "window" | "ResizeObserver", value: unknown): () => void {
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

test("shader context loss immediately reveals video and restores with a fresh render loop", () => {
  const gl = new FakeWebGlContext();
  const canvas = new FakeCanvas(gl);
  const video = new FakeVideo();
  const restoreGlobals = [
    replaceGlobal("document", {
      createElement: () => canvas,
    }),
    replaceGlobal("window", { devicePixelRatio: 1 }),
    replaceGlobal("ResizeObserver", undefined),
  ];

  try {
    const pipeline = new VideoShaderPipeline(video as unknown as HTMLVideoElement, {
      enabled: true,
      sharpen: 40,
      saturation: 100,
      contrast: 100,
      brightness: 100,
      vibrance: 0,
      filmGrain: 0,
    });

    assert.equal(canvas.style.display, "none");
    assert.equal(video.requestCount, 1);
    video.invokeCallback(1);
    assert.equal(canvas.style.display, "block");
    assert.equal(pipeline.getCanvas(), canvas as unknown as HTMLCanvasElement);
    assert.equal(gl.drawCalls, 1);

    gl.lost = true;
    const lostEvent = canvas.dispatch("webglcontextlost");
    assert.equal(lostEvent.defaultPrevented, true);
    assert.equal(canvas.style.display, "none");
    assert.equal(pipeline.getCanvas(), null);
    assert.equal(pipeline.isActive(), false);

    gl.lost = false;
    canvas.dispatch("webglcontextrestored");
    assert.equal(gl.programCreations, 2);
    assert.equal(gl.textureCreations, 2);
    assert.equal(video.requestCount, 3);
    assert.equal(canvas.style.display, "none");

    video.invokeCallback(2);
    assert.equal(video.requestCount, 3);
    assert.equal(canvas.style.display, "none");

    video.invokeCallback(3);
    assert.equal(gl.drawCalls, 2);
    assert.equal(video.requestCount, 4);
    assert.equal(canvas.style.display, "block");

    pipeline.dispose();
    canvas.dispatch("webglcontextlost");
    canvas.dispatch("webglcontextrestored");
    assert.equal(canvas.removed, true);
    assert.equal(gl.programCreations, 2);
    assert.equal(video.requestCount, 4);
  } finally {
    for (const restore of restoreGlobals.reverse()) {
      restore();
    }
  }
});
