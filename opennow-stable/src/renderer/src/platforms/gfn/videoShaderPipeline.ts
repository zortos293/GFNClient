import type { VideoShaderSettings } from "@shared/gfn";
import { videoShaderHasVisibleEffect } from "@shared/gfn";

/**
 * WebGL2 post-processing pipeline for the embedded WebRTC stream.
 *
 * Renders the decoded <video> frames into an overlay canvas with a single-pass
 * fragment shader providing contrast-adaptive sharpening (CAS-style), color
 * grading (brightness/contrast/saturation/vibrance), and optional film grain.
 *
 * The overlay reproduces `object-fit: contain` letterboxing so it can sit
 * directly on top of the video element. When the pipeline is inactive the
 * canvas is hidden and the raw video shows through unchanged.
 */

const VERTEX_SHADER = `#version 300 es
precision highp float;
out vec2 vUv;
void main() {
  // Fullscreen triangle
  vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
  vUv = vec2(pos.x, 1.0 - pos.y);
  gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}`;

const FRAGMENT_SHADER = `#version 300 es
precision highp float;

uniform sampler2D uFrame;
uniform vec2 uTexelSize;
uniform float uSharpen;    // 0..1
uniform float uSaturation; // 0..2 (1 = neutral)
uniform float uContrast;   // 0.5..1.5 (1 = neutral)
uniform float uBrightness; // 0.5..1.5 (1 = neutral)
uniform float uVibrance;   // 0..1
uniform float uGrain;      // 0..1
uniform float uTime;       // seconds, for animated grain

in vec2 vUv;
out vec4 outColor;

float luma(vec3 c) {
  return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Cheap hash for film grain
float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

// Contrast-adaptive sharpening (simplified AMD FidelityFX CAS).
vec3 casSharpen(vec2 uv, vec3 center, float amount) {
  vec3 n = texture(uFrame, uv + vec2(0.0, -uTexelSize.y)).rgb;
  vec3 s = texture(uFrame, uv + vec2(0.0,  uTexelSize.y)).rgb;
  vec3 w = texture(uFrame, uv + vec2(-uTexelSize.x, 0.0)).rgb;
  vec3 e = texture(uFrame, uv + vec2( uTexelSize.x, 0.0)).rgb;

  vec3 mn = min(center, min(min(n, s), min(w, e)));
  vec3 mx = max(center, max(max(n, s), max(w, e)));

  // Adaptive weight: sharpen less where local contrast is already high
  vec3 amp = clamp(min(mn, 1.0 - mx) / max(mx, vec3(1e-5)), 0.0, 1.0);
  amp = sqrt(amp);

  float peak = mix(-0.125, -0.2, amount);
  vec3 weight = amp * peak;

  vec3 result = (center + (n + s + w + e) * weight) / (1.0 + 4.0 * weight);
  return clamp(result, mn, mx);
}

void main() {
  vec3 color = texture(uFrame, vUv).rgb;

  if (uSharpen > 0.001) {
    color = casSharpen(vUv, color, uSharpen);
  }

  // Brightness (linear gain)
  color *= uBrightness;

  // Contrast around mid-gray
  color = (color - 0.5) * uContrast + 0.5;

  // Saturation
  float l = luma(color);
  color = mix(vec3(l), color, uSaturation);

  // Vibrance: boost saturation more on muted colors, protect skin-ish tones
  if (uVibrance > 0.001) {
    float maxC = max(color.r, max(color.g, color.b));
    float minC = min(color.r, min(color.g, color.b));
    float sat = maxC - minC;
    float boost = uVibrance * (1.0 - sat);
    color = mix(vec3(luma(color)), color, 1.0 + boost);
  }

  // Animated film grain
  if (uGrain > 0.001) {
    float g = hash(gl_FragCoord.xy + fract(uTime) * 1024.0) - 0.5;
    // Scale grain by luminance so shadows don't get crushed to noise
    color += g * uGrain * 0.12 * (0.3 + 0.7 * luma(color));
  }

  outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}`;

interface ShaderUniforms {
  frame: WebGLUniformLocation | null;
  texelSize: WebGLUniformLocation | null;
  sharpen: WebGLUniformLocation | null;
  saturation: WebGLUniformLocation | null;
  contrast: WebGLUniformLocation | null;
  brightness: WebGLUniformLocation | null;
  vibrance: WebGLUniformLocation | null;
  grain: WebGLUniformLocation | null;
  time: WebGLUniformLocation | null;
}

