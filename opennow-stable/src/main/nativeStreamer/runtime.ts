import {
  existsSync,
  mkdirSync,
  realpathSync,
  statSync,
} from "node:fs";
import { delimiter, dirname, join, resolve, sep } from "node:path";

import {
  nativeStreamerFeatureModeToEnvValue,
  type NativeGstreamerInstallInstruction,
  type NativeGstreamerRuntimeStatus,
  type NativeStreamerBackendPreference,
  type NativeStreamerFeatureMode,
  type NativeVideoBackendPreference,
} from "@shared/gfn";

export interface NativeStreamerRuntimeEnvironmentOptions {
  executablePath: string;
  baseEnv: NodeJS.ProcessEnv;
  platform: NodeJS.Platform;
  arch: string;
  userDataPath: string;
  protocolVersion: number;
  backendPreference: NativeStreamerBackendPreference;
  videoBackendPreference: NativeVideoBackendPreference;
  externalRendererEnabled: boolean;
  cloudGsyncMode: NativeStreamerFeatureMode;
  d3dFullscreenMode: NativeStreamerFeatureMode;
}

export interface NativeStreamerRuntimeEnvironment {
  env: NodeJS.ProcessEnv;
  runtimeStatus: NativeGstreamerRuntimeStatus;
}

const LINUX_GSTREAMER_INSTALL_INSTRUCTIONS: NativeGstreamerInstallInstruction[] = [
  {
    distro: "Debian / Ubuntu / Mint / Pop!_OS / KDE neon",
    command: "sudo apt update && sudo apt install libgstreamer1.0-0 libgstreamer-plugins-base1.0-0 gstreamer1.0-tools gstreamer1.0-libav gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-nice gstreamer1.0-gl gstreamer1.0-vaapi gstreamer1.0-x gstreamer1.0-alsa libva2 libva-drm2 libvulkan1 mesa-vulkan-drivers",
  },
  {
    distro: "Fedora / RHEL / Nobara / Bazzite",
    command: "sudo dnf install gstreamer1 gstreamer1-plugins-base gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-bad-freeworld gstreamer1-plugins-ugly gstreamer1-libav gstreamer1-vaapi gstreamer1-plugin-openh264 libnice-gstreamer1 mesa-vulkan-drivers libva",
    note: "RPM Fusion may be required for libav, ugly, or bad-freeworld packages.",
  },
  {
    distro: "Arch / Manjaro / EndeavourOS / SteamOS",
    command: "sudo pacman -S --needed gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugins-ugly gst-libav gst-plugin-va libnice libva mesa vulkan-radeon",
    note: "NVIDIA users should use their distro NVIDIA/Vulkan driver packages instead of vulkan-radeon.",
  },
  {
    distro: "openSUSE Tumbleweed / Leap",
    command: "sudo zypper install gstreamer gstreamer-plugins-base gstreamer-plugins-good gstreamer-plugins-bad gstreamer-plugins-ugly gstreamer-plugins-libav gstreamer-plugins-vaapi gstreamer-libnice libva2 Mesa-vulkan-device-select",
  },
];

export function nativeStreamerExecutableName(platform = process.platform): string {
  return platform === "win32" ? "opennow-streamer.exe" : "opennow-streamer";
}

export function nativeStreamerPlatformKey(
  platform: NodeJS.Platform = process.platform,
  arch: string = process.arch,
): string {
  return `${platform}-${arch}`;
}

export function isExistingFile(path: string): boolean {
  try {
    return existsSync(path) && statSync(path).isFile();
  } catch {
    return false;
  }
}

export function isExistingDirectory(path: string): boolean {
  try {
    return existsSync(path) && statSync(path).isDirectory();
  } catch {
    return false;
  }
}

export function normalizePathForComparison(
  path: string,
  platform = process.platform,
): string {
  let resolvedPath = resolve(path);
  try {
    resolvedPath = realpathSync.native(resolvedPath);
  } catch {
    // Cache destinations and configured overrides may not exist yet.
  }
  return platform === "win32" ? resolvedPath.toLowerCase() : resolvedPath;
}

export function isPathInside(
  parent: string,
  child: string,
  platform = process.platform,
): boolean {
  const normalizedParent = normalizePathForComparison(parent, platform);
  const normalizedChild = normalizePathForComparison(child, platform);
  return normalizedChild === normalizedParent || normalizedChild.startsWith(`${normalizedParent}${sep}`);
}

export function hasBundledRuntimeNextToExecutable(executablePath: string): boolean {
  return isExistingDirectory(join(dirname(executablePath), "gstreamer"));
}

export function linuxInstallInstructions(
  platform = process.platform,
): NativeGstreamerInstallInstruction[] | undefined {
  return platform === "linux" ? LINUX_GSTREAMER_INSTALL_INSTRUCTIONS : undefined;
}

function prependEnvPath(env: NodeJS.ProcessEnv, key: string, directory: string): void {
  env[key] = env[key] ? `${directory}${delimiter}${env[key]}` : directory;
}

function prependProcessPath(env: NodeJS.ProcessEnv, directory: string): void {
  const pathKey = Object.keys(env).find((key) => key.toLowerCase() === "path") || "PATH";
  prependEnvPath(env, pathKey, directory);
}

