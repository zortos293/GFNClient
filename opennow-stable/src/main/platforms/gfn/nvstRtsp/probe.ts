/**
 * Classic NVST RTSPS-over-WSS handshake (GO-with-Moonlight-hypothesis).
 *
 * Runs OPTIONS → DESCRIBE → SETUP advertised video control → ANNOUNCE → PLAY.
 * Extracts or generates runtime.encryptionKey for SRTP, then returns nvstVideo
 * handoff fields for the native UDP receiver. The UDP reservation is released
 * immediately before handoff so native can rebind clientUdpPort, while the
 * production owner retains the RTSPS control client until stream teardown.
 *
 * Evidence: docs/research/nvst-wire-format.md, nvst-srtp-key-derivation.md,
 * nvst-announce-allowlist-1080p60.json.
 */

import { createSocket } from "node:dgram";

import type { NvstSrtpProfile, NvstVideoSession } from "@shared/gfn";

import {
  extractVideoPeer,
  header,
  RtspOverWssClient,
  type ParsedRtspResponse,
} from "./rtspClient";
import {
  buildAnnounceSdp,
  extractHmacSeed,
  extractMediaControl,
  extractRuntimeEncryptionKey,
  generateClientEncryptionKey,
  packSrtpMasterKeySalt,
  redactKey,
} from "./sdp";
import {
  deriveSrtpSaltHex,
  extractAdvertisedSrtpProfileFromHeaders,
  extractAdvertisedSrtpProfileFromSdp,
} from "./srtp";

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

export interface NvstRtspClient {
  connect(sessionId?: string): Promise<void>;
  request(
    method: string,
    uri: string,
    extraHeaders?: Record<string, string>,
    body?: string,
  ): Promise<ParsedRtspResponse>;
  close(): void;
}

export interface NvstUdpPortReservation {
  port: number;
  release(): Promise<void>;
}

export interface NvstRtspNegotiationDependencies {
  createClient(
    host: string,
    port: number,
    timeoutMs: number,
    onLog?: (message: string) => void,
  ): NvstRtspClient;
  reserveUdpPort(): Promise<NvstUdpPortReservation>;
}

