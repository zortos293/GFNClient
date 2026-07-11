/**
 * Captures real OpenNOW app footage for the walkthrough video.
 *
 * Launches the built Electron app (opennow-stable) via Playwright's Electron
 * driver, drives the real UI with human-paced input, and records the window
 * region with ffmpeg (gdigrab) at 1920x1080@60. One MP4 per scene is written
 * to video/public/footage/.
 *
 * Usage:
 *   npm run capture                  # all scenes
 *   npm run capture -- --scenes=store,library
 *   npm run capture -- --skip-launch # skip session launch/gameplay scenes
 */
import { _electron, type ElectronApplication, type Page, type Locator } from "playwright-core";
import ffmpegPath from "ffmpeg-static";
import { spawn, type ChildProcess } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "..", "..");
const appDir = join(repoRoot, "opennow-stable");
const electronExe = join(appDir, "node_modules", "electron", "dist", "electron.exe");
const footageDir = join(repoRoot, "video", "public", "footage");

const CONTENT_W = 1920;
const CONTENT_H = 1080;
const WINDOW_X = 320;
const WINDOW_Y = 120;

const argv = process.argv.slice(2);
const scenesArg = argv.find((a) => a.startsWith("--scenes="));
const skipLaunch = argv.includes("--skip-launch");
const requestedScenes = scenesArg
  ? scenesArg.split("=")[1].split(",").map((s) => s.trim())
  : null;

function log(msg: string): void {
  console.log(`[capture ${new Date().toISOString().slice(11, 19)}] ${msg}`);
}

// ── ffmpeg recorder ───────────────────────────────────────────────────────────

class Recorder {
  private proc: ChildProcess | null = null;
  private currentFile = "";

  start(sceneId: string, bounds: { x: number; y: number }): void {
    if (this.proc) throw new Error("Recorder already running");
    this.currentFile = join(footageDir, `${sceneId}.mp4`);
    this.proc = spawn(
      ffmpegPath as unknown as string,
      [
        "-y",
        "-f", "gdigrab",
        "-framerate", "60",
        "-offset_x", String(bounds.x),
        "-offset_y", String(bounds.y),
        "-video_size", `${CONTENT_W}x${CONTENT_H}`,
        "-draw_mouse", "0",
        "-i", "desktop",
        "-c:v", "libx264",
        "-preset", "veryfast",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-movflags", "+faststart",
        this.currentFile,
      ],
      { stdio: ["pipe", "ignore", "pipe"] },
    );
    let errBuf = "";
    this.proc.stderr?.on("data", (d: Buffer) => {
      errBuf = (errBuf + d.toString()).slice(-2000);
    });
    this.proc.on("exit", (code) => {
      if (code !== 0 && code !== 255) {
        console.error(`ffmpeg exited with ${code} for ${this.currentFile}\n${errBuf}`);
      }
    });
    log(`recording -> ${sceneId}.mp4`);
  }

  async stop(): Promise<void> {
    const proc = this.proc;
    if (!proc) return;
    this.proc = null;
    proc.stdin?.write("q");
    await new Promise<void>((resolveExit) => {
      const t = setTimeout(() => {
        proc.kill();
        resolveExit();
      }, 8000);
      proc.on("exit", () => {
        clearTimeout(t);
        resolveExit();
      });
    });
    log(`stopped recording`);
  }
}

// ── Human-paced input helpers ─────────────────────────────────────────────────

