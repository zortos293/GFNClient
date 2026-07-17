import type { JSX, ReactNode } from "react";
import { useTranslation } from "../i18n";
import { ModalSurface } from "./ui/ModalSurface";

export interface SettingsModalHostProps {
  open: boolean;
  onClose: () => void;
  onExitComplete?: () => void;
  children: ReactNode;
}

export function SettingsModalHost({
  open,
  onClose,
  onExitComplete,
  children,
}: SettingsModalHostProps): JSX.Element | null {
  const { t } = useTranslation();

  return (
    <ModalSurface
      open={open}
      onClose={onClose}
      onExitComplete={onExitComplete}
      motion="large"
      overlayClassName="animated-modal-overlay modal-portal-layer"
      backdropClassName="animated-modal-scrim"
      panelClassName="animated-modal-panel settings-modal"
      ariaLabel={t("settings.title")}
      backdropLabel={t("app.actions.close")}
    >
      {children}
    </ModalSurface>
  );
}
