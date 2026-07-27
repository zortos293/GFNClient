import type { VideoCodec } from "@shared/gfn";

function normalizeCodec(name: string): string {
  const upper = name.toUpperCase();
  return upper === "HEVC" ? "H265" : upper;
}

export function rewriteH265TierFlag(
  sdp: string,
  tierFlag: 0 | 1,
): { sdp: string; replacements: number } {
  const lineEnding = sdp.includes("\r\n") ? "\r\n" : "\n";
  const lines = sdp.split(/\r?\n/);

  const h265Payloads = new Set<string>();
  let inVideoSection = false;

  for (const line of lines) {
    if (line.startsWith("m=video")) {
      inVideoSection = true;
      continue;
    }
    if (line.startsWith("m=") && inVideoSection) {
      inVideoSection = false;
    }
    if (!inVideoSection || !line.startsWith("a=rtpmap:")) {
      continue;
    }

    const [, rest = ""] = line.split(":", 2);
    const [pt = "", codecPart = ""] = rest.split(/\s+/, 2);
    const codecName = normalizeCodec((codecPart.split("/")[0] ?? "").trim());
    if (pt && codecName === "H265") {
      h265Payloads.add(pt);
    }
  }

  if (h265Payloads.size === 0) {
    return { sdp, replacements: 0 };
  }

  let replacements = 0;
  const rewritten = lines.map((line) => {
    if (!line.startsWith("a=fmtp:")) {
      return line;
    }

    const [, rest = ""] = line.split(":", 2);
    const [pt = ""] = rest.split(/\s+/, 1);
    if (!pt || !h265Payloads.has(pt)) {
      return line;
    }

    const next = line.replace(/tier-flag=1/gi, `tier-flag=${tierFlag}`);
    if (next !== line) {
      replacements += 1;
    }
    return next;
  });

  return {
    sdp: rewritten.join(lineEnding),
    replacements,
  };
}

export function rewriteH265LevelIdByProfile(
  sdp: string,
  maxLevelByProfile: Partial<Record<1 | 2, number>>,
): { sdp: string; replacements: number } {
  const lineEnding = sdp.includes("\r\n") ? "\r\n" : "\n";
  const lines = sdp.split(/\r?\n/);

  const h265Payloads = new Set<string>();
  let inVideoSection = false;

  for (const line of lines) {
    if (line.startsWith("m=video")) {
      inVideoSection = true;
      continue;
    }
    if (line.startsWith("m=") && inVideoSection) {
      inVideoSection = false;
    }
    if (!inVideoSection || !line.startsWith("a=rtpmap:")) {
      continue;
    }

    const [, rest = ""] = line.split(":", 2);
    const [pt = "", codecPart = ""] = rest.split(/\s+/, 2);
    const codecName = normalizeCodec((codecPart.split("/")[0] ?? "").trim());
    if (pt && codecName === "H265") {
      h265Payloads.add(pt);
    }
  }

  if (h265Payloads.size === 0) {
    return { sdp, replacements: 0 };
  }

  let replacements = 0;
  const rewritten = lines.map((line) => {
    if (!line.startsWith("a=fmtp:")) {
      return line;
    }

    const [, rest = ""] = line.split(":", 2);
    const [pt = "", params = ""] = rest.split(/\s+/, 2);
    if (!pt || !params || !h265Payloads.has(pt)) {
      return line;
    }

    const profileMatch = params.match(/(?:^|;)\s*profile-id=(\d+)/i);
    const levelMatch = params.match(/(?:^|;)\s*level-id=(\d+)/i);
    if (!profileMatch?.[1] || !levelMatch?.[1]) {
      return line;
    }

    const profileNum = Number.parseInt(profileMatch[1], 10) as 1 | 2;
    const offeredLevel = Number.parseInt(levelMatch[1], 10);
    const maxLevel = maxLevelByProfile[profileNum];
    if (!Number.isFinite(offeredLevel) || !maxLevel || offeredLevel <= maxLevel) {
      return line;
    }

    const next = line.replace(/(level-id=)(\d+)/i, `$1${maxLevel}`);
    if (next !== line) {
      replacements += 1;
    }
    return next;
  });

  return {
    sdp: rewritten.join(lineEnding),
    replacements,
  };
}

interface PreferCodecOptions {
  preferHevcProfileId?: 1 | 2;
}

