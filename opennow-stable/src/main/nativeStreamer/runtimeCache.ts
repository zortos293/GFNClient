import { createHash } from "node:crypto";
import {
  cpSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";

import {
  hasBundledRuntimeNextToExecutable,
  isExistingDirectory,
  isExistingFile,
  isPathInside,
} from "./runtime";

export interface PackagedNativeStreamerCacheMarker {
  appVersion: string;
  platformKey: string;
  exeName: string;
  exeSha256: string;
  bundledRuntime: boolean;
  runtimeManifestSha256?: string;
}

export interface PackagedNativeStreamerCacheContext {
  appVersion: string;
  isPackaged: boolean;
  platform: NodeJS.Platform;
  resourcesPath: string;
  tempDirectory: string;
  userDataPath: string;
}

function safePathSegment(value: string): string {
  return value.replace(/[^a-zA-Z0-9._-]/g, "_") || "unknown";
}

function fileSha256(path: string): string {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

export function shouldUseStablePackagedNativeStreamerCache(
  context: Pick<
    PackagedNativeStreamerCacheContext,
    "isPackaged" | "platform" | "resourcesPath" | "tempDirectory"
  >,
): boolean {
  return context.isPackaged
    && context.platform === "win32"
    && isPathInside(context.tempDirectory, context.resourcesPath, context.platform);
}

export function buildPackagedNativeStreamerCacheMarker(
  sourceDirectory: string,
  exeName: string,
  platformKey: string,
  appVersion: string,
): PackagedNativeStreamerCacheMarker {
  const runtimeManifest = join(sourceDirectory, "gstreamer", "OPENNOW-GSTREAMER-RUNTIME.txt");
  return {
    appVersion,
    platformKey,
    exeName,
    exeSha256: fileSha256(join(sourceDirectory, exeName)),
    bundledRuntime: isExistingDirectory(join(sourceDirectory, "gstreamer")),
    runtimeManifestSha256: isExistingFile(runtimeManifest) ? fileSha256(runtimeManifest) : undefined,
  };
}

export function readPackagedNativeStreamerCacheMarker(
  markerPath: string,
): PackagedNativeStreamerCacheMarker | null {
  try {
    return JSON.parse(readFileSync(markerPath, "utf8")) as PackagedNativeStreamerCacheMarker;
  } catch {
    return null;
  }
}

export function isSamePackagedNativeStreamerCacheMarker(
  left: PackagedNativeStreamerCacheMarker | null,
  right: PackagedNativeStreamerCacheMarker,
): boolean {
  if (!left) {
    return false;
  }

  return left.appVersion === right.appVersion
    && left.platformKey === right.platformKey
    && left.exeName === right.exeName
    && left.exeSha256 === right.exeSha256
    && left.bundledRuntime === right.bundledRuntime
    && left.runtimeManifestSha256 === right.runtimeManifestSha256;
}

export function materializePackagedNativeStreamerCache(
  sourceExecutablePath: string,
  platformKey: string,
  exeName: string,
  context: PackagedNativeStreamerCacheContext,
): string | null {
  if (!shouldUseStablePackagedNativeStreamerCache(context)) {
    return null;
  }

  const sourceDirectory = dirname(sourceExecutablePath);
  const cacheDirectory = join(
    context.userDataPath,
    "native-streamer",
    "runtime",
    safePathSegment(context.appVersion),
    safePathSegment(platformKey),
  );
  const cachedExecutablePath = join(cacheDirectory, exeName);
  const markerPath = join(cacheDirectory, ".opennow-native-runtime.json");
  let stagingDirectory: string | null = null;

  try {
    const expectedMarker = buildPackagedNativeStreamerCacheMarker(
      sourceDirectory,
      exeName,
      platformKey,
      context.appVersion,
    );
    const cachedMarker = readPackagedNativeStreamerCacheMarker(markerPath);
    if (
      isExistingFile(cachedExecutablePath)
      && isSamePackagedNativeStreamerCacheMarker(cachedMarker, expectedMarker)
      && (!expectedMarker.bundledRuntime || hasBundledRuntimeNextToExecutable(cachedExecutablePath))
    ) {
      return cachedExecutablePath;
    }

    stagingDirectory = `${cacheDirectory}.tmp-${process.pid}-${Date.now()}`;
    rmSync(stagingDirectory, { recursive: true, force: true });
    mkdirSync(dirname(stagingDirectory), { recursive: true });
    cpSync(sourceDirectory, stagingDirectory, {
      recursive: true,
      force: true,
      dereference: true,
      filter: (entry) => {
        const lower = entry.toLowerCase();
        return !lower.endsWith(".pdb") && !lower.endsWith(".lib") && !lower.endsWith(".a");
      },
    });
    writeFileSync(
      join(stagingDirectory, ".opennow-native-runtime.json"),
      `${JSON.stringify(expectedMarker, null, 2)}\n`,
      "utf8",
    );

    if (!isExistingFile(join(stagingDirectory, exeName))) {
      throw new Error(`Cached native streamer executable was not created: ${join(stagingDirectory, exeName)}`);
    }
    if (expectedMarker.bundledRuntime && !hasBundledRuntimeNextToExecutable(join(stagingDirectory, exeName))) {
      throw new Error("Cached native streamer runtime is missing its bundled GStreamer directory.");
    }

    rmSync(cacheDirectory, { recursive: true, force: true });
    renameSync(stagingDirectory, cacheDirectory);
    stagingDirectory = null;
    console.log("[NativeStreamer] Cached packaged native streamer in stable runtime path:", cachedExecutablePath);
    return cachedExecutablePath;
  } catch (error) {
    console.warn("[NativeStreamer] Failed to prepare stable packaged runtime cache; using packaged resource path:", error);
    return null;
  } finally {
    if (stagingDirectory) {
      rmSync(stagingDirectory, { recursive: true, force: true });
    }
  }
}
