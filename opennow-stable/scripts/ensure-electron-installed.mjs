import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);

const electronPackageJson = require.resolve("electron/package.json");
const electronDir = path.dirname(electronPackageJson);
const electronPackage = require(electronPackageJson);
const electronChecksums = require(path.join(electronDir, "checksums.json"));
const electronInstallScript = path.join(electronDir, "install.js");
const electronPathFile = path.join(electronDir, "path.txt");
const platform = process.env.ELECTRON_INSTALL_PLATFORM ?? process.env.npm_config_platform ?? process.platform;
const arch = process.env.ELECTRON_INSTALL_ARCH ?? process.env.npm_config_arch ?? process.arch;

const electronSkipFlag = process.env.ELECTRON_SKIP_BINARY_DOWNLOAD
  ? "ELECTRON_SKIP_BINARY_DOWNLOAD"
  : process.env.npm_config_electron_skip_binary_download
    ? "npm_config_electron_skip_binary_download"
    : null;
if (electronSkipFlag) {
  console.log(`[ensure-electron-installed] Skipping Electron binary download (${electronSkipFlag} is set).`);
  process.exit(0);
}

function getPlatformPath() {
  switch (platform) {
    case "mas":
    case "darwin":
      return "Electron.app/Contents/MacOS/Electron";
    case "freebsd":
    case "openbsd":
    case "linux":
      return "electron";
    case "win32":
      return "electron.exe";
    default:
      throw new Error(`Electron builds are not available on platform: ${platform}`);
  }
}

function readElectronPath() {
  if (!existsSync(electronPathFile)) {
    return null;
  }

  const electronPath = readFileSync(electronPathFile, "utf8").trim();
  return electronPath || null;
}

function hasElectronBinary() {
  const electronPath = readElectronPath();
  if (!electronPath) {
    return false;
  }

  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH ?? path.join(electronDir, "dist");
  const installedVersionPath = path.join(distRoot, "version");
  if (!existsSync(installedVersionPath)) {
    return false;
  }

  const installedVersion = readFileSync(installedVersionPath, "utf8").trim().replace(/^v/, "");
  if (installedVersion !== electronPackage.version) {
    return false;
  }

  return existsSync(path.join(distRoot, electronPath));
}

if (hasElectronBinary()) {
  process.exit(0);
}

if (!existsSync(electronInstallScript)) {
  console.error(`Electron install script not found: ${electronInstallScript}`);
  process.exit(1);
}

console.log("Installing Electron runtime binary...");

const installResult = spawnSync(process.execPath, [electronInstallScript], {
  stdio: "inherit",
});

if (installResult.status !== 0) {
  process.exit(installResult.status ?? 1);
}

if (!hasElectronBinary()) {
  console.log("Electron package installer did not produce a runtime binary; using archive fallback...");

  const { downloadArtifact } = require("@electron/get");
  const zipPath = await downloadArtifact({
    version: electronPackage.version,
    artifactName: "electron",
    force: process.env.force_no_cache === "true",
    cacheRoot: process.env.electron_config_cache,
    checksums:
      process.env.electron_use_remote_checksums || process.env.npm_config_electron_use_remote_checksums
        ? undefined
        : electronChecksums,
    platform,
    arch,
  });

  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH ?? path.join(electronDir, "dist");
  const resolvedDistRoot = path.resolve(distRoot);
  const resolvedElectronDir = path.resolve(electronDir);

  if (!process.env.ELECTRON_OVERRIDE_DIST_PATH && !resolvedDistRoot.startsWith(resolvedElectronDir)) {
    console.error(`Refusing to rewrite unexpected Electron dist path: ${resolvedDistRoot}`);
    process.exit(1);
  }

  rmSync(resolvedDistRoot, { recursive: true, force: true });
  mkdirSync(resolvedDistRoot, { recursive: true });

  try {
    if (platform === "win32") {
      // Windows ships bsdtar, which extracts Electron's zip reliably even on
      // newer Node releases where extract-zip@2 can leave its promise pending.
      const extractResult = spawnSync("tar.exe", ["-xf", zipPath, "-C", resolvedDistRoot], {
        stdio: "inherit",
      });
      if (extractResult.status !== 0) {
        throw new Error(`Windows archive extraction failed with exit code ${extractResult.status ?? "unknown"}.`);
      }
    } else {
      // Electron 42 publishes extract-zip as a regular dependency. Resolve it
      // from Electron's package boundary so npm hoisting does not affect us.
      const electronRequire = createRequire(electronPackageJson);
      const extractZipModule = await import(pathToFileURL(electronRequire.resolve("extract-zip")).href);
      const extract = extractZipModule.default ?? extractZipModule.extract;
      if (typeof extract !== "function") {
        throw new TypeError("Electron archive extractor did not expose a callable function.");
      }
      await extract(zipPath, { dir: resolvedDistRoot });
    }
  } catch (error) {
    console.error("Failed to extract Electron archive:", error);
    process.exit(1);
  }

  const extractedTypeDef = path.join(resolvedDistRoot, "electron.d.ts");
  if (existsSync(extractedTypeDef)) {
    renameSync(extractedTypeDef, path.join(electronDir, "electron.d.ts"));
  }

  writeFileSync(electronPathFile, getPlatformPath());
}

if (!hasElectronBinary()) {
  console.error("Electron install completed, but the runtime binary is still missing.");
  process.exit(1);
}

console.log(`Electron ${electronPackage.version} runtime is installed for ${platform}-${arch}.`);
