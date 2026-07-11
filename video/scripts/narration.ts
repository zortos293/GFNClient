/**
 * Narration lines per scene. Keep in sync with video/script.md.
 *
 * Written for the Eleven v3 model: square-bracket audio tags like [chuckles]
 * control delivery inline and are not spoken. Use them sparingly.
 */
export const NARRATION: Record<string, string> = {
  intro:
    "Okay, so this is OpenNOW. It's a free, open-source desktop client for GeForce NOW that me and the community have been building. Basically, you bring your own GeForce NOW account, and you get a way faster, way cleaner app... with a ton of stuff the official client just doesn't let you do. Let me just show you.",
  signin:
    "So getting in is super simple. You sign in with your normal NVIDIA account, through NVIDIA's actual login page — we never see your password, it all goes straight to their servers. [chuckles] And if you're lazy like me, just scan a QR code with your phone and you're in. Oh — and it does multiple accounts too, so you can hop between them with one click.",
  store:
    "Alright, this is the Store. It's literally the entire GeForce NOW catalog — thousands of games — and search is instant. Every card shows you which store the game is on: Steam, Epic, Ubisoft, GOG, Xbox... whatever. You can filter by store, by genre, by how you wanna play. And when you find something you own? You just hit play. That's it. The whole thing is fast, and it stays out of your way.",
  library:
    "This is your Library — every game you own, pulled straight from your connected store accounts, all in one place. You can filter by platform, by play type, or by input — keyboard and mouse, controller, even touch. Click on any game and you get all the details: developer, publisher, genres, what NVIDIA tech it runs. It's your whole cloud library... actually organized.",
  "settings-stream":
    "Okay, this is honestly my favorite part — the control you get. Open the Stream settings and everything's yours. Pick your server region by hand, or let the app ping every server and pick the best one for you. Resolution, frame rate, codec, max bitrate — all yours, no artificial caps. There's even live video filters — sharpening, saturation — that you can tweak while you're playing.",
  "settings-app":
    "And if you really wanna nerd out... there's an experimental native streamer. It's written in Rust, with GStreamer, DirectX backends, custom frame pacing — all built to squeeze out every last millisecond of latency. The app itself is super customizable too: light and dark themes, translucent U I, accent colors, a full controller mode for the couch... and it's translated into twelve languages.",
  launch:
    "Launching a game is exactly what you'd hope. One click on play, and it just handles everything — you get queued, your cloud rig spins up, the stream connects. You can literally watch each step happen... and you're in the game in like, seconds.",
  gameplay:
    "And once you're in, OpenNOW stays completely out of your way — but everything's one shortcut away. Hit F3, and you get a live stats overlay: bitrate, latency, packet loss, decode times — all real time. Control G opens the sidebar — that's your remaining playtime, your mic, screenshots, everything for the session. And when you're done, one shortcut ends the session... and you're right back in the app.",
  extras:
    "And there's a bunch of little stuff that adds up. Discord Rich Presence, so your friends see what you're playing. Anti-A F K, so your session doesn't die on you. Screenshots and recordings saved locally, Playnite integration, launching games straight from the command line... it's all just there, in one client.",
  outro:
    "So yeah — OpenNOW. It's free, it's open source, and it gets better literally every week. Grab it on GitHub, come hang out in the Discord — links are all in the description. Thanks for watching... and see you in the cloud.",
};
