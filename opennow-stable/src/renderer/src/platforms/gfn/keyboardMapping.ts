import type { KeyboardLayout } from "@shared/gfn";


import {
  scancodeByCode,
  specialVirtualKeyByCode,
  keyFallbackMap,
  baseCharCodeMap,
  shiftedCharCodeMap,
  germanBaseCharCodeMap,
  germanShiftedCharCodeMap,
} from "./keyboardScancodes";

export interface KeyboardPayload {
  keycode: number;
  scancode: number;
  modifiers: number;
  timestampUs: bigint;
}

export interface KeyMapping {
  vk: number;
  scancode: number;
}

export interface TextKeySpec extends KeyMapping {
  shift?: boolean;
}

type KeyLike = Pick<KeyboardEvent, "code" | "key" | "keyCode" | "location">;

const DOM_KEY_LOCATION_STANDARD = 0;
const DOM_KEY_LOCATION_LEFT = 1;
const DOM_KEY_LOCATION_RIGHT = 2;
const DOM_KEY_LOCATION_NUMPAD = 3;

function defaultVirtualKeyFromCode(code: string): number | null {
  if (code.startsWith("Key") && code.length === 4) {
    return code.charCodeAt(3);
  }

  if (code.startsWith("Digit") && code.length === 6) {
    return code.charCodeAt(5);
  }

  if (code.startsWith("F")) {
    const index = Number.parseInt(code.slice(1), 10);
    if (index >= 1 && index <= 24) {
      return 0x70 + index - 1;
    }
  }

  if (code.startsWith("Numpad") && code.length === 7) {
    const digit = Number.parseInt(code.slice(6), 10);
    if (digit >= 0 && digit <= 9) {
      return 0x60 + digit;
    }
  }

  return specialVirtualKeyByCode[code] ?? null;
}

function keyMappingFromCode(code: string): KeyMapping | null {
  const scancode = scancodeByCode[code];
  if (scancode === undefined) {
    return null;
  }

  const vk = defaultVirtualKeyFromCode(code);
  if (vk === null) {
    return null;
  }

  return { vk, scancode };
}

export const codeMap: Record<string, KeyMapping> = Object.freeze(
  Object.fromEntries(Object.keys(scancodeByCode).map((code) => [code, keyMappingFromCode(code)!])),
) as Record<string, KeyMapping>;

function virtualKeyFromKeyCode(event: KeyLike): number | null {
  const keyCode = event.keyCode;
  if (!Number.isInteger(keyCode) || keyCode <= 0 || keyCode === 229) {
    return null;
  }

  switch (event.code) {
    case "ShiftLeft":
      return 0xa0;
    case "ShiftRight":
      return 0xa1;
    case "ControlLeft":
      return 0xa2;
    case "ControlRight":
      return 0xa3;
    case "AltLeft":
      return 0xa4;
    case "AltRight":
      return 0xa5;
    case "MetaLeft":
      return 0x5b;
    case "MetaRight":
      return 0x5c;
  }

  if (event.location === DOM_KEY_LOCATION_NUMPAD) {
    if (keyCode >= 0x60 && keyCode <= 0x69) {
      return keyCode;
    }
    if (keyCode === 0x0d && event.code === "NumpadEnter") {
      return keyCode;
    }
  }

  return keyCode;
}

function virtualKeyFromKeyValue(key: string): number | null {
  if (key.length === 1) {
    const codePoint = key.toUpperCase().charCodeAt(0);
    if ((codePoint >= 0x30 && codePoint <= 0x39) || (codePoint >= 0x41 && codePoint <= 0x5a)) {
      return codePoint;
    }
  }

  switch (key) {
    case "Escape":
    case "Esc":
      return 0x1b;
    case "Enter":
      return 0x0d;
    case "Tab":
      return 0x09;
    case "Backspace":
      return 0x08;
    case " ":
    case "Spacebar":
      return 0x20;
    case "ArrowLeft":
      return 0x25;
    case "ArrowUp":
      return 0x26;
    case "ArrowRight":
      return 0x27;
    case "ArrowDown":
      return 0x28;
    case "Delete":
      return 0x2e;
    case "Insert":
      return 0x2d;
    case "Home":
      return 0x24;
    case "End":
      return 0x23;
    case "PageUp":
      return 0x21;
    case "PageDown":
      return 0x22;
  }

  return null;
}