export function preferCodec(sdp: string, codec: VideoCodec, options?: PreferCodecOptions): string {
  console.log(`[SDP] preferCodec: filtering SDP for codec "${codec}"`);
  const lineEnding = sdp.includes("\r\n") ? "\r\n" : "\n";
  const lines = sdp.split(/\r?\n/);

  let inVideoSection = false;
  const payloadTypesByCodec = new Map<string, string[]>();
  const codecByPayloadType = new Map<string, string>();
  const rtxAptByPayloadType = new Map<string, string>();
  const fmtpByPayloadType = new Map<string, string>();

  for (const line of lines) {
    if (line.startsWith("m=video")) {
      inVideoSection = true;
      continue;
    }
    if (line.startsWith("m=") && inVideoSection) {
      inVideoSection = false;
    }

    if (!inVideoSection || !line.startsWith("a=rtpmap:")) {
      continue;
    }

    const [, rest = ""] = line.split("a=rtpmap:");
    const [pt, codecPart] = rest.split(/\s+/, 2);
    const codecName = normalizeCodec((codecPart ?? "").split("/")[0] ?? "");
    if (!pt || !codecName) {
      continue;
    }

    const list = payloadTypesByCodec.get(codecName) ?? [];
    list.push(pt);
    payloadTypesByCodec.set(codecName, list);
    codecByPayloadType.set(pt, codecName);

    continue;
  }

  // Parse RTX apt mappings from fmtp lines so we can keep RTX for chosen codec payloads
  inVideoSection = false;
  for (const line of lines) {
    if (line.startsWith("m=video")) {
      inVideoSection = true;
      continue;
    }
    if (line.startsWith("m=") && inVideoSection) {
      inVideoSection = false;
    }
    if (!inVideoSection || !line.startsWith("a=fmtp:")) {
      continue;
    }

    const [, rest = ""] = line.split(":", 2);
    const [pt = "", params = ""] = rest.split(/\s+/, 2);
    if (!pt || !params) {
      continue;
    }

    const aptMatch = params.match(/(?:^|;)\s*apt=(\d+)/i);
    if (aptMatch?.[1]) {
      rtxAptByPayloadType.set(pt, aptMatch[1]);
    }
    fmtpByPayloadType.set(pt, params);
  }

  // Log all codecs found in the SDP
  for (const [name, pts] of payloadTypesByCodec.entries()) {
    console.log(`[SDP] preferCodec: found codec ${name} with payload types [${pts.join(", ")}]`);
  }

  const preferredPayloads = payloadTypesByCodec.get(codec) ?? [];
  if (preferredPayloads.length === 0) {
    console.log(`[SDP] preferCodec: codec "${codec}" NOT found in offer — returning SDP unmodified`);
    return sdp;
  }

  // H265 often appears with multiple profiles in one offer.
  // Prefer profile-id=1 first (widest decoder compatibility), then others.
  const orderedPreferredPayloads = codec === "H265" && options?.preferHevcProfileId
    ? [...preferredPayloads].sort((a, b) => {
      const pa = fmtpByPayloadType.get(a) ?? "";
      const pb = fmtpByPayloadType.get(b) ?? "";
      const score = (fmtp: string): number => {
        const profile = fmtp.match(/(?:^|;)\s*profile-id=(\d+)/i)?.[1];
        if (profile === String(options.preferHevcProfileId)) return 0;
        if (!profile) return 1;
        return 2;
      };
      return score(pa) - score(pb);
    })
    : preferredPayloads;

  const preferred = new Set(orderedPreferredPayloads);

  const allowed = new Set<string>(preferred);

  // Keep RTX payloads linked to preferred payloads (apt mapping)
  for (const [rtxPt, apt] of rtxAptByPayloadType.entries()) {
    if (preferred.has(apt) && codecByPayloadType.get(rtxPt) === "RTX") {
      allowed.add(rtxPt);
    }
  }

  // Do NOT keep FLEXFEC/RED/ULPFEC during hard codec filtering.
  // Chromium can otherwise negotiate a "video" m-line with only FEC payloads
  // when primary codec intersection fails, causing black video with live audio.

  console.log(`[SDP] preferCodec: preferred ordered payloads [${orderedPreferredPayloads.join(", ")}] for ${codec}`);
  console.log(`[SDP] preferCodec: keeping payload types [${Array.from(allowed).join(", ")}] for ${codec}`);

  const filtered: string[] = [];
  inVideoSection = false;

  for (const line of lines) {
    if (line.startsWith("m=video")) {
      inVideoSection = true;
      const parts = line.split(/\s+/);
      const header = parts.slice(0, 3);
      const available = parts.slice(3).filter((pt) => allowed.has(pt));
      const ordered: string[] = [];

      for (const pt of orderedPreferredPayloads) {
        if (available.includes(pt)) {
          ordered.push(pt);
        }
      }
      for (const pt of available) {
        if (!preferred.has(pt)) {
          ordered.push(pt);
        }
      }

      filtered.push(ordered.length > 0 ? [...header, ...ordered].join(" ") : line);
      continue;
    }

    if (line.startsWith("m=") && inVideoSection) {
      inVideoSection = false;
    }

    if (inVideoSection) {
      if (
        line.startsWith("a=rtpmap:") ||
        line.startsWith("a=fmtp:") ||
        line.startsWith("a=rtcp-fb:")
      ) {
        const [, rest = ""] = line.split(":", 2);
        const [pt = ""] = rest.split(/\s+/, 1);
        if (pt && !allowed.has(pt)) {
          continue;
        }
      }
    }

    filtered.push(line);
  }

  return filtered.join(lineEnding);
}
