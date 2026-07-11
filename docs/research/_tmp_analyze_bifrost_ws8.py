#!/usr/bin/env python3
"""Find real WebSocket client handshake ctor and HTTPRequest URI argument."""
from __future__ import annotations

import struct
from collections import defaultdict
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-httpreq-uri.txt")


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

    def file_to_rva(foff: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if rawptr <= foff < rawptr + rawsize:
                return vaddr + (foff - rawptr)
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
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            s = va_to_cstr(image_base + instr_rva + len(raw) + disp)
            if s:
                return f"  ; {s!r}"
        # also mov rax,[rip+disp] for Upgrade pointer table style
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] == 0x8B and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            target_rva = instr_rva + len(raw) + disp
            f = rva_to_file(target_rva)
            if f is not None and f + 8 <= len(data):
                q = struct.unpack_from("<Q", data, f)[0]
                s = va_to_cstr(q)
                if s:
                    return f"  ; ->ptr {s!r}"
                s2 = va_to_cstr(image_base + target_rva)
                if s2:
                    return f"  ; {s2!r}"
        return ""

    # Find function containing Upgrade set at 0xca7958
    site = 0xCA7958
    start = site
    while start > site - 0x800:
        if data[start - 1] == 0xCC and data[start] in (0x40, 0x48, 0x55, 0x53, 0x56, 0x57):
            break
        # also accept push rbp (55) without int3
        if data[start] == 0x55 and data[start + 1] in (0x48, 0x41, 0x56, 0x57, 0x53):
            break
        start -= 1
    lines.append(f"handshake fn start guess {start:#x}")

    # Find ALL callers of this function start (and nearby entry points)
    # Also find callers of 0xca7880..0xca7a00 by scanning E8 rel32 across .text
    print("scanning callers...")
    entries = set()
    for cand in range(start, site, 1):
        if data[cand - 1] == 0xCC and data[cand] in (0x40, 0x48, 0x55, 0x53):
            entries.add(cand)
    entries.add(start)
    # also try common: function might start with mov [rsp+8]
    for cand in range(site - 0x400, site):
        if data[cand : cand + 4] == bytes([0x48, 0x89, 0x5C, 0x24]) or data[cand : cand + 3] == bytes([0x40, 0x55, 0x53]):
            entries.add(cand)

    callers: dict[int, list[int]] = defaultdict(list)
    i = trawptr
    end = trawptr + trawsize - 5
    while i < end:
        if data[i] == 0xE8:
            rel = struct.unpack_from("<i", data, i + 1)[0]
            tgt = i + 5 + rel
            if start - 0x40 <= tgt <= site + 0x40:
                callers[tgt].append(i)
        i += 1

    lines.append(f"entries near handshake: {sorted(hex(x) for x in entries)[:30]}")
    lines.append("callers of nearby entries:")
    for tgt, clist in sorted(callers.items()):
        lines.append(f"  tgt {tgt:#x}: {len(clist)} callers {[hex(c) for c in clist[:15]]}")
        for c in clist[:8]:
            # strings near caller
            for k in range(max(trawptr, c - 0x300), min(trawptr + trawsize, c + 0x100)):
                if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
                    disp = struct.unpack_from("<i", data, k + 3)[0]
                    instr_rva = tvaddr + (k - trawptr)
                    s = va_to_cstr(image_base + instr_rva + 7 + disp)
                    if s and any(x in s for x in ("WebSocket", "Connecting", "HTTP", "RTSP", "WSS", "Host", "GET", "upgrade", "Upgrade", "WS ")):
                        lines.append(f"    near caller {c:#x} @{k:#x} -> {s!r}")

    # Dump the handshake function with annotations
    lines.append(f"\n===== handshake fn dump {start:#x} - {start+0x500:#x} =====")
    for insn in md.disasm(data[start : start + 0x500], start):
        lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Dump connect region carefully - sync from known good instruction at 0x466d10
    # Prior said approx fn start 0x466d10
    lines.append("\n===== connect fn 0x466d10-0x466f80 =====")
    for insn in md.disasm(data[0x466D10:0x466F80], 0x466D10):
        lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Also dump BEFORE connect - HTTPRequest construction at 0x466600-0x466d10
    # Find real start by walking back from 0x4666a6 (HTTP/1.1)
    http11_ref = None
    # find lea of HTTP/1.1 near 0x4666a6
    for k in range(0x466680, 0x4666c0):
        if data[k] in (0x48, 0x4C) and data[k + 1] == 0x8D and (data[k + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, k + 3)[0]
            instr_rva = tvaddr + (k - trawptr)
            s = va_to_cstr(image_base + instr_rva + 7 + disp)
            if s == "HTTP/1.1":
                http11_ref = k
                break
    lines.append(f"\nHTTP/1.1 lea @{http11_ref}")
    if http11_ref:
        # find fn start
        fs = http11_ref
        while fs > http11_ref - 0x400:
            if data[fs - 1] == 0xCC and data[fs] in (0x40, 0x48, 0x55, 0x53):
                break
            fs -= 1
        lines.append(f"HTTPRequest build fn~{fs:#x}")
        lines.append(f"\n===== HTTPRequest build {fs:#x}-{http11_ref+0x200:#x} =====")
        for insn in md.disasm(data[fs : http11_ref + 0x200], fs):
            lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Critical: find HTTPRequest::HTTPRequest(method, uri, version) calls
    # Poco HTTP_GET is "GET" - find lea GET near HTTP/1.1 in same function
    lines.append("\n===== GET/URI string loads in HTTPRequest build window =====")
    if http11_ref:
        lo, hi = fs, http11_ref + 0x300
        for k in range(lo, hi - 7):
            if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", data, k + 3)[0]
                instr_rva = tvaddr + (k - trawptr)
                s = va_to_cstr(image_base + instr_rva + 7 + disp)
                if s:
                    lines.append(f"  @{k:#x} -> {s!r}")

    # Look for path coming from URI.getPath() - no string. Instead look at
    # what is passed as 2nd arg to HTTPRequest ctor.
    # Find call after HTTP/1.1 lea
    if http11_ref:
        lines.append("\n===== calls after HTTP/1.1 =====")
        for k in range(http11_ref, http11_ref + 0x100):
            if data[k] == 0xE8:
                rel = struct.unpack_from("<i", data, k + 1)[0]
                tgt = k + 5 + rel
                lines.append(f"  call @{k:#x} -> {tgt:#x}")
                # disasm target prologue
                for insn in list(md.disasm(data[tgt : tgt + 0x80], tgt))[:20]:
                    lines.append(f"    {insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Search entire DLL for pattern: HTTPRequest constructed with "/" 
    # by finding code that does lea rdx, "/" then call near HTTP/1.1 usage
    # Also check if URI path is taken from Poco::URI of rtsps URL
    # When Poco::URI parses "rtsps://host:322", getPath() returns "" or "/"
    lines.append("\n===== Poco URI path behavior notes =====")
    lines.append("Poco::URI('rtsps://host:322').getPath() typically returns empty or '/'")
    lines.append("HTTPRequest(HTTP_GET, path, HTTP_1_1) then WebSocket(session, request, response)")
    lines.append("Request line becomes: GET / HTTP/1.1   OR   GET  HTTP/1.1 (empty)")

    # Find empty string cstring used as path near connect
    empty_hits = []
    # \0\0 is too common. Look for lea of a zero-length string: byte 0 at known rdata
    # Better: in HTTPRequest build function, look for lea of something that is empty
    if http11_ref:
        for k in range(fs, http11_ref + 0x200):
            if data[k] in (0x48, 0x4C) and data[k + 1] == 0x8D and (data[k + 2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", data, k + 3)[0]
                instr_rva = tvaddr + (k - trawptr)
                target_rva = instr_rva + 7 + disp
                f = rva_to_file(target_rva)
                if f is not None and data[f] == 0:
                    empty_hits.append(k)
        lines.append(f"lea of empty cstring in HTTPRequest build: {[hex(x) for x in empty_hits]}")

    # Disassemble 0xc915c0 which was called near HTTP/1.1
    lines.append("\n===== 0xc915c0 (call near HTTP/1.1) =====")
    for insn in md.disasm(data[0xC915C0 : 0xC915C0 + 0x120], 0xC915C0):
        lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # 0x466985 -> 0x466d10 Connecting
    # What about 0xca61d0 called after ca8f70?
    lines.append("\n===== 0xca61d0 after connect helper =====")
    for insn in md.disasm(data[0xCA61D0 : 0xCA61D0 + 0x200], 0xCA61D0):
        lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{annotate(insn)}")

    # Find WebSocket::WebSocket by looking for function that sets Sec-WebSocket-Key
    # Key string has refs=0 for RIP lea - maybe accessed via pointer table
    # From handshake dump at ca7958: mov rax, [rip+disp] ; 'Upgrade' - pointer indirection!
    # So Sec-WebSocket-Key may also be via pointer. Search mov rax,[rip] that resolves to Key
    key_off = data.find(b"Sec-WebSocket-Key\0")
    key_rva = file_to_rva(key_off)
    key_va = image_base + key_rva
    lines.append(f"\nSec-WebSocket-Key va={key_va:#x}")
    # find qwords in rdata equal to key_va
    ptr_locs = []
    # scan .rdata
    for name, vaddr, vsize, rawptr, rawsize in sections:
        if name not in (".rdata", ".data"):
            continue
        for off in range(rawptr, rawptr + rawsize - 7, 8):
            if struct.unpack_from("<Q", data, off)[0] == key_va:
                ptr_locs.append(off)
    lines.append(f"pointer-to-Key locations: {[hex(x) for x in ptr_locs[:20]]}")
    # RIP-rel loads of those pointer locations
    refs = defaultdict(list)
    i = trawptr
    while i < trawptr + trawsize - 7:
        if data[i] in (0x48, 0x4C) and data[i + 1] in (0x8D, 0x8B) and (data[i + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 3)[0]
            instr_rva = tvaddr + (i - trawptr)
            refs[instr_rva + 7 + disp].append(i)
        i += 1
    for pl in ptr_locs:
        prva = file_to_rva(pl)
        lines.append(f"  ptrloc {pl:#x} rva={prva:#x} refs={[hex(x) for x in refs.get(prva, [])]}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
