import type { ColorQuality, VideoCodec } from "@shared/gfn";
import {
  PARTIALLY_RELIABLE_GAMEPAD_MASK_ALL,
  PARTIALLY_RELIABLE_HID_DEVICE_MASK_ALL,
} from "../inputProtocol";

// Match the official web client's 240 FPS profile. Disabling split encode at
// this frame rate can leave H265 streams smeared because the server/client
// repair and frame-state assumptions no longer line up.
const ENABLE_240_FPS_SPLIT_ENCODE = true;
const ENABLE_DYNAMIC_SPLIT_ENCODE_UPDATES = true;
const OFFICIAL_MIN_BITRATE_KBPS = 4000;
const HIGH_RESOLUTION_PIXEL_COUNT = 2764800; // 2560x1080 / 1920x1440 class
const HIGH_BITRATE_PACING_THRESHOLD_KBPS = 42000;

interface IceCredentials {
  ufrag: string;
  pwd: string;
  fingerprint: string;
}

interface NvstParams {
  width: number;
  height: number;
  fps: number;
  maxBitrateKbps: number;
  partialReliableThresholdMs: number;
  codec: VideoCodec;
  colorQuality: ColorQuality;
  credentials: IceCredentials;
  hidDeviceMask?: number;
  enablePartiallyReliableTransferGamepad?: number;
  enablePartiallyReliableTransferHid?: number;
  dynamicSplitEncodeUpdatesEnabled?: boolean;
}