function configureBundledGstreamerRuntime(
  env: NodeJS.ProcessEnv,
  executablePath: string,
  platform: NodeJS.Platform,
  arch: string,
  userDataPath: string,
): NativeGstreamerRuntimeStatus {
  const runtimeRoot = join(dirname(executablePath), "gstreamer");
  if (!isExistingDirectory(runtimeRoot)) {
    return {
      source: "system",
      bundled: false,
      message: platform === "linux"
        ? "No bundled GStreamer runtime was found. Linux uses distro GStreamer packages so VAAPI/V4L2/Vulkan plugins match the host driver stack."
        : "No bundled GStreamer runtime was found; using the system runtime if available.",
      installInstructions: linuxInstallInstructions(platform),
    };
  }

  const binDir = join(runtimeRoot, "bin");
  const libDir = join(runtimeRoot, "lib");
  const pluginDir = join(runtimeRoot, "lib", "gstreamer-1.0");
  const scanner = join(
    runtimeRoot,
    "libexec",
    "gstreamer-1.0",
    platform === "win32" ? "gst-plugin-scanner.exe" : "gst-plugin-scanner",
  );
  const gioModulesDir = join(runtimeRoot, "lib", "gio", "modules");

  if (platform === "win32") prependProcessPath(env, dirname(executablePath));
  if (isExistingDirectory(binDir)) prependProcessPath(env, binDir);
  if (isExistingDirectory(pluginDir)) {
    env.GST_PLUGIN_PATH = pluginDir;
    env.GST_PLUGIN_PATH_1_0 = pluginDir;
    env.GST_PLUGIN_SYSTEM_PATH = pluginDir;
    env.GST_PLUGIN_SYSTEM_PATH_1_0 = pluginDir;
  }
  if (isExistingFile(scanner)) {
    env.GST_PLUGIN_SCANNER = scanner;
    env.GST_PLUGIN_SCANNER_1_0 = scanner;
  }
  env.GST_REGISTRY_REUSE_PLUGIN_SCANNER = "no";
  if (isExistingDirectory(gioModulesDir)) {
    env.GIO_MODULE_DIR = gioModulesDir;
    env.GIO_EXTRA_MODULES = gioModulesDir;
  }
  const registryDir = join(userDataPath, "native-streamer", "gstreamer");
  const registryPath = join(registryDir, `${nativeStreamerPlatformKey(platform, arch)}-registry.bin`);
  mkdirSync(registryDir, { recursive: true });
  env.GST_REGISTRY = registryPath;
  if (platform === "linux") {
    if (isExistingDirectory(libDir)) prependEnvPath(env, "LD_LIBRARY_PATH", libDir);
    if (isExistingDirectory(binDir)) prependEnvPath(env, "LD_LIBRARY_PATH", binDir);
  }
  if (platform === "darwin") {
    if (isExistingDirectory(libDir)) {
      prependEnvPath(env, "DYLD_LIBRARY_PATH", libDir);
      prependEnvPath(env, "DYLD_FALLBACK_LIBRARY_PATH", libDir);
    }
    if (isExistingDirectory(binDir)) {
      prependEnvPath(env, "DYLD_LIBRARY_PATH", binDir);
      prependEnvPath(env, "DYLD_FALLBACK_LIBRARY_PATH", binDir);
    }
  }

  return {
    source: "bundled",
    bundled: true,
    path: runtimeRoot,
    message: "Using bundled GStreamer runtime next to the native streamer executable.",
  };
}

export function createNativeStreamerRuntimeEnvironment(
  options: NativeStreamerRuntimeEnvironmentOptions,
): NativeStreamerRuntimeEnvironment {
  const env: NodeJS.ProcessEnv = {
    ...options.baseEnv,
    OPENNOW_NATIVE_STREAMER_PROTOCOL: String(options.protocolVersion),
  };
  delete env.OPENNOW_NATIVE_VIDEO_API;
  delete env.OPENNOW_NATIVE_VIDEO_BACKEND;
  if (options.videoBackendPreference !== "auto") {
    env.OPENNOW_NATIVE_VIDEO_BACKEND = options.videoBackendPreference;
  }
  if (options.platform === "linux") {
    env.OPENNOW_NATIVE_EXTERNAL_RENDERER = "0";
    if ((options.arch === "arm64" || options.arch === "arm") && !env.GST_V4L2_ENABLE_PROBE) {
      env.GST_V4L2_ENABLE_PROBE = "1";
    }
  } else if (options.platform === "win32") {
    env.OPENNOW_NATIVE_EXTERNAL_RENDERER = options.externalRendererEnabled ? "1" : "0";
    env.OPENNOW_NATIVE_D3D_ALLOW_TEARING = "1";
  }
  env.OPENNOW_NATIVE_CLOUD_GSYNC = nativeStreamerFeatureModeToEnvValue(options.cloudGsyncMode);
  env.OPENNOW_NATIVE_D3D_FULLSCREEN = nativeStreamerFeatureModeToEnvValue(options.d3dFullscreenMode);
  if (options.backendPreference !== "auto") {
    env.OPENNOW_NATIVE_STREAMER_BACKEND = options.backendPreference;
  }

  return {
    env,
    runtimeStatus: configureBundledGstreamerRuntime(
      env,
      options.executablePath,
      options.platform,
      options.arch,
      options.userDataPath,
    ),
  };
}
