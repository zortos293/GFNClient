import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";
import { loadFont } from "@remotion/google-fonts/Inter";
import { theme } from "./theme";

loadFont();

/** Clean YouTube thumbnail: centered logo lockup on the brand background. */
export const Thumbnail: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: theme.bgA }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(ellipse 700px 450px at 50% 50%, rgba(88,217,138,0.10), transparent 70%)`,
        }}
      />
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "row",
          gap: 44,
        }}
      >
        <Img
          src={staticFile("brand/logo.png")}
          style={{
            width: 340,
            filter: "drop-shadow(0 20px 60px rgba(88,217,138,0.30))",
          }}
        />
        <div
          style={{
            fontFamily: "Inter",
            fontWeight: 800,
            fontSize: 170,
            letterSpacing: "-0.03em",
            color: theme.ink,
          }}
        >
          Open<span style={{ color: theme.accent }}>NOW</span>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
