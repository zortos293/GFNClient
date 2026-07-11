import React from "react";
import { Composition, staticFile, type CalculateMetadataFunction } from "remotion";
import { getAudioDurationInSeconds, getVideoMetadata } from "@remotion/media-utils";
import { Walkthrough } from "./Walkthrough";
import { Thumbnail } from "./Thumbnail";
import { SCENES, type SceneTiming, type WalkthroughProps } from "./scenes";
import { VIDEO_FPS, VIDEO_HEIGHT, VIDEO_WIDTH } from "./theme";

const CROSSFADE_PAD_SEC = 0.5;

const calculateMetadata: CalculateMetadataFunction<WalkthroughProps> = async () => {
  const timings: SceneTiming[] = [];
  let cursor = 0;

  for (const scene of SCENES) {
    let voSec: number | null = null;
    try {
      voSec = await getAudioDurationInSeconds(staticFile(`audio/${scene.id}.mp3`));
    } catch {
      voSec = null;
    }

    let footageSec = 0;
    let hasFootage = false;
    if (scene.kind === "feature") {
      try {
        footageSec = (await getVideoMetadata(staticFile(`footage/${scene.id}.mp4`))).durationInSeconds;
        hasFootage = true;
      } catch {
        hasFootage = false;
      }
    }

    const tail = scene.tailSec ?? CROSSFADE_PAD_SEC;
    const sceneSec = voSec !== null ? voSec + 0.6 + tail : scene.fallbackSec;
    const durationInFrames = Math.round(sceneSec * VIDEO_FPS);

    timings.push({
      id: scene.id,
      from: cursor,
      durationInFrames,
      hasVo: voSec !== null,
      hasFootage,
      footageDurationInFrames: Math.floor(footageSec * VIDEO_FPS),
    });
    cursor += durationInFrames;
  }

  return {
    durationInFrames: cursor,
    props: { timings },
  };
};

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Walkthrough"
        component={Walkthrough}
        width={VIDEO_WIDTH}
        height={VIDEO_HEIGHT}
        fps={VIDEO_FPS}
        defaultProps={{ timings: [] }}
        calculateMetadata={calculateMetadata}
      />
      <Composition
        id="Thumbnail"
        component={Thumbnail}
        width={VIDEO_WIDTH}
        height={VIDEO_HEIGHT}
        fps={VIDEO_FPS}
        durationInFrames={1}
      />
    </>
  );
};
