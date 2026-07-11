import React from "react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";

export const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();

  const logoIn = spring({ frame, fps, config: { damping: 200, stiffness: 60 }, durationInFrames: 50 });
  const nameIn = spring({ frame: frame - 22, fps, config: { damping: 200, stiffness: 70 }, durationInFrames: 45 });
  const tagIn = interpolate(frame, [55, 90], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const chipIn = interpolate(frame, [80, 105], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const fadeOut = interpolate(frame, [durationInFrames - 25, durationInFrames - 5], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const glow = interpolate(frame, [0, 90], [0.35, 0.65], { extrapolateRight: "clamp" });

  return (
    <AbsoluteFill style={{ backgroundColor: theme.bgA, opacity: fadeOut }}>
      {/* Ambient brand glow */}
      <AbsoluteFill
        style={{
          background: `radial-gradient(ellipse 900px 600px at 50% 42%, rgba(88,217,138,${0.14 * glow}), transparent 70%),
                       radial-gradient(ellipse 1400px 900px at 50% 110%, rgba(88,217,138,0.05), transparent 60%)`,
        }}
      />
      {/* Faint grid for depth */}
      <AbsoluteFill
        style={{
          backgroundImage:
            "linear-gradient(rgba(255,255,255,0.025) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.025) 1px, transparent 1px)",
          backgroundSize: "72px 72px",
          maskImage: "radial-gradient(ellipse 60% 55% at 50% 45%, black, transparent)",
          WebkitMaskImage: "radial-gradient(ellipse 60% 55% at 50% 45%, black, transparent)",
        }}
      />

      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
        <Img
          src={staticFile("brand/logo.png")}
          style={{
            width: 300,
            transform: `scale(${0.7 + 0.3 * logoIn}) translateY(${(1 - logoIn) * 40}px)`,
            opacity: logoIn,
            filter: "drop-shadow(0 24px 70px rgba(88,217,138,0.35))",
          }}
        />
        <div
          style={{
            fontFamily: "Inter",
            fontWeight: 800,
            fontSize: 110,
            letterSpacing: "-0.03em",
            color: theme.ink,
            marginTop: 18,
            opacity: nameIn,
            transform: `translateY(${(1 - nameIn) * 30}px)`,
          }}
        >
          Open<span style={{ color: theme.accent }}>NOW</span>
        </div>
        <div
          style={{
            fontFamily: "Inter",
            fontWeight: 500,
            fontSize: 34,
            color: theme.inkSoft,
            marginTop: 10,
            opacity: tagIn,
            letterSpacing: "0.01em",
          }}
        >
          Open-source cloud gaming client
        </div>
        <div
          style={{
            marginTop: 42,
            opacity: chipIn,
            transform: `translateY(${(1 - chipIn) * 14}px)`,
            display: "flex",
            gap: 16,
          }}
        >
          {["Free", "Open Source", "Windows · macOS · Linux"].map((label) => (
            <div
              key={label}
              style={{
                fontFamily: "Inter",
                fontWeight: 600,
                fontSize: 22,
                color: theme.accent,
                background: "rgba(88,217,138,0.09)",
                border: "1px solid rgba(88,217,138,0.28)",
                borderRadius: 999,
                padding: "10px 26px",
              }}
            >
              {label}
            </div>
          ))}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
