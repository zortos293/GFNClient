import { connect } from "../scratch-cdp.mjs";
const cdp = await connect();
console.log(await cdp.evaluate([
  "(() => {",
  "  const chain = [];",
  "  let el = document.querySelector('.console-page');",
  "  while (el && el !== document.documentElement) {",
  "    const b = el.getBoundingClientRect(); const cs = getComputedStyle(el);",
  "    chain.push({ cls: el.className.slice(0,44), top: Math.round(b.top), bottom: Math.round(b.bottom), h: Math.round(b.height),",
  "      display: cs.display, flex: cs.flex, padBottom: cs.paddingBottom, minH: cs.minHeight, overflow: cs.overflow });",
  "    el = el.parentElement;",
  "  }",
  "  return JSON.stringify({ viewport: innerHeight, hintBarBottom: Math.round(document.querySelector('.console-hint-bar').getBoundingClientRect().bottom), chain }, null, 2);",
  "})()",
].join("\n")));
cdp.close();
