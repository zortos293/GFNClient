import crypto from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { createRequire } from "node:module";

const GFN_DEVICE_ID_FILENAME = "gfn-device-id.json";
let cachedStableDeviceId: string | null = null;
const require = createRequire(import.meta.url);

function getElectronApp(): Electron.App | null {
  try {
    return require("electron").app ?? null;
  } catch {
    return null;
  }
}

export function getStableDeviceId(): string {
  if (cachedStableDeviceId) {
    return cachedStableDeviceId;
  }

  try {
    const electronApp = getElectronApp();
    if (!electronApp) {
      throw new Error("Electron app is unavailable outside the main process.");
    }
    const path = join(electronApp.getPath("userData"), GFN_DEVICE_ID_FILENAME);
    if (existsSync(path)) {
      const parsed = JSON.parse(readFileSync(path, "utf-8")) as { deviceId?: unknown };
      if (typeof parsed.deviceId === "string" && parsed.deviceId.length > 0) {
        cachedStableDeviceId = parsed.deviceId;
        return parsed.deviceId;
      }
    }

    const deviceId = crypto.randomUUID();
    writeFileSync(path, JSON.stringify({ deviceId }, null, 2), "utf-8");
    cachedStableDeviceId = deviceId;
    return deviceId;
  } catch (error) {
    const fallback = crypto.randomUUID();
    cachedStableDeviceId = fallback;
    console.warn("[DeviceId] Failed to load persisted device ID, using in-memory fallback:", error);
    return fallback;
  }
}
