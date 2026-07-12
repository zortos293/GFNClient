import type { UpdateChannel } from "@shared/gfn";

interface UpdaterChannelTarget {
  channel: string | null;
  allowPrerelease: boolean;
  allowDowngrade: boolean;
}

export interface UpdateChannelPolicy {
  feedChannel: "latest" | "nightly";
  allowPrerelease: boolean;
}

export function getUpdateChannelPolicy(channel: UpdateChannel): UpdateChannelPolicy {
  return channel === "nightly"
    ? { feedChannel: "nightly", allowPrerelease: true }
    : { feedChannel: "latest", allowPrerelease: false };
}

export function applyUpdateChannel(target: UpdaterChannelTarget, channel: UpdateChannel): void {
  const policy = getUpdateChannelPolicy(channel);
  target.channel = policy.feedChannel;
  target.allowPrerelease = policy.allowPrerelease;
  // Changing electron-updater's channel enables downgrades. OpenNOW never needs
  // that behavior: nightly versions target the next stable semver instead.
  target.allowDowngrade = false;
}