const CURSOR_OVERLAY_JS = `
(() => {
  if (document.getElementById('__demo_cursor')) return;
  const c = document.createElement('div');
  c.id = '__demo_cursor';
  c.style.cssText = 'position:fixed;left:960px;top:540px;width:22px;height:22px;z-index:2147483647;pointer-events:none;filter:drop-shadow(0 2px 5px rgba(0,0,0,.55));';
  c.innerHTML = '<svg width="22" height="22" viewBox="0 0 24 24"><path d="M5 2 L5 19 L9.5 15 L12.5 21.5 L15 20.3 L12 14 L18 14 Z" fill="#ffffff" stroke="#1b1b1f" stroke-width="1.4" stroke-linejoin="round"/></svg>';
  document.body.appendChild(c);
  window.addEventListener('mousemove', (e) => {
    c.style.left = e.clientX + 'px';
    c.style.top = e.clientY + 'px';
  }, true);
  window.addEventListener('mousedown', (e) => {
    const r = document.createElement('div');
    r.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;border-radius:50%;border:2px solid rgba(255,255,255,.85);width:10px;height:10px;transform:translate(-50%,-50%);transition:all .45s ease-out;opacity:.9;left:' + e.clientX + 'px;top:' + e.clientY + 'px;';
    document.body.appendChild(r);
    requestAnimationFrame(() => {
      r.style.width = '44px';
      r.style.height = '44px';
      r.style.opacity = '0';
    });
    setTimeout(() => r.remove(), 500);
  }, true);
})();
`;

/**
 * Blurs on-screen PII so it never lands in the recorded footage:
 * account emails in the navbar dropdown and any input whose value looks like
 * a URL/credential (e.g. the session proxy URL).
 */
const PRIVACY_MASK_JS = `
(() => {
  if (document.getElementById('__demo_privacy')) return;
  const s = document.createElement('style');
  s.id = '__demo_privacy';
  s.textContent = '.navbar-account-item-email{filter:blur(5px)!important} .navbar-account-item-name{filter:blur(5px)!important}';
  document.head.appendChild(s);
  const mask = () => {
    for (const i of document.querySelectorAll('input')) {
      if (i.type === 'password') continue;
      if (/:\\/\\/|@/.test(i.value || '')) i.style.filter = 'blur(7px)';
    }
  };
  mask();
  setInterval(mask, 400);
})();
`;

class Demo {
  private pos = { x: CONTENT_W / 2, y: CONTENT_H / 2 };

  constructor(private page: Page) {}

  async installCursor(): Promise<void> {
    await this.page.evaluate(CURSOR_OVERLAY_JS);
    await this.page.evaluate(PRIVACY_MASK_JS);
  }

  pause(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }

  async moveTo(x: number, y: number): Promise<void> {
    const dist = Math.hypot(x - this.pos.x, y - this.pos.y);
    const steps = Math.min(70, Math.max(14, Math.round(dist / 14)));
    await this.page.mouse.move(x, y, { steps });
    this.pos = { x, y };
  }

  async hover(loc: Locator): Promise<void> {
    const box = await loc.boundingBox();
    if (!box) throw new Error("hover target has no bounding box");
    // Aim slightly off-center so movement looks natural.
    const x = box.x + box.width * (0.42 + Math.random() * 0.16);
    const y = box.y + box.height * (0.42 + Math.random() * 0.16);
    await this.moveTo(x, y);
  }

  async click(loc: Locator, settleMs = 320): Promise<void> {
    await this.hover(loc);
    await this.pause(settleMs);
    await this.page.mouse.down();
    await this.pause(70);
    await this.page.mouse.up();
  }

  async typeInto(loc: Locator, text: string): Promise<void> {
    await this.click(loc);
    await this.pause(300);
    await this.page.keyboard.type(text, { delay: 80 });
  }

  async clearInput(): Promise<void> {
    await this.page.keyboard.press("Control+a");
    await this.pause(150);
    await this.page.keyboard.press("Backspace");
  }

  /** Smooth scroll by dy pixels in gentle wheel steps at the current position. */
  async scroll(dy: number): Promise<void> {
    const step = dy > 0 ? 110 : -110;
    const count = Math.max(1, Math.round(Math.abs(dy) / 110));
    for (let i = 0; i < count; i++) {
      await this.page.mouse.wheel(0, step);
      await this.pause(90);
    }
  }
}

// ── App bootstrap ─────────────────────────────────────────────────────────────