export interface NvstSrtpMaterial {
  /** 64-char hex AES-256 key (runtime.encryptionKey). */
  aesKeyHex: string;
  /** Unsigned key id used for salt packing (runtime.encryptionKeyId as u32). */
  keyId: number;
  /** 88-char hex libsrtp master key||salt (AES-256 || 12-byte salt). */
  masterKeySaltHex: string;
  /** 24-char hex salt derived from runtime.encryptionKeyId. */
  saltHex: string;
  /** Present only when DESCRIBE or SETUP explicitly advertises a known profile. */
  profile?: NvstSrtpProfile;
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

export type NvstRtspNegotiationErrorCode =
  | "missing-rtsps-endpoint"
  | "missing-video-control"
  | "missing-video-peer"
  | "conflicting-srtp-profile"
  | "negotiation-failed";

export class NvstRtspNegotiationError extends Error {
  constructor(
    readonly code: NvstRtspNegotiationErrorCode,
    message: string,
    options?: ErrorOptions,
    readonly steps: string[] = [],
  ) {
    super(message, options);
    this.name = "NvstRtspNegotiationError";
  }
}

export interface NvstRtspSession {
  endpoint: string;
  session: string;
  hmacSeedPresent: boolean;
  videoPeer: { ip: string; port: number };
  clientUdpPort: number;
  srtp: NvstSrtpMaterial;
  pingPayload?: string;
  pingVersion?: number;
  videoSession: NvstVideoSession;
  steps: string[];
  release(reason?: string): Promise<void>;
}

function log(onLog: NvstRtspProbeInput["onLog"], message: string): void {
  console.log(`[NvstRtspProbe] ${message}`);
  onLog?.(message);
}

export function selectPrimaryRtspsEndpoint(endpoints: string[]): string | null {
  const normalized = endpoints
    .map((value) => value.trim())
    .filter((value) => /^rtsps?:\/\//i.test(value));
  return normalized[0] ?? null;
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
    if (conn.usage !== 16) {
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

export function resolveRtspControlUri(baseUri: string, control: string): string {
  if (/^rtsps?:\/\//i.test(control)) {
    return control;
  }

  const base = new URL(baseUri.replace(/^rtsps:/i, "https:").replace(/^rtsp:/i, "http:"));
  const scheme = baseUri.toLowerCase().startsWith("rtsp:") ? "rtsp:" : "rtsps:";
  if (control.startsWith("/")) {
    return `${scheme}//${base.host}${control}`;
  }
  return `${baseUri.replace(/\/+$/, "")}/${control.replace(/^\/+/, "")}`;
}

async function bindEphemeralUdp(): Promise<NvstUdpPortReservation> {
  const socket = createSocket("udp4");
  try {
    await new Promise<void>((resolve, reject) => {
      socket.once("error", reject);
      socket.bind(0, "0.0.0.0", () => {
        socket.off("error", reject);
        resolve();
      });
    });
  } catch (error) {
    try {
      socket.close();
    } catch {}
    throw error;
  }
  const address = socket.address();
  if (typeof address === "string") {
    socket.close();
    throw new Error("Unexpected UDP socket address shape");
  }
  let released = false;
  return {
    port: address.port,
    release: async () => {
      if (released) {
        return;
      }
      released = true;
      await new Promise<void>((resolve) => socket.close(resolve));
    },
  };
}

const DEFAULT_NEGOTIATION_DEPENDENCIES: NvstRtspNegotiationDependencies = {
  createClient: (host, port, timeoutMs, onLog) =>
    new RtspOverWssClient(host, port, timeoutMs, onLog),
  reserveUdpPort: bindEphemeralUdp,
};

async function teardownAndClose(
  client: NvstRtspClient,
  endpoint: string,
  session: string | null,
  reason: string,
  onLog?: (message: string) => void,
): Promise<void> {
  try {
    if (session) {
      const response = await client.request("TEARDOWN", endpoint, { Session: session });
      if (response.statusCode === 200) {
        log(onLog, `TEARDOWN ok (${reason})`);
      } else {
        log(onLog, `TEARDOWN returned ${response.statusCode} ${response.statusText} (${reason})`);
      }
    }
  } catch (error) {
    log(onLog, `TEARDOWN failed (${reason}): ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    client.close();
  }
}

export async function negotiateNvstRtspSession(
  input: NvstRtspProbeInput,
  dependencies: NvstRtspNegotiationDependencies = DEFAULT_NEGOTIATION_DEPENDENCIES,
): Promise<NvstRtspSession> {
  const steps: string[] = [];
  const endpoint = selectPrimaryRtspsEndpoint(input.rtspsEndpoints);
  if (!endpoint) {
    throw new NvstRtspNegotiationError(
      "missing-rtsps-endpoint",
      "No rtsps:// endpoints available on the session",
    );
  }

  const timeoutMs = input.timeoutMs ?? DEFAULT_PROBE_TIMEOUT_MS;
  const wssUrl = rtspsUrlToWssUrl(endpoint);
  const parsedEndpoint = new URL(endpoint.replace(/^rtsps:/i, "https:").replace(/^rtsp:/i, "http:"));
  const client = dependencies.createClient(
    parsedEndpoint.hostname,
    Number(parsedEndpoint.port || "322"),
    timeoutMs,
    (message) => log(input.onLog, message),
  );
  let udp: NvstUdpPortReservation | null = null;
  let session: string | null = null;

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
    session = header(describe.headers, "session")?.split(";")[0]?.trim() ?? null;
    if (!session) {
      throw new Error("DESCRIBE response missing Session header");
    }
    const videoControl = extractMediaControl(describe.body, "video");
    if (!videoControl) {
      throw new NvstRtspNegotiationError(
        "missing-video-control",
        "DESCRIBE response did not advertise a video media control URI",
      );
    }
    const controlBase = header(describe.headers, "content-base")
      ?? header(describe.headers, "content-location")
      ?? endpoint;
    const videoControlUri = resolveRtspControlUri(controlBase, videoControl);
    const hmacSeed = extractHmacSeed(describe.body);
    const describedSrtpProfile = extractAdvertisedSrtpProfileFromSdp(describe.body);
    const describedKey = extractRuntimeEncryptionKey(describe.body);
    let encryptionKeyHex: string;
    let encryptionKeyId: number;
    let clientGenerated = false;
    if (describedKey) {
      encryptionKeyHex = describedKey.aesKeyHex;
      encryptionKeyId = describedKey.keyId;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, videoControl=${videoControl}, HMAC ${hmacSeed ? "present" : "missing"}, encryptionKey ${redactKey(encryptionKeyHex)} from server)`,
      );
    } else {
      const generated = generateClientEncryptionKey();
      encryptionKeyHex = generated.aesKeyHex;
      encryptionKeyId = generated.keyId;
      clientGenerated = true;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, videoControl=${videoControl}, HMAC ${hmacSeed ? "present" : "missing"}, encryptionKey absent — client-generated ${redactKey(encryptionKeyHex)} for ANNOUNCE)`,
      );
    }

    udp = await dependencies.reserveUdpPort();
    const clientPort = udp.port;
    const setup = await client.request("SETUP", videoControlUri, {
      Session: session,
      Transport: `unicast;X-GS-ClientPort=${clientPort}-${clientPort + 1}`,
    });
    if (setup.statusCode !== 200) {
      throw new Error(`SETUP video failed: ${setup.statusCode} ${setup.statusText}`);
    }
    steps.push("setup-video");
    const setupSrtpProfile = extractAdvertisedSrtpProfileFromHeaders(setup.headers);
    if (
      describedSrtpProfile
      && setupSrtpProfile
      && describedSrtpProfile !== setupSrtpProfile
    ) {
      throw new NvstRtspNegotiationError(
        "conflicting-srtp-profile",
        `DESCRIBE advertised ${describedSrtpProfile} but SETUP advertised ${setupSrtpProfile}`,
      );
    }
    const srtpProfile = setupSrtpProfile ?? describedSrtpProfile ?? undefined;
    const videoPeer = extractVideoPeer(header(setup.headers, "transport"));
    if (!videoPeer) {
      throw new NvstRtspNegotiationError(
        "missing-video-peer",
        "SETUP did not return video peer (X-GS-ServerPort/source)",
      );
    }
    const pingPayload = header(setup.headers, "x-nv-ping-payload");
    const pingVersionRaw = header(setup.headers, "x-nv-ping");
    const pingVersion = pingVersionRaw ? Number(pingVersionRaw) : undefined;
    log(
      input.onLog,
      `SETUP ${videoControl} ok (clientPort=${clientPort}, peer=${videoPeer.ip}:${videoPeer.port}, srtpProfile=${srtpProfile ?? "legacy-default"})`,
    );

    const saltHex = deriveSrtpSaltHex(encryptionKeyId);
    const srtp: NvstSrtpMaterial = {
      aesKeyHex: encryptionKeyHex,
      keyId: encryptionKeyId,
      masterKeySaltHex: packSrtpMasterKeySalt(encryptionKeyHex, encryptionKeyId),
      saltHex,
      profile: srtpProfile,
      clientGenerated,
    };
    const announce = await client.request(
      "ANNOUNCE",
      endpoint,
      {
        Session: session,
        "Content-Type": "application/sdp",
      },
      buildAnnounceSdp({
        resolution: input.resolution,
        fps: input.fps,
        encryptionKeyHex,
        encryptionKeyId,
      }),
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

    await udp.release();
    udp = null;

    const videoSession: NvstVideoSession = {
      clientUdpPort: clientPort,
      videoPeerIp: videoPeer.ip,
      videoPeerPort: videoPeer.port,
      srtpAesKeyHex: srtp.aesKeyHex,
      srtpKeyId: srtp.keyId,
      srtpSaltHex: srtp.saltHex,
      srtpProfile: srtp.profile,
      pingPayload,
      codec: input.codec,
    };
    log(
      input.onLog,
      `PLAY ok — NVST video handoff ready after UDP reservation release (peer ${videoPeer.ip}:${videoPeer.port}, clientUdp ${clientPort})`,
    );

    let released = false;
    return {
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
      release: async (reason = "NVST session released") => {
        if (released) {
          return;
        }
        released = true;
        await teardownAndClose(client, endpoint, session, reason, input.onLog);
      },
    };
  } catch (error) {
    await udp?.release().catch(() => undefined);
    await teardownAndClose(client, endpoint, session, "failed negotiation", input.onLog);
    if (error instanceof NvstRtspNegotiationError) {
      throw new NvstRtspNegotiationError(error.code, error.message, {
        cause: error,
      }, [...steps]);
    }
    const message = error instanceof Error ? error.message : String(error);
    throw new NvstRtspNegotiationError("negotiation-failed", message, {
      cause: error,
    }, [...steps]);
  }
}

export async function runNvstRtspHandshakeProbe(
  input: NvstRtspProbeInput,
  dependencies?: NvstRtspNegotiationDependencies,
): Promise<NvstRtspProbeResult> {
  const endpoint = selectPrimaryRtspsEndpoint(input.rtspsEndpoints) ?? "";
  try {
    const negotiated = await negotiateNvstRtspSession(input, dependencies);
    const result: NvstRtspProbeResult = {
      ok: true,
      endpoint: negotiated.endpoint,
      session: negotiated.session,
      hmacSeedPresent: negotiated.hmacSeedPresent,
      videoPeer: negotiated.videoPeer,
      clientUdpPort: negotiated.clientUdpPort,
      srtp: negotiated.srtp,
      pingPayload: negotiated.pingPayload,
      pingVersion: negotiated.pingVersion,
      videoSession: negotiated.videoSession,
      steps: negotiated.steps,
    };
    await negotiated.release("handshake probe complete");
    return result;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    log(input.onLog, `Probe failed: ${message}`);
    return {
      ok: false,
      endpoint,
      hmacSeedPresent: false,
      steps: error instanceof NvstRtspNegotiationError ? error.steps : [],
      error: message,
    };
  }
}
