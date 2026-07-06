import test from "node:test";
import assert from "node:assert/strict";

import { fetchLcarsGraphQl, postLcarsMutation } from "./lcarsGraphql";

test("LCARS persisted queries retry HTTP 400 with the full query text", async (t) => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ url: string; init?: RequestInit }> = [];

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    calls.push({ url, init });

    if (calls.length === 1) {
      return new Response("persisted query not found", { status: 400 });
    }

    return new Response(JSON.stringify({ data: { panels: [] } }), { status: 200 });
  }) as typeof fetch;

  const payload = await fetchLcarsGraphQl(
    "Main",
    { vpcId: "GFN-PC", locale: "en_US", panelNames: ["MAIN"] },
    "token",
  );

  assert.deepEqual(payload, { data: { panels: [] } });
  assert.equal(calls.length, 2);

  const firstUrl = new URL(calls[0].url);
  const firstExtensions = JSON.parse(firstUrl.searchParams.get("extensions") ?? "{}");
  assert.equal(firstUrl.origin + firstUrl.pathname, "https://games.geforce.com/graphql");
  assert.equal(firstUrl.searchParams.get("requestType"), "panels/MainV2");
  assert.equal(firstUrl.searchParams.has("query"), false);
  assert.equal(
    firstExtensions.persistedQuery.sha256Hash,
    "46ec15f267a056e7d5e46e629efa929529e5e7542a4850faece90b9f8fa5f810",
  );

  const secondUrl = new URL(calls[1].url);
  assert.match(secondUrl.searchParams.get("query") ?? "", /query GetGameSection/);
  assert.equal((calls[0].init?.headers as Record<string, string>)["Content-Type"], "application/graphql");
  assert.equal((calls[0].init?.headers as Record<string, string>).Authorization, "GFNJWT token");
});

test("LCARS named mutations post the registered mutation query", async (t) => {
  const originalFetch = globalThis.fetch;
  let capturedUrl = "";
  let capturedBody = "";

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  globalThis.fetch = (async (input, init) => {
    capturedUrl = String(input);
    capturedBody = String(init?.body);
    return new Response(JSON.stringify({ data: { addOwnedVariant: { app: { id: "app-1" } } } }), { status: 200 });
  }) as typeof fetch;

  await postLcarsMutation(
    "AddOwnedVariant",
    { cmsId: "123", locale: "en_US" },
    "token",
  );

  const body = JSON.parse(capturedBody) as { query?: string; variables?: Record<string, unknown> };
  assert.equal(capturedUrl, "https://apps.gxn.nvidia.com/graphql");
  assert.match(body.query ?? "", /mutation AddOwnedVariant/);
  assert.deepEqual(body.variables, { cmsId: "123", locale: "en_US" });
});
