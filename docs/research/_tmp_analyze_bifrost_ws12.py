#!/usr/bin/env python3
"""Deeper URI resolution: callers, GET method, resourcePath, log tail, Mall rtsps."""
from __future__ import annotations

import re
import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
LOG = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log")
MALL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\Mall")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-uri-resolved.txt")

NVWS_CTOR = 0x466640
NVWS_CONNECT = 0x466D10


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

    def va_to_cstr(va: int, maxlen: int = 200) -> str | None:
        f = rva_to_file(va - image_base)
        if f is None:
            return None
        end = data.find(b"\0", f, f + maxlen)
        if end < 0:
            return None
        raw = data[f:end]
        if all(32 <= b < 127 for b in raw) or raw == b"":
            return raw.decode()
        return None

    def find_cstr(s: bytes) -> list[int]:
        out, start = [], 0
        while True:
            i = data.find(s, start)
            if i < 0:
                break
            out.append(i)
            start = i + 1
        return out

    def rip_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C, 0x4D) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        if len(raw) >= 6 and raw[0] == 0x8D and (raw[1] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 2)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        return None

    def call_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        if raw[0] == 0xE8 and len(raw) == 5:
            return insn.address + 5 + struct.unpack_from("<i", raw, 1)[0]
        return None

    def disasm_range(start: int, end: int) -> list[str]:
        out = []
        for insn in md.disasm(data[start:end], start):
            extra = ""
            t = rip_target(insn)
            if t is not None:
                s = va_to_cstr(t)
                extra = f"  ; {s!r}" if s is not None else f"  ; va={t:#x}"
            ct = call_target(insn)
            if ct is not None:
                extra += f"  ; -> {ct:#x}"
            out.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")
        return out

    # ================================================================
    # Byte-scan for E8 calls to NVWS_CTOR and NVWS_CONNECT
    # ================================================================
    lines.append("# Deep follow-up: callers / GET / resourcePath / log / Mall")
    lines.append("")

    def find_e8_callers(target: int) -> list[int]:
        callers = []
        # scan .text raw for E8 rel32
        blob = data[trawptr : trawptr + trawsize]
        i = 0
        while True:
            j = blob.find(b"\xE8", i)
            if j < 0:
                break
            rel = struct.unpack_from("<i", blob, j + 1)[0]
            abs_addr = (trawptr + j) + 5 + rel
            if abs_addr == target:
                callers.append(trawptr + j)
            i = j + 1
        return callers

    ctor_callers = find_e8_callers(NVWS_CTOR)
    conn_callers = find_e8_callers(NVWS_CONNECT)
    lines.append(f"E8 callers of ctor 0x466640: {[hex(x) for x in ctor_callers]}")
    lines.append(f"E8 callers of connect 0x466d10: {[hex(x) for x in conn_callers]}")

    # Also try RVA-based (in case addresses are RVAs not file offs)
    # In this PE, .text vaddr==rawptr typically for simple layout? check
    lines.append(f".text vaddr={tvaddr:#x} rawptr={trawptr:#x}")

    for c in ctor_callers[:8]:
        lines.append(f"\n## ctor caller @{c:#x} — 0x220 bytes before")
        for row in disasm_range(max(trawptr, c - 0x220), c + 5):
            lines.append(row)

    for c in conn_callers[:8]:
        lines.append(f"\n## connect caller @{c:#x} — 0x100 bytes before")
        for row in disasm_range(max(trawptr, c - 0x100), c + 5):
            lines.append(row)

    # ================================================================
    # Decode HTTPRequest ctor arg mapping with actual strings
    # ================================================================
    lines.append("\n## HTTPRequest ctor arg decode (0x4666a6)")
    # r9 = version HTTP/1.1
    # r8 = rbx = URI from stack arg
    # rdx = empty = METHOD
    for insn in md.disasm(data[0x4666A6:0x4666C0], 0x4666A6):
        t = rip_target(insn)
        s = va_to_cstr(t) if t else None
        lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str} ; {s!r} va={t}")

    # What is at empty string used as method?
    empty_va = None
    for insn in md.disasm(data[0x4666B0:0x4666B7], 0x4666B0):
        empty_va = rip_target(insn)
    lines.append(f"method cstr va={empty_va} value={va_to_cstr(empty_va)!r}")

    # Find where GET is assigned — search for lea of 'GET' near Net/WS
    get_locs = find_cstr(b"GET\0")
    lines.append(f"\n## GET\\0 cstring locs: {[hex(x) for x in get_locs[:20]]}")
    for fo in get_locs:
        rva = file_to_rva(fo)
        if rva is None:
            continue
        va = image_base + rva
        # only care if looks like exact GET (3 chars)
        s = va_to_cstr(va)
        if s != "GET":
            continue
        refs = []
        blob = data[trawptr : trawptr + trawsize]
        # find lea/mov rip-relative to this VA
        for insn in md.disasm(blob, trawptr):
            if rip_target(insn) == va:
                refs.append(insn.address)
        if refs:
            lines.append(f"  GET @{fo:#x} va={va:#x} refs={ [hex(r) for r in refs[:20]] }")
            for r in refs[:6]:
                lines.append(f"  --- around {r:#x} ---")
                for row in disasm_range(r - 0x40, r + 0x60)[:25]:
                    lines.append("  " + row)

    # Also HTTP_GET might be a static std::string object
    # Search for bytes 'G','E','T',0 in SSO init near ca7900 / c915c0
    lines.append("\n## Imm 'GET' (0x544547) stores near WS/HTTP")
    for start, end, label in [
        (0xCA7900, 0xCA7E00, "handshake"),
        (0xCA61D0, 0xCA6300, "ws wrapper"),
        (0xC915C0, 0xC91800, "httpreq ctors"),
        (0xCA5A00, 0xCA5C00, "httpmessage"),
        (0x466640, 0x466900, "nvws ctor"),
    ]:
        hits = []
        for insn in md.disasm(data[start:end], start):
            if "0x544547" in insn.op_str or "0x474554" in insn.op_str:
                hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
            # also dword 0x20544547 'GET '
            if "0x20544547" in insn.op_str:
                hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str} ; 'GET '?")
        lines.append(f"  {label}: {hits}")

    # ================================================================
    # ca61d0 r9 string — WebSocket ctor third arg?
    # ================================================================
    lines.append("\n## ca61d0 / ca6220 wrapper string args")
    for row in disasm_range(0xCA61D0, 0xCA6260):
        lines.append(row)

    # ================================================================
    # Find HTTPRequest::write — look for method+uri+version stream pattern
    # Poco writes: ostr << getMethod() << " " << getURI() << " " << getVersion() << "\r\n"
    # Search for functions loading +0x50 and +0x70 from same object
    # ================================================================
    lines.append("\n## HTTPRequest write: functions loading both +0x50 and +0x70")
    # Scan Poco Net region for functions that reference both offsets from same base reg
    # Simpler: find 'HTTP/1.0' and 'HTTP/1.1' used in write validation
    for needle in [b"HTTP/1.0\0", b"Invalid HTTP request", b"HTTP message", b"\r\n"]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: n={len(locs)} first={[hex(x) for x in locs[:5]]}")

    # Find setMethod by looking for assign to +0x50 with string from GET
    # Disasm ca5a10 (called at start of HTTPRequest ctor) 
    lines.append("\n## ca5a10 (HTTPMessage/HTTPRequest base init)")
    for row in disasm_range(0xCA5A10, 0xCA5A80):
        lines.append(row)

    # ================================================================
    # resourcePath / session path near RTSP/WS
    # ================================================================
    lines.append("\n## resourcePath / session URL strings")
    for needle in [
        b"resourcePath",
        b"/v2/session",
        b"streamingSessionId",
        b"sessionid",
        b"x-nv-sessionid",
        b"getPath",
        b"getPathEtc",
        b"setURI",
        b"setPath",
        b"pathEtc",
    ]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: n={len(locs)} {[hex(x) for x in locs[:6]]}")

    # xrefs to x-nv-sessionid and streamingSessionId
    for name, needle in [("x-nv-sessionid", b"x-nv-sessionid\0"), ("streamingSessionId", b"streamingSessionId :")]:
        locs = find_cstr(needle)
        if not locs:
            locs = find_cstr(needle.rstrip(b"\0"))
        for fo in locs[:1]:
            rva = file_to_rva(fo)
            va = image_base + rva
            refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
            lines.append(f"  {name} refs: {[hex(r) for r in refs[:10]]}")
            for r in refs[:2]:
                lines.append(f"  --- {r:#x} ---")
                for row in disasm_range(r - 0x60, r + 0xA0)[:30]:
                    lines.append("  " + row)

    # ================================================================
    # URI from Poco::URI getPathEtc — find getPathEtc / empty path handling
    # ================================================================
    lines.append("\n## Poco URI path helpers / empty path")
    for needle in [b"getPathEtc", b"getPathAndQuery", b"rawPath", b"URI.cpp", b"bad path"]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: {[hex(x) for x in locs[:5]]}")

    # ================================================================
    # Secure session creation — 0x8a6000 (HTTPS) vs 0xc9b9e0 (HTTP)
    # from connect: byte [rbx+0x10] selects secure
    # ================================================================
    lines.append("\n## Secure vs plain session ctors")
    lines.append("### HTTPS-ish 0x8a6000 (first 0x80)")
    for row in disasm_range(0x8A6000, 0x8A6080):
        lines.append(row)
    lines.append("### HTTP 0xc9b9e0 (first 0x80)")
    for row in disasm_range(0xC9B9E0, 0xC9BA60):
        lines.append(row)

    # Creating Secure string refs
    fo = find_cstr(b"Creating Secure")[0]
    rva = file_to_rva(fo)
    va = image_base + rva
    refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
    lines.append(f"Creating Secure refs: {[hex(r) for r in refs]}")
    for r in refs[:2]:
        for row in disasm_range(r - 0x40, r + 0x80)[:30]:
            lines.append(row)

    # ================================================================
    # 2-way / 1-way — find via file offset string neighborhood code refs
    # String at 0x12a9d28 — try scanning for push/lea of nearby
    # ================================================================
    lines.append("\n## 1-way/2-way neighborhood + protocol TAG fn")
    # protocol TAG string refs
    fo = find_cstr(b"Establishing RTSP session with protocol TAG")[0]
    rva = file_to_rva(fo)
    va = image_base + rva
    refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
    lines.append(f"Establishing RTSP refs: {[hex(r) for r in refs]}")
    for r in refs[:1]:
        # dump larger function window looking for URI/path construction
        lines.append(f"### large window around {r:#x}")
        for row in disasm_range(r - 0x200, r + 0x300):
            # filter to interesting
            if any(k in row for k in ("call", "lea", ";", "0x2f", "mov r8", "mov rdx", "mov r9")):
                lines.append(row)

    # Scheme mismatch warning
    fo = find_cstr(b"RTSP session requested scheme")[0]
    rva = file_to_rva(fo)
    va = image_base + rva
    refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
    lines.append(f"\nscheme mismatch refs: {[hex(r) for r in refs]}")
    for r in refs[:1]:
        for row in disasm_range(r - 0x100, r + 0x150):
            if "call" in row or ";" in row or "lea" in row:
                lines.append(row)

    # ================================================================
    # geronimo.log: from Connecting to host through WSS Options
    # ================================================================
    lines.append("\n## geronimo.log: Connecting → WSS Options sequence")
    text_log = LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    # find indices
    idxs = [i for i, ln in enumerate(text_log) if "Connecting to host" in ln and "322" in ln]
    lines.append(f"Connecting to host ... 322 hits: {len(idxs)} at lines {[i+1 for i in idxs[:5]]}")
    for i in idxs[:2]:
        start = max(0, i - 5)
        end = min(len(text_log), i + 40)
        lines.append(f"--- context line {i+1} ---")
        for ln in text_log[start:end]:
            lines.append("  " + ln.strip()[:320])

    # Also WSS Options
    opts = [i for i, ln in enumerate(text_log) if "WSS Options" in ln or "Options:" in ln and "rtsps" in ln.lower()]
    lines.append(f"\nWSS Options-like hits: {len(opts)}")
    for i in opts[:5]:
        for ln in text_log[max(0, i - 3) : i + 5]:
            lines.append("  " + ln.strip()[:320])
        lines.append("  ---")

    # Certificate / client cert near connect
    cert_lines = [ln.strip() for ln in text_log if re.search(r"client.?cert|2-way|1-way|mTLS|mutual", ln, re.I)]
    lines.append(f"\nclient-cert/2-way log lines: {len(cert_lines)}")
    for ln in cert_lines[:20]:
        lines.append("  " + ln[:300])

    # ================================================================
    # Mall: rtsps / nvst / classic context
    # ================================================================
    lines.append("\n## Mall JS: rtsps/nvst/classic context snippets")
    for jf in MALL.glob("*.js"):
        t = jf.read_text(encoding="utf-8", errors="replace")
        for pat in [r".{0,80}rtsps.{0,80}", r".{0,60}classic.{0,80}", r".{0,40}NVST.{0,80}", r".{0,40}nvst.{0,80}"]:
            for m in re.finditer(pat, t, re.I):
                sn = m.group(0).replace("\n", " ")
                if "rtsps" in sn.lower() or "classic" in sn.lower() or "nvst" in sn.lower():
                    lines.append(f"  {jf.name}: {sn[:220]}")
                    if sum(1 for l in lines if l.startswith("  " + jf.name)) > 25:
                        break
            if sum(1 for l in lines if "Mall JS" in l or l.startswith("  ")) > 80:
                break

    # Count whether Mall uses port 322 or only WebRTC signaling
    lines.append("\n## Mall verdict helpers")
    all_js = ""
    for jf in MALL.glob("*.js"):
        all_js += jf.read_text(encoding="utf-8", errors="replace")
    for term in ["rtsps://", "wss://", ":322", ":48322", "appLevelProtocol", "protocol\":0", "NVB_PU_RTSPS", "Secure WebSocket", "webrtc", "RTCPeerConnection"]:
        lines.append(f"  {term!r}: {all_js.lower().count(term.lower())}")

    # ================================================================
    # Critical: does HTTPClientSession rewrite empty URI?
    # Find sendRequest implementation via string
    # ================================================================
    lines.append("\n## sendRequest string refs → HTTPClientSession::sendRequest")
    for fo in find_cstr(b"sendRequest")[:6]:
        # might be mangled or log
        s = data[fo : fo + 40].split(b"\0")[0]
        lines.append(f"  @{fo:#x}: {s!r}")

    # Look for HTTPClientSession.cpp path and nearby PDB-less; find write of request line
    # Search for pattern: space character written between strings — hard
    # Alternative: dump function that references both getMethod-like and writes CRLF
    crlf_fo = None
    # Find std::string "\r\n" used in HTTP writes — often SSO
    # Search code that does mov word ptr [...], 0x0a0d
    lines.append("\n## 0x0a0d (CRLF) stores in Poco Net cluster")
    crlf_hits = []
    for insn in md.disasm(data[0xCA0000:0xCB8000], 0xCA0000):
        if "0xa0d" in insn.op_str or "0x0a0d" in insn.op_str:
            crlf_hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    lines.append(f"count={len(crlf_hits)}")
    for h in crlf_hits[:40]:
        lines.append("  " + h)

    # For each CRLF hit, check if nearby loads +0x50/+0x70
    lines.append("\n## CRLF sites with nearby +0x50/+0x70 (request-line write)")
    for insn in md.disasm(data[0xCA0000:0xCB8000], 0xCA0000):
        if "0xa0d" not in insn.op_str and "0x0a0d" not in insn.op_str:
            continue
        window = list(md.disasm(data[insn.address - 0x80 : insn.address + 0x40], insn.address - 0x80))
        ops = " ".join(i.op_str for i in window)
        if ("+ 0x70]" in ops or "+0x70]" in ops) and ("+ 0x50]" in ops or "+0x50]" in ops):
            lines.append(f"  CANDIDATE write @{insn.address:#x}")
            for row in disasm_range(insn.address - 0xA0, insn.address + 0x30):
                lines.append("  " + row)

    # ================================================================
    # Prior known caller 0x721e83 — dump regardless
    # ================================================================
    lines.append("\n## Prior-noted caller region 0x721e00")
    # Check if E8 to ctor exists nearby via byte scan
    region = data[0x721000:0x723000]
    for j in range(len(region) - 5):
        if region[j] != 0xE8:
            continue
        rel = struct.unpack_from("<i", region, j + 1)[0]
        abs_addr = 0x721000 + j + 5 + rel
        if abs_addr in (NVWS_CTOR, NVWS_CONNECT, 0x466640):
            lines.append(f"  found call @{0x721000+j:#x} -> {abs_addr:#x}")
    for row in disasm_range(0x721E00, 0x722100):
        if any(k in row for k in ("call", "lea", "mov qword", "xor", "0xf", ";")):
            lines.append(row)

    # Search ALL e8 to 0x466640 using file-offset arithmetic more carefully
    # Maybe ctor is not at file offset 0x466640 but RVA — check section mapping
    rva_ctor = file_to_rva(0x466640)
    lines.append(f"\nfile 0x466640 -> rva {rva_ctor}")
    # If rawptr == vaddr for .text, file offset == RVA for code
    lines.append(f"same? {rva_ctor == 0x466640}")

    # Try finding calls where target RVA equals 0x466640
    # When disasm address is file offset and .text rawptr==vaddr, OK
    # Check first bytes at 0x466640
    lines.append(f"bytes at 0x466640: {data[0x466640:0x466650].hex()}")
    lines.append(f"bytes at 0x466d10: {data[0x466d10:0x466d20].hex()}")

    # Search for call pattern using RVA if different
    if rva_ctor and rva_ctor != 0x466640:
        lines.append(f"Trying RVA callers of {rva_ctor:#x}")
        lines.append(f"  {[hex(x) for x in find_e8_callers(rva_ctor)]}")

    # Broader: any E8 whose target is within 0x466640..0x466650
    near = []
    blob = data[trawptr : trawptr + trawsize]
    for j in range(0, len(blob) - 5):
        if blob[j] != 0xE8:
            continue
        rel = struct.unpack_from("<i", blob, j + 1)[0]
        abs_addr = trawptr + j + 5 + rel
        if 0x466640 <= abs_addr < 0x466700:
            near.append((trawptr + j, abs_addr))
    lines.append(f"E8 targeting 0x466640..0x466700: {[(hex(a), hex(b)) for a,b in near[:20]]}")

    # Maybe it's called via JMP or through a pointer — search for immediate 0x466640 as absolute
    imm = struct.pack("<I", 0x466640)
    imm_va = struct.pack("<Q", image_base + 0x466640)
    lines.append(f"imm32 0x466640 count in dll: {data.count(imm)}")
    lines.append(f"imm64 image_base+0x466640 count: {data.count(imm_va)}")

    # Find references by scanning for lea rcx, ...; call that looks like construction
    # Search string 'Connecting to host' caller path upward to NvWS creation
    fo = find_cstr(b"Connecting to host %s, port %hu")[0]
    rva = file_to_rva(fo)
    va = image_base + rva
    refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
    lines.append(f"Connecting refs: {[hex(r) for r in refs]}")

    # Who calls connect function? find E8 to 0x466d10
    lines.append(f"connect E8 callers detail count={len(conn_callers)}")

    # Find who constructs NvWebSocketSession — search for size allocation 0x1e0 or similar before call
    # Object size from ctor: uses fields up to 0x1d8+ — large
    # Search for `mov ecx, XXX; call alloc; call 0x466640`
    lines.append("\n## Alloc-then-ctor pattern near 0x466640")
    for a, tgt in near[:20]:
        for row in disasm_range(a - 0x40, a + 5):
            lines.append(row)

    # ================================================================
    # Check OpenSSLCertUtils — client cert vs trust store
    # ================================================================
    lines.append("\n## OpenSSLCertUtils / Certificate bag")
    for needle in [b"Certificate bag", b"OpenSSLCertUtils", b"usePrivateKey", b"addCertificateAuthority", b"loadCertificate"]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: n={len(locs)} {[hex(x) for x in locs[:4]]}")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
