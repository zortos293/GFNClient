import type { VideoCodec } from "@shared/gfn";
import { extractNegotiatedVideoCodec } from "@shared/gfn/sdpValidation";

export interface CodecAnswerAttempt<T> {
  answer: T;
  sdp?: string | null;
}

export interface NegotiatedCodecAnswer<T> {
  answer: T;
  attemptedCodec: VideoCodec;
  negotiatedCodec: VideoCodec;
  attemptCount: number;
}

export interface CodecNegotiationPeerConnection {
  readonly signalingState: RTCSignalingState;
  setRemoteDescription(description: RTCSessionDescriptionInit): Promise<void>;
  createAnswer(): Promise<RTCSessionDescriptionInit>;
}

function currentSignalingState(pc: CodecNegotiationPeerConnection): RTCSignalingState {
  return pc.signalingState;
}

export async function negotiateCodecAnswer<T>(
  candidates: readonly VideoCodec[],
  attempt: (codec: VideoCodec, attemptIndex: number) => Promise<CodecAnswerAttempt<T>>,
): Promise<NegotiatedCodecAnswer<T>> {
  for (let index = 0; index < candidates.length; index += 1) {
    const attemptedCodec = candidates[index];
    if (!attemptedCodec) {
      continue;
    }
    const result = await attempt(attemptedCodec, index);
    const negotiatedCodec = result.sdp
      ? extractNegotiatedVideoCodec(result.sdp)
      : null;
    if (negotiatedCodec) {
      return {
        answer: result.answer,
        attemptedCodec,
        negotiatedCodec,
        attemptCount: index + 1,
      };
    }
  }

  throw new Error(
    `No video codec negotiated in local SDP answer after ${candidates.length} attempt(s): ${candidates.join(", ") || "none"}`,
  );
}

export async function negotiatePeerConnectionCodecAnswer(
  pc: CodecNegotiationPeerConnection,
  candidates: readonly VideoCodec[],
  buildOffer: (codec: VideoCodec) => string,
  beforeCreateAnswer?: (codec: VideoCodec, attemptIndex: number) => Promise<void> | void,
): Promise<NegotiatedCodecAnswer<RTCSessionDescriptionInit>> {
  return negotiateCodecAnswer(candidates, async (codec, attemptIndex) => {
    if (attemptIndex > 0) {
      if (pc.signalingState !== "have-remote-offer") {
        throw new Error(
          `Cannot retry codec ${codec}: expected have-remote-offer before rollback, got ${pc.signalingState}`,
        );
      }
      try {
        await pc.setRemoteDescription({ type: "rollback" });
      } catch (cause) {
        throw new Error(`Cannot retry codec ${codec}: remote-offer rollback failed`, { cause });
      }
      const stateAfterRollback = currentSignalingState(pc);
      if (stateAfterRollback !== "stable") {
        throw new Error(
          `Cannot retry codec ${codec}: remote-offer rollback did not restore stable signaling state (got ${stateAfterRollback})`,
        );
      }
    } else if (pc.signalingState !== "stable") {
      throw new Error(
        `Cannot start codec negotiation for ${codec}: expected stable signaling state, got ${pc.signalingState}`,
      );
    }

    await pc.setRemoteDescription({ type: "offer", sdp: buildOffer(codec) });
    await beforeCreateAnswer?.(codec, attemptIndex);
    const answer = await pc.createAnswer();
    return { answer, sdp: answer.sdp };
  });
}
