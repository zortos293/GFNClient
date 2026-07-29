import { connect, sleep } from "../scratch-cdp.mjs";
const cdp = await connect();
const waitFor = async (expr, ms = 40000) => { const d = Date.now()+ms; while (Date.now()<d) { if (await cdp.evaluate(expr)) return true; await sleep(400);} return false; };
await cdp.evaluate("location.reload()");
if (await waitFor("!!document.querySelector('.console-gate')", 25000)) {
  await cdp.key("Enter", "Enter", { vk: 13 });
  await waitFor("!document.querySelector('.console-gate')");
}
await waitFor("document.querySelectorAll('.console-card').length > 0");
await sleep(3500);
console.log(await cdp.evaluate([
  "(() => {",
  "  const r = (s) => { const e = document.querySelector(s); if(!e) return null; const b = e.getBoundingClientRect(); return {t:Math.round(b.top),b:Math.round(b.bottom),h:Math.round(b.height)}; };",
  "  const scroll = document.querySelector('.console-scroll');",
  "  const rows = [...document.querySelectorAll('.console-row')];",
  "  const cb = document.querySelector('.console-card')?.getBoundingClientRect();",
  "  const track = document.querySelector('.console-row-track');",
  "  const hint = r('.console-hint-bar');",
  "  const fullyVisibleRows = rows.filter(el => { const b = el.getBoundingClientRect(); return b.bottom <= (hint?.t ?? innerHeight) + 1; }).length;",
  "  return JSON.stringify({",
  "    viewport: [innerWidth, innerHeight],",
  "    billboard: r('.console-billboard'),",
  "    billboardPctOfScroll: scroll ? Math.round((r('.console-billboard').h / scroll.clientHeight) * 100) : null,",
  "    scrollVisibleH: scroll?.clientHeight,",
  "    firstRow: r('.console-row'),",
  "    card: cb ? { w: Math.round(cb.width), h: Math.round(cb.height) } : null,",
  "    cardsPerScreen: track && cb ? Math.floor(track.clientWidth / (cb.width + 14)) : null,",
  "    fullyVisibleRows, totalRows: rows.length, hintBarTop: hint?.t,",
  "  }, null, 2);",
  "})()",
].join("\n")));
await cdp.shot(".probe/18-library-fit.png");
cdp.close();
