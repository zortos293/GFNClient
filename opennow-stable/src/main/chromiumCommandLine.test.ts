import assert from "node:assert/strict";
import test from "node:test";

import {
  buildChromiumCommandLine,
  MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE,
  normalizeBootstrapChromiumPreferences,
} from "./chromiumCommandLine";

test("normalizes bootstrap Chromium preferences", () => {
  assert.deepEqual(
    normalizeBootstrapChromiumPreferences({
      decoderPreference: "hardware",
      encoderPreference: "software",
      steamControllerCompatibilityMode: true,
    }),
    {
      decoderPreference: "hardware",
      encoderPreference: "software",
      steamControllerCompatibilityMode: true,
    },
  );

  assert.deepEqual(
    normalizeBootstrapChromiumPreferences({
      decoderPreference: "invalid",
      encoderPreference: false,
      steamControllerCompatibilityMode: "true",
    }),
    {
      decoderPreference: "auto",
      encoderPreference: "auto",
      steamControllerCompatibilityMode: false,
    },
  );
});

test("adds Steam Controller compatibility feature disable only on macOS when enabled", () => {
  const preferences = normalizeBootstrapChromiumPreferences({
    steamControllerCompatibilityMode: true,
  });

  assert.equal(
    buildChromiumCommandLine(preferences, "darwin", "arm64").disableFeatures.includes(
      MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE,
    ),
    true,
  );
  assert.equal(
    buildChromiumCommandLine(preferences, "linux", "x64").disableFeatures.includes(
      MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE,
    ),
    false,
  );
  assert.equal(
    buildChromiumCommandLine(
      { ...preferences, steamControllerCompatibilityMode: false },
      "darwin",
      "arm64",
    ).disableFeatures.includes(MAC_STEAM_CONTROLLER_COMPATIBILITY_DISABLED_FEATURE),
    false,
  );
});
