import { execFileSync, spawnSync } from "node:child_process";
import { open, readdir, stat } from "node:fs/promises";
import { join } from "node:path";

const NESTED_CODE_BUNDLE_EXTENSIONS = [".app", ".appex", ".bundle", ".framework", ".xpc"];
const MACH_O_MAGICS = new Set([
  0xfeedface,
  0xfeedfacf,
  0xcafebabe,
  0xcafebabf,
  0xbebafeca,
  0xbfbafeca,
  0xcffaedfe,
  0xcefaedfe,
]);

function isNestedCodeBundle(path) {
  return NESTED_CODE_BUNDLE_EXTENSIONS.some((extension) => path.endsWith(extension));
}

async function isMachO(path) {
  let file;
  try {
    file = await open(path, "r");
    const header = Buffer.allocUnsafe(4);
    const { bytesRead } = await file.read(header, 0, header.length, 0);
    return bytesRead === header.length && MACH_O_MAGICS.has(header.readUInt32BE(0));
  } catch (error) {
    if (error?.code === "ENOENT") return false;
    throw error;
  } finally {
    await file?.close();
  }
}

async function collectNestedCodeObjects(root) {
  const codeObjects = [];

  async function isFileEntry(path, entry) {
    if (entry.isFile()) return true;
    if (!entry.isSymbolicLink()) return false;
    try {
      return (await stat(path)).isFile();
    } catch (error) {
      if (error?.code === "ENOENT") return false;
      throw error;
    }
  }

  async function walk(directory) {
    let entries;
    try {
      entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
      if (error?.code === "ENOENT") return;
      throw error;
    }

    for (const entry of entries) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        await walk(path);
        if (isNestedCodeBundle(path)) codeObjects.push(path);
      } else if (await isFileEntry(path, entry) && await isMachO(path)) {
        codeObjects.push(path);
      }
    }
  }

  await walk(root);
  return codeObjects.sort((left, right) => {
    const leftDepth = left.split(/[\\/]+/).length;
    const rightDepth = right.split(/[\\/]+/).length;
    return rightDepth - leftDepth;
  });
}

function hasValidCodeSignature(path) {
  return spawnSync("codesign", ["--verify", "--strict", path], {
    stdio: "ignore",
  }).status === 0;
}

function adHocSign(path, extraArgs = []) {
  execFileSync("codesign", ["--force", "--sign", "-", ...extraArgs, path]);
}

export async function signMacAppPreservingValidSignatures(
  appPath,
  bundleId,
  { isSignatureValid = hasValidCodeSignature, sign = adHocSign } = {},
) {
  // Sign nested Mach-O binaries and bundles inside-out. Valid signatures are
  // preserved; unsigned or invalidated code (for example after relocation) gets
  // a fresh ad-hoc signature before its containing bundle is signed.
  for (const codeObject of await collectNestedCodeObjects(join(appPath, "Contents"))) {
    if (!isSignatureValid(codeObject)) sign(codeObject);
  }

  sign(appPath, ["--requirements", `=designated => identifier "${bundleId}"`]);
}

export default async function afterSign({ appOutDir, packager }) {
  if (process.platform !== "darwin") return;
  if (process.env.CSC_IDENTITY_AUTO_DISCOVERY !== "false") return;

  const appPath = join(appOutDir, `${packager.appInfo.productFilename}.app`);
  const bundleId = packager.appInfo.id;

  await signMacAppPreservingValidSignatures(appPath, bundleId);
}