async function launchApp(): Promise<{
  app: ElectronApplication;
  page: Page;
  bounds: { x: number; y: number };
}> {
  if (!existsSync(electronExe)) {
    throw new Error(`Electron binary not found at ${electronExe}. Run npm install in opennow-stable.`);
  }
  if (!existsSync(join(appDir, "dist-electron", "main", "index.js"))) {
    throw new Error("Built app not found. Run: npm --prefix opennow-stable run build");
  }

  log("launching OpenNOW...");
  const app = await _electron.launch({
    executablePath: electronExe,
    args: ["."],
    cwd: appDir,
    timeout: 60_000,
  });

  const page = await app.firstWindow({ timeout: 60_000 });
  await page.waitForLoadState("domcontentloaded");

  const bounds = await app.evaluate(
    async ({ BrowserWindow }, { w, h, x, y }) => {
      const win = BrowserWindow.getAllWindows()[0];
      win.unmaximize();
      win.setFullScreen(false);
      win.setContentSize(w, h);
      win.setPosition(x, y);
      win.setAlwaysOnTop(true, "screen-saver");
      win.show();
      win.focus();
      const cb = win.getContentBounds();
      return { x: cb.x, y: cb.y, width: cb.width, height: cb.height };
    },
    { w: CONTENT_W, h: CONTENT_H, x: WINDOW_X, y: WINDOW_Y },
  );

  if (bounds.width !== CONTENT_W || bounds.height !== CONTENT_H) {
    log(`warning: content bounds are ${bounds.width}x${bounds.height}, expected ${CONTENT_W}x${CONTENT_H}`);
  }
  log(`window content at (${bounds.x}, ${bounds.y}) size ${bounds.width}x${bounds.height}`);
  return { app, page, bounds: { x: bounds.x, y: bounds.y } };
}

async function waitForSignedIn(page: Page): Promise<boolean> {
  // The login screen doubles as the "restoring session" splash at startup, so
  // wait for the navbar itself; it appears once the persisted session loads.
  try {
    await page.locator(".navbar-nav").waitFor({ state: "visible", timeout: 60_000 });
    return true;
  } catch {
    return false;
  }
}

/**
 * Brings the app back to an idle, navbar-visible state between scenes:
 * cancels a pending queue, exits an active stream, and closes stray modals.
 * Runs off-camera, so it uses direct (non-humanized) interactions.
 */
async function recoverToIdle(page: Page): Promise<void> {
  const cancelQueue = page.locator("button.sload-cancel").first();
  if (await cancelQueue.isVisible().catch(() => false)) {
    log("recovery: cancelling pending queue");
    await cancelQueue.click().catch(() => {});
    await page.waitForTimeout(3000);
  }

  const hasStreamVideo = (await page.locator("video.sv-video").count()) > 0;
  const streaming =
    hasStreamVideo &&
    (await page
      .locator("video.sv-video")
      .evaluate((v: HTMLVideoElement) => v.videoWidth > 0)
      .catch(() => false));
  if (streaming) {
    log("recovery: exiting active stream");
    await page.keyboard.press("Control+Shift+q");
    const confirm = page.locator("button.sv-exit-btn-confirm").first();
    if (await confirm.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false)) {
      await confirm.click().catch(() => {});
    }
  }

  for (let i = 0; i < 2; i++) {
    if (await page.locator(".navbar-nav").isVisible().catch(() => false)) break;
    await page.keyboard.press("Escape");
    await page.waitForTimeout(1200);
  }
  await page
    .locator(".navbar-nav")
    .waitFor({ state: "visible", timeout: 30_000 })
    .catch(() => log("recovery: navbar still not visible"));
}

// ── Scenes ────────────────────────────────────────────────────────────────────

type SceneFn = (page: Page, demo: Demo) => Promise<void>;

const navButton = (page: Page, label: string) =>
  page.locator(`.navbar-link:has-text("${label}")`).first();

