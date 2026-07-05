import { useEffect, useRef } from "react";
import type { JSX } from "react";
import type { AppAccentColor } from "@shared/gfn";
import { getAccentColorOption } from "../lib/uiCustomization";

export interface ShaderBackgroundProps {
  accentColor: AppAccentColor;
  /** 0..1 — how prominent the shader glow is (login is more vivid than the app shell). */
  intensity?: number;
  className?: string;
}

/** Cap the frame rate: an ambient gradient does not need more than this. */
const TARGET_FPS = 30;
/** Render at a fraction of CSS pixels — the output is a smooth gradient, so upscaling is invisible. */
const RENDER_SCALE = 0.5;
const MAX_DPR = 1.5;

const VERTEX_SHADER_SOURCE = `
attribute vec2 a_position;
void main() {
  gl_Position = vec4(a_position, 0.0, 1.0);
}
`;

/**
 * Domain-warped fbm "aurora" gradient: a dark base tinted by the UI accent in the
 * upper-right and a cool counter-tone in the lower-left (mirroring the old CSS orbs),
 * finished with animated grain to prevent banding.
 */
const FRAGMENT_SHADER_SOURCE = `
precision mediump float;

uniform vec2 u_resolution;
uniform float u_time;
uniform vec3 u_accent;
uniform float u_intensity;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 0.5;
  for (int i = 0; i < 4; i++) {
    value += amplitude * noise(p);
    p = p * 2.02 + vec2(17.3, 9.1);
    amplitude *= 0.5;
  }
  return value;
}

void main() {
  vec2 uv = gl_FragCoord.xy / u_resolution;
  float aspect = u_resolution.x / max(u_resolution.y, 1.0);
  vec2 p = vec2(uv.x * aspect, uv.y) * 1.4;
  float t = u_time * 0.045;

  vec2 q = vec2(fbm(p + t), fbm(p - t * 0.7 + vec2(3.1, 1.3)));
  vec2 r = vec2(
    fbm(p + 2.1 * q + vec2(1.7, 9.2) + t * 0.9),
    fbm(p + 2.1 * q + vec2(8.3, 2.8) - t * 0.6)
  );
  float f = fbm(p + 1.9 * r);

  vec3 base = vec3(0.039, 0.039, 0.047);
  vec3 accent = u_accent;
  vec3 cool = normalize(mix(accent, vec3(0.24, 0.47, 0.78), 0.72) + 0.001) * 0.55;

  // Corner energy wells echo the previous static orb placement.
  float accentWell = smoothstep(1.25, 0.0, distance(uv, vec2(0.86, 0.92)));
  float coolWell = smoothstep(1.35, 0.0, distance(uv, vec2(0.08, 0.06)));
  float midDrift = smoothstep(0.9, 0.0, distance(uv, vec2(0.55 + 0.08 * sin(t * 1.7), 0.45)));

  float flow = smoothstep(0.25, 0.95, f);
  float glow = smoothstep(0.45, 1.0, fbm(p * 0.7 - 1.4 * r + t * 0.5));

  vec3 col = base;
  col += accent * flow * accentWell * 0.42 * u_intensity;
  col += cool * (1.0 - flow) * coolWell * 0.5 * u_intensity;
  col += accent * glow * midDrift * 0.16 * u_intensity;
  col += accent * pow(flow * accentWell, 3.0) * 0.28 * u_intensity;

  // Gentle vignette keeps edges anchored to the app background.
  float vignette = smoothstep(1.35, 0.45, distance(uv, vec2(0.5, 0.5)) * 1.35);
  col = mix(base, col, vignette);

  // Animated grain hides gradient banding on dark tones.
  float grain = (hash(gl_FragCoord.xy + fract(u_time) * 61.7) - 0.5) * 0.014;
  col += grain;

  gl_FragColor = vec4(col, 1.0);
}
`;

interface ShaderProgramHandles {
  program: WebGLProgram;
  resolution: WebGLUniformLocation | null;
  time: WebGLUniformLocation | null;
  accent: WebGLUniformLocation | null;
  intensity: WebGLUniformLocation | null;
}

function compileShader(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
  const shader = gl.createShader(type);
  if (!shader) {
    return null;
  }
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader);
    return null;
  }
  return shader;
}

function createProgram(gl: WebGLRenderingContext): ShaderProgramHandles | null {
  const vertex = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER_SOURCE);
  const fragment = compileShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);
  if (!vertex || !fragment) {
    return null;
  }
  const program = gl.createProgram();
  if (!program) {
    return null;
  }
  gl.attachShader(program, vertex);
  gl.attachShader(program, fragment);
  gl.linkProgram(program);
  gl.deleteShader(vertex);
  gl.deleteShader(fragment);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    gl.deleteProgram(program);
    return null;
  }

  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  gl.useProgram(program);
  const positionLocation = gl.getAttribLocation(program, "a_position");
  gl.enableVertexAttribArray(positionLocation);
  gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

  return {
    program,
    resolution: gl.getUniformLocation(program, "u_resolution"),
    time: gl.getUniformLocation(program, "u_time"),
    accent: gl.getUniformLocation(program, "u_accent"),
    intensity: gl.getUniformLocation(program, "u_intensity"),
  };
}

