/**
 * Copy Framegen v7-small weights into the renderer public folder so the
 * Electron app can load them offline from the app origin.
 *
 * Weight license is non-commercial (see third_party/framegen/WEIGHTS_LICENSE.md).
 */
import { cpSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = join(root, "node_modules", "framegen", "weights");
const dest = join(root, "src", "renderer", "public", "framegen-weights");
const noticeDir = join(root, "third_party", "framegen");

if (!existsSync(src)) {
  console.warn(`[copy-framegen-weights] skip: ${src} not found (install framegen first)`);
  process.exit(0);
}

mkdirSync(dest, { recursive: true });
cpSync(src, dest, { recursive: true });

mkdirSync(noticeDir, { recursive: true });
writeFileSync(
  join(noticeDir, "WEIGHTS_LICENSE.md"),
  `# Framegen model weights

The files under \`src/renderer/public/framegen-weights/\` are the Framegen
v7-small neural frame-interpolation weights, redistributed from the
[\`framegen\`](https://www.npmjs.com/package/framegen) npm package.

**Non-commercial research and personal use only.** See the upstream notice:
https://github.com/MONZikWasTaken/Framegen/blob/main/WEIGHTS_LICENSE.md

Framegen **runtime code** (JavaScript/WGSL) is MIT-licensed separately.
`,
  "utf8",
);

console.log(`[copy-framegen-weights] copied weights to ${dest}`);
