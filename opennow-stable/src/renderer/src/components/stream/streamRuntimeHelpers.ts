import { normalizeShortcut } from "../../shortcuts";

export const RECORDING_MIME_TYPES = [
  "video/mp4;codecs=avc1.42E01E,mp4a.40.2",
  "video/mp4;codecs=avc1",
  "video/mp4",
  "video/webm;codecs=h264",
  "video/webm;codecs=vp8",
  "video/webm",
] as const;

export function getShortcutConflictError(
  rawValue: string,
  reservedShortcuts: readonly (string | undefined)[],
): string | null {
  const trimmed = rawValue.trim();
  if (!trimmed) {
    return "Shortcut cannot be empty.";
  }

  const normalized = normalizeShortcut(trimmed);
  if (!normalized.valid) {
    return "Invalid shortcut format.";
  }

  const reserved = reservedShortcuts
    .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    .map((value) => normalizeShortcut(value))
    .filter((parsed) => parsed.valid)
    .map((parsed) => parsed.canonical);

  return reserved.includes(normalized.canonical)
    ? "Shortcut conflicts with an existing binding."
    : null;
}

export function selectRecordingMimeType(
  isTypeSupported: (mimeType: string) => boolean,
): string {
  return RECORDING_MIME_TYPES.find(isTypeSupported) ?? "video/webm";
}

export interface ThumbnailSize {
  width: number;
  height: number;
}

export function fitThumbnailSize(
  width: number,
  height: number,
  maxWidth = 320,
  maxHeight = 180,
): ThumbnailSize {
  let fittedWidth = width;
  let fittedHeight = height;

  if (fittedWidth > maxWidth) {
    fittedHeight = Math.round((maxWidth / fittedWidth) * fittedHeight);
    fittedWidth = maxWidth;
  }
  if (fittedHeight > maxHeight) {
    fittedWidth = Math.round((maxHeight / fittedHeight) * fittedWidth);
    fittedHeight = maxHeight;
  }

  return { width: fittedWidth, height: fittedHeight };
}
