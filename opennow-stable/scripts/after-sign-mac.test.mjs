import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { signMacAppPreservingValidSignatures } from "./after-sign-mac.mjs";

test("signs nested Mach-O code inside-out without replacing valid signatures", async () => {
  const root = await mkdtemp(join(tmpdir(), "opennow-after-sign-"));
  const appOutDir = join(root, "output");

  try {
    const appPath = join(appOutDir, "OpenNOW.app");
    const frameworksDir = join(appPath, "Contents", "Frameworks");
    const signedHelper = join(frameworksDir, "OpenNOW Helper.app");
    const unsignedHelper = join(frameworksDir, "OpenNOW Helper (Plugin).app");
    const electronFramework = join(frameworksDir, "Electron Framework.framework");
    const frameworkBinary = join(electronFramework, "Versions", "A", "Electron Framework");
    const crashpadHandler = join(
      electronFramework,
      "Versions",
      "A",
      "Helpers",
      "chrome_crashpad_handler",
    );
    await mkdir(signedHelper, { recursive: true });
    await mkdir(unsignedHelper, { recursive: true });
    await mkdir(join(crashpadHandler, ".."), { recursive: true });
    await writeFile(frameworkBinary, Buffer.from([0xcf, 0xfa, 0xed, 0xfe]));
    await writeFile(crashpadHandler, Buffer.from([0xcf, 0xfa, 0xed, 0xfe]));
    const signCalls = [];

    await signMacAppPreservingValidSignatures(appPath, "com.zortos.opennow.stable", {
      isSignatureValid: (path) => path === signedHelper,
      sign: (path, extraArgs = []) => signCalls.push({ path, extraArgs }),
    });

    const signedPaths = signCalls.map(({ path }) => path);
    assert.equal(signedPaths.includes(signedHelper), false);
    assert.equal(signedPaths.includes(unsignedHelper), true);
    assert.equal(signedPaths.includes(crashpadHandler), true);
    assert.equal(signedPaths.includes(frameworkBinary), true);
    assert.equal(signedPaths.includes(electronFramework), true);
    assert.ok(signedPaths.indexOf(crashpadHandler) < signedPaths.indexOf(frameworkBinary));
    assert.ok(signedPaths.indexOf(crashpadHandler) < signedPaths.indexOf(electronFramework));
    assert.ok(signedPaths.indexOf(frameworkBinary) < signedPaths.indexOf(electronFramework));
    assert.deepEqual(signCalls.at(-1), {
      path: appPath,
      extraArgs: [
        "--requirements",
        '=designated => identifier "com.zortos.opennow.stable"',
      ],
    });
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
