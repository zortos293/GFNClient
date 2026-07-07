import assert from "node:assert/strict";
import test from "node:test";

import { buildVideoAccelerationCommandLine } from "./videoAcceleration";

test("enables NVIDIA VA-API Chromium flags for Linux desktop hardware decode", () => {
  const commandLine = buildVideoAccelerationCommandLine(
    { decoderPreference: "hardware", encoderPreference: "auto" },
    "linux",
    "x64",
  );

  assert.ok(commandLine.enableFeatures.includes("VaapiVideoDecoder"));
  assert.ok(commandLine.enableFeatures.includes("AcceleratedVideoDecodeLinuxGL"));
  assert.ok(commandLine.enableFeatures.includes("AcceleratedVideoDecodeLinuxZeroCopyGL"));
  assert.ok(commandLine.enableFeatures.includes("VaapiOnNvidiaGPUs"));
  assert.ok(commandLine.enableFeatures.includes("VaapiIgnoreDriverChecks"));
  assert.ok(commandLine.disableFeatures.includes("UseChromeOSDirectVideoDecoder"));
  assert.equal(commandLine.switches["enable-accelerated-video-decode"], true);
});

test("does not enable Linux VA-API decoder flags when software decode is forced", () => {
  const commandLine = buildVideoAccelerationCommandLine(
    { decoderPreference: "software", encoderPreference: "software" },
    "linux",
    "x64",
  );

  assert.equal(commandLine.enableFeatures.includes("VaapiVideoDecoder"), false);
  assert.equal(commandLine.enableFeatures.includes("VaapiOnNvidiaGPUs"), false);
  assert.equal(commandLine.switches["disable-accelerated-video-decode"], true);
  assert.equal(commandLine.switches["disable-accelerated-video-encode"], true);
});

test("enables Linux ARM Chromium V4L2 decoder feature flags", () => {
  const commandLine = buildVideoAccelerationCommandLine(
    { decoderPreference: "hardware", encoderPreference: "auto" },
    "linux",
    "arm64",
  );

  assert.ok(commandLine.enableFeatures.includes("AcceleratedVideoDecoder"));
  assert.ok(commandLine.enableFeatures.includes("AcceleratedVideoDecodeLinuxGL"));
  assert.ok(commandLine.enableFeatures.includes("AcceleratedVideoDecodeLinuxZeroCopyGL"));
  assert.ok(commandLine.enableFeatures.includes("UseChromeOSDirectVideoDecoder"));
  assert.equal(commandLine.enableFeatures.includes("VaapiVideoDecoder"), false);
  assert.equal(commandLine.disableFeatures.includes("UseChromeOSDirectVideoDecoder"), false);
  assert.equal(commandLine.switches["enable-accelerated-video-decode"], true);
});
