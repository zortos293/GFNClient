import type { AppLaunchMode } from "@shared/gfn";

export interface AppLaunchModeInputs {
  controllerMode: boolean;
  requestGamepadFriendlySession: boolean;
  directLaunchConsoleMode: boolean;
}

export function resolveAppLaunchMode({
  controllerMode,
  requestGamepadFriendlySession,
  directLaunchConsoleMode,
}: AppLaunchModeInputs): AppLaunchMode {
  return controllerMode || requestGamepadFriendlySession || directLaunchConsoleMode
    ? "gamepadFriendly"
    : "default";
}
