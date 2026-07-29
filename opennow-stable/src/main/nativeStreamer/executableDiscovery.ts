import { join, resolve } from "node:path";

import {
  hasBundledRuntimeNextToExecutable,
  isExistingFile,
  isPathInside,
  nativeStreamerExecutableName,
  nativeStreamerPlatformKey,
} from "./runtime";
import {
  materializePackagedNativeStreamerCache,
  type PackagedNativeStreamerCacheContext,
} from "./runtimeCache";

export interface NativeStreamerExecutableDiscoveryOptions {
  platform: NodeJS.Platform;
  arch: string;
  resourcesPath: string;
  appPath: string;
  mainDir: string;
  isPackaged: boolean;
  envExecutablePath: string | undefined;
  getConfiguredPath(): string;
  cacheContext: PackagedNativeStreamerCacheContext;
}

export function shouldIgnorePackagedExecutableOverride(
  configuredPath: string,
  options: Pick<
    NativeStreamerExecutableDiscoveryOptions,
    "resourcesPath" | "appPath" | "mainDir" | "platform"
  >,
): boolean {
  if (hasBundledRuntimeNextToExecutable(configuredPath)) {
    return false;
  }

  const packagedRoots = [
    join(options.resourcesPath, "native", "opennow-streamer"),
    resolve(options.appPath, "../native/opennow-streamer"),
    resolve(options.mainDir, "../../../dist-release/win-unpacked/resources/native/opennow-streamer"),
    resolve(options.mainDir, "../../../dist-release/win-unpacked/resources/app.asar.unpacked/native/opennow-streamer"),
  ];

  return packagedRoots.some((root) => isPathInside(root, configuredPath, options.platform));
}

export function resolveNativeStreamerExecutableCandidates(
  options: NativeStreamerExecutableDiscoveryOptions,
): string[] {
  const exeName = nativeStreamerExecutableName(options.platform);
  const platformKey = nativeStreamerPlatformKey(options.platform, options.arch);
  const bundledCandidates = [
    join(options.resourcesPath, "native", "opennow-streamer", platformKey, exeName),
    join(options.resourcesPath, "native", "opennow-streamer", exeName),
  ];
  const candidates: string[] = [];
  const addCandidate = (candidate: string | undefined): void => {
    if (!candidate || !isExistingFile(candidate) || candidates.includes(candidate)) {
      return;
    }
    candidates.push(candidate);
  };

  if (options.isPackaged) {
    for (const candidate of bundledCandidates) {
      if (!isExistingFile(candidate) || !hasBundledRuntimeNextToExecutable(candidate)) {
        continue;
      }
      addCandidate(
        materializePackagedNativeStreamerCache(
          candidate,
          platformKey,
          exeName,
          options.cacheContext,
        ) ?? undefined,
      );
    }
  }
  bundledCandidates.forEach(addCandidate);
  if (options.isPackaged && candidates.length > 0) {
    const packagedBundledCandidates = candidates.filter((candidate) =>
      hasBundledRuntimeNextToExecutable(candidate),
    );
    return packagedBundledCandidates.length > 0 ? packagedBundledCandidates : candidates;
  }

  const configuredPath = options.getConfiguredPath().trim();
  if (configuredPath) {
    if (isExistingFile(configuredPath)) {
      if (!shouldIgnorePackagedExecutableOverride(configuredPath, options)) {
        addCandidate(configuredPath);
      } else {
        console.warn(
          "[NativeStreamer] Ignoring packaged executable override without bundled runtime:",
          configuredPath,
        );
      }
    } else {
      throw new Error(`Configured native streamer executable was not found: ${configuredPath}`);
    }
  }

  [
    options.envExecutablePath,
    ...bundledCandidates,
    resolve(options.mainDir, "../../../native/opennow-streamer/bin", platformKey, exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/bin", exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/dist", platformKey, exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/dist", exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/target/release", platformKey, exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/target/release", exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/target/debug", platformKey, exeName),
    resolve(options.mainDir, "../../../native/opennow-streamer/target/debug", exeName),
    resolve(options.appPath, "../native/opennow-streamer/bin", platformKey, exeName),
    resolve(options.appPath, "../native/opennow-streamer/bin", exeName),
    resolve(options.appPath, "../native/opennow-streamer/dist", platformKey, exeName),
    resolve(options.appPath, "../native/opennow-streamer/dist", exeName),
    resolve(options.appPath, "../native/opennow-streamer/target/release", platformKey, exeName),
    resolve(options.appPath, "../native/opennow-streamer/target/release", exeName),
    resolve(options.appPath, "../native/opennow-streamer/target/debug", platformKey, exeName),
    resolve(options.appPath, "../native/opennow-streamer/target/debug", exeName),
  ]
    .filter((candidate): candidate is string => Boolean(candidate))
    .forEach(addCandidate);

  if (candidates.length > 0) {
    return candidates;
  }

  throw new Error(`Native streamer binary not found. Checked: ${candidates.join(", ")}`);
}
