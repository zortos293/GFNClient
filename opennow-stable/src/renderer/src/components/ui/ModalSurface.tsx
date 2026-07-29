import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useSyncExternalStore,
  type JSX,
  type ReactNode,
  type RefObject,
} from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, m, useIsPresent } from "motion/react";
import {
  dialogMotion,
  largeSurfaceMotion,
  overlayMotion,
} from "../MotionProvider";

type ModalMotion = "large" | "compact" | "none";

export interface ModalSurfaceProps {
  open: boolean;
  onClose: () => void;
  onConfirm?: () => void;
  onExitComplete?: () => void;
  children: ReactNode;
  overlayClassName: string;
  backdropClassName: string;
  panelClassName: string;
  motion?: ModalMotion;
  ariaLabel?: string;
  ariaLabelledBy?: string;
  ariaDescribedBy?: string;
  backdropLabel?: string;
  initialFocusRef?: RefObject<HTMLElement | null>;
  restoreFocusRef?: RefObject<HTMLElement | null>;
  closeOnBackdrop?: boolean;
  closeOnEscape?: boolean;
}

const modalStack: string[] = [];
const modalStackListeners = new Set<() => void>();
let bodyScrollLockCount = 0;
let bodyOverflowBeforeLock = "";

function emitModalStackChange(): void {
  for (const listener of modalStackListeners) listener();
}

function registerModal(stackId: string): void {
  const existingIndex = modalStack.lastIndexOf(stackId);
  if (existingIndex >= 0) modalStack.splice(existingIndex, 1);
  modalStack.push(stackId);
  emitModalStackChange();
}

function unregisterModal(stackId: string): boolean {
  const wasTopmost = modalStack.at(-1) === stackId;
  const stackIndex = modalStack.lastIndexOf(stackId);
  if (stackIndex >= 0) {
    modalStack.splice(stackIndex, 1);
    emitModalStackChange();
  }
  return wasTopmost;
}

function subscribeToModalStack(listener: () => void): () => void {
  modalStackListeners.add(listener);
  return () => modalStackListeners.delete(listener);
}

function getTopModalId(): string | null {
  return modalStack.at(-1) ?? null;
}

function lockBodyScroll(): void {
  if (bodyScrollLockCount === 0) {
    bodyOverflowBeforeLock = document.body.style.overflow;
    document.body.style.overflow = "hidden";
  }
  bodyScrollLockCount += 1;
}

function unlockBodyScroll(): void {
  bodyScrollLockCount = Math.max(0, bodyScrollLockCount - 1);
  if (bodyScrollLockCount === 0) {
    document.body.style.overflow = bodyOverflowBeforeLock;
    bodyOverflowBeforeLock = "";
  }
}

function getFocusableElements(scope: HTMLElement): HTMLElement[] {
  return Array.from(scope.querySelectorAll<HTMLElement>([
    "a[href]",
    "button:not([disabled])",
    "input:not([disabled])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    '[tabindex]:not([tabindex="-1"])',
  ].join(","))).filter((element) => {
    const style = window.getComputedStyle(element);
    return element.tabIndex >= 0
      && element.getAttribute("aria-hidden") !== "true"
      && style.display !== "none"
      && style.visibility !== "hidden";
  });
}

function isInteractiveKeyboardTarget(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(target.closest([
    "a[href]",
    "button",
    "input",
    "select",
    "textarea",
    '[role="button"]',
    '[role="combobox"]',
    '[contenteditable="true"]',
  ].join(",")));
}

interface ModalSurfaceFrameProps extends Omit<ModalSurfaceProps, "open" | "onExitComplete"> {
  stackId: string;
}

