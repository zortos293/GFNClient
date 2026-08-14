import { isShortcutMatch, normalizeShortcut } from "@shared/shortcut";
import type { StreamShortcutInterceptionGate } from "@shared/gfn";

export interface StreamShortcutInput {
  type: string;
  key: string;
  code: string;
  control: boolean;
  alt: boolean;
  shift: boolean;
  meta: boolean;
  isAutoRepeat: boolean;
}

export type StreamShortcutInterceptionDecision = "ignore" | "consume" | "dispatch";

export function resolveStatsShortcutInterception(
  gate: StreamShortcutInterceptionGate,
  input: StreamShortcutInput,
  shortcut: string,
): StreamShortcutInterceptionDecision {
  if (!gate.streamActive || gate.shortcutCaptureActive) {
    return "ignore";
  }

  const matched = isShortcutMatch(
    {
      key: input.key,
      code: input.code,
      ctrlKey: input.control,
      altKey: input.alt,
      shiftKey: input.shift,
      metaKey: input.meta,
      repeat: input.isAutoRepeat,
    },
    normalizeShortcut(shortcut),
  );
  if (!matched) {
    return "ignore";
  }

  return input.type === "keyDown" && !input.isAutoRepeat
    ? "dispatch"
    : "consume";
}
