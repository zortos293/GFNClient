import { createRT, type RT } from "framegen";
import {
  FRAMEGEN_WEIGHTS_BIN_URL,
  FRAMEGEN_WEIGHTS_MANIFEST_URL,
} from "./frameGenerationAssets";
import type {
  FrameGenerationBackend,
  FrameGenerationDimensions,
  FrameGenerationFrame,
} from "./frameGenerationPipeline";
import { FRAME_GENERATION_TEXTURE_POOL_SIZE } from "./frameGenerationCapacity";

const FRAME_TEXTURE_FORMAT: GPUTextureFormat = "rgba8unorm";

const CAPTURE_SHADER = `
struct VertexOutput {
  @builtin(position) position: vec4f,
  @location(0) uv: vec2f,
}

@vertex
fn vertexMain(@builtin(vertex_index) index: u32) -> VertexOutput {
  var positions = array<vec2f, 3>(
    vec2f(-1.0, -1.0),
    vec2f(3.0, -1.0),
    vec2f(-1.0, 3.0),
  );
  let position = positions[index];
  var output: VertexOutput;
  output.position = vec4f(position, 0.0, 1.0);
  output.uv = vec2f((position.x + 1.0) * 0.5, (1.0 - position.y) * 0.5);
  return output;
}

@group(0) @binding(0) var source: texture_external;
@group(0) @binding(1) var sourceSampler: sampler;

@fragment
fn fragmentMain(input: VertexOutput) -> @location(0) vec4f {
  return textureSampleBaseClampToEdge(source, sourceSampler, input.uv);
}
`;

const PRESENT_SHADER = `
struct VertexOutput {
  @builtin(position) position: vec4f,
  @location(0) uv: vec2f,
}

@vertex
fn vertexMain(@builtin(vertex_index) index: u32) -> VertexOutput {
  var positions = array<vec2f, 3>(
    vec2f(-1.0, -1.0),
    vec2f(3.0, -1.0),
    vec2f(-1.0, 3.0),
  );
  let position = positions[index];
  var output: VertexOutput;
  output.position = vec4f(position, 0.0, 1.0);
  output.uv = vec2f((position.x + 1.0) * 0.5, (1.0 - position.y) * 0.5);
  return output;
}

@group(0) @binding(0) var source: texture_2d<f32>;
@group(0) @binding(1) var sourceSampler: sampler;

@fragment
fn fragmentMain(input: VertexOutput) -> @location(0) vec4f {
  return textureSample(source, sourceSampler, input.uv);
}
`;

interface TextureSlot {
  texture: GPUTexture;
  references: number;
  pendingSubmissions: number;
}

class WebGpuFrameHandle implements FrameGenerationFrame {
  private released = false;

  constructor(
    readonly owner: WebGpuFrameGenerationBackend,
    readonly slot: TextureSlot,
  ) {
    slot.references++;
  }

  retain(): FrameGenerationFrame {
    if (this.released) {
      throw new Error("Cannot retain a released frame");
    }
    return new WebGpuFrameHandle(this.owner, this.slot);
  }

  release(): void {
    if (this.released) return;
    this.released = true;
    this.slot.references--;
  }
}

class WebGpuFrameGenerationBackend implements FrameGenerationBackend {
  private readonly sampler: GPUSampler;
  private readonly capturePipeline: GPURenderPipeline;
  private readonly presentPipeline: GPURenderPipeline;
  private readonly slots: TextureSlot[];
  private readonly deviceLostCallbacks = new Set<() => void>();
  private disposed = false;

