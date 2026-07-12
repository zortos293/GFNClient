import { Canvas, useFrame } from "@react-three/fiber";
import { useEffect, useMemo, useRef, useState } from "react";
import type { JSX, RefObject } from "react";
import type { ShaderMaterial } from "three";
import { Vector2 } from "three";

export type ShaderAtmosphereVariant = "controller" | "queue" | "connecting";

export interface ShaderAtmosphereProps {
  variant?: ShaderAtmosphereVariant;
  className?: string;
}

type PointerPosition = { x: number; y: number };

const vertexShader = /* glsl */ `
  varying vec2 vUv;

  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`;

const fragmentShader = /* glsl */ `
  precision highp float;

  uniform float uTime;
  uniform float uEnergy;
  uniform float uDotted;
  uniform vec3 uAccent;
  uniform vec2 uPointer;
  varying vec2 vUv;

  float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
  }

  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
      mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
      mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
      f.y
    );
  }

  float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
      value += amplitude * noise(p);
      p = p * 2.03 + 9.17;
      amplitude *= 0.5;
    }
    return value;
  }

  void main() {
    vec2 uv = vUv;
    vec2 centered = uv - 0.5;
    centered.x *= 1.65;

    if (uDotted > 0.5) {
      vec2 pointerDelta = uv - uPointer;
      pointerDelta.x *= 1.65;
      float pointerDistance = length(pointerDelta);
      float pointerInfluence = exp(-pointerDistance * 7.0);
      vec2 pointerDirection = pointerDelta / max(pointerDistance, 0.001);
      vec2 warpedPixel = gl_FragCoord.xy - pointerDirection * pointerInfluence * 7.0;
      vec2 cell = fract(warpedPixel / 18.0) - 0.5;
      float dotRadius = mix(0.095, 0.19, pointerInfluence);
      float dotShape = 1.0 - smoothstep(dotRadius, dotRadius + 0.05, length(cell));
      float distanceFromCenter = length(centered);
      float signal = 0.55 + 0.45 * sin(distanceFromCenter * 24.0 - uTime * 0.9);
      float focus = smoothstep(1.05, 0.08, distanceFromCenter);
      vec3 dottedBase = vec3(0.032, 0.045, 0.037);
      vec3 dottedColor = dottedBase + uAccent * dotShape * (0.035 + signal * focus * 0.085);
      dottedColor += uAccent * dotShape * pointerInfluence * 0.16;
      gl_FragColor = vec4(dottedColor, 1.0);
      return;
    }

    float time = uTime * 0.045;
    float field = fbm(centered * 1.7 + vec2(time, -time * 0.42));
    float detail = fbm(centered * 3.1 + vec2(-time * 0.36, time * 0.28) + 11.7);
    float cloud = smoothstep(0.3, 0.78, field) * smoothstep(1.2, 0.12, length(centered));
    float veil = smoothstep(0.46, 0.84, detail) * (0.4 + cloud * 0.6);
    float leftPool = pow(max(0.0, 1.0 - length(centered - vec2(-0.48, 0.18))), 3.6);
    float rightPool = pow(max(0.0, 1.0 - length(centered - vec2(0.52, -0.22))), 4.2);
    float vignette = smoothstep(1.05, 0.18, length(centered));
    float grain = (hash(gl_FragCoord.xy + floor(uTime * 8.0)) - 0.5) * 0.028;

    vec3 base = vec3(0.004, 0.008, 0.009);
    vec3 color = base;
    color += uAccent * cloud * (0.055 + uEnergy * 0.075);
    color += mix(uAccent, vec3(0.18, 0.64, 0.72), 0.42) * veil * 0.055;
    color += uAccent * leftPool * 0.07;
    color += mix(uAccent, vec3(0.12, 0.5, 0.44), 0.5) * rightPool * 0.06;
    color *= 0.45 + vignette * 0.72;
    color += grain;

    gl_FragColor = vec4(color, 1.0);
  }
`;

function SignalField({
  variant,
  pointerRef,
}: {
  variant: ShaderAtmosphereVariant;
  pointerRef: RefObject<PointerPosition>;
}): JSX.Element {
  const materialRef = useRef<ShaderMaterial>(null);
  const energy = variant === "controller" ? 0.36 : variant === "queue" ? 0.72 : 1;
  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uEnergy: { value: energy },
      uDotted: { value: variant === "controller" ? 0 : 1 },
      uAccent: { value: [0.18, 0.92, 0.48] },
      uPointer: { value: new Vector2(-2, -2) },
    }),
    [energy],
  );

  useFrame((state, delta) => {
    if (materialRef.current) {
      materialRef.current.uniforms.uTime.value = state.clock.elapsedTime;
      const pointerUniform = materialRef.current.uniforms.uPointer.value as Vector2;
      const smoothing = 1 - Math.exp(-delta * 12);
      pointerUniform.x += (pointerRef.current.x - pointerUniform.x) * smoothing;
      pointerUniform.y += (pointerRef.current.y - pointerUniform.y) * smoothing;
    }
  });

  return (
    <mesh>
      <planeGeometry args={[2, 2]} />
      <shaderMaterial ref={materialRef} uniforms={uniforms} vertexShader={vertexShader} fragmentShader={fragmentShader} />
    </mesh>
  );
}

function supportsWebGl(): boolean {
  try {
    const canvas = document.createElement("canvas");
    return Boolean(canvas.getContext("webgl2") || canvas.getContext("webgl"));
  } catch {
    return false;
  }
}

export function ShaderAtmosphere({
  variant = "controller",
  className,
}: ShaderAtmosphereProps): JSX.Element {
  const [canRender, setCanRender] = useState(false);
  const pointerRef = useRef<PointerPosition>({ x: -2, y: -2 });

  useEffect(() => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    setCanRender(!reducedMotion && supportsWebGl());
  }, []);

  useEffect(() => {
    if (!canRender || variant === "controller") return undefined;

    const handlePointerMove = (event: PointerEvent): void => {
      pointerRef.current.x = event.clientX / Math.max(window.innerWidth, 1);
      pointerRef.current.y = 1 - event.clientY / Math.max(window.innerHeight, 1);
    };
    const clearPointer = (): void => {
      pointerRef.current.x = -2;
      pointerRef.current.y = -2;
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("blur", clearPointer);
    document.documentElement.addEventListener("mouseleave", clearPointer);
    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("blur", clearPointer);
      document.documentElement.removeEventListener("mouseleave", clearPointer);
    };
  }, [canRender, variant]);

  return (
    <div
      className={["shader-atmosphere", `shader-atmosphere--${variant}`, className].filter(Boolean).join(" ")}
      aria-hidden="true"
    >
      {canRender && (
        <Canvas
          dpr={[0.75, 1]}
          camera={{ position: [0, 0, 1] }}
          gl={{ antialias: false, alpha: false, powerPreference: "high-performance" }}
        >
          <SignalField variant={variant} pointerRef={pointerRef} />
        </Canvas>
      )}
    </div>
  );
}
