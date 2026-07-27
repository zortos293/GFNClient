export interface DecoderPressureSample {
  framesReceived: number;
  framesDecoded: number;
  framesDropped: number;
  decodeTimeMs: number;
  decodeFps: number;
  prevSample: {
    framesReceived: number;
    framesDecoded: number;
    framesDropped: number;
  } | null;
}

export interface DecoderPressureSignal {
  active: boolean;
  reason: string;
  backlogFrames: number;
  dropRatePercent: number;
}

export type DecoderRecoveryAction =
  | "none"
  | "sender_keyframe"
  | "control_channel_keyframe"
  | "signaling_keyframe"
  | "bitrate_step_down";

export interface DecoderPressureState {
  active: boolean;
  recoveryAttempts: number;
  recoveryAction: DecoderRecoveryAction;
}

interface DecoderPressureControllerDependencies {
  log: (message: string) => void;
  getPeerConnection: () => RTCPeerConnection | null;
  getControlChannel: () => RTCDataChannel | null;
  requestSignalingKeyframe: (request: {
    reason: string;
    backlogFrames: number;
    attempt: number;
  }) => Promise<unknown>;
  setMaxBitrateKbps: (kbps: number) => Promise<void>;
  onStateChange: (state: DecoderPressureState) => void;
  now?: () => number;
}

const VIDEO_PRESSURE_JITTER_TARGET_MS = 30;
const AUDIO_PRESSURE_JITTER_TARGET_MS = 32;
const PRESSURE_CONSECUTIVE_POLLS = 3;
const STABLE_CONSECUTIVE_POLLS = 6;
const RECOVERY_COOLDOWN_MS = 1500;
const KEYFRAME_COOLDOWN_MS = 1200;
const BITRATE_STEP_FACTOR = 0.85;
export const DECODER_MIN_RECOVERY_BITRATE_KBPS = 4000;

export function classifyDecoderPressureSample(
  params: DecoderPressureSample,
): DecoderPressureSignal {
  const backlogFrames = Math.max(0, params.framesReceived - params.framesDecoded);
  const dropRatePercent = params.framesReceived > 0
    ? (params.framesDropped / params.framesReceived) * 100
    : 0;
  const severeStall = params.framesReceived > 120 && params.framesDecoded === 0;
  const backlogHigh = backlogFrames >= 45;
  const dropRateHigh = dropRatePercent >= 6;

  let dropBurst = false;
  if (params.prevSample) {
    const decodedDelta = params.framesDecoded - params.prevSample.framesDecoded;
    const droppedDelta = params.framesDropped - params.prevSample.framesDropped;
    dropBurst = droppedDelta >= 8 && decodedDelta <= 4;
  }

  let decodeSaturated = false;
  if (params.decodeFps > 0 && params.decodeTimeMs > 0) {
    const frameBudgetMs = 1000 / params.decodeFps;
    decodeSaturated = params.decodeTimeMs >= frameBudgetMs * 0.82;
  }

  if (severeStall) {
    return {
      active: true,
      reason: "severe_stall",
      backlogFrames,
      dropRatePercent,
    };
  }

  const active = (backlogHigh && (dropRateHigh || dropBurst || decodeSaturated))
    || (dropBurst && decodeSaturated);
  return {
    active,
    reason: active
      ? (backlogHigh ? "backlog_and_drop" : "decode_saturated")
      : "stable",
    backlogFrames,
    dropRatePercent,
  };
}

export class DecoderPressureController {
  private pressureActive = false;
  private pressureConsecutivePolls = 0;
  private stableConsecutivePolls = 0;
  private recoveryAttemptCount = 0;
  private lastRecoveryAtMs = 0;
  private lastKeyframeRequestAtMs = 0;
  private negotiatedMaxBitrateKbps = 0;
  private currentBitrateCeilingKbps = 0;
  private recoveryAction: DecoderRecoveryAction = "none";
  private readonly receiverLatencyTargets: Record<"video" | "audio", number | null> = {
    video: null,
    audio: null,
  };
  private activeReceivers: Array<{
    receiver: RTCRtpReceiver;
    kind: "audio" | "video";
  }> = [];

