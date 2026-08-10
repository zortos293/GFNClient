export type SenderBitrateUpdateResult =
  | { status: "updated" }
  | { status: "unavailable" }
  | { status: "unsupported"; error: unknown };

function findVideoSender(pc: RTCPeerConnection): RTCRtpSender | null {
  return pc.getSenders().find((sender) => sender.track?.kind === "video") ?? null;
}

export async function updateVideoSenderBitrate(
  pc: RTCPeerConnection,
  maxBitrateKbps: number,
): Promise<SenderBitrateUpdateResult> {
  const sender = findVideoSender(pc);
  if (!sender) {
    return { status: "unavailable" };
  }

  try {
    const parameters = sender.getParameters();
    if (parameters.encodings.length === 0) {
      parameters.encodings = [{}];
    }
    parameters.encodings[0].maxBitrate = maxBitrateKbps * 1000;
    await sender.setParameters(parameters);
    return { status: "updated" };
  } catch (error) {
    return { status: "unsupported", error };
  }
}
