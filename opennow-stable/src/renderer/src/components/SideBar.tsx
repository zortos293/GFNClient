import type { JSX, ReactNode, Ref } from "react";
import { useTranslation } from "../i18n";

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
    <aside
      ref={elementRef}
      className={classNames}
      role="dialog"
      aria-label={sidebarTitle}
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
            ✕
          </button>
        )}
      </div>
      <div className="sidebar-body">
        {children}
      </div>
      {footer && <div className="sidebar-footer">{footer}</div>}
    </aside>
  );
}