async function sceneStore(page: Page, demo: Demo): Promise<void> {
  await demo.click(navButton(page, "Store"));
  await page.locator(".game-card").first().waitFor({ state: "visible", timeout: 90_000 });
  await demo.pause(2200);

  // Browse: hover a few cards, gentle scroll.
  const cards = page.locator(".game-card");
  const count = Math.min(await cards.count(), 4);
  for (let i = 0; i < count; i++) {
    await demo.hover(cards.nth(i));
    await demo.pause(850);
  }
  await demo.scroll(900);
  await demo.pause(1400);
  await demo.scroll(700);
  await demo.pause(1400);
  await demo.scroll(-1600);
  await demo.pause(900);

  // Search.
  const search = page.locator("input.home-search-input");
  await demo.typeInto(search, "cyberpunk");
  await demo.pause(2400);
  const firstResult = page.locator(".game-card").first();
  if (await firstResult.isVisible()) {
    await demo.hover(firstResult);
    await demo.pause(1600);
  }
  await demo.click(search);
  await demo.clearInput();
  await demo.pause(1200);

  // Filters dropdown.
  const filterTrigger = page.locator("summary.home-filter-dropdown-trigger").first();
  if (await filterTrigger.isVisible()) {
    await demo.click(filterTrigger);
    await demo.pause(1500);
    const chips = page.locator("button.home-filter-chip");
    const chipCount = Math.min(await chips.count(), 3);
    for (let i = 0; i < chipCount; i++) {
      await demo.hover(chips.nth(i));
      await demo.pause(650);
    }
    if (chipCount > 0) {
      await demo.click(chips.first());
      await demo.pause(1800);
      await demo.click(chips.first()); // deselect again
      await demo.pause(800);
    }
    await page.keyboard.press("Escape");
    await demo.pause(600);
  }
  await demo.scroll(500);
  await demo.pause(1500);
}

async function sceneLibrary(page: Page, demo: Demo): Promise<void> {
  await demo.click(navButton(page, "Library"));
  await page.locator("input.library-search-input").waitFor({ state: "visible", timeout: 60_000 });
  await demo.pause(2000);

  const cards = page.locator(".game-card");
  try {
    await cards.first().waitFor({ state: "visible", timeout: 30_000 });
  } catch {
    log("library appears empty; capturing empty state");
  }

  const count = Math.min(await cards.count(), 4);
  for (let i = 0; i < count; i++) {
    await demo.hover(cards.nth(i));
    await demo.pause(1100);
  }
  await demo.scroll(700);
  await demo.pause(2200);
  await demo.scroll(500);
  await demo.pause(1800);
  await demo.scroll(-1200);
  await demo.pause(1400);

  // Select a game to show details. Click the title strip, not the card
  // center: hovering the artwork reveals a Play overlay button there and
  // clicking it would launch a real session.
  if (count > 0) {
    const title = cards.first().locator(".game-card-title").first();
    if (await title.isVisible().catch(() => false)) {
      await demo.click(title);
      await demo.pause(3000);
    }
  }

  // Filters.
  const filterTrigger = page.locator("summary.library-filter-dropdown-trigger, .library-filter-dropdown summary").first();
  if (await filterTrigger.isVisible().catch(() => false)) {
    await demo.click(filterTrigger);
    await demo.pause(2200);
    const chips = page.locator("button.library-filter-chip");
    const chipCount = Math.min(await chips.count(), 4);
    for (let i = 0; i < chipCount; i++) {
      await demo.hover(chips.nth(i));
      await demo.pause(550);
    }
    await page.keyboard.press("Escape");
    await demo.pause(800);
  }
  await demo.pause(1200);
}

async function openSettingsSection(page: Page, demo: Demo, section: string): Promise<void> {
  const overlay = page.locator('.animated-modal-overlay[aria-label="Settings"], .settings-modal, .settings-sidebar');
  if (!(await overlay.first().isVisible().catch(() => false))) {
    await demo.click(navButton(page, "Settings"));
    await page.locator(".settings-nav-item").first().waitFor({ state: "visible", timeout: 30_000 });
    await demo.pause(1200);
  }
  await demo.click(page.locator(`.settings-nav-item:has-text("${section}")`).first());
  await demo.pause(1500);
}

