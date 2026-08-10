import assert from "node:assert/strict";
import test from "node:test";

import {
  describeDecodeBackend,
  describeEncodeBackend,
  getCodecToMigrateToAuto,
  getGpuDriverSubtitle,
  isCodecUsableForStream,
  resolveEffectiveCodec,
  resolveStreamProfileCodec,
  resolveSupportedStreamCodecs,
  shouldShowLinuxHardwareCodecHint,
  shouldShowQuickSyncDriverHint,
  type CodecTestResult,
} from "./codecDiagnostics";
import type { GpuBackendInfo } from "@shared/gfn";

function withNavigator(platform: string, userAgent: string, run: () => void): void {
  const original = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: { platform, userAgent },
  });
  try {
    run();
  } finally {
    if (original) {
      Object.defineProperty(globalThis, "navigator", original);
    } else {
      delete (globalThis as { navigator?: Navigator }).navigator;
    }
  }
}

function codecResult(overrides: Partial<CodecTestResult> = {}): CodecTestResult {
  return {
    codec: "H264",
    webrtcSupported: true,
    decodeSupported: true,
    hwAccelerated: false,
    encodeSupported: false,
    encodeHwAccelerated: false,
    decodeVia: "Software (CPU)",
    encodeVia: "Unsupported",
    profiles: [],
    ...overrides,
  };
}

test("shows Linux hardware hint for software-only codec diagnostics", () => {
  withNavigator("Linux x86_64", "OpenNOW Linux", () => {
    assert.equal(shouldShowLinuxHardwareCodecHint([codecResult()]), true);
  });
});

test("does not show Linux hardware hint for GPU-backed diagnostics", () => {
  withNavigator("Linux x86_64", "OpenNOW Linux", () => {
    assert.equal(shouldShowLinuxHardwareCodecHint([codecResult({ hwAccelerated: true })]), false);
  });
});

test("does not show Linux hardware hint on non-Linux clients", () => {
  withNavigator("Win32", "OpenNOW Windows", () => {
    assert.equal(shouldShowLinuxHardwareCodecHint([codecResult()]), false);
  });
});

test("shows Quick Sync driver hint when H264 encodes on CPU but decodes on GPU (Windows)", () => {
  // The user's exact case: H.264 decode on D3D11 GPU, encode fell back to
  // software — the Intel H.264 encoder MFT is not registered.
  withNavigator("Win32", "OpenNOW Windows", () => {
    const results = [
      codecResult({
        codec: "H264",
        decodeSupported: true,
        hwAccelerated: true,
        encodeSupported: true,
        encodeHwAccelerated: false,
      }),
    ];
    assert.equal(shouldShowQuickSyncDriverHint(results), true);
  });
});

test("does not show Quick Sync hint when H264 encode is also hardware", () => {
  withNavigator("Win32", "OpenNOW Windows", () => {
    const results = [
      codecResult({
        codec: "H264",
        decodeSupported: true,
        hwAccelerated: true,
        encodeSupported: true,
        encodeHwAccelerated: true,
      }),
    ];
    assert.equal(shouldShowQuickSyncDriverHint(results), false);
  });
});

test("does not show Quick Sync hint when H264 decode is not GPU-backed", () => {
  withNavigator("Win32", "OpenNOW Windows", () => {
    const results = [
      codecResult({
        codec: "H264",
        decodeSupported: true,
        hwAccelerated: false,
        encodeSupported: true,
        encodeHwAccelerated: false,
      }),
    ];
    assert.equal(shouldShowQuickSyncDriverHint(results), false);
  });
});

test("decode backend label uses the actual GPU name from the GPU process", () => {
  const gpuInfo: GpuBackendInfo = {
    gpuName: "Intel(R) UHD Graphics",
    vendorName: "Intel",
    driverVersion: "31.0.101",
    decodeAccelerated: true,
    encodeAccelerated: true,
    hardwareDecodeCodecs: ["H264", "H265", "AV1"],
    hardwareEncodeCodecs: ["H265"],
  };
  // Hardware decode → actual GPU model, not the guessed "D3D11".
  assert.equal(describeDecodeBackend(true, gpuInfo), "Intel(R) UHD Graphics (GPU)");
  // Software decode stays honest.
  assert.equal(describeDecodeBackend(false, gpuInfo), "Software (CPU)");
});

test("decode backend falls back to the platform guess when GPU info is missing", () => {
  withNavigator("Win32", "OpenNOW Windows", () => {
    assert.equal(describeDecodeBackend(true, null), "D3D11 (GPU)");
    assert.equal(describeDecodeBackend(false, null), "Software (CPU)");
  });
});

