import { connect, sleep } from "../scratch-cdp.mjs";
const cdp = await connect();
const waitFor = async (expr, ms = 40000) => { const d = Date.now()+ms; while (Date.now()<d) { if (await cdp.evaluate(expr)) return true; await sleep(400);} return false; };
const probe = [
  "(() => {",
  "  const r = (s) => { const e = document.querySelector(s); if(!e) return null; const b = e.getBoundingClientRect(); return {t:Math.round(b.top),b:Math.round(b.bottom)}; };",
  "  const hint = r('.console-hint-bar'); const copy = r('.console-billboard-copy'); const head = r('.console-row-heading');",
  "  const card = document.querySelector('.console-card.is-focused') ?? document.querySelector('.console-card');",
  "  const track = card?.closest('.console-row-track');",
  "  const c = card?.getBoundingClientRect(), t = track?.getBoundingClientRect();",
  "  return JSON.stringify({",
  "    blackBar: innerHeight - hint.b,",
  "    copyOverlapsHeading: head.t < copy.b,",
  "    cardClipTop: c && t ? Math.max(0, Math.round(t.top - c.top)) : null,",
  "    cardClipBottom: c && t ? Math.max(0, Math.round(c.bottom - t.bottom)) : null,",
  "  });",
  "})()",
].join("\n");

await cdp.evaluate("[...document.querySelectorAll('.navbar button')].find(b => /Store/i.test(b.textContent))?.click()");
await waitFor("!!document.querySelector('.console-store') && document.querySelectorAll('.console-card').length > 0");
await sleep(3000);
await cdp.key("ArrowRight", "ArrowRight", { vk: 39 });
await sleep(700);
console.log("STORE:", await cdp.evaluate(probe));
await cdp.shot(".probe/22-store-final.png");

await cdp.key("Enter", "Enter", { vk: 13 });
await waitFor("!!document.querySelector('.console-details')", 8000);
await sleep(2500);
console.log("DETAILS:", await cdp.evaluate("JSON.stringify({ actions: [...document.querySelectorAll('.console-details-actions .console-action')].map(b=>b.textContent), shots: document.querySelectorAll('.console-details-thumb').length })"));
await cdp.shot(".probe/23-details-final.png");
await cdp.key("Escape", "Escape", { vk: 27 });
cdp.close();
