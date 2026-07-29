import { writeFileSync } from "node:fs";
const HOST = "http://127.0.0.1:9222";
export async function connect() {
  const targets = await fetch(`${HOST}/json/list`).then((r) => r.json());
  const target = targets.find((i) => i.type === "page" && i.title === "OpenNOW") ?? targets[0];
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 1; const pending = new Map();
  ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result); } };
  const send = (method, params = {}) => new Promise((resolve, reject) => { const i = id++; pending.set(i, { resolve, reject }); ws.send(JSON.stringify({ id: i, method, params })); });
  return {
    send, close: () => ws.close(),
    async evaluate(expression) { const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true, userGesture: true }); if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? "eval error"); return r.result.value; },
    async key(key, code, extra = {}) { const b = { key, code, windowsVirtualKeyCode: extra.vk, nativeVirtualKeyCode: extra.vk, text: extra.text }; await send("Input.dispatchKeyEvent", { type: extra.text ? "keyDown" : "rawKeyDown", ...b }); await send("Input.dispatchKeyEvent", { type: "keyUp", ...b }); },
    async shot(path) { const r = await send("Page.captureScreenshot", { format: "png", fromSurface: true, captureBeyondViewport: false }); writeFileSync(path, Buffer.from(r.data, "base64")); },
  };
}
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
