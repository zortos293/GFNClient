#!/usr/bin/env python3
"""Find HTTPRequest URI/path for NVST WebSocket client handshake."""
from __future__ import annotations

import struct
from collections import defaultdict
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-httpreq.txt")


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

    text = next(s for s in sections if s[0] == ".text")
    tvaddr, trawptr, trawsize = text[1], text[3], text[4]
    md = Cs(CS_ARCH_X86, CS_MODE_64)

    print("rip map...")
    refs: dict[int, list[int]] = defaultdict(list)
    i = trawptr
    while i < trawptr + trawsize - 7:
        if data[i] in (0x48, 0x4C) and data[i + 1] in (0x8D, 0x8B) and (data[i + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 3)[0]
            instr_rva = tvaddr + (i - trawptr)
            refs[instr_rva + 7 + disp].append(i)
        i += 1

    def strings_near(file_off: int, radius: int = 0x500) -> list[str]:
        out = []
        start = max(trawptr, file_off - radius)
        endw = min(trawptr + trawsize, file_off + radius)
        k = start
        while k < endw - 7:
            if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", data, k + 3)[0]
                instr_rva = tvaddr + (k - trawptr)
                s = va_to_cstr(image_base + instr_rva + 7 + disp)
                if s:
                    out.append(f"  @{k:#x} -> {s!r}")
            k += 1
        return out

    def disasm(file_off: int, before: int = 0x80, after: int = 0x120) -> list[str]:
        start = max(trawptr, file_off - before)
        out = []
        for insn in md.disasm(data[start : start + before + after], start):
            mark = ">>" if abs(insn.address - file_off) < 6 else "  "
            out.append(f"{mark}{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
        return out

    # 1) All Upgrade\0 with refs, classify client vs server
    lines.append("=== Upgrade\\0 sites ===")
    start = 0
    while True:
        h = data.find(b"Upgrade\0", start)
        if h < 0:
            break
        # ensure exact
        if h > 0 and 32 <= data[h - 1] < 127:
            start = h + 1
            continue
        rva = file_to_rva(h)
        rlist = refs.get(rva, []) if rva else []
        lines.append(f"Upgrade @{h:#x} refs={len(rlist)} {[hex(x) for x in rlist[:12]]}")
        for rh in rlist:
            nearby = strings_near(rh, 0x400)
            interesting = [x for x in nearby if any(
                k in x for k in ("websocket", "Sec-Web", "Connection", "Host", "GET", "HTTP", "13", "WebSocket", "RTSP", "WSS")
            )]
            lines.append(f"  -- {rh:#x} interesting --")
            lines.extend(interesting[:25] if interesting else nearby[:15])
        start = h + 1

    # 2) Client handshake: look for '13' near Upgrade in code (Sec-WebSocket-Version value)
    lines.append("\n=== '13' cstring near WS ===")
    # Poco has '13' at 0x1127370
    thirteen = data.find(b"\x0013\x00", 0x1127300, 0x1127400)
    if thirteen >= 0:
        thirteen += 1
    else:
        thirteen = data.find(b"13\0", 0x1127360, 0x1127390)
    lines.append(f"thirteen @{thirteen:#x}")
    if thirteen >= 0:
        rva = file_to_rva(thirteen)
        for rh in refs.get(rva, []):
            lines.append(f"-- {rh:#x} --")
            lines.extend(strings_near(rh, 0x600))
            lines.extend(disasm(rh, 0x100, 0x200)[:80])

    # 3) Find WebSocket client ctor region by clustering Upgrade+websocket+13 refs
    # From prior: 0xca7958 Upgrade, 0xca7a8b Upgrade, 0xca7b74 '13'
    lines.append("\n=== dense disasm WebSocket handshake builder 0xca7800-0xca7c00 ===")
    for insn in md.disasm(data[0xCA7800:0xCA7C00], 0xCA7800):
        # annotate RIP string loads
        ann = ""
        raw = insn.bytes
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            target = insn.address + len(raw) + disp
            # target is file offset if we're using file offsets as addresses - YES we are
            # wait: md.disasm uses file offset as address, but RIP calc needs RVA!
            # Fix: convert
            instr_rva = tvaddr + (insn.address - trawptr)
            target_rva = instr_rva + len(raw) + disp
            s = va_to_cstr(image_base + target_rva)
            if s:
                ann = f"  ; {s!r}"
        lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}{ann}")

    # 4) Who calls into WebSocket handshake? Find xrefs to 0xca7800-ish function starts
    # Find function containing 0xca7b74 (13)
    # Search for call targets into this region from NVST code (0x46xxxx, 0x6fxxxx)
    lines.append("\n=== calls into 0xca7000-0xca9000 from NVST-ish ranges ===")
    call_hits = []
    for region in [(0x460000, 0x490000), (0x6B0000, 0x710000), (0xEE0000, 0xEF0000), (0x3A0000, 0x3B0000)]:
        for k in range(region[0], region[1] - 5):
            if data[k] == 0xE8:
                rel = struct.unpack_from("<i", data, k + 1)[0]
                tgt = k + 5 + rel
                if 0xCA7000 <= tgt <= 0xCA9000:
                    call_hits.append((k, tgt))
    lines.append(f"call hits: {len(call_hits)}")
    for k, tgt in call_hits[:40]:
        lines.append(f"  call @{k:#x} -> {tgt:#x}")
        lines.extend(strings_near(k, 0x200)[:10])

    # 5) HTTPRequest setURI / path: search for empty path patterns
    # Poco HTTPRequest default URI is "/"
    # Look for lea of "/" near WebSocket / HTTPRequest construction in 0xca6xxx-0xca9xxx and 0x466xxx
    lines.append("\n=== '/' loads near WS client code ===")
    slash_candidates = []
    start = 0
    while True:
        j = data.find(b"/\0", start)
        if j < 0:
            break
        if j > 0 and 32 <= data[j - 1] < 127:
            start = j + 1
            continue
        rva = file_to_rva(j)
        for rh in refs.get(rva, []):
            if 0xCA0000 <= rh <= 0xCB0000 or 0x460000 <= rh <= 0x490000 or 0x6F0000 <= rh <= 0x710000:
                slash_candidates.append((j, rh))
        start = j + 1
    for j, rh in slash_candidates[:20]:
        lines.append(f"'/' @{j:#x} used @{rh:#x}")
        lines.extend(strings_near(rh, 0x300)[:20])
        lines.extend(disasm(rh, 0x40, 0x80)[:25])

    # 6) Trace CreateSession set URL function at 0x484cad area - full string dump + disasm
    lines.append("\n=== RtspClient CreateSession URL rewrite region ===")
    create = data.find(b"RtspClient CreateSession set URL")
    if create >= 0:
        rva = file_to_rva(create)
        for rh in refs.get(rva, []):
            lines.append(f"ref {rh:#x}")
            lines.extend(strings_near(rh, 0x1000))
            # large disasm
            # find fn start
            start = rh
            while start > rh - 0x400 and not (data[start - 1] == 0xCC and data[start] in (0x40, 0x48, 0x55)):
                start -= 1
            lines.append(f"fn~{start:#x}")
            for insn in md.disasm(data[start : start + 0x280], start):
                ann = ""
                raw = bytes(insn.bytes)
                if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] == 0x8D and (raw[2] & 0xC7) == 0x05:
                    disp = struct.unpack_from("<i", raw, 3)[0]
                    instr_rva = tvaddr + (insn.address - trawptr)
                    s = va_to_cstr(image_base + instr_rva + len(raw) + disp)
                    if s:
                        ann = f"  ; {s!r}"
                lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}{ann}")

    # 7) Connecting function - find HTTPRequest / WebSocket construction BEFORE the log
    lines.append("\n=== Connecting function full string map 0x466400-0x467200 ===")
    for k in range(0x466400, 0x467200):
        if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, k + 3)[0]
            instr_rva = tvaddr + (k - trawptr)
            s = va_to_cstr(image_base + instr_rva + 7 + disp)
            if s:
                lines.append(f"  @{k:#x} -> {s!r}")

    # disasm from before Connecting through after
    lines.append("\n=== disasm 0x466c80-0x467100 (connect+upgrade) ===")
    for insn in md.disasm(data[0x466C80:0x467100], 0x466C80):
        ann = ""
        raw = bytes(insn.bytes)
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            s = va_to_cstr(image_base + instr_rva + len(raw) + disp)
            if s:
                ann = f"  ; {s!r}"
        # also annotate direct calls
        if insn.mnemonic == "call" and insn.op_str.startswith("0x"):
            tgt = int(insn.op_str, 16)
            # if target in poco ws range note it
            if 0xCA0000 <= tgt <= 0xCB0000:
                ann += "  ; -> Poco WS region"
        lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}{ann}")

    # 8) Check if path could be absolute URI: search format that builds request line
    # Near Host header set for HTTP client session
    lines.append("\n=== Host header set sites (HTTP client) ===")
    # Host\0 near RtspSessionPocoBase was at 0x12a9fe8
    for host_off in [data.find(b"Host\0Poco"), data.find(b"\0Host\0", 0x1127000, 0x1128000)]:
        if host_off is None or host_off < 0:
            continue
        if data[host_off] == 0:
            host_off += 1
        rva = file_to_rva(host_off)
        lines.append(f"Host @{host_off:#x} refs={[hex(x) for x in refs.get(rva, [])[:10]]}")
        for rh in refs.get(rva, [])[:5]:
            lines.extend(strings_near(rh, 0x400)[:20])

    # 9) Poco HTTPRequest::write - look for method + URI concatenation
    # Search ' HTTP/1.1' refs
    lines.append("\n=== ' HTTP/1.1' / write request line ===")
    for needle in [b" HTTP/1.1\0", b"HTTP/1.1\r\n", b" HTTP/\0"]:
        h = data.find(needle)
        lines.append(f"{needle!r} @{h:#x}")
        if h < 0:
            continue
        rva = file_to_rva(h)
        for rh in refs.get(rva, [])[:6]:
            lines.append(f"  ref {rh:#x}")
            lines.extend(strings_near(rh, 0x300)[:15])
            lines.extend(disasm(rh, 0x60, 0x100)[:40])

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
