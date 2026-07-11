/**
 * Classic NVST RTSPS-over-WSS handshake (GO-with-Moonlight-hypothesis).
 *
 * Runs OPTIONS → DESCRIBE → SETUP video/0/0 → ANNOUNCE → PLAY against `:322`.
 * Extracts or generates runtime.encryptionKey for SRTP, then returns nvstVideo
 * handoff fields for the native UDP receive scaffold. Does not keep the UDP
 * socket open across the process boundary — native rebinds clientUdpPort.
 *
 * Evidence: docs/research/nvst-wire-format.md, nvst-srtp-key-derivation.md,
 * nvst-announce-allowlist-1080p60.json.
 */

import { createHash, randomBytes } from "node:crypto";
import { createSocket, type Socket } from "node:dgram";
import { connect as tlsConnect, type TLSSocket } from "node:tls";
import type { Duplex } from "node:stream";

import type { NvstVideoSession } from "@shared/gfn";

const GS_VERSION = "14.2";
const DEFAULT_PROBE_TIMEOUT_MS = 20_000;

/** Minimal ANNOUNCE attrs from docs/research/nvst-announce-allowlist-1080p60.json */
const ANNOUNCE_ALLOWLIST = {
  video: {
    clientViewportWd: "1920",
    clientViewportHt: "1080",
    maxFPS: "60",
    videoSplitEncodeStripsPerFrame: "3",
    updateSplitEncodeStateDynamically: "1",
    packetSize: "1408",
    enableRtpNack: "1",
    rtpNackQueueLength: "2048",
    rtpNackQueueMaxPackets: "1024",
    rtpNackMaxPacketCount: "64",
    "framePacing.mode": "2",
    "framePacing.pid.minTargetFrameTimeUs": "16666",
    "adaptiveQuantization.spatialAQSetting": "7",
    "adaptiveQuantization.temporalAQSetting": "0",
    "adaptiveQuantization.spatialAQStrength": "12",
    "adaptiveQuantization.qpThresholdAdjPercent": "2",
    "adaptiveQuantization.saqAdaptMinQpThresholdPercent": "40",
    "adaptiveQuantization.saqAdaptMaxQpThresholdPercent": "100",
    "adaptiveQuantization.saqAdaptDecayStrengthX100": "250",
    "adaptiveQuantization.perfAdjEnablement": "1",
    enableAv1RcPrecisionFactor: "1",
  },
  vqos: {
    "fec.enable": "1",
    "fec.repairPercent": "20",
    "fec.repairMinPercent": "5",
    "fec.repairMaxPercent": "40",
    "bllFec.enable": "1",
    "grc.enable": "0",
    "drc.enable": "0",
    "dfc.adjustResAndFps": "0",
    calculateAvgVideoStreamingBitrate: "1",
    "bw.maximumBitrateKbps": "100000",
    "bw.minimumBitrateKbps": "1000",
  },
  packetPacing: {
    version: "3",
    mode: "1",
    numGroups: "5",
    maxDelayUs: "1000",
    minNumPacketsFrame: "10",
    minNumPacketsPerGroup: "0",
    enableAccurateSleep: "1",
    enableSmoothTransition: "1",
    allowFpsBasedToggle: "1",
  },
  ri: {
    partialReliableThresholdMs: "300",
    timestampsEnabled: "1",
    useMultipleGamepads: "1",
    usePartiallyReliableUdpChannel: "0",
    enablePartiallyReliableTransferGamepad: "255",
    enablePartiallyReliableTransferHid: "-1",
  },
  aqos: {
    enableRedundancy: "1",
    redundancyLevel: "2",
  },
  general: {
    rtspWebSocketPerConnection: "1",
    "enetControlChannel.mtuSize": "1191",
    pingIntervalBeforeConnectionMs: "20",
    pingIntervalAfterConnectionMs: "100",
  },
  runtime: {
    audioSrtp: "0",
    micSrtp: "0",
  },
} as const;

