import { connect } from "../scratch-cdp.mjs";
const cdp = await connect();
console.log(await cdp.evaluate([
  "(() => {",
  "  const r = (s) => { const e = document.querySelector(s); if(!e) return null; const b = e.getBoundingClientRect(); return {t:Math.round(b.top),b:Math.round(b.bottom),h:Math.round(b.height)}; };",
  "  const scroll = document.querySelector('.console-scroll');",
  "  const rows = [...document.querySelectorAll('.console-row')];",
  "  const card = document.querySelector('.console-card');",
  "  const cb = card?.getBoundingClientRect();",
  "  const track = document.querySelector('.console-row-track');",
  "  return JSON.stringify({",
  "    viewport: [innerWidth, innerHeight],",
  "    navbarBottom: Math.round(document.querySelector('.navbar')?.getBoundingClientRect().bottom ?? 0),",
  "    billboard: r('.console-billboard'),",
  "    hintBar: r('.console-hint-bar'),",
  "    scrollVisibleH: scroll ? Math.round(scroll.clientHeight) : null,",
  "    scrollContentH: scroll ? Math.round(scroll.scrollHeight) : null,",
  "    firstRow: r('.console-row'),",
  "    card: cb ? { w: Math.round(cb.width), h: Math.round(cb.height) } : null,",
  "    cardsPerScreen: track ? Math.floor(track.clientWidth / (cb.width + 16)) : null,",
  "    rowsVisible: rows.filter(el => { const b = el.getBoundingClientRect(); return b.top < innerHeight && b.bottom > 0; }).length,",
  "    totalRows: rows.length,",
  "  }, null, 2);",
  "})()",
].join("\n")));
cdp.close();
