#!/usr/bin/env python3
"""Confirm empty→/ rewrite, GET method set, header map, session-path candidates."""
from __future__ import annotations

import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-uri-resolved.txt")
PRIOR = OUT.read_text(encoding="utf-8") if OUT.exists() else ""


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

    def va_to_bytes(va: int, n: int = 64) -> bytes | None:
        f = rva_to_file(va - image_base)
        if f is None:
            return None
        return data[f : f + n]

    def va_to_cstr(va: int, maxlen: int = 200) -> str | None:
        raw = va_to_bytes(va, maxlen)
        if raw is None:
            return None
        end = raw.find(b"\0")
        if end < 0:
            end = len(raw)
        raw = raw[:end]
        if all(32 <= b < 127 for b in raw) or raw == b"":
            return raw.decode()
        return None

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
                if s is not None:
                    extra = f"  ; {s!r}"
                else:
                    b = va_to_bytes(t, 16)
                    extra = f"  ; va={t:#x} bytes={b.hex() if b else '?'}"
            ct = call_target(insn)
            if ct is not None:
                extra += f"  ; -> {ct:#x}"
            out.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")
        return out

    lines.append("# URI-resolved FINAL synthesis inputs")
    lines.append("")

    # What's at ca61d0's lea r9?
    lines.append("## ca61d0 r9 constant (likely GET or credentials sentinel)")
    for insn in md.disasm(data[0xCA61DF:0xCA61E6], 0xCA61DF):
        t = rip_target(insn)
        lines.append(f"  insn: {insn.mnemonic} {insn.op_str}")
        lines.append(f"  va={t:#x}")
        lines.append(f"  cstr={va_to_cstr(t)!r}")
        b = va_to_bytes(t, 32)
        lines.append(f"  bytes={b.hex() if b else None}")
        # if vtable-like, show as pointers
        if b and len(b) >= 8:
            p = struct.unpack_from("<Q", b, 0)[0]
            lines.append(f"  as_ptr={p:#x} -> {va_to_cstr(p)!r}")

    # ================================================================
    # Find setMethod: assign to HTTPRequest+0x50 with "GET"
    # ================================================================
    lines.append("\n## Search setMethod(GET) in handshake ca7900 and helpers")
    # Dump ca7900 looking for +0x50 on request object (rsi = request)
    for insn in md.disasm(data[0xCA7900:0xCA7D00], 0xCA7900):
        if "+0x50" in insn.op_str or "+ 0x50" in insn.op_str:
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")

    # Check function ca4fe0 (jumped from ca5d80) and any setMethod
    lines.append("\n## Find HTTPRequest::setMethod by string assign patterns")
    # Poco setMethod is typically: assign string to this+0x50
    # Search for lea rcx,[rdx+0x50] or lea rcx,[rcx+0x50] then call 1c5900 in Net cluster
    setmethod_cands = []
    for insn in md.disasm(data[0xCA5000:0xCAA000], 0xCA5000):
        if insn.mnemonic != "lea":
            continue
        if not ("+ 0x50]" in insn.op_str or "+0x50]" in insn.op_str):
            continue
        # look ahead for call 1c5900
        for a in md.disasm(data[insn.address : insn.address + 0x30], insn.address):
            ct = call_target(a)
            if ct == 0x1C5900:
                setmethod_cands.append(insn.address)
                break
    lines.append(f"lea +0x50; call string_assign: {[hex(x) for x in setmethod_cands[:30]]}")
    for addr in setmethod_cands[:10]:
        lines.append(f"--- {addr:#x} ---")
        for row in disasm_range(addr - 0x20, addr + 0x40)[:20]:
            lines.append("  " + row)

    # Same for +0x70 setURI
    seturi_cands = []
    for insn in md.disasm(data[0xCA5000:0xCAA000], 0xCA5000):
        if insn.mnemonic != "lea":
            continue
        if not ("+ 0x70]" in insn.op_str or "+0x70]" in insn.op_str):
            continue
        for a in md.disasm(data[insn.address : insn.address + 0x30], insn.address):
            ct = call_target(a)
            if ct == 0x1C5900:
                seturi_cands.append(insn.address)
                break
    lines.append(f"\nlea +0x70; call string_assign (setURI): {[hex(x) for x in seturi_cands[:30]]}")
    for addr in seturi_cands[:15]:
        lines.append(f"--- {addr:#x} ---")
        for row in disasm_range(addr - 0x30, addr + 0x40)[:22]:
            lines.append("  " + row)

    # ================================================================
    # HTTPClientSession::sendRequest — find via vtable slot 0x28 call
    # From handshake: call qword ptr [rax+0x28] with rcx=session, rdx=request
    # Find HTTPClientSession vtable and slot 0x28
    # ================================================================
    lines.append("\n## HTTPClientSession vtable / sendRequest")
    # From HTTP session ctor 0xc9b9e0: lea rax, [rip+0x48c203] -> va 0x181128808 = vtable
    vt_va = 0x181128808
    vt = va_to_bytes(vt_va, 0x200)
    if vt:
        lines.append(f"HTTPClientSession vtable @{vt_va:#x}")
        for i in range(0, 0x120, 8):
            p = struct.unpack_from("<Q", vt, i)[0]
            # resolve if in image
            rva = p - image_base if p >= image_base else None
            lines.append(f"  [{i:#x}] {p:#x}")
            if i == 0x28:
                lines.append(f"  *** slot +0x28 sendRequest? ***")
                if rva:
                    f = rva_to_file(rva)
                    if f:
                        lines.append(f"  disasm sendRequest candidate @{f:#x}:")
                        for row in disasm_range(f, f + 0x200)[:80]:
                            lines.append("  " + row)

    # HTTPS session vtable from 0x8a6038: va 0x181008680
    vt2_va = 0x181008680
    vt2 = va_to_bytes(vt2_va, 0x120)
    if vt2:
        lines.append(f"\nHTTPSClientSession vtable @{vt2_va:#x}")
        for i in range(0, 0x80, 8):
            p = struct.unpack_from("<Q", vt2, i)[0]
            lines.append(f"  [{i:#x}] {p:#x}")
            if i == 0x28:
                rva = p - image_base
                f = rva_to_file(rva)
                lines.append(f"  *** HTTPS sendRequest @{f:#x} ***")
                if f:
                    for row in disasm_range(f, f + 0x280)[:100]:
                        lines.append("  " + row)

    # ================================================================
    # Empty URI check: look for cmp [uri+0x10], 0 then set '/'
    # in sendRequest candidates
    # ================================================================
    lines.append("\n## Empty-URI → '/' patterns (cmp length 0; store 0x2f)")
    # Scan sendRequest-sized windows for 0x2f stores near length checks
    # Broader: in 0xc9d000-0xca2000 (HTTP client area)
    slash_near_empty = []
    for insn in md.disasm(data[0xC9D000:0xCA2000], 0xC9D000):
        if "0x2f" in insn.op_str and insn.mnemonic in ("mov", "movzx", "mov word", "mov byte"):
            slash_near_empty.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    lines.append(f"0x2f stores in HTTP client area: {len(slash_near_empty)}")
    for h in slash_near_empty[:40]:
        lines.append("  " + h)

    # Also check known write function - search for space char 0x20 between method and uri writes
    lines.append("\n## Space (0x20) immediates near HTTP write (request-line)")
    space_hits = []
    for insn in md.disasm(data[0xCA4000:0xCA6000], 0xCA4000):
        if insn.mnemonic.startswith("mov") and ("0x20" in insn.op_str or "0x20," in insn.op_str):
            space_hits.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}")
    for h in space_hits[:30]:
        lines.append("  " + h)

    # ================================================================
    # Decode Content-Length header bytes at NvWS ctor
    # ================================================================
    lines.append("\n## NvWS ctor header set at 0x46682b (Content-Length: 0)")
    # Read the SSO bytes being constructed
    for insn in md.disasm(data[0x46682B:0x466880], 0x46682B):
        t = rip_target(insn)
        if t:
            b = va_to_bytes(t, 16)
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str} ; va={t:#x} cstr={va_to_cstr(t)!r} bytes={b.hex() if b else None}")
        else:
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")

    # ================================================================
    # Header map copy: what is at rbp+0x77 in ctor?
    # Trace which arg is the NameValueCollection
    # ================================================================
    lines.append("\n## Ctor stack args mapping (URI / headers)")
    for row in disasm_range(0x466660, 0x4666C0):
        lines.append(row)
    lines.append("--- header loop source ---")
    for row in disasm_range(0x466900, 0x466990):
        lines.append(row)

    # Caller passes at rsp+0x28 = empty string. Map MSVC stack args:
    # After home space: rsp+0x20 = 5th, +0x28 = 6th, +0x30 = 7th, +0x38 = 8th, +0x40 = 9th
    # Callee with push rbp; sub rsp, 0xa0; lea rbp,[rsp-7] — messy
    # From ctor: mov rbx, [rbp+0x67] used as URI — compute
    lines.append("\nCtor uses [rbp+0x67] as URI (r8). Caller empty string is 6th arg at [rsp+0x28] pre-call.")

    # ================================================================
    # Check if HTTPS sendRequest rewrites URI using host for absolute form
    # when proxy is set — and whether proxy host is ever set for NVST
    # ================================================================
    lines.append("\n## Proxy host usage for NVST path")
    for needle in [b"setProxyHost", b"proxyHost", b"ProxyHost", b"HTTP_PROXY", b"https_proxy"]:
        idx = 0
        locs = []
        while True:
            i = data.find(needle, idx)
            if i < 0:
                break
            locs.append(i)
            idx = i + 1
        lines.append(f"  {needle!r}: {[hex(x) for x in locs[:5]]}")

    # ================================================================
    # resourcePath string usage — is it ever used as WS path?
    # ================================================================
    lines.append("\n## resourcePath xrefs")
    fo = data.find(b"resourcePath")
    rva = file_to_rva(fo)
    va = image_base + rva
    refs = []
    for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
        if rip_target(insn) == va:
            refs.append(insn.address)
    # Also try with quotes for JSON
    lines.append(f"resourcePath refs: {[hex(r) for r in refs]}")
    for r in refs[:5]:
        for row in disasm_range(r - 0x40, r + 0x80)[:25]:
            lines.append("  " + row)

    # /v2/session string refs
    for needle in [b"/v2/session/", b"/v2/session"]:
        fo = data.find(needle)
        if fo < 0:
            lines.append(f"{needle!r}: MISSING")
            continue
        rva = file_to_rva(fo)
        va = image_base + rva
        refs = [insn.address for insn in md.disasm(data[trawptr:trawptr+trawsize], trawptr) if rip_target(insn) == va]
        lines.append(f"{needle!r} @{fo:#x} refs={ [hex(r) for r in refs[:10]] }")

    # ================================================================
    # Poco WebSocket: does completeHandshake call setURI?
    # Dump from ca7c6f (xor edx; call ca5d80) through sendRequest
    # and check ca8f40 keepAlive, then [rax+0x28]
    # ================================================================
    lines.append("\n## Handshake send path detail 0xca7c6f-0xca7cb0")
    for row in disasm_range(0xCA7C6F, 0xCA7CB5):
        lines.append(row)

    # What is at request method before send? Check if setMethod called earlier via
    # looking for call that takes "GET" - search GET cstring refs again with better filter
    lines.append("\n## All code refs to exact 'GET' cstring")
    for fo in [data.find(b"GET\0"), data.find(b"GET\0", data.find(b"GET\0") + 1)]:
        if fo < 0:
            continue
        # ensure it's standalone GET
        rva = file_to_rva(fo)
        va = image_base + rva
        s = va_to_cstr(va)
        lines.append(f"  @{fo:#x} va={va:#x} s={s!r}")
        refs = []
        for insn in md.disasm(data[trawptr : trawptr + trawsize], trawptr):
            if rip_target(insn) == va:
                refs.append(insn.address)
        lines.append(f"  refs={ [hex(r) for r in refs] }")
        for r in refs:
            lines.append(f"  context {r:#x}:")
            for row in disasm_range(r - 0x30, r + 0x50)[:20]:
                lines.append("    " + row)

    # HTTP_GET as static std::string in rdata — search for SSO 'GET' with length 3 capacity 15
    # Pattern in rdata: 47 45 54 00 .... 03 00 00 00 00 00 00 00 0F 00 00 00 00 00 00 00
    pat = bytes([0x47, 0x45, 0x54, 0x00]) + bytes(4) + struct.pack("<Q", 3) + struct.pack("<Q", 15)
    # looser: GET\0 + len=3
    idx = 0
    sso_gets = []
    while True:
        i = data.find(b"GET\0", idx)
        if i < 0:
            break
        # check if next qwords look like SSO
        if i + 24 <= len(data):
            # possible inline SSO at i
            ln = struct.unpack_from("<Q", data, i + 16)[0] if False else None
        # check preceding as part of std::string object in rdata
        sso_gets.append(i)
        idx = i + 1
        if len(sso_gets) > 30:
            break
    lines.append(f"\nGET\\0 occurrences: {len(sso_gets)} first={[hex(x) for x in sso_gets[:15]]}")

    # Look at 0x143dxxx area near HTTP/1.1 for static method strings
    for fo in [0x143D4C0, 0x143D4E0, 0x143CE70, 0x11272D0]:
        lines.append(f"  @{fo:#x}: {data[fo:fo+32]!r}")

    # ================================================================
    # CRITICAL: read HTTPRequest::write 
    # Find via looking for function that reads +0x50 and +0x70 and +0x30 (version in message)
    # From ca5a10, version is at +0x30 of HTTPMessage base
    # HTTPRequest layout: HTTPMessage, then method@+0x50, uri@+0x70
    # ================================================================
    lines.append("\n## Find HTTPRequest::write (loads +0x50 and +0x70 from same object)")
    # Scan for: mov/lea from [reg+0x50] and later [same_reg+0x70] within 0x60 bytes
    write_fns = []
    insns = list(md.disasm(data[0xCA4000:0xCA5800], 0xCA4000))
    for i, insn in enumerate(insns):
        if "+ 0x50]" not in insn.op_str and "+0x50]" not in insn.op_str:
            continue
        # extract base reg
        window = insns[i : i + 25]
        ops = " | ".join(x.op_str for x in window)
        if "+ 0x70]" in ops or "+0x70]" in ops:
            write_fns.append(insn.address)
    lines.append(f"candidates: {[hex(x) for x in write_fns]}")
    for addr in write_fns[:5]:
        # find function start
        start = addr
        for back in range(addr, max(0xCA4000, addr - 0x80), -1):
            if data[back] == 0xCC and data[back + 1] != 0xCC:
                start = back + 1
                break
            if data[back : back + 3] == b"\x48\x89\x5c":  # common prologue
                start = back
                break
        lines.append(f"### write cand near {addr:#x} start~{start:#x}")
        for row in disasm_range(start, start + 0x120)[:50]:
            lines.append("  " + row)

    # ================================================================
    # Append synthesis
    # ================================================================
    lines.append("""
================================================================================
# SYNTHESIS: What Bifrost2 actually does on :322 WS upgrade
================================================================================

## Proven facts
1. Transport IS standard TLS + Poco Net::WebSocket HTTP upgrade (not a custom framing).
2. Official log: Connecting to host:322 → (~67ms) → "WSS client using 1-way SSL" → WSS Options 200.
3. 1-way SSL only (NOT mutual/client-cert). Certificate-bag logs are trust-store roots.
4. Geronimo.dll has ZERO WebSocket/rtsps/:322 strings — Bifrost2 owns the wire.
5. Mall JS is WebRTC-primary (RTCPeerConnection×358, webrtc×186). Has AppLevelProtocol.RTSPS enum
   and secureRTSPSupported flag, but NO rtsps://, :322, or /rtsp connect URLs. Classic NVST is
   native Bifrost2, not Mall.
6. Live OpenNOW falsified request-targets: empty→400; / →404; /rtsp→404;
   rtsps://host:322→404; wss://host:322→404; https://host:322→404.
7. NvWebSocketSession ctor (0x466640) builds HTTPRequest via 0xc915c0 with:
   - method = empty cstring
   - URI   = empty std::string from caller (0x721e83 SSO at rsp+0x78)
   - version = HTTP/1.1
   Then sets Content-Length: 0, copies optional header map, calls connect().
8. Poco WS handshake (0xca7900) adds Connection/Upgrade/Sec-WebSocket-Version/Key only.
   No Sec-WebSocket-Protocol in Bifrost2. No Origin on this path.
9. /rtsp string has ZERO code xrefs (dead).

## Critical unresolved tension
Binary constructs EMPTY method+URI; official still gets 101. Empty on wire is live-400.
Therefore either:
  (H1) Poco sendRequest/write rewrites empty URI → "/" AND empty method → "GET" before send,
       and our live GET / →404 was for a *different* reason (headers/Host/SNI/session affinity), OR
  (H2) Something sets URI to a session-specific path (e.g. /v2/session/<uuid>) we have not tried, OR
  (H3) Header map from caller includes a pseudo-path / unusual headers that change routing.

## Ranked next experiments (exact request-line candidates)
1. GET / HTTP/1.1  — RE-TEST with byte-identical Poco header set + Host: host:322 + Content-Length: 0
   + Connection: Upgrade + Upgrade: websocket + Sec-WebSocket-Version: 13 + Sec-WebSocket-Key
   on a FRESH session's exact ConnectionInfo host (not a stale probe host). Confidence fix: high if H1.
2. GET /v2/session/<sessionId> HTTP/1.1  — CloudMatch resourcePath as request-target.
3. GET /v2/session/<sessionId>/ HTTP/1.1 and GET /v2/session/<sessionId>?… variants.
4. Capture official bytes with Wireshark/npcap on localhost loopback or SSLKEYLOGFILE if GFN
   exports keys — ultimate ground truth for request-line.
5. Only after 101: try x-nv-sessionid (official adds post-upgrade for RTSP; upgrade itself may not need it).

## Already falsified request-lines (do not retry as-is)
- GET  HTTP/1.1
- GET /rtsp HTTP/1.1
- GET rtsps://host:322 HTTP/1.1
- GET wss://host:322 HTTP/1.1
- GET https://host:322 HTTP/1.1
""")

    # Prepend brief pointer and write
    header = PRIOR.split("================================================================================")[0] if "SYNTHESIS" in PRIOR else ""
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(lines)} lines)")


if __name__ == "__main__":
    main()
