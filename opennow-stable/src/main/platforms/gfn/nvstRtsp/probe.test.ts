/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  collectRtspsEndpoints,
  rtspsUrlToWssUrl,
  selectPrimaryRtspsEndpoint,
} from "./probe";

test("selectPrimaryRtspsEndpoint prefers :322", () => {
  const selected = selectPrimaryRtspsEndpoint([
    "rtsps://host.example:48322",
    "rtsps://host.example:322",
  ]);
  assert.equal(selected, "rtsps://host.example:322");
});

test("rtspsUrlToWssUrl is host:port with no path (empty upgrade path is manual)", () => {
  assert.equal(
    rtspsUrlToWssUrl("rtsps://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322"),
    "wss://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322",
  );
});

test("collectRtspsEndpoints keeps both usage=14 paths", () => {
  const endpoints = collectRtspsEndpoints(
    [
      {
        usage: 14,
        port: 322,
        resourcePath: "rtsps://host.example:322",
      },
      {
        usage: 14,
        port: 48322,
        resourcePath: "rtsps://host.example:48322",
      },
      {
        usage: 2,
        port: 49006,
        resourcePath: null,
      },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});

test("collectRtspsEndpoints synthesizes from port when resourcePath missing", () => {
  const endpoints = collectRtspsEndpoints(
    [
      { usage: 14, port: 322, resourcePath: null },
      { usage: 14, port: 48322, resourcePath: null },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});
