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
