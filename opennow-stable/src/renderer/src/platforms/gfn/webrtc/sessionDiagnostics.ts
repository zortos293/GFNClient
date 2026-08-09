import type { SessionInfo } from "@shared/gfn";

export interface StreamSessionDiagnostics {
  sessionId: string;
  serverRegion: string;
  serverZone: string;
  serverLocation: string;
  serverGpuType: string;
}

function normalizeServerRegion(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }

  try {
    const url = new URL(trimmed.includes("://") ? trimmed : `https://${trimmed}`);
    return url.hostname || trimmed;
  } catch {
    return trimmed;
  }
}

export function deriveStreamSessionDiagnostics(
  session: Pick<
    SessionInfo,
    | "sessionId"
    | "zone"
    | "serverLocation"
    | "signalingServer"
    | "streamingBaseUrl"
    | "serverIp"
    | "gpuType"
  >,
): StreamSessionDiagnostics {
  const serverLocation = session.serverLocation?.trim() ?? "";
  return {
    sessionId: session.sessionId,
    serverZone: session.zone,
    serverLocation,
    serverGpuType: session.gpuType ?? "",
    serverRegion: normalizeServerRegion(
      serverLocation ||
      session.signalingServer ||
      session.streamingBaseUrl ||
      session.serverIp,
    ),
  };
}
