import type {
  NativeGstreamerRuntimeStatus,
  NativeStreamerStatus,
  NativeVideoBackendCapability,
  NativeVideoBackendPreference,
} from "@shared/gfn";
import type { NativeStreamerCapabilities } from "@shared/nativeStreamer";
import { linuxInstallInstructions } from "./runtime";

function formatVideoBackendName(backend: string | undefined): string {
  switch (backend) {
    case "d3d12":
      return "D3D12";
    case "d3d11":
      return "D3D11";
    case "videotoolbox":
      return "VideoToolbox";
    case "vaapi":
      return "VAAPI";
    case "v4l2":
      return "V4L2";
    case "vulkan":
      return "Vulkan";
    case "software":
      return "Software";
    default:
      return backend ?? "Unknown";
  }
}

function formatVideoCodec(codec: string): string {
  switch (codec.toLowerCase()) {
    case "h264":
      return "H.264";
    case "h265":
      return "H.265";
    case "av1":
      return "AV1";
    default:
      return codec.toUpperCase();
  }
}

function capabilityPlatform(platform: NodeJS.Platform): NativeVideoBackendCapability["platform"] | "other" {
  if (platform === "win32") return "windows";
  if (platform === "darwin") return "macos";
  if (platform === "linux") return "linux";
  return "other";
}

export function resolveActiveVideoBackend(
  videoBackends: NativeVideoBackendCapability[],
  preferredBackend: NativeVideoBackendPreference = "auto",
  platform = process.platform,
): NativeVideoBackendCapability | undefined {
  if (preferredBackend !== "auto") {
    const preferred = videoBackends.find((candidate) =>
      candidate.available && candidate.backend === preferredBackend,
    );
    if (preferred) return preferred;
  }

  const currentPlatform = capabilityPlatform(platform);
  return videoBackends.find((candidate) => candidate.available && candidate.platform === currentPlatform)
    ?? videoBackends.find((candidate) => candidate.available && candidate.platform === "cross-platform")
    ?? videoBackends.find((candidate) => candidate.available);
}

function summarizeCodecs(backend: NativeVideoBackendCapability | undefined): string {
  const codecs = backend?.codecs
    .filter((codec) => codec.available)
    .map((codec) => formatVideoCodec(codec.codec)) ?? [];
  return codecs.length > 0 ? codecs.join(", ") : "No hardware codec path";
}

function summarizeZeroCopy(backend: NativeVideoBackendCapability | undefined): string {
  if (!backend) {
    return "Not available";
  }
  return backend.zeroCopyModes.length > 0
    ? `Hardware memory: ${backend.zeroCopyModes.join(", ")}`
    : "System memory";
}

export function formatError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function isWindowsDllLoadFailure(error: unknown, platform: NodeJS.Platform): boolean {
  const message = formatError(error);
  return platform === "win32"
    && (message.includes("3221225781") || message.toLowerCase().includes("0xc0000135"));
}

function formatNativeStreamerDetectionFailure(
  error: unknown,
  runtime: NativeGstreamerRuntimeStatus | null,
  platform: NodeJS.Platform,
): string {
  if (isWindowsDllLoadFailure(error, platform)) {
    return runtime?.bundled
      ? `Native streamer could not load a required DLL even though bundled GStreamer was detected at ${runtime.path}. The packaged runtime may be incomplete or blocked. ${formatError(error)}`
      : `Native streamer could not load a required DLL and no bundled GStreamer runtime was detected. ${formatError(error)}`;
  }
  return `Native streamer was not detected: ${formatError(error)}`;
}

export function createNativeStreamerStatus(
  capabilities: NativeStreamerCapabilities | null,
  runtimeStatus: NativeGstreamerRuntimeStatus | null,
  preferredBackend: NativeVideoBackendPreference,
  platform = process.platform,
): NativeStreamerStatus {
  const backend = capabilities?.backend;
  const gstreamerAvailable = backend === "gstreamer" && capabilities?.supportsOfferAnswer === true;
  const videoBackends = capabilities?.videoBackends ?? [];
  const activeVideoBackend = resolveActiveVideoBackend(videoBackends, preferredBackend, platform);
  const runtime = runtimeStatus ?? {
    source: "unknown",
    bundled: false,
    message: "GStreamer runtime has not been checked yet.",
    installInstructions: linuxInstallInstructions(platform),
  } satisfies NativeGstreamerRuntimeStatus;
  const effectiveRuntime: NativeGstreamerRuntimeStatus = gstreamerAvailable
    ? runtime.bundled
      ? runtime
      : {
        ...runtime,
        source: "system",
        message: "Using system GStreamer runtime; packaged Windows/macOS builds should use the bundled runtime.",
      }
    : {
      ...runtime,
      source: runtime.bundled ? "bundled" : platform === "linux" ? "missing" : runtime.source,
      message: runtime.bundled
        ? "Bundled GStreamer runtime was found, but the GStreamer backend is not ready."
        : platform === "linux"
          ? "GStreamer is not ready. Install distro GStreamer packages so plugins match the host GPU/driver stack."
          : runtime.message,
      installInstructions: runtime.installInstructions ?? linuxInstallInstructions(platform),
    };

  return {
    detected: true,
    gstreamerAvailable,
    supportsOfferAnswer: capabilities?.supportsOfferAnswer === true,
    backend,
    fallbackReason: capabilities?.fallbackReason,
    videoBackends,
    activeVideoBackend,
    codecSummary: summarizeCodecs(activeVideoBackend),
    zeroCopySummary: summarizeZeroCopy(activeVideoBackend),
    gstreamerRuntime: effectiveRuntime,
    message: gstreamerAvailable
      ? `${effectiveRuntime.message} Video path: ${formatVideoBackendName(activeVideoBackend?.backend)}.`
      : capabilities?.fallbackReason ?? effectiveRuntime.message,
  };
}

export function createNativeStreamerDetectionFailureStatus(
  error: unknown,
  runtimeStatus: NativeGstreamerRuntimeStatus | null,
  platform = process.platform,
): NativeStreamerStatus {
  const runtime = runtimeStatus ?? {
    source: platform === "linux" ? "missing" : "unknown",
    bundled: false,
    message: platform === "linux"
      ? "GStreamer is not ready. Linux uses distro packages because private AppImage GStreamer bundling is unreliable across glibc, libdrm/VAAPI/Vulkan, and GPU driver stacks."
      : "GStreamer runtime could not be checked because the native streamer did not start.",
    installInstructions: linuxInstallInstructions(platform),
  } satisfies NativeGstreamerRuntimeStatus;
  return {
    detected: false,
    gstreamerAvailable: false,
    supportsOfferAnswer: false,
    gstreamerRuntime: runtime,
    message: formatNativeStreamerDetectionFailure(error, runtime, platform),
  };
}
