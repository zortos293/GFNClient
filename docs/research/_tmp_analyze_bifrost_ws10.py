#!/usr/bin/env python3
"""Confirm WS handshake sets GET and leaves URI empty; reconstruct request line."""
from __future__ import annotations

import struct
from pathlib import Path

from capstone import Cs, CS_ARCH_X86, CS_MODE_64

DLL = Path(r"C:\Users\Zortos\AppData\Local\NVIDIA Corporation\GeForceNOW\CEF\Bifrost2.dll")
OUT = Path(r"C:\Users\Zortos\Projects\OpenNOW\docs\research\_tmp-bifrost2-ws-request-line-final.txt")


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

    def va_to_cstr(va: int) -> str | None:
        f = rva_to_file(va - image_base)
        if f is None:
            return None
        end = data.find(b"\0", f, f + 120)
        if end < 0:
            return None
        raw = data[f:end]
        if raw and all(32 <= b < 127 for b in raw):
            return raw.decode()
        if raw == b"":
            return ""
        return None

    def ann(insn) -> str:
        raw = bytes(insn.bytes)
        if len(raw) >= 7 and raw[0] in (0x48, 0x4C) and raw[1] in (0x8D, 0x8B) and (raw[2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", raw, 3)[0]
            instr_rva = tvaddr + (insn.address - trawptr)
            s = va_to_cstr(image_base + instr_rva + len(raw) + disp)
            if s is not None:
                return f"  ; {s!r}"
        # imm '/'
        if "0x2f" in insn.op_str:
            return "  ; '/'?"
        return ""

    # Dump full ca7900 handshake looking for GET / setMethod / setURI
    lines.append("===== ca7900 full handshake (method/URI?) =====")
    for insn in md.disasm(data[0xCA7900:0xCA7E00], 0xCA7900):
        a = ann(insn)
        if a or insn.mnemonic in ("call", "lea", "mov") and (
            "0x2f" in insn.op_str or insn.mnemonic == "call" or a
        ):
            lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{a}")

    # Specifically search ca7900-ca7e00 for lea of GET or '/'
    lines.append("\n===== string loads in ca7900-ca7e00 =====")
    for k in range(0xCA7900, 0xCA7E00):
        if data[k] in (0x48, 0x4C) and data[k + 1] in (0x8D, 0x8B) and (data[k + 2] & 0xC7) == 0x05:
            disp = struct.unpack_from("<i", data, k + 3)[0]
            instr_rva = tvaddr + (k - trawptr)
            s = va_to_cstr(image_base + instr_rva + 7 + disp)
            if s is not None:
                lines.append(f"  @{k:#x} -> {s!r}")

    # Search for setMethod-like: copy "GET" into HTTPRequest+0x50 in WS region
    # Find "GET" as exact 3-char SSO store: mov dword with 'GET\0' = 0x00544547
    lines.append("\n===== imm 'GET\\0' (0x544547) stores in WS/connect =====")
    for lo, hi in [(0xCA7000, 0xCA9000), (0x466000, 0x468000), (0xC91000, 0xC92000)]:
        for k in range(lo, hi - 4):
            # C7 0x xx 47 45 54 00 = mov dword, 'GET\0'
            if data[k + 2 : k + 6] == b"GET\x00" and data[k] in (0xC7, 0x48):
                lines.append(f"  @{k:#x}: {data[k:k+8].hex()}")
            if data[k : k + 4] == b"GET\x00":
                # might be in code as immediate via mov reg
                pass

    # Search mov reg, 0x544547 / 0x474554
    for lo, hi in [(0xCA7000, 0xCA9000), (0x466000, 0x468000)]:
        for k in range(lo, hi - 5):
            imm = struct.unpack_from("<I", data, k)[0]
            if imm in (0x00544547, 0x47455400, 0x544547):
                lines.append(f"  immGET @{k:#x} context {data[k-2:k+6].hex()}")

    # HTTPRequest write path - find function that uses method+uri+version
    # Look for format that concatenates with spaces near HTTP/1.1 write
    http11 = data.find(b"HTTP/1.1\0", 0x143D000, 0x1440000)
    lines.append(f"\nHTTP/1.1 @ {http11:#x}")

    # Check: does sendRequest replace empty URI with '/'?
    # Search for cmp size,0 then mov '/' near HTTP client send
    lines.append("\n===== empty-URI-to-slash patterns near HTTP client =====")
    # look for 0x2f stores near 0x7c3000 (Host header site was 0x7c34c6) and 0xca5xxx
    for lo, hi in [(0x7C3000, 0x7C4000), (0xCA5000, 0xCA7000), (0xC9F000, 0xCA2000)]:
        hits = []
        for k in range(lo, hi - 6):
            if data[k : k + 2] == b"\x66\xc7" and data[k + 4 : k + 6] == b"\x2f\x00":
                hits.append(k)
        if hits:
            lines.append(f"  region {lo:#x}: {[hex(h) for h in hits[:10]]}")

    # Reconstruct expected request from Poco WebSocket client behavior + our findings
    lines.append("""
===== RECONSTRUCTED OFFICIAL UPGRADE REQUEST =====
Evidence chain:
1. NvWebSocketSession ctor (0x466640) builds HTTPRequest via c915c0:
   - version = 'HTTP/1.1'
   - one string field = EMPTY (rdata 0x143ce70)
   - other string field = caller-provided std::string (caller 0x721e83 passes EMPTY SSO at rsp+0x78)
2. Default HTTPRequest ctor (0xc91680) sets URI field at +0x70 to '/' (imm 0x2f).
   Connect ctor instead copies caller string into the URI slot (r8→+0x70) = EMPTY.
3. '/rtsp' has ZERO code refs (dead string in scheme pointer table only).
4. Poco WebSocket client handshake (0xca7900) adds:
   Connection: Upgrade
   Upgrade: websocket
   Sec-WebSocket-Version: 13
   Sec-WebSocket-Key: <random>
   (no Sec-WebSocket-Protocol anywhere in Bifrost2)
5. Connect also pre-sets Content-Length: 0 on the HTTPRequest.
6. Host is set by HTTPClientSession from connect host:port (logged 'Connecting to host %s, port %hu').

Therefore the request-target path is EMPTY, not '/', not '/rtsp'.

Exact request line (method set to GET by Poco WS client — standard):
  GET  HTTP/1.1\\r\\n
(i.e. 'GET' + SP + '' + SP + 'HTTP/1.1' — empty path)

Full upgrade (canonical):
  GET  HTTP/1.1\\r\\n
  Host: <host>:322\\r\\n
  Connection: Upgrade\\r\\n
  Upgrade: websocket\\r\\n
  Sec-WebSocket-Version: 13\\r\\n
  Sec-WebSocket-Key: <base64>\\r\\n
  Content-Length: 0\\r\\n
  \\r\\n

NOTE: Node 'ws' with wss://host:322/ always sends path '/':
  GET / HTTP/1.1\\r\\n
Server returns 404 for '/' — matches OpenNOW symptom for both '/' and '/rtsp'.
""")

    # Verify method GET is set in ca7900 or ca61d0 path - dump ca7f70
    lines.append("===== ca7f70 (called at start of handshake) =====")
    for insn in md.disasm(data[0xCA7F70 : 0xCA7F70 + 0x100], 0xCA7F70):
        lines.append(f"{insn.address:#x}: {insn.mnemonic} {insn.op_str}{ann(insn)}")

    # Find HTTP_GET string used by setMethod in websocket - search "GET" near ca7900 via pointer
    # Also check ca5810 = NameValueCollection::set
    lines.append("\n===== does handshake call setMethod? search GET in 0x1126000-0x1128000 poco =====")
    # Poco often has HTTPRequest::HTTP_GET as global string
    for off in range(0x1126000, 0x1128000):
        if data[off : off + 4] == b"GET\x00" and (off == 0 or data[off - 1] == 0):
            rva = None
            for name, vaddr, vsize, rawptr, rawsize in sections:
                if rawptr <= off < rawptr + rawsize:
                    rva = vaddr + (off - rawptr)
                    break
            lines.append(f"  GET at {off:#x} rva={rva:#x}" if rva is not None else f"  GET at {off:#x}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} lines)")
    # print reconstruction
    for line in lines:
        if "RECONSTRUCTED" in line or line.startswith("  GET") or "Evidence" in line or "Node" in line or "empty" in line.lower() and "URI" in line:
            print(line)


if __name__ == "__main__":
    main()