function accentToUniform(accentColor: AppAccentColor): [number, number, number] {
  const hex = getAccentColorOption(accentColor).hex.replace("#", "");
  return [
    Number.parseInt(hex.slice(0, 2), 16) / 255,
    Number.parseInt(hex.slice(2, 4), 16) / 255,
    Number.parseInt(hex.slice(4, 6), 16) / 255,
  ];
}

/**
 * Full-viewport animated WebGL background. Fails closed: if WebGL is unavailable or the
 * context is lost, the canvas stays transparent and the CSS fallback background shows.
 * Rendering pauses while the document is hidden and freezes under prefers-reduced-motion.
 */
export function ShaderBackground({ accentColor, intensity = 1, className }: ShaderBackgroundProps): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const accentRef = useRef<[number, number, number]>(accentToUniform(accentColor));
  const intensityRef = useRef(intensity);
  const redrawRef = useRef<(() => void) | null>(null);

  accentRef.current = accentToUniform(accentColor);
  intensityRef.current = intensity;

  // Re-render a paused (reduced-motion) frame when the accent changes.
  useEffect(() => {
    redrawRef.current?.();
  }, [accentColor, intensity]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    let gl: WebGLRenderingContext | null = null;
    let handles: ShaderProgramHandles | null = null;
    let rafId = 0;
    let disposed = false;
    let contextLost = false;
    let lastFrameAt = 0;
    let elapsed = 0;
    let lastTickAt = performance.now();

    const reducedMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");

    const initGl = (): boolean => {
      try {
        gl = canvas.getContext("webgl", {
          alpha: false,
          antialias: false,
          depth: false,
          stencil: false,
          powerPreference: "low-power",
        }) as WebGLRenderingContext | null;
      } catch {
        gl = null;
      }
      if (!gl) {
        return false;
      }
      handles = createProgram(gl);
      return handles !== null;
    };

    const resize = (): void => {
      if (!gl) {
        return;
      }
      const dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR);
      const width = Math.max(1, Math.round(canvas.clientWidth * dpr * RENDER_SCALE));
      const height = Math.max(1, Math.round(canvas.clientHeight * dpr * RENDER_SCALE));
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
        gl.viewport(0, 0, width, height);
      }
    };

    const drawFrame = (): void => {
      if (!gl || !handles || contextLost) {
        return;
      }
      resize();
      gl.uniform2f(handles.resolution, canvas.width, canvas.height);
      gl.uniform1f(handles.time, elapsed);
      const [r, g, b] = accentRef.current;
      gl.uniform3f(handles.accent, r, g, b);
      gl.uniform1f(handles.intensity, intensityRef.current);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    };

    const isAnimating = (): boolean => !document.hidden && !reducedMotionQuery.matches;

    const loop = (now: number): void => {
      if (disposed || contextLost) {
        return;
      }
      if (!isAnimating()) {
        rafId = 0;
        return;
      }
      rafId = requestAnimationFrame(loop);
      if (now - lastFrameAt < 1000 / TARGET_FPS) {
        return;
      }
      lastFrameAt = now;
      elapsed += (now - lastTickAt) / 1000;
      lastTickAt = now;
      drawFrame();
    };

    const startLoop = (): void => {
      if (disposed || rafId !== 0) {
        return;
      }
      lastTickAt = performance.now();
      lastFrameAt = 0;
      if (isAnimating()) {
        rafId = requestAnimationFrame(loop);
      } else {
        // Reduced motion: paint one static frame so the scene still has depth.
        drawFrame();
      }
    };

    const stopLoop = (): void => {
      if (rafId !== 0) {
        cancelAnimationFrame(rafId);
        rafId = 0;
      }
    };

    const handleVisibility = (): void => {
      if (document.hidden) {
        stopLoop();
      } else {
        startLoop();
      }
    };

    const handleMotionPreference = (): void => {
      stopLoop();
      startLoop();
    };

    const handleContextLost = (event: Event): void => {
      event.preventDefault();
      contextLost = true;
      stopLoop();
    };

    const handleContextRestored = (): void => {
      contextLost = false;
      if (initGl()) {
        startLoop();
      }
    };

    canvas.addEventListener("webglcontextlost", handleContextLost);
    canvas.addEventListener("webglcontextrestored", handleContextRestored);

    if (!initGl()) {
      canvas.removeEventListener("webglcontextlost", handleContextLost);
      canvas.removeEventListener("webglcontextrestored", handleContextRestored);
      return;
    }

    redrawRef.current = () => {
      if (!isAnimating()) {
        drawFrame();
      }
    };

    document.addEventListener("visibilitychange", handleVisibility);
    reducedMotionQuery.addEventListener("change", handleMotionPreference);
    window.addEventListener("resize", handleVisibility);
    startLoop();

    return () => {
      disposed = true;
      stopLoop();
      redrawRef.current = null;
      document.removeEventListener("visibilitychange", handleVisibility);
      reducedMotionQuery.removeEventListener("change", handleMotionPreference);
      window.removeEventListener("resize", handleVisibility);
      canvas.removeEventListener("webglcontextlost", handleContextLost);
      canvas.removeEventListener("webglcontextrestored", handleContextRestored);
      if (gl && handles) {
        gl.deleteProgram(handles.program);
      }
      const loseContext = gl?.getExtension("WEBGL_lose_context");
      loseContext?.loseContext();
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className={`shader-bg${className ? ` ${className}` : ""}`}
      aria-hidden="true"
    />
  );
}
