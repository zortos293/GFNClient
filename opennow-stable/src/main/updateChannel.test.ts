import assert from "node:assert/strict";
import test from "node:test";

import { normalizeUpdateChannel } from "@shared/gfn";
import { applyUpdateChannel, getUpdateChannelPolicy } from "./updateChannel";

test("normalizes persisted update channels to the stable default", () => {
  assert.equal(normalizeUpdateChannel("nightly"), "nightly");
  assert.equal(normalizeUpdateChannel("stable"), "stable");
  assert.equal(normalizeUpdateChannel("beta"), "stable");
  assert.equal(normalizeUpdateChannel(undefined), "stable");
});

test("maps stable and nightly settings to explicit updater policies", () => {
  assert.deepEqual(getUpdateChannelPolicy("stable"), {
    feedChannel: "latest",
    allowPrerelease: false,
  });
  assert.deepEqual(getUpdateChannelPolicy("nightly"), {
    feedChannel: "nightly",
    allowPrerelease: true,
  });
});

test("applying a channel never enables update downgrades", () => {
  const updater = {
    channel: null as string | null,
    allowPrerelease: false,
    allowDowngrade: true,
  };

  applyUpdateChannel(updater, "nightly");
  assert.deepEqual(updater, {
    channel: "nightly",
    allowPrerelease: true,
    allowDowngrade: false,
  });

  applyUpdateChannel(updater, "stable");
  assert.deepEqual(updater, {
    channel: "latest",
    allowPrerelease: false,
    allowDowngrade: false,
  });
});
