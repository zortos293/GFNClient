import { useCallback, useEffect, useState } from "react";
import { controllerButton } from "../utils/controllerGamepad";

export type ControllerModePromptAction = "accept" | "decline" | null;

export function resolveControllerModePromptAction(pressedButtons: number): ControllerModePromptAction {
  if (pressedButtons & controllerButton.south) return "accept";
  if (pressedButtons & controllerButton.east) return "decline";
  return null;
}

export interface ControllerModePromptEligibility {
  settingsLoaded: boolean;
  controllerMode: boolean;
  directLaunchConsoleMode: boolean;
  promptDismissed: boolean;
}

export function shouldOfferControllerModePrompt({
  settingsLoaded,
  controllerMode,
  directLaunchConsoleMode,
  promptDismissed,
}: ControllerModePromptEligibility): boolean {
  return settingsLoaded
    && !controllerMode
    && !directLaunchConsoleMode
    && !promptDismissed;
}

export function hasConnectedGamepad(
  gamepads: ArrayLike<Gamepad | null> | undefined,
): boolean {
  return gamepads !== undefined
    && Array.from(gamepads).some((gamepad) => gamepad !== null && gamepad.connected !== false);
}

export function useControllerModePrompt(enabled: boolean): {
  open: boolean;
  dismiss: () => void;
} {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!enabled) {
      setOpen(false);
      return undefined;
    }

    const showPrompt = (): void => setOpen(true);
    window.addEventListener("gamepadconnected", showPrompt);

    if (hasConnectedGamepad(navigator.getGamepads?.())) {
      showPrompt();
    }

    return () => window.removeEventListener("gamepadconnected", showPrompt);
  }, [enabled]);

  const dismiss = useCallback((): void => setOpen(false), []);
  return { open, dismiss };
}
