import assert from "node:assert/strict";
import test from "node:test";

import { isZoneHostname } from "./cloudmatchTransport";

test("isZoneHostname accepts NVIDIA CloudMatch domains and their subdomains", () => {
  assert.equal(isZoneHostname("cloudmatch.nvidiagrid.net"), true);
  assert.equal(isZoneHostname("np-ams-06.cloudmatchbeta.nvidiagrid.net"), true);
  assert.equal(isZoneHostname("NP-AMS-06.CLOUDMATCHBETA.NVIDIAGRID.NET."), true);
});

test("isZoneHostname rejects hostnames that only contain a CloudMatch domain substring", () => {
  assert.equal(isZoneHostname("cloudmatch.nvidiagrid.net.attacker.example"), false);
  assert.equal(isZoneHostname("attacker-cloudmatchbeta.nvidiagrid.net"), false);
  assert.equal(isZoneHostname("cloudmatchbeta.nvidiagrid.net.evil.test"), false);
});