function ModalSurfaceFrame({
  stackId,
  onClose,
  onConfirm,
  children,
  overlayClassName,
  backdropClassName,
  panelClassName,
  motion = "compact",
  ariaLabel,
  ariaLabelledBy,
  ariaDescribedBy,
  backdropLabel = "Close dialog",
  initialFocusRef,
  restoreFocusRef,
  closeOnBackdrop = true,
  closeOnEscape = true,
}: ModalSurfaceFrameProps): JSX.Element {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  const onConfirmRef = useRef(onConfirm);
  const closeOnEscapeRef = useRef(closeOnEscape);
  const initialFocusTargetRef = useRef(initialFocusRef);
  const restoreFocusTargetRef = useRef(restoreFocusRef);
  const confirmHandledRef = useRef(false);
  const isPresent = useIsPresent();
  const topModalId = useSyncExternalStore(subscribeToModalStack, getTopModalId, () => null);
  const ownsInteraction = isPresent && topModalId === stackId;
  const ownsInteractionRef = useRef(ownsInteraction);
  onCloseRef.current = onClose;
  onConfirmRef.current = onConfirm;
  closeOnEscapeRef.current = closeOnEscape;
  initialFocusTargetRef.current = initialFocusRef;
  restoreFocusTargetRef.current = restoreFocusRef;
  ownsInteractionRef.current = ownsInteraction;

  useEffect(() => {
    if (isPresent) confirmHandledRef.current = false;
  }, [isPresent]);

  useLayoutEffect(() => {
    previousFocusRef.current = restoreFocusTargetRef.current?.current
      ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);
    registerModal(stackId);
    lockBodyScroll();

    const focusFrame = window.requestAnimationFrame(() => {
      const panel = panelRef.current;
      if (!panel || modalStack.at(-1) !== stackId) return;
      const initialTarget = initialFocusTargetRef.current?.current ?? getFocusableElements(panel)[0] ?? panel;
      initialTarget.focus({ preventScroll: true });
    });

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (!ownsInteractionRef.current || event.defaultPrevented) return;

      if (event.key === "Escape" && closeOnEscapeRef.current) {
        event.preventDefault();
        event.stopPropagation();
        onCloseRef.current();
        return;
      }

      if (
        event.key === "Enter"
        && !event.repeat
        && !event.isComposing
        && onConfirmRef.current
        && !isInteractiveKeyboardTarget(event.target)
      ) {
        event.preventDefault();
        event.stopPropagation();
        if (confirmHandledRef.current) return;
        confirmHandledRef.current = true;
        onConfirmRef.current();
        return;
      }

      if (event.key !== "Tab") return;
      const panel = panelRef.current;
      if (!panel) return;
      const focusable = getFocusableElements(panel);
      if (focusable.length === 0) {
        event.preventDefault();
        panel.focus({ preventScroll: true });
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || active === panel || !panel.contains(active))) {
        event.preventDefault();
        last.focus({ preventScroll: true });
      } else if (!event.shiftKey && (active === last || active === panel || !panel.contains(active))) {
        event.preventDefault();
        first.focus({ preventScroll: true });
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown);
      const wasTopmost = unregisterModal(stackId);
      unlockBodyScroll();

      const previousFocus = previousFocusRef.current;
      previousFocusRef.current = null;
      if (wasTopmost && previousFocus?.isConnected) {
        window.requestAnimationFrame(() => {
          window.requestAnimationFrame(() => {
            if (previousFocus.isConnected) {
              previousFocus.focus({ preventScroll: true });
            }
          });
        });
      }
    };
  }, [stackId]);

  const panelMotion = motion === "large" ? largeSurfaceMotion : dialogMotion;
  const modalState = !isPresent ? "exiting" : ownsInteraction ? "active" : "covered";
  const closeFromBackdrop = closeOnBackdrop
    ? () => {
        if (ownsInteractionRef.current) onCloseRef.current();
      }
    : undefined;

  if (motion === "none") {
    return (
      <div
        className={overlayClassName}
        role="dialog"
        aria-modal={ownsInteraction ? true : undefined}
        aria-hidden={ownsInteraction ? undefined : true}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        aria-describedby={ariaDescribedBy}
        data-modal-state={modalState}
        inert={ownsInteraction ? undefined : true}
        style={ownsInteraction ? undefined : { pointerEvents: "none" }}
      >
        <button
          type="button"
          className={backdropClassName}
          aria-label={backdropLabel}
          tabIndex={-1}
          onClick={closeFromBackdrop}
        />
        <div ref={panelRef} className={panelClassName} tabIndex={-1}>
          {children}
        </div>
      </div>
    );
  }

  return (
    <m.div
      className={overlayClassName}
      role="dialog"
      aria-modal={ownsInteraction ? true : undefined}
      aria-hidden={ownsInteraction ? undefined : true}
      aria-label={ariaLabel}
      aria-labelledby={ariaLabelledBy}
      aria-describedby={ariaDescribedBy}
      data-modal-state={modalState}
      inert={ownsInteraction ? undefined : true}
      style={ownsInteraction ? undefined : { pointerEvents: "none" }}
      initial={overlayMotion.initial}
      animate={overlayMotion.animate}
      exit={overlayMotion.exit}
      transition={overlayMotion.transition}
    >
      <button
        type="button"
        className={backdropClassName}
        aria-label={backdropLabel}
        tabIndex={-1}
        onClick={closeFromBackdrop}
      />
      <m.div
        ref={panelRef}
        className={panelClassName}
        tabIndex={-1}
        initial={panelMotion.initial}
        animate={panelMotion.animate}
        exit={panelMotion.exit}
        transition={panelMotion.transition}
      >
        {children}
      </m.div>
    </m.div>
  );
}

export function ModalSurface({
  open,
  onExitComplete,
  motion = "compact",
  ...frameProps
}: ModalSurfaceProps): JSX.Element | null {
  const generatedId = useId();
  const stackId = `modal-${generatedId}`;

  if (typeof document === "undefined") return null;

  const handleExitComplete = (): void => {
    onExitComplete?.();
    const restoreTarget = frameProps.restoreFocusRef?.current;
    if (!restoreTarget) return;

    let remainingFrames = 12;
    const restoreWhenInteractive = (): void => {
      if (!restoreTarget.isConnected) return;
      if (!restoreTarget.closest("[inert]")) {
        restoreTarget.focus({ preventScroll: true });
        return;
      }
      if (remainingFrames > 0) {
        remainingFrames -= 1;
        window.requestAnimationFrame(restoreWhenInteractive);
      }
    };
    window.requestAnimationFrame(restoreWhenInteractive);
  };

  if (motion === "none") {
    return open
      ? createPortal(<ModalSurfaceFrame {...frameProps} motion="none" stackId={stackId} />, document.body)
      : null;
  }

  return createPortal(
    <AnimatePresence initial={false} onExitComplete={handleExitComplete}>
      {open ? (
        <ModalSurfaceFrame
          key={stackId}
          {...frameProps}
          motion={motion}
          stackId={stackId}
        />
      ) : null}
    </AnimatePresence>,
    document.body,
  );
}