export class VideoShaderPipeline {
  private readonly canvas: HTMLCanvasElement;
  private gl: WebGL2RenderingContext | null = null;
  private program: WebGLProgram | null = null;
  private texture: WebGLTexture | null = null;
  private uniforms: ShaderUniforms | null = null;
  private resizeObserver: ResizeObserver | null = null;

  private settings: VideoShaderSettings;
  private active = false;
  private disposed = false;
  private contextFailed = false;
  private frameCallbackId: number | null = null;
  private rafId: number | null = null;
  private hasRenderedFrame = false;
  private readonly startTimeMs = performance.now();

  constructor(
    private readonly videoElement: HTMLVideoElement,
    initialSettings: VideoShaderSettings,
  ) {
    this.settings = { ...initialSettings };
    this.canvas = document.createElement("canvas");
    this.canvas.className = "sv-shader-canvas";
    this.canvas.style.position = "absolute";
    this.canvas.style.inset = "0";
    this.canvas.style.width = "100%";
    this.canvas.style.height = "100%";
    // Above the video (z-index 1) but below the cursor overlay (z-index 200)
    this.canvas.style.zIndex = "5";
    this.canvas.style.pointerEvents = "none";
    this.canvas.style.display = "none";
    videoElement.insertAdjacentElement("afterend", this.canvas);

    if (typeof ResizeObserver !== "undefined") {
      this.resizeObserver = new ResizeObserver(() => this.syncCanvasSize());
      this.resizeObserver.observe(videoElement);
    }

    this.applyActivation();
  }

  /** The overlay canvas, exposed for screenshot/recording composition. */
  public getCanvas(): HTMLCanvasElement | null {
    return this.active && this.hasRenderedFrame ? this.canvas : null;
  }

  public isActive(): boolean {
    return this.active;
  }

  public updateSettings(settings: VideoShaderSettings): void {
    this.settings = { ...settings };
    this.applyActivation();
  }

  public dispose(): void {
    this.disposed = true;
    this.stopRenderLoop();
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    if (this.gl) {
      if (this.texture) this.gl.deleteTexture(this.texture);
      if (this.program) this.gl.deleteProgram(this.program);
      this.gl.getExtension("WEBGL_lose_context")?.loseContext();
    }
    this.gl = null;
    this.program = null;
    this.texture = null;
    this.canvas.remove();
  }

  private applyActivation(): void {
    const shouldRun = !this.disposed && !this.contextFailed && videoShaderHasVisibleEffect(this.settings);
    if (shouldRun === this.active) {
      return;
    }
    this.active = shouldRun;
    if (shouldRun) {
      if (!this.gl && !this.initGl()) {
        this.active = false;
        return;
      }
      this.hasRenderedFrame = false;
      this.syncCanvasSize();
      this.startRenderLoop();
    } else {
      this.stopRenderLoop();
      this.canvas.style.display = "none";
      this.hasRenderedFrame = false;
    }
  }

  private initGl(): boolean {
    const gl = this.canvas.getContext("webgl2", {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      desynchronized: true,
      powerPreference: "high-performance",
    });
    if (!gl) {
      console.warn("[VideoShader] WebGL2 unavailable; shader pipeline disabled");
      this.contextFailed = true;
      return false;
    }

    const compile = (type: number, source: string): WebGLShader | null => {
      const shader = gl.createShader(type);
      if (!shader) return null;
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        console.error("[VideoShader] Shader compile failed:", gl.getShaderInfoLog(shader));
        gl.deleteShader(shader);
        return null;
      }
      return shader;
    };

    const vs = compile(gl.VERTEX_SHADER, VERTEX_SHADER);
    const fs = compile(gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) {
      this.contextFailed = true;
      return false;
    }

