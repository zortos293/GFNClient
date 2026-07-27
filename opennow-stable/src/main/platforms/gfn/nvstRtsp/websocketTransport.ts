import { createHash, randomBytes } from "node:crypto";
import type { Duplex } from "node:stream";
import { connect as tlsConnect, type TLSSocket } from "node:tls";

/**
 * Official `:322` upgrade request-target is still under research.
 *
 * Live matrix (raw TLS unless noted):
 * - empty (`GET  HTTP/1.1`) → HTTP 400
 * - absolute `rtsps://` / `wss://` / `https://` → HTTP 404
 * - `/` via Node `ws` → HTTP 404 (extra Client headers; not Bifrost-shaped)
 * - `/` via raw TLS Bifrost-shaped headers → **pending** (never tried)
 *
 * Poco 1.14.1 WebSocket::connect does not set method/URI; it only adds Upgrade
 * headers. Default HTTPRequest is GET `/`. See docs/research/_tmp-bifrost2-ws-uri-resolved.txt.
 */
export type NvstWssUpgradeTargetForm =
  | "slash"
  | "sessionPath"
  | "rtsps"
  | "wss"
  | "https"
  | "empty";

export function buildNvstWssUpgradeRequestTarget(
  host: string,
  port: number,
  form: NvstWssUpgradeTargetForm,
  sessionId?: string,
): string {
  switch (form) {
    case "slash":
      return "/";
    case "sessionPath":
      return sessionId && sessionId.trim().length > 0
        ? `/v2/session/${sessionId.trim()}`
        : "/";
    case "rtsps":
      return `rtsps://${host}:${port}`;
    case "wss":
      return `wss://${host}:${port}`;
    case "https":
      return `https://${host}:${port}`;
    case "empty":
      return "";
  }
}

/**
 * Raw TLS WebSocket upgrade for NVST `:322`.
 * Header order matches Poco WebSocket::connect + Bifrost Content-Length: 0.
 * Optional `x-nv-sessionid` is only for a 403 retry.
 */
export function buildNvstWssUpgradeRequest(
  host: string,
  port: number,
  secWebSocketKey: string,
  options: {
    form?: NvstWssUpgradeTargetForm;
    sessionId?: string;
  } = {},
): string {
  const form = options.form ?? "slash";
  const target = buildNvstWssUpgradeRequestTarget(host, port, form, options.sessionId);
  const requestLine = `GET ${target} HTTP/1.1`;
  // Poco sets Connection/Upgrade/Version/Key; Bifrost presets Content-Length: 0.
  // Host is auto-set by HTTPClientSession (host:port for non-443).
  let request =
    `${requestLine}\r\n` +
    `Host: ${host}:${port}\r\n` +
    `Connection: Upgrade\r\n` +
    `Upgrade: websocket\r\n` +
    `Sec-WebSocket-Version: 13\r\n` +
    `Sec-WebSocket-Key: ${secWebSocketKey}\r\n` +
    `Content-Length: 0\r\n`;
  const sessionId = options.sessionId?.trim();
  if (sessionId && form !== "sessionPath") {
    // Only attach as header on 403 retry paths; sessionPath already uses UUID in URI.
    request += `x-nv-sessionid: ${sessionId}\r\n`;
  }
  return `${request}\r\n`;
}

/** @deprecated Empty path is live-falsified (HTTP 400). */
export function buildEmptyPathUpgradeRequest(
  host: string,
  port: number,
  secWebSocketKey: string,
  sessionId?: string,
): string {
  return buildNvstWssUpgradeRequest(host, port, secWebSocketKey, {
    form: "empty",
    sessionId,
  });
}

function connectNvstWssOnce(
  host: string,
  port: number,
  timeoutMs: number,
  form: NvstWssUpgradeTargetForm,
  sessionId?: string,
  attachSessionHeader = false,
): Promise<Duplex> {
  const key = randomBytes(16).toString("base64");
  const expectedAccept = createHash("sha1")
    .update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
    .digest("base64");
  const request = buildNvstWssUpgradeRequest(host, port, key, {
    form,
    // For slash/rtsps/… first attempt: no session header.
    // For 403 retry or sessionPath: pass sessionId.
    sessionId: attachSessionHeader || form === "sessionPath" ? sessionId : undefined,
  });
  const requestLine = request.split("\r\n")[0] ?? "";
  const requestLineHex = Buffer.from(requestLine, "utf8").toString("hex");

  return new Promise((resolve, reject) => {
    let settled = false;
    let buffer = Buffer.alloc(0);
    let socket: TLSSocket | null = null;

    const fail = (error: Error): void => {
      if (settled) {
        return;
      }
      settled = true;
      try {
        socket?.destroy();
      } catch {
        // ignore
      }
      reject(error);
    };

    const succeed = (tlsSocket: TLSSocket, leftover: Buffer): void => {
      if (settled) {
        return;
      }
      settled = true;
      tlsSocket.removeAllListeners("data");
      tlsSocket.removeAllListeners("error");
      tlsSocket.removeAllListeners("timeout");
      tlsSocket.setTimeout(0);
      if (leftover.length > 0) {
        tlsSocket.unshift(leftover);
      }
      resolve(tlsSocket);
    };

    socket = tlsConnect(
      {
        host,
        port,
        servername: host,
        rejectUnauthorized: true,
      },
      () => {
        socket?.write(request);
      },
    );

    socket.setTimeout(timeoutMs);
    socket.on("timeout", () => fail(new Error(`WSS upgrade timed out after ${timeoutMs}ms`)));
    socket.on("error", (error) => fail(error instanceof Error ? error : new Error(String(error))));
    socket.on("data", (chunk: Buffer) => {
      buffer = Buffer.concat([buffer, chunk]);
      const headerEnd = buffer.indexOf("\r\n\r\n");
      if (headerEnd < 0) {
        if (buffer.length > 16_384) {
          fail(new Error("WSS upgrade failed: response headers too large"));
        }
        return;
      }

      const headerText = buffer.subarray(0, headerEnd).toString("utf8");
      const leftover = buffer.subarray(headerEnd + 4);
      const statusLine = headerText.split("\r\n")[0] ?? "";
      const statusMatch = /^HTTP\/\d(?:\.\d)?\s+(\d{3})\s*(.*)$/i.exec(statusLine);
      const statusCode = statusMatch ? Number(statusMatch[1]) : 0;
      const headers: Record<string, string> = {};
      for (const line of headerText.split("\r\n").slice(1)) {
        const idx = line.indexOf(":");
        if (idx <= 0) {
          continue;
        }
        headers[line.slice(0, idx).trim().toLowerCase()] = line.slice(idx + 1).trim();
      }

      if (statusCode !== 101) {
        fail(
          new Error(
            `WSS upgrade failed: HTTP ${statusCode || "unknown"} (${statusLine || "no status"}); ` +
              `form=${form} request-line=${requestLine} hex=${requestLineHex}` +
              (attachSessionHeader ? " with x-nv-sessionid" : ""),
          ),
        );
        return;
      }
      if (headers["sec-websocket-accept"] !== expectedAccept) {
        fail(new Error("WSS upgrade failed: invalid Sec-WebSocket-Accept"));
        return;
      }
      succeed(socket!, leftover);
    });
  });
}

