import { useCallback, useEffect, useRef, useState } from "react";
import type { Dispatch, RefObject, SetStateAction } from "react";
import { isShortcutMatch, normalizeShortcut } from "../shortcuts";
import { addStreamShortcutActionListener } from "../streamShortcutActions";
import { controllerButton, readControllerGamepadButtons } from "../utils/controllerGamepad";

const CONTROLLER_MENU_REPEAT_MS = 180;

export const STREAM_MENU_TABS = ["session", "controls", "media", "shortcuts"] as const;
export type StreamMenuTab = (typeof STREAM_MENU_TABS)[number];

interface UseStreamMenuNavigationOptions {
  shortcuts: {
    screenshot: string;
    recording: string;
  };
  isMacClient: boolean;
  exitPromptOpen: boolean;
  selectedScreenshotId: string | null;
  setSelectedScreenshotId: Dispatch<SetStateAction<string | null>>;
  captureScreenshot: () => Promise<void>;
  toggleRecording: () => Promise<void>;
  onCancelExit: () => void;
  onConfirmExit: () => void;
  onBeforeOpen: () => void;
}

interface StreamMenuNavigation {
  showSideBar: boolean;
  setShowSideBar: Dispatch<SetStateAction<boolean>>;
  activeSidebarTab: StreamMenuTab;
  setActiveSidebarTab: Dispatch<SetStateAction<StreamMenuTab>>;
  sidebarRef: RefObject<HTMLElement | null>;
}

function isTypingTarget(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  return !!element && (
    element.tagName === "INPUT" ||
    element.tagName === "TEXTAREA" ||
    element.isContentEditable
  );
}

function readButtons(): number {
  const pad = navigator.getGamepads?.().find((gamepad): gamepad is Gamepad => Boolean(gamepad));
  return readControllerGamepadButtons(pad);
}

