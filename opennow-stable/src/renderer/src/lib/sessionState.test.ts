/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { isStreamVideoReady, toLaunchErrorState } from "./sessionState";

const translations: Record<string, string> = {
  "errors.duplicateSessionTitle": "Duplicate Session Detected",
  "errors.duplicateSessionDescription": "Another session is already running.",
  "errors.insufficientPlayabilityTitle": "Membership Upgrade Required",
  "errors.insufficientPlayabilityDescription": "Your current GeForce NOW membership is not high enough to play this game. Upgrade to a higher tier and try again.",
  "errors.insufficientPlayabilityTierDescription": "This game requires {{tier}} on GeForce NOW. Upgrade your membership to play it.",
  "errors.launchFailedTitle": "Launch Failed",
  "errors.launchUnknown": "The game could not start. Please try again.",
  "errors.userStorageUnavailableTitle": "Persistent Storage Unavailable",
  "errors.userStorageUnavailableDescription": "NVIDIA reported that Persistent Storage is unavailable for this account or storage location. Open Settings → Account → Persistent Storage, then use NVIDIA Storage Manager to reset or move the storage location. If it still fails, check NVIDIA server status.",
  "errors.userStorageUnavailableAction": "Open Storage Settings",
};

function t(key: string, values: Record<string, string | number | boolean | null | undefined> = {}): string {
  const template = translations[key] ?? key;
  return template.replace(/\{\{\s*(\w+)\s*\}\}/g, (_match, token: string) => String(values[token] ?? ""));
}

test("launch error state treats insufficient playability as a membership upgrade problem", () => {
  const state = toLaunchErrorState(t, {
    gfnErrorCode: 3237093718,
    title: "Playability Level Issue",
    description: "Your account's playability level is insufficient.",
  }, "queue");

  assert.equal(state.title, "Membership Upgrade Required");
  assert.match(state.description, /GeForce NOW membership is not high enough/i);
  assert.equal(state.codeLabel, "SessionInsufficientPlayabilityLevel (3237093718)");
});

test("launch error state prefers game catalog unplayable copy when available", () => {
  const state = toLaunchErrorState(t, { gfnErrorCode: 3237093718 }, "setup", {
    title: "Cyberpunk 2077",
    membershipTierLabel: "Ultimate",
    catalogSkuStrings: {
      SKU_BASED_UNPLAYABLE_DIALOG_HEADER: "Ultimate membership required",
      SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE: "Upgrade to {{SKU}} to play this game.",
    },
  });

  assert.equal(state.title, "Ultimate membership required");
  assert.equal(state.description, "Upgrade to Ultimate membership required to play this game.");
});

test("launch error state still treats session limit as duplicate session", () => {
  const state = toLaunchErrorState(t, { gfnErrorCode: 3237093643 }, "queue");

  assert.equal(state.title, "Duplicate Session Detected");
  assert.equal(state.description, "Another session is already running.");
});

test("launch error state treats NVIDIA user storage failures as persistent storage recovery", () => {
  const state = toLaunchErrorState(t, {
    gfnErrorCode: 3237093721,
    statusCode: 89,
    statusDescription: "USER_STORAGE_NOT_AVAILABLE CA8C3011",
    title: "Storage Unavailable",
    description: "User storage is not available.",
  }, "setup");

  assert.equal(state.title, "Persistent Storage Unavailable");
  assert.match(state.description, /NVIDIA Storage Manager/i);
  assert.equal(state.codeLabel, "UserStorageNotAvailable (3237093721)");
  assert.equal(state.action, "persistent-storage-settings");
  assert.equal(state.actionLabel, "Open Storage Settings");
});

test("stream video stays covered until the session is streaming and a frame source is ready", () => {
  assert.equal(isStreamVideoReady("connecting", true, true), false);
  assert.equal(isStreamVideoReady("streaming", false, false), false);
  assert.equal(isStreamVideoReady("streaming", true, false), true);
  assert.equal(isStreamVideoReady("streaming", false, true), true);
});
