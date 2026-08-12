import assert from "node:assert/strict";
import test from "node:test";
import { settingsScopeMatchesSearch } from "./settingsSearch";

test("routes browser recording searches to the recording scope", () => {
  assert.equal(
    settingsScopeMatchesSearch("stream-recording", "recording bitrate"),
    true,
  );
  assert.equal(
    settingsScopeMatchesSearch("stream-video", "recording bitrate"),
    false,
  );
  assert.equal(
    settingsScopeMatchesSearch("native-streamer", "recording bitrate"),
    false,
  );
});

test("routes frame stats and codec searches to diagnostics", () => {
  assert.equal(
    settingsScopeMatchesSearch("stream-diagnostics", "frame stats"),
    true,
  );
  assert.equal(
    settingsScopeMatchesSearch("stream-diagnostics", "codec test"),
    true,
  );
  assert.equal(settingsScopeMatchesSearch("interface", "frame stats"), false);
});

test("routes legacy session timing aliases from interface to diagnostics", () => {
  const movedQueries = [
    "session time left",
    "session countdown",
    "free tier time",
    "priority time",
    "ultimate time",
    "counter",
  ];

  for (const query of movedQueries) {
    assert.equal(settingsScopeMatchesSearch("stream-diagnostics", query), true);
    assert.equal(settingsScopeMatchesSearch("interface", query), false);
  }
});

test("routes OpenNOW console shell settings out of interface and stream scopes", () => {
  const movedQueries = ["controller mode", "console profile picker", "console shell"];

  for (const query of movedQueries) {
    assert.equal(settingsScopeMatchesSearch("console", query), true);
    assert.equal(settingsScopeMatchesSearch("interface", query), false);
    assert.equal(settingsScopeMatchesSearch("stream-video", query), false);
  }
});

test("routes session-only console identity searches to Stream", () => {
  const movedQueries = [
    "identify as console",
    "big picture",
    "gamepad friendly",
    "GFN launch mode",
  ];

  for (const query of movedQueries) {
    assert.equal(settingsScopeMatchesSearch("stream-video", query), true);
    assert.equal(settingsScopeMatchesSearch("console", query), false);
  }
});

test("matches search tokens by word prefix", () => {
  assert.equal(
    settingsScopeMatchesSearch("stream-diagnostics", "diag over"),
    true,
  );
  assert.equal(
    settingsScopeMatchesSearch("stream-recording", "browser rec"),
    true,
  );
});
