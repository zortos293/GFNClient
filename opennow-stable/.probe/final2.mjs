import { connect, sleep } from "../scratch-cdp.mjs";
const cdp = await connect();
const waitFor = async (expr, ms = 40000) => { const d = Date.now()+ms; while (Date.now()<d) { if (await cdp.evaluate(expr)) return true; await sleep(400);} return false; };
await cdp.evaluate("[...document.querySelectorAll('.navbar button')].find(b => /Library/i.test(b.textContent))?.click()");
await waitFor("!!document.querySelector('.console-library') && document.querySelectorAll('.console-card').length > 0");
await sleep(3000);
console.log("LIBRARY:", await cdp.evaluate([
  "(() => {",
  "  const hint = document.querySelector('.console-hint-bar').getBoundingClientRect();",
  "  const rows = [...document.querySelectorAll('.console-row')];",
  "  return JSON.stringify({",
  "    rowTitles: [...document.querySelectorAll('.console-row-title')].map(e => e.textContent),",
  "    fullyVisible: rows.filter(el => el.getBoundingClientRect().bottom <= hint.top + 1).length,",
  "    peeking: rows.filter(el => { const b = el.getBoundingClientRect(); return b.top < hint.top && b.bottom > hint.top; }).length,",
  "  });",
  "})()",
].join("\n")));
await cdp.shot(".probe/19-library-fit.png");
await cdp.key("Enter", "Enter", { vk: 13 });
await waitFor("!!document.querySelector('.console-details')", 8000);
await sleep(2500);
console.log("DETAILS fits:", await cdp.evaluate([
  "(() => {",
  "  const body = document.querySelector('.console-details-body');",
  "  return JSON.stringify({ overflows: body.scrollHeight > body.clientHeight + 2, h: Math.round(body.clientHeight), content: Math.round(body.scrollHeight) });",
  "})()",
].join("\n")));
await cdp.shot(".probe/20-details-fit.png");
cdp.close();
