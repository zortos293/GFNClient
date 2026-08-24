export function nativeStreamerCargoArgs({ manifestPath, nativeTarget, platformKey }) {
  const args = [
    "build",
    "--locked",
    "--release",
    "--package",
    "opennow-streamer",
    "--manifest-path",
    manifestPath,
  ];
  if (platformKey.startsWith("linux-")) {
    args.push("--features", "linux-vaapi");
  }
  if (nativeTarget) {
    args.push("--target", nativeTarget);
  }
  return args;
}
