#!/usr/bin/env python3
"""Resolve what URI Bifrost2 actually sends on :322 WS upgrade after empty construct."""
from __future__ import annotations

import re
import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
GERONIMO = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Geronimo.dll")
LOG = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log")
LOG_BAK = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\geronimo.log.bak")
MALL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\Mall")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-uri-resolved.txt")

# Known Bifrost2 offsets (file offsets into .text / raw PE)
NVWS_CTOR = 0x466640
NVWS_CONNECT = 0x466D10
HTTP_REQ_CTOR = 0xC915C0  # HTTPRequest(method, uri, version)
HTTP_REQ_DEFAULT = 0xC91680  # sets URI to '/'
WS_HANDSHAKE = 0xCA7900
WS_CTOR_WRAPPER = 0xCA61D0
SET_HEADER = 0xCA5810  # NameValueCollection set / HTTPMessage::set


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
    rdata = next(s for s in sections if s[0] == ".rdata")
    tvaddr, trawptr, trawsize = text[1], text[3], text[4]
    md = Cs(CS_ARCH_X86, CS_MODE_64)
    md.detail = False
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

    def va_to_cstr(va: int, maxlen: int = 160) -> str | None:
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
        out = []
        start = 0
        while True:
            i = data.find(s, start)
            if i < 0:
                break
            out.append(i)
            start = i + 1
        return out

    def rip_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        # lea/mov reg, [rip+disp32]
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C, 0x4D) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        if len(raw) >= 7 and raw[0] == 0x48 and raw[1] == 0x8D and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        # plain lea rcx,[rip+disp] without REX sometimes
        if len(raw) >= 6 and raw[0] == 0x8D and (raw[1] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 2)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        return None

    def call_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        if raw[0] == 0xE8 and len(raw) == 5:
            disp = struct.unpack_from("<i", raw, 1)[0]
            return insn.address + 5 + disp
        return None

    def disasm_range(start: int, end: int, annotate: bool = True) -> list[str]:
        out = []
        for insn in md.disasm(data[start:end], start):
            extra = ""
            if annotate:
                t = rip_target(insn)
                if t is not None:
                    s = va_to_cstr(t)
                    if s is not None:
                        extra = f"  ; {s!r}"
                    else:
                        extra = f"  ; va={t:#x}"
                ct = call_target(insn)
                if ct is not None:
                    extra += f"  ; -> {ct:#x}"
                if "0x2f" in insn.op_str:
                    extra += "  ; '/'?"
                if "+0x70" in insn.op_str or "+ 0x70" in insn.op_str:
                    extra += "  ; +0x70 URI?"
            out.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")
        return out

    lines.append("# Bifrost2 :322 WebSocket URI — resolved analysis")
    lines.append(f"DLL size={len(data)} image_base={image_base:#x}")
    lines.append("")
    lines.append("## Live falsifications (OpenNOW 2026-07-09)")
    lines.append("- empty GET  HTTP/1.1 → 400")
    lines.append("- GET / → 404")
    lines.append("- GET /rtsp → 404")
    lines.append("- GET rtsps://host:322 → 404")
    lines.append("- GET wss://host:322 → 404")
    lines.append("- GET https://host:322 → 404")
    lines.append("")

    # ------------------------------------------------------------------
    # A) Who passes URI into NvWebSocketSession ctor?
    # ------------------------------------------------------------------
    lines.append("## A) NvWebSocketSession ctor URI source")
    # Find callers of 0x466640
    callers = []
    for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
        ct = call_target(insn)
        if ct == NVWS_CTOR:
            callers.append(insn.address)
    lines.append(f"callers of NvWebSocketSession ctor 0x466640: {len(callers)}")
    for c in callers[:20]:
        lines.append(f"  caller @{c:#x}")
        # dump 0x80 bytes before call for arg setup
        window_start = max(trawptr, c - 0xC0)
        lines.append("  --- pre-call window ---")
        for row in disasm_range(window_start, c + 5)[-40:]:
            lines.append("  " + row)

    # Inspect empty string at lea in ctor
    lines.append("")
    lines.append("### ctor HTTPRequest args at 0x4666a6")
    for row in disasm_range(0x4666A0, 0x4666C0):
        lines.append(row)
    # What is lea rdx at 0x4666b0?
    for insn in md.disasm(data[0x4666B0:0x4666B7], 0x4666B0):
        t = rip_target(insn)
        if t:
            s = va_to_cstr(t)
            lines.append(f"rdx (method?) target va={t:#x} cstr={s!r}")
    # rbx = URI from stack (rbp+0x67) — find what callers put there

    # ------------------------------------------------------------------
    # B) Stores to +0x70 after construct (URI field)
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## B) Stores / copies targeting +0x70 (URI) near WS path")
    # Scan connect + handshake for +0x70
    for label, start, end in [
        ("NvWS connect", NVWS_CONNECT, NVWS_CONNECT + 0x400),
        ("WS handshake ca7900", WS_HANDSHAKE, WS_HANDSHAKE + 0x700),
        ("WS wrapper ca61d0", WS_CTOR_WRAPPER, WS_CTOR_WRAPPER + 0x100),
        ("HTTPRequest ctor c915c0", HTTP_REQ_CTOR, HTTP_REQ_CTOR + 0x80),
    ]:
        hits = []
        for insn in md.disasm(data[start:end], start):
            if "+0x70" in insn.op_str or "+ 0x70" in insn.op_str:
                hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
        lines.append(f"### {label}: {len(hits)} +0x70 hits")
        for h in hits[:30]:
            lines.append("  " + h)

    # Search for lea rcx,[reg+0x70] followed by call 1c5900 (string assign) in whole text near WS
    lines.append("")
    lines.append("## B2) string-assign to +0x70 across .text (sample)")
    assign_hits = []
    # Look for pattern: lea rcx, [reg+0x70]; ... call 0x1c5900
    for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
        if insn.mnemonic == "lea" and ("+ 0x70]" in insn.op_str or "+0x70]" in insn.op_str):
            # look ahead 8 instructions for call 1c5900
            ahead = list(md.disasm(data[insn.address : insn.address + 0x40], insn.address))
            for a in ahead[:10]:
                ct = call_target(a)
                if ct in (0x1C5900, 0xCA5810, HTTP_REQ_CTOR):
                    assign_hits.append((insn.address, a.address, ct, insn.op_str))
                    break
    lines.append(f"lea +0x70 then string-ish call: {len(assign_hits)}")
    for addr, call_a, ct, op in assign_hits[:40]:
        lines.append(f"  lea@{addr:#x} {op} ; call@{call_a:#x} -> {ct:#x}")

    # ------------------------------------------------------------------
    # C) HTTPClientSession write / proxy rewrite
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## C) Poco HTTPClientSession request-line / proxy behavior")
    for needle in [
        b"HTTPClientSession.cpp",
        b"proxy",
        b"getProxyHost",
        b"setProxy",
        b"HTTP_PROXY",
        b"using proxy",
        b"sendRequest",
        b"HTTPRequest.cpp",
        b"WebSocket.cpp",
    ]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: count={len(locs)} first={[hex(x) for x in locs[:3]]}")

    # Find HTTPMessage::write / HTTPRequest write that emits method SP uri SP version
    # Look for format or sequential writes of method/uri near known HTTP/1.1 string
    http11 = find_cstr(b"HTTP/1.1\0")
    lines.append(f"HTTP/1.1 cstrings: {[hex(x) for x in http11]}")
    # Disasm functions that reference HTTP/1.1 near Net code (file ~0xff0488 and 0x143d4e0)
    for fo in http11:
        rva = file_to_rva(fo)
        if rva is None:
            continue
        va = image_base + rva
        # find code refs via rip-relative
        refs = []
        for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
            t = rip_target(insn)
            if t == va:
                refs.append(insn.address)
        lines.append(f"  refs to HTTP/1.1 @{fo:#x} va={va:#x}: {len(refs)} {[hex(r) for r in refs[:15]]}")

    # Look for empty→slash rewrite: cmp length 0; mov '/', or similar near write
    lines.append("")
    lines.append("## C2) empty-URI → '/' rewrite candidates near HTTP write")
    # Search for mov word/byte with 0x2f near functions that also load HTTP/1.1
    # Known default ctor at c91680 sets '/' — already documented
    lines.append("Default HTTPRequest ctor 0xc91680 sets URI='/' (imm 0x2f) — NOT used by NvWS.")
    lines.append("Connect path uses 0xc915c0 with caller URI (empty).")

    # Disasm HTTPClientSession sendRequest-ish: find 'Connecting' is NvWS; find write path
    # Search for string ' ' (space) writes between method and uri — hard.
    # Instead: find function containing both getURI-like +0x70 load and write to stream
    lines.append("")
    lines.append("### HTTPRequest write candidate: scan for load from +0x70 then stream write")
    # Heuristic: functions in 0xCA5000-0xCB0000 (Poco Net cluster)
    write_cands = []
    for insn in md.disasm(data[0xCA5000:0xCB5000], 0xCA5000):
        if insn.mnemonic in ("lea", "mov") and ("+ 0x70]" in insn.op_str or "+0x70]" in insn.op_str):
            write_cands.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    lines.append(f"Poco Net cluster +0x70 refs: {len(write_cands)}")
    for h in write_cands[:50]:
        lines.append("  " + h)

    # ------------------------------------------------------------------
    # D) setMethod GET — does handshake change URI?
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## D) WS handshake ca7900 — does it touch URI/method?")
    # Dump calls only
    for insn in md.disasm(data[WS_HANDSHAKE : WS_HANDSHAKE + 0x500], WS_HANDSHAKE):
        ct = call_target(insn)
        if ct is not None or rip_target(insn):
            t = rip_target(insn)
            s = va_to_cstr(t) if t else None
            extra = f" -> {ct:#x}" if ct else ""
            if s is not None:
                extra += f" ; {s!r}"
            if insn.mnemonic == "call" or s:
                lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")

    # Identify ca5d80 (called with xor edx,edx before send) — likely setMethod(GET) or clear
    lines.append("")
    lines.append("### 0xca5d80 (called before sendRequest in handshake)")
    for row in disasm_range(0xCA5D80, 0xCA5E80)[:60]:
        lines.append(row)

    # ------------------------------------------------------------------
    # E) Format strings building request URI
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## E) URI/path format strings in Bifrost2")
    for needle in [
        b"GET %s",
        b"%s %s HTTP",
        b"GET /",
        b"rtsps://",
        b"wss://",
        b"https://",
        b"%s://%s:%u",
        b"%s://%s:%hu",
        b"%s://%s:%d",
        b"/%s",
        b"/rtsp",
        b"*",
        b"OPTIONS *",
        b"GET *",
    ]:
        locs = find_cstr(needle if needle.endswith(b"\0") or len(needle) > 3 else needle)
        # for short needles be careful
        if needle in (b"*",):
            # skip noisy
            continue
        lines.append(f"  {needle!r}: {[hex(x) for x in locs[:8]]} (n={len(locs)})")

    # Asterisk as HTTP request-target (OPTIONS * / GET *)
    star_hits = []
    # Look for SSO string with '*' length 1 near WS
    for insn in md.disasm(data[NVWS_CTOR : NVWS_CTOR + 0x300], NVWS_CTOR):
        if "0x2a" in insn.op_str:  # '*'
            star_hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    lines.append(f"imm 0x2a ('*') in NvWS ctor: {star_hits}")

    # ------------------------------------------------------------------
    # F) 2-way SSL / client cert / pre-handshake
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## F) TLS / client-cert / sequencing strings")
    for needle in [
        b"2-way",
        b"1-way",
        b"client certificate",
        b"Client certificate",
        b"SSL_CTX_use_certificate",
        b"useCertificate",
        b"PrivateKey",
        b"Creating Secure",
        b"HTTPSClientSession",
        b"SecureStreamSocket",
        b"Context::verify",
        b"verifyMode",
        b"VerificationMode",
        b"WSS Options",
        b"Connecting to host",
        b"protocol TAG",
        b"Establishing RTSP",
    ]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: n={len(locs)} {[hex(x) for x in locs[:4]]}")

    # Disasm around 2-way / 1-way scheme selection
    twoway = find_cstr(b"2-way\0")
    if twoway:
        fo = twoway[0]
        rva = file_to_rva(fo)
        va = image_base + rva
        refs = []
        for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
            if rip_target(insn) == va:
                refs.append(insn.address)
        lines.append(f"2-way refs: {[hex(r) for r in refs]}")
        for r in refs[:3]:
            lines.append(f"--- around {r:#x} ---")
            for row in disasm_range(r - 0x80, r + 0x120)[:50]:
                lines.append(row)

    # ------------------------------------------------------------------
    # Caller of connect: what URI string is on stack?
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## A2) Deep dive: caller URI argument construction")
    # From prior notes: caller 0x721e83 passes EMPTY SSO at rsp+0x78
    # Verify and dump that function
    for start_guess in [0x721E00, 0x721A00, 0x720000]:
        # find call to 0x466640 near there
        pass
    for c in callers:
        # Find function prologue before caller
        # dump more context for string construction
        lines.append(f"### caller detail @{c:#x}")
        # search backwards for empty string lea or xor length
        for row in disasm_range(c - 0x200, c + 8):
            if any(
                k in row
                for k in (
                    "0xf",
                    "call",
                    "lea",
                    "xor",
                    "mov qword ptr [rsp",
                    "mov qword ptr [rbp",
                    "HTTP",
                    "empty",
                    "0x0",
                )
            ):
                # keep denser: all rows
                pass
        for row in disasm_range(c - 0x180, c + 8):
            lines.append("  " + row)

    # ------------------------------------------------------------------
    # Geronimo.dll quick string scan
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## Geronimo.dll string scan")
    if GERONIMO.exists():
        gdata = GERONIMO.read_bytes()
        lines.append(f"Geronimo.dll size={len(gdata)}")
        for needle in [
            b"Sec-WebSocket",
            b"Connecting to host",
            b"/rtsp",
            b"rtsps://",
            b"wss://",
            b"WebSocket",
            b"GET ",
            b"HTTP/1.1",
            b"x-nv-sessionid",
            b"WSS Options",
            b"port 322",
            b"NvWebSocket",
        ]:
            n = gdata.count(needle)
            i = gdata.find(needle)
            lines.append(f"  {needle!r}: n={n} first={hex(i) if i>=0 else None}")
    else:
        lines.append("Geronimo.dll missing")

    # ------------------------------------------------------------------
    # geronimo.log sequence
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## geronimo.log / .bak sequence around port 322")
    for logpath in (LOG, LOG_BAK):
        if not logpath.exists():
            continue
        text_log = logpath.read_text(encoding="utf-8", errors="replace")
        lines.append(f"### {logpath.name} size={len(text_log)}")
        # Find lines with 322 / WSS / Connecting / upgrade / Options
        pats = re.compile(
            r"(322|WSS|Connecting to host|WS upgrade|WebSocket|Options|Describe|Forbidden|2-way|1-way|certificate|TLS|handshake|sessionid|resourcePath|rtsps)",
            re.I,
        )
        matched = [ln.strip() for ln in text_log.splitlines() if pats.search(ln)]
        lines.append(f"matched lines: {len(matched)}")
        # Dedup consecutive similar, show up to 60 around first Connecting
        shown = 0
        for ln in matched:
            if shown >= 80:
                break
            # truncate long lines
            lines.append("  " + ln[:300])
            shown += 1

    # ------------------------------------------------------------------
    # Mall JS
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## Mall JS NVST / classic / WS scan")
    if MALL.exists():
        js_files = list(MALL.glob("*.js"))
        lines.append(f"js files: {len(js_files)}")
        keys = re.compile(
            r"rtsps|wss://|:322|/rtsp|WebSocket|nvst|NVST|classic|webrtc|RTCPeerConnection",
            re.I,
        )
        totals = {k: 0 for k in ["rtsps", "wss://", ":322", "/rtsp", "WebSocket", "nvst", "classic", "webrtc", "RTCPeerConnection"]}
        samples = []
        for jf in js_files:
            try:
                t = jf.read_text(encoding="utf-8", errors="replace")
            except Exception as e:
                lines.append(f"  read fail {jf.name}: {e}")
                continue
            for k in list(totals):
                totals[k] += len(re.findall(re.escape(k), t, re.I)) if k != "webrtc" else len(re.findall(r"webrtc", t, re.I))
            for m in keys.finditer(t):
                if len(samples) < 30:
                    start = max(0, m.start() - 40)
                    end = min(len(t), m.end() + 60)
                    snippet = t[start:end].replace("\n", " ")
                    samples.append(f"  {jf.name} @{m.start()}: ...{snippet}...")
        lines.append(f"hit counts: {totals}")
        lines.extend(samples)
        if totals.get("rtsps", 0) == 0 and totals.get(":322", 0) == 0 and totals.get("/rtsp", 0) == 0:
            lines.append("VERDICT: Mall JS has no classic NVST :322/rtsps path — WebRTC-oriented UI only.")
    else:
        lines.append("Mall dir missing")

    # ------------------------------------------------------------------
    # HTTPClientSession: does it rewrite URI for proxy?
    # Find getProxyHost usage near send
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## C3) Proxy rewrite evidence")
    # Poco typically: if proxy, request.setURI(absolute); else path-only
    # Search for code that compares proxy host empty near HTTPClientSession
    proxy_strs = find_cstr(b"Proxy-Connection\0")
    lines.append(f"Proxy-Connection: {[hex(x) for x in proxy_strs]}")
    for fo in proxy_strs[:2]:
        rva = file_to_rva(fo)
        va = image_base + rva
        refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
        lines.append(f"  refs: {[hex(r) for r in refs[:10]]}")
        for r in refs[:2]:
            for row in disasm_range(r - 0x100, r + 0x80)[:40]:
                lines.append("  " + row)

    # ------------------------------------------------------------------
    # Critical: what does ca5d80 do? And vtable send at [rax+0x28]
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## D2) Identify ca5d80 and sendRequest path")
    # Also dump ca8f40 (keepAlive?)
    for label, addr in [("ca5d80", 0xCA5D80), ("ca8f40", 0xCA8F40), ("ca6fa0", 0xCA6FA0)]:
        lines.append(f"### {label} @{addr:#x}")
        for row in disasm_range(addr, addr + 0x100)[:40]:
            lines.append(row)

    # Look at HTTPRequest setMethod — often stores to +0x50
    lines.append("")
    lines.append("## Method field +0x50 writes in handshake")
    for insn in md.disasm(data[WS_HANDSHAKE : WS_HANDSHAKE + 0x500], WS_HANDSHAKE):
        if "+0x50" in insn.op_str or "+ 0x50" in insn.op_str:
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")

    # ------------------------------------------------------------------
    # Asterisk / empty path alternatives from Poco WebSocket examples
    # Search Geronimo + Bifrost for request-target '*'
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## Request-target '*' (asterisk-form) search")
    # In NvWS ctor after HTTPRequest, Content-Length set — check if URI ever set to '*'
    # Search SSO init with byte 0x2a and length 1 near connect callers
    for c in callers[:5]:
        count_2a = 0
        for insn in md.disasm(data[c - 0x400 : c + 0x20], c - 0x400):
            if "0x2a" in insn.op_str:
                count_2a += 1
                lines.append(f"  near caller {c:#x}: {insn.address:#x}: {insn.mnemonic} {insn.op_str}")
        if count_2a == 0:
            lines.append(f"  near caller {c:#x}: no imm 0x2a")

    # ------------------------------------------------------------------
    # Check if Host header includes path somehow / unusual headers
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## Headers set on HTTPRequest before connect")
    # NvWS ctor sets Content-Length: 0 via ca5810 at 0x46687f
    for row in disasm_range(0x466810, 0x466890):
        lines.append(row)

    # Search x-nv headers near WS
    for needle in [b"x-nv-", b"X-NV-", b"X-GS-", b"Origin", b"User-Agent", b"Cookie"]:
        locs = find_cstr(needle)
        lines.append(f"  {needle!r}: n={len(locs)} first={[hex(x) for x in locs[:5]]}")

    # ------------------------------------------------------------------
    # Verdict section scaffold
    # ------------------------------------------------------------------
    lines.append("")
    lines.append("## Interim binary conclusions")
    lines.append("1. NvWebSocketSession constructs HTTPRequest with EMPTY uri (confirmed).")
    lines.append("2. Poco WS handshake (ca7900) sets Connection/Upgrade/Sec-WebSocket-* only — no URI write observed in prior dumps.")
    lines.append("3. Absolute-form rtsps/wss/https ALL live-404 — not the request-target.")
    lines.append("4. Empty and / and /rtsp also falsified.")
    lines.append("5. Remaining: (a) URI set AFTER ctor by caller before connect; (b) non-standard path; (c) not standard HTTP upgrade (unlikely — Poco WS); (d) TLS client-cert gate before HTTP; (e) asterisk-form; (f) query-string path.")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
