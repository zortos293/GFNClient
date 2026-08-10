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
export const OFFICIAL_MIN_BITRATE_KBPS = 4000;
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

// This builder targets the WEB (Chromium WebRTC) transport and is aligned
// byte-for-byte with the official play.geforcenow.com SDP where it matters.
// The NATIVE streamer (native/opennow-streamer/src/sdp.rs) intentionally keeps
// its own (native-client) attribute set — do NOT sync this file's removals
// into sdp.rs; native mode works with its current profile.
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
    // Official web client defaults to dynamicStreamingMode=3 (full dynamic
    // streaming: DRC + DFC + bitrate envelope). The fork previously locked 0
    // to prevent mid-session SSRC switches, but the renderer now handles track
    // replacement (peerMediaLifecycleController), so align with the official
    // default and let the server's BWE drive the bitrate like the web app.
    "a=vqos.dynamicStreamingMode:3",
    // Official web client always sends vqos.bllFec.enable:0 (backward-lossless
    // FEC off). drc.enable / dfc.enable are NOT emitted for 60 FPS sessions —
    // the official DFC/DRC helper only writes them for high-FPS (see below).
    // vqos.calculateAvgVideoStreamingBitrate was a fork-only extra (zero hits
    // in the play.geforcenow.com bundle) and is dropped for parity.
    "a=vqos.bllFec.enable:0",
  ];

  if (isHighFps) {
    lines.push(
      // Official web client, dynamicStreamingMode=3 + high FPS: drc.enable:0,
      // dfc.enable:1, decodeFpsAdjPercent:85, targetDownCooldownMs:250,
      // dfcAlgoVersion 2 (120/240) / 1 (90), minTargetFps 100 (120/240) / 60
      // (90), resControl.dfc.useClientFpsPerf:0, and dfc.adjustResAndFps:1
      // (the mode-3 case sets it for high-FPS sessions).
      "a=vqos.drc.enable:0",
      "a=vqos.dfc.enable:1",
      "a=vqos.dfc.decodeFpsAdjPercent:85",
      "a=vqos.dfc.targetDownCooldownMs:250",
      `a=vqos.dfc.dfcAlgoVersion:${is120Fps || is240Fps ? 2 : 1}`,
      `a=vqos.dfc.minTargetFps:${is90Fps ? 60 : 100}`,
      "a=vqos.resControl.dfc.useClientFpsPerf:0",
      "a=vqos.dfc.adjustResAndFps:1",
    );
  } else {
    // Official web client, 60 FPS + dynamicStreamingMode=3: only drc.enable:1
    // is emitted (the mode-3 case enables DRC for non-high-FPS sessions).
    lines.push(
      "a=vqos.drc.enable:1",
    );
  }

  // Video encoder settings
  lines.push(
    "a=video.dx9EnableNv12:1",
    "a=video.dx9EnableHdr:1",
    "a=vqos.qpg.enable:1",
    "a=vqos.resControl.qp.qpg.featureSetting:7",
    // NOTE: the official web client does NOT send video.framePacing.* or
    // video.adaptiveQuantization.* — those fork additions came from native
    // client dumps. Dropped to match play.geforcenow.com byte-for-byte.
    "a=bwe.useOwdCongestionControl:1",
    "a=video.enableRtpNack:1",
    "a=vqos.bw.txRxLag.minFeedbackTxDeltaMs:200",
    "a=vqos.drc.bitrateIirFilterFactor:18",
    "a=video.packetSize:1140",
    // The official web client only sends packetPacing.minNumPacketsPerGroup
    // here (version/mode/enableAccurateSleep/etc. are native-client extras,
    // and vqos.relaxMaxBitrate.* / vqos.qpDelta.* detail attrs are not sent
    // by play.geforcenow.com at all — dropped to match it byte-for-byte).
    "a=packetPacing.minNumPacketsPerGroup:15",
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
      // Official client sends the 120 FPS encoder-rate hint for 120/240 sessions.
      ...(is120Fps || is240Fps ? ["a=video.fakeEncodeFps:120"] : []),
    );
  }

  // 240+ FPS optimizations
  // NOTE: vqos.rtcPreemptiveIdrSettings.* (older web-client extras) are not
  // sent — the official play.geforcenow.com bundle doesn't include them.
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
      );
    }
  }

  // Out-of-focus handling + CPM resolution control (official web client).
  // The official bundle emits cpmRtc.featureMask:3 when the CPM path is
  // enabled (web default) and never sends cpmRtc.enable / minResolutionPercent
  // / resolutionChangeHoldonMs — those fork-only locks disabled the server's
  // CPM resolution control, which fights dynamicStreamingMode:3 and pins the
  // BWE to the 4000 kbps floor (~5 Mbps) instead of ramping to the ceiling.
  lines.push(
    "a=vqos.adjustStreamingFpsDuringOutOfFocus:1",
    "a=vqos.resControl.cpmRtc.ignoreOutOfFocusWindowState:1",
    "a=vqos.resControl.perfHistory.rtcIgnoreOutOfFocusWindowState:1",
    "a=vqos.resControl.cpmRtc.featureMask:3",
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
    // Official web client sends the session FPS from settings (previously the
    // fork forced maxFPS:120 — reverted as part of full alignment with the
    // official bundle dump; revert this line again if GFN ignores low FPS).
    `a=video.maxFPS:${params.fps}`,
    // Bitrate attributes mirror the official GFN web client exactly (verified
    // against a dump of play.geforcenow.com's vendor bundle): initial =
    // initialPeak = max(4000, max/4), minimum fixed at 4000. The official
    // client does NOT send enableBandwidthEstimation / disableBitrateLimit /
    // peakBitrateKbps / serverPeakBitrateKbps / grc.maximumBitrateKbps — those
    // fork additions made the server enable its own throttling BWE, which read
    // Chromium WebRTC feedback and sat around the 4000 kbps floor (~5 Mbps at
    // a 35 Mbps cap) instead of ramping to the ceiling.
    `a=video.initialBitrateKbps:${startupBitrate}`,
    `a=video.initialPeakBitrateKbps:${startupBitrate}`,
    `a=vqos.bw.maximumBitrateKbps:${maxBitrate}`,
    `a=vqos.bw.minimumBitrateKbps:${OFFICIAL_MIN_BITRATE_KBPS}`,
    // Encoder settings — encoderCscMode is always 3 on desktop web (the official
    // bundle computes it as `TIZEN ? 2 : 3`; the fork's old 4 for 10-bit was a
    // fork-only value the server does not expect). encoderHdrCscMode:4 and
    // dynamicRangeMode mirror the official client for HDR content.
    "a=video.maxNumReferenceFrames:4",
    "a=video.mapRtpTimestampsToFrames:1",
    "a=video.encoderCscMode:3",
    "a=video.encoderHdrCscMode:4",
    `a=video.dynamicRangeMode:${bitDepth === 10 ? 1 : 0}`,
    `a=video.bitDepth:${bitDepth}`,
    // Official web client sets video.minQp:14 only for 10-bit H265 (verified
    // against the vendor bundle: `10===To && "H265"===tn`); 8-bit H265 sends
    // no minQp. AV1 pins minQp:25 in its block above.
    ...(params.codec === "H265" && bitDepth === 10 ? ["a=video.minQp:14"] : []),
    // Disable server-side scaling and prefilter (prevents resolution downgrade).
    // Official web client sends the full prefilterParams set — mode OFF,
    // model 0, denoise 0, sharpness 0 — the fork previously sent only model.
    `a=video.scalingFeature1:${isAv1 ? 1 : 0}`,
    "a=video.prefilterParams.prefilterMode:0",
    "a=video.prefilterParams.prefilterModel:0",
    "a=video.prefilterParams.denoiseLevel:0",
    "a=video.prefilterParams.sharpnessLevel:0",
    // Audio track (receive-only from server)
    // NOTE: the official web client sends NO aqos.* / audio.* attributes here
    // (verified against the play.geforcenow.com bundle dump) — the fork's
    // aqos redundancy + dynamic audio config lines were native-client extras
    // and are dropped for byte-for-byte alignment.
    "m=audio 0 RTP/AVP",
    "a=msid:audio",
    // Mic track (send to server)
    "m=mic 0 RTP/AVP",
    "a=msid:mic",
    "a=rtpmap:0 PCMU/8000",
    // Input/application track
    // ri.* values echo the server's offer (partial reliability for input) —
    // matches the official client, which echoes them from the DESCRIBE
    // response instead of sending fixed values.
    "m=application 0 RTP/AVP",
    "a=msid:input_1",
    `a=ri.partialReliableThresholdMs:${params.partialReliableThresholdMs}`,
    `a=ri.hidDeviceMask:${hidDeviceMask}`,
    `a=ri.enablePartiallyReliableTransferGamepad:${enablePartiallyReliableTransferGamepad}`,
    `a=ri.enablePartiallyReliableTransferHid:${enablePartiallyReliableTransferHid}`,
    "",
  );

  return lines.join("\n");
}
