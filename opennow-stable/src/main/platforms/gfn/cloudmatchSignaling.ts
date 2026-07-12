import dns from "node:dns";

import type { IceServer, MediaConnectionInfo } from "@shared/gfn";

import type { CloudMatchResponse } from "./types";
import { collectRtspsEndpoints } from "./nvstRtspProbe";
import { isZoneHostname } from "./cloudmatchTransport";

const READY_SESSION_STATUSES = new Set([2, 3]);

export function isReadySessionStatus(status: number): boolean {
  return READY_SESSION_STATUSES.has(status);
}

async function resolveHostnameWithFallback(hostname: string): Promise<string | null> {
  // Try system resolver first, then fall back to Cloudflare (1.1.1.1) and Google (8.8.8.8)
  try {
    const r = await dns.promises.lookup(hostname);
    if (r && (r as any).address) return (r as any).address;
  } catch {
    // ignore and try custom resolvers
  }

  const fallbackServers = ["1.1.1.1", "8.8.8.8"];
  for (const server of fallbackServers) {
    try {
      const resolver = new dns.Resolver();
      resolver.setServers([server]);
      const addrs: string[] = await new Promise((resolve, reject) => {
        resolver.resolve4(hostname, (err, addresses) => {
          if (err) reject(err);
          else resolve(addresses);
        });
      });
      if (addrs && addrs.length > 0) return addrs[0];
    } catch {
      // try next fallback
    }
  }

  return null;
}

export async function normalizeIceServers(response: CloudMatchResponse): Promise<IceServer[]> {
  const raw = response.session.iceServerConfiguration?.iceServers ?? [];
  const servers = raw
    .map((entry) => {
      const urls = Array.isArray(entry.urls) ? entry.urls : [entry.urls];
      return {
        urls,
        username: entry.username,
        credential: entry.credential,
      };
    })
    .filter((entry) => entry.urls.length > 0);

  if (servers.length > 0) {
    // Attempt to resolve any hostnames in STUN/TURN URLs to IPs to avoid relying on the
    // renderer's DNS resolution. This makes it possible to try alternate DNS servers
    // when the system resolver fails.
    const resolvedServers: IceServer[] = [];
    for (const s of servers) {
      const resolvedUrls: string[] = [];
      for (const u of s.urls) {
          try {
          const m = u.match(/^([a-zA-Z0-9+.-]+):([^/]+)/);
          if (m) {
            const scheme = m[1];
            const hostPort = m[2];
            const host = hostPort.split(":")[0];
            const portPart = hostPort.includes(":") ? ":" + hostPort.split(":").slice(1).join(":") : "";

            // Helper to bracket IPv6 literals when necessary
            const bracketIfIpv6 = (h: string) => {
              if (h.startsWith("[") && h.endsWith("]")) return h;
              // Heuristic: contains ':' and is not an IPv4 dotted-quad
              if (h.includes(":") && !/^\d{1,3}(?:\.\d{1,3}){3}$/.test(h)) {
                return `[${h}]`;
              }
              return h;
            };

            // If host already looks like an IPv4 or bracketed IPv6, keep original URL
            if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(host) || /^\[[0-9a-fA-F:]+\]$/.test(host)) {
              resolvedUrls.push(u);
            } else {
              const ip = await resolveHostnameWithFallback(host);
              const finalHost = ip ?? host;
              const maybeBracketted = bracketIfIpv6(finalHost);
              resolvedUrls.push(`${scheme}:${maybeBracketted}${portPart}`);
            }
          } else {
            resolvedUrls.push(u);
          }
        } catch {
          resolvedUrls.push(u);
        }
      }
      resolvedServers.push({ urls: resolvedUrls, username: s.username, credential: s.credential });
    }

    return resolvedServers;
  }

  // Default fallbacks — try to resolve known STUN hostnames to IPs as well
  const defaults = ["s1.stun.gamestream.nvidia.com:19308", "stun.l.google.com:19302", "stun1.l.google.com:19302"];
  const out: IceServer[] = [];
  for (const d of defaults) {
    const parts = d.split(":");
    const host = parts[0];
    const port = parts.length > 1 ? `:${parts.slice(1).join(":")}` : "";
    const ip = await resolveHostnameWithFallback(host);
    const bracketIfIpv6 = (h: string) => (h.includes(":") && !h.startsWith("[") ? `[${h}]` : h);
    if (ip) out.push({ urls: [`stun:${bracketIfIpv6(ip)}${port}`] });
    else out.push({ urls: [`stun:${bracketIfIpv6(host)}${port}`] });
  }

  return out;
}