  constructor(
    private readonly device: GPUDevice,
    private readonly context: GPUCanvasContext,
    private readonly runtime: RT,
    private readonly dimensions: FrameGenerationDimensions,
    canvasFormat: GPUTextureFormat,
  ) {
    this.sampler = device.createSampler({
      magFilter: "linear",
      minFilter: "linear",
      addressModeU: "clamp-to-edge",
      addressModeV: "clamp-to-edge",
    });
    this.capturePipeline = device.createRenderPipeline({
      layout: "auto",
      vertex: {
        module: device.createShaderModule({ code: CAPTURE_SHADER }),
        entryPoint: "vertexMain",
      },
      fragment: {
        module: device.createShaderModule({ code: CAPTURE_SHADER }),
        entryPoint: "fragmentMain",
        targets: [{ format: FRAME_TEXTURE_FORMAT }],
      },
      primitive: { topology: "triangle-list" },
    });
    this.presentPipeline = device.createRenderPipeline({
      layout: "auto",
      vertex: {
        module: device.createShaderModule({ code: PRESENT_SHADER }),
        entryPoint: "vertexMain",
      },
      fragment: {
        module: device.createShaderModule({ code: PRESENT_SHADER }),
        entryPoint: "fragmentMain",
        targets: [{ format: canvasFormat }],
      },
      primitive: { topology: "triangle-list" },
    });
    this.slots = Array.from({ length: FRAME_GENERATION_TEXTURE_POOL_SIZE }, () => ({
      texture: device.createTexture({
        size: dimensions,
        format: FRAME_TEXTURE_FORMAT,
        usage:
          GPUTextureUsage.RENDER_ATTACHMENT
          | GPUTextureUsage.TEXTURE_BINDING
          | GPUTextureUsage.STORAGE_BINDING
          | GPUTextureUsage.COPY_SRC,
      }),
      references: 0,
      pendingSubmissions: 0,
    }));

    void device.lost.then(() => {
      if (this.disposed) return;
      for (const callback of this.deviceLostCallbacks) callback();
    });
  }

  capture(video: HTMLVideoElement): FrameGenerationFrame {
    this.assertUsable();
    const frame = this.acquireFrame();
    try {
      const encoder = this.device.createCommandEncoder();
      const pass = encoder.beginRenderPass({
        colorAttachments: [{
          view: frame.slot.texture.createView(),
          loadOp: "clear",
          storeOp: "store",
          clearValue: { r: 0, g: 0, b: 0, a: 1 },
        }],
      });
      const externalTexture = this.device.importExternalTexture({ source: video });
      pass.setPipeline(this.capturePipeline);
      pass.setBindGroup(0, this.device.createBindGroup({
        layout: this.capturePipeline.getBindGroupLayout(0),
        entries: [
          { binding: 0, resource: externalTexture },
          { binding: 1, resource: this.sampler },
        ],
      }));
      pass.draw(3);
      pass.end();
      this.device.queue.submit([encoder.finish()]);
      this.markSubmitted(frame.slot);
      return frame;
    } catch (error) {
      frame.release();
      throw error;
    }
  }

  interpolate(
    previous: FrameGenerationFrame,
    current: FrameGenerationFrame,
  ): FrameGenerationFrame {
    this.assertUsable();
    const previousFrame = this.unwrap(previous);
    const currentFrame = this.unwrap(current);
    const midpoint = this.acquireFrame();
    try {
      this.runtime.prepPair(previousFrame.slot.texture, currentFrame.slot.texture);
      this.runtime.runT(0.5, midpoint.slot.texture);
      this.markSubmitted(previousFrame.slot, currentFrame.slot, midpoint.slot);
      return midpoint;
    } catch (error) {
      midpoint.release();
      throw error;
    }
  }

  present(frame: FrameGenerationFrame): boolean {
    this.assertUsable();
    const source = this.unwrap(frame);
    const encoder = this.device.createCommandEncoder();
    const pass = encoder.beginRenderPass({
      colorAttachments: [{
        view: this.context.getCurrentTexture().createView(),
        loadOp: "clear",
        storeOp: "store",
        clearValue: { r: 0, g: 0, b: 0, a: 1 },
      }],
    });
    pass.setPipeline(this.presentPipeline);
    pass.setBindGroup(0, this.device.createBindGroup({
      layout: this.presentPipeline.getBindGroupLayout(0),
      entries: [
        { binding: 0, resource: source.slot.texture.createView() },
        { binding: 1, resource: this.sampler },
      ],
    }));
    pass.draw(3);
    pass.end();
    this.device.queue.submit([encoder.finish()]);
    this.markSubmitted(source.slot);
    return true;
  }