export function useStreamMenuNavigation({
  shortcuts,
  isMacClient,
  exitPromptOpen,
  selectedScreenshotId,
  setSelectedScreenshotId,
  captureScreenshot,
  toggleRecording,
  onCancelExit,
  onConfirmExit,
  onBeforeOpen,
}: UseStreamMenuNavigationOptions): StreamMenuNavigation {
  const [showSideBar, setShowSideBar] = useState(false);
  const [activeSidebarTab, setActiveSidebarTab] = useState<StreamMenuTab>("session");
  const sidebarRef = useRef<HTMLElement | null>(null);
  const sidebarGamepadFrameRef = useRef<number | null>(null);
  const sidebarGamepadPreviousButtonsRef = useRef(0);
  const sidebarGamepadLastMoveAtRef = useRef(0);
  const exitPromptGamepadFrameRef = useRef<number | null>(null);
  const exitPromptGamepadPreviousButtonsRef = useRef(0);

  const handleToggleSideBar = useCallback(() => {
    setShowSideBar((open) => {
      if (!open) {
        onBeforeOpen();
      }
      return !open;
    });
  }, [onBeforeOpen]);

  const selectAdjacentSidebarTab = useCallback((direction: -1 | 1) => {
    setActiveSidebarTab((current) => {
      const index = STREAM_MENU_TABS.indexOf(current);
      return STREAM_MENU_TABS[(index + direction + STREAM_MENU_TABS.length) % STREAM_MENU_TABS.length];
    });
    window.requestAnimationFrame(() => {
      sidebarRef.current?.querySelector<HTMLElement>(".sidebar-tab--active")?.focus({ preventScroll: true });
    });
  }, []);

  useEffect(() => {
    if (!showSideBar || exitPromptOpen) return;

    const getMenuItems = (): HTMLElement[] => {
      const scope = selectedScreenshotId
        ? document.querySelector<HTMLElement>(".sv-shot-modal-card")
        : sidebarRef.current;
      if (!scope) return [];
      return Array.from(scope.querySelectorAll<HTMLElement>(
        "button:not(:disabled), input:not(:disabled):not([type='checkbox']), label.sidebar-mini-toggle, [tabindex='0']",
      )).filter((element) => {
        const style = window.getComputedStyle(element);
        const isInactiveTab =
          element.getAttribute("role") === "tab" &&
          element.getAttribute("aria-selected") !== "true";
        return !isInactiveTab && style.display !== "none" && style.visibility !== "hidden";
      });
    };

    const focusItem = (direction: -1 | 1): void => {
      const items = getMenuItems();
      if (items.length === 0) return;
      const currentIndex = items.findIndex((item) => item === document.activeElement);
      const nextIndex = currentIndex < 0
        ? 0
        : (currentIndex + direction + items.length) % items.length;
      items[nextIndex]?.focus({ preventScroll: true });
      items[nextIndex]?.scrollIntoView({ block: "nearest" });
    };

    const changeRange = (input: HTMLInputElement, direction: -1 | 1): void => {
      const min = Number(input.min || 0);
      const max = Number(input.max || 100);
      const step = Number(input.step || 1);
      const value = Math.max(min, Math.min(max, Number(input.value) + step * direction));
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set?.call(input, String(value));
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
    };

    const handleGamepadFrame = (): void => {
      const buttons = readButtons();
      let pressed = buttons & ~sidebarGamepadPreviousButtonsRef.current;
      const moveMask = controllerButton.up |
        controllerButton.down |
        controllerButton.left |
        controllerButton.right;
      const activeMoves = buttons & moveMask;
      const now = performance.now();
      if (pressed & moveMask) {
        sidebarGamepadLastMoveAtRef.current = now;
      } else if (
        activeMoves &&
        now - sidebarGamepadLastMoveAtRef.current > CONTROLLER_MENU_REPEAT_MS
      ) {
        pressed |= activeMoves;
        sidebarGamepadLastMoveAtRef.current = now;
      }

      const active = document.activeElement as HTMLElement | null;
      const range = active instanceof HTMLInputElement && active.type === "range" ? active : null;
      if (pressed & controllerButton.up) focusItem(-1);
      if (pressed & controllerButton.down) focusItem(1);
      if (pressed & controllerButton.left) {
        if (range) changeRange(range, -1);
        else if (active?.getAttribute("role") === "tab") selectAdjacentSidebarTab(-1);
        else focusItem(-1);
      }
      if (pressed & controllerButton.right) {
        if (range) changeRange(range, 1);
        else if (active?.getAttribute("role") === "tab") selectAdjacentSidebarTab(1);
        else focusItem(1);
      }
      if (pressed & controllerButton.leftShoulder) selectAdjacentSidebarTab(-1);
      if (pressed & controllerButton.rightShoulder) selectAdjacentSidebarTab(1);
      if (pressed & controllerButton.south) {
        if (active && !range) active.click();
      }
      if (pressed & controllerButton.east) {
        if (selectedScreenshotId) setSelectedScreenshotId(null);
        else setShowSideBar(false);
      }
      if (pressed & controllerButton.menu) setShowSideBar(false);

      sidebarGamepadPreviousButtonsRef.current = buttons;
      sidebarGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };

    const initialFocusTimer = window.setTimeout(() => {
      const initialFocus = selectedScreenshotId
        ? document.querySelector<HTMLElement>(".sv-shot-modal-btn:not(:disabled), .sv-shot-modal-close")
        : sidebarRef.current?.querySelector<HTMLElement>(".sidebar-tab--active");
      initialFocus?.focus({ preventScroll: true });
    }, 0);
    sidebarGamepadPreviousButtonsRef.current = readButtons();
    sidebarGamepadLastMoveAtRef.current = performance.now();
    sidebarGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);

    return () => {
      window.clearTimeout(initialFocusTimer);
      if (sidebarGamepadFrameRef.current !== null) {
        window.cancelAnimationFrame(sidebarGamepadFrameRef.current);
        sidebarGamepadFrameRef.current = null;
      }
      sidebarGamepadPreviousButtonsRef.current = 0;
      sidebarGamepadLastMoveAtRef.current = 0;
    };
  }, [
    exitPromptOpen,
    selectAdjacentSidebarTab,
    selectedScreenshotId,
    setSelectedScreenshotId,
    showSideBar,
  ]);

  useEffect(() => {
    if (!exitPromptOpen) return;

    const focusExitButton = (confirm: boolean): void => {
      document.querySelector<HTMLButtonElement>(
        confirm ? ".sv-exit-btn-confirm" : ".sv-exit-btn-cancel",
      )?.focus({ preventScroll: true });
    };
    const handleGamepadFrame = (): void => {
      const buttons = readButtons();
      const pressed = buttons & ~exitPromptGamepadPreviousButtonsRef.current;
      if (pressed & (controllerButton.left | controllerButton.up)) focusExitButton(false);
      if (pressed & (controllerButton.right | controllerButton.down)) focusExitButton(true);
      if (pressed & controllerButton.south) {
        const active = document.activeElement as HTMLElement | null;
        if (active?.closest(".sv-exit-card")) active.click();
      }
      if (pressed & (controllerButton.east | controllerButton.menu)) onCancelExit();
      exitPromptGamepadPreviousButtonsRef.current = buttons;
      exitPromptGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    };
    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancelExit();
      } else if (event.key === "Enter") {
        event.preventDefault();
        onConfirmExit();
      }
    };

    const focusTimer = window.setTimeout(() => focusExitButton(false), 0);
    exitPromptGamepadPreviousButtonsRef.current = readButtons();
    exitPromptGamepadFrameRef.current = window.requestAnimationFrame(handleGamepadFrame);
    window.addEventListener("keydown", handleKeyDown, true);
    return () => {
      window.clearTimeout(focusTimer);
      window.removeEventListener("keydown", handleKeyDown, true);
      if (exitPromptGamepadFrameRef.current !== null) {
        window.cancelAnimationFrame(exitPromptGamepadFrameRef.current);
        exitPromptGamepadFrameRef.current = null;
      }
      exitPromptGamepadPreviousButtonsRef.current = 0;
    };
  }, [exitPromptOpen, onCancelExit, onConfirmExit]);

  useEffect(() => {
    return addStreamShortcutActionListener((action) => {
      if (action === "toggleSidebar") {
        handleToggleSideBar();
        return;
      }
      if (action === "screenshot") {
        void captureScreenshot();
        return;
      }
      if (action === "toggleRecording") {
        void toggleRecording();
      }
    });
  }, [captureScreenshot, handleToggleSideBar, toggleRecording]);

  useEffect(() => {
    const screenshotShortcut = normalizeShortcut(shortcuts.screenshot);
    const recordingShortcut = normalizeShortcut(shortcuts.recording);

    const onKeyDown = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) {
        return;
      }

      const key = event.key.toLowerCase();
      const isSidebarShortcut = isMacClient
        ? event.metaKey && !event.ctrlKey && !event.altKey && !event.shiftKey && key === "g"
        : event.ctrlKey && !event.altKey && !event.metaKey && key === "g";
      if (isSidebarShortcut) {
        return;
      }

      if (isShortcutMatch(event, screenshotShortcut)) {
        event.preventDefault();
        event.stopPropagation();
        void captureScreenshot();
        return;
      }

      if (isShortcutMatch(event, recordingShortcut)) {
        event.preventDefault();
        event.stopPropagation();
        void toggleRecording();
      }
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [
    captureScreenshot,
    isMacClient,
    shortcuts.recording,
    shortcuts.screenshot,
    toggleRecording,
  ]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) {
        return;
      }

      const key = event.key.toLowerCase();
      if (isMacClient) {
        if (event.metaKey && !event.ctrlKey && !event.shiftKey && key === "g") {
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();
          handleToggleSideBar();
        }
      } else if (event.ctrlKey && !event.altKey && !event.metaKey && key === "g") {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        handleToggleSideBar();
      }
    };

    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [handleToggleSideBar, isMacClient]);

  return {
    showSideBar,
    setShowSideBar,
    activeSidebarTab,
    setActiveSidebarTab,
    sidebarRef,
  };
}
