# OpenNOW walkthrough video (Remotion)

Produces the YouTube walkthrough video from real app footage.

## Pipeline

1. `npm install` (in this folder)
2. Build the app once: `npm --prefix ../opennow-stable run build`
3. Capture real footage (drives the actual app, records 1080p60):
   `npm run capture` — clips land in `public/footage/`
4. Put `ELEVENLABS_API_KEY=...` in `.env`, then generate the voiceover:
   `npm run vo` — mp3s land in `public/audio/`
5. Preview: `npm run studio`
6. Render: `npm run render` → `out/opennow-walkthrough.mp4`

Scene timing is derived from the voiceover lengths automatically; scenes fall
back to fixed durations when an mp3 is missing. Narration text lives in
`scripts/narration.ts` (mirrored in `script.md`).

Notes:

- Capture requires the machine to be idle (the recorder captures the screen
  region under the app window; keep it unobstructed).
- The capture script restores the signed-in session from the dev profile and
  never completes QR logins. Account emails and proxy URLs are blurred at
  capture time.
