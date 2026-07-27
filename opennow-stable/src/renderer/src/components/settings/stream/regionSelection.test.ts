import assert from "node:assert/strict";
import test from "node:test";

import {
  filterAndSortRegions,
  findBestRegionUrl,
  getRegionPingQuality,
  type RegionSelectionOption,
} from "./regionSelection";

const regions: RegionSelectionOption[] = [
  { name: "US Southwest", url: "southwest" },
  { name: "EU Northeast", url: "northeast" },
  { name: "US East", url: "east" },
  { name: "US West", url: "west" },
];

test("findBestRegionUrl selects the lowest successful latency", () => {
  assert.equal(findBestRegionUrl(new Map([
    ["southwest", null],
    ["east", 41],
    ["west", 18],
  ])), "west");
  assert.equal(findBestRegionUrl(new Map([["southwest", null]])), null);
});

test("filterAndSortRegions filters by name and ranks successful pings first", () => {
  const sorted = filterAndSortRegions(
    regions,
    " us ",
    new Map([
      ["southwest", null],
      ["east", 55],
      ["west", 20],
    ]),
  );

  assert.deepEqual(sorted.map((region) => region.url), ["west", "east", "southwest"]);
});

test("filterAndSortRegions alphabetizes failed and untested regions", () => {
  const sorted = filterAndSortRegions(regions, "", new Map([["southwest", null]]));

  assert.deepEqual(
    sorted.map((region) => region.name),
    ["EU Northeast", "US East", "US Southwest", "US West"],
  );
  assert.deepEqual(regions.map((region) => region.name), [
    "US Southwest",
    "EU Northeast",
    "US East",
    "US West",
  ]);
});

test("getRegionPingQuality uses the existing latency thresholds", () => {
  assert.equal(getRegionPingQuality(50), "good");
  assert.equal(getRegionPingQuality(51), "medium");
  assert.equal(getRegionPingQuality(100), "medium");
  assert.equal(getRegionPingQuality(101), "poor");
});

