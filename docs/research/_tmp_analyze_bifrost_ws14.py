#!/usr/bin/env python3
"""Find setMethod(GET), empty→/, and ca6030; produce final resolved report."""
from __future__ import annotations

import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-uri-resolved.txt")


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

    def va_to_cstr(va: int, maxlen: int = 80) -> str | None:
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

    def va_bytes(va: int, n: int = 32) -> bytes | None:
        f = rva_to_file(va - image_base)
        return None if f is None else data[f : f + n]

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

    lines: list[str] = []
    lines.append("# Bifrost2 :322 WS URI — RESOLVED REPORT")
    lines.append("")

    # ================================================================
    # Find MSVC SSO static "GET" (size=3, capacity=15)
    # ================================================================
    lines.append("## Static std::string HTTP_GET (SSO GET + size 3 + cap 15)")
    # Pattern: 47 45 54 00 + pad to 16 + 03 00 00 00 00 00 00 00 + 0F 00 00 00 00 00 00 00
    pat = b"GET\0" + b"\0" * 12 + struct.pack("<Q", 3) + struct.pack("<Q", 15)
    hits = []
    start = 0
    while True:
        i = data.find(b"GET\0", start)
        if i < 0:
            break
        # check size/cap at i+16 / i+24
        if i + 32 <= len(data):
            sz = struct.unpack_from("<Q", data, i + 16)[0]
            cap = struct.unpack_from("<Q", data, i + 24)[0]
            if sz == 3 and cap == 15 and data[i : i + 3] == b"GET":
                hits.append(i)
        start = i + 1
    lines.append(f"SSO GET objects: {[hex(x) for x in hits]}")

    # Also search for any 16-byte aligned GET with size 3 nearby
    for fo in hits:
        rva = file_to_rva(fo)
        va = image_base + rva
        refs = []
        for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
            t = rip_target(insn)
            if t == va:
                refs.append(insn.address)
            # also ref to object start if lea of whole string object
        lines.append(f"  @{fo:#x} va={va:#x} refs={[hex(r) for r in refs]}")
        for r in refs[:8]:
            lines.append(f"  --- {r:#x} ---")
            for row in disasm_range(r - 0x40, r + 0x60)[:25]:
                lines.append("  " + row)

    # Broader: find lea that points into a GET SSO even if not exact
    # Search rdata for GET\0 with size 3 at +16
    rdata_raw = rdata[3]
    rdata_va = rdata[1]
    for off in range(0, rdata[4] - 32):
        fo = rdata_raw + off
        if data[fo : fo + 4] != b"GET\0":
            continue
        sz = struct.unpack_from("<Q", data, fo + 16)[0]
        cap = struct.unpack_from("<Q", data, fo + 24)[0]
        if sz == 3 and (cap == 15 or cap >= 3):
            if fo not in hits:
                lines.append(f"  extra SSO-like @{fo:#x} sz={sz} cap={cap}")

    # ================================================================
    # ca6030 — called from sendRequest with xor edx before Host set
    # ================================================================
    lines.append("\n## ca6030 (sendRequest calls with edx=0 on request)")
    for row in disasm_range(0xCA6030, 0xCA6100)[:50]:
        lines.append(row)

    # ================================================================
    # ca5986 setMethod candidate — full function
    # ================================================================
    lines.append("\n## ca5986 region (setMethod?)")
    # find function start
    start = 0xCA5986
    for b in range(0xCA5986, 0xCA5900, -1):
        if data[b] == 0xCC:
            start = b + 1
            break
        if data[b : b + 4] == b"\x48\x89\x5c\x24":
            start = b
            break
    for row in disasm_range(start, start + 0x80):
        lines.append(row)

    # ================================================================
    # Does WebSocket handshake call setMethod?
    # Search ca7900 for call to ca5986 or any +0x50 assign
    # ================================================================
    lines.append("\n## Calls from ca7900 that might setMethod")
    for insn in md.disasm(data[0xCA7900:0xCA7D00], 0xCA7900):
        ct = call_target(insn)
        if ct is not None:
            lines.append(f"  {insn.address:#x} -> {ct:#x}")

    # Check if any call target assigns to +0x50
    for insn in md.disasm(data[0xCA7900:0xCA7D00], 0xCA7900):
        ct = call_target(insn)
        if ct is None:
            continue
        # disasm first 30 of callee looking for +0x50 and GET
        for row in disasm_range(ct, ct + 0x60)[:20]:
            if "+0x50" in row or "GET" in row or "0x544547" in row:
                lines.append(f"  interesting in callee {ct:#x}: {row}")

    # ================================================================
    # HTTPRequest::write — find via ostr << pattern
    # Poco HTTPRequest::write calls HTTPMessage::write for headers after request line
    # Search for function referencing both method and URI offsets from rcx
    # ================================================================
    lines.append("\n## Locate HTTPRequest::write via vtable of HTTPRequest")
    # From c915c0: lea rax, [rip+0x495b99] -> vtable stored at [rsi]
    # At 0xc915e0: lea rax, [rip + 0x495b99]
    for insn in md.disasm(data[0xC915E0:0xC915E7], 0xC915E0):
        vt = rip_target(insn)
        lines.append(f"HTTPRequest vtable va={vt:#x}")
        vb = va_bytes(vt, 0x40)
        if vb:
            for i in range(0, 0x40, 8):
                p = struct.unpack_from("<Q", vb, i)[0]
                lines.append(f"  [{i:#x}] {p:#x}")
                # slot 0 often dtor; find write - often virtual
                rva = p - image_base
                f = rva_to_file(rva)
                if f and i in (0x18, 0x20, 0x28, 0x30, 0x38):
                    lines.append(f"  disasm [{i:#x}] @{f:#x}:")
                    for row in disasm_range(f, f + 0x100)[:35]:
                        lines.append("    " + row)

    # ================================================================
    # Complete sendRequest after proxy block — does it call request.write?
    # Continue disasm from 0xc9e026
    # ================================================================
    lines.append("\n## sendRequest continuation 0xc9e026+")
    for row in disasm_range(0xC9E026, 0xC9E120):
        lines.append(row)

    # ================================================================
    # ca8be0 / connected check; c9dae0 connect
    # ================================================================
    lines.append("\n## What sets method to GET in Poco WebSocket — check ca7900 xref to HTTP_GET static")
    # Scan for lea rdx, [rip+X] where target is SSO GET object
    for fo in hits:
        va = image_base + file_to_rva(fo)
        for insn in md.disasm(data[0xCA7900:0xCA8000], 0xCA7900):
            if rip_target(insn) == va:
                lines.append(f"  handshake refs GET SSO at {insn.address:#x}")

    # Search WHOLE text for refs to GET SSO
    for fo in hits:
        va = image_base + file_to_rva(fo)
        refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
        lines.append(f"  all refs to GET SSO @{fo:#x}: {[hex(r) for r in refs[:20]]}")

    # ================================================================
    # Check 0xca6030 more carefully - maybe setChunked or clear
    # And check if HTTPRequest write emits empty URI as-is
    # Find write by searching for " " (space) string between method and URI
    # ================================================================
    lines.append("\n## HTTPRequest::write — search space char SSO / stream insert")
    # In Poco, write does: ostr << _method << " " << _uri << " " << _version << "\r\n";
    # Look for function loading [rcx+0x50] then calling operator<< 
    for insn in md.disasm(data[0xC91000:0xC93000], 0xC91000):
        if ("+ 0x50]" in insn.op_str or "+0x50]" in insn.op_str) and insn.mnemonic in ("lea", "mov"):
            # dump surrounding
            lines.append(f"  hit {insn.address:#x}: {insn.mnemonic} {insn.op_str}")
            for row in disasm_range(insn.address - 0x10, insn.address + 0x80)[:25]:
                lines.append("  " + row)
            lines.append("  ---")

    # ================================================================
    # Header map: what does caller pass? Trace 0x721e83 args
    # 5th arg rsp+0x20 = host string from [rsp+0x60]
    # 6th rsp+0x28 = empty URI  
    # 7th rsp+0x30 = from [rsp+0x58]
    # 8th rsp+0x38 = callback wrapper
    # 9th rsp+0x40 = callback wrapper
    # Need: is header map empty?
    # From ctor: mov rax,[rbp+0x77]; iterate map — if empty map, skip
    # Caller: need to find what's at the header arg
    # ================================================================
    lines.append("\n## Caller 0x721e83 — which arg is header map?")
    # Dump more of parent function to see header map construction
    for row in disasm_range(0x721A00, 0x721E90):
        if any(k in row for k in ("call", "lea", "mov qword ptr [rsp", "xorps", "0xf", "HTTP", ";")):
            lines.append(row)

    # Find function that contains 0x721e83 — look for prologue
    lines.append("\n## Parent fn prologue search")
    for addr in range(0x721E83, 0x721000, -1):
        if data[addr : addr + 3] == b"\x48\x89\x5c" or data[addr : addr + 4] == b"\x40\x55\x53\x56":
            # check if this looks like start
            pass
    # scan backwards for int3 padding
    for addr in range(0x721E83, 0x720000, -1):
        if data[addr] == 0xCC and data[addr + 1] == 0xCC:
            # next non-cc
            start = addr + 1
            while data[start] == 0xCC:
                start += 1
            if start < 0x721E83:
                lines.append(f"fn start candidate {start:#x}")
                for row in disasm_range(start, start + 0x80)[:30]:
                    lines.append(row)
                break

    # ================================================================
    # FINAL REPORT
    # ================================================================
    lines.append("""
================================================================================
FINAL VERDICT
================================================================================

## A) What sets URI after empty construct?
NOTHING in the NvWebSocketSession / Poco WebSocket handshake path writes to
HTTPRequest+0x70 after construct. Confirmed:
  - ctor 0xc915c0 copies caller empty string into +0x70
  - caller 0x721e83 passes empty SSO (xorps [rsp+0x78]; size=0; cap=15)
  - handshake 0xca7900 only sets Upgrade headers
  - setURI(+0x70 string assign) has ZERO hits in Poco Net cluster
  - sendRequest (0xc9dea0) ONLY rewrites URI when proxy host [session+0x150] != 0
    (prefixes http:// or https:// absolute-form). NVST uses direct TLS → no rewrite.

## B) Does Poco rewrite request-line for non-proxy TLS?
NO absolute-form rewrite without proxy. Host header IS auto-set from session
host:port if missing (sendRequest @0xc9df47). No empty→'/' store found in
HTTP client area. Default HTTPRequest ctor (0xc91680) sets '/', but NvWS does
NOT use that ctor.

## C) HTTP/1.0? Different Connection? Origin? Cookie?
Upgrade request version is HTTP/1.1 (ctor). Post-upgrade RTSP responses log as
HTTP/1.0 200 OK — that is the RTSP-over-WS layer, not the upgrade request.
Headers on upgrade: Connection: Upgrade, Upgrade: websocket,
Sec-WebSocket-Version: 13, Sec-WebSocket-Key, Content-Length: 0, Host.
No Sec-WebSocket-Protocol. Origin/Cookie not set by NvWS path.
Connection flag: ca8f40 sets session+0x30 keepAlive=1 before sendRequest.

## D) Could '/' work with correct Sec-WebSocket-* on fresh session?
STILL THE LEADING HYPOTHESIS for the request-target, despite prior 404:
  - Binary leaves URI empty; Poco WS must somehow send a legal target
  - Live empty → 400 proves server requires a non-empty target
  - '/' is the only remaining simple target not explained by dead code
  - Prior '/' 404 may be session-affinity / wrong host / missing keep-alive
    nuance / or server returns 404 for non-WS-shaped requests we thought were WS
  MUST re-verify with hex dump of our on-wire request vs expected Poco shape,
  on the exact ConnectionInfo host from a live session that official can join.

## E) setURI / +0x70 after construct
No setURI. Method field also constructed empty; HTTP_GET static string refs
need confirmation — Poco WebSocket typically setMethod(GET) before send.
If method stays empty, request-line would be ` HTTP/1.1` or `  HTTP/1.1` — also 400.

## F) Pre-handshake / client certs
Official: trust-store Certificate bag → Connecting to host:322 →
"WSS client using 1-way SSL" → WSS Options 200.
1-way only. No client cert. No prior HTTP to :322. Session must already exist
(CloudMatch session create/poll) before Connecting.

## G) Mall JS
WebRTC-only for streaming connect. Has RTSPS enum / secureRTSPSupported flag
but zero rtsps:// / :322 / /rtsp URL construction. Classic NVST = Bifrost2 native.

## Exact request-line candidates (ranked)
1. `GET / HTTP/1.1`     — primary remaining; re-test carefully
2. `GET /v2/session/<uuid> HTTP/1.1` — resourcePath (no binary xref, lower odds)
3. Packet capture of official (Wireshark + SSLKEYLOG or MITM) — ground truth

## Falsified (do not retry same way)
empty | /rtsp | rtsps://host:322 | wss://host:322 | https://host:322

## Top 3 OpenNOW code changes
1. (conf ~0.55) Change upgrade cascade default to path `/` only; dump exact
   on-wire hex of request-line+headers; require live session host from
   ConnectionInfo[NVB_PU_RTSPS]; compare Accept. Treat prior `/` 404 as
   possibly environmental, not path-proof.
2. (conf ~0.45) Add probe form `GET /v2/session/${sessionId}` using CloudMatch
   resourcePath (strip host) as request-target.
3. (conf ~0.70) Stop absolute-form cascade (rtsps/wss/https) — live-falsified;
   add optional SSLKEYLOGFILE/pcap capture helper to record official upgrade
   bytes on next GFN launch (highest-value experiment, not a path guess).
""")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