/**
 * Extract the streaming server IP from the CloudMatch response, matching Rust's
 * `streaming_server_ip()` priority chain:
 *   1. connectionInfo[usage==14].ip (direct IP)
 *   2. Host extracted from connectionInfo[usage==14].resourcePath (for rtsps:// URLs)
 *   3. sessionControlInfo.ip (fallback)
 */
export function streamingServerIp(response: CloudMatchResponse): string | null {
  const connections = response.session.connectionInfo ?? [];
  const sigConn = connections.find((conn) => conn.usage === 14);

  if (sigConn) {
    // Priority 1: Direct IP field
    const rawIp = sigConn.ip;
    const directIp = Array.isArray(rawIp) ? rawIp[0] : rawIp;
    if (directIp && directIp.length > 0) {
      return directIp;
    }

    // Priority 2: Extract host from resourcePath (Alliance format: rtsps://host:port)
    if (sigConn.resourcePath) {
      const host = extractHostFromUrl(sigConn.resourcePath);
      if (host) return host;
    }
  }

  // Priority 3: sessionControlInfo.ip
  const controlIp = response.session.sessionControlInfo?.ip;
  if (controlIp && controlIp.length > 0) {
    return Array.isArray(controlIp) ? controlIp[0] : controlIp;
  }

  return null;
}

/**
 * Extract host from a URL string (handles rtsps://, rtsp://, wss://, https://).
 * Matches Rust's extract_host_from_url().
 */
export function extractHostFromUrl(url: string): string | null {
  const prefixes = ["rtsps://", "rtsp://", "wss://", "https://"];
  let afterProto: string | null = null;
  for (const prefix of prefixes) {
    if (url.startsWith(prefix)) {
      afterProto = url.slice(prefix.length);
      break;
    }
  }
  if (!afterProto) return null;

  // Get host (before port or path)
  const host = afterProto.split(":")[0]?.split("/")[0];
  if (!host || host.length === 0 || host.startsWith(".")) return null;
  return host;
}

export function resolveSignaling(response: CloudMatchResponse): {
  serverIp: string;
  signalingServer: string;
  signalingUrl: string;
  mediaConnectionInfo?: MediaConnectionInfo;
  rtspsEndpoints: string[];
} {
  const connections = response.session.connectionInfo ?? [];
  const signalingConnection =
    connections.find((conn) => conn.usage === 14 && conn.ip) ?? connections.find((conn) => conn.ip);

  // Use the Rust-matching priority chain for server IP
  const serverIp = streamingServerIp(response);
  if (!serverIp) {
    throw new Error("CloudMatch response did not include a signaling host");
  }

  const resourcePath = signalingConnection?.resourcePath ?? "/nvst/";

  // Build signaling URL matching Rust's build_signaling_url() behavior:
  // - rtsps://host:port -> extract host, convert to wss://host/nvst/
  // - wss://... -> use as-is
  // - /path -> wss://serverIp:443/path
  // - fallback -> wss://serverIp:443/nvst/
  const { signalingUrl, signalingHost } = buildSignalingUrl(resourcePath, serverIp);

  // Use the resolved signaling host (which may differ from serverIp if extracted from rtsps:// URL)
  const effectiveHost = signalingHost ?? serverIp;
  const signalingServer = effectiveHost.includes(":")
    ? effectiveHost
    : `${effectiveHost}:443`;

  const rtspsHost =
    connections
      .map((connection) => typeof connection.resourcePath === "string"
        ? extractHostFromUrl(connection.resourcePath)
        : null)
      .find((host): host is string => Boolean(host)) ??
    signalingHost ??
    (isZoneHostname(serverIp) ? null : serverIp);

  return {
    serverIp,
    signalingServer,
    signalingUrl,
    mediaConnectionInfo: resolveMediaConnectionInfo(connections, serverIp, {
      logMissing: isReadySessionStatus(response.session.status),
    }),
    rtspsEndpoints: collectRtspsEndpoints(connections, rtspsHost),
  };
}

/**
 * Resolve the media connection endpoint (IP + port) from the session's connectionInfo array.
 * Matches Rust's media_connection_info() priority chain:
 *   1. usage=2 (Primary media path, UDP)
 *   2. usage=17 (Alternative media path)
 *   3. usage=14 with highest port (Alliance fallback — distinguishes media port from signaling port)
 *   4. Fallback: use serverIp with the highest port from any usage=14 entry
 *
 * For each entry, IP is extracted from:
 *   a. The .ip field directly
 *   b. The hostname in .resourcePath (e.g. rtsps://80-250-97-40.server.net:48322)
 *   c. Fallback to serverIp (only for usage=14 Alliance fallback)
 */
