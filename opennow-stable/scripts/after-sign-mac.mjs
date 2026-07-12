import { execFileSync, spawnSync } from "node:child_process";
import { readdir } from "node:fs/promises";
import { join } from "node:path";

const NESTED_CODE_BUNDLE_EXTENSIONS = [".app", ".appex", ".bundle", ".framework", ".xpc"];

function isNestedCodeBundle(path) {
  return NESTED_CODE_BUNDLE_EXTENSIONS.some((extension) => path.endsWith(extension));
}

async function collectNestedCodeBundles(root) {
  const bundles = [];

  async function walk(directory) {
    let entries;
    try {
      entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
      if (error?.code === "ENOENT") return;
      throw error;
    }

    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const path = join(directory, entry.name);
      await walk(path);
      if (isNestedCodeBundle(path)) bundles.push(path);
    }
  }

  await walk(root);
  return bundles;
}

function hasCodeSignature(path) {
  return spawnSync("codesign", ["--display", "--verbose=0", path], {
    stdio: "ignore",
  }).status === 0;
}

function adHocSign(path, extraArgs = []) {
  execFileSync("codesign", ["--force", "--sign", "-", ...extraArgs, path]);
}

export async function signUnsignedMacApp(
  appPath,
  bundleId,
  { isSigned = hasCodeSignature, sign = adHocSign } = {},
) {
  // Ad-hoc sign only unsigned nested bundles, deepest first. This makes unsigned
  // Electron distributions valid without replacing real signatures on helpers,
  // frameworks, extensions, or plug-ins.
  for (const nestedBundle of await collectNestedCodeBundles(join(appPath, "Contents"))) {
    if (!isSigned(nestedBundle)) sign(nestedBundle);
  }

  sign(appPath, ["--requirements", `=designated => identifier "${bundleId}"`]);
}

export default async function afterSign({ appOutDir, packager }) {
  if (process.platform !== "darwin") return;
  if (process.env.CSC_IDENTITY_AUTO_DISCOVERY !== "false") return;

  const appPath = join(appOutDir, `${packager.appInfo.productFilename}.app`);
  const bundleId = packager.appInfo.id;

  await signUnsignedMacApp(appPath, bundleId);
}
