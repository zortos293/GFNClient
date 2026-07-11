function isPressedGamepadButton(button: GamepadButton | undefined): boolean {
  return Boolean(button?.pressed || (button?.value ?? 0) > 0.5);
}

type ControllerOverlayChordHalf = "view" | "menu";

export interface ControllerOverlayChordState {
  pendingHalf: ControllerOverlayChordHalf;
  pendingSinceMs: number;
  disqualified: boolean;
}

export interface ControllerOverlayShortcutGate {
  overlayPressed: boolean;
  preemptInput: boolean;
  nextState: ControllerOverlayChordState | null;
}

const CONTROLLER_OVERLAY_CHORD_GRACE_MS = 120;

export function evaluateControllerOverlayShortcutGate(
  gamepad: Pick<Gamepad, "buttons">,
  state: ControllerOverlayChordState | null,
  nowMs: number,
  graceMs: number = CONTROLLER_OVERLAY_CHORD_GRACE_MS,
): ControllerOverlayShortcutGate {
  const guidePressed = isPressedGamepadButton(gamepad.buttons[16]);
  const viewPressed = isPressedGamepadButton(gamepad.buttons[8]);
  const menuPressed = isPressedGamepadButton(gamepad.buttons[9]);
  const pressedHalf: ControllerOverlayChordHalf | null = viewPressed === menuPressed
    ? null
    : viewPressed
      ? "view"
      : "menu";

  if (guidePressed) {
    return { overlayPressed: true, preemptInput: true, nextState: null };
  }

  if (viewPressed && menuPressed) {
    if (state?.disqualified) {
      return { overlayPressed: false, preemptInput: false, nextState: state };
    }

    return { overlayPressed: true, preemptInput: true, nextState: null };
  }

  if (!pressedHalf) {
    return { overlayPressed: false, preemptInput: false, nextState: null };
  }

  if (state?.disqualified) {
    return {
      overlayPressed: false,
      preemptInput: false,
      nextState: state.pendingHalf === pressedHalf
        ? state
        : { pendingHalf: pressedHalf, pendingSinceMs: nowMs, disqualified: true },
    };
  }

  if (!state || state.pendingHalf !== pressedHalf) {
    return {
      overlayPressed: false,
      preemptInput: true,
      nextState: { pendingHalf: pressedHalf, pendingSinceMs: nowMs, disqualified: false },
    };
  }

  const disqualified = nowMs - state.pendingSinceMs >= graceMs;
  const nextState = { ...state, disqualified };
  return {
    overlayPressed: false,
    preemptInput: !disqualified,
    nextState,
  };
}
