import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const packageRoot = resolve(__dirname, "..");
const repoRoot = resolve(packageRoot, "..");
const vendorRoot = join(repoRoot, "native", "opennow-streamer", "vendor", "gstreamer-vulkan-windows");

function parseArgs(argv) {
  const parsed = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (!value.startsWith("--")) continue;
    const key = value.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith("--")) {
      parsed.set(key, "true");
      continue;
    }
    parsed.set(key, next);
    index += 1;
  }
  return parsed;
}

function isExistingFile(path) {
  try {
    return existsSync(path) && statSync(path).isFile();
  } catch {
    return false;
  }
}

function isExistingDirectory(path) {
  try {
    return existsSync(path) && statSync(path).isDirectory();
  } catch {
    return false;
  }
}

function readRuntimeVersion(runtimeRoot) {
  const metadataPath = join(runtimeRoot, "OPENNOW-GSTREAMER-RUNTIME.txt");
  if (!isExistingFile(metadataPath)) return null;
  const text = readFileSync(metadataPath, "utf8");
  const sourceLine = text.split(/\r?\n/).find((line) => line.startsWith("Source:"));
  // Prefer probing the bundled gst-inspect version when available.
  const inspect = join(runtimeRoot, "bin", "gst-inspect-1.0.exe");
  if (isExistingFile(inspect)) {
    const result = spawnSync(inspect, ["--version"], { encoding: "utf8" });
    const match = `${result.stdout ?? ""}\n${result.stderr ?? ""}`.match(/GStreamer\s+(\d+\.\d+\.\d+)/i);
    if (match) return match[1];
  }
  return sourceLine ? sourceLine.slice("Source:".length).trim() : null;
}

function listVendorVersions() {
  if (!isExistingDirectory(vendorRoot)) return [];
  return readdirSync(vendorRoot)
    .filter((name) => /^\d+\.\d+\.\d+$/.test(name) && isExistingDirectory(join(vendorRoot, name)))
    .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
}

function resolveVendorDir(runtimeVersion) {
  const versions = listVendorVersions();
  if (versions.length === 0) return null;
  if (runtimeVersion && versions.includes(runtimeVersion)) {
    return join(vendorRoot, runtimeVersion);
  }
  // Fall back to newest vendored build; plugin ABI is usually compatible within a minor series.
  if (runtimeVersion) {
    const [major, minor] = runtimeVersion.split(".");
    const sameSeries = versions.find((version) => version.startsWith(`${major}.${minor}.`));
    if (sameSeries) return join(vendorRoot, sameSeries);
    return null;
  }
  return join(vendorRoot, versions[0]);
}

function injectVulkanPlugins(runtimeRoot, vendorDir) {
  const pluginSource = join(vendorDir, "lib", "gstreamer-1.0", "gstvulkan.dll");
  const librarySource = join(vendorDir, "bin", "gstvulkan-1.0-0.dll");
  if (!isExistingFile(pluginSource) || !isExistingFile(librarySource)) {
    throw new Error(`Vendored Vulkan artifacts are incomplete under ${vendorDir}`);
  }

  const pluginDestDir = join(runtimeRoot, "lib", "gstreamer-1.0");
  const binDestDir = join(runtimeRoot, "bin");
  mkdirSync(pluginDestDir, { recursive: true });
  mkdirSync(binDestDir, { recursive: true });
  copyFileSync(pluginSource, join(pluginDestDir, "gstvulkan.dll"));
  copyFileSync(librarySource, join(binDestDir, "gstvulkan-1.0-0.dll"));

  const metadataPath = join(runtimeRoot, "OPENNOW-GSTREAMER-RUNTIME.txt");
  if (isExistingFile(metadataPath)) {
    const text = readFileSync(metadataPath, "utf8");
    if (!text.includes("Vulkan plugin: injected")) {
      writeFileSync(
        metadataPath,
        `${text.trimEnd()}\nVulkan plugin: injected from ${vendorDir}\n`,
        "utf8",
      );
    }
  }

  console.log(`Injected Windows GStreamer Vulkan plugins from ${vendorDir} into ${runtimeRoot}.`);
}

const args = parseArgs(process.argv.slice(2));
const destination = args.get("dest");
if (!destination) {
  console.error("Usage: node scripts/inject-gstreamer-vulkan-windows.mjs --dest <runtime-dir>");
  process.exit(1);
}

if (process.platform !== "win32") {
  console.log("Skipping Windows Vulkan plugin inject on non-Windows host.");
  process.exit(0);
}

try {
  const runtimeRoot = resolve(packageRoot, destination);
  if (!isExistingDirectory(runtimeRoot)) {
    throw new Error(`GStreamer runtime directory was not found: ${runtimeRoot}`);
  }

  const runtimeVersion = readRuntimeVersion(runtimeRoot);
  const vendorDir = resolveVendorDir(runtimeVersion);
  if (!vendorDir) {
    throw new Error(
      `No compatible vendored Windows GStreamer Vulkan plugins were found for runtime ${runtimeVersion ?? "unknown"}. `
      + `Expected artifacts under ${vendorRoot}/<gstreamer-version>/.`,
    );
  }

  injectVulkanPlugins(runtimeRoot, vendorDir);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