test("encode backend label uses the actual GPU name from the GPU process", () => {
  const gpuInfo: GpuBackendInfo = {
    gpuName: "Intel(R) UHD Graphics",
    vendorName: "Intel",
    driverVersion: "31.0.101",
    decodeAccelerated: true,
    encodeAccelerated: true,
    hardwareDecodeCodecs: ["H264", "H265", "AV1"],
    hardwareEncodeCodecs: ["H265"],
  };
  // Hardware encode → actual GPU model, not the guessed "Media Foundation".
  assert.equal(describeEncodeBackend(true, gpuInfo), "Intel(R) UHD Graphics (GPU)");
  assert.equal(describeEncodeBackend(false, gpuInfo), "Software (CPU)");
});

test("encode backend falls back to the platform guess when GPU info is missing", () => {
  withNavigator("Win32", "OpenNOW Windows", () => {
    assert.equal(describeEncodeBackend(true, null), "Media Foundation (GPU)");
    assert.equal(describeEncodeBackend(false, null), "Software (CPU)");
  });
});

test("does not show Quick Sync hint without an H264 result or on non-Windows", () => {
  // No H264 entry.
  assert.equal(shouldShowQuickSyncDriverHint([
    codecResult({ codec: "AV1" }),
  ]), false);
  // Null / empty results.
  assert.equal(shouldShowQuickSyncDriverHint(null), false);
  assert.equal(shouldShowQuickSyncDriverHint([]), false);
  // Non-Windows client.
  withNavigator("Linux x86_64", "OpenNOW Linux", () => {
    assert.equal(shouldShowQuickSyncDriverHint([
      codecResult({
        codec: "H264",
        decodeSupported: true,
        hwAccelerated: true,
        encodeSupported: true,
        encodeHwAccelerated: false,
      }),
    ]), false);
  });
});

test("supported stream codecs gate AV1 on hardware decode like the official bundle", () => {
  // Software-only AV1 (powerEfficient=false) is excluded, mirroring the
  // official probe Ki(); H265/H264 stay available when decodable.
  assert.deepEqual(resolveSupportedStreamCodecs([
    codecResult({ codec: "H264", decodeSupported: true, webrtcSupported: true }),
    codecResult({ codec: "H265", decodeSupported: true, webrtcSupported: true, hwAccelerated: false }),
    codecResult({ codec: "AV1", decodeSupported: true, webrtcSupported: true, hwAccelerated: false }),
  ]), ["H264", "H265"]);
  // Hardware AV1 decode → AV1 is advertised.
  assert.deepEqual(resolveSupportedStreamCodecs([
    codecResult({ codec: "H264", decodeSupported: true, webrtcSupported: true }),
    codecResult({ codec: "H265", decodeSupported: true, webrtcSupported: true }),
    codecResult({ codec: "AV1", decodeSupported: true, webrtcSupported: true, hwAccelerated: true }),
  ]), ["H264", "H265", "AV1"]);
  // Not decodable (e.g. H265 without the HEVC extension) → excluded.
  assert.deepEqual(resolveSupportedStreamCodecs([
    codecResult({ codec: "H264", decodeSupported: true, webrtcSupported: true }),
    codecResult({ codec: "H265", decodeSupported: false, webrtcSupported: true }),
    codecResult({ codec: "AV1", decodeSupported: false, webrtcSupported: true }),
  ]), ["H264"]);
  // No probe results → sync WebRTC fallback; in the Node test env every check
  // fails so the list floors to H264 (never empty, so ladder resolution stays
  // active in the session request).
  assert.deepEqual(resolveSupportedStreamCodecs(null), ["H264"]);
});

test("explicit codec preference is honored even when the device reports it unsupported", () => {
  // In the Node test env RTCRtpReceiver is undefined, so "auto" falls back to H264.
  assert.equal(resolveEffectiveCodec("H265"), "H265");
  assert.equal(resolveEffectiveCodec("AV1"), "AV1");
});

test("auto codec preference resolves to a usable fallback when capabilities are unknown", () => {
  // No RTCRtpReceiver in Node → every availability check fails → H264 fallback.
  assert.equal(resolveEffectiveCodec("auto"), "H264");
});

