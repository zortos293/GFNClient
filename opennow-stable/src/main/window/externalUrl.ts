import { shell } from "electron";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import {
  parseExplicitExternalUrl,
  parseExternalHttpUrl,
} from "./externalUrlPolicy";

export function isAppNavigationUrl(url: string, mainDir: string): boolean {
  try {
    const parsed = new URL(url);
    if (process.env.ELECTRON_RENDERER_URL) {
      return parsed.origin === new URL(process.env.ELECTRON_RENDERER_URL).origin;
    }
    return (
      parsed.toString() ===
      pathToFileURL(join(mainDir, "../../dist/index.html")).toString()
    );
  } catch {
    return false;
  }
}

export async function openExternalHttpUrl(url: string): Promise<void> {
  await shell.openExternal(parseExternalHttpUrl(url).toString());
}

export async function openExplicitExternalUrl(url: string): Promise<void> {
  await shell.openExternal(parseExplicitExternalUrl(url).toString());
}