/** Primary: Bifrost-shaped GET /. Fallback: CloudMatch-style /v2/session/<id>. */
const UPGRADE_TARGET_FORMS: NvstWssUpgradeTargetForm[] = ["slash", "sessionPath"];

export async function connectNvstWss(
  host: string,
  port: number,
  timeoutMs: number,
  sessionId?: string,
  onLog?: (message: string) => void,
): Promise<Duplex> {
  let lastError: Error | null = null;
  for (const form of UPGRADE_TARGET_FORMS) {
    try {
      const target = buildNvstWssUpgradeRequestTarget(host, port, form, sessionId);
      onLog?.(
        `Trying WSS upgrade form=${form} (GET ${target} HTTP/1.1) raw-TLS Bifrost headers`,
      );
      return await connectNvstWssOnce(host, port, timeoutMs, form, sessionId, false);
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      lastError = err;
      const message = err.message;
      if (sessionId && /\bHTTP 403\b/.test(message)) {
        onLog?.(`Upgrade HTTP 403 on form=${form}; retrying with x-nv-sessionid`);
        try {
          return await connectNvstWssOnce(host, port, timeoutMs, form, sessionId, true);
        } catch (retryError) {
          lastError = retryError instanceof Error ? retryError : new Error(String(retryError));
        }
      }
      if (!/\bHTTP (400|404)\b/.test(message)) {
        throw lastError;
      }
      onLog?.(message);
    }
  }
  throw lastError ?? new Error("WSS upgrade failed for all request-target forms");
}

export function encodeWsTextFrame(payload: Buffer): Buffer {
  const len = payload.length;
  const mask = randomBytes(4);
  let header: Buffer;
  if (len < 126) {
    header = Buffer.alloc(2);
    header[0] = 0x81; // FIN + text
    header[1] = 0x80 | len; // MASK bit set
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 0x80 | 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 0x80 | 127;
    header.writeUInt32BE(0, 2);
    header.writeUInt32BE(len, 6);
  }
  const masked = Buffer.alloc(len);
  for (let i = 0; i < len; i++) {
    masked[i] = payload[i]! ^ mask[i % 4]!;
  }
  return Buffer.concat([header, mask, masked]);
}

/** Minimal client-side WS frame reader (text + close; ignores ping/pong/control). */
export class WsFrameReader {
  private buffer = Buffer.alloc(0);

  push(chunk: Buffer): Buffer[] {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    const messages: Buffer[] = [];
    while (true) {
      if (this.buffer.length < 2) {
        break;
      }
      const b1 = this.buffer[1]!;
      const masked = (b1 & 0x80) !== 0;
      let payloadLen = b1 & 0x7f;
      let offset = 2;
      if (payloadLen === 126) {
        if (this.buffer.length < 4) {
          break;
        }
        payloadLen = this.buffer.readUInt16BE(2);
        offset = 4;
      } else if (payloadLen === 127) {
        if (this.buffer.length < 10) {
          break;
        }
        const high = this.buffer.readUInt32BE(2);
        const low = this.buffer.readUInt32BE(6);
        if (high !== 0 || low > 0x7fffffff) {
          throw new Error("WS frame too large");
        }
        payloadLen = low;
        offset = 10;
      }
      const maskLen = masked ? 4 : 0;
      if (this.buffer.length < offset + maskLen + payloadLen) {
        break;
      }
      const opcode = this.buffer[0]! & 0x0f;
      let payload = this.buffer.subarray(offset + maskLen, offset + maskLen + payloadLen);
      if (masked) {
        const mask = this.buffer.subarray(offset, offset + 4);
        const unmasked = Buffer.alloc(payloadLen);
        for (let i = 0; i < payloadLen; i++) {
          unmasked[i] = payload[i]! ^ mask[i % 4]!;
        }
        payload = unmasked;
      }
      this.buffer = this.buffer.subarray(offset + maskLen + payloadLen);
      if (opcode === 0x1 || opcode === 0x2) {
        messages.push(payload);
      } else if (opcode === 0x8) {
        throw new Error("RTSPS WebSocket closed");
      }
      // 0x9/0xa ping/pong ignored for probe
    }
    return messages;
  }
}
