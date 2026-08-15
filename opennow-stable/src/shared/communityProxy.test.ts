/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import {
  buildZortosCommunityProxyUrl,
  isZortosCommunityProxyUrl,
  ZORTOS_COMMUNITY_PROXY_HOST,
  ZORTOS_COMMUNITY_PROXY_PORT,
  ZORTOS_COMMUNITY_PROXY_PROVISION_URL,
} from "./communityProxy";

test("provisions against the Railway community proxy", () => {
  assert.equal(ZORTOS_COMMUNITY_PROXY_HOST, "altaria.proxy.rlwy.net");
  assert.equal(ZORTOS_COMMUNITY_PROXY_PORT, 51545);
  assert.equal(
    ZORTOS_COMMUNITY_PROXY_PROVISION_URL,
    "https://opennow-proxy-production.up.railway.app/api/public/proxy",
  );
  assert.equal(
    buildZortosCommunityProxyUrl("user", "p@ss"),
    "http://user:p%40ss@altaria.proxy.rlwy.net:51545",
  );
});

test("recognizes current and legacy community proxy URLs", () => {
  assert.equal(isZortosCommunityProxyUrl("http://user:pass@altaria.proxy.rlwy.net:51545"), true);
  assert.equal(isZortosCommunityProxyUrl("http://user:pass@opennow-proxy-tcp.zortos.me:3128"), true);
  assert.equal(isZortosCommunityProxyUrl("http://user:pass@217.76.50.166:3128"), true);
  assert.equal(isZortosCommunityProxyUrl("http://user:pass@proxy.example.com:8080"), false);
  assert.equal(isZortosCommunityProxyUrl(""), false);
});
