import { useCallback, useRef, type InputHTMLAttributes, type JSX, type KeyboardEvent, type PointerEvent } from "react";

export interface SettingRangeProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "type" | "value" | "onChange" | "onInput"
> {
  value: number;
  onPreview: (value: number) => void;
  onCommit: (value: number) => void | Promise<void>;
  normalize?: (value: number) => number;
}

const COMMIT_KEYS = new Set([
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
  "ArrowUp",
  "End",
  "Home",
  "PageDown",
  "PageUp",
]);

export function SettingRange({
  value,
  onPreview,
  onCommit,
  normalize = (nextValue) => nextValue,
  onBlur,
  onKeyUp,
  onPointerCancel,
  onPointerUp,
  ...inputProps
}: SettingRangeProps): JSX.Element {
  const currentValueRef = useRef(value);
  const dirtyRef = useRef(false);
  if (!dirtyRef.current) {
    currentValueRef.current = value;
  }

  const commitPreview = useCallback((): void => {
    if (!dirtyRef.current) return;
    dirtyRef.current = false;
    void onCommit(currentValueRef.current);
  }, [onCommit]);

  return (
    <input
      {...inputProps}
      type="range"
      value={value}
      onChange={(event) => {
        const nextValue = normalize(Number(event.currentTarget.value));
        if (!Number.isFinite(nextValue)) return;
        currentValueRef.current = nextValue;
        dirtyRef.current = true;
        onPreview(nextValue);
      }}
      onBlur={(event) => {
        commitPreview();
        onBlur?.(event);
      }}
      onKeyUp={(event: KeyboardEvent<HTMLInputElement>) => {
        if (COMMIT_KEYS.has(event.key)) {
          commitPreview();
        }
        onKeyUp?.(event);
      }}
      onPointerUp={(event: PointerEvent<HTMLInputElement>) => {
        queueMicrotask(commitPreview);
        onPointerUp?.(event);
      }}
      onPointerCancel={(event: PointerEvent<HTMLInputElement>) => {
        queueMicrotask(commitPreview);
        onPointerCancel?.(event);
      }}
    />
  );
}
