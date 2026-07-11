import type { JSX, ReactNode, Ref } from "react";
import { X } from "lucide-react";
import { m } from "motion/react";
import { useTranslation } from "../i18n";
import { panelSpring } from "./MotionProvider";

interface SideBarProps {
  title?: string;
  children?: ReactNode;
  footer?: ReactNode;
  className?: string;
  onClose?: () => void;
  elementRef?: Ref<HTMLElement>;
}

export default function SideBar({
  title,
  children,
  footer,
  className = "",
  onClose,
  elementRef,
}: SideBarProps): JSX.Element {
  const { t } = useTranslation();
  const classNames = ["sidebar", className].filter(Boolean).join(" ");
  const sidebarTitle = title ?? t("sidebar.title");

  return (
    <m.aside
      ref={elementRef}
      className={classNames}
      role="dialog"
      aria-modal="true"
      aria-label={sidebarTitle}
      initial={{ opacity: 0, x: -28 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -28 }}
      transition={panelSpring}
    >
      <div className="sidebar-header">
        <h3>{sidebarTitle}</h3>
        {onClose && (
          <button
            type="button"
            className="sidebar-close"
            onClick={onClose}
            aria-label={t("sidebar.closeSettings")}
          >
            <X size={16} aria-hidden="true" />
            <span className="sidebar-close-hit-area" aria-hidden="true" />
          </button>
        )}
      </div>
      <div className="sidebar-body">
        {children}
      </div>
      {footer && <div className="sidebar-footer">{footer}</div>}
    </m.aside>
  );
}
