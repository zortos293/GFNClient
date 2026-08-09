import assert from "node:assert/strict";
import test from "node:test";

import {
  collectGpuBackendInfo,
  EMPTY_GPU_BACKEND_INFO,
  parseAcceleratedProfiles,
} from "./gpuInfo";

test("parses accelerated profiles into codec names", () => {
  assert.deepEqual(
    parseAcceleratedProfiles(
      "H264: 16x16 to 4096x4096 pixels, HEVC: 16x16 to 8192x4352 pixels, AV1: 16x16 to 8192x4352 pixels",
    ),
    ["H264", "H265", "AV1"],
  );
});

test("maps HEVC to H265 and deduplicates", () => {
  assert.deepEqual(
    parseAcceleratedProfiles("HEVC: 16x16 to 8192x4352 pixels, HEVC: 16x16 to 8192x4352 pixels"),
    ["H265"],
  );
});

test("returns empty array for missing or blank profiles", () => {
  assert.deepEqual(parseAcceleratedProfiles(undefined), []);
  assert.deepEqual(parseAcceleratedProfiles(""), []);
});

test("collects active GPU device into backend info", () => {
  const info = collectGpuBackendInfo(
    {
      gpuDevice: [
        { active: false, deviceString: "Old GPU", vendorString: "AMD", driverVersion: "1" },
        { active: true, deviceString: "Intel(R) UHD Graphics", vendorString: "Intel", driverVersion: "31.0.101" },
      ],
      auxAttributes: {
        videoDecodeAcceleratorSupportedProfiles: "H264: ..., HEVC: ...",
        videoEncodeAcceleratorSupportedProfiles: "H264: ...",
      },
    },
    { video_decode: "hardware_accelerated", video_encode: "software_rendering" },
  );

  assert.equal(info.gpuName, "Intel(R) UHD Graphics");
  assert.equal(info.vendorName, "Intel");
  assert.equal(info.driverVersion, "31.0.101");
  assert.equal(info.decodeAccelerated, true);
  assert.equal(info.encodeAccelerated, false);
  assert.deepEqual(info.hardwareDecodeCodecs, ["H264", "H265"]);
  assert.deepEqual(info.hardwareEncodeCodecs, ["H264"]);
});

test("falls back to first GPU device when none is marked active", () => {
  const info = collectGpuBackendInfo(
    {
      gpuDevice: [{ deviceString: "NVIDIA GeForce RTX 3060", vendorString: "NVIDIA" }],
    },
    {},
  );

  assert.equal(info.gpuName, "NVIDIA GeForce RTX 3060");
  assert.equal(info.vendorName, "NVIDIA");
  assert.equal(info.decodeAccelerated, null);
  assert.equal(info.encodeAccelerated, null);
});

test("returns empty info for missing GPU data", () => {
  assert.deepEqual(collectGpuBackendInfo(null, null), EMPTY_GPU_BACKEND_INFO);
  assert.deepEqual(collectGpuBackendInfo({}, {}), EMPTY_GPU_BACKEND_INFO);
});
