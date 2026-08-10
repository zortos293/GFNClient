export type IceTransportType = "udp" | "tcp" | "unknown";

export interface ActiveIceTransportStats {
  activePair: Record<string, unknown> | null;
  transportType: IceTransportType;
  localCandidateType: string;
}

function protocolFrom(value: unknown): Exclude<IceTransportType, "unknown"> | null {
  const normalized = typeof value === "string" ? value.toLowerCase() : "";
  return normalized === "udp" || normalized === "tcp" ? normalized : null;
}

export function extractActiveIceTransportStats(
  entries: Iterable<Record<string, unknown>>,
): ActiveIceTransportStats {
  const candidatePairs = new Map<string, Record<string, unknown>>();
  const localCandidates = new Map<string, Record<string, unknown>>();
  const transports = new Map<string, Record<string, unknown>>();
  let nominatedPair: Record<string, unknown> | null = null;

  for (const stats of entries) {
    const id = typeof stats.id === "string" ? stats.id : "";
    if (stats.type === "candidate-pair") {
      if (id) {
        candidatePairs.set(id, stats);
      }
      if (stats.state === "succeeded" && stats.nominated === true) {
        nominatedPair = stats;
      }
    } else if (stats.type === "local-candidate" && id) {
      localCandidates.set(id, stats);
    } else if (stats.type === "transport" && id) {
      transports.set(id, stats);
    }
  }

  let activePair: Record<string, unknown> | null = null;
  let selectedTransport: Record<string, unknown> | undefined;
  for (const transport of transports.values()) {
    const selectedPairId = typeof transport.selectedCandidatePairId === "string"
      ? transport.selectedCandidatePairId
      : "";
    const selectedPair = selectedPairId ? candidatePairs.get(selectedPairId) : undefined;
    if (selectedPair) {
      activePair = selectedPair;
      selectedTransport = transport;
      break;
    }
  }

  activePair ??= nominatedPair;
  if (!activePair) {
    return {
      activePair: null,
      transportType: "unknown",
      localCandidateType: "",
    };
  }

  if (!selectedTransport) {
    const transportId = typeof activePair.transportId === "string" ? activePair.transportId : "";
    selectedTransport = transportId ? transports.get(transportId) : undefined;
  }

  const localCandidateId = typeof activePair.localCandidateId === "string"
    ? activePair.localCandidateId
    : "";
  const localCandidate = localCandidateId ? localCandidates.get(localCandidateId) : undefined;
  const transportType =
    protocolFrom(selectedTransport?.protocol) ??
    protocolFrom(activePair.protocol) ??
    protocolFrom(localCandidate?.protocol) ??
    "unknown";
  const localCandidateType = typeof localCandidate?.candidateType === "string"
    ? localCandidate.candidateType.toLowerCase()
    : "";

  return {
    activePair,
    transportType,
    localCandidateType,
  };
}