export function resolveMediaConnectionInfo(
  connections: Array<{ ip?: string; port: number; usage: number; protocol?: number; resourcePath?: string }>,
  serverIp: string,
  options?: { logMissing?: boolean },
): { ip: string; port: number; usage: number } | undefined {
  // Helper: extract IP from a connection entry
  const extractIp = (conn: { ip?: string; resourcePath?: string }): string | null => {
    // Try direct IP field
    const rawIp = conn.ip;
    const directIp = Array.isArray(rawIp) ? rawIp[0] : rawIp;
    if (directIp && directIp.length > 0) return directIp;

    // Try hostname from resourcePath
    if (conn.resourcePath) {
      const host = extractHostFromUrl(conn.resourcePath);
      if (host) return host;
    }

    return null;
  };

  // Helper: extract port from a connection entry (fallback to resourcePath URL port)
  const extractPort = (conn: { port: number; resourcePath?: string }): number => {
    if (conn.port > 0) return conn.port;

    // Try extracting port from resourcePath URL
    if (conn.resourcePath) {
      try {
        const url = new URL(conn.resourcePath.replace("rtsps://", "https://").replace("rtsp://", "http://"));
        const portStr = url.port;
        if (portStr) return parseInt(portStr, 10);
      } catch {
        // Ignore
      }
    }

    return 0;
  };

  // Priority 1: usage=2 (Primary media path, UDP)
  const primary = connections.find((c) => c.usage === 2);
  if (primary) {
    const ip = extractIp(primary);
    const port = extractPort(primary);
    console.log(`[CloudMatch] resolveMediaConnectionInfo: usage=2 candidate: ip=${ip}, port=${port}`);
    if (ip && port > 0) return { ip, port, usage: primary.usage };
  }

  // Priority 2: usage=17 (Alternative media path)
  const alt = connections.find((c) => c.usage === 17);
  if (alt) {
    const ip = extractIp(alt);
    const port = extractPort(alt);
    console.log(`[CloudMatch] resolveMediaConnectionInfo: usage=17 candidate: ip=${ip}, port=${port}`);
    if (ip && port > 0) return { ip, port, usage: alt.usage };
  }

  // Priority 3: usage=14 with highest port (Alliance fallback)
  const alliance = connections
    .filter((c) => c.usage === 14)
    .sort((a, b) => b.port - a.port);

  for (const conn of alliance) {
    const ip = extractIp(conn) ?? serverIp;
    const port = extractPort(conn);
    console.log(`[CloudMatch] resolveMediaConnectionInfo: usage=14 candidate: ip=${ip}, port=${port} (serverIp fallback=${serverIp})`);
    if (ip && port > 0) return { ip, port, usage: conn.usage };
  }

  if (options?.logMissing ?? true) {
    console.log("[CloudMatch] resolveMediaConnectionInfo: NO valid media connection info found");
  }
  return undefined;
}

/**
 * Build signaling WSS URL from the resourcePath, matching Rust implementation.
 * Returns the URL and optionally the extracted host (if different from serverIp).
 */
export function buildSignalingUrl(
  raw: string,
  serverIp: string,
): { signalingUrl: string; signalingHost: string | null } {
  if (raw.startsWith("rtsps://") || raw.startsWith("rtsp://")) {
    // Extract hostname from RTSP URL, convert to wss://
    const withoutScheme = raw.startsWith("rtsps://")
      ? raw.slice("rtsps://".length)
      : raw.slice("rtsp://".length);
    const host = withoutScheme.split(":")[0]?.split("/")[0];
    if (host && host.length > 0 && !host.startsWith(".")) {
      return {
        signalingUrl: `wss://${host}/nvst/`,
        signalingHost: host,
      };
    }
    return {
      signalingUrl: `wss://${serverIp}:443/nvst/`,
      signalingHost: null,
    };
  }

  if (raw.startsWith("wss://")) {
    // Already a full WSS URL, use as-is; extract host
    const withoutScheme = raw.slice("wss://".length);
    const host = withoutScheme.split("/")[0] ?? null;
    return { signalingUrl: raw, signalingHost: host };
  }

  if (raw.startsWith("/")) {
    // Relative path
    return {
      signalingUrl: `wss://${serverIp}:443${raw}`,
      signalingHost: null,
    };
  }

  // Fallback
  return {
    signalingUrl: `wss://${serverIp}:443/nvst/`,
    signalingHost: null,
  };
}
