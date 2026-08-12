import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import {
  fetchAndConsumeWithTimeout,
  readResponseTextWithLimit,
} from "./requestTimeout";

test("fetchAndConsumeWithTimeout keeps the timeout active while reading the body", async () => {
  const server = createServer((_request, response) => {
    response.writeHead(200, { "Content-Type": "application/json" });
    response.flushHeaders();
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address === "object");

  try {
    await assert.rejects(
      fetchAndConsumeWithTimeout(
        `http://127.0.0.1:${address.port}`,
        {},
        50,
        "Stalled response",
        (response) => response.text(),
      ),
      /Stalled response timed out after 50ms/,
    );
  } finally {
    await new Promise<void>((resolve, reject) => {
      server.close((error) => error ? reject(error) : resolve());
    });
  }
});

test("readResponseTextWithLimit stops after the configured byte limit", async () => {
  const response = new Response("0123456789".repeat(20_000));
  const body = await readResponseTextWithLimit(response, 64 * 1024);

  assert.equal(Buffer.byteLength(body, "utf8"), 64 * 1024);
  assert.equal(body, "0123456789".repeat(6_553) + "012345");
});
