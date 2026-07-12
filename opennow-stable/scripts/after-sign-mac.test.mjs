import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { signUnsignedMacApp } from "./after-sign-mac.mjs";

test("signs unsigned nested bundles without replacing existing signatures", async () => {
  const root = await mkdtemp(join(tmpdir(), "opennow-after-sign-"));
  const appOutDir = join(root, "output");

  try {
    const appPath = join(appOutDir, "OpenNOW.app");
    const frameworksDir = join(appPath, "Contents", "Frameworks");
    const signedHelper = join(frameworksDir, "OpenNOW Helper.app");
    const unsignedHelper = join(frameworksDir, "OpenNOW Helper (Plugin).app");
    await mkdir(signedHelper, { recursive: true });
    await mkdir(unsignedHelper, { recursive: true });
    const signCalls = [];

    await signUnsignedMacApp(appPath, "com.zortos.opennow.stable", {
      isSigned: (path) => path === signedHelper,
      sign: (path, extraArgs = []) => signCalls.push({ path, extraArgs }),
    });

    assert.deepEqual(signCalls, [
      { path: unsignedHelper, extraArgs: [] },
      {
        path: appPath,
        extraArgs: [
          "--requirements",
          '=designated => identifier "com.zortos.opennow.stable"',
        ],
      },
    ]);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
