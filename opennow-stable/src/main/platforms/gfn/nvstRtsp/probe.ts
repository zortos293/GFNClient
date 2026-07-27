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

import { createSocket, type Socket } from "node:dgram";

import type { NvstVideoSession } from "@shared/gfn";

import {
  extractVideoPeer,
  header,
  RtspOverWssClient,
} from "./rtspClient";
import {
  buildAnnounceSdp,
  extractHmacSeed,
  extractRuntimeEncryptionKey,
  generateClientEncryptionKey,
  packSrtpMasterKeySalt,
  redactKey,
} from "./sdp";

const DEFAULT_PROBE_TIMEOUT_MS = 20_000;

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
  const client = new RtspOverWssClient(
    host,
    port,
    timeoutMs,
    (message) => log(input.onLog, message),
  );
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