test("codec usability requires both decode support and WebRTC receiver support", () => {
  // In WebRTC receiver capabilities but not actually decodable (the GFN-web
  // "Unsupported" case, e.g. H.265 without the HEVC extension).
  assert.equal(isCodecUsableForStream("H265", [
    codecResult({ codec: "H265", webrtcSupported: true, decodeSupported: false }),
  ]), false);
  // Decodable (e.g. AV1 via software dav1d) but absent from the WebRTC
  // receiver capabilities — the exact case that previously left the video
  // m-line rejected when the codec was force-selected.
  assert.equal(isCodecUsableForStream("AV1", [
    codecResult({ codec: "AV1", webrtcSupported: false, decodeSupported: true }),
  ]), false);
  // Fully supported: decodable AND present in WebRTC receiver capabilities.
  assert.equal(isCodecUsableForStream("H265", [
    codecResult({ codec: "H265", webrtcSupported: true, decodeSupported: true }),
  ]), true);
  assert.equal(isCodecUsableForStream("AV1", [
    codecResult({ codec: "AV1", webrtcSupported: true, decodeSupported: true }),
  ]), true);
});

test("saved concrete codec is flagged for auto migration only when unusable", () => {
  // "auto" is never migrated.
  assert.equal(getCodecToMigrateToAuto("auto", [
    codecResult({ codec: "AV1", webrtcSupported: false, decodeSupported: false }),
  ]), null);
  // Fully usable codec → nothing to migrate.
  assert.equal(getCodecToMigrateToAuto("H265", [
    codecResult({ codec: "H265", webrtcSupported: true, decodeSupported: true }),
  ]), null);
  // The GFN-web H.265-without-HEVC case: in receiver caps but not decodable → migrate.
  assert.equal(getCodecToMigrateToAuto("H265", [
    codecResult({ codec: "H265", webrtcSupported: true, decodeSupported: false }),
  ]), "H265");
  // AV1 decodable (e.g. software dav1d) but absent from WebRTC receiver caps → migrate.
  assert.equal(getCodecToMigrateToAuto("AV1", [
    codecResult({ codec: "AV1", webrtcSupported: false, decodeSupported: true }),
  ]), "AV1");
});

test("codec migration waits for diagnostics results before deciding", () => {
  // No result set yet (startup test pending, failed, or cleared) → never
  // migrate, even when the codec might turn out to be unusable.
  assert.equal(getCodecToMigrateToAuto("AV1", null), null);
  assert.equal(getCodecToMigrateToAuto("H265", []), null);
});

test("resolved stream profile pins color quality to the resolved codec", () => {
  // No RTCRtpReceiver in Node → auto resolves to H264 → color pinned to 8-bit.
  assert.deepEqual(resolveStreamProfileCodec("auto", "10bit_420"), {
    codec: "H264",
    colorQuality: "8bit_420",
  });
  // Explicit H265 keeps 10-bit color.
  assert.deepEqual(resolveStreamProfileCodec("H265", "10bit_420"), {
    codec: "H265",
    colorQuality: "10bit_420",
  });
});

test("gpu driver subtitle prefers the vendor name with the driver version", () => {
  const gpuInfo: GpuBackendInfo = {
    gpuName: "Intel(R) UHD Graphics",
    vendorName: "Intel",
    driverVersion: "31.0.101.5336",
    decodeAccelerated: true,
    encodeAccelerated: true,
    hardwareDecodeCodecs: [],
    hardwareEncodeCodecs: [],
  };
  assert.deepEqual(getGpuDriverSubtitle(gpuInfo), {
    name: "Intel",
    version: "31.0.101.5336",
  });
});

test("gpu driver subtitle falls back to the GPU model without a vendor", () => {
  const gpuInfo: GpuBackendInfo = {
    gpuName: "NVIDIA GeForce RTX 3060",
    vendorName: null,
    driverVersion: null,
    decodeAccelerated: true,
    encodeAccelerated: null,
    hardwareDecodeCodecs: ["H264"],
    hardwareEncodeCodecs: [],
  };
  // No driver version → name only, so the panel renders a plain label.
  assert.deepEqual(getGpuDriverSubtitle(gpuInfo), {
    name: "NVIDIA GeForce RTX 3060",
    version: null,
  });
});

test("gpu driver subtitle returns null when no identity is known", () => {
  assert.equal(getGpuDriverSubtitle(null), null);
  const empty: GpuBackendInfo = {
    gpuName: null,
    vendorName: null,
    driverVersion: "",
    decodeAccelerated: null,
    encodeAccelerated: null,
    hardwareDecodeCodecs: [],
    hardwareEncodeCodecs: [],
  };
  assert.equal(getGpuDriverSubtitle(empty), null);
});
