export type VideoCodec = "H264" | "H265" | "AV1";
export type VideoAccelerationPreference = "auto" | "hardware" | "software";
export type StreamClientMode = "web" | "native";
/**
 * How the server-side session should present launched games.
 * Mirrors the official client's AppLaunchMode: TV/console clients request
 * "gamepadFriendly" so launchers (e.g. Steam) start in big picture mode.
 */
export type AppLaunchMode = "default" | "gamepadFriendly" | "touchFriendly";
export type NativeQueueMode = "auto" | "fixed" | "adaptive" | "vrr";

/** Color quality (bit depth + chroma subsampling), matching Rust ColorQuality enum */
export type ColorQuality = "8bit_420" | "8bit_444" | "10bit_420" | "10bit_444";

/** Helper: get CloudMatch bitDepth value (0 = 8-bit, 1 = 10-bit) */
export function colorQualityBitDepth(cq: ColorQuality): number {
  return cq.startsWith("10bit") ? 1 : 0;
}

/** Helper: get CloudMatch chromaFormat value (0 = 4:2:0, 1 = 4:4:4) */
export function colorQualityChromaFormat(cq: ColorQuality): number {
  return cq.endsWith("444") ? 1 : 0;
}

/** Helper: does this color quality mode require HEVC or AV1? */
export function colorQualityRequiresHevc(cq: ColorQuality): boolean {
  return cq !== "8bit_420";
}

export const USER_FACING_VIDEO_CODEC_OPTIONS: readonly VideoCodec[] = ["H264", "H265", "AV1"];
export const USER_FACING_COLOR_QUALITY_OPTIONS: readonly ColorQuality[] = ["8bit_420", "8bit_444", "10bit_420", "10bit_444"];

export function isSupportedUserFacingCodec(codec: VideoCodec): boolean {
  return USER_FACING_VIDEO_CODEC_OPTIONS.includes(codec);
}

export function normalizeStreamPreferences(codec: VideoCodec, colorQuality: ColorQuality): {
  codec: VideoCodec;
  colorQuality: ColorQuality;
  migrated: boolean;
} {
  const normalizedCodec = isSupportedUserFacingCodec(codec)
    ? codec
    : USER_FACING_VIDEO_CODEC_OPTIONS[0];
  const normalizedColorQuality = USER_FACING_COLOR_QUALITY_OPTIONS.includes(colorQuality)
    ? colorQuality
    : USER_FACING_COLOR_QUALITY_OPTIONS[0];
  const codecCompatibleColorQuality = normalizedCodec === "H264" ? "8bit_420" : normalizedColorQuality;

  return {
    codec: normalizedCodec,
    colorQuality: codecCompatibleColorQuality,
    migrated: normalizedCodec !== codec || codecCompatibleColorQuality !== colorQuality,
  };
}

/** Helper: is this a 10-bit (HDR-capable) mode? */
export function colorQualityIs10Bit(cq: ColorQuality): boolean {
  return cq.startsWith("10bit");
}

export interface StreamingFeatures {
  reflex?: boolean;
  bitDepth?: number;
  cloudGsync?: boolean;
  chromaFormat?: number;
  enabledL4S?: boolean;
  trueHdr?: boolean;
}

export interface NativeTransitionDiagnostics {
  disableDynamicSplitEncodeUpdates?: boolean;
  forceQueueMode?: NativeQueueMode;
  disableTransitionFlushEscalation?: boolean;
}
