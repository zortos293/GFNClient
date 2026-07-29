import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { collectBundledPeDependencies, readPeImportNames } from "./windows-pe-imports.mjs";

function peFixture(importNames) {
  const buffer = Buffer.alloc(0x800);
  const peOffset = 0x80;
  const optionalHeaderOffset = peOffset + 24;
  const sectionTableOffset = optionalHeaderOffset + 0xf0;
  const sectionRva = 0x1000;
  const sectionOffset = 0x200;
  const importTableOffset = sectionOffset;

  buffer.write("MZ", 0, "ascii");
  buffer.writeUInt32LE(peOffset, 0x3c);
  buffer.write("PE\u0000\u0000", peOffset, "ascii");
  buffer.writeUInt16LE(0x8664, peOffset + 4);
  buffer.writeUInt16LE(1, peOffset + 6);
  buffer.writeUInt16LE(0xf0, peOffset + 20);
  buffer.writeUInt16LE(0x20b, optionalHeaderOffset);
  buffer.writeUInt32LE(sectionRva, optionalHeaderOffset + 112 + 8);
  buffer.write(".rdata\u0000\u0000", sectionTableOffset, "ascii");
  buffer.writeUInt32LE(0x600, sectionTableOffset + 8);
  buffer.writeUInt32LE(sectionRva, sectionTableOffset + 12);
  buffer.writeUInt32LE(0x600, sectionTableOffset + 16);
  buffer.writeUInt32LE(sectionOffset, sectionTableOffset + 20);

  let nameOffset = importTableOffset + (importNames.length + 1) * 20;
  importNames.forEach((name, index) => {
    buffer.writeUInt32LE(sectionRva + nameOffset - sectionOffset, importTableOffset + index * 20 + 12);
    buffer.write(`${name}\u0000`, nameOffset, "ascii");
    nameOffset += Buffer.byteLength(name) + 1;
  });
  return buffer;
}

test("reads imported DLL names from a 64-bit PE image", () => {
  assert.deepEqual(
    readPeImportNames(peFixture(["gstvideo-1.0-0.dll", "KERNEL32.dll"])),
    ["gstvideo-1.0-0.dll", "KERNEL32.dll"],
  );
});

test("collects only the recursive DLL closure available in the runtime", async () => {
  const root = await mkdtemp(join(tmpdir(), "opennow-pe-imports-"));
  const runtime = join(root, "runtime");
  const binary = join(root, "opennow-streamer.exe");

  try {
    await mkdir(runtime);
    await writeFile(binary, peFixture(["gstvideo-1.0-0.dll", "KERNEL32.dll"]));
    await writeFile(
      join(runtime, "gstvideo-1.0-0.dll"),
      peFixture(["gstreamer-1.0-0.dll", "ffi-7.dll"]),
    );
    await writeFile(join(runtime, "gstreamer-1.0-0.dll"), peFixture(["glib-2.0-0.dll"]));
    await writeFile(join(runtime, "glib-2.0-0.dll"), peFixture([]));
    await writeFile(join(runtime, "unused.dll"), peFixture([]));

    assert.deepEqual(
      collectBundledPeDependencies(binary, runtime).map((path) => path.slice(runtime.length + 1)),
      ["glib-2.0-0.dll", "gstreamer-1.0-0.dll", "gstvideo-1.0-0.dll"],
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