    const program = gl.createProgram();
    if (!program) {
      this.contextFailed = true;
      return false;
    }
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      console.error("[VideoShader] Program link failed:", gl.getProgramInfoLog(program));
      gl.deleteProgram(program);
      this.contextFailed = true;
      return false;
    }

    const texture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);

    gl.useProgram(program);
    this.uniforms = {
      frame: gl.getUniformLocation(program, "uFrame"),
      texelSize: gl.getUniformLocation(program, "uTexelSize"),
      sharpen: gl.getUniformLocation(program, "uSharpen"),
      saturation: gl.getUniformLocation(program, "uSaturation"),
      contrast: gl.getUniformLocation(program, "uContrast"),
      brightness: gl.getUniformLocation(program, "uBrightness"),
      vibrance: gl.getUniformLocation(program, "uVibrance"),
      grain: gl.getUniformLocation(program, "uGrain"),
      time: gl.getUniformLocation(program, "uTime"),
    };
    gl.uniform1i(this.uniforms.frame, 0);

    this.canvas.addEventListener("webglcontextlost", this.onContextLost);

    this.gl = gl;
    this.program = program;
    this.texture = texture;
    return true;
  }

  private readonly onContextLost = (event: Event): void => {
    event.preventDefault();
    console.warn("[VideoShader] WebGL context lost; shader pipeline disabled");
    this.contextFailed = true;
    this.gl = null;
    this.program = null;
    this.texture = null;
    this.applyActivation();
  };

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

  private startRenderLoop(): void {
    this.stopRenderLoop();
    const video = this.videoElement;
    if (typeof video.requestVideoFrameCallback === "function") {
      const onFrame = (): void => {
        if (!this.active || this.disposed) return;
        this.renderFrame();
        this.frameCallbackId = video.requestVideoFrameCallback(onFrame);
      };
      this.frameCallbackId = video.requestVideoFrameCallback(onFrame);
    } else {
      const onRaf = (): void => {
        if (!this.active || this.disposed) return;
        this.renderFrame();
        this.rafId = requestAnimationFrame(onRaf);
      };
      this.rafId = requestAnimationFrame(onRaf);
    }
  }

  private stopRenderLoop(): void {
    if (this.frameCallbackId !== null) {
      this.videoElement.cancelVideoFrameCallback?.(this.frameCallbackId);
      this.frameCallbackId = null;
    }
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  private renderFrame(): void {
    const gl = this.gl;
    const video = this.videoElement;
    if (!gl || !this.program || !this.texture || !this.uniforms) return;

    const videoWidth = video.videoWidth;
    const videoHeight = video.videoHeight;
    if (videoWidth === 0 || videoHeight === 0 || video.readyState < 2) {
      return;
    }

    this.syncCanvasSize();

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.texture);
    try {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
    } catch {
      // Frame not ready or cross-origin issue; skip this frame
      return;
    }

    const canvasWidth = this.canvas.width;
    const canvasHeight = this.canvas.height;

    // Reproduce object-fit: contain letterboxing
    const scale = Math.min(canvasWidth / videoWidth, canvasHeight / videoHeight);
    const drawWidth = Math.round(videoWidth * scale);
    const drawHeight = Math.round(videoHeight * scale);
    const offsetX = Math.floor((canvasWidth - drawWidth) / 2);
    const offsetY = Math.floor((canvasHeight - drawHeight) / 2);

    gl.viewport(0, 0, canvasWidth, canvasHeight);
    gl.disable(gl.SCISSOR_TEST);
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT);

    gl.viewport(offsetX, offsetY, drawWidth, drawHeight);
    gl.useProgram(this.program);

    const s = this.settings;
    gl.uniform2f(this.uniforms.texelSize, 1 / videoWidth, 1 / videoHeight);
    gl.uniform1f(this.uniforms.sharpen, s.sharpen / 100);
    gl.uniform1f(this.uniforms.saturation, s.saturation / 100);
    gl.uniform1f(this.uniforms.contrast, s.contrast / 100);
    gl.uniform1f(this.uniforms.brightness, s.brightness / 100);
    gl.uniform1f(this.uniforms.vibrance, s.vibrance / 100);
    gl.uniform1f(this.uniforms.grain, s.filmGrain / 100);
    gl.uniform1f(this.uniforms.time, (performance.now() - this.startTimeMs) / 1000);

    gl.drawArrays(gl.TRIANGLES, 0, 3);

    if (!this.hasRenderedFrame) {
      this.hasRenderedFrame = true;
      this.canvas.style.display = "block";
    }
  }
}
