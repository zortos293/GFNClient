import type { VideoCodec } from "./stream";

function normalizeCodec(name: string): string {
  const upper = name.toUpperCase();
  return upper === "HEVC" ? "H265" : upper;
}

function parseBundleMids(lines: readonly string[]): Set<string> | null {
  const bundle = lines.find((line) => line.startsWith("a=group:BUNDLE "));
  return bundle ? new Set(bundle.trim().split(/\s+/).slice(1)) : null;
}

export function extractNegotiatedVideoCodec(sdp: string): VideoCodec | null {
  const lines = sdp.split(/\r?\n/);
  const bundleMids = parseBundleMids(lines);

  for (let start = 0; start < lines.length; start += 1) {
    const mediaLine = lines[start];
    if (!mediaLine?.startsWith("m=video")) {
      continue;
    }

    const mediaParts = mediaLine.trim().split(/\s+/);
    if (mediaParts[1] === "0") {
      continue;
    }

    let end = start + 1;
    while (end < lines.length && !lines[end]?.startsWith("m=")) {
      end += 1;
    }
    const section = lines.slice(start + 1, end);
    const mid = section.find((line) => line.startsWith("a=mid:"))?.slice("a=mid:".length);
    if (bundleMids && (!mid || !bundleMids.has(mid))) {
      continue;
    }

    const codecByPayloadType = new Map<string, string>();
    for (const line of section) {
      if (!line.startsWith("a=rtpmap:")) {
        continue;
      }
      const [, rest = ""] = line.split("a=rtpmap:");
      const [payloadType, codecPart] = rest.split(/\s+/, 2);
      const codec = normalizeCodec((codecPart ?? "").split("/")[0] ?? "");
      if (payloadType && codec) {
        codecByPayloadType.set(payloadType, codec);
      }
    }

    for (const payloadType of mediaParts.slice(3)) {
      const codec = codecByPayloadType.get(payloadType);
      if (codec === "H264" || codec === "H265" || codec === "AV1") {
        return codec;
      }
    }
  }

  return null;
}

export function answerHasVideoCodec(sdp: string): boolean {
  return extractNegotiatedVideoCodec(sdp) !== null;
}