export interface NvstRtspProbeInput {
  sessionId: string;
  rtspsEndpoints: string[];
  resolution?: string;
  fps?: number;
  codec?: string;
  timeoutMs?: number;
  onLog?: (message: string) => void;
}

export interface NvstSrtpMaterial {
  /** 64-char hex AES-256 key (runtime.encryptionKey). */
  aesKeyHex: string;
  /** Unsigned key id used for salt packing (runtime.encryptionKeyId as u32). */
  keyId: number;
  /** 88-char hex libsrtp master key||salt (AES-256 || 12-byte salt). */
  masterKeySaltHex: string;
  /** True when OpenNOW generated the key for ANNOUNCE (DESCRIBE lacked it). */
  clientGenerated: boolean;
}

export interface NvstRtspProbeResult {
  ok: boolean;
  endpoint: string;
  session?: string;
  hmacSeedPresent: boolean;
  videoPeer?: { ip: string; port: number };
  clientUdpPort?: number;
  srtp?: NvstSrtpMaterial;
  pingPayload?: string;
  pingVersion?: number;
  /** Handoff for native UDP video (shared NvstVideoSession shape). */
  videoSession?: NvstVideoSession;
  steps: string[];
  error?: string;
}

interface ParsedRtspResponse {
  statusCode: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
}

function log(onLog: NvstRtspProbeInput["onLog"], message: string): void {
  console.log(`[NvstRtspProbe] ${message}`);
  onLog?.(message);
}

