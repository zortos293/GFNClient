import { Menu } from "lucide-react";
import type { JSX } from "react";

export type ConsoleHintGlyph = "a" | "b" | "x" | "y" | "menu";

export interface ConsoleHint {
  glyph: ConsoleHintGlyph;
  label: string;
  onSelect?: () => void;
}

export interface ConsoleHintBarProps {
  hints: ConsoleHint[];
}

export function ConsoleHintGlyphIcon({ glyph }: { glyph: ConsoleHintGlyph }): JSX.Element {
  return (
    <span className={`console-hint-glyph console-hint-glyph--${glyph}`} aria-hidden="true">
      {glyph === "menu" ? <Menu /> : glyph.toUpperCase()}
    </span>
  );
}

/** Shared button-prompt strip along the bottom of every console surface. */
export function ConsoleHintBar({ hints }: ConsoleHintBarProps): JSX.Element {
  return (
    <div className="console-hint-bar" role="toolbar">
      {hints.map((hint) => (
        <button key={`${hint.glyph}-${hint.label}`} type="button" className="console-hint" onClick={() => hint.onSelect?.()}>
          <ConsoleHintGlyphIcon glyph={hint.glyph} />
          <span>{hint.label}</span>
        </button>
      ))}
    </div>
  );
}
