/**
 * Classic NVST RTSPS-over-WSS handshake (GO-with-Moonlight-hypothesis).
 *
 * Runs OPTIONS → DESCRIBE → SETUP advertised streams → ANNOUNCE. GFN disables PLAY.
 * Extracts or generates runtime.encryptionKey for SRTP, then returns nvstVideo
 * handoff fields for the native UDP receiver. The UDP reservation is released
 * immediately before handoff so native can rebind clientUdpPort, while the
 * production owner retains the RTSPS control client until stream teardown.
 *
 * Evidence: docs/research/nvst-wire-format.md, nvst-srtp-key-derivation.md,
 * nvst-announce-allowlist-1080p60.json.
 */

import { createSocket } from "node:dgram";
import { createHmac, randomBytes } from "node:crypto";

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
  extractNvstIceCredentials,
  extractMediaControl,
  extractRuntimeEncryptionKey,
  generateClientEncryptionKey,
  generateNvstIceCredentials,
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
  localAddress?: string;
  send?(payload: Buffer, peerHost: string, peerPort: number): Promise<void>;
  release(): Promise<void>;
}

export interface NvstRtspNegotiationDependencies {
  createClient(
    host: string,
    port: number,
    timeoutMs: number,
    onLog?: (message: string) => void,
  ): NvstRtspClient;
  reserveUdpPort(peerHost?: string, peerPort?: number): Promise<NvstUdpPortReservation>;
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
  | "missing-audio-control"
  | "missing-control-stream"
  | "missing-video-peer"
  | "missing-ice-credentials"
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

const CRC32_TABLE = Array.from({ length: 256 }, (_, value) => {
  let crc = value;
  for (let bit = 0; bit < 8; bit += 1) {
    crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return crc >>> 0;
});

function appendStunAttribute(packet: Buffer[], type: number, value: Buffer): void {
  const header = Buffer.alloc(4);
  header.writeUInt16BE(type, 0);
  header.writeUInt16BE(value.length, 2);
  packet.push(header, value);
  const padding = value.length % 4;
  if (padding) {
    packet.push(Buffer.alloc(4 - padding));
  }
}

export function buildNvstStunBindingRequest(
  localUsernameFragment: string,
  remoteUsernameFragment: string,
  remotePassword: string,
  transactionId: Buffer = randomBytes(12),
): Buffer {
  if (transactionId.length !== 12) {
    throw new Error("STUN transaction ID must be 12 bytes");
  }
  const header = Buffer.alloc(20);
  header.writeUInt16BE(0x0001, 0);
  header.writeUInt32BE(0x2112a442, 4);
  transactionId.copy(header, 8);
  const parts = [header];
  appendStunAttribute(
    parts,
    0x0006,
    Buffer.from(`${remoteUsernameFragment}:${localUsernameFragment}`, "utf8"),
  );
  let packet = Buffer.concat(parts);
  header.writeUInt16BE(packet.length - 20 + 24, 2);
  packet = Buffer.concat(parts);
  appendStunAttribute(parts, 0x0008, createHmac("sha1", remotePassword).update(packet).digest());
  packet = Buffer.concat(parts);
  header.writeUInt16BE(packet.length - 20 + 8, 2);
  packet = Buffer.concat(parts);
  let crc = 0xffffffff;
  for (const byte of packet) {
    crc = (crc >>> 8) ^ CRC32_TABLE[(crc ^ byte) & 0xff]!;
  }
  const fingerprint = Buffer.alloc(4);
  fingerprint.writeUInt32BE(((crc ^ 0xffffffff) ^ 0x5354554e) >>> 0);
  appendStunAttribute(parts, 0x8028, fingerprint);
  return Buffer.concat(parts);
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
  connections: Array<{
    usage?: number;
    port?: number;
    appLevelProtocol?: number;
    resourcePath?: string | null;
  }>,
  fallbackHost?: string | null,
): string[] {
  const endpoints: string[] = [];
  const seen = new Set<string>();

  for (const conn of connections) {
    const resourcePath = typeof conn.resourcePath === "string" ? conn.resourcePath.trim() : "";
    if (/^rtsps?:\/\//i.test(resourcePath)) {
      if (!seen.has(resourcePath)) {
        seen.add(resourcePath);
        endpoints.push(resourcePath);
      }
      continue;
    }
    if (conn.usage !== 16 && conn.appLevelProtocol !== 6) {
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

async function bindEphemeralUdp(peerHost?: string, peerPort?: number): Promise<NvstUdpPortReservation> {
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
  if (peerHost && peerPort) {
    await new Promise<void>((resolve, reject) => {
      socket.once("error", reject);
      socket.connect(peerPort, peerHost, () => {
        socket.off("error", reject);
        resolve();
      });
    });
  }
  const address = socket.address();
  if (typeof address === "string") {
    socket.close();
    throw new Error("Unexpected UDP socket address shape");
  }
  let released = false;
  if (peerHost && peerPort) {
    socket.disconnect();
  }
  return {
    port: address.port,
    localAddress: address.address === "0.0.0.0" ? undefined : address.address,
    send: async (payload, host, port) => {
      await new Promise<void>((resolve, reject) => {
        socket.send(payload, port, host, (error) => error ? reject(error) : resolve());
      });
    },
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
  let audioUdp: NvstUdpPortReservation | null = null;
  const auxiliaryUdp: NvstUdpPortReservation[] = [];
  const holePunchTimers: NodeJS.Timeout[] = [];
  let videoHolePunchTimer: NodeJS.Timeout | null = null;
  let session: string | null = null;

  try {
    log(
      input.onLog,
      `Connecting RTSPS WSS ${wssUrl} via raw-TLS Bifrost-shaped upgrade (GET /rtsp) (session ${input.sessionId})`,
    );
    await client.connect(input.sessionId);
    steps.push("wss-open");

    const rtspTarget = `rtsp://${parsedEndpoint.host}`;
    const commonHeaders = {
      "X-GS-Version": "14.2",
      Host: parsedEndpoint.host,
    };
    const options = await client.request("OPTIONS", rtspTarget, commonHeaders);
    if (options.statusCode !== 200) {
      throw new Error(`OPTIONS failed: ${options.statusCode} ${options.statusText}`);
    }
    steps.push("options");
    log(input.onLog, `OPTIONS ok (X-GS-Version=${header(options.headers, "x-gs-version") ?? "n/a"})`);

    const describe = await client.request("DESCRIBE", rtspTarget, {
      ...commonHeaders,
      Accept: "application/sdp",
      "X-Nv-Abtesting": "2",
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
    const audioControl = extractMediaControl(describe.body, "audio");
    if (!audioControl) {
      throw new NvstRtspNegotiationError(
        "missing-audio-control",
        "DESCRIBE response did not advertise an audio media control URI",
      );
    }
    const describedControls = [...describe.body.matchAll(/^a=control:(.+)$/gm)]
      .map((match) => match[1]?.trim())
      .filter((control): control is string => Boolean(control));
    const controlStream = describedControls.find((control) => /(?:^|[=/])control\/0(?:\/|$)/i.test(control));
    if (!controlStream) {
      throw new NvstRtspNegotiationError(
        "missing-control-stream",
        "DESCRIBE response did not advertise the primary control/0 stream",
      );
    }
    log(input.onLog, `DESCRIBE media controls: ${describedControls.join(", ") || "none"}`);
    const videoControlUri = videoControl;
    const hmacSeed = extractHmacSeed(describe.body);
    const iceCredentials = extractNvstIceCredentials(describe.body);
    const legacyIceUsername = /^a=(?:x-nv-)?general\.iceUsernameFragment:([^\r\n]+)$/mi
      .exec(describe.body)?.[1]?.trim();
    const legacyIcePassword = /^a=(?:x-nv-)?general\.iceUsernamePwd:([^\r\n]+)$/mi
      .exec(describe.body)?.[1]?.trim();
    const v2IceUsername = /^a=(?:x-nv-)?general\.iceUserNameFragmentV2:([^\r\n]+)$/mi
      .exec(describe.body)?.[1]?.trim();
    const v2IcePassword = /^a=(?:x-nv-)?general\.icePasswordV2:([^\r\n]+)$/mi
      .exec(describe.body)?.[1]?.trim();
    const describedSrtpProfile = extractAdvertisedSrtpProfileFromSdp(describe.body);
    const describedKey = extractRuntimeEncryptionKey(describe.body);
    const dtlsFingerprint = /^a=(?:x-nv-)?general\.dtlsFingerprintV2:([^\r\n]+)$/mi
      .exec(describe.body)?.[1]?.trim();
    const serverTransport = /^a=(?:x-nv-)?general\.serverTransport:([A-Za-z0-9_-]{1,32})\s*$/mi
      .exec(describe.body)?.[1];
    log(
      input.onLog,
      `DESCRIBE transport metadata: dtlsFingerprintBytes=${dtlsFingerprint?.length ?? 0}, serverTransport=${serverTransport ?? "absent"}, legacyIce=${legacyIceUsername?.length ?? 0}/${legacyIcePassword?.length ?? 0}, v2Ice=${v2IceUsername?.length ?? 0}/${v2IcePassword?.length ?? 0}, iceVariantsMatch=${legacyIceUsername === v2IceUsername && legacyIcePassword === v2IcePassword}`,
    );
    let encryptionKeyHex: string;
    let encryptionKeyId: number;
    let clientGenerated = false;
    if (describedKey) {
      encryptionKeyHex = describedKey.aesKeyHex;
      encryptionKeyId = describedKey.keyId;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, videoControl=${videoControl}, HMAC ${hmacSeed ? "present" : "missing"}, ICE credentials ${iceCredentials ? `present (ufragBytes=${iceCredentials.usernameFragment.length}, pwdBytes=${iceCredentials.password.length})` : "missing"}, encryptionKey ${redactKey(encryptionKeyHex)} from server)`,
      );
    } else {
      const generated = generateClientEncryptionKey();
      encryptionKeyHex = generated.aesKeyHex;
      encryptionKeyId = generated.keyId;
      clientGenerated = true;
      log(
        input.onLog,
        `DESCRIBE ok (Session=${session}, videoControl=${videoControl}, HMAC ${hmacSeed ? "present" : "missing"}, ICE credentials ${iceCredentials ? `present (ufragBytes=${iceCredentials.usernameFragment.length}, pwdBytes=${iceCredentials.password.length})` : "missing"}, encryptionKey absent — client-generated ${redactKey(encryptionKeyHex)} for ANNOUNCE)`,
      );
    }

    udp = await dependencies.reserveUdpPort(parsedEndpoint.hostname, Number(parsedEndpoint.port || "322"));
    const clientPort = udp.port;
    const setup = await client.request("SETUP", videoControlUri, {
      ...commonHeaders,
      Session: session,
      Transport: `unicast;X-GS-ClientPort=${clientPort}-${clientPort + 1}`,
      "If-Modified-Since": "Thu, 01 Jan 1970 00:00:00 GMT",
    });
    if (setup.statusCode !== 200) {
      throw new Error(`SETUP video failed: ${setup.statusCode} ${setup.statusText}`);
    }
    steps.push("setup-video");
    session = header(setup.headers, "session")?.split(";")[0]?.trim() ?? session;
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
    if (pingVersion === 6 && (!iceCredentials || !pingPayload)) {
      throw new NvstRtspNegotiationError(
        "missing-ice-credentials",
        "SETUP selected ping version 6 but its username payload or DESCRIBE password was missing",
      );
    }
    const localIceCredentials = iceCredentials ? generateNvstIceCredentials() : null;
    const startAuthenticatedHolePunch = (
      reservation: NvstUdpPortReservation,
      peer: { ip: string; port: number },
      remoteUsernameFragment: string,
    ): NodeJS.Timeout | null => {
      if (!localIceCredentials || !iceCredentials || !reservation.send) {
        return null;
      }
      const sendPing = (): void => {
        const packet = buildNvstStunBindingRequest(
          localIceCredentials.usernameFragment,
          remoteUsernameFragment,
          iceCredentials.password,
        );
        void reservation.send?.(packet, peer.ip, peer.port).catch(() => undefined);
      };
      sendPing();
      const timer = setInterval(sendPing, 20);
      timer.unref();
      holePunchTimers.push(timer);
      return timer;
    };
    if (pingPayload) {
      videoHolePunchTimer = startAuthenticatedHolePunch(udp, videoPeer, pingPayload);
    }
    const setupHeaderNames = Object.keys(setup.headers).sort();
    const setupCredentialHeaders = setupHeaderNames
      .filter((name) => /(ice|stun|credential|password|user)/i.test(name))
      .map((name) => `${name}[${header(setup.headers, name)?.length ?? 0}]`);
    log(
      input.onLog,
      `SETUP ${videoControl} ok (clientPort=${clientPort}, peer=${videoPeer.ip}:${videoPeer.port}, srtpProfile=${srtpProfile ?? "legacy-default"}, pingVersion=${Number.isFinite(pingVersion) ? pingVersion : "legacy"}, pingPayloadBytes=${pingPayload ? Buffer.byteLength(pingPayload, "utf8") : 4}, headers=${setupHeaderNames.join(",")}, credentialHeaders=${setupCredentialHeaders.join(",") || "none"})`,
    );

    audioUdp = await dependencies.reserveUdpPort(parsedEndpoint.hostname, Number(parsedEndpoint.port || "322"));
    const audioClientPort = audioUdp.port;
    const audioSetup = await client.request("SETUP", audioControl, {
      ...commonHeaders,
      Session: session,
      Transport: `unicast;X-GS-ClientPort=${audioClientPort}-${audioClientPort + 1}`,
      "If-Modified-Since": "Thu, 01 Jan 1970 00:00:00 GMT",
    });
    if (audioSetup.statusCode !== 200) {
      throw new Error(`SETUP audio failed: ${audioSetup.statusCode} ${audioSetup.statusText}`);
    }
    steps.push("setup-audio");
    session = header(audioSetup.headers, "session")?.split(";")[0]?.trim() ?? session;
    log(input.onLog, `SETUP ${audioControl} ok (clientPort=${audioClientPort})`);
    const audioPeer = extractVideoPeer(header(audioSetup.headers, "transport"));
    if (audioPeer) {
      startAuthenticatedHolePunch(
        audioUdp,
        audioPeer,
        header(audioSetup.headers, "x-nv-ping-payload") ?? session,
      );
    }

    for (const control of describedControls) {
      if (control === videoControl || control === audioControl) {
        continue;
      }
      const reservation = await dependencies.reserveUdpPort(
        parsedEndpoint.hostname,
        Number(parsedEndpoint.port || "322"),
      );
      auxiliaryUdp.push(reservation);
      const auxiliarySetup = await client.request("SETUP", control, {
        ...commonHeaders,
        Session: session,
        Transport: `unicast;X-GS-ClientPort=${reservation.port}-${reservation.port + 1}`,
        "If-Modified-Since": "Thu, 01 Jan 1970 00:00:00 GMT",
      });
      if (auxiliarySetup.statusCode !== 200) {
        throw new Error(`SETUP ${control} failed: ${auxiliarySetup.statusCode} ${auxiliarySetup.statusText}`);
      }
      steps.push(`setup-${control}`);
      session = header(auxiliarySetup.headers, "session")?.split(";")[0]?.trim() ?? session;
      const auxiliaryPeer = extractVideoPeer(header(auxiliarySetup.headers, "transport"));
      if (auxiliaryPeer) {
        startAuthenticatedHolePunch(
          reservation,
          auxiliaryPeer,
          header(auxiliarySetup.headers, "x-nv-ping-payload") ?? session,
        );
      }
      log(
        input.onLog,
        `SETUP ${control} ok (clientPort=${reservation.port}, peer=${auxiliaryPeer ? `${auxiliaryPeer.ip}:${auxiliaryPeer.port}` : "absent"}, pingVersion=${header(auxiliarySetup.headers, "x-nv-ping") ?? "legacy"})`,
      );
    }

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
      "/",
      {
        ...commonHeaders,
        Session: session,
        "Content-Type": "application/sdp",
      },
      buildAnnounceSdp({
        resolution: input.resolution,
        fps: input.fps,
        encryptionKeyHex,
        encryptionKeyId,
        iceCredentials: localIceCredentials ?? undefined,
      }),
    );
    if (announce.statusCode !== 200) {
      throw new Error(`ANNOUNCE failed: ${announce.statusCode} ${announce.statusText}`);
    }
    steps.push("announce");
    log(
      input.onLog,
      `ANNOUNCE ok (allowlist + encryptionKey${localIceCredentials ? ` + ICE V2 credentials (local ufragBytes=${localIceCredentials.usernameFragment.length}, pwdBytes=${localIceCredentials.password.length})` : ""})`,
    );

    steps.push("play-skipped");
    log(input.onLog, "PLAY skipped because the GFN Bifrost flow disables it after ANNOUNCE");

    if (videoHolePunchTimer) {
      clearInterval(videoHolePunchTimer);
      const index = holePunchTimers.indexOf(videoHolePunchTimer);
      if (index >= 0) {
        holePunchTimers.splice(index, 1);
      }
      videoHolePunchTimer = null;
    }
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
      pingVersion: Number.isFinite(pingVersion) ? pingVersion : undefined,
      localIceUsernameFragment: localIceCredentials?.usernameFragment,
      localIcePassword: localIceCredentials?.password,
      remoteIceUsernameFragment: pingVersion === 6 ? pingPayload : iceCredentials?.usernameFragment,
      remoteIcePassword: iceCredentials?.password,
      codec: input.codec,
    };
    log(
      input.onLog,
      `ANNOUNCE complete — NVST video handoff ready after UDP reservation release (peer ${videoPeer.ip}:${videoPeer.port}, clientUdp ${clientPort})`,
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
        for (const timer of holePunchTimers.splice(0)) {
          clearInterval(timer);
        }
        await audioUdp?.release().catch(() => undefined);
        audioUdp = null;
        await Promise.all(
          auxiliaryUdp.splice(0).map((reservation) => reservation.release().catch(() => undefined)),
        );
        await teardownAndClose(client, endpoint, session, reason, input.onLog);
      },
    };
  } catch (error) {
    for (const timer of holePunchTimers.splice(0)) {
      clearInterval(timer);
    }
    await udp?.release().catch(() => undefined);
    await audioUdp?.release().catch(() => undefined);
    await Promise.all(auxiliaryUdp.map((reservation) => reservation.release().catch(() => undefined)));
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