async function closeSettings(page: Page, demo: Demo): Promise<void> {
  const closeBtn = page.locator("button.settings-modal-close").first();
  if (await closeBtn.isVisible().catch(() => false)) {
    await demo.click(closeBtn);
  } else {
    await page.keyboard.press("Escape");
  }
  await demo.pause(900);
}

async function sceneSettingsStream(page: Page, demo: Demo): Promise<void> {
  await openSettingsSection(page, demo, "Stream");

  const content = page.locator(".settings-content").first();
  await demo.hover(content);
  await demo.pause(2600); // dwell on Region section at the top

  // Open (and close without changing) the video dropdowns.
  const dropdowns = content.locator("button.select-dropdown__trigger");
  const ddCount = await dropdowns.count();
  for (let i = 0; i < Math.min(ddCount, 2); i++) {
    const dd = dropdowns.nth(i);
    if (await dd.isVisible().catch(() => false)) {
      await demo.click(dd);
      await demo.pause(2400);
      await page.keyboard.press("Escape");
      await demo.pause(1000);
    }
  }

  await demo.scroll(450);
  await demo.pause(2600);
  await demo.scroll(450);
  await demo.pause(2600);

  // Hover a few toggles/rows to give the eye an anchor.
  const rows = content.locator(".settings-row, label, input[type=checkbox]");
  const rowCount = Math.min(await rows.count(), 3);
  for (let i = 0; i < rowCount; i++) {
    if (await rows.nth(i).isVisible().catch(() => false)) {
      await demo.hover(rows.nth(i));
      await demo.pause(1400);
    }
  }

  await demo.scroll(450);
  await demo.pause(2600);
  await demo.scroll(450);
  await demo.pause(2800);
  await demo.scroll(400);
  await demo.pause(3200);
}

async function sceneSettingsApp(page: Page, demo: Demo): Promise<void> {
  await openSettingsSection(page, demo, "Native Streamer");
  const content = page.locator(".settings-content").first();
  await demo.hover(content);
  await demo.pause(2600);
  await demo.scroll(400);
  await demo.pause(3200);
  await demo.scroll(300);
  await demo.pause(2600);

  await openSettingsSection(page, demo, "Interface");
  await demo.hover(content);
  await demo.pause(2400);
  await demo.scroll(400);
  await demo.pause(2600);
  await demo.scroll(400);
  await demo.pause(2600);
  await demo.scroll(400);
  await demo.pause(2400);
  await demo.scroll(-400);
  await demo.pause(2200);

  await closeSettings(page, demo);
  await demo.pause(1000);
}

async function sceneExtras(page: Page, demo: Demo): Promise<void> {
  // Playtime / subscription details modal.
  const chip = page.locator("button.navbar-subscription-chip").first();
  if (await chip.isVisible().catch(() => false)) {
    await demo.click(chip);
    await demo.pause(3200);
    const close = page.locator("button.navbar-modal-close").first();
    if (await close.isVisible().catch(() => false)) {
      await demo.click(close);
    } else {
      await page.keyboard.press("Escape");
    }
    await demo.pause(900);
  }

  // Account dropdown (multi-account UI).
  const user = page.locator("button.navbar-user--clickable").first();
  if (await user.isVisible().catch(() => false)) {
    await demo.click(user);
    await demo.pause(3600);
    await page.keyboard.press("Escape");
    await demo.pause(1000);
  }

  // About section (updates, what's new).
  await openSettingsSection(page, demo, "About");
  const content = page.locator(".settings-content").first();
  await demo.hover(content);
  await demo.scroll(400);
  await demo.pause(2200);
  await closeSettings(page, demo);
  await demo.pause(600);
}

