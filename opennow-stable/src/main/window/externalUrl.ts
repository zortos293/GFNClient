import { shell } from "electron";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

export function parseExternalHttpUrl(url: string): URL {
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
    throw new Error("Only HTTP(S) external URLs can be opened.");
  }
  return parsed;
}

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
