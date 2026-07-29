import { connect, sleep } from "../scratch-cdp.mjs";
const cdp = await connect();
const waitFor = async (expr, ms = 40000) => { const d = Date.now()+ms; while (Date.now()<d) { if (await cdp.evaluate(expr)) return true; await sleep(400);} return false; };
await cdp.evaluate("location.reload()");
if (await waitFor("!!document.querySelector('.console-gate')", 25000)) {
  await cdp.key("Enter", "Enter", { vk: 13 });
  await waitFor("!document.querySelector('.console-gate')");
}
await cdp.evaluate("[...document.querySelectorAll('.navbar button')].find(b => /Library/i.test(b.textContent))?.click()");
await waitFor("!!document.querySelector('.console-library') && document.querySelectorAll('.console-card').length > 0");
await sleep(3500);
console.log(await cdp.evaluate([
  "(() => {",
  "  const r = (s) => { const e = document.querySelector(s); if(!e) return null; const b = e.getBoundingClientRect(); return {t:Math.round(b.top),b:Math.round(b.bottom),h:Math.round(b.height)}; };",
  "  const bill = r('.console-billboard'), copy = r('.console-billboard-copy'), head = r('.console-row-heading'), hint = r('.console-hint-bar');",
  "  return JSON.stringify({",
  "    viewport: innerHeight,",
  "    blackBarBelowHintBar: innerHeight - hint.b,",
  "    billboard: bill, copy, firstRowHeading: head,",
  "    rowsBleedOntoBillboard: Math.max(0, bill.b - head.t),",
  "    copyClearsRows: head.t - copy.b,",
  "  }, null, 2);",
  "})()",
].join("\n")));
await cdp.shot(".probe/21-fade.png");
cdp.close();
