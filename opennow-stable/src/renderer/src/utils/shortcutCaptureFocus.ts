interface ShortcutCaptureTarget {
  classList?: {
    contains: (value: string) => boolean;
  };
  disabled?: boolean;
}

export function isShortcutCaptureTarget(
  target: ShortcutCaptureTarget | null,
): boolean {
  return (
    target?.classList?.contains("settings-shortcut-input") === true
    && target.classList.contains("settings-shortcut-input--static") === false
    && target.disabled !== true
  );
}
