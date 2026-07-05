/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import type { StreamSettings } from "@shared/gfn";
import {
  DEFAULT_KEYBOARD_LAYOUT,
  colorQualityBitDepth,
  colorQualityChromaFormat,
  resolveGfnKeyboardLayout,
} from "@shared/gfn";
import {
  appLaunchModeWireValue,
  buildRequestedStreamingFeatures,
  claimSession,
  createSession,
  extractServerInfoRegionBases,
  getActiveSessions,
} from "./cloudmatch";

function makeSettings(overrides: Partial<StreamSettings> = {}): StreamSettings {
  return {
    resolution: "2560x1440",
    fps: 240,
    maxBitrateMbps: 75,
    codec: "H265",
    colorQuality: "8bit_420",
    keyboardLayout: "en-US",
    gameLanguage: "en_US",
    enableL4S: false,
    enableCloudGsync: false,
    clientMode: "native",
    ...overrides,
  };
}

test("CloudMatch requests resolved Cloud G-Sync value", () => {
  const off = buildRequestedStreamingFeatures(makeSettings({ enableCloudGsync: false }), 0, 0, false);
  const on = buildRequestedStreamingFeatures(makeSettings({ enableCloudGsync: true }), 0, 0, false);

  assert.equal(off.cloudGsync, false);
  assert.equal(on.cloudGsync, true);
});

test("CloudMatch reflex request follows official-style Cloud G-Sync gating", () => {
  const lowFpsNoVrr = buildRequestedStreamingFeatures(
    makeSettings({ fps: 60, enableCloudGsync: false }),
    0,
    0,
    false,
  );
  const lowFpsWithVrr = buildRequestedStreamingFeatures(
    makeSettings({ fps: 60, enableCloudGsync: true }),
    0,
    0,
    false,
  );
  const highFpsNoVrr = buildRequestedStreamingFeatures(
    makeSettings({ fps: 120, enableCloudGsync: false }),
    0,
    0,
    false,
  );

  assert.equal(lowFpsNoVrr.reflex, false);
  assert.equal(lowFpsWithVrr.reflex, true);
  assert.equal(highFpsNoVrr.reflex, true);
});

test("CloudMatch uses resolver Reflex decision when present", () => {
  const features = buildRequestedStreamingFeatures(
    makeSettings({
      fps: 60,
      enableCloudGsync: true,
      clientMode: "web",
      cloudGsyncResolution: {
        requested: true,
        enabled: true,
        reflexEnabled: false,
        reason: "web-mode",
        capabilities: {
          platformSupportsCloudGsync: false,
          isVrrCapableDisplay: false,
          isGsyncDisplay: false,
          minimumFpsForCloudGsync: 60,
          minimumFpsForReflexWithoutVrr: 120,
          detectionSource: "unsupported",
        },
      },
    }),
    0,
    0,
    false,
  );

  assert.equal(features.cloudGsync, true);
  assert.equal(features.reflex, false);
});

test("CloudMatch uses official streaming feature enum values", () => {
  assert.equal(colorQualityBitDepth("8bit_420"), 0);
  assert.equal(colorQualityBitDepth("10bit_420"), 1);
  assert.equal(colorQualityChromaFormat("8bit_420"), 0);
  assert.equal(colorQualityChromaFormat("8bit_444"), 1);

  const features = buildRequestedStreamingFeatures(makeSettings({ enableL4S: true }), 1, 1, false);
  assert.deepEqual(features, {
    reflex: true,
    bitDepth: 1,
    cloudGsync: false,
    enabledL4S: true,
    supportedHidDevices: 0,
    profile: 0,
    fallbackToLogicalResolution: false,
    chromaFormat: 1,
    prefilterMode: 0,
    prefilterSharpness: 0,
    prefilterNoiseReduction: 0,
    hudStreamingMode: 0,
  });
});

