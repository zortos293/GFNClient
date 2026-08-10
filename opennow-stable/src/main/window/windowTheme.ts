import type { AppTheme } from "@shared/gfn";

interface NativeThemeLike {
  themeSource: "system" | "light" | "dark";
  readonly shouldUseDarkColors: boolean;
}

export function applyNativeAppTheme(theme: AppTheme, nativeTheme: NativeThemeLike): string {
  nativeTheme.themeSource = theme === "auto" ? "system" : theme;
  return nativeTheme.shouldUseDarkColors ? "#101014" : "#f8fafc";
}