function virtualKeyFromEvent(event: KeyLike): number | null {
  if (event.code) {
    const codeVk = defaultVirtualKeyFromCode(event.code);
    if (codeVk !== null) {
      return codeVk;
    }
  }

  return (
    virtualKeyFromKeyCode(event)
    ?? virtualKeyFromKeyValue(event.key)
    ?? defaultVirtualKeyFromCode(event.code)
  );
}

function textKeySpecFromCode(code: string, shift: boolean = false): TextKeySpec | null {
  const mapped = keyMappingFromCode(code);
  if (!mapped) {
    return null;
  }
  return shift ? { ...mapped, shift: true } : mapped;
}

export function mapTextCharToKeySpec(char: string, layout?: KeyboardLayout): TextKeySpec | null {
  const baseMap = layout === "de-DE" ? germanBaseCharCodeMap : baseCharCodeMap;
  const shiftedMap = layout === "de-DE" ? germanShiftedCharCodeMap : shiftedCharCodeMap;

  const baseCode = baseMap[char];
  if (baseCode) {
    return textKeySpecFromCode(baseCode);
  }

  const shiftedCode = shiftedMap[char];
  if (shiftedCode) {
    return textKeySpecFromCode(shiftedCode, true);
  }

  if (char >= "a" && char <= "z") {
    return textKeySpecFromCode(`Key${char.toUpperCase()}`);
  }

  if (char >= "A" && char <= "Z") {
    return textKeySpecFromCode(`Key${char}`, true);
  }

  return null;
}

function isMacKeyboardLayout(): boolean {
  return typeof navigator !== "undefined" && /Mac|iPhone|iPad|iPod/.test(navigator.platform);
}

/** Shift bit for per-key modifier byte (official GFN xb()). */
export function shiftModifierByte(event: KeyboardEvent, isMacLayout: boolean = isMacKeyboardLayout()): number {
  if (isMacLayout && event.key.length === 1) {
    if ("!@#$%^&*()~_+{}|:\"<>?".includes(event.key)) {
      return 1;
    }
    if ("1234567890`-=[]\\;',./".includes(event.key)) {
      return 0;
    }
  }
  if (event.shiftKey && !event.code.startsWith("Shift")) {
    return 1;
  }
  return 0;
}

/** Per-key modifier byte (official GFN Cb(): ctrl/alt/meta + xb shift). */
export function modifierFlags(event: KeyboardEvent, isMacLayout: boolean = isMacKeyboardLayout()): number {
  let flags = 0;
  if (event.ctrlKey && !event.code.startsWith("Control")) flags |= 0x02;
  if (event.altKey && !event.code.startsWith("Alt")) flags |= 0x04;
  if (event.metaKey && !event.code.startsWith("Meta")) flags |= 0x08;
  flags |= shiftModifierByte(event, isMacLayout);
  return flags;
}

/**
 * Lock-key bitmask for INPUT_LOCK_KEYS_SYNC (official GFN iS() on Windows/desktop).
 * Caps/Num/Scroll are not stuffed into per-key modifier bytes.
 */
export function lockKeysStateFromEvent(event: KeyboardEvent): number {
  let state = 0x10;
  if (event.getModifierState("CapsLock")) state |= 0x01;
  state |= 0x20;
  state |= 0x40;
  if (event.getModifierState("NumLock")) state |= 0x02;
  if (event.getModifierState("ScrollLock")) state |= 0x04;
  return state;
}

export function mapKeyboardEvent(event: KeyboardEvent, _layout?: KeyboardLayout): KeyMapping | null {
  const vk = virtualKeyFromEvent(event);
  if (vk === null || vk === 0) {
    return null;
  }

  // Official GFN Zc() always sends scancode 0; the server uses layout + VK instead.
  return { vk, scancode: 0 };
}
