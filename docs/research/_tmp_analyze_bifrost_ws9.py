#!/usr/bin/env python3
"""Resolve HTTPRequest method/URI bytes and pre-upgrade headers in connect ctor."""
from __future__ import annotations

import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-request-line.txt")


def parse_pe(data: bytes):
    e_lfanew = struct.unpack_from("<I", data, 0x3C)[0]
    coff = e_lfanew + 4
    num = struct.unpack_from("<H", data, coff + 2)[0]
    opt_size = struct.unpack_from("<H", data, coff + 16)[0]
    opt = coff + 20
    image_base = struct.unpack_from("<Q", data, opt + 24)[0]
    sec_off = opt + opt_size
    sections = []
    for i in range(num):
        off = sec_off + i * 40
        name = data[off : off + 8].split(b"\0", 1)[0].decode("ascii", "ignore")
        vsize, vaddr, rawsize, rawptr = struct.unpack_from("<IIII", data, off + 8)
        sections.append((name, vaddr, vsize, rawptr, rawsize))
    return image_base, sections


def main() -> None:
    data = DLL.read_bytes()
    image_base, sections = parse_pe(data)
    text = next(s for s in sections if s[0] == ".text")
    tvaddr, trawptr, trawsize = text[1], text[3], text[4]
    lines: list[str] = []

    def rva_to_file(rva: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if vaddr <= rva < vaddr + max(vsize, rawsize):
                return rawptr + (rva - vaddr)
        return None

    def file_to_rva(foff: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if rawptr <= foff < rawptr + rawsize:
                return vaddr + (foff - rawptr)
        return None

    def rip_target(file_off: int, instr_len: int = 7) -> int:
        disp = struct.unpack_from("<i", data, file_off + 3)[0]
        instr_rva = tvaddr + (file_off - trawptr)
        return instr_rva + instr_len + disp

    def show_cstr(rva: int, label: str) -> None:
        f = rva_to_file(rva)
        assert f is not None
        end = data.find(b"\0", f, f + 200)
        raw = data[f:end] if end >= 0 else data[f : f + 32]
        lines.append(f"{label}: rva={rva:#x} file={f:#x} bytes={raw!r} ascii={raw.decode('latin1')!r}")

    # Resolve lea at 0x4666b0 (rdx → HTTPRequest arg2)
    t = rip_target(0x4666B0)
    show_cstr(t, "4666b0 rdx (HTTPRequest arg2)")

    # Resolve HTTP/1.1
    t = rip_target(0x4666A6)
    show_cstr(t, "4666a6 r9 version")

    # Resolve header name/value construction at 0x46682b..
    # movzx eax, word [rip+d9f54e] at 0x46682b - opcode 0F B7 05 disp32 = 7 bytes
    # Actually: 0x46682b bytes?
    lines.append(f"\nbytes at 46682b: {data[0x46682b:0x466835].hex()}")
    # 0f b7 05 xx xx xx xx = movzx eax, word ptr [rip+disp]
    if data[0x46682B] == 0x0F and data[0x46682C] == 0xB7 and data[0x46682D] == 0x05:
        disp = struct.unpack_from("<i", data, 0x46682E)[0]
        instr_rva = tvaddr + (0x46682B - trawptr)
        target_rva = instr_rva + 7 + disp
        f = rva_to_file(target_rva)
        lines.append(f"46682b word load from rva={target_rva:#x} bytes={data[f:f+4]!r}")

    # movsd xmm0, [rip+e02d92] at 0x46684e - F2 0F 10 05 disp32 = 8 bytes
    lines.append(f"bytes at 46684e: {data[0x46684e:0x46685b].hex()}")
    if data[0x46684E : 0x466852] == bytes.fromhex("f20f1005"):
        disp = struct.unpack_from("<i", data, 0x466852)[0]
        instr_rva = tvaddr + (0x46684E - trawptr)
        target_rva = instr_rva + 8 + disp
        f = rva_to_file(target_rva)
        blob = data[f : f + 16]
        lines.append(f"46684e movsd from rva={target_rva:#x} blob={blob!r}")
        # continue with dword and word loads for full string
    # mov eax, dword [rip] at 0x46685b
    lines.append(f"bytes at 46685b: {data[0x46685b:0x466870].hex()}")
    if data[0x46685B] == 0x8B and data[0x46685C] == 0x05:
        disp = struct.unpack_from("<i", data, 0x46685D)[0]
        instr_rva = tvaddr + (0x46685B - trawptr)
        target_rva = instr_rva + 6 + disp
        f = rva_to_file(target_rva)
        lines.append(f"46685b dword from rva={target_rva:#x} bytes={data[f:f+8]!r}")
    if data[0x466864] == 0x0F and data[0x466865] == 0xB7 and data[0x466866] == 0x05:
        disp = struct.unpack_from("<i", data, 0x466867)[0]
        instr_rva = tvaddr + (0x466864 - trawptr)
        target_rva = instr_rva + 7 + disp
        f = rva_to_file(target_rva)
        lines.append(f"466864 word from rva={target_rva:#x} bytes={data[f:f+4]!r}")

    # Reconstruct the header name string built on stack (14 bytes claimed capacity 0xe)
    # From disasm: copies 8+4+2 = 14 bytes then null at -0x33
    # Find contiguous source - the three loads may be consecutive in rdata
    # Compute all three target RVAs and merge
    targets = []
    # movsd
    disp = struct.unpack_from("<i", data, 0x466852)[0]
    instr_rva = tvaddr + (0x46684E - trawptr)
    targets.append(instr_rva + 8 + disp)
    # dword
    disp = struct.unpack_from("<i", data, 0x46685D)[0]
    instr_rva = tvaddr + (0x46685B - trawptr)
    targets.append(instr_rva + 6 + disp)
    # word
    disp = struct.unpack_from("<i", data, 0x466867)[0]
    instr_rva = tvaddr + (0x466864 - trawptr)
    targets.append(instr_rva + 7 + disp)
    lines.append(f"header name piece RVAs: {[hex(t) for t in targets]}")
    # If consecutive, read as one string
    f0 = rva_to_file(targets[0])
    name_bytes = data[f0 : f0 + 14]
    lines.append(f"header name reconstructed: {name_bytes!r}")
    # null-terminated interpret
    if 0 in name_bytes:
        lines.append(f"  as cstring: {name_bytes.split(b'\0')[0].decode('latin1')!r}")
    else:
        lines.append(f"  as 14-byte: {name_bytes.decode('latin1')!r}")

    # 1-char value from word load at 46682b
    disp = struct.unpack_from("<i", data, 0x46682E)[0]
    instr_rva = tvaddr + (0x46682B - trawptr)
    tr = instr_rva + 7 + disp
    f = rva_to_file(tr)
    lines.append(f"header value source word: {data[f:f+2]!r} -> char {chr(data[f])!r}")

    # Default HTTPRequest path 0x2f confirmation
    lines.append("\n=== default HTTPRequest at c91680 sets path '/' (0x2f) ===")
    lines.append(f"bytes: {data[0xc916c8:0xc916ce].hex()}  ; mov word [rbx+0x70], 0x2f")

    # What does c915c0 put where - check GET string near default ctor
    # lea rdx at c9169c
    k = 0xC9169C
    disp = struct.unpack_from("<i", data, k + 3)[0]
    instr_rva = tvaddr + (k - trawptr)
    show_cstr(instr_rva + 7 + disp, "c9169c default method string")

    # Who calls the connect ctor 0x466640? Find callers to learn path arg
    lines.append("\n=== callers of 0x466640 (Nv WebSocket session ctor) ===")
    callers = []
    i = trawptr
    while i < trawptr + trawsize - 5:
        if data[i] == 0xE8:
            rel = struct.unpack_from("<i", data, i + 1)[0]
            if i + 5 + rel == 0x466640:
                callers.append(i)
        i += 1
    lines.append(f"callers: {[hex(c) for c in callers]}")

    md = Cs(CS_ARCH_X86, CS_MODE_64)
    for c in callers[:8]:
        lines.append(f"\n--- caller {c:#x} context ---")
        # dump 0x80 before with string ann
        lo = max(trawptr, c - 0x100)
        for insn in md.disasm(data[lo : c + 0x20], lo):
            ann = ""
            raw = bytes(insn.bytes)
            if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", raw, 3)[0]
                instr_rva = tvaddr + (insn.address - trawptr)
                fr = rva_to_file(instr_rva + len(raw) + disp)
                if fr is not None:
                    if data[fr] == 0:
                        ann = "  ; EMPTY"
                    else:
                        end = data.find(b"\0", fr, fr + 80)
                        s = data[fr:end].decode("ascii", "replace") if end > fr else ""
                        if s and all(32 <= ord(ch) < 127 for ch in s):
                            ann = f"  ; {s!r}"
            mark = ">>" if insn.address == c else "  "
            lines.append(f"{mark}{insn.address:#x}: {insn.mnemonic} {insn.op_str}{ann}")

    # Also check: does HTTPRequest write empty URI as "/" ?
    # Search Poco HTTPRequest::getURI or write - look for comparison with empty then slash
    lines.append("\n=== Poco HTTPRequest write: empty URI handling ===")
    # In many Poco versions, HTTPRequest::write writes getURI() as-is.
    # If URI is empty, request line is 'GET  HTTP/1.1' (invalid) unless set to '/'.
    # NVST passes rbx as URI - need to see if caller passes '/' or getPath().

    # Search for mov word xx, 0x2f near WS connect (force slash)
    slash_imm = []
    for k in range(0x466000, 0x468000):
        # 66 C7 43/47/45 xx 2F 00 = mov word ptr [...], 0x2f
        if data[k] == 0x66 and data[k + 1] == 0xC7 and data[k + 4 : k + 6] == b"\x2f\x00":
            slash_imm.append(k)
        # C7 43 xx 2F 00 00 00
        if data[k] == 0xC7 and data[k + 2 : k + 6] == b"\x2f\x00\x00\x00":
            slash_imm.append(k)
    lines.append(f"mov imm '/' in connect region: {[hex(x) for x in slash_imm]}")

    # Broader in poco http request write function - find "GET" method write
    # Already know GET\0 at feb9f4 used by openssl not poco.
    # Poco HTTPRequest::HTTP_GET - search RTTI or static
    get_static = []
    start = 0
    while True:
        h = data.find(b"\0GET\0", start)
        if h < 0:
            break
        get_static.append(h + 1)
        start = h + 1
    lines.append(f"\\0GET\\0 sites: {[hex(x) for x in get_static[:10]]}")

    # Check 4666b0 again - maybe it's GET not empty (false positive if pointing at padding)
    t = rip_target(0x4666B0)
    f = rva_to_file(t)
    lines.append(f"\n4666b0 neighborhood: {data[f-8:f+16]!r}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT}")
    print("\n".join(lines[:80]))


if __name__ == "__main__":
    main()
