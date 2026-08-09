import type { App } from "electron";
import type { GpuBackendInfo } from "@shared/gfn";

export function parseAcceleratedProfiles(profiles: string | undefined): string[] {
  if (!profiles) {
    return [];
  }

  const codecs: string[] = [];
  for (const part of profiles.split(",")) {
    const rawName = part.trim().split(":")[0]?.trim().toUpperCase();
    if (!rawName) {
      continue;
    }
    const name = rawName === "HEVC" ? "H265" : rawName;
    if (!codecs.includes(name)) {
      codecs.push(name);
    }
  }
  return codecs;
}

function featureToBoolean(value: string | undefined): boolean | null {
  if (value === "hardware_accelerated") return true;
  if (value === "disabled" || value === "unavailable" || value === "software_rendering") return false;
  return null;
}

interface RawGpuDevice {
  active?: boolean;
  deviceString?: string;
  vendorString?: string;
  driverVersion?: string;
}

interface RawGpuInfo {
  gpuDevice?: RawGpuDevice[];
  auxAttributes?: {
    videoDecodeAcceleratorSupportedProfiles?: string;
    videoEncodeAcceleratorSupportedProfiles?: string;
  };
}

interface RawGpuFeatureStatus {
  video_decode?: string;
  video_encode?: string;
}

export const EMPTY_GPU_BACKEND_INFO: GpuBackendInfo = {
  gpuName: null,
  vendorName: null,
  driverVersion: null,
  decodeAccelerated: null,
  encodeAccelerated: null,
  hardwareDecodeCodecs: [],
  hardwareEncodeCodecs: [],
};

export function collectGpuBackendInfo(
  rawInfo: unknown,
  rawFeatureStatus: unknown,
): GpuBackendInfo {
  const info = (rawInfo ?? {}) as RawGpuInfo;
  const featureStatus = (rawFeatureStatus ?? {}) as RawGpuFeatureStatus;
  const activeDevice =
    info.gpuDevice?.find((device) => device.active === true) ?? info.gpuDevice?.[0];

  return {
    gpuName: activeDevice?.deviceString ?? null,
    vendorName: activeDevice?.vendorString ?? null,
    driverVersion: activeDevice?.driverVersion ?? null,
    decodeAccelerated: featureToBoolean(featureStatus.video_decode),
    encodeAccelerated: featureToBoolean(featureStatus.video_encode),
    hardwareDecodeCodecs: parseAcceleratedProfiles(
      info.auxAttributes?.videoDecodeAcceleratorSupportedProfiles,
    ),
    hardwareEncodeCodecs: parseAcceleratedProfiles(
      info.auxAttributes?.videoEncodeAcceleratorSupportedProfiles,
    ),
  };
}

let cachedGpuBackendInfo: GpuBackendInfo | null = null;

export async function getGpuBackendInfo(app: App): Promise<GpuBackendInfo> {
  if (cachedGpuBackendInfo) {
    return cachedGpuBackendInfo;
  }

  const [rawInfo, rawFeatureStatus] = await Promise.all([
    app.getGPUInfo("complete"),
    Promise.resolve(app.getGPUFeatureStatus()),
  ]);
  cachedGpuBackendInfo = collectGpuBackendInfo(rawInfo, rawFeatureStatus);
  return cachedGpuBackendInfo;
}

export function resetGpuBackendInfoCache(): void {
  cachedGpuBackendInfo = null;
}
