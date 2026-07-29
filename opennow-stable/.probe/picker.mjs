import { connect, sleep } from "../scratch-cdp.mjs";
const cdp = await connect();
const waitFor = async (expr, ms = 40000) => { const d = Date.now()+ms; while (Date.now()<d) { if (await cdp.evaluate(expr)) return true; await sleep(400);} return false; };
if (await waitFor("!!document.querySelector('.console-gate')", 25000)) {
  await cdp.key("Enter", "Enter", { vk: 13 });
  await waitFor("!document.querySelector('.console-gate')");
}
await waitFor("!!document.console || document.querySelectorAll('.console-card').length > 0");
await sleep(3000);

// Find a multi-store game so the picker is reachable.
const idx = await cdp.evaluate([
  "(() => {",
  "  const cards = [...document.querySelectorAll('.console-row')[0].querySelectorAll('.console-card')];",
  "  return cards.length;",
  "})()",
]. join("\n"));
console.log("cards in row 0:", idx);

let opened = false;
for (let i = 0; i < 10 && !opened; i++) {
  await cdp.key("Enter", "Enter", { vk: 13 });
  await waitFor("!!document.console || !!document.querySelector('.console-details')", 6000);
  await sleep(700);
  const hasVariant = await cdp.evaluate("[...document.querySelectorAll('.console-details-actions .console-action')].some(b => /change store/i.test(b.textContent))");
  if (hasVariant) {
    await cdp.key("ArrowRight", "ArrowRight", { vk: 39 });
    await sleep(300);
    await cdp.key("Enter", "Enter", { vk: 13 });
    opened = await waitFor("!!document.querySelector('.console-store-picker')", 6000);
    if (opened) break;
  }
  await cdp.key("Escape", "Escape", { vk: 27 });
  await sleep(500);
  await cdp.key("ArrowRight", "ArrowRight", { vk: 39 });
  await sleep(400);
}
console.log("store picker opened:", opened);
if (opened) {
  await sleep(900);
  console.log("PICKER:", await cdp.evaluate([
    "JSON.stringify({",
    "  rows: [...document.querySelectorAll('.console-store-choice')].map(b => ({",
    "    name: b.querySelector('.console-store-choice-name')?.textContent,",
    "    owned: !!b.querySelector('.console-store-choice-owned'),",
    "    active: b.classList.contains('is-active'),",
    "    focused: b.classList.contains('is-focused'),",
    "    hasIcon: !!b.querySelector('.console-store-choice-icon svg'),",
    "  })),",
    "}, null, 2)",
  ].join("\n")));
  await cdp.shot(".probe/24-store-picker.png");
}
cdp.close();
