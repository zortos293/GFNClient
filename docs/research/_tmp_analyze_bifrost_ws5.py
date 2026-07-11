#!/usr/bin/env python3
"""Trace wss/rtsps URI rewrite and pointer-table consumers for WS path."""
from __future__ import annotations

import struct
from collections import defaultdict
from pathlib import Path

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-uri.txt")
LOG = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log")


def parse_pe(data: bytes):
    e_lfanew = struct.unpack_from("<I", data, 0x3C)[0]
    coff = e_lfanew + 4
    num = struct.unpack_from("<H", data, coff + 2)[0]
    opt_size = struct.unpack_from("<H", data, coff + 16)[0]
    opt = coff + 20
    magic = struct.unpack_from("<H", data, opt)[0]
    image_base = (
        struct.unpack_from("<Q", data, opt + 24)[0]
        if magic == 0x20B
        else struct.unpack_from("<I", data, opt + 28)[0]
    )
    sec_off = opt + opt_size
    sections = []
    for i in range(num):
        off = sec_off + i * 40
        name = data[off : off + 8].split(b"\0", 1)[0].decode("ascii", "ignore")
        vsize, vaddr, rawsize, rawptr = struct.unpack_from("<IIII", data, off + 8)
        sections.append((name, vaddr, vsize, rawptr, rawsize))
    return image_base, sections


