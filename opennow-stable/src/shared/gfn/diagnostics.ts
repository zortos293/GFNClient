import type { IceCandidatePayload } from "./signaling";

const SENSITIVE_QUERY_KEY = /(authorization|token|credential|password|secret|cookie|code|deviceid|userid)/i;

/** Keep a stable correlation key without copying the complete provider session ID into logs. */
export function streamDiagnosticId(value: string | null | undefined): string {
  const cleaned = value?.trim() ?? "";
  if (!cleaned) return "-";
  return cleaned.length <= 12
    ? cleaned
    : `${cleaned.slice(0, 4)}...${cleaned.slice(-6)}`;
}

/** Redact sensitive query values and shorten the session correlation value. */
export function signalingUrlForDiagnostics(
  raw: string | null | undefined,
  sessionId: string,
): string {
  if (!raw?.trim()) return "(default signaling URL)";
  try {
    const url = new URL(raw);
    for (const key of url.searchParams.keys()) {
      const value = url.searchParams.get(key) ?? "";
      if (SENSITIVE_QUERY_KEY.test(key)) {
        url.searchParams.set(key, "[redacted]");
      } else if (sessionId && value.includes(sessionId)) {
        url.searchParams.set(key, value.replaceAll(sessionId, streamDiagnosticId(sessionId)));
      }
    }
    return url.toString().replaceAll(sessionId, streamDiagnosticId(sessionId));
  } catch {
    return raw.replaceAll(sessionId, streamDiagnosticId(sessionId));
  }
}

/** Summarize negotiation shape without retaining full SDP credentials or payloads. */
export function sdpDiagnosticSummary(label: string, sdp: string): string {
  const lines = sdp.split(/\r?\n/).filter(Boolean);
  const media = lines.filter((line) => line.startsWith("m=")).join("|").slice(0, 180);
  const endpoints = lines
    .filter((line) => line.startsWith("a=candidate:"))
    .map((line) => line.match(/^a=candidate:\S+\s+\d+\s+\S+\s+\d+\s+([^\s]+)\s+(\d+)/i))
    .filter((match): match is RegExpMatchArray => Boolean(match))
    .map((match) => `${match[1]}:${match[2]}`)
    .filter((value, index, all) => all.indexOf(value) === index)
    .slice(0, 6)
    .join(",");
  const codecs = lines
    .filter((line) => line.startsWith("a=rtpmap:"))
    .map((line) => line.slice(line.indexOf(" ") + 1).split("/")[0]?.trim())
    .filter((codec): codec is string => Boolean(codec))
    .filter((codec, index, all) => all.indexOf(codec) === index)
    .slice(0, 12)
    .join(",");
  const candidateCount = lines.filter((line) => line.startsWith("a=candidate:")).length;
  const hasIce = lines.some((line) => line.startsWith("a=ice-ufrag:"))
    && lines.some((line) => line.startsWith("a=ice-pwd:"));
  const hasFingerprint = lines.some((line) => line.startsWith("a=fingerprint:"));
  return [
    label,
    `lines=${lines.length}`,
    `media=${media || "none"}`,
    `codecs=${codecs || "unknown"}`,
    `candidates=${candidateCount}`,
    `endpoints=${endpoints || "none"}`,
    `ice=${hasIce}`,
    `fingerprint=${hasFingerprint}`,
  ].join(" ");
}

/** Preserve transport/type evidence while keeping noisy ICE lines out of the event log. */
export function iceCandidateDiagnosticSummary(candidate: IceCandidatePayload): string {
  const raw = candidate.candidate;
  const protocol = raw.match(/\s(udp|tcp)\s/i)?.[1]?.toLowerCase() ?? "unknown";
  const type = raw.match(/\styp\s+([a-z0-9]+)/i)?.[1]?.toLowerCase() ?? "unknown";
  const endpoint = raw.match(/^candidate:\S+\s+\d+\s+\S+\s+\d+\s+([^\s]+)\s+(\d+)/i);
  return [
    `mid=${candidate.sdpMid ?? ""}`,
    `line=${candidate.sdpMLineIndex ?? 0}`,
    `type=${type}`,
    `protocol=${protocol}`,
    `address=${endpoint ? `${endpoint[1]}:${endpoint[2]}` : "unknown"}`,
  ].join(" ");
}
