export function nativeStreamerCargoArgs({
  manifestPath,
  nativeTarget,
  platformKey,
  enableLinuxVaapi = true,
}) {
  const args = [
    "build",
    "--locked",
    "--release",
    "--package",
    "opennow-streamer",
    "--manifest-path",
    manifestPath,
  ];
  if (platformKey.startsWith("linux-") && enableLinuxVaapi) {
    args.push("--features", "linux-vaapi");
  }
  if (nativeTarget) {
    args.push("--target", nativeTarget);
  }
  return args;
}