test("CloudMatch extracts local serverInfo region before fallback regions", () => {
  const bases = extractServerInfoRegionBases({
    metaData: [
      { key: "local-region", value: "TH BPC" },
      { key: "gfn-regions", value: "EU West, TH BPC, US East" },
      { key: "EU West", value: "https://np-eu.example.nvidiagrid.net/" },
      { key: "TH BPC", value: "https://th.bpc.geforcenow.nvidiagrid.net" },
      { key: "US East", value: "https://np-us.example.nvidiagrid.net/" },
    ],
  });

  assert.deepEqual(bases, [
    "https://th.bpc.geforcenow.nvidiagrid.net",
    "https://np-eu.example.nvidiagrid.net",
    "https://np-us.example.nvidiagrid.net",
  ]);
});

test("CloudMatch resolves default prod endpoint to serverInfo local region before creating a session", async () => {
  const originalFetch = globalThis.fetch;
  const originalWarn = console.warn;
  const calls: string[] = [];
  type CapturedSessionRequestBody = {
    sessionRequestData: {
      appLaunchMode?: number;
      enablePersistingInGameSettings?: boolean;
      requestedStreamingFeatures: {
        bitDepth?: number;
        chromaFormat?: number;
      };
    };
  };
  let requestBody: CapturedSessionRequestBody | null = null;
  const expectedSessionUrl = `https://np-lax-01.cloudmatchbeta.nvidiagrid.net/v2/session?${new URLSearchParams({
    keyboardLayout: resolveGfnKeyboardLayout(DEFAULT_KEYBOARD_LAYOUT, process.platform),
    languageCode: "en_US",
  }).toString()}`;

  console.warn = () => {};
  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    calls.push(url);

    if (url === "https://prod.cloudmatchbeta.nvidiagrid.net/v2/serverInfo") {
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS", serverId: "NP-LAX-01" },
        metaData: [
          { key: "local-region", value: "US West" },
          { key: "gfn-regions", value: "US West, US East" },
          { key: "US West", value: "https://np-lax-01.cloudmatchbeta.nvidiagrid.net/" },
          { key: "US East", value: "https://np-ash-01.cloudmatchbeta.nvidiagrid.net/" },
        ],
      }), { status: 200 });
    }

    if (url === expectedSessionUrl) {
      requestBody = JSON.parse(String(init?.body));
      const createdRequestBody = requestBody;
      if (!createdRequestBody) {
        throw new Error("Session request body was not captured");
      }
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        session: {
          sessionId: "session-1",
          status: 1,
          seatSetupInfo: { seatSetupStep: 0 },
          sessionControlInfo: { ip: "np-lax-01.cloudmatchbeta.nvidiagrid.net" },
          connectionInfo: [],
          iceServerConfiguration: {
            iceServers: [{ urls: "stun:127.0.0.1:19302" }],
          },
          sessionRequestData: {
            clientRequestMonitorSettings: [{ widthInPixels: 2560, heightInPixels: 1440, framesPerSecond: 240 }],
            requestedStreamingFeatures: createdRequestBody.sessionRequestData.requestedStreamingFeatures,
            enablePersistingInGameSettings: createdRequestBody.sessionRequestData.enablePersistingInGameSettings,
          },
        },
      }), { status: 200 });
    }

    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  try {
    const session = await createSession({
      token: "token",
      streamingBaseUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/",
      appId: "1001",
      internalTitle: "Test Game",
      accountLinked: true,
      zone: "prod",
      settings: makeSettings({ colorQuality: "10bit_444", enableL4S: true, appLaunchMode: "gamepadFriendly" }),
    });

    assert.equal(session.streamingBaseUrl, "https://np-lax-01.cloudmatchbeta.nvidiagrid.net");
    assert.equal(session.enablePersistingInGameSettings, false);
    assert.deepEqual(calls, [
      "https://prod.cloudmatchbeta.nvidiagrid.net/v2/serverInfo",
      expectedSessionUrl,
    ]);
    const capturedRequestBody = requestBody as CapturedSessionRequestBody | null;
    assert.ok(capturedRequestBody);
    assert.equal(capturedRequestBody.sessionRequestData.requestedStreamingFeatures.bitDepth, 1);
    assert.equal(capturedRequestBody.sessionRequestData.requestedStreamingFeatures.chromaFormat, 1);
    assert.equal(capturedRequestBody.sessionRequestData.appLaunchMode, 2);
    assert.equal(capturedRequestBody.sessionRequestData.enablePersistingInGameSettings, false);
  } finally {
    globalThis.fetch = originalFetch;
    console.warn = originalWarn;
  }
});

