import { readFileSync, readdirSync } from "node:fs";
import { basename, join } from "node:path";

function checkedRead(buffer, offset, size, label) {
  if (!Number.isInteger(offset) || offset < 0 || offset + size > buffer.length) {
    throw new Error(`Invalid PE ${label} offset: ${offset}`);
  }
}

function readUInt16(buffer, offset, label) {
  checkedRead(buffer, offset, 2, label);
  return buffer.readUInt16LE(offset);
}

function readUInt32(buffer, offset, label) {
  checkedRead(buffer, offset, 4, label);
  return buffer.readUInt32LE(offset);
}

function readAsciiString(buffer, offset) {
  checkedRead(buffer, offset, 1, "string");
  const end = buffer.indexOf(0, offset);
  if (end < 0) {
    throw new Error(`Unterminated PE import name at offset ${offset}`);
  }
  return buffer.toString("ascii", offset, end);
}

export function readPeImportNames(buffer) {
  if (buffer.length < 64 || buffer.toString("ascii", 0, 2) !== "MZ") {
    throw new Error("File is not a PE executable");
  }

  const peOffset = readUInt32(buffer, 0x3c, "header");
  checkedRead(buffer, peOffset, 24, "signature");
  if (buffer.toString("ascii", peOffset, peOffset + 4) !== "PE\u0000\u0000") {
    throw new Error("Invalid PE signature");
  }

  const sectionCount = readUInt16(buffer, peOffset + 6, "section count");
  const optionalHeaderSize = readUInt16(buffer, peOffset + 20, "optional header size");
  const optionalHeaderOffset = peOffset + 24;
  const optionalHeaderMagic = readUInt16(buffer, optionalHeaderOffset, "optional header");
  const dataDirectoryOffset = optionalHeaderOffset + (
    optionalHeaderMagic === 0x10b ? 96 : optionalHeaderMagic === 0x20b ? 112 : 0
  );
  if (dataDirectoryOffset === optionalHeaderOffset) {
    throw new Error(`Unsupported PE optional header: 0x${optionalHeaderMagic.toString(16)}`);
  }

  checkedRead(buffer, optionalHeaderOffset, optionalHeaderSize, "optional header");
  const importTableRva = readUInt32(buffer, dataDirectoryOffset + 8, "import table");
  if (importTableRva === 0) {
    return [];
  }

  const sectionTableOffset = optionalHeaderOffset + optionalHeaderSize;
  const sections = [];
  for (let index = 0; index < sectionCount; index += 1) {
    const offset = sectionTableOffset + index * 40;
    checkedRead(buffer, offset, 40, "section");
    sections.push({
      virtualSize: readUInt32(buffer, offset + 8, "section virtual size"),
      virtualAddress: readUInt32(buffer, offset + 12, "section virtual address"),
      rawSize: readUInt32(buffer, offset + 16, "section raw size"),
      rawOffset: readUInt32(buffer, offset + 20, "section raw offset"),
    });
  }

  const rvaToOffset = (rva) => {
    const section = sections.find(({ virtualAddress, virtualSize, rawSize }) =>
      rva >= virtualAddress && rva < virtualAddress + Math.max(virtualSize, rawSize),
    );
    if (!section) {
      throw new Error(`PE import RVA 0x${rva.toString(16)} is outside every section`);
    }
    const offset = section.rawOffset + rva - section.virtualAddress;
    checkedRead(buffer, offset, 1, "import");
    return offset;
  };

  const imports = [];
  let descriptorOffset = rvaToOffset(importTableRva);
  for (;;) {
    checkedRead(buffer, descriptorOffset, 20, "import descriptor");
    const fields = Array.from({ length: 5 }, (_, index) =>
      readUInt32(buffer, descriptorOffset + index * 4, "import descriptor"),
    );
    if (fields.every((value) => value === 0)) {
      break;
    }
    imports.push(readAsciiString(buffer, rvaToOffset(fields[3])));
    descriptorOffset += 20;
  }
  return imports;
}

export function collectBundledPeDependencies(binary, dependencyDirectory) {
  const available = new Map(
    readdirSync(dependencyDirectory)
      .filter((name) => name.toLowerCase().endsWith(".dll"))
      .map((name) => [name.toLowerCase(), join(dependencyDirectory, name)]),
  );
  const dependencies = new Map();
  const pending = [binary];

  while (pending.length > 0) {
    const current = pending.pop();
    for (const importedName of readPeImportNames(readFileSync(current))) {
      const normalizedName = importedName.toLowerCase();
      const dependency = available.get(normalizedName);
      if (!dependency || dependencies.has(normalizedName)) {
        continue;
      }
      dependencies.set(normalizedName, dependency);
      pending.push(dependency);
    }
  }

  return [...dependencies.values()].sort((left, right) =>
    basename(left).localeCompare(basename(right), "en", { sensitivity: "base" }),
  );
}
