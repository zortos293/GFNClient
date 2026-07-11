import React from "react";
import { AbsoluteFill, Audio, Sequence, staticFile } from "remotion";
import { loadFont } from "@remotion/google-fonts/Inter";
import { SCENES, type WalkthroughProps } from "./scenes";
import { theme } from "./theme";
import { Intro } from "./scenes/Intro";
import { Outro } from "./scenes/Outro";
import { FeatureScene } from "./scenes/FeatureScene";

loadFont();

export const Walkthrough: React.FC<WalkthroughProps> = ({ timings }) => {
  return (
    <AbsoluteFill style={{ backgroundColor: theme.bgA }}>
      {timings.map((timing) => {
        const config = SCENES.find((s) => s.id === timing.id);
        if (!config) return null;
        return (
          <Sequence key={timing.id} from={timing.from} durationInFrames={timing.durationInFrames} name={timing.id}>
            {config.kind === "intro" && <Intro />}
            {config.kind === "outro" && <Outro />}
            {config.kind === "feature" && <FeatureScene config={config} timing={timing} />}
            {timing.hasVo && <Audio src={staticFile(`audio/${timing.id}.mp3`)} />}
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
