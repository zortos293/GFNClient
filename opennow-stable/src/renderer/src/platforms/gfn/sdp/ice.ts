import type { MediaConnectionInfo } from "@shared/gfn";

interface IceCredentials {
  ufrag: string;
  pwd: string;
  fingerprint: string;
}

/**
 * Convert dash-separated hostname to dotted IP if it matches the GFN pattern.
 * e.g. "80-250-97-40.cloudmatchbeta.nvidiagrid.net" -> "80.250.97.40"
 * e.g. "161-248-11-132.bpc.geforcenow.nvidiagrid.net" -> "161.248.11.132"
 */
export function extractPublicIp(hostOrIp: string): string | null {
  if (!hostOrIp) return null;

  // Already a dotted IP?
  if (/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostOrIp)) {
    return hostOrIp;
  }

  // Dash-separated hostname: take the first label, convert dashes to dots
  const firstLabel = hostOrIp.split(".")[0] ?? "";
  const parts = firstLabel.split("-");
  if (parts.length === 4 && parts.every((p) => /^\d{1,3}$/.test(p))) {
    return parts.join(".");
  }

  return null;
}

/**
 * Fix 0.0.0.0 ICE candidate addresses with the actual server IP.
 *
 * The official GFN web client leaves c=IN IP4 0.0.0.0 lines intact and only
 * rewrites a=candidate lines when an explicit WebRTC media endpoint is present.
 * Rewriting every c= line to an RTSPS/session host can make DTLS close before
 * media tracks arrive.
 */
export function fixServerIp(sdp: string, serverIp: string): string {
  const ip = extractPublicIp(serverIp);
  if (!ip) {
    console.log(`[SDP] fixServerIp: could not extract IP from "${serverIp}"`);
    return sdp;
  }
  let fixed = sdp;
  const candidateCount = (sdp.match(/(a=candidate:\S+\s+\d+\s+\w+\s+\d+\s+)0\.0\.0\.0(\s+)/g) ?? []).length;
  if (candidateCount > 0) {
    fixed = fixed.replace(
      /(a=candidate:\S+\s+\d+\s+\w+\s+\d+\s+)0\.0\.0\.0(\s+)/g,
      `$1${ip}$2`,
    );
    console.log(`[SDP] fixServerIp: replaced ${candidateCount} a=candidate lines with ${ip}`);
  }

  return fixed;
}

function normalizeWebRtcMediaConnectionInfo(
  mediaConnectionInfo: MediaConnectionInfo | null | undefined,
): { ip: string; port: number } | null {
  if (!mediaConnectionInfo) {
    return null;
  }
  if (mediaConnectionInfo.usage !== 2 && mediaConnectionInfo.usage !== 17) {
    return null;
  }
  const ip = mediaConnectionInfo.ip.trim();
  const port = Math.round(mediaConnectionInfo.port);
  if (!ip || !Number.isFinite(port) || port <= 0 || port > 65535) {
    return null;
  }
  return { ip, port };
}

export function rewriteIceCandidateEndpoint(
  candidate: string,
  mediaConnectionInfo: MediaConnectionInfo | null | undefined,
): { candidate: string; rewritten: boolean } {
  const endpoint = normalizeWebRtcMediaConnectionInfo(mediaConnectionInfo);
  if (!endpoint) {
    return { candidate, rewritten: false };
  }

  const match = candidate.match(
    /^(a=candidate:\S+\s+\d+\s+\S+\s+\d+\s+|candidate:\S+\s+\d+\s+\S+\s+\d+\s+)(\S+)(\s+)(\d+)(?=\s|$)/,
  );
  if (!match) {
    return { candidate, rewritten: false };
  }

  const [, prefix = "", oldIp = "", separator = " ", oldPort = ""] = match;
  if (oldIp === endpoint.ip && Number.parseInt(oldPort, 10) === endpoint.port) {
    return { candidate, rewritten: false };
  }

  return {
    candidate: candidate.replace(
      match[0],
      `${prefix}${endpoint.ip}${separator}${endpoint.port}`,
    ),
    rewritten: true,
  };
}

export function rewriteSdpIceCandidateEndpoints(
  sdp: string,
  mediaConnectionInfo: MediaConnectionInfo | null | undefined,
): { sdp: string; replacements: number } {
  if (!normalizeWebRtcMediaConnectionInfo(mediaConnectionInfo)) {
    return { sdp, replacements: 0 };
  }

  const lineEnding = sdp.includes("\r\n") ? "\r\n" : "\n";
  let replacements = 0;
  const rewritten = sdp.split(/\r?\n/).map((line) => {
    if (!line.startsWith("a=candidate:")) {
      return line;
    }
    const result = rewriteIceCandidateEndpoint(line, mediaConnectionInfo);
    if (result.rewritten) {
      replacements += 1;
    }
    return result.candidate;
  });

  return { sdp: rewritten.join(lineEnding), replacements };
}

/**
 * Extract the server's ice-ufrag from the offer SDP.
 * Needed for manual ICE candidate injection (ice-lite servers).
 */
export function extractIceUfragFromOffer(sdp: string): string {
  const match = sdp.match(/a=ice-ufrag:([^\r\n]+)/);
  return match?.[1]?.trim() ?? "";
}

export function extractIceCredentials(sdp: string): IceCredentials {
  const ufrag = sdp
    .split(/\r?\n/)
    .find((line) => line.startsWith("a=ice-ufrag:"))
    ?.replace("a=ice-ufrag:", "")
    .trim();
  const pwd = sdp
    .split(/\r?\n/)
    .find((line) => line.startsWith("a=ice-pwd:"))
    ?.replace("a=ice-pwd:", "")
    .trim();
  const fingerprint = sdp
    .split(/\r?\n/)
    .find((line) => line.startsWith("a=fingerprint:sha-256 "))
    ?.replace("a=fingerprint:sha-256 ", "")
    .trim();

  return {
    ufrag: ufrag ?? "",
    pwd: pwd ?? "",
    fingerprint: fingerprint ?? "",
  };
}