/** Returns true if the stream reached the live/streaming state. */
async function sceneLaunch(page: Page, demo: Demo): Promise<boolean> {
  // A lingering active cloud session would trigger a native session-conflict
  // dialog on launch, which cannot be driven via CDP. Terminate it first.
  const terminate = page.locator("button.navbar-session-terminate").first();
  if (await terminate.isVisible().catch(() => false)) {
    log("terminating stale active cloud session before launch");
    await terminate.click().catch(() => {});
    await terminate.waitFor({ state: "hidden", timeout: 45_000 }).catch(() => {});
    await demo.pause(2000);
  }

  // Prefer the library so we launch a game the account owns.
  await demo.click(navButton(page, "Library"));
  await page.locator("input.library-search-input").waitFor({ state: "visible", timeout: 60_000 });
  let card = page.locator(".game-card").first();
  try {
    await card.waitFor({ state: "visible", timeout: 20_000 });
  } catch {
    log("no library games; using store");
    await demo.click(navButton(page, "Store"));
    card = page.locator(".game-card").first();
    await card.waitFor({ state: "visible", timeout: 60_000 });
  }

  await demo.hover(card);
  await demo.pause(900);
  const playBtn = card.locator(".game-card-play-button");
  await playBtn.waitFor({ state: "visible", timeout: 10_000 });
  await demo.click(playBtn);

  // Free tier: server select dialog may appear.
  const serverDialog = page.locator("#queue-server-select-title");
  const appeared = await serverDialog
    .waitFor({ state: "visible", timeout: 15_000 })
    .then(() => true)
    .catch(() => false);
  if (appeared) {
    log("server select dialog shown");
    // Let the ping/queue data populate on camera.
    await page
      .locator('div[role="dialog"] >> text=Recommended')
      .waitFor({ state: "visible", timeout: 45_000 })
      .catch(() => {});
    await demo.pause(6000);
    await demo.click(page.getByRole("button", { name: "Launch", exact: true }));
  }

  // Queue / setup overlay.
  await page.locator(".sload").waitFor({ state: "visible", timeout: 30_000 });
  log("in queue; waiting for stream (max 5 min)...");

  const deadline = Date.now() + 5 * 60_000;
  let streaming = false;
  while (Date.now() < deadline) {
    streaming = await page
      .locator("video.sv-video")
      .evaluate((v: HTMLVideoElement) => v.videoWidth > 0)
      .catch(() => false);
    if (streaming) break;
    const failed = !(await page.locator(".sload, .sv").first().isVisible().catch(() => false));
    if (failed) break;
    await demo.pause(3000);
  }

  if (!streaming) {
    log("stream did not start in time; cancelling");
    const cancel = page.locator("button.sload-cancel").first();
    if (await cancel.isVisible().catch(() => false)) {
      await demo.click(cancel);
      await demo.pause(2000);
    }
    return false;
  }
  log("stream is live");
  await demo.pause(4000); // capture the Connected splash
  return true;
}

async function sceneGameplay(page: Page, demo: Demo): Promise<void> {
  await demo.pause(3000);

  // Move the mouse a little so in-game camera reacts (if applicable).
  await demo.moveTo(CONTENT_W / 2 + 200, CONTENT_H / 2 + 60);
  await demo.pause(1500);
  await demo.moveTo(CONTENT_W / 2 - 150, CONTENT_H / 2 - 40);
  await demo.pause(2000);

  // Stats overlay.
  await page.keyboard.press("F3");
  await demo.pause(7000);
  await page.keyboard.press("F3");
  await demo.pause(1500);

  // Stream sidebar.
  await page.keyboard.press("Control+g");
  await demo.pause(6000);
  await page.keyboard.press("Escape");
  await demo.pause(2500);

  // Exit via the stop-stream shortcut -> confirmation dialog.
  await page.keyboard.press("Control+Shift+q");
  const confirmBtn = page.locator("button.sv-exit-btn-confirm").first();
  const confirmed = await confirmBtn
    .waitFor({ state: "visible", timeout: 10_000 })
    .then(() => true)
    .catch(() => false);
  if (confirmed) {
    await demo.pause(1800);
    await demo.click(confirmBtn);
  }
  await page.locator(".navbar-nav").waitFor({ state: "visible", timeout: 60_000 }).catch(() => {});
  await demo.pause(2500);
}