  constructor(private readonly dependencies: DecoderPressureControllerDependencies) {}

  get targetBitrateKbps(): number {
    return this.negotiatedMaxBitrateKbps;
  }

  initializeBitrate(maxBitrateKbps: number): void {
    this.negotiatedMaxBitrateKbps = Math.max(
      DECODER_MIN_RECOVERY_BITRATE_KBPS,
      Math.floor(maxBitrateKbps),
    );
    this.currentBitrateCeilingKbps = this.negotiatedMaxBitrateKbps;
  }

  classifySample(sample: DecoderPressureSample): DecoderPressureSignal {
    return classifyDecoderPressureSample(sample);
  }

  configureReceiver(receiver: RTCRtpReceiver, kind: string): void {
    if (kind !== "video" && kind !== "audio") {
      return;
    }
    if (!this.activeReceivers.some((entry) => entry.receiver === receiver)) {
      this.activeReceivers.push({ receiver, kind });
    }

    try {
      const targetMs = this.receiverLatencyTargets[kind];
      const rawReceiver = receiver as unknown as Record<string, unknown>;
      if ("jitterBufferTarget" in receiver) {
        rawReceiver.jitterBufferTarget = targetMs;
        this.dependencies.log(
          `${kind} receiver: jitterBufferTarget ${targetMs === null ? "adaptive" : `${targetMs}ms`}`,
        );
      }
      if ("playoutDelayHint" in receiver) {
        const playoutDelaySeconds = targetMs === null ? null : targetMs / 1000;
        rawReceiver.playoutDelayHint = playoutDelaySeconds;
        this.dependencies.log(
          `${kind} receiver: playoutDelayHint ${playoutDelaySeconds === null ? "adaptive" : `${playoutDelaySeconds}s`}`,
        );
      }
      if (kind === "video" && "contentHint" in receiver.track) {
        receiver.track.contentHint = "motion";
      }
    } catch (error) {
      this.dependencies.log(
        `Warning: could not apply ${kind} low-latency receiver tuning: ${String(error)}`,
      );
    }
  }

  reset(): void {
    this.pressureActive = false;
    this.pressureConsecutivePolls = 0;
    this.stableConsecutivePolls = 0;
    this.recoveryAttemptCount = 0;
    this.lastRecoveryAtMs = 0;
    this.lastKeyframeRequestAtMs = 0;
    this.negotiatedMaxBitrateKbps = 0;
    this.currentBitrateCeilingKbps = 0;
    this.recoveryAction = "none";
    this.receiverLatencyTargets.video = null;
    this.receiverLatencyTargets.audio = null;
    this.activeReceivers = [];
    this.emitState();
  }

  async recover(signal: DecoderPressureSignal): Promise<void> {
    if (!signal.active) {
      this.pressureConsecutivePolls = 0;
      this.stableConsecutivePolls++;
      if (this.stableConsecutivePolls >= STABLE_CONSECUTIVE_POLLS) {
        this.recoveryAttemptCount = 0;
        this.recoveryAction = "none";
        this.setPressureMode(false);
        this.emitState();
      }
      return;
    }

    this.stableConsecutivePolls = 0;
    this.pressureConsecutivePolls++;
    if (this.pressureConsecutivePolls < PRESSURE_CONSECUTIVE_POLLS) {
      return;
    }

    this.setPressureMode(true);
    const now = this.dependencies.now?.() ?? performance.now();
    if (now - this.lastRecoveryAtMs < RECOVERY_COOLDOWN_MS) {
      return;
    }

    const keyframeRequested = await this.requestKeyframe(
      signal.backlogFrames,
      signal.reason,
    );
    let bitrateReduced = false;
    if (!keyframeRequested || this.recoveryAttemptCount >= 1) {
      bitrateReduced = await this.reduceBitrate();
    }

    if (keyframeRequested || bitrateReduced) {
      this.recoveryAttemptCount++;
      this.lastRecoveryAtMs = now;
      this.emitState();
    }
  }

  private emitState(): void {
    this.dependencies.onStateChange({
      active: this.pressureActive,
      recoveryAttempts: this.recoveryAttemptCount,
      recoveryAction: this.recoveryAction,
    });
  }

