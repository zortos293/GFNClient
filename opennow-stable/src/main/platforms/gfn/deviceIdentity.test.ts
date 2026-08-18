/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildGfnCloudMatchHeaders,
  buildGfnGraphQlHeaders,
  buildGfnLcarsHeaders,
  gfnUserAgentForPlatform,
  LCARS_CLIENT_ID,
} from "./clientHeaders";
import {
  STEAM_DECK_DEVICE_IDENTITY,
  configureIdentifyAsSteamDeck,
  resolveGfnDeviceIdentity,
} from "./deviceIdentity";

test("resolveGfnDeviceIdentity defaults to host desktop DESKTOP/UNKNOWN", () => {
  configureIdentifyAsSteamDeck(() => false);
  const identity = resolveGfnDeviceIdentity({ identifyAsSteamDeck: false, platform: "win32" });
  assert.equal(identity.deviceOs, "WINDOWS");
  assert.equal(identity.deviceType, "DESKTOP");
  assert.equal(identity.deviceMake, "UNKNOWN");
  assert.equal(identity.deviceModel, "UNKNOWN");
  assert.equal(identity.clientPlatformName, "Windows");
});

test("desktop identity uses native platform names", () => {
  assert.equal(resolveGfnDeviceIdentity({ platform: "darwin" }).clientPlatformName, "MacOS");
  assert.equal(resolveGfnDeviceIdentity({ platform: "linux" }).clientPlatformName, "Linux");
});

test("Linux requests use the packaged native CEF product identity", () => {
  const userAgent = gfnUserAgentForPlatform("linux");
  assert.match(userAgent, /\(X11; Linux x86_64\)/);
  assert.match(userAgent, /NVIDIACEFClient\/gfn_release\/2f4a4c46/);
  assert.match(userAgent, /GFN-PC\/2\.0\.80\.173/);
});

test("CloudMatch defaults to the stable LCARS client identity", () => {
  const headers = buildGfnCloudMatchHeaders({ token: "token", deviceId: "device" });
  assert.equal(headers["nv-client-id"], LCARS_CLIENT_ID);
});

test("resolveGfnDeviceIdentity Steam Deck profile matches official headers", () => {
  const identity = resolveGfnDeviceIdentity({ identifyAsSteamDeck: true });
  assert.deepEqual(identity, STEAM_DECK_DEVICE_IDENTITY);
  assert.equal(identity.deviceOs, "STEAMOS");
  assert.equal(identity.deviceType, "CONSOLE");
  assert.equal(identity.deviceMake, "VALVE");
  assert.equal(identity.deviceModel, "STEAMDECK");
  assert.equal(identity.clientPlatformName, "SteamOS");
});

test("CloudMatch/LCARS/GraphQL headers honor Steam Deck identity", () => {
  configureIdentifyAsSteamDeck(() => true);

  const cloudMatch = buildGfnCloudMatchHeaders({
    token: "token",
    clientId: "client",
    deviceId: "device",
  });
  assert.equal(cloudMatch["nv-device-os"], "STEAMOS");
  assert.equal(cloudMatch["nv-device-type"], "CONSOLE");
  assert.equal(cloudMatch["nv-device-make"], "VALVE");
  assert.equal(cloudMatch["nv-device-model"], "STEAMDECK");

  const lcars = buildGfnLcarsHeaders({
    token: "token",
    clientType: "NATIVE",
    clientStreamer: "NVIDIA-CLASSIC",
  });
  assert.equal(lcars["nv-device-os"], "STEAMOS");
  assert.equal(lcars["nv-device-type"], "CONSOLE");
  assert.equal(lcars["nv-device-make"], "VALVE");
  assert.equal(lcars["nv-device-model"], "STEAMDECK");

  const graphQl = buildGfnGraphQlHeaders("token");
  assert.equal(graphQl["nv-device-os"], "STEAMOS");
  assert.equal(graphQl["nv-device-type"], "CONSOLE");
  assert.equal(graphQl["nv-device-make"], "VALVE");
  assert.equal(graphQl["nv-device-model"], "STEAMDECK");

  configureIdentifyAsSteamDeck(() => false);
});

test("explicit identifyAsSteamDeck option overrides settings reader", () => {
  configureIdentifyAsSteamDeck(() => false);
  const headers = buildGfnCloudMatchHeaders({
    token: "token",
    clientId: "client",
    deviceId: "device",
    identifyAsSteamDeck: true,
  });
  assert.equal(headers["nv-device-os"], "STEAMOS");
  assert.equal(headers["nv-device-type"], "CONSOLE");
});
