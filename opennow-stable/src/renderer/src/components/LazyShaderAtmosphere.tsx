import { lazy, Suspense } from "react";
import type { JSX } from "react";
import type { ShaderAtmosphereProps } from "./ShaderAtmosphere";

const ShaderAtmosphereCanvas = lazy(async () => {
  const module = await import("./ShaderAtmosphere");
  return { default: module.ShaderAtmosphere };
});

export function LazyShaderAtmosphere({
  variant = "controller",
  className,
}: ShaderAtmosphereProps): JSX.Element {
  const fallbackClassName = [
    "shader-atmosphere",
    `shader-atmosphere--${variant}`,
    className,
  ].filter(Boolean).join(" ");

  return (
    <Suspense fallback={<div className={fallbackClassName} aria-hidden="true" />}>
      <ShaderAtmosphereCanvas variant={variant} className={className} />
    </Suspense>
  );
}
