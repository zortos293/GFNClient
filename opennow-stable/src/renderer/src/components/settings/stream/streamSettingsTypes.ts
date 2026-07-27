import type { Settings } from "@shared/gfn";

export type SettingsChangeHandler = <K extends keyof Settings>(
  key: K,
  value: Settings[K],
) => void;

