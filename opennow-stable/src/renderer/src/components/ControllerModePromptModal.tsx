import { ArrowRight, Gamepad2 } from "lucide-react";
import { useRef, type JSX } from "react";
import { useTranslation } from "../i18n";
import { useControllerNavigation } from "../hooks/useControllerNavigation";
import { resolveControllerModePromptAction } from "../hooks/useControllerModePrompt";
import { ConsoleHintGlyphIcon } from "./console/ConsoleHintBar";
import { ModalSurface } from "./ui/ModalSurface";

export interface ControllerModePromptModalProps {
  open: boolean;
  onAccept: () => void;
  onDecline: () => void;
  onExitComplete?: () => void;
}

export function ControllerModePromptModal({
  open,
  onAccept,
  onDecline,
  onExitComplete,
}: ControllerModePromptModalProps): JSX.Element {
  const { t } = useTranslation();
  const primaryActionRef = useRef<HTMLButtonElement | null>(null);
  useControllerNavigation({
    enabled: open,
    onFrame: ({ pressed }) => {
      const action = resolveControllerModePromptAction(pressed);
      if (action === "accept") onAccept();
      if (action === "decline") onDecline();
    },
  });

  return (
    <ModalSurface
      open={open}
      onClose={onDecline}
      onConfirm={onAccept}
      onExitComplete={onExitComplete}
      motion="compact"
      overlayClassName="rh-overlay controller-mode-prompt-overlay"
      backdropClassName="rh-backdrop"
      panelClassName="rh-card controller-mode-prompt-card"
      ariaLabelledBy="controller-mode-prompt-title"
      ariaDescribedBy="controller-mode-prompt-description"
      backdropLabel={t("app.actions.close")}
      initialFocusRef={primaryActionRef}
      closeOnBackdrop={false}
      closeOnEscape={false}
    >
      <div className="controller-mode-prompt-content">
        <div className="controller-mode-prompt-icon" aria-hidden="true">
          <Gamepad2 size={32} strokeWidth={1.8} />
          <span className="controller-mode-prompt-status" />
        </div>
        <div>
          <div className="controller-mode-prompt-kicker">{t("console.controllerPrompt.kicker")}</div>
          <h2 id="controller-mode-prompt-title" className="controller-mode-prompt-title">
            {t("console.controllerPrompt.title")}
          </h2>
          <p id="controller-mode-prompt-description" className="controller-mode-prompt-description">
            {t("console.controllerPrompt.description")}
          </p>
        </div>
      </div>

      <div className="controller-mode-prompt-actions">
        <button type="button" className="controller-mode-prompt-decline" onClick={onDecline}>
          <ConsoleHintGlyphIcon glyph="b" />
          {t("console.controllerPrompt.decline")}
        </button>
        <button
          type="button"
          className="controller-mode-prompt-accept"
          onClick={onAccept}
          ref={primaryActionRef}
        >
          <ConsoleHintGlyphIcon glyph="a" />
          {t("console.controllerPrompt.accept")}
          <ArrowRight size={16} strokeWidth={2.2} aria-hidden="true" />
        </button>
      </div>
    </ModalSurface>
  );
}
