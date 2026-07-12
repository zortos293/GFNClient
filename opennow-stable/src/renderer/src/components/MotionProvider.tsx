import type { JSX, PropsWithChildren } from "react";
import { LazyMotion, MotionConfig, domAnimation } from "motion/react";

export const smoothEase = [0.16, 1, 0.3, 1] as const;
export const standardEase = [0.4, 0, 0.2, 1] as const;
export const exitEase = [0.4, 0, 1, 1] as const;

export const pageTransition = {
  duration: 0.24,
  ease: smoothEase,
} as const;

/** Shared reveal timing for floating panels (stats HUD, settings modal, etc.). */
export const surfaceRevealTransition = {
  duration: 0.34,
  ease: smoothEase,
} as const;

export const panelSpring = {
  type: "spring",
  stiffness: 420,
  damping: 36,
  mass: 0.85,
} as const;

export const overlayMotion = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.18, ease: standardEase },
} as const;

export const dialogMotion = {
  initial: { opacity: 0, scale: 0.97, y: 10 },
  animate: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: 0.985, y: 6 },
  transition: { duration: 0.2, ease: smoothEase },
} as const;

export const spinnerTransition = {
  duration: 0.85,
  ease: "linear",
  repeat: Infinity,
} as const;

export function MotionProvider({ children }: PropsWithChildren): JSX.Element {
  return (
    <LazyMotion features={domAnimation}>
      <MotionConfig reducedMotion="user" transition={panelSpring}>
        {children}
      </MotionConfig>
    </LazyMotion>
  );
}
