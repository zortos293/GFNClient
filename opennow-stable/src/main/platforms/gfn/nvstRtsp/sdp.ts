import { randomBytes } from "node:crypto";

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

export function extractHmacSeed(sdp: string): string | null {
  const match = /^k=HMAC:([0-9A-Fa-f]{64})\s*$/m.exec(sdp);
  return match?.[1] ?? null;
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

export function generateClientEncryptionKey(): { aesKeyHex: string; keyId: number } {
  const aesKeyHex = randomBytes(32).toString("hex").toUpperCase();
  const keyId = randomBytes(4).readUInt32BE(0);
  return { aesKeyHex, keyId };
}

export function redactKey(aesKeyHex: string): string {
  if (aesKeyHex.length < 8) {
    return "****";
  }
  return `${aesKeyHex.slice(0, 4)}…${aesKeyHex.slice(-4)}`;
}
