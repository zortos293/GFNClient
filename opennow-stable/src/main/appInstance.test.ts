import assert from "node:assert/strict";
import test from "node:test";

import {
  SECONDARY_INSTANCE_SWITCH,
  orderPortsForAppInstance,
  resolveAppInstanceProfile,
} from "./appInstance";

test("uses the normal profile when no secondary switch is present", () => {
  assert.deepEqual(
    resolveAppInstanceProfile(["OpenNOW.exe"], "C:\\Users\\User\\AppData\\Roaming\\OpenNOW"),
    {
      isSecondary: false,
      userDataPath: "C:\\Users\\User\\AppData\\Roaming\\OpenNOW",
    },
  );
});

test("isolates an explicitly launched secondary instance", () => {
  assert.deepEqual(
    resolveAppInstanceProfile(
      ["OpenNOW.exe", SECONDARY_INSTANCE_SWITCH],
      "C:\\Users\\User\\AppData\\Roaming\\OpenNOW",
    ),
    {
      isSecondary: true,
      userDataPath: "C:\\Users\\User\\AppData\\Roaming\\OpenNOW-secondary",
      windowTitle: "OpenNOW — Secondary",
    },
  );
});

test("does not activate the secondary profile for similar argument values", () => {
  assert.equal(
    resolveAppInstanceProfile(
      ["OpenNOW.exe", "--title", "--secondary-account"],
      "C:\\OpenNOW",
    ).isSecondary,
    false,
  );
});

test("secondary callbacks prefer the opposite end of a shared port pool", () => {
  const ports = [2259, 6460, 7119, 8870, 9096];
  assert.deepEqual(orderPortsForAppInstance(ports, false), ports);
  assert.deepEqual(orderPortsForAppInstance(ports, true), [...ports].reverse());
  assert.deepEqual(ports, [2259, 6460, 7119, 8870, 9096]);
});
