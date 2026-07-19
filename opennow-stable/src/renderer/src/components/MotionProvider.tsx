import type { JSX, PropsWithChildren } from "react";
import { LazyMotion, MotionConfig, domAnimation } from "motion/react";

export const smoothEase = [0.16, 1, 0.3, 1] as const;
export const standardEase = [0.4, 0, 0.2, 1] as const;
export const exitEase = [0.4, 0, 1, 1] as const;

export const pageTransition = {
  duration: 0.24,
  ease: smoothEase,
} as const;

export const largeSurfaceTransition = {
  duration: 0.28,
  ease: smoothEase,
} as const;

export const compactDialogTransition = {
  duration: 0.2,
  ease: smoothEase,
} as const;

export const disclosureTransition = {
  duration: 0.24,
  ease: smoothEase,
} as const;

export const streamRevealTransition = {
  duration: 0.62,
  ease: standardEase,
} as const;

export const statusPulseTransition = {
  duration: 1.8,
  repeat: Infinity,
  ease: "easeInOut",
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

export const largeSurfaceMotion = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: 8 },
  transition: largeSurfaceTransition,
} as const;

export const dialogMotion = {
  initial: { opacity: 0, scale: 0.97, y: 10 },
  animate: { opacity: 1, scale: 1, y: 0 },
  exit: { opacity: 0, scale: 0.985, y: 6 },
  transition: compactDialogTransition,
} as const;

export function getStatusPulseMotion(reducedMotion: boolean | null): {
  animate: { opacity: number | number[]; scale: number | number[] };
  transition: typeof statusPulseTransition | { duration: 0 };
} {
  if (reducedMotion) {
    return {
      animate: { opacity: 1, scale: 1 },
      transition: { duration: 0 },
    };
  }

  return {
    animate: { opacity: [0.55, 1, 0.55], scale: [0.94, 1.06, 0.94] },
    transition: statusPulseTransition,
  };
}

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