export function selectPrimaryRtspsEndpoint(endpoints: string[]): string | null {
  const normalized = endpoints
    .map((value) => value.trim())
    .filter((value) => /^rtsps?:\/\//i.test(value));
  if (normalized.length === 0) {
    return null;
  }
  const port322 = normalized.find((url) => /:322(?:\/|$)/.test(url));
  return port322 ?? normalized[0] ?? null;
}

/**
 * Logging identity only (`wss://host:port`, no path). Real `:322` connect uses
 * {@link connectNvstWss} with absolute-form upgrade cascade (rtsps/wss/https).
 * See docs/research/nvst-rtsps-wss-connect.md and `_tmp-bifrost2-ws-400-followup.txt`.
 */
export function rtspsUrlToWssUrl(rtspsUrl: string): string {
  const parsed = new URL(rtspsUrl.replace(/^rtsps:/i, "https:").replace(/^rtsp:/i, "http:"));
  const port = parsed.port || "322";
  return `wss://${parsed.hostname}:${port}`;
}

export function collectRtspsEndpoints(
  connections: Array<{ usage?: number; port?: number; resourcePath?: string | null }>,
  fallbackHost?: string | null,
): string[] {
  const endpoints: string[] = [];
  const seen = new Set<string>();

  for (const conn of connections) {
    if (conn.usage !== 14) {
      continue;
    }
    const resourcePath = typeof conn.resourcePath === "string" ? conn.resourcePath.trim() : "";
    if (/^rtsps?:\/\//i.test(resourcePath)) {
      if (!seen.has(resourcePath)) {
        seen.add(resourcePath);
        endpoints.push(resourcePath);
      }
      continue;
    }
    if (!fallbackHost || !conn.port) {
      continue;
    }
    const synthesized = `rtsps://${fallbackHost}:${conn.port}`;
    if (!seen.has(synthesized)) {
      seen.add(synthesized);
      endpoints.push(synthesized);
    }
  }

  return endpoints;
}

function parseResolution(resolution: string | undefined): { width: number; height: number } {
  const match = /^(\d+)\s*[xX]\s*(\d+)$/.exec(resolution?.trim() ?? "");
  if (!match) {
    return { width: 1920, height: 1080 };
  }
  return { width: Number(match[1]), height: Number(match[2]) };
}

export function buildAnnounceSdp(
  options: {
    resolution?: string;
    fps?: number;
    encryptionKeyHex?: string;
    encryptionKeyId?: number;
  } = {},
): string {
  const { width, height } = parseResolution(options.resolution);
  const fps = options.fps && options.fps > 0 ? Math.round(options.fps) : 60;
  const frameTimeUs = String(Math.round(1_000_000 / fps));

  const lines: string[] = [
    "v=0",
    "o=- 0 0 IN IP4 127.0.0.1",
    "s=OpenNOW NVST Handshake",
    "t=0 0",
  ];

  const pushGroup = (
    prefix: string,
    indexed: boolean,
    values: Record<string, string>,
  ): void => {
    for (const [key, value] of Object.entries(values)) {
      let nextValue = value;
      if (prefix === "video" && key === "clientViewportWd") {
        nextValue = String(width);
      } else if (prefix === "video" && key === "clientViewportHt") {
        nextValue = String(height);
      } else if (prefix === "video" && key === "maxFPS") {
        nextValue = String(fps);
      } else if (prefix === "video" && key === "framePacing.pid.minTargetFrameTimeUs") {
        nextValue = frameTimeUs;
      }
      const name = indexed ? `x-nv-${prefix}[0].${key}` : `x-nv-${prefix}.${key}`;
      lines.push(`a=${name}:${nextValue}`);
    }
  };

  pushGroup("video", true, ANNOUNCE_ALLOWLIST.video);
  pushGroup("vqos", true, ANNOUNCE_ALLOWLIST.vqos);
  pushGroup("packetPacing", false, ANNOUNCE_ALLOWLIST.packetPacing);
  pushGroup("ri", false, ANNOUNCE_ALLOWLIST.ri);
  pushGroup("aqos", false, ANNOUNCE_ALLOWLIST.aqos);
  pushGroup("general", false, ANNOUNCE_ALLOWLIST.general);
  pushGroup("runtime", false, ANNOUNCE_ALLOWLIST.runtime);
  lines.push("a=x-nv-runtime.videoSrtp:1");
  if (options.encryptionKeyHex && options.encryptionKeyId !== undefined) {
    lines.push(`a=x-nv-runtime.encryptionKey:${options.encryptionKeyHex.toUpperCase()}`);
    // Signed i32 form matches geronimo runtime.encryptionKeyId dumps.
    const signedId = options.encryptionKeyId > 0x7fffffff
      ? options.encryptionKeyId - 0x1_0000_0000
      : options.encryptionKeyId;
    lines.push(`a=x-nv-runtime.encryptionKeyId:${signedId}`);
  }
  // Control protocol preference evidenced in geronimo: udp_ag (not encrypted).
  lines.push("a=x-nv-general.controlProtocol:udp_ag");
  lines.push("");
  return lines.join("\r\n");
}

function parseRtspResponse(raw: string): ParsedRtspResponse {
  const normalized = raw.replace(/\r\n/g, "\n");
  const splitAt = normalized.indexOf("\n\n");
  const headerBlock = splitAt >= 0 ? normalized.slice(0, splitAt) : normalized;
  const body = splitAt >= 0 ? normalized.slice(splitAt + 2) : "";
  const headerLines = headerBlock.split("\n").filter((line) => line.length > 0);
  const statusLine = headerLines[0] ?? "";
  const statusMatch = /^(?:RTSP|HTTP)\/\d(?:\.\d)?\s+(\d{3})\s*(.*)$/i.exec(statusLine);
  if (!statusMatch) {
    throw new Error(`Invalid RTSP status line: ${statusLine.slice(0, 120)}`);
  }
  const headers: Record<string, string> = {};
  for (const line of headerLines.slice(1)) {
    const idx = line.indexOf(":");
    if (idx <= 0) {
      continue;
    }
    headers[line.slice(0, idx).trim().toLowerCase()] = line.slice(idx + 1).trim();
  }
  return {
    statusCode: Number(statusMatch[1]),
    statusText: statusMatch[2]?.trim() ?? "",
    headers,
    body,
  };
}

function header(headers: Record<string, string>, name: string): string | undefined {
  return headers[name.toLowerCase()];
}

export function extractHmacSeed(sdp: string): string | null {
  const match = /^k=HMAC:([0-9A-Fa-f]{64})\s*$/m.exec(sdp);
  return match?.[1] ?? null;
}

export function extractVideoPeer(
  transport: string | undefined,
): { ip: string; port: number } | undefined {
  if (!transport) {
    return undefined;
  }
  const portMatch = /X-GS-ServerPort=(\d+)/i.exec(transport);
  const sourceMatch = /source=([^;,\s]+)/i.exec(transport);
  if (!portMatch || !sourceMatch) {
    return undefined;
  }
  return { ip: sourceMatch[1]!, port: Number(portMatch[1]) };
}

/**
 * Pack AES-256 key + keyId into libsrtp master key||salt (88 hex).
 * Salt = keyId as `%024x` (12 bytes BE). See docs/research/nvst-srtp-key-derivation.md.
 */
export function packSrtpMasterKeySalt(aesKeyHex: string, keyId: number): string {
  const key = aesKeyHex.trim().toUpperCase();
  if (!/^[0-9A-F]{64}$/.test(key)) {
    throw new Error(`encryptionKey must be 64 hex chars, got length ${key.length}`);
  }
  const id = keyId >>> 0;
  const salt = id.toString(16).toUpperCase().padStart(24, "0");
  return `${key}${salt}`;
}

export function extractRuntimeEncryptionKey(
  sdp: string,
): { aesKeyHex: string; keyId: number } | null {
  const keyMatch = /^a=x-nv-runtime\.encryptionKey:([0-9A-Fa-f]{64})\s*$/m.exec(sdp);
  const idMatch = /^a=x-nv-runtime\.encryptionKeyId:(-?\d+)\s*$/m.exec(sdp);
  if (!keyMatch || !idMatch) {
    return null;
  }
  let keyId = Number(idMatch[1]);
  if (!Number.isFinite(keyId)) {
    return null;
  }
  // Signed i32 in SDP → unsigned u32 for salt packing.
  if (keyId < 0) {
    keyId = keyId + 0x1_0000_0000;
  }
  return { aesKeyHex: keyMatch[1]!.toUpperCase(), keyId: keyId >>> 0 };
}

function generateClientEncryptionKey(): { aesKeyHex: string; keyId: number } {
  const aesKeyHex = randomBytes(32).toString("hex").toUpperCase();
  const keyId = randomBytes(4).readUInt32BE(0);
  return { aesKeyHex, keyId };
}

function redactKey(aesKeyHex: string): string {
  if (aesKeyHex.length < 8) {
    return "****";
  }
  return `${aesKeyHex.slice(0, 4)}…${aesKeyHex.slice(-4)}`;
}

async function bindEphemeralUdp(): Promise<{ socket: Socket; port: number }> {
  const socket = createSocket("udp4");
  await new Promise<void>((resolve, reject) => {
    socket.once("error", reject);
    socket.bind(0, "0.0.0.0", () => {
      socket.off("error", reject);
      resolve();
    });
  });
  const address = socket.address();
  if (typeof address === "string") {
    socket.close();
    throw new Error("Unexpected UDP socket address shape");
  }
  return { socket, port: address.port };
}

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

async function connectNvstWss(
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

function encodeWsTextFrame(payload: Buffer): Buffer {
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
class WsFrameReader {
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

class RtspOverWssClient {
  private socket: Duplex | null = null;
  private frameReader = new WsFrameReader();
  private buffer = Buffer.alloc(0);
  private cseq = 0;
  private pending: {
    resolve: (response: ParsedRtspResponse) => void;
    reject: (error: Error) => void;
  } | null = null;

  constructor(
    private readonly host: string,
    private readonly port: number,
    private readonly timeoutMs: number,
    private readonly onLog?: (message: string) => void,
  ) {}

  async connect(sessionId?: string): Promise<void> {
    const socket = await connectNvstWss(
      this.host,
      this.port,
      this.timeoutMs,
      sessionId,
      (message) => log(this.onLog, message),
    );
    this.socket = socket;
    socket.on("data", (chunk: Buffer) => this.onSocketData(chunk));
    socket.on("error", (error) => {
      if (this.pending) {
        this.pending.reject(error instanceof Error ? error : new Error(String(error)));
        this.pending = null;
      }
    });
    socket.on("close", () => {
      if (this.pending) {
        this.pending.reject(new Error("RTSPS WebSocket closed"));
        this.pending = null;
      }
    });
  }

  close(): void {
    try {
      this.socket?.destroy();
    } catch {
      // ignore
    }
    this.socket = null;
    if (this.pending) {
      this.pending.reject(new Error("RTSPS probe closed"));
      this.pending = null;
    }
  }

  async request(
    method: string,
    uri: string,
    extraHeaders: Record<string, string> = {},
    body = "",
  ): Promise<ParsedRtspResponse> {
    if (!this.socket || this.socket.destroyed) {
      throw new Error("RTSPS WebSocket is not open");
    }
    if (this.pending) {
      throw new Error("Overlapping RTSP requests are not supported");
    }

    this.cseq += 1;
    const headers: Record<string, string> = {
      CSeq: String(this.cseq),
      "Request-Id": String(this.cseq),
      "X-GS-Version": GS_VERSION,
      ...extraHeaders,
    };
    if (body.length > 0) {
      headers["Content-Length"] = String(Buffer.byteLength(body, "utf8"));
    }

    let message = `${method} ${uri} RTSP/1.0\r\n`;
    for (const [key, value] of Object.entries(headers)) {
      message += `${key}: ${value}\r\n`;
    }
    message += "\r\n";
    if (body.length > 0) {
      message += body;
    }

    return await new Promise<ParsedRtspResponse>((resolve, reject) => {
      const timer = setTimeout(() => {
        if (this.pending) {
          this.pending = null;
          reject(new Error(`RTSP ${method} timed out after ${this.timeoutMs}ms`));
        }
      }, this.timeoutMs);

      this.pending = {
        resolve: (response) => {
          clearTimeout(timer);
          resolve(response);
        },
        reject: (error) => {
          clearTimeout(timer);
          reject(error);
        },
      };

      try {
        this.socket?.write(encodeWsTextFrame(Buffer.from(message, "utf8")));
      } catch (error) {
        clearTimeout(timer);
        this.pending = null;
        reject(error instanceof Error ? error : new Error(String(error)));
      }
    });
  }

  private onSocketData(chunk: Buffer): void {
    try {
      for (const payload of this.frameReader.push(chunk)) {
        this.buffer = Buffer.concat([this.buffer, payload]);
        this.tryCompleteResponse();
      }
    } catch (error) {
      if (this.pending) {
        const pending = this.pending;
        this.pending = null;
        pending.reject(error instanceof Error ? error : new Error(String(error)));
      }
    }
  }

  private tryCompleteResponse(): void {
    if (!this.pending) {
      return;
    }

    const text = this.buffer.toString("utf8");
    const crlfSplit = text.indexOf("\r\n\r\n");
    const lfSplit = text.indexOf("\n\n");
    let headerEnd = -1;
    let sepLen = 0;
    if (crlfSplit >= 0 && (lfSplit < 0 || crlfSplit <= lfSplit)) {
      headerEnd = crlfSplit;
      sepLen = 4;
    } else if (lfSplit >= 0) {
      headerEnd = lfSplit;
      sepLen = 2;
    }
    if (headerEnd < 0) {
      return;
    }

    const headerText = text.slice(0, headerEnd);
    const contentLengthMatch = /^Content-Length:\s*(\d+)\s*$/im.exec(headerText);
    const contentLength = contentLengthMatch ? Number(contentLengthMatch[1]) : 0;
    const bodyStart = headerEnd + sepLen;
    // Use byte length of remaining buffer after header separator.
    const headerByteLength = Buffer.byteLength(text.slice(0, bodyStart), "utf8");
    const availableBodyBytes = this.buffer.length - headerByteLength;
    if (availableBodyBytes < contentLength) {
      return;
    }

    const totalBytes = headerByteLength + contentLength;
    const raw = this.buffer.subarray(0, totalBytes).toString("utf8");
    this.buffer = this.buffer.subarray(totalBytes);

    try {
      const parsed = parseRtspResponse(raw);
      const pending = this.pending;
      this.pending = null;
      pending?.resolve(parsed);
    } catch (error) {
      const pending = this.pending;
      this.pending = null;
      pending?.reject(error instanceof Error ? error : new Error(String(error)));
    }
  }
}

export async function runNvstRtspHandshakeProbe(input: NvstRtspProbeInput): Promise<NvstRtspProbeResult> {
  const steps: string[] = [];
  const endpoint = selectPrimaryRtspsEndpoint(input.rtspsEndpoints);
  if (!endpoint) {
    return {
      ok: false,
      endpoint: "",
      hmacSeedPresent: false,
      steps,
      error: "No rtsps:// endpoints available on the session",
    };
  }

  const timeoutMs = input.timeoutMs ?? DEFAULT_PROBE_TIMEOUT_MS;
  const wssUrl = rtspsUrlToWssUrl(endpoint);
  const parsedEndpoint = new URL(endpoint.replace(/^rtsps:/i, "https:").replace(/^rtsp:/i, "http:"));
  const host = parsedEndpoint.hostname;
  const port = Number(parsedEndpoint.port || "322");
  const client = new RtspOverWssClient(host, port, timeoutMs, input.onLog);
  let udp: { socket: Socket; port: number } | null = null;

  try {
    log(
      input.onLog,
      `Connecting RTSPS WSS ${wssUrl} via raw-TLS Bifrost-shaped upgrade (GET / then /v2/session/<id>) (session ${input.sessionId})`,
    );
    await client.connect(input.sessionId);
    steps.push("wss-open");

    const options = await client.request("OPTIONS", endpoint);
    if (options.statusCode !== 200) {
      throw new Error(`OPTIONS failed: ${options.statusCode} ${options.statusText}`);
    }
    steps.push("options");
    log(input.onLog, `OPTIONS ok (X-GS-Version=${header(options.headers, "x-gs-version") ?? "n/a"})`);

    const describe = await client.request("DESCRIBE", endpoint, {
      Accept: "application/sdp",
    });
    if (describe.statusCode !== 200) {
      throw new Error(`DESCRIBE failed: ${describe.statusCode} ${describe.statusText}`);
    }
    steps.push("describe");
    const session = header(describe.headers, "session")?.split(";")[0]?.trim();
    if (!session) {
      throw new Error("DESCRIBE response missing Session header");
    }
    const hmacSeed = extractHmacSeed(describe.body);
    const describedKey = extractRuntimeEncryptionKey(describe.body);
    let encryptionKeyHex: string;
    let encryptionKeyId: number;
    let clientGenerated = false;
    if (describedKey) {
      encryptionKeyHex = describedKey.aesKeyHex;
      encryptionKeyId = describedKey.keyId;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, HMAC ${hmacSeed ? "present" : "missing"}, encryptionKey ${redactKey(encryptionKeyHex)} from server)`,
      );
    } else {
      const generated = generateClientEncryptionKey();
      encryptionKeyHex = generated.aesKeyHex;
      encryptionKeyId = generated.keyId;
      clientGenerated = true;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, HMAC ${hmacSeed ? "present" : "missing"}, encryptionKey absent — client-generated ${redactKey(encryptionKeyHex)} for ANNOUNCE)`,
      );
    }

    udp = await bindEphemeralUdp();
    const clientPort = udp.port;
    // Transport uses X-GS-ClientPort (GameStream/Moonlight family). Official logs omit the
    // client Transport summary string; server still returns X-GS-ServerPort + source.
    const setup = await client.request("SETUP", `${endpoint}/streamid=video/0/0`, {
      Session: session,
      Transport: `unicast;X-GS-ClientPort=${clientPort}-${clientPort + 1}`,
    });
    if (setup.statusCode !== 200) {
      throw new Error(`SETUP video failed: ${setup.statusCode} ${setup.statusText}`);
    }
    steps.push("setup-video");
    const videoPeer = extractVideoPeer(header(setup.headers, "transport"));
    const pingPayload = header(setup.headers, "x-nv-ping-payload");
    const pingVersionRaw = header(setup.headers, "x-nv-ping");
    const pingVersion = pingVersionRaw ? Number(pingVersionRaw) : undefined;
    log(
      input.onLog,
      `SETUP video/0/0 ok (clientPort=${clientPort}, peer=${videoPeer ? `${videoPeer.ip}:${videoPeer.port}` : "unknown"})`,
    );

    const srtp: NvstSrtpMaterial = {
      aesKeyHex: encryptionKeyHex,
      keyId: encryptionKeyId,
      masterKeySaltHex: packSrtpMasterKeySalt(encryptionKeyHex, encryptionKeyId),
      clientGenerated,
    };

    const announceBody = buildAnnounceSdp({
      resolution: input.resolution,
      fps: input.fps,
      encryptionKeyHex,
      encryptionKeyId,
    });
    const announce = await client.request(
      "ANNOUNCE",
      endpoint,
      {
        Session: session,
        "Content-Type": "application/sdp",
      },
      announceBody,
    );
    if (announce.statusCode !== 200) {
      throw new Error(`ANNOUNCE failed: ${announce.statusCode} ${announce.statusText}`);
    }
    steps.push("announce");
    log(input.onLog, "ANNOUNCE ok (allowlist + encryptionKey; ICE/DTLS omitted)");

    const play = await client.request("PLAY", endpoint, {
      Session: session,
      Range: "npt=0.000-",
    });
    if (play.statusCode !== 200) {
      throw new Error(`PLAY failed: ${play.statusCode} ${play.statusText}`);
    }
    steps.push("play");

    // Close the probe UDP socket so the native streamer can rebind the same port.
    udp.socket.close();
    udp = null;

    if (!videoPeer) {
      throw new Error("SETUP did not return video peer (X-GS-ServerPort/source)");
    }

    const videoSession: NvstVideoSession = {
      clientUdpPort: clientPort,
      videoPeerIp: videoPeer.ip,
      videoPeerPort: videoPeer.port,
      srtpAesKeyHex: srtp.aesKeyHex,
      srtpKeyId: srtp.keyId,
      pingPayload,
      codec: input.codec,
    };

    log(
      input.onLog,
      `PLAY ok — NVST video handoff ready (peer ${videoPeer.ip}:${videoPeer.port}, clientUdp ${clientPort}); WebRTC remains for SCTP input`,
    );

    return {
      ok: true,
      endpoint,
      session,
      hmacSeedPresent: Boolean(hmacSeed),
      videoPeer,
      clientUdpPort: clientPort,
      srtp,
      pingPayload,
      pingVersion: Number.isFinite(pingVersion) ? pingVersion : undefined,
      videoSession,
      steps,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    log(input.onLog, `Probe failed: ${message}`);
    return {
      ok: false,
      endpoint,
      hmacSeedPresent: false,
      steps,
      error: message,
    };
  } finally {
    client.close();
    udp?.socket.close();
  }
}
