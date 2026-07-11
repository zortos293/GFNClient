# OpenNOW — YouTube Walkthrough Script

Tone: a young founder/student casually demoing his own app (not a commercial
narrator). Voice: ElevenLabs "Will" (conversational young American male),
stability 0.38 / style 0.45 for a looser read.

One voiceover file per scene (`video/public/audio/<scene>.mp3`), one footage
clip per scene (`video/public/footage/<scene>.mp4`). Scene durations in the
final composition are driven by the narration length. The canonical text lives
in `scripts/narration.ts` — keep this file in sync.

## 1. intro — Logo reveal

> Okay, so this is OpenNOW. It's a free, open-source desktop client for GeForce
> NOW that me and the community have been building. Basically, you bring your
> own GeForce NOW account, and you get a way faster, way cleaner app with a ton
> of stuff the official client just doesn't let you do. Let me just show you.

## 2. signin — Login screen

> So getting in is super simple. You sign in with your normal NVIDIA account,
> through NVIDIA's actual login page — we never see your password, it all goes
> straight to their servers. If you're lazy like me, just scan a QR code with
> your phone and you're in. Oh, and it does multiple accounts too, so you can
> hop between them with one click.

## 3. store — Browsing the catalog

> Alright, this is the Store. It's literally the entire GeForce NOW catalog —
> thousands of games — and search is instant. Every card shows you which store
> the game is on: Steam, Epic, Ubisoft, GOG, Xbox, whatever. You can filter by
> store, by genre, by how you wanna play. And when you find something you own,
> you just hit play. That's it. The whole thing is fast and it stays out of
> your way.

## 4. library — Your games

> This is your Library — every game you own, pulled straight from your
> connected store accounts, all in one place. You can filter by platform, by
> play type, or by input — keyboard and mouse, controller, even touch. Click on
> any game and you get all the details: developer, publisher, genres, what
> NVIDIA tech it runs. It's your whole cloud library, actually organized.

## 5. settings-stream — Stream quality

> Okay, this is honestly my favorite part — the control you get. Open the
> Stream settings and everything's yours. Pick your server region by hand, or
> let the app ping every server and pick the best one for you. Resolution,
> frame rate, codec, max bitrate — all yours, no artificial caps. There's even
> live video filters, like sharpening and saturation, that you can tweak while
> you're playing.

## 6. settings-app — Native streamer and personalization

> And if you really wanna nerd out, there's an experimental native streamer —
> it's written in Rust with GStreamer, DirectX backends, custom frame pacing —
> all built to squeeze out every last millisecond of latency. The app itself is
> super customizable too: light and dark themes, translucent UI, accent colors,
> a full controller mode for the couch, and it's translated into twelve
> languages.

## 7. launch — Starting a session

> Launching a game is exactly what you'd hope. One click on play, and it just
> handles everything — you get queued, your cloud rig spins up, the stream
> connects. You can literally watch each step happen, and you're in the game in
> like, seconds.

## 8. gameplay — In the stream

> And once you're in, OpenNOW stays completely out of your way — but
> everything's one shortcut away. Hit F3 and you get a live stats overlay:
> bitrate, latency, packet loss, decode times, all real time. Control G opens
> the sidebar — that's your remaining playtime, your mic, screenshots,
> everything for the session. And when you're done, one shortcut ends the
> session, and you're right back in the app.

## 9. extras — The little things

> And there's a bunch of little stuff that adds up. Discord Rich Presence so
> your friends see what you're playing. Anti-AFK so your session doesn't die on
> you. Screenshots and recordings saved locally, Playnite integration,
> launching games straight from the command line — it's all just there, in one
> client.

## 10. outro — Call to action

> So yeah — OpenNOW. It's free, it's open source, and it gets better literally
> every week. Grab it on GitHub, come hang out in the Discord, links are all in
> the description. Thanks for watching — see you in the cloud.
