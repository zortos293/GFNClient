/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  fitThumbnailSize,
  getShortcutConflictError,
  selectRecordingMimeType,
} from "./streamRuntimeHelpers";

test("shortcut conflict validation preserves empty, invalid, and conflict errors", () => {
  assert.equal(getShortcutConflictError("", []), "Shortcut cannot be empty.");
  assert.equal(getShortcutConflictError("Ctrl+UnknownNamedKey", []), "Invalid shortcut format.");
  assert.equal(
    getShortcutConflictError("shift+ctrl+s", ["Ctrl+Shift+S"]),
    "Shortcut conflicts with an existing binding.",
  );
  assert.equal(getShortcutConflictError("Ctrl+Shift+S", ["Ctrl+R", undefined]), null);
});

test("recording MIME selection uses the first supported preference", () => {
  assert.equal(
    selectRecordingMimeType((mimeType) => mimeType === "video/webm;codecs=h264"),
    "video/webm;codecs=h264",
  );
  assert.equal(selectRecordingMimeType(() => false), "video/webm");
});

test("thumbnail sizing preserves aspect ratio within recording bounds", () => {
  assert.deepEqual(fitThumbnailSize(1920, 1080), { width: 320, height: 180 });
  assert.deepEqual(fitThumbnailSize(1024, 768), { width: 240, height: 180 });
  assert.deepEqual(fitThumbnailSize(1080, 1920), { width: 101, height: 180 });
  assert.deepEqual(fitThumbnailSize(160, 90), { width: 160, height: 90 });
});
