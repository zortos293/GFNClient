import type { JSX } from "react";

export type SettingsNavItem = {
  id: SettingsSectionId;
  label: string;
  icon: JSX.Element;
};

export type SettingsNavGroup = {
  label: string;
  items: SettingsNavItem[];
};

export type ThanksLoadState = "idle" | "loading" | "loaded" | "error";
export type StorageResetState = "idle" | "resetting" | "success" | "error";
export type GameAccountBusyAction = "link" | "unlink" | "resync";

export type SettingsSectionId = "account" | "stream" | "native-streamer" | "game" | "audio" | "input" | "interface" | "about" | "thanks";
export type SettingsSearchScopeId =
  | "account-storage"
  | "stream-region"
  | "stream-video"
  | "stream-codec-diagnostics"
  | "native-streamer"
  | "game"
  | "audio"
  | "input"
  | "interface"
  | "about"
  | "thanks";

export const SETTINGS_SCOPE_SEARCH_TERMS: Record<SettingsSearchScopeId, readonly string[]> = {
  "account-storage": [
    "account",
    "subscription",
    "storage",
    "persistent storage",
    "cloud storage",
    "install to play",
    "reset storage",
    "storage reset",
    "connections",
    "connected accounts",
    "game accounts",
    "link accounts",
    "unlink accounts",
    "sync library",
    "steam",
    "epic",
    "ubisoft",
    "battle.net",
    "xbox",
    "gaijin",
    "region",
    "data",
    "games",
    "downloads",
    "available",
    "used",
  ],
  "stream-region": [
    "stream",
    "region",
    "latency",
    "ping",
    "server",
    "route",
    "auto best",
    "proxy",
    "vpn",
    "queue",
    "session",
    "sponsor",
    "github sponsor",
    "supporter",
  ],
  "stream-video": [
    "stream",
    "video",
    "quality",
    "codec",
    "fps",
    "resolution",
    "bitrate",
    "aspect ratio",
    "l4s",
    "cloud gsync",
    "console mode",
    "tv mode",
    "big picture",
    "gamepad friendly",
    "video acceleration",
    "filters",
    "shader",
    "shaders",
    "sharpen",
    "sharpening",
    "saturation",
    "contrast",
    "brightness",
    "vibrance",
    "film grain",
    "post processing",
    "session proxy",
    "community proxy",
    "zortos",
  ],
  "stream-codec-diagnostics": [
    "stream",
    "codec diagnostics",
    "diagnostics",
    "decode",
    "encode",
    "gpu",
    "cpu",
    "test codecs",
  ],
  "native-streamer": [
    "native",
    "streamer",
    "native streaming",
    "gstreamer",
    "backend",
    "directx",
    "dx11",
    "dx12",
    "cloud gsync",
    "diagnostics",
    "stats",
    "overlay",
    "experimental",
    "shortcuts",
    "alt-tab",
    "exit",
    "issue",
    "github",
    "discord",
    "report",
    "bug",
  ],
  game: ["game", "language", "keyboard layout", "store", "launch", "graphics settings", "in-game settings", "persistence"],
  audio: ["audio", "microphone", "mic", "push to talk", "voice activity"],
  input: [
    "input",
    "mouse",
    "keyboard layout",
    "shortcut",
    "hotkey",
    "keybind",
    "controls",
    "controller",
    "gamepad",
    "gyro",
    "gyroscope",
    "steam",
    "steam controller",
    "xbox",
    "compatibility",
    "gamecontroller",
    "hid",
    "macos",
    "motion controls",
    "anti afk",
    "pointer lock",
    "native cursor",
    "cursor overlay",
    "server cursor",
    "server-side cursor",
    "recording",
    "screenshot",
  ],
  interface: [
    "interface",
    "ui",
    "language",
    "locale",
    "translation",
    "app language",
    "accent color",
    "theme color",
    "overlay",
    "library",
    "fullscreen",
    "discord",
    "rich presence",
    "poster",
    "session timer",
    "session time left",
    "session countdown",
    "free tier time",
    "priority time",
    "ultimate time",
    "counter",
    "controller",
    "gamepad",
    "big picture",
  ],
  about: ["about", "update", "version", "logs", "cache", "download"],
  thanks: ["thanks", "contributors", "supporters", "sponsors", "community"],
};