  private setPressureMode(active: boolean): void {
    if (this.pressureActive === active) {
      return;
    }
    this.pressureActive = active;
    this.receiverLatencyTargets.video = active
      ? VIDEO_PRESSURE_JITTER_TARGET_MS
      : null;
    this.receiverLatencyTargets.audio = active
      ? AUDIO_PRESSURE_JITTER_TARGET_MS
      : null;
    this.dependencies.log(
      `Decoder pressure mode ${active ? "enabled" : "cleared"}; receiver targets video=${this.receiverLatencyTargets.video ?? "adaptive"} audio=${this.receiverLatencyTargets.audio ?? "adaptive"}`,
    );
    for (const { receiver, kind } of this.activeReceivers) {
      this.configureReceiver(receiver, kind);
    }
    this.emitState();
  }

  private async requestKeyframe(
    backlogFrames: number,
    reason: string,
  ): Promise<boolean> {
    const now = this.dependencies.now?.() ?? performance.now();
    if (now - this.lastKeyframeRequestAtMs < KEYFRAME_COOLDOWN_MS) {
      return false;
    }

    let requested = false;
    const pc = this.dependencies.getPeerConnection();
    if (pc) {
      for (const sender of pc.getSenders()) {
        if (sender.track?.kind !== "video") {
          continue;
        }
        const senderWithKeyframe = sender as RTCRtpSender & {
          requestKeyFrame?: () => Promise<void>;
        };
        if (typeof senderWithKeyframe.requestKeyFrame !== "function") {
          continue;
        }
        try {
          await senderWithKeyframe.requestKeyFrame();
          requested = true;
        } catch (error) {
          this.dependencies.log(
            `requestKeyFrame failed on sender (non-fatal): ${String(error)}`,
          );
        }
      }
    }

    const attempt = this.recoveryAttemptCount + 1;
    const controlChannel = this.dependencies.getControlChannel();
    if (!requested && controlChannel?.readyState === "open") {
      try {
        controlChannel.send(JSON.stringify({
          type: "request_keyframe",
          reason,
          backlogFrames,
          attempt,
        }));
        requested = true;
        this.recoveryAction = "control_channel_keyframe";
      } catch (error) {
        this.dependencies.log(
          `control_channel keyframe request failed (non-fatal): ${String(error)}`,
        );
      }
    }

    if (!requested) {
      try {
        await this.dependencies.requestSignalingKeyframe({
          reason,
          backlogFrames,
          attempt,
        });
        requested = true;
        this.recoveryAction = "signaling_keyframe";
      } catch (error) {
        this.dependencies.log(
          `signaling keyframe request failed (non-fatal): ${String(error)}`,
        );
      }
    }

    if (!requested) {
      return false;
    }
    this.lastKeyframeRequestAtMs = now;
    if (this.recoveryAction === "none") {
      this.recoveryAction = "sender_keyframe";
    }
    this.dependencies.log(
      `Decoder recovery: keyframe requested (reason=${reason}, backlog=${backlogFrames}, attempt=${attempt})`,
    );
    return true;
  }

  private async reduceBitrate(): Promise<boolean> {
    const pc = this.dependencies.getPeerConnection();
    if (!pc?.localDescription) {
      return false;
    }
    const current = this.currentBitrateCeilingKbps > 0
      ? this.currentBitrateCeilingKbps
      : this.negotiatedMaxBitrateKbps;
    if (current <= DECODER_MIN_RECOVERY_BITRATE_KBPS) {
      return false;
    }
    const next = Math.max(
      DECODER_MIN_RECOVERY_BITRATE_KBPS,
      Math.floor(current * BITRATE_STEP_FACTOR),
    );
    if (next >= current) {
      return false;
    }
    await this.dependencies.setMaxBitrateKbps(next);
    this.currentBitrateCeilingKbps = next;
    this.recoveryAction = "bitrate_step_down";
    this.dependencies.log(
      `Decoder recovery: bitrate ceiling stepped down ${current} -> ${next} kbps`,
    );
    return true;
  }
}
