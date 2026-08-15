/**
 * Copy Framegen v7-small weights into the renderer public folder so the
 * Electron app can load them offline from the app origin.
 *
 * Weight license is non-commercial (see third_party/framegen/WEIGHTS_LICENSE.md).
 */
import { cpSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = join(root, "node_modules", "framegen", "weights");
const dest = join(root, "src", "renderer", "public", "framegen-weights");

if (!existsSync(src)) {
  console.warn(`[copy-framegen-weights] skip: ${src} not found (install framegen first)`);
  process.exit(0);
}

mkdirSync(dest, { recursive: true });
cpSync(src, dest, { recursive: true });

console.log(`[copy-framegen-weights] copied weights to ${dest}`);
