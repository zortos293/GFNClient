#!/usr/bin/env python3
"""Clean final report: ctor arg map, HEAD/GET statics, write path, ranked experiments."""
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

    def va_to_cstr(va: int, maxlen: int = 64) -> str | None:
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

    def rip_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C, 0x4D) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            return image_base + instr_rva + len(raw) + disp
        return None

    def call_target(insn) -> int | None:
        raw = bytes(insn.bytes)
        if raw[0] == 0xE8 and len(raw) == 5:
            return insn.address + 5 + struct.unpack_from("<i", raw, 1)[0]
        return None

    lines: list[str] = []

    # HEAD static near sendRequest
    lines.append("## HEAD/GET/POST static strings near sendRequest write")
    for needle in [b"HEAD\0", b"GET\0", b"POST\0", b"OPTIONS\0", b"HTTP/1.1\0"]:
        locs = []
        i = 0
        while True:
            j = data.find(needle, i)
            if j < 0:
                break
            locs.append(j)
            i = j + 1
        lines.append(f"{needle!r}: {[hex(x) for x in locs[:8]]}")

    # The HEAD lea at 0xc9e0d9 — resolve and look at neighbors in rdata for GET
    for insn in md.disasm(data[0xC9E0D9:0xC9E0E0], 0xC9E0D9):
        t = rip_target(insn)
        lines.append(f"HEAD lea target va={t:#x} cstr={va_to_cstr(t)!r}")
        if t:
            f = rva_to_file(t - image_base)
            # dump ±64 bytes of rdata around HEAD
            chunk = data[f - 64 : f + 64]
            lines.append(f"  neighborhood ascii: {chunk!r}")

    # Resolve empty method cstring at 0x4666b0
    for insn in md.disasm(data[0x4666B0:0x4666B7], 0x4666B0):
        t = rip_target(insn)
        lines.append(f"ctor rdx (method) va={t:#x} cstr={va_to_cstr(t)!r}")
        if t:
            f = rva_to_file(t - image_base)
            lines.append(f"  bytes={data[f:f+32]!r}")

    # Resolve what rbx is — dump ctor reading [rbp+0x67]
    # Compute: with prologue lea rbp,[rsp-7]; sub rsp,0xa0; 7 pushes
    # On entry to body after prologue, stack args are at rbp+X
    # MSVC: first stack arg typically at [rsp+0x28] after call = home+arg
    # Empirically from prior: empty is URI. Confirm by checking if ANY non-empty
    # string is assigned to +0x50 or +0x70 between ctor and sendRequest.

    lines.append("\n## Assignments to HTTPRequest +0x50/+0x70 between NvWS ctor and connect")
    # In 0x466640..0x466985 only c915c0 assigns those
    for insn in md.disasm(data[0x466640:0x466990], 0x466640):
        if "+0x50" in insn.op_str or "+0x70" in insn.op_str or "+ 0x50" in insn.op_str or "+ 0x70" in insn.op_str:
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")

    # c9e070 write path — continue to see how method/uri are emitted
    lines.append("\n## HTTPClientSession request write 0xc9e070 (method at rsi+0x50)")
    rows = []
    for insn in md.disasm(data[0xC9E070:0xC9E300], 0xC9E070):
        extra = ""
        t = rip_target(insn)
        if t:
            s = va_to_cstr(t)
            extra = f"  ; {s!r}" if s is not None else f"  ; va={t:#x}"
        ct = call_target(insn)
        if ct:
            extra += f"  ; -> {ct:#x}"
        rows.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")
    for row in rows:
        if any(k in row for k in ("call", "lea", "0x50", "0x70", ";", "0x20", "HEAD", "GET", "HTTP")):
            lines.append(row)

    # Dump more of write focusing on stream output of method
    lines.append("\n## Full write fn until first ret-ish (0xc9e070-0xc9e250)")
    for insn in md.disasm(data[0xC9E0C0:0xC9E250], 0xC9E0C0):
        t = rip_target(insn)
        ct = call_target(insn)
        extra = ""
        if t:
            s = va_to_cstr(t)
            extra = f"  ; {s!r}" if s is not None else f"  ; va={t:#x}"
        if ct:
            extra += f"  ; -> {ct:#x}"
        if insn.mnemonic == "call" or (t and va_to_cstr(t)) or "+0x50" in insn.op_str or "+0x70" in insn.op_str:
            lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{extra}")

    # Key: does write substitute empty URI?
    lines.append("\n## Scan 0xc9e070-0xc9e400 for 0x2f '/' immediate")
    for insn in md.disasm(data[0xC9E070:0xC9E400], 0xC9E070):
        if "0x2f" in insn.op_str:
            lines.append(f"  {insn.address:#x}: {insn.mnemonic} {insn.op_str}")

    # ================================================================
    # Write CLEAN final document
    # ================================================================
    report = """# Bifrost2 :322 WebSocket upgrade — URI RESOLVED

Date: 2026-07-09
Sources: Bifrost2.dll (Poco 1.14.1), Geronimo.dll, geronimo.log, Mall/*.js, live OpenNOW probes, upstream Poco WebSocket.cpp 1.14.1

================================================================================
EXECUTIVE VERDICT
================================================================================

The :322 path IS a real TLS + standard HTTP/1.1 WebSocket upgrade (Poco Net::WebSocket).
It is NOT a custom non-HTTP framing.

However: **every simple request-target OpenNOW has tried is live-falsified**, and static
analysis shows NvWebSocketSession constructs the HTTPRequest with an **empty method and
empty URI**, then Poco WebSocket::connect (confirmed in upstream 1.14.1 source) does
**not** call setMethod(GET) or setURI("/"). Poco sendRequest only rewrites the URI to
absolute-form when a **proxy host** is configured — NVST uses direct 1-way TLS, so no rewrite.

That leaves a hard tension: official gets 101; empty-on-wire gets 400; `/` got 404.
The remaining work is **not more path guessing from strings** — it is (1) prove what
bytes official actually sends (pcap/SSLKEYLOG), and (2) re-test `/` under identical
session/host/header conditions while hex-dumping our request-line.

================================================================================
ANSWERS TO FOCUS QUESTIONS
================================================================================

## A) After empty HTTPRequest construct, what SETS the URI before write?

**Nothing in the NvWS → Poco WS → sendRequest path.**

Evidence:
- Caller `0x721e83` builds empty SSO (`xorps [rsp+0x78]`, size=0, cap=15) and passes it
  into NvWebSocketSession ctor `0x466640`.
- Ctor calls `HTTPRequest(method, uri, version)` at `0xc915c0` with:
  - method = empty cstring (lea rdx → `""`)
  - uri    = caller empty std::string
  - version = `HTTP/1.1`
- Then sets `Content-Length: 0`, copies optional header map, calls connect `0x466d10`.
- Poco WS handshake `0xca7900` ≡ upstream WebSocket::connect: sets Connection/Upgrade/
  Sec-WebSocket-Version/Key + setChunked(false) + setKeepAlive(true); **no setMethod/setURI**.
- `setURI` (+0x70 string-assign) hits in Poco Net cluster: **0**.
- Upstream Poco 1.14.1 WebSocket.cpp lines 196–209 confirm: no method/URI mutation.

## B) Does Poco HTTPClientSession rewrite the request-line for non-proxy TLS?

**No.** sendRequest `0xc9dea0` (shared by HTTP + HTTPS sessions, vtable +0x28):
- Auto-sets `Host` from session host:port if missing.
- If `proxyHost` (session+0x150) non-empty AND URI does not already start with
  `http://` or `https://`, rewrites URI to absolute-form via helper `0x1f15c0`.
- NVST connect uses SecureStreamSocket / HTTPSClientSession directly to :322 with
  **no proxy** → absolute-form rewrite does **not** run.
- No empty→`/` store found in the write path (`0xc9e070+`).

## C) HTTP/1.0? Different Connection? Origin? Cookie?

| Item | Finding |
|------|---------|
| Upgrade request version | **HTTP/1.1** (ctor) |
| Post-upgrade RTSP status line | Logged as `HTTP/1.0 200 OK` (RTSP-over-WS layer, not upgrade) |
| Connection | `Upgrade` (plus session keepAlive=1 via `0xca8f40`) |
| Upgrade | `websocket` |
| Sec-WebSocket-Version | `13` |
| Sec-WebSocket-Key | random (createKey) |
| Sec-WebSocket-Protocol | **absent** (0 strings in Bifrost2) |
| Content-Length | `0` (set in NvWS ctor) |
| Origin / Cookie | **not** set on this path |
| Host | auto from session `host:port` |

## D) Could `/` work when Sec-WebSocket-* are present?

**Still the only simple path consistent with Poco defaults — but live-404 once.**

Re-interpret:
- Empty target → 400 matches RFC + our hex `GET  HTTP/1.1`.
- Official cannot be sending empty (would 400).
- Binary never sets `/rtsp` (0 code xrefs) or absolute rtsps/wss/https (live-404).
- Default Poco HTTPRequest ctor uses `/`, but NvWS **bypasses** that ctor.
- Therefore either (i) something outside the analyzed window sets URI to `/` or a
  session path, or (ii) our prior `/` probe differed from official in host/session/
  SNI/header bytes and the 404 was not a pure path rejection.

**Must re-test `/` with hex dump on a fresh ConnectionInfo host.**

## E) setURI / +0x70 / format strings

- No setURI after construct.
- No `GET %s` / `%s %s HTTP` formatters for the upgrade line.
- `/rtsp` exists only as a dead string next to scheme tags.
- `resourcePath` / `/v2/session` have **no code xrefs** into the WS path (JSON field
  name only for GridServer parsing).

## F) Pre-handshake / client certs

Official sequence (geronimo.log):
```
Certificate bag (trust roots) 
→ Connecting to host <h>, port 322
→ WSS client using 1-way SSL
→ streamingSessionId : <uuid>
→ WSS Options: rtsps://h:322 → HTTP/1.0 200 OK
```
- **1-way SSL only** (not mutual/client-cert).
- No prior HTTP exchange on :322 before Connecting.
- Requires an already-created CloudMatch session (session poll → ConnectionInfo).

## G) Mall JS

**WebRTC-primary / not classic NVST wire.**
- RTCPeerConnection ×358, webrtc ×186
- rtsps:// ×0, :322 ×0, /rtsp ×0, wss:// ×0
- Has `AppLevelProtocol.RTSPS` enum + `secureRTSPSupported` flag only
- Classic :322 WSS lives entirely in **Bifrost2.dll** (Geronimo.dll has 0 WS/rtsps strings)

================================================================================
LIVE FALSIFICATIONS (do not retry same way)
================================================================================

| Request-target | Result |
|----------------|--------|
| empty (`GET  HTTP/1.1`) | 400 |
| `/` | 404 (re-test under stricter conditions) |
| `/rtsp` | 404 |
| `rtsps://host:322` | 404 |
| `wss://host:322` | 404 |
| `https://host:322` | 404 |

================================================================================
RANKED NEXT EXPERIMENTS (exact request-line candidates)
================================================================================

1. **Packet-capture official** (highest value)
   - Wireshark/npcap on the GFN process, or SSLKEYLOGFILE if obtainable
   - Goal: exact request-line bytes + header order
   - Candidate unknown until capture

2. **`GET / HTTP/1.1`** — careful re-test
   ```
   GET / HTTP/1.1\\r\\n
   Host: <ConnectionInfo-host>:322\\r\\n
   Connection: Upgrade\\r\\n
   Upgrade: websocket\\r\\n
   Sec-WebSocket-Version: 13\\r\\n
   Sec-WebSocket-Key: <24B-random-b64>\\r\\n
   Content-Length: 0\\r\\n
   \\r\\n
   ```
   Requirements: fresh session, exact NVB_PU_RTSPS host, hex-log request-line,
   validate Sec-WebSocket-Accept. Do not use stale probe hosts.

3. **`GET /v2/session/<sessionId> HTTP/1.1`**
   - Use CloudMatch `resourcePath` (e.g. `/v2/session/<uuid>`) as request-target
   - Low binary support (no xrefs) but only remaining structured path in the ecosystem

4. **Exotic: empty method** `  HTTP/1.1` (two spaces) — only if capture suggests it;
   binary constructs empty method+URI; unlikely to 101 but explains the empty construct.

================================================================================
TOP 3 OPENNOW CODE CHANGES
================================================================================

### 1. Kill absolute-form cascade; default to `/` with hex diagnostics
**Confidence: 0.72**

In `nvstRtspProbe.ts` / `buildNvstWssUpgradeRequest*`:
- Remove `rtsps`/`wss`/`https` absolute-form attempts (live-falsified).
- Default request-target to `/`.
- Log the exact on-wire request-line as hex (first line only) before parse.
- Bind probe host strictly to live `ConnectionInfo` address for port 322.

### 2. Add `resourcePath` request-target probe form
**Confidence: 0.40**

Add form `sessionPath` -> `GET <resourcePath> HTTP/1.1` using the CloudMatch
session resourcePath (`/v2/session/<id>`). Try only after `/` still 404s on a
verified fresh host.

### 3. Add official-capture helper (SSLKEYLOG / raw TLS mirror)
**Confidence: 0.85 that this unblocks; 0.30 that code alone guesses the path**

Ship a small main-process helper or doc'd script to:
- launch/attach with SSLKEYLOGFILE if GFN/OpenSSL respects it, or
- instruct Wireshark capture filtered to tcp/322 during official connect,
then diff against OpenNOW's hex-logged upgrade.

Without (3), path guessing has exhausted the binary's static evidence.

================================================================================
APPENDIX — key addresses (Bifrost2.dll file offsets)
================================================================================

| Symbol / role | Offset |
|---------------|--------|
| NvWebSocketSession ctor | 0x466640 |
| NvWebSocketSession connect | 0x466d10 |
| Caller (empty URI) | 0x721e83 |
| HTTPRequest(method,uri,ver) | 0xc915c0 |
| Default HTTPRequest (URI=`/`) | 0xc91680 (UNUSED by NvWS) |
| Poco WebSocket::connect | 0xca7900 |
| sendRequest (vtable +0x28) | 0xc9dea0 |
| request body write | 0xc9e070 |
| setKeepAlive | 0xca8f40 |

================================================================================
ANALYSIS NOTES (raw)
================================================================================
"""
    report = report + "\n".join(lines) + "\n"
    OUT.write_text(report, encoding="utf-8")
    print(f"Wrote {OUT} ({len(report)} bytes)")


if __name__ == "__main__":
    main()
