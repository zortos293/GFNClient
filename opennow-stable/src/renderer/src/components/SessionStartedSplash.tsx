import { useEffect } from "react";
import { AnimatePresence, m } from "motion/react";
import { Check } from "lucide-react";
import type { JSX } from "react";
import { smoothEase } from "./MotionProvider";
import { useTranslation } from "../i18n";

const CONNECTED_BADGE_VISIBLE_MS = 2400;

const badgeVariants = {
  hidden: {
    opacity: 0,
    scale: 0.88,
    y: -28,
    filter: "blur(8px)",
  },
  visible: {
    opacity: 1,
    scale: 1,
    y: 0,
    filter: "blur(0px)",
    transition: {
      duration: 0.5,
      ease: smoothEase,
    },
  },
  exit: {
    opacity: 0,
    scale: 0.96,
    y: -20,
    filter: "blur(6px)",
    transition: { duration: 0.34, ease: smoothEase },
  },
} as const;

export interface SessionStartedSplashProps {
  visible: boolean;
  gameTitle: string;
  onFinished: () => void;
}

export function SessionStartedSplash({
  visible,
  onFinished,
}: SessionStartedSplashProps): JSX.Element | null {
  const { t } = useTranslation();

  useEffect(() => {
    if (!visible) {
      return undefined;
    }
    const timer = window.setTimeout(onFinished, CONNECTED_BADGE_VISIBLE_MS);
    return () => window.clearTimeout(timer);
  }, [onFinished, visible]);

  return (
    <AnimatePresence>
      {visible && (
        <m.div
          className="sv-ready-splash"
          role="status"
          aria-live="polite"
          variants={badgeVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
        >
          <span className="sv-ready-splash-card">
            <span className="sv-ready-splash-emblem" aria-hidden>
              <Check size={15} strokeWidth={3} />
            </span>
            <span className="sv-ready-splash-title">{t("stream.connectedBadge.title")}</span>
          </span>
        </m.div>
      )}
    </AnimatePresence>
  );
}
