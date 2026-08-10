import assert from "node:assert/strict";
import test from "node:test";

import {
  isZoneHostname,
  normalizeTrustedCloudMatchBaseUrl,
} from "./cloudmatchTransport";

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

test("trusted CloudMatch endpoints require a clean NVIDIA HTTPS origin", () => {
  assert.equal(
    normalizeTrustedCloudMatchBaseUrl("https://NP-AMS-06.CLOUDMATCHBETA.NVIDIAGRID.NET./"),
    "https://np-ams-06.cloudmatchbeta.nvidiagrid.net",
  );
  assert.equal(
    normalizeTrustedCloudMatchBaseUrl("https://prod.bpc.geforcenow.nvidiagrid.net:443/"),
    "https://prod.bpc.geforcenow.nvidiagrid.net",
  );

  for (const endpoint of [
    "http://prod.cloudmatchbeta.nvidiagrid.net/",
    "https://localhost/",
    "https://127.0.0.1/",
    "https://cloudmatchbeta.nvidiagrid.net.attacker.example/",
    "https://attacker.example/?next=nvidiagrid.net",
    "https://user:password@prod.cloudmatchbeta.nvidiagrid.net/",
    "https://prod.cloudmatchbeta.nvidiagrid.net:8443/",
    "https://prod.cloudmatchbeta.nvidiagrid.net/redirect",
  ]) {
    assert.throws(() => normalizeTrustedCloudMatchBaseUrl(endpoint), /Untrusted CloudMatch endpoint/);
  }
});
