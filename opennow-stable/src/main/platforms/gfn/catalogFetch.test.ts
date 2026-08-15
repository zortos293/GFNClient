import test from "node:test";
import assert from "node:assert/strict";

import { createCatalogFetcher } from "./catalogFetch";

const proxyUrl = "http://proxy.example.com:8080";

test("catalog requests use the configured proxy when it is healthy", async () => {
  let proxyCalls = 0;
  let directCalls = 0;
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: async () => {
      proxyCalls += 1;
      return new Response("proxied", { status: 200 });
    },
    directFetch: async () => {
      directCalls += 1;
      return new Response("direct", { status: 200 });
    },
  });

  const response = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);

  assert.equal(await response.text(), "proxied");
  assert.equal(proxyCalls, 1);
  assert.equal(directCalls, 0);
});

test("catalog requests fall back to direct access and temporarily bypass a failed proxy", async () => {
  let now = 1_000;
  let proxyCalls = 0;
  let directCalls = 0;
  const warnings: string[] = [];
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: async () => {
      proxyCalls += 1;
      throw new Error("proxy unavailable");
    },
    directFetch: async () => {
      directCalls += 1;
      return new Response("direct", { status: 200 });
    },
    now: () => now,
    retryAfterMs: 60_000,
    warn: (message) => warnings.push(message),
  });

  const first = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);
  const second = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);
  now += 60_001;
  const third = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);

  assert.equal(await first.text(), "direct");
  assert.equal(await second.text(), "direct");
  assert.equal(await third.text(), "direct");
  assert.equal(proxyCalls, 2);
  assert.equal(directCalls, 3);
  assert.equal(warnings.length, 2);
  assert.match(warnings[0], /proxy unavailable/);
});

test("catalog requests fall back on transient proxy responses", async () => {
  let directCalls = 0;
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: async () => new Response("upstream unavailable", { status: 503 }),
    directFetch: async () => {
      directCalls += 1;
      return new Response("direct", { status: 200 });
    },
    warn: () => undefined,
  });

  const response = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);

  assert.equal(await response.text(), "direct");
  assert.equal(directCalls, 1);
});

test("catalog requests preserve GraphQL protocol responses from the proxy", async () => {
  let directCalls = 0;
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: async () => new Response("persisted query not found", { status: 400 }),
    directFetch: async () => {
      directCalls += 1;
      return new Response("direct", { status: 200 });
    },
  });

  const response = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);

  assert.equal(response.status, 400);
  assert.equal(directCalls, 0);
});

test("catalog requests time out an unresponsive proxy before falling back", async () => {
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: (_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => reject(init.signal?.reason), { once: true });
    }),
    directFetch: async () => new Response("direct", { status: 200 }),
    proxyTimeoutMs: 5,
    warn: () => undefined,
  });

  const response = await fetchCatalog("https://games.geforce.com/graphql", undefined, proxyUrl);

  assert.equal(await response.text(), "direct");
});

test("catalog requests do not retry directly after caller cancellation", async () => {
  let directCalls = 0;
  const controller = new AbortController();
  controller.abort(new Error("cancelled"));
  const fetchCatalog = createCatalogFetcher({
    proxyFetch: async (_input, init) => {
      throw init?.signal?.reason;
    },
    directFetch: async () => {
      directCalls += 1;
      return new Response("direct", { status: 200 });
    },
  });

  await assert.rejects(
    fetchCatalog("https://games.geforce.com/graphql", { signal: controller.signal }, proxyUrl),
    /cancelled/,
  );
  assert.equal(directCalls, 0);
});