test("CloudMatch only sends in-game settings persistence when user opt-in and game support are both true", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalLog = console.log;
  console.log = () => {};

  t.after(() => {
    globalThis.fetch = originalFetch;
    console.log = originalLog;
  });

  const persistenceFlags: boolean[] = [];
  const expectedSessionUrl = `https://np-test.example.test/v2/session?${new URLSearchParams({
    keyboardLayout: resolveGfnKeyboardLayout(DEFAULT_KEYBOARD_LAYOUT, process.platform),
    languageCode: "en_US",
  }).toString()}`;

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url !== expectedSessionUrl) {
      throw new Error(`Unexpected fetch: ${url}`);
    }

    const body = JSON.parse(String(init?.body)) as {
      sessionRequestData: {
        enablePersistingInGameSettings?: boolean;
      };
    };
    const enablePersistingInGameSettings = body.sessionRequestData.enablePersistingInGameSettings === true;
    persistenceFlags.push(enablePersistingInGameSettings);

    return new Response(JSON.stringify({
      requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
      session: {
        sessionId: `session-${persistenceFlags.length}`,
        status: 1,
        seatSetupInfo: { seatSetupStep: 0 },
        sessionControlInfo: { ip: "np-test.example.test" },
        connectionInfo: [],
        iceServerConfiguration: {
          iceServers: [{ urls: "stun:127.0.0.1:19302" }],
        },
        sessionRequestData: {
          enablePersistingInGameSettings,
        },
      },
    }), { status: 200 });
  }) as typeof fetch;

  const baseRequest = {
    token: "token",
    streamingBaseUrl: "https://np-test.example.test/",
    appId: "1001",
    internalTitle: "Test Game",
    accountLinked: true,
    zone: "prod",
    settings: makeSettings(),
  };

  await createSession({
    ...baseRequest,
    enablePersistingInGameSettings: true,
    supportsInGameSettingsPersistence: false,
  });
  await createSession({
    ...baseRequest,
    enablePersistingInGameSettings: true,
    supportsInGameSettingsPersistence: true,
  });
  await createSession({
    ...baseRequest,
    enablePersistingInGameSettings: false,
    supportsInGameSettingsPersistence: true,
  });

  assert.deepEqual(persistenceFlags, [false, true, false]);
});

