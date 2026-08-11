/// <reference types="node" />

import assert from "node:assert/strict";
import test from "node:test";

import { firstZoneHostname } from "./cloudmatchSessionParsing";

test("prefers a zone load-balancer hostname over a server address", () => {
  assert.equal(
    firstZoneHostname(
      "183-78-14-236.yes.geforcenow.nvidiagrid.net",
      "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net",
    ),
    "npa-yes-kul-01.yes.geforcenow.nvidiagrid.net",
  );
});

test("skips IP-shaped hostnames and accepts later array entries", () => {
  assert.equal(
    firstZoneHostname(
      "203.0.113.10",
      ["183-78-14-236.yes.geforcenow.nvidiagrid.net", "np-lax-01.cloudmatchbeta.nvidiagrid.net"],
    ),
    "np-lax-01.cloudmatchbeta.nvidiagrid.net",
  );
});

test("preserves an explicit human-readable server location", () => {
  assert.equal(firstZoneHostname("Amsterdam"), "Amsterdam");
});

test("returns undefined when no candidate carries location metadata", () => {
  assert.equal(firstZoneHostname("", undefined, [], "203.0.113.10"), undefined);
});
