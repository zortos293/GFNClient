import type { VideoCodec } from "@shared/gfn";

export const CODEC_MIME_BY_NAME: Record<VideoCodec, string> = {
  H264: "video/H264",
  H265: "video/H265",
  AV1: "video/AV1",
};

/**
 * GFN primary video codecs. When `keepFallbacks` is set, the requested codec
 * stays first and these are appended so the answer can fall back to a
 * decodable codec.
 */
const GFN_PRIMARY_MIMES: readonly string[] = ["video/H264", "video/H265", "video/AV1"];

export interface CodecPreferenceListOptions {
  preferredHevcProfileId?: 1 | 2;
  /**
   * When true, also include the other GFN primary codecs after the requested
   * one. Receiver capabilities can list a codec that `createAnswer` still
   * rejects for a given server payload, so the answer must always have a
   * decodable fallback available (GFN-web behavior).
   */
  keepFallbacks?: boolean;
  /**
   * User-pinned fallback codec (web mode). When `keepFallbacks` is set, this
   * codec's entries come first among the fallbacks so it wins whenever the
   * requested codec cannot be negotiated. Must differ from the requested
   * codec; ignored when equal, absent, or `keepFallbacks` is false.
   */
  fallbackCodec?: VideoCodec;
}

/**
 * Build the `RTCRtpCodec` list for `setCodecPreferences` from the receiver's
 * codec capabilities. The requested codec's entries come first (H265 ordered by
 * the preferred profile-id for decoder compatibility), then — when
 * `keepFallbacks` is set — the other GFN primaries, then RTX/FlexFEC auxiliary
 * entries. Returns an empty list only when no GFN primary or auxiliary codec is
 * present in the caps; callers must skip `setCodecPreferences` in that case (an
 * empty list is invalid there). Note that in strict mode with no matching
 * requested codec, the auxiliary entries alone may be returned — callers are
 * responsible for guarding against a video section with no primary codec.
 */
export function buildCodecPreferenceList(
  receiverCaps: RTCRtpCodec[],
  codec: VideoCodec,
  options: CodecPreferenceListOptions = {},
): RTCRtpCodec[] {
  const preferredMime = CODEC_MIME_BY_NAME[codec];
  const mimeOf = (entry: RTCRtpCodec): string => entry.mimeType.toLowerCase();

  let preferred = receiverCaps.filter((entry) => mimeOf(entry) === preferredMime.toLowerCase());

  if (codec === "H265" && options.preferredHevcProfileId) {
    preferred = [...preferred].sort((a, b) => {
      const score = (entry: RTCRtpCodec): number => {
        const fmtp = (entry.sdpFmtpLine ?? "").toLowerCase();
        const profile = fmtp.match(/(?:^|;)\s*profile-id=(\d+)/)?.[1];
        if (profile === String(options.preferredHevcProfileId)) return 0;
        if (!profile) return 1;
        return 2;
      };
      return score(a) - score(b);
    });
  }

  const auxiliary = receiverCaps.filter((entry) => {
    const mime = mimeOf(entry);
    return mime.includes("rtx") || mime.includes("flexfec-03");
  });

  if (!options.keepFallbacks) {
    return [...preferred, ...auxiliary];
  }

  const fallbacks: RTCRtpCodec[] = [];
  const pinnedFallback = options.fallbackCodec && options.fallbackCodec !== codec
    ? options.fallbackCodec
    : undefined;
  const fallbackMimes = pinnedFallback
    ? [CODEC_MIME_BY_NAME[pinnedFallback], ...GFN_PRIMARY_MIMES.filter(
      (mime) => mime.toLowerCase() !== preferredMime.toLowerCase()
        && mime.toLowerCase() !== CODEC_MIME_BY_NAME[pinnedFallback].toLowerCase(),
    )]
    : GFN_PRIMARY_MIMES;
  for (const mime of fallbackMimes) {
    if (mime.toLowerCase() === preferredMime.toLowerCase()) {
      continue;
    }
    const entries = receiverCaps.filter((entry) => mimeOf(entry) === mime.toLowerCase());
    if (pinnedFallback === "H265" && mime.toLowerCase() === "video/h265" && options.preferredHevcProfileId) {
      entries.sort((a, b) => {
        const score = (entry: RTCRtpCodec): number => {
          const fmtp = (entry.sdpFmtpLine ?? "").toLowerCase();
          const profile = fmtp.match(/(?:^|;)\s*profile-id=(\d+)/)?.[1];
          if (profile === String(options.preferredHevcProfileId)) return 0;
          if (!profile) return 1;
          return 2;
        };
        return score(a) - score(b);
      });
    }
    fallbacks.push(...entries);
  }

  return [...preferred, ...fallbacks, ...auxiliary];
}