def main() -> None:
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64

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

    def va_to_cstr(va: int, maxlen: int = 300) -> str | None:
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
    end = trawptr + trawsize - 7
    while i < end:
        if data[i] in (0x48, 0x4C) and data[i + 1] in (0x8D, 0x8B) and (data[i + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 3)[0]
            instr_rva = tvaddr + (i - trawptr)
            refs[instr_rva + 7 + disp].append(i)
        i += 1

    def dump_near(file_off: int, radius: int = 0x1000) -> list[str]:
        out = []
        start = max(trawptr, file_off - radius)
        endw = min(trawptr + trawsize, file_off + radius)
        k = start
        seen = set()
        while k < endw - 7:
            if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
                disp = struct.unpack_from("<i", data, k + 3)[0]
                instr_rva = tvaddr + (k - trawptr)
                target_rva = instr_rva + 7 + disp
                s = va_to_cstr(image_base + target_rva)
                if s and (k, s) not in seen:
                    seen.add((k, s))
                    out.append(f"  @{k:#x} -> {s!r}")
            k += 1
        return out

    def disasm(file_off: int, before: int = 0x100, after: int = 0x200) -> list[str]:
        start = max(trawptr, file_off - before)
        length = before + after
        out = []
        for insn in md.disasm(data[start : start + length], start):
            mark = ">>" if abs(insn.address - file_off) < 8 else "  "
            out.append(f"{mark}{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
        return out

    # --- wss:// and rtsps:// refs ---
    for needle in [b"wss://\0", b"rtsps://\0", b"ws://\0", b"rtsp://\0"]:
        start = 0
        while True:
            h = data.find(needle, start)
            if h < 0:
                break
            rva = file_to_rva(h)
            rlist = refs.get(rva, []) if rva else []
            s = needle[:-1].decode()
            lines.append(f"\n=== {s!r} @{h:#x} rva={rva:#x} refs={len(rlist)} {[hex(x) for x in rlist]} ===")
            for rh in rlist:
                lines.append(f"-- strings near {rh:#x} --")
                lines.extend(dump_near(rh, 0x800))
                lines.append(f"-- disasm {rh:#x} --")
                lines.extend(disasm(rh, 0x80, 0x180)[:60])
            start = h + 1

    # --- pointer table slot for /rtsp: who loads [rip+disp] pointing AT the table entry? ---
    # Table is at file 0x1204700, rva = file_to_rva(0x1204700)
    table_file = 0x1204700
    table_rva = file_to_rva(table_file)
    lines.append(f"\n=== /rtsp pointer slot table_rva={table_rva:#x} ===")
    # RIP-rel loads of the SLOT address (lea of the pointer itself)
    if table_rva is not None:
        slot_refs = refs.get(table_rva, [])
        lines.append(f"lea of slot itself: {len(slot_refs)} {[hex(x) for x in slot_refs]}")
        for rh in slot_refs:
            lines.extend(dump_near(rh, 0x400))
            lines.extend(disasm(rh, 0x40, 0x80)[:40])

    # Also scan for lea of nearby table base (0x12046e8 RTSP, 0x1204758 WSS, etc.)
    for label, foff in [
        ("RTSP_slot", 0x12046E8),
        ("RTSPS_slot", 0x12046F8),
        ("rtsp_path_slot", 0x1204700),
        ("WSS_slot", 0x1204758),
        ("WS_slot", 0x1204760),
        ("OPTIONS_slot", 0x12046C0),
        ("table_base_guess", 0x1204680),
    ]:
        rva = file_to_rva(foff)
        if rva is None:
            continue
        rlist = refs.get(rva, [])
        lines.append(f"{label} @{foff:#x} rva={rva:#x} lea_refs={len(rlist)} {[hex(x) for x in rlist[:10]]}")

    # Scan for mov reg, [rip+disp] where target is in 0x1204500-0x1204900 (load pointer FROM table)
    lines.append("\n=== rip-relative LOADS from pointer table region ===")
    region_lo, region_hi = 0x1204500, 0x1204900
    # Convert file offs of region to RVAs
    region_rva_lo = file_to_rva(region_lo)
    region_rva_hi = file_to_rva(region_hi)
    load_hits = []
    i = trawptr
    while i < trawptr + trawsize - 7:
        # 48 8B 05 disp32 = mov rax, [rip+disp]
        # 48 8B 0D = mov rcx
        # 48 8B 15 = mov rdx
        # 4C 8B 05 = mov r8
        if data[i] in (0x48, 0x4C) and data[i + 1] == 0x8B and (data[i + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 3)[0]
            instr_rva = tvaddr + (i - trawptr)
            target_rva = instr_rva + 7 + disp
            if region_rva_lo is not None and region_rva_lo <= target_rva < region_rva_hi:
                q = struct.unpack_from("<Q", data, rva_to_file(target_rva))[0]
                s = va_to_cstr(q)
                load_hits.append((i, target_rva, s))
        i += 1
    lines.append(f"load hits: {len(load_hits)}")
    for h, tr, s in load_hits[:40]:
        lines.append(f"  @{h:#x} loads rva={tr:#x} -> {s!r}")
        lines.extend(disasm(h, 0x30, 0x50)[:20])

    # --- GET ' ' string at 0x12a6a90 ---
    get_sp = data.find(b"GET \0")
    if get_sp < 0:
        get_sp = data.find(b"GET ")
    lines.append(f"\n=== GET[space] @{get_sp:#x} ===")
    if get_sp >= 0:
        rva = file_to_rva(get_sp)
        for rh in refs.get(rva, []):
            lines.append(f"-- near {rh:#x} --")
            lines.extend(dump_near(rh, 0x600))
            lines.extend(disasm(rh, 0x60, 0x120)[:50])

    # --- GET\0 at 0xfeb9f4 ---
    get0 = 0xFEB9F4
    rva = file_to_rva(get0)
    lines.append(f"\n=== GET\\0 @{get0:#x} rva={rva:#x} refs={refs.get(rva, [])} ===")
    for rh in refs.get(rva, []):
        lines.append(f"-- near {rh:#x} --")
        lines.extend(dump_near(rh, 0x800))
        lines.extend(disasm(rh, 0x80, 0x150)[:60])

    # --- NvWebSocketSession / WebSocket constructor path ---
    for needle in [
        b"NvWebSocketSession\0",
        b"WebSocket Loop Starting.\0",
        b"Creating Secure RTSP handshake",
        b"WSS client using",
        b"%s client using %s SSL\0",
        b"HTTP/1.1\0",
    ]:
        h = data.find(needle)
        lines.append(f"\n{needle!r} @{h:#x}")
        if h < 0:
            continue
        rva = file_to_rva(h)
        for rh in refs.get(rva, [])[:4]:
            lines.append(f"-- refs {rh:#x} strings --")
            lines.extend(dump_near(rh, 0x600)[:40])

    # Find Poco WebSocket::WebSocket that sets Upgrade headers — look for
    # sequential lea of Sec-WebSocket-Key, upgrade, websocket, 13
    key_off = data.find(b"Sec-WebSocket-Key\0")
    key_rva = file_to_rva(key_off) if key_off >= 0 else None
    lines.append(f"\n=== Sec-WebSocket-Key refs (should be Poco WS handshake) ===")
    if key_rva:
        # refs=0 earlier — maybe accessed via relative within same module differently
        # Search for the string address as immediate in movabs
        va = image_base + key_rva
        pat = struct.pack("<Q", va)
        abs_hits = []
        start = 0
        while True:
            j = data.find(pat, start)
            if j < 0:
                break
            abs_hits.append(j)
            start = j + 1
        lines.append(f"abs VA hits for Sec-WebSocket-Key: {[hex(x) for x in abs_hits[:20]]}")
        # Also try finding nearby 'upgrade' + 'websocket' used together in code via RIP
        up_off = data.find(b"upgrade\0websocket\0")
        lines.append(f"upgrade\\0websocket block @{up_off:#x}")
        if up_off >= 0:
            # individual
            for sub in [b"upgrade\0", b"websocket\0", b"13\0", b"Sec-WebSocket-Version\0"]:
                so = data.find(sub, 0x1127300, 0x1127600)
                if so < 0:
                    continue
                sr = file_to_rva(so)
                lines.append(f"  {sub!r} @{so:#x} refs={len(refs.get(sr, []))} {[hex(x) for x in refs.get(sr, [])[:8]]}")

    # Broader: any RIP ref into Poco WS string block 0x1127388-0x1127580
    lines.append("\n=== any RIP refs into Poco WS handshake string block ===")
    block_lo = file_to_rva(0x1127388)
    block_hi = file_to_rva(0x1127580)
    if block_lo and block_hi:
        for rva, rlist in refs.items():
            if block_lo <= rva < block_hi and rlist:
                s = va_to_cstr(image_base + rva)
                lines.append(f"  rva={rva:#x} {s!r} refs={[hex(x) for x in rlist[:6]]}")
                for rh in rlist[:1]:
                    lines.extend(disasm(rh, 0x40, 0x100)[:30])

    # --- Scheme rewrite near 0x6fb900: look for path '/' or empty ---
    # Search for cmp/lea of single-char '/' near establish/connect
    lines.append("\n=== single-char '/' cstring refs near RTSP/WS code ===")
    # find \0/\0 as standalone
    start = 0
    slash_hits = []
    while True:
        j = data.find(b"/\0", start)
        if j < 0:
            break
        if j > 0 and 32 <= data[j - 1] < 127:
            start = j + 1
            continue
        rva = file_to_rva(j)
        rlist = refs.get(rva, []) if rva else []
        if rlist:
            slash_hits.append((j, rlist))
        start = j + 1
    lines.append(f"standalone '/' with refs: {len(slash_hits)}")
    for j, rlist in slash_hits[:30]:
        # filter to refs near connect/establish/websocket regions
        interesting = [x for x in rlist if 0x400000 <= x <= 0x800000 or 0xEE0000 <= x <= 0xF00000 or 0xC90000 <= x <= 0xCB0000]
        if not interesting:
            interesting = rlist[:2]
        if interesting:
            lines.append(f"  '/' @{j:#x} refs={[hex(x) for x in interesting[:6]]}")
            for rh in interesting[:1]:
                lines.extend(dump_near(rh, 0x200)[:15])

    # --- Look at HTTPRequest construction: Poco uses HTTP_GET = "GET" ---
    # Search RTTI / vtable area usage is hard. Instead find calls near
    # "Cannot upgrade to WebSocket" which is in WebSocket.cpp handshake response check
    cannot = data.find(b"Cannot upgrade to WebSocket connection\0")
    lines.append(f"\nCannot upgrade @{cannot:#x} refs={refs.get(file_to_rva(cannot), []) if cannot>=0 else []}")
    if cannot >= 0:
        for rh in refs.get(file_to_rva(cannot), []):
            lines.extend(dump_near(rh, 0x1000)[:50])
            lines.extend(disasm(rh, 0x200, 0x100)[:80])

    # --- geronimo: extract session JSON snippet with ports / resourcePath ---
    lines.append("\n=== geronimo session resourcePath / ports ===")
    text = LOG.read_text(encoding="utf-8", errors="replace")
    import re
    for pat in [
        r"resourcePath[^,]{0,120}",
        r"rtsps://[^\"'\\s]+",
        r"\"port\":\s*322",
        r"usage.:?\s*14",
        r"appLevelProtocol.:?\s*\d+",
        r"x-nv-sessionid[^\\s,]{0,80}",
        r"Sec-WebSocket-Protocol",
        r"nvst/",
    ]:
        ms = list(re.finditer(pat, text))
        lines.append(f"{pat}: {len(ms)}")
        for m in ms[:8]:
            a = max(0, m.start() - 60)
            b = min(len(text), m.end() + 60)
            lines.append("  " + text[a:b].replace("\n", " ")[:240])

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} lines={len(lines)}")


if __name__ == "__main__":
    main()
