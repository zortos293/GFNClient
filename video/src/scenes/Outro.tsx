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

const LINKS = [
  { label: "Download", value: "github.com/OpenCloudGaming/OpenNOW" },
  { label: "Docs", value: "opennow.zortos.me" },
  { label: "Community", value: "Discord — link in description" },
];

export const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const cardIn = spring({ frame, fps, config: { damping: 200, stiffness: 65 }, durationInFrames: 45 });

  return (
    <AbsoluteFill style={{ backgroundColor: theme.bgA }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(ellipse 1000px 650px at 50% 50%, rgba(88,217,138,0.10), transparent 70%)`,
        }}
      />
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", flexDirection: "column" }}>
        <div
          style={{
            opacity: cardIn,
            transform: `translateY(${(1 - cardIn) * 40}px)`,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
          }}
        >
          <Img
            src={staticFile("brand/logo-mark.png")}
            style={{ width: 130, borderRadius: 30, filter: "drop-shadow(0 16px 50px rgba(88,217,138,0.35))" }}
          />
          <div
            style={{
              fontFamily: "Inter",
              fontWeight: 800,
              fontSize: 76,
              letterSpacing: "-0.025em",
              color: theme.ink,
              marginTop: 30,
            }}
          >
            Take your cloud gaming further.
          </div>
          <div
            style={{
              fontFamily: "Inter",
              fontWeight: 500,
              fontSize: 30,
              color: theme.inkSoft,
              marginTop: 14,
            }}
          >
            OpenNOW v0.5.2 — free and open source
          </div>

          <div style={{ display: "flex", gap: 22, marginTop: 56 }}>
            {LINKS.map((link, i) => {
              const rowIn = interpolate(frame, [30 + i * 10, 55 + i * 10], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
              });
              return (
                <div
                  key={link.label}
                  style={{
                    opacity: rowIn,
                    transform: `translateY(${(1 - rowIn) * 16}px)`,
                    background: theme.panel,
                    border: `1px solid ${theme.panelBorder}`,
                    borderRadius: 18,
                    padding: "22px 34px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 8,
                    minWidth: 330,
                  }}
                >
                  <div
                    style={{
                      fontFamily: "Inter",
                      fontWeight: 700,
                      fontSize: 19,
                      color: theme.accent,
                      textTransform: "uppercase",
                      letterSpacing: "0.1em",
                    }}
                  >
                    {link.label}
                  </div>
                  <div style={{ fontFamily: "Inter", fontWeight: 600, fontSize: 25, color: theme.ink }}>
                    {link.value}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
