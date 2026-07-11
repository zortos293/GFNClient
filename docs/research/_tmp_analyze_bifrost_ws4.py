#!/usr/bin/env python3
"""Deep Bifrost2 WS upgrade path / HTTPRequest construction analysis."""
from __future__ import annotations

import re
import struct
from collections import defaultdict
from pathlib import Path

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-deep.txt")
CEF = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF")
LOG = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log")
LOG_BAK = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log.bak")
PLAY = Path(r"C:\Users\Zortos\Documents\research-nvidia\geforcenow-play-js-runtime\beautified")
RESEARCH = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research")


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


def disasm_window(data: bytes, start: int, length: int) -> list[str]:
    """Lightweight x64 decode for lea/mov/call/jmp/cmp/test around a site."""
    try:
        from capstone import Cs, CS_ARCH_X86, CS_MODE_64  # type: ignore
    except ImportError:
        return ["(capstone not installed)"]
    md = Cs(CS_ARCH_X86, CS_MODE_64)
    md.detail = False
    out = []
    for insn in md.disasm(data[start : start + length], start):
        out.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    return out


def main() -> None:
    data = DLL.read_bytes()
    image_base, sections = parse_pe(data)
    lines: list[str] = []
    lines.append(f"image_base={image_base:#x} size={len(data)}")

    def file_to_rva(foff: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if rawptr <= foff < rawptr + rawsize:
                return vaddr + (foff - rawptr)
        return None

    def rva_to_file(rva: int) -> int | None:
        for name, vaddr, vsize, rawptr, rawsize in sections:
            if vaddr <= rva < vaddr + max(vsize, rawsize):
                return rawptr + (rva - vaddr)
        return None

    def va_to_cstr(va: int, maxlen: int = 400) -> str | None:
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
    rdata = next((s for s in sections if s[0] in (".rdata", ".data")), None)

    print("building rip-relative lea/mov map...")
    refs: dict[int, list[int]] = defaultdict(list)
    # also track absolute VA loads via movabs? rare. Focus RIP-rel.
    i = trawptr
    end = trawptr + trawsize - 7
    while i < end:
        b0, b1, b2 = data[i], data[i + 1], data[i + 2]
        # REX.W lea/mov r64, [rip+disp32]
        if b0 in (0x48, 0x4C) and b1 in (0x8D, 0x8B) and (b2 & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 3)[0]
            instr_rva = tvaddr + (i - trawptr)
            target_rva = instr_rva + 7 + disp
            refs[target_rva].append(i)
        # lea r32, [rip+disp] without REX sometimes
        elif b0 == 0x8D and (b1 & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, i + 2)[0]
            instr_rva = tvaddr + (i - trawptr)
            target_rva = instr_rva + 6 + disp
            refs[target_rva].append(i)
        i += 1

    def dump_string_refs_near(file_off: int, radius: int = 0x2000) -> list[str]:
        """All RIP-rel string loads in [file_off-radius, file_off+radius]."""
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
                if s and s not in seen:
                    seen.add(s)
                    out.append(f"  @{k:#x} -> {s!r}")
            k += 1
        return out

    def find_cstr(needle: bytes) -> list[int]:
        hits = []
        start = 0
        while True:
            i = data.find(needle, start)
            if i < 0:
                break
            hits.append(i)
            start = i + 1
        return hits

    # --- 1. Large windows around WS upgrade sites ---
    upgrade_sites = [0xEEBA0C, 0xEEE482]  # file offsets from prior analysis
    # verify they still point to the string
    for site in list(upgrade_sites):
        # prior used file offsets into .text that load the string
        pass

    # Re-find WS upgrade failed refs
    ws_fail_off = data.find(b"WS upgrade failed: %s\0")
    lines.append(f"\n=== WS upgrade failed string @file {ws_fail_off:#x} ===")
    if ws_fail_off >= 0:
        rva = file_to_rva(ws_fail_off)
        lines.append(f"  rva={rva:#x} refs={len(refs.get(rva, []))}")
        for h in refs.get(rva, []):
            lines.append(f"\n--- LARGE string window around ref {h:#x} (±0x4000) ---")
            lines.extend(dump_string_refs_near(h, 0x4000))
            # disasm ±0x200
            lines.append(f"\n--- disasm around {h:#x} ---")
            try:
                lines.extend(disasm_window(data, max(trawptr, h - 0x180), 0x300)[:80])
            except Exception as e:
                lines.append(f"  disasm err: {e}")

    # --- 2. Search interesting strings ---
    needles = [
        b"GET ",
        b"GET\0",
        b"HTTP/1.1",
        b"HTTP/1.0",
        b"Upgrade",
        b"upgrade",
        b"websocket",
        b"WebSocket",
        b"setURI",
        b"setPath",
        b"HTTPRequest",
        b"Sec-WebSocket-Protocol",
        b"Sec-WebSocket-Key",
        b"/rtsp",
        b"/nvst",
        b"OPTIONS",
        b"RTSP/1.0",
        b"rtsps://",
        b"wss://",
        b"setURI(",
        b"HTTP_GET",
        b"HTTP_OPTIONS",
        b"Connection: Upgrade",
        b"Connection:Upgrade",
    ]
    lines.append("\n=== key string inventory ===")
    for n in needles:
        hits = find_cstr(n)
        # filter to null-terminated-ish for short ones
        shown = []
        for h in hits[:20]:
            # show surrounding cstring
            start = h
            while start > 0 and data[start - 1] >= 32 and data[start - 1] < 127 and h - start < 40:
                start -= 1
            end = data.find(b"\0", h, min(len(data), h + 200))
            s = data[start:end].decode("ascii", "replace") if end > start else "?"
            rva = file_to_rva(h)
            nrefs = len(refs.get(rva, [])) if rva else 0
            rva_s = f"{rva:#x}" if rva is not None else "None"
            shown.append(f"  {h:#x} rva={rva_s} refs={nrefs} {s!r}")
        lines.append(f"{n!r}: count={len(hits)}")
        lines.extend(shown[:12])

    # --- 3. Pointer table around /rtsp — decode as VA pointers ---
    lines.append("\n=== pointer table decode near /rtsp (0x12045f0 region) ===")
    # prior said file 0x1204700 has '/rtsp' as string content OR pointer?
    # Check if region is pointer table of QWORDs
    region = 0x1204500
    for off in range(region, region + 0x400, 8):
        if off + 8 > len(data):
            break
        q = struct.unpack_from("<Q", data, off)[0]
        s = va_to_cstr(q)
        if s:
            lines.append(f"  [{off:#x}] -> {q:#x} = {s!r}")
        elif 0x1000 < q < image_base + len(data):
            # maybe not cstring
            pass

    # Also check if /rtsp at 0x12a9dd0 is only in .rdata as literal and
    # whether any absolute VA reference exists (mov rax, imm64)
    rtsp_path_off = data.find(b"/rtsp\0")
    lines.append(f"\n=== /rtsp absolute VA xref scan ===")
    if rtsp_path_off >= 0:
        rva = file_to_rva(rtsp_path_off)
        va = image_base + rva
        lines.append(f"  file={rtsp_path_off:#x} rva={rva:#x} va={va:#x}")
        # scan for LE little-endian VA bytes in .text and .rdata
        pat = struct.pack("<Q", va)
        abs_hits = []
        start = 0
        while True:
            j = data.find(pat, start)
            if j < 0:
                break
            abs_hits.append(j)
            start = j + 1
        lines.append(f"  abs VA qword hits: {[hex(h) for h in abs_hits[:30]]}")
        # also 32-bit RVA?
        pat32 = struct.pack("<I", rva)
        # too many false positives; only in pointer-sized tables near scheme

    # --- 4. WebSocket constructor / HTTPRequest path in Poco ---
    # Find WebSocket.cpp path if present
    for needle in [
        b"WebSocket.cpp",
        b"HTTPRequest.cpp",
        b"HTTPClientSession.cpp",
        b"HTTPSClientSession.cpp",
        b"RTSPClientSession",
        b"RtspClientSession",
        b"createWebSocket",
        b"HTTPRequest",
        b"HTTP_GET",
        b"HTTP_OPTIONS",
        b"HTTP_PUT",
        b"HTTP_POST",
        b"HTTP_HEAD",
        b"HTTP_CONNECT",
    ]:
        hits = find_cstr(needle + b"\0") if not needle.endswith(b".cpp") else find_cstr(needle)
        if not hits:
            hits = find_cstr(needle)
        lines.append(f"\n{needle!r} hits={len(hits)} first={[hex(h) for h in hits[:5]]}")
        for h in hits[:3]:
            rva = file_to_rva(h)
            if rva is None:
                continue
            rlist = refs.get(rva, [])
            lines.append(f"  @{h:#x} rva={rva:#x} rip_refs={len(rlist)} {[hex(x) for x in rlist[:8]]}")
            for rh in rlist[:3]:
                lines.append(f"  -- strings near {rh:#x} ±0x800 --")
                lines.extend(dump_string_refs_near(rh, 0x800)[:40])

    # --- 5. Connecting to host function — larger window + disasm ---
    conn_off = data.find(b"Connecting to host %s, port %hu\0")
    lines.append(f"\n=== Connecting to host @ {conn_off:#x} ===")
    if conn_off >= 0:
        rva = file_to_rva(conn_off)
        for h in refs.get(rva, []):
            lines.append(f"\n--- strings ±0x3000 around connect ref {h:#x} ---")
            lines.extend(dump_string_refs_near(h, 0x3000))
            lines.append(f"\n--- disasm connect fn around {h:#x} ---")
            lines.extend(disasm_window(data, max(trawptr, h - 0x200), 0x400)[:120])

    # --- 6. Scheme selection ~0x6fb900 — look for path set ---
    # Find Establishing RTSP session
    est_off = data.find(b"Establishing RTSP session with protocol TAG")
    lines.append(f"\n=== Establishing RTSP @ {est_off:#x} ===")
    if est_off >= 0:
        rva = file_to_rva(est_off)
        for h in refs.get(rva, []):
            lines.append(f"\n--- strings ±0x2500 around establish {h:#x} ---")
            lines.extend(dump_string_refs_near(h, 0x2500))
            lines.append(f"\n--- disasm establish {h:#x} ---")
            lines.extend(disasm_window(data, max(trawptr, h - 0x400), 0x600)[:150])

    # --- 7. Look for URI path construction: lea of "/", empty, or getPath ---
    for needle in [b"getPath", b"setPath", b"getURI", b"setURI", b"getHost", b"setHost",
                   b"getPort", b"setPort", b"rawPath", b"getRawPath", b"setScheme",
                   b"getScheme", b"resolve", b"URI(", b"Poco::URI", b"Poco::Net::HTTPRequest"]:
        hits = find_cstr(needle)
        if hits:
            lines.append(f"{needle!r}: {[hex(h) for h in hits[:8]]}")

    # --- 8. HTTPRequest method constants near GET ---
    get_off = data.find(b"GET\0")
    # find all exact GET\0
    lines.append("\n=== exact GET\\0 with refs ===")
    start = 0
    while True:
        h = data.find(b"GET\0", start)
        if h < 0:
            break
        # ensure not part of longer word: prev byte null or non-alpha
        if h > 0 and (65 <= data[h - 1] <= 122):
            start = h + 1
            continue
        rva = file_to_rva(h)
        rlist = refs.get(rva, []) if rva else []
        ctx = data[max(0, h - 16) : h + 32]
        lines.append(f"  {h:#x} refs={len(rlist)} {[hex(x) for x in rlist[:6]]} ctx={ctx!r}")
        for rh in rlist[:2]:
            lines.append(f"  -- near GET ref {rh:#x} ---")
            lines.extend(dump_string_refs_near(rh, 0x600)[:30])
            lines.extend(disasm_window(data, max(trawptr, rh - 0x80), 0x120)[:40])
        start = h + 1

    # --- 9. OPTIONS as first RTSP — refs ---
    lines.append("\n=== OPTIONS / RTSP method refs ===")
    for meth in [b"OPTIONS\0", b"DESCRIBE\0", b"SETUP\0", b"PLAY\0"]:
        h = data.find(meth)
        while h >= 0:
            # skip if mid-word
            if h == 0 or data[h - 1] == 0 or not (65 <= data[h - 1] <= 122):
                rva = file_to_rva(h)
                rlist = refs.get(rva, []) if rva else []
                if rlist:
                    lines.append(f"  {meth[:-1]!r} @{h:#x} refs={len(rlist)} {[hex(x) for x in rlist[:8]]}")
            h = data.find(meth, h + 1)
            if h > 0 and len([x for x in lines if meth[:-1].decode() in x]) > 5:
                break

    # --- 10. Forbidden / 404 / status near upgrade ---
    for needle in [b"Forbidden", b"404", b"Not Found", b"upgrade forbidden", b"WS upgrade"]:
        hits = find_cstr(needle)
        lines.append(f"{needle!r} count={len(hits)} {[hex(h) for h in hits[:6]]}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")

    # --- CEF-wide Sec-WebSocket-Protocol ---
    print("scanning CEF for Sec-WebSocket-Protocol...")
    cef_hits = []
    for p in CEF.rglob("*"):
        if not p.is_file():
            continue
        if p.suffix.lower() not in {".dll", ".exe", ".pak", ".bin", ".dat", ".json", ".js", ".txt", ".log"}:
            # still scan dll/exe primarily
            if p.suffix.lower() not in {".dll", ".exe"}:
                continue
        try:
            if p.stat().st_size > 80_000_000:
                continue
            blob = p.read_bytes()
        except Exception:
            continue
        if b"Sec-WebSocket-Protocol" in blob:
            # count + context
            idx = blob.find(b"Sec-WebSocket-Protocol")
            ctx = blob[max(0, idx - 40) : idx + 80]
            cef_hits.append(f"{p.name}: @{idx:#x} ctx={ctx!r}")
        if b"Sec-WebSocket-Protocol" in blob or b"sec-websocket-protocol" in blob.lower():
            pass
    Path(RESEARCH / "_tmp-cef-sec-websocket-protocol.txt").write_text(
        "\n".join(cef_hits) if cef_hits else "NO HITS in CEF dll/exe", encoding="utf-8"
    )
    print(f"CEF Sec-WebSocket-Protocol hits: {len(cef_hits)}")

    # --- geronimo GET / upgrade ---
    print("scanning geronimo logs...")
    log_lines = []
    for logp in [LOG, LOG_BAK]:
        if not logp.exists():
            continue
        try:
            text = logp.read_text(encoding="utf-8", errors="replace")
        except Exception:
            text = logp.read_bytes().decode("utf-8", "replace")
        pats = [
            r"GET ",
            r"Upgrade",
            r"websocket",
            r"WebSocket",
            r"/rtsp",
            r"wss://",
            r"Sec-WebSocket",
            r"HTTP/1\.[01]",
            r"Connecting to host",
            r"WS upgrade",
            r"Options:",
            r"RTSP Scheme",
            r"protocol TAG",
            r"404",
            r"Forbidden",
            r"streamingSessionId",
            r":322",
        ]
        log_lines.append(f"===== {logp.name} size={len(text)} =====")
        for pat in pats:
            matches = list(re.finditer(pat, text, re.IGNORECASE))
            log_lines.append(f"\n-- {pat!r}: {len(matches)} matches --")
            for m in matches[:15]:
                # line context
                a = text.rfind("\n", 0, m.start()) + 1
                b = text.find("\n", m.end())
                if b < 0:
                    b = min(len(text), m.end() + 200)
                line = text[a:b].strip()
                if len(line) > 300:
                    line = line[:300] + "..."
                log_lines.append(f"  {line}")
    Path(RESEARCH / "_tmp-geronimo-ws-get-hits.txt").write_text("\n".join(log_lines), encoding="utf-8")

    # --- web client port 322 / rtsps ---
    print("scanning play-js beautified...")
    play_hits = []
    if PLAY.is_dir():
        for p in PLAY.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".js", ".mjs", ".ts", ".json", ".txt", ".md"}:
                continue
            try:
                if p.stat().st_size > 30_000_000:
                    continue
                t = p.read_text(encoding="utf-8", errors="replace")
            except Exception:
                continue
            for pat in ["322", "rtsps", "rtsp://", "wss://", "/rtsp", "nvst", "WebSocket", "classic"]:
                if pat in t:
                    # count
                    c = t.count(pat)
                    if c and pat in ("322", "rtsps", "rtsp://", "/rtsp"):
                        # extract snippets
                        for m in re.finditer(re.escape(pat), t):
                            a = max(0, m.start() - 80)
                            b = min(len(t), m.end() + 80)
                            snip = t[a:b].replace("\n", " ")
                            play_hits.append(f"{p.name}:{pat}: {snip}")
                            if sum(1 for x in play_hits if x.startswith(p.name + ":" + pat)) >= 5:
                                break
                    elif pat in ("nvst",) and c:
                        play_hits.append(f"{p.name}: count({pat})={c}")
        # summary for WebRTC-only
        play_hits.insert(0, f"files scanned under {PLAY}")
    Path(RESEARCH / "_tmp-playjs-322-hits.txt").write_text(
        "\n".join(play_hits[:200]) if play_hits else "no play dir", encoding="utf-8"
    )

    # --- research folder moonlight / docs ---
    print("scanning research for moonlight/docs...")
    res_hits = []
    for p in RESEARCH.rglob("*"):
        if not p.is_file():
            continue
        if p.suffix.lower() not in {".md", ".txt", ".json"}:
            continue
        try:
            t = p.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        low = t.lower()
        if any(k in low for k in ("moonlight", "open-source", "websocket upgrade", "wss://", "sec-websocket-protocol")):
            if "moonlight" in low or "websocket upgrade" in low:
                res_hits.append(f"{p.name}: moonlight={low.count('moonlight')} wss={low.count('wss://')}")
    Path(RESEARCH / "_tmp-research-moonlight-hits.txt").write_text(
        "\n".join(res_hits) if res_hits else "none", encoding="utf-8"
    )
    print("done")


if __name__ == "__main__":
    main()