async function sceneSignin(page: Page, demo: Demo): Promise<void> {
  // Uses the "Add account" flow so the persisted session is untouched;
  // restarting the app afterwards restores the signed-in state.
  if (!(await page.locator(".login-screen").isVisible().catch(() => false))) {
    const user = page.locator("button.navbar-user--clickable").first();
    await demo.click(user);
    await demo.pause(1200);
    await demo.click(page.locator('#navbar-account-dropdown >> text="Add account"').first());
    await page.locator(".login-screen").waitFor({ state: "visible", timeout: 20_000 });
  }
  await demo.pause(4500);

  // Show the provider selector.
  const providerField = page.locator(".login-field").first();
  if (await providerField.isVisible().catch(() => false)) {
    await demo.hover(providerField);
    await demo.pause(2600);
  }

  // QR login (shows a real QR code; we never complete it).
  const qrBtn = page.locator('button:has-text("Sign in with QR code")').first();
  if (await qrBtn.isVisible().catch(() => false)) {
    await demo.click(qrBtn);
    await page
      .locator(".login-qr-code img")
      .waitFor({ state: "visible", timeout: 30_000 })
      .catch(() => {});
    await demo.pause(12_000);
    const cancelQr = page.locator('button:has-text("Cancel QR login")').first();
    if (await cancelQr.isVisible().catch(() => false)) {
      await demo.click(cancelQr);
      await demo.pause(1000);
    }
  }
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main(): Promise<void> {
  if (!ffmpegPath) throw new Error("ffmpeg-static did not provide a binary");
  mkdirSync(footageDir, { recursive: true });

  const { app, page, bounds } = await launchApp();
  const demo = new Demo(page);
  const recorder = new Recorder();
  const results: Record<string, "ok" | "skipped" | "failed"> = {};

  const signedIn = await waitForSignedIn(page);
  log(signedIn ? "signed-in session restored" : "no session; only the sign-in scene can be captured");
  await demo.installCursor();
  await demo.pause(1500);

  type SceneDef = { id: string; fn: SceneFn; needsAuth: boolean };
  let streamReached = false;

  const scenes: SceneDef[] = [
    { id: "store", fn: sceneStore, needsAuth: true },
    { id: "library", fn: sceneLibrary, needsAuth: true },
    { id: "settings-stream", fn: sceneSettingsStream, needsAuth: true },
    { id: "settings-app", fn: sceneSettingsApp, needsAuth: true },
    { id: "extras", fn: sceneExtras, needsAuth: true },
    ...(skipLaunch
      ? []
      : [
          {
            id: "launch",
            fn: async (p: Page, d: Demo) => {
              streamReached = await sceneLaunch(p, d);
            },
            needsAuth: true,
          },
          {
            id: "gameplay",
            fn: async (p: Page, d: Demo) => {
              if (!streamReached) throw new Error("skip: stream not reached");
              await sceneGameplay(p, d);
            },
            needsAuth: true,
          },
        ]),
    // Last: temporarily leaves the session (in-memory only).
    { id: "signin", fn: sceneSignin, needsAuth: true },
  ];

  for (const scene of scenes) {
    if (requestedScenes && !requestedScenes.includes(scene.id)) continue;
    if (scene.needsAuth && !signedIn && scene.id !== "signin") {
      results[scene.id] = "skipped";
      log(`skipping ${scene.id} (not signed in)`);
      continue;
    }
    log(`--- scene: ${scene.id} ---`);
    try {
      if (scene.id !== "gameplay") {
        await recoverToIdle(page);
      }
      await demo.installCursor();
      recorder.start(scene.id, bounds);
      await demo.pause(600);
      await scene.fn(page, demo);
      await demo.pause(800);
      await recorder.stop();
      results[scene.id] = "ok";
    } catch (err) {
      await recorder.stop();
      results[scene.id] = "failed";
      log(`scene ${scene.id} failed: ${(err as Error).message}`);
    }
  }

  writeFileSync(
    join(footageDir, "manifest.json"),
    JSON.stringify({ capturedAt: new Date().toISOString(), results }, null, 2),
  );
  log(`results: ${JSON.stringify(results)}`);

  await app.close().catch(() => {});
  log("done");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
