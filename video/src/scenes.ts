export interface SceneConfig {
  id: string;
  kind: "intro" | "feature" | "outro";
  chapter?: { index: number; title: string; subtitle: string };
  /** Seconds trimmed from the start of the footage clip. */
  trimStartSec?: number;
  /** Fallback scene length in seconds when no voiceover file exists. */
  fallbackSec: number;
  /** Extra tail after the narration ends, in seconds. */
  tailSec?: number;
}

export const SCENES: SceneConfig[] = [
  { id: "intro", kind: "intro", fallbackSec: 13 },
  {
    id: "signin",
    kind: "feature",
    chapter: { index: 1, title: "Sign in", subtitle: "Your NVIDIA account, official login, QR support" },
    fallbackSec: 20,
  },
  {
    id: "store",
    kind: "feature",
    chapter: { index: 2, title: "The Store", subtitle: "The full GeForce NOW catalog, searchable in real time" },
    fallbackSec: 33,
  },
  {
    id: "library",
    kind: "feature",
    chapter: { index: 3, title: "Your Library", subtitle: "Every owned game, one organized place" },
    fallbackSec: 27,
  },
  {
    id: "settings-stream",
    kind: "feature",
    chapter: { index: 4, title: "Stream Settings", subtitle: "Region, resolution, FPS, codec, bitrate, live filters" },
    fallbackSec: 36,
  },
  {
    id: "settings-app",
    kind: "feature",
    chapter: { index: 5, title: "Make It Yours", subtitle: "Native streamer, themes, accents, 12 languages" },
    fallbackSec: 28,
  },
  {
    id: "launch",
    kind: "feature",
    chapter: { index: 6, title: "Launching a Game", subtitle: "One click from library to cloud" },
    trimStartSec: 8,
    fallbackSec: 24,
  },
  {
    id: "gameplay",
    kind: "feature",
    chapter: { index: 7, title: "In the Stream", subtitle: "Stats overlay, sidebar controls, clean exit" },
    fallbackSec: 29,
  },
  {
    id: "extras",
    kind: "feature",
    chapter: { index: 8, title: "The Little Things", subtitle: "Multi-account, playtime tracking, Discord, and more" },
    fallbackSec: 23,
  },
  { id: "outro", kind: "outro", fallbackSec: 15, tailSec: 2 },
];

export interface SceneTiming {
  id: string;
  from: number;
  durationInFrames: number;
  hasVo: boolean;
  hasFootage: boolean;
  footageDurationInFrames: number;
}

export interface WalkthroughProps extends Record<string, unknown> {
  timings: SceneTiming[];
}