test("CloudMatch falls back to serverInfo local region when active-session HTTP request fails", async () => {
  const originalFetch = globalThis.fetch;
  const originalWarn = console.warn;
  const calls: string[] = [];

  console.warn = () => {};
  globalThis.fetch = (async (input) => {
    const url = String(input);
    calls.push(url);

    if (url === "https://prod.bpc.geforcenow.nvidiagrid.net/v2/session") {
      return new Response("bad gateway", { status: 502 });
    }

    if (url === "https://prod.bpc.geforcenow.nvidiagrid.net/v2/serverInfo") {
      return new Response(JSON.stringify({
        metaData: [
          { key: "local-region", value: "TH BPC" },
          { key: "gfn-regions", value: "TH BPC" },
          { key: "TH BPC", value: "https://th.bpc.geforcenow.nvidiagrid.net" },
        ],
      }), { status: 200 });
    }

    if (url === "https://th.bpc.geforcenow.nvidiagrid.net/v2/session") {
      return new Response(JSON.stringify({
        requestStatus: {
          statusCode: 1,
          statusDescription: "SUCCESS_STATUS",
        },
        sessions: [{
          sessionId: "session-1",
          status: 3,
          gpuType: "RTX",
          sessionRequestData: { appId: "1001", enablePersistingInGameSettings: true },
          sessionControlInfo: { ip: "th.bpc.geforcenow.nvidiagrid.net" },
          connectionInfo: [{ ip: "161.248.11.132", port: 443, usage: 14 }],
          monitorSettings: [{ widthInPixels: 1920, heightInPixels: 1080, framesPerSecond: 60 }],
        }],
      }), { status: 200 });
    }

    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  try {
    const sessions = await getActiveSessions("token", "https://prod.bpc.geforcenow.nvidiagrid.net/");

    assert.equal(sessions.length, 1);
    assert.equal(sessions[0].sessionId, "session-1");
    assert.equal(sessions[0].serverIp, "161.248.11.132");
    assert.equal(sessions[0].enablePersistingInGameSettings, true);
    assert.deepEqual(calls, [
      "https://prod.bpc.geforcenow.nvidiagrid.net/v2/session",
      "https://prod.bpc.geforcenow.nvidiagrid.net/v2/serverInfo",
      "https://th.bpc.geforcenow.nvidiagrid.net/v2/session",
    ]);
  } finally {
    globalThis.fetch = originalFetch;
    console.warn = originalWarn;
  }
});

test("CloudMatch appLaunchMode maps to official wire values", () => {
  assert.equal(appLaunchModeWireValue(undefined), 1);
  assert.equal(appLaunchModeWireValue("default"), 1);
  assert.equal(appLaunchModeWireValue("gamepadFriendly"), 2);
  assert.equal(appLaunchModeWireValue("touchFriendly"), 3);
});

test("CloudMatch claim keeps the session-stable appLaunchMode over live settings", async () => {
  const originalFetch = globalThis.fetch;
  const originalWarn = console.warn;
  const originalLog = console.log;
  console.warn = () => {};
  console.log = () => {};

  const claimBodies: Array<{
    sessionRequestData: {
      appLaunchMode?: number;
      enablePersistingInGameSettings?: boolean;
    };
  }> = [];

  const readySessionResponse = JSON.stringify({
    requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
    session: {
      sessionId: "sess-1",
      status: 3,
      sessionControlInfo: { ip: "203.0.113.10" },
      connectionInfo: [
        { ip: "203.0.113.10", port: 443, usage: 14, resourcePath: "/nvst/" },
        { ip: "203.0.113.10", port: 49006, usage: 2 },
      ],
      iceServerConfiguration: {
        iceServers: [{ urls: "stun:127.0.0.1:19302" }],
      },
      sessionRequestData: {},
    },
  });

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url.startsWith("https://203.0.113.10/v2/session/sess-1")) {
      if (init?.method === "PUT") {
        claimBodies.push(JSON.parse(String(init.body)));
      }
      return new Response(readySessionResponse, { status: 200 });
    }
    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  try {
    // Session created as gamepad-friendly (wire 2) must stay gamepad-friendly on
    // resume even if the live settings toggles now say "default".
    await claimSession({
      token: "token",
      sessionId: "sess-1",
      serverIp: "203.0.113.10",
      appId: "1001",
      appLaunchMode: 2,
      settings: makeSettings(),
    });

    // Without a session-stable value the claim falls back to the settings-derived mode.
    await claimSession({
      token: "token",
      sessionId: "sess-1",
      serverIp: "203.0.113.10",
      appId: "1001",
      enablePersistingInGameSettings: true,
      settings: makeSettings({ appLaunchMode: "gamepadFriendly" }),
    });

    await claimSession({
      token: "token",
      sessionId: "sess-1",
      serverIp: "203.0.113.10",
      appId: "1001",
      settings: makeSettings(),
    });

    assert.equal(claimBodies.length, 3);
    assert.equal(claimBodies[0].sessionRequestData.appLaunchMode, 2);
    assert.equal(claimBodies[1].sessionRequestData.appLaunchMode, 2);
    assert.equal(claimBodies[2].sessionRequestData.appLaunchMode, 1);
    assert.equal(claimBodies[0].sessionRequestData.enablePersistingInGameSettings, false);
    assert.equal(claimBodies[1].sessionRequestData.enablePersistingInGameSettings, true);
    assert.equal(claimBodies[2].sessionRequestData.enablePersistingInGameSettings, false);
  } finally {
    globalThis.fetch = originalFetch;
    console.warn = originalWarn;
    console.log = originalLog;
  }
});
