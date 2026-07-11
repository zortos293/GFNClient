/**
 * Generates the per-scene voiceover with the ElevenLabs API.
 *
 * Requires ELEVENLABS_API_KEY in video/.env (or the environment).
 * Optional: ELEVENLABS_VOICE_ID (defaults to "Adam").
 *
 * Usage:
 *   npm run vo             # generate missing files
 *   npm run vo -- --force  # regenerate everything
 *   npm run vo -- --scenes=intro,outro
 */
import { config as loadEnv } from "dotenv";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { NARRATION } from "./narration";

const __dirname = dirname(fileURLToPath(import.meta.url));
loadEnv({ path: resolve(__dirname, "..", ".env") });

const API_KEY = process.env.ELEVENLABS_API_KEY;
// Will: laid-back, conversational young American male — reads like a founder
// demoing his own app rather than a commercial narrator.
const VOICE_ID = process.env.ELEVENLABS_VOICE_ID ?? "bIHbv24MWmeRgasZH58o"; // Will
// Eleven v3: the expressive model. Understands audio tags like [chuckles] and
// delivers far more human pacing than multilingual v2's neutral narration.
const MODEL_ID = process.env.ELEVENLABS_MODEL_ID ?? "eleven_v3";

const audioDir = resolve(__dirname, "..", "public", "audio");
const argv = process.argv.slice(2);
const force = argv.includes("--force");
const scenesArg = argv.find((a) => a.startsWith("--scenes="));
const only = scenesArg ? scenesArg.split("=")[1].split(",") : null;

async function generate(sceneId: string, text: string): Promise<void> {
  const outFile = join(audioDir, `${sceneId}.mp3`);
  if (!force && existsSync(outFile)) {
    console.log(`skip ${sceneId} (exists)`);
    return;
  }
  console.log(`generating ${sceneId}...`);
  const res = await fetch(
    `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}?output_format=mp3_44100_128`,
    {
      method: "POST",
      headers: {
        "xi-api-key": API_KEY as string,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        text,
        model_id: MODEL_ID,
        voice_settings: MODEL_ID.startsWith("eleven_v3")
          ? // v3 only accepts discrete stability: 0.0 Creative / 0.5 Natural / 1.0 Robust.
            { stability: 0.5 }
          : {
              // Lower stability + higher style = looser, more conversational read.
              stability: 0.38,
              similarity_boost: 0.7,
              style: 0.45,
              use_speaker_boost: true,
            },
      }),
    },
  );
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`ElevenLabs request failed for ${sceneId}: ${res.status} ${body.slice(0, 300)}`);
  }
  const buf = Buffer.from(await res.arrayBuffer());
  writeFileSync(outFile, buf);
  console.log(`wrote ${outFile} (${(buf.length / 1024).toFixed(0)} KiB)`);
}

async function main(): Promise<void> {
  if (!API_KEY) {
    console.error("ELEVENLABS_API_KEY is not set. Add it to video/.env");
    process.exit(1);
  }
  mkdirSync(audioDir, { recursive: true });
  for (const [sceneId, text] of Object.entries(NARRATION)) {
    if (only && !only.includes(sceneId)) continue;
    await generate(sceneId, text);
  }
  console.log("done");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
