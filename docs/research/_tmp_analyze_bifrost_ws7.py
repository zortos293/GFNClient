#!/usr/bin/env python3
"""Disassemble NVST WebSocket connect → Poco HTTPRequest URI construction."""
from __future__ import annotations

import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-connect-disasm.txt")


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
    md = Cs(CS_ARCH_X86, CS_MODE_64)
    lines: list[str] = []

    def rva_to_file(rva: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if vaddr <= rva < vaddr + max(vsize, rawsize):
                return rawptr + (rva - vaddr)
        return None

    def va_to_cstr(va: int, maxlen: int = 240) -> str | None:
        if va < image_base:
            return None
        f = rva_to_file(va - image_base)
        if f is None:
            return None
        end = data.find(b"\0", f, min(len(data), f + maxlen))
        if end < 0:
            return None
        raw = data[f:end]
        if raw and all(32 <= b < 127 for b in raw):
            return raw.decode("ascii")
        return None

    def annotate(insn) -> str:
        raw = bytes(insn.bytes)
        ann = ""
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            s = va_to_cstr(image_base + instr_rva + len(raw) + disp)
            if s:
                ann = f"  ; {s!r}"
        if insn.mnemonic == "call":
            try:
                if insn.op_str.startswith("0x"):
                    tgt = int(insn.op_str, 16)
                    if 0xCA0000 <= tgt <= 0xCB5000:
                        ann += "  ; Poco/WS"
                    elif 0x1B0000 <= tgt <= 0x1D0000:
                        ann += "  ; likely HTTPRequest/string helper"
            except Exception:
                pass
        return ann

    def dump_range(lo: int, hi: int, title: str) -> None:
        lines.append(f"\n===== {title} {lo:#x}-{hi:#x} =====")
        for insn in md.disasm(data[lo:hi], lo):
            lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Connect region: from HTTP/1.1 lea through call to ca8f70
    dump_range(0x466600, 0x467000, "connect+HTTP/1.1+WS ctor call")

    # Poco WebSocket client ctor candidates
    for tgt, name in [
        (0xCA8F70, "called from Connecting"),
        (0xCA8F40, "called from SSL 1-way setup"),
        (0xCA8BE0, "called from NvWebSocketSession"),
        (0xCA8240, "send helper"),
        (0xCA82F0, "recv/send helper"),
    ]:
        # find fn start
        start = tgt
        while start > tgt - 0x200 and not (data[start - 1] == 0xCC and data[start] in (0x40, 0x48, 0x55, 0x53)):
            start -= 1
        dump_range(start, start + 0x220, f"{name} fn~{start:#x} entry {tgt:#x}")

    # Also dump 0xca7900-0xca7c80 where Upgrade/13 are set (client handshake headers)
    # Find real function by scanning for push rbp near 0xca7900
    dump_range(0xCA7880, 0xCA7C80, "Poco WS client handshake header set")

    # Look for HTTPRequest constructor pattern: HTTP_GET string + path
    # Search for lea of GET\0 near 0xca8xxx
    lines.append("\n===== string loads in 0xca8e00-0xca9200 =====")
    for k in range(0xCA8E00, 0xCA9200):
        if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, k + 3)[0]
            instr_rva = tvaddr + (k - trawptr)
            s = va_to_cstr(image_base + instr_rva + 7 + disp)
            if s:
                lines.append(f"  @{k:#x} -> {s!r}")

    # Immediate '/' as path: look for mov of small string or lea '/' in 0x466600-0x467000 and 0xca8e00-0xca9200
    lines.append("\n===== search lea '/' or empty path immediates in connect+ctor =====")
    for lo, hi in [(0x466600, 0x467000), (0xCA8E00, 0xCA9300), (0x6FC200, 0x6FC500)]:
        for k in range(lo, hi - 7):
            if data[k] in (0x48, 0x4C) and data[k + 1] == 0x8D and (data[k + 2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", data, k + 3)[0]
                instr_rva = tvaddr + (k - trawptr)
                target_rva = instr_rva + 7 + disp
                s = va_to_cstr(image_base + target_rva)
                if s in ("/", "", "GET", "HTTP/1.1", "Host", "Upgrade", "websocket", "Connection", "13"):
                    lines.append(f"  @{k:#x} -> {s!r}")

    # Check HTTPRequest::HTTP_GET constant - in Poco it's often a static string "GET"
    # Find refs to GET\0 from within 0xca8000-0xcaa000
    get_off = data.find(b"GET\0", 0xFEB000, 0xFF0000)
    lines.append(f"\nGET@poco-ish {get_off:#x}")
    # scan calls from connect that might construct HTTPRequest
    lines.append("\n===== all calls from 0x466600-0x467000 =====")
    for k in range(0x466600, 0x467000 - 5):
        if data[k] == 0xE8:
            rel = struct.unpack_from("<i", data, k + 1)[0]
            tgt = k + 5 + rel
            lines.append(f"  call @{k:#x} -> {tgt:#x}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
