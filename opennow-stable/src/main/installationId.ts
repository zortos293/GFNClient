import { randomUUID } from "node:crypto";
import type { SettingsManager } from "./settings";

/** Main-process owner for the anonymous installation ID shared by opt-in telemetry and reports. */
export function getOrCreateTelemetryInstallId(settingsManager: SettingsManager): string {
  const existing = settingsManager.get("telemetryInstallId").trim();
  if (existing) return existing;

  const generated = randomUUID();
  settingsManager.set("telemetryInstallId", generated);
  return generated;
}