export function buildNvstSdp(params: NvstParams): string {
  console.log(`[SDP] buildNvstSdp: ${params.width}x${params.height}@${params.fps}fps, codec=${params.codec}, colorQuality=${params.colorQuality}, maxBitrate=${params.maxBitrateKbps}kbps`);
  console.log(`[SDP] buildNvstSdp: ICE ufrag=${params.credentials.ufrag}, pwd=${params.credentials.pwd.slice(0, 8)}..., fingerprint=${params.credentials.fingerprint.slice(0, 20)}...`);
  const maxBitrate = Math.max(OFFICIAL_MIN_BITRATE_KBPS, Math.floor(params.maxBitrateKbps));
  const startupBitrate = Math.max(OFFICIAL_MIN_BITRATE_KBPS, Math.round(maxBitrate / 4));
  const isHighFps = params.fps >= 90;
  const is90Fps = params.fps === 90;
  const is120Fps = params.fps === 120;
  const is240Fps = params.fps >= 240;
  const isAv1 = params.codec === "AV1";
  const pixelCount = params.width * params.height;
  const useHighThroughputPacing =
    pixelCount >= HIGH_RESOLUTION_PIXEL_COUNT || maxBitrate >= HIGH_BITRATE_PACING_THRESHOLD_KBPS;
  const supportsHighBitDepth = params.codec === "H265" || params.codec === "AV1";
  const bitDepth = supportsHighBitDepth && params.colorQuality.startsWith("10bit") ? 10 : 8;
  const hidDeviceMask = params.hidDeviceMask ?? PARTIALLY_RELIABLE_HID_DEVICE_MASK_ALL;
  const enablePartiallyReliableTransferGamepad = params.enablePartiallyReliableTransferGamepad
    ?? PARTIALLY_RELIABLE_GAMEPAD_MASK_ALL;
  const enablePartiallyReliableTransferHid = params.enablePartiallyReliableTransferHid ?? hidDeviceMask;
  const dynamicSplitEncodeUpdatesEnabled = params.dynamicSplitEncodeUpdatesEnabled ?? ENABLE_DYNAMIC_SPLIT_ENCODE_UPDATES;

  const lines: string[] = [
    "v=0",
    "o=SdpTest test_id_13 14 IN IPv4 127.0.0.1",
    "s=-",
    "t=0 0",
    `a=general.icePassword:${params.credentials.pwd}`,
    `a=general.iceUserNameFragment:${params.credentials.ufrag}`,
    `a=general.dtlsFingerprint:${params.credentials.fingerprint}`,
    "m=video 0 RTP/AVP",
    "a=msid:fbc-video-0",
    // Match the stable Android-native recovery profile. Large FEC/NACK bursts
    // amplify congestion after packet loss instead of letting BWE recover.
    "a=vqos.fec.rateDropWindow:10",
    "a=vqos.fec.minRequiredFecPackets:2",
    "a=vqos.drc.minRequiredBitrateCheckEnabled:1",
    "a=vqos.fec.repairMinPercent:5",
    "a=vqos.fec.repairPercent:5",
    "a=vqos.fec.repairMaxPercent:35",
    // Official dynamicStreamingMode=0 path disables server resolution/FPS switching.
    "a=vqos.dynamicStreamingMode:0",
    "a=vqos.drc.enable:0",
    "a=vqos.calculateAvgVideoStreamingBitrate:1",
  ];

  if (isHighFps) {
    lines.push(
      "a=vqos.dfc.enable:1",
      "a=vqos.dfc.decodeFpsAdjPercent:85",
      "a=vqos.dfc.targetDownCooldownMs:250",
      `a=vqos.dfc.dfcAlgoVersion:${is120Fps || is240Fps ? 2 : 1}`,
      `a=vqos.dfc.minTargetFps:${is120Fps || is240Fps ? 100 : 60}`,
      "a=vqos.resControl.dfc.useClientFpsPerf:0",
      "a=vqos.dfc.adjustResAndFps:0",
    );
  } else {
    lines.push(
      "a=vqos.dfc.enable:0",
      "a=vqos.dfc.adjustResAndFps:0",
    );
  }

  // Frame pacing target: ~1/fps with a small headroom (official 240 FPS DESCRIBE used 7936 µs).
  const minTargetFrameTimeUs = Math.max(
    1000,
    Math.floor((1_000_000 * 95) / (Math.max(1, params.fps) * 100)),
  );

  // Video encoder settings
  lines.push(
    "a=video.dx9EnableNv12:1",
    "a=video.dx9EnableHdr:1",
    "a=vqos.qpg.enable:1",
    "a=vqos.resControl.qp.qpg.featureSetting:7",
    // Official DESCRIBE adaptive quantization block (HEVC/AV1 sessions).
    "a=video.adaptiveQuantization.spatialAQSetting:7",
    "a=video.adaptiveQuantization.temporalAQSetting:0",
    "a=video.adaptiveQuantization.spatialAQStrength:12",
    "a=video.adaptiveQuantization.qpThresholdAdjPercent:2",
    "a=video.adaptiveQuantization.saqAdaptMinQpThresholdPercent:40",
    "a=video.adaptiveQuantization.saqAdaptMaxQpThresholdPercent:100",
    "a=video.adaptiveQuantization.saqAdaptDecayStrengthX100:250",
    "a=video.adaptiveQuantization.perfAdjEnablement:1",
    "a=video.framePacing.mode:2",
    `a=video.framePacing.pid.minTargetFrameTimeUs:${minTargetFrameTimeUs}`,
    "a=bwe.useOwdCongestionControl:1",
    "a=video.enableRtpNack:1",
    "a=vqos.bw.txRxLag.minFeedbackTxDeltaMs:200",
    "a=vqos.drc.bitrateIirFilterFactor:18",
    "a=video.packetSize:1140",
    // Official packet pacing profile (Nvsc dumps + DESCRIBE enableAccurateSleep).
    "a=packetPacing.version:3",
    "a=packetPacing.mode:1",
    "a=packetPacing.minNumPacketsPerGroup:15",
    "a=packetPacing.enableAccurateSleep:1",
    "a=packetPacing.enableSmoothTransition:1",
    "a=packetPacing.allowFpsBasedToggle:1",
    // Bitrate headroom / QP delta — present on official DESCRIBE ANNOUNCE path.
    "a=vqos.relaxMaxBitrate.overrideAvgBitrateThresholdPercent:4",
    "a=vqos.relaxMaxBitrate.customAvgBitrateThresholdPercent:65",
    "a=vqos.relaxMaxBitrate.overrideAvgQpThresholdPercent:7",
    "a=vqos.relaxMaxBitrate.customAvgQpThresholdPercent:51",
    "a=vqos.relaxMaxBitrate.iirFilterFactor:120",
    "a=vqos.qpDelta.qpDeltaMaxPercent:10",
    "a=vqos.qpDelta.qpDeltaSurfaceAdjustmentStrengthPercent:70",
    "a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentH264:100",
    "a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentH265:100",
    "a=vqos.qpDelta.qpDeltaVbvUsageFactorPercentAv1:100",
    "a=vqos.qpDelta.qpDeltaMinPercent:60",
    "a=vqos.qpDelta.qpDeltaIirFactor:60",
    "a=vqos.qpDelta.qpDeltaThrottlePercent:100",
  );

  // High FPS optimizations
  if (isHighFps) {
    lines.push(
      "a=bwe.iirFilterFactor:8",
      "a=video.encoderFeatureSetting:47",
      "a=video.encoderPreset:6",
      "a=vqos.resControl.cpmRtc.badNwSkipFramesCount:600",
      `a=vqos.resControl.cpmRtc.decodeTimeThresholdMs:${is90Fps ? 11 : 9}`,
      `a=video.fbcDynamicFpsGrabTimeoutMs:${is90Fps ? 9 : is120Fps ? 6 : 18}`,
      `a=vqos.resControl.cpmRtc.serverResolutionUpdateCoolDownCount:${is120Fps ? 6000 : 12000}`,
    );
  }

  // 240+ FPS optimizations
  if (is240Fps) {
    lines.push(
      "a=video.enableNextCaptureMode:1",
      "a=vqos.maxStreamFpsEstimate:240",
    );
    if (ENABLE_240_FPS_SPLIT_ENCODE) {
      // Official 240 FPS DESCRIBE uses 63 strips (not the older web-client value of 3).
      lines.push(
        "a=video.videoSplitEncodeStripsPerFrame:63",
        `a=video.updateSplitEncodeStateDynamically:${dynamicSplitEncodeUpdatesEnabled ? 1 : 0}`,
        "a=vqos.rtcPreemptiveIdrSettings.minBurstNackSize:65535",
        "a=vqos.rtcPreemptiveIdrSettings.minNackPacketCaptureAgeMs:65535",
      );
    }
  }

  // Out-of-focus handling + disable CPM-based resolution changes
  lines.push(
    "a=vqos.adjustStreamingFpsDuringOutOfFocus:1",
    "a=vqos.resControl.cpmRtc.ignoreOutOfFocusWindowState:1",
    "a=vqos.resControl.perfHistory.rtcIgnoreOutOfFocusWindowState:1",
    // Disable CPM-based resolution changes (prevents SSRC switches)
    "a=vqos.resControl.cpmRtc.featureMask:0",
    "a=vqos.resControl.cpmRtc.enable:0",
    // Never scale down resolution
    "a=vqos.resControl.cpmRtc.minResolutionPercent:100",
    // Infinite cooldown to prevent resolution changes
    "a=vqos.resControl.cpmRtc.resolutionChangeHoldonMs:999999",
  );

  // Packet pacing group/delay + NACK queues (official Nvsc defaults).
  lines.push(
    `a=packetPacing.numGroups:${is120Fps ? 3 : 5}`,
    "a=packetPacing.maxDelayUs:1000",
    "a=packetPacing.minNumPacketsFrame:10",
    "a=video.rtpNackQueueLength:1024",
    "a=video.rtpNackQueueMaxPackets:512",
    "a=video.rtpNackMaxPacketCount:25",
  );

  if (useHighThroughputPacing) {
    lines.push(
      // Resolution/quality thresholds — match the browser client's high-throughput profile.
      "a=vqos.drc.iirFilterFactor:100",
    );

    if (!isAv1) {
      lines.push(
        "a=vqos.drc.qpMaxResThresholdAdj:4",
        "a=vqos.dfc.qpMaxResThresholdAdj:4",
        "a=vqos.grc.qpMaxResThresholdAdj:2",
      );
    }
  }

  // AV1-specific DRC/GRC tuning (mirrors official client intent):
  // bias towards QP adaptation before resolution downgrade.
  if (isAv1) {
    const av1QpMaxResThresholdAdj = useHighThroughputPacing ? 20 : 0;
    lines.push(
      "a=vqos.drc.minQpHeadroom:20",
      "a=vqos.drc.lowerQpThreshold:100",
      "a=vqos.drc.upperQpThreshold:200",
      "a=vqos.drc.minAdaptiveQpThreshold:180",
      `a=vqos.drc.qpMaxResThresholdAdj:${av1QpMaxResThresholdAdj}`,
      "a=vqos.drc.qpCodecThresholdAdj:0",
      // mirror to DFC/GRC
      "a=vqos.dfc.minQpHeadroom:20",
      "a=vqos.dfc.qpLowerLimit:100",
      "a=vqos.dfc.qpMaxUpperLimit:200",
      "a=vqos.dfc.qpMinUpperLimit:180",
      `a=vqos.dfc.qpMaxResThresholdAdj:${av1QpMaxResThresholdAdj}`,
      "a=vqos.dfc.qpCodecThresholdAdj:0",
      "a=vqos.grc.minQpHeadroom:20",
      "a=vqos.grc.lowerQpThreshold:100",
      "a=vqos.grc.upperQpThreshold:200",
      "a=vqos.grc.minAdaptiveQpThreshold:180",
      `a=vqos.grc.qpMaxResThresholdAdj:${av1QpMaxResThresholdAdj}`,
      "a=vqos.grc.qpCodecThresholdAdj:0",
      "a=video.minQp:25",
      // official client can enable this for AV1 depending on resolution class
      "a=video.enableAv1RcPrecisionFactor:1",
    );
  }

  // Viewport, FPS, and bitrate
  lines.push(
    `a=video.clientViewportWd:${params.width}`,
    `a=video.clientViewportHt:${params.height}`,
    `a=video.maxFPS:${params.fps}`,
    `a=video.initialBitrateKbps:${startupBitrate}`,
    `a=video.initialPeakBitrateKbps:${startupBitrate}`,
    `a=vqos.bw.maximumBitrateKbps:${maxBitrate}`,
    `a=vqos.bw.minimumBitrateKbps:${OFFICIAL_MIN_BITRATE_KBPS}`,
    `a=vqos.bw.peakBitrateKbps:${maxBitrate}`,
    `a=vqos.bw.serverPeakBitrateKbps:${maxBitrate}`,
    "a=vqos.bw.enableBandwidthEstimation:1",
    "a=vqos.bw.disableBitrateLimit:0",
    // GRC — disabled
    `a=vqos.grc.maximumBitrateKbps:${maxBitrate}`,
    "a=vqos.grc.enable:0",
    // Encoder settings
    "a=video.maxNumReferenceFrames:4",
    "a=video.mapRtpTimestampsToFrames:1",
    "a=video.encoderCscMode:3",
    "a=video.dynamicRangeMode:0",
    `a=video.bitDepth:${bitDepth}`,
    // Disable server-side scaling and prefilter (prevents resolution downgrade)
    `a=video.scalingFeature1:${isAv1 ? 1 : 0}`,
    "a=video.prefilterParams.prefilterModel:0",
    // Audio track (receive-only from server)
    "m=audio 0 RTP/AVP",
    "a=msid:audio",
    // Official aqos redundancy + dynamic audio config (DESCRIBE / Nvsc dumps).
    "a=aqos.enableRedundancy:1",
    "a=aqos.redundancyLevel:2",
    "a=aqos.enableRedundancyForMic:1",
    "a=aqos.redundancyLevelForMic:3",
    "a=audio.enableDynamicAudioConfig:1",
    "a=audio.enableTimestampAudioBuffer:1",
    // Mic track (send to server)
    "m=mic 0 RTP/AVP",
    "a=msid:mic",
    "a=rtpmap:0 PCMU/8000",
    // Input/application track
    "m=application 0 RTP/AVP",
    "a=msid:input_1",
    `a=ri.partialReliableThresholdMs:${params.partialReliableThresholdMs}`,
    `a=ri.hidDeviceMask:${hidDeviceMask}`,
    `a=ri.enablePartiallyReliableTransferGamepad:${enablePartiallyReliableTransferGamepad}`,
    `a=ri.enablePartiallyReliableTransferHid:${enablePartiallyReliableTransferHid}`,
    "a=ri.timestampsEnabled:1",
    "a=ri.useMultipleGamepads:1",
    "",
  );

  return lines.join("\n");
}
