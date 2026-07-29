import { Loader2 } from "lucide-react";
import { m } from "motion/react";
import type { JSX } from "react";
import { spinnerTransition } from "./MotionProvider";

interface MotionSpinnerProps {
  size?: number;
  className?: string;
  label?: string;
}

export function MotionSpinner({
  size = 20,
  className,
  label = "Loading",
}: MotionSpinnerProps): JSX.Element {
  return (
    <m.span
      className={["motion-spinner", className].filter(Boolean).join(" ")}
      animate={{ rotate: 360 }}
      transition={spinnerTransition}
      role="status"
      aria-label={label}
    >
      <Loader2 size={size} aria-hidden="true" />
    </m.span>
  );
}
