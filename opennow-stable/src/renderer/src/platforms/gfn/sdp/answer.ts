/**
 * Munge an SDP answer to inject bitrate limits and optimize audio codec params.
 * 
 * This matches what the official GFN browser client does:
 * 1. Adds "b=AS:<kbps>" after each m= line to signal our max receive bitrate
 * 2. Adds "stereo=1" to the opus fmtp line for stereo audio support
 * 
 * These are hints to the server encoder — they don't enforce limits client-side
 * but help the server avoid overshooting our link capacity.
 */
export function mungeAnswerSdp(sdp: string, maxBitrateKbps: number): string {
  const lineEnding = sdp.includes("\r\n") ? "\r\n" : "\n";
  const lines = sdp.split(/\r?\n/);
  const result: string[] = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    result.push(line);

    // After each m= line, inject b=AS: if not already present
    if (line.startsWith("m=video") || line.startsWith("m=audio")) {
      const bitrateForSection = line.startsWith("m=video")
        ? maxBitrateKbps
        : 128; // 128 kbps for audio is plenty for opus stereo
      const nextLine = lines[i + 1] ?? "";
      if (!nextLine.startsWith("b=")) {
        result.push(`b=AS:${bitrateForSection}`);
      }
    }

    // Append stereo=1 to opus fmtp line if not already present
    if (line.startsWith("a=fmtp:") && line.includes("minptime=") && !line.includes("stereo=1")) {
      // Replace the line we just pushed with the stereo-augmented version
      result[result.length - 1] = line + ";stereo=1";
    }
  }

  console.log(`[SDP] mungeAnswerSdp: injected b=AS:${maxBitrateKbps} for video, b=AS:128 for audio, stereo=1 for opus`);
  return result.join(lineEnding);
}