  onDeviceLost(callback: () => void): () => void {
    this.deviceLostCallbacks.add(callback);
    return () => this.deviceLostCallbacks.delete(callback);
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.deviceLostCallbacks.clear();
    this.runtime.destroy();
    for (const slot of this.slots) slot.texture.destroy();
    this.context.unconfigure();
    this.device.destroy();
  }

  private acquireFrame(): WebGpuFrameHandle {
    const slot = this.slots.find((candidate) => (
      candidate.references === 0 && candidate.pendingSubmissions === 0
    ));
    if (!slot) {
      throw new Error("Frame texture pool exhausted");
    }
    return new WebGpuFrameHandle(this, slot);
  }

  private unwrap(frame: FrameGenerationFrame): WebGpuFrameHandle {
    if (!(frame instanceof WebGpuFrameHandle) || frame.owner !== this) {
      throw new Error("Frame belongs to a different backend");
    }
    return frame;
  }

  private markSubmitted(...slots: TextureSlot[]): void {
    const uniqueSlots = [...new Set(slots)];
    for (const slot of uniqueSlots) slot.pendingSubmissions++;
    void this.device.queue.onSubmittedWorkDone().then(
      () => {
        for (const slot of uniqueSlots) slot.pendingSubmissions--;
      },
      () => {
        for (const callback of this.deviceLostCallbacks) callback();
      },
    );
  }

  private assertUsable(): void {
    if (this.disposed) {
      throw new Error("Frame generation backend is disposed");
    }
  }
}

async function fetchWeights(): Promise<{
  bin: ArrayBuffer;
  manifest: Record<string, { offset: number; shape: number[] }>;
}> {
  const [binResponse, manifestResponse] = await Promise.all([
    fetch(FRAMEGEN_WEIGHTS_BIN_URL),
    fetch(FRAMEGEN_WEIGHTS_MANIFEST_URL),
  ]);
  if (!binResponse.ok || !manifestResponse.ok) {
    throw new Error("Bundled frame generation weights are unavailable");
  }

  const [bin, manifest] = await Promise.all([
    binResponse.arrayBuffer(),
    manifestResponse.json(),
  ]);
  if (bin.byteLength === 0 || typeof manifest !== "object" || manifest === null) {
    throw new Error("Bundled frame generation weights are invalid");
  }
  return {
    bin,
    manifest: manifest as Record<string, { offset: number; shape: number[] }>,
  };
}

export async function createWebGpuFrameGenerationBackend(
  canvas: HTMLCanvasElement,
  dimensions: FrameGenerationDimensions,
): Promise<FrameGenerationBackend> {
  if (!navigator.gpu) {
    throw new Error("WebGPU is unavailable");
  }
  const adapter = await navigator.gpu.requestAdapter({ powerPreference: "high-performance" });
  if (!adapter || !adapter.features.has("shader-f16")) {
    throw new Error("A high-performance WebGPU adapter with shader-f16 is required");
  }

  const device = await adapter.requestDevice({ requiredFeatures: ["shader-f16"] });
  const context = canvas.getContext("webgpu");
  if (!context) {
    device.destroy();
    throw new Error("Could not create a WebGPU canvas context");
  }

  let runtime: RT | null = null;
  try {
    const weights = await fetchWeights();
    runtime = await createRT(device, {
      w: dimensions.width,
      h: dimensions.height,
      weightsBin: weights.bin,
      weightsManifest: weights.manifest,
      textureInput: true,
      textureOutput: true,
    });
    const canvasFormat = navigator.gpu.getPreferredCanvasFormat();
    context.configure({
      device,
      format: canvasFormat,
      alphaMode: "opaque",
    });
    return new WebGpuFrameGenerationBackend(
      device,
      context,
      runtime,
      dimensions,
      canvasFormat,
    );
  } catch (error) {
    runtime?.destroy();
    context.unconfigure();
    device.destroy();
    throw error;
  }
}
