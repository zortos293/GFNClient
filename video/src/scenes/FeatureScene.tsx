import React from "react";
import {
  AbsoluteFill,
  OffthreadVideo,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";
import type { SceneConfig, SceneTiming } from "../scenes";

const CHAPTER_VISIBLE_SEC = 4.6;

const ChapterLowerThird: React.FC<{
  chapter: NonNullable<SceneConfig["chapter"]>;
}> = ({ chapter }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const inSpring = spring({ frame: frame - 12, fps, config: { damping: 200, stiffness: 90 }, durationInFrames: 35 });
  const outStart = Math.round(CHAPTER_VISIBLE_SEC * fps);
  const out = interpolate(frame, [outStart, outStart + 22], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const y = (1 - inSpring) * 60 + out * 60;
  const opacity = inSpring * (1 - out);

  return (
    <div
      style={{
        position: "absolute",
        left: 70,
        bottom: 64,
        transform: `translateY(${y}px)`,
        opacity,
        display: "flex",
        alignItems: "stretch",
        gap: 22,
        background: "rgba(16,16,20,0.82)",
        backdropFilter: "blur(14px)",
        border: `1px solid ${theme.panelBorder}`,
        borderRadius: 20,
        padding: "24px 36px 24px 28px",
        boxShadow: "0 22px 60px rgba(0,0,0,0.45)",
      }}
    >
      <div style={{ width: 6, borderRadius: 3, background: theme.accent }} />
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <div
          style={{
            fontFamily: "Inter",
            fontWeight: 700,
            fontSize: 19,
            letterSpacing: "0.16em",
            color: theme.accent,
            textTransform: "uppercase",
          }}
        >
          {String(chapter.index).padStart(2, "0")} — OpenNOW
        </div>
        <div style={{ fontFamily: "Inter", fontWeight: 800, fontSize: 46, letterSpacing: "-0.02em", color: theme.ink }}>
          {chapter.title}
        </div>
        <div style={{ fontFamily: "Inter", fontWeight: 500, fontSize: 24, color: theme.inkSoft }}>
          {chapter.subtitle}
        </div>
      </div>
    </div>
  );
};

export const FeatureScene: React.FC<{
  config: SceneConfig;
  timing: SceneTiming;
}> = ({ config, timing }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();

  const fadeIn = interpolate(frame, [0, 14], [0, 1], { extrapolateRight: "clamp" });
  const fadeOut = interpolate(frame, [durationInFrames - 14, durationInFrames], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  // Subtle Ken Burns: slow push-in over the scene.
  const zoom = interpolate(frame, [0, durationInFrames], [1.0, 1.045]);

  const trimFrames = Math.round((config.trimStartSec ?? 0) * fps);

  // If the footage runs out before the scene ends, OffthreadVideo holds the
  // last frame, which reads as a natural pause in a screen recording.
  const video = timing.hasFootage ? (
    <OffthreadVideo
      src={staticFile(`footage/${config.id}.mp4`)}
      trimBefore={trimFrames}
      muted
      style={{ width: "100%", height: "100%", objectFit: "cover" }}
    />
  ) : null;

  return (
    <AbsoluteFill style={{ backgroundColor: theme.bgA }}>
      <AbsoluteFill style={{ opacity: fadeIn * fadeOut }}>
        <AbsoluteFill style={{ transform: `scale(${zoom})` }}>
          {video}
          {!timing.hasFootage && (
            <AbsoluteFill
              style={{
                alignItems: "center",
                justifyContent: "center",
                fontFamily: "Inter",
                fontWeight: 600,
                fontSize: 34,
                color: theme.inkMuted,
              }}
            >
              Footage missing: {config.id}.mp4
            </AbsoluteFill>
          )}
        </AbsoluteFill>
        {/* Gentle vignette to focus the eye */}
        <AbsoluteFill
          style={{
            background:
              "radial-gradient(ellipse 120% 120% at 50% 40%, transparent 60%, rgba(0,0,0,0.28) 100%)",
            pointerEvents: "none",
          }}
        />
        {config.chapter && <ChapterLowerThird chapter={config.chapter} />}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
