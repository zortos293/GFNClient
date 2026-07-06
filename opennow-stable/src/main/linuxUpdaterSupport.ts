import { accessSync, constants, readFileSync } from "node:fs";
import { delimiter, dirname, isAbsolute, join } from "node:path";
import { getLogCapture } from "@shared/logger";

export type LinuxUpdaterPackageKind = "appimage" | "deb" | "native" | "unsupported";

export interface LinuxUpdaterSupport {
  packageKind: LinuxUpdaterPackageKind;
  supported: boolean;
  message?: string;
}

interface LinuxUpdaterSupportOptions {
  platform?: NodeJS.Platform;
  env?: NodeJS.ProcessEnv;
  accessPath?: (path: string, mode: number) => void;
  commandExists?: (command: string, env: NodeJS.ProcessEnv) => boolean;
  canReplaceAppImage?: (appImagePath: string) => boolean;
  readOsRelease?: () => string | null;
}

const UNSUPPORTED_LINUX_UPDATER_MESSAGE =
  "Automatic Linux updates are not available for this install on this system. Download the AppImage from GitHub Releases, or use a Debian/Ubuntu package on a Debian-compatible system with dpkg or apt.";
const READ_ONLY_APPIMAGE_UPDATER_MESSAGE =
  "Automatic Linux updates cannot replace this AppImage because its install folder is not writable by your user. Move the AppImage to a user-writable folder or update through your package manager.";

function hasValue(value: string | undefined): value is string {
  return Boolean(value?.trim());
}

function defaultReadOsRelease(): string | null {
  try {
    return readFileSync("/etc/os-release", "utf8");
  } catch {
    return null;
  }
}

function unquoteOsReleaseValue(value: string): string {
  const trimmed = value.trim();
  if (
    (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

function parseOsRelease(content: string | null): Record<string, string> | null {
  if (!content) {
    return null;
  }

  const entries: Record<string, string> = {};
  for (const line of content.split(/\r?\n/)) {
    const match = line.match(/^([A-Z][A-Z0-9_]*?)=(.*)$/);
    if (match) {
      entries[match[1]] = unquoteOsReleaseValue(match[2]);
    }
  }

  return entries;
}

function isDebianCompatible(osRelease: Record<string, string> | null): boolean {
  if (!osRelease) {
    return true;
  }

  const ids = [osRelease.ID, osRelease.ID_LIKE]
    .filter((value): value is string => Boolean(value))
    .flatMap((value) => value.split(/\s+/))
    .map((value) => value.toLowerCase());

  return ids.some((id) => id === "debian" || id === "ubuntu");
}

export function linuxCommandExists(command: string, env: NodeJS.ProcessEnv = process.env): boolean {
  if (isAbsolute(command)) {
    try {
      accessSync(command, constants.X_OK);
      return true;
    } catch {
      return false;
    }
  }

  for (const directory of (env.PATH ?? "").split(delimiter)) {
    if (!directory) {
      continue;
    }

    try {
      accessSync(join(directory, command), constants.X_OK);
      return true;
    } catch {
      continue;
    }
  }

  return false;
}

function canReplaceAppImage(appImagePath: string, accessPath?: (path: string, mode: number) => void): boolean {
  const appImageDirectory = dirname(appImagePath);
  const checkAccess = accessPath ?? ((path: string, mode: number) => accessSync(path, mode));
  try {
    checkAccess(appImageDirectory, constants.W_OK | constants.X_OK);
    return true;
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    const logCapture = getLogCapture();
    if (logCapture) {
      logCapture.addEntry(
        "debug",
        "AppUpdater",
        "AppImage install directory is not writable:",
        [appImageDirectory, reason],
      );
    } else {
      console.debug("[AppUpdater] AppImage install directory is not writable:", appImageDirectory, reason);
    }
    return false;
  }
}

export function getLinuxUpdaterSupport(options: LinuxUpdaterSupportOptions = {}): LinuxUpdaterSupport {
  const platform = options.platform ?? process.platform;
  if (platform !== "linux") {
    return {
      packageKind: "native",
      supported: true,
    };
  }

  const env = options.env ?? process.env;
  const appImageEnv = env.APPIMAGE;
  if (hasValue(appImageEnv)) {
    const appImagePath = appImageEnv.trim();
    const canReplace = options.canReplaceAppImage ?? ((path: string) => canReplaceAppImage(path, options.accessPath));
    if (!canReplace(appImagePath)) {
      return {
        packageKind: "appimage",
        supported: false,
        message: READ_ONLY_APPIMAGE_UPDATER_MESSAGE,
      };
    }

    return {
      packageKind: "appimage",
      supported: true,
    };
  }

  const commandExists = options.commandExists ?? linuxCommandExists;
  if (
    (commandExists("dpkg", env) || commandExists("apt", env)) &&
    isDebianCompatible(parseOsRelease((options.readOsRelease ?? defaultReadOsRelease)()))
  ) {
    return {
      packageKind: "deb",
      supported: true,
    };
  }

  return {
    packageKind: "unsupported",
    supported: false,
    message: UNSUPPORTED_LINUX_UPDATER_MESSAGE,
  };
}
