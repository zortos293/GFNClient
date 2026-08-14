export function isControllerKeyboardActivationTarget(target: EventTarget | null): boolean {
  return target instanceof Element
    && target.closest("button, a, input, select, textarea, [role='button'], [role='option']") !== null;
}
