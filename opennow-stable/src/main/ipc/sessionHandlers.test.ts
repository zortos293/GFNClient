/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";
import type { IpcMain } from "electron";

import { IPC_CHANNELS } from "@shared/ipc";
import type { SessionCreateRequest, StreamSettings } from "@shared/gfn";
import { registerSessionIpcHandlers } from "./sessionHandlers";

function makeSettings(overrides: Partial<StreamSettings> = {}): StreamSettings {
  return {
    resolution: "1920x1080",
    fps: 60,
    maxBitrateMbps: 75,
    codec: "H264",
    colorQuality: "8bit_420",
    keyboardLayout: "en-US",
    gameLanguage: "en_US",
    enableL4S: false,
    enableCloudGsync: false,
    ...overrides,
  };
}

test("CREATE_SESSION existing-session claim preserves echoed persistence flag", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalLog = console.log;
  const originalWarn = console.warn;
  const handlers = new Map<string, (...args: unknown[]) => Promise<unknown>>();
  const claimBodies: Array<{ sessionRequestData?: { enablePersistingInGameSettings?: boolean } }> = [];

  console.log = () => {};
  console.warn = () => {};
  t.after(() => {
    globalThis.fetch = originalFetch;
    console.log = originalLog;
    console.warn = originalWarn;
  });

  const readySessionResponse = {
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
      sessionRequestData: {
        appId: "1001",
        appLaunchMode: 1,
        enablePersistingInGameSettings: true,
      },
    },
  };

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url === "https://prod.cloudmatchbeta.nvidiagrid.net/v2/session") {
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        sessions: [{
          sessionId: "sess-1",
          status: 3,
          sessionRequestData: {
            appId: "1001",
            appLaunchMode: 1,
            enablePersistingInGameSettings: true,
          },
          sessionControlInfo: { ip: "203.0.113.10" },
          connectionInfo: [{ ip: "203.0.113.10", port: 443, usage: 14 }],
        }],
      }), { status: 200 });
    }

    if (url.startsWith("https://203.0.113.10/v2/session/sess-1")) {
      if (init?.method === "PUT") {
        claimBodies.push(JSON.parse(String(init.body)));
      }
      return new Response(JSON.stringify(readySessionResponse), { status: 200 });
    }

    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  const ipcMain = {
    handle(channel: string, handler: (...args: unknown[]) => Promise<unknown>): void {
      handlers.set(channel, handler);
    },
  } as unknown as IpcMain;

  registerSessionIpcHandlers({
    ipcMain,
    authService: {
      getSelectedProvider: () => ({ streamingServiceUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/" }),
    } as never,
    settingsManager: {
      get: () => false,
    } as never,
    resolveJwt: async () => "token",
    setActivity: async () => {},
    clearActivity: async () => {},
    dialog: {
      showMessageBox: async () => ({ response: 2, checkboxChecked: false }),
    } as never,
    getMainWindow: () => null,
  });

  const createSessionHandler = handlers.get(IPC_CHANNELS.CREATE_SESSION);
  assert.ok(createSessionHandler);

  const request: SessionCreateRequest = {
    token: "renderer-token",
    streamingBaseUrl: "https://prod.cloudmatchbeta.nvidiagrid.net/",
    appId: "1001",
    internalTitle: "Test Game",
    accountLinked: true,
    zone: "prod",
    settings: makeSettings(),
  };
  const result = await createSessionHandler(null, request);

  assert.equal(claimBodies[0]?.sessionRequestData?.enablePersistingInGameSettings, true);
  assert.equal((result as { enablePersistingInGameSettings?: boolean }).enablePersistingInGameSettings, true);
});

test("CREATE_SESSION stops a launching session when the persistence wire value changed", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalLog = console.log;
  const originalWarn = console.warn;
  const handlers = new Map<string, (...args: unknown[]) => Promise<unknown>>();
  const createBodies: Array<{ sessionRequestData?: { enablePersistingInGameSettings?: boolean } }> = [];
  const stopUrls: string[] = [];
  const base = "https://unit.cloudmatchbeta.nvidiagrid.net";

  console.log = () => {};
  console.warn = () => {};
  t.after(() => {
    globalThis.fetch = originalFetch;
    console.log = originalLog;
    console.warn = originalWarn;
  });

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url === `${base}/v2/session`) {
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        sessions: [{
          sessionId: "sess-stale",
          status: 1,
          sessionRequestData: {
            appId: "1001",
            appLaunchMode: 1,
            enablePersistingInGameSettings: true,
          },
          sessionControlInfo: { ip: "203.0.113.10" },
          connectionInfo: [{ ip: "203.0.113.10", port: 443, usage: 14 }],
        }],
      }), { status: 200 });
    }

    if (url === "https://203.0.113.10/v2/session/sess-stale" && init?.method === "DELETE") {
      stopUrls.push(url);
      return new Response("", { status: 204 });
    }

    if (url.startsWith(`${base}/v2/session?`) && init?.method === "POST") {
      createBodies.push(JSON.parse(String(init.body)));
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        session: {
          sessionId: "sess-new",
          status: 1,
          sessionControlInfo: { ip: "203.0.113.20" },
          connectionInfo: [{ ip: "203.0.113.20", port: 443, usage: 14 }],
          sessionRequestData: {
            appId: "1001",
            enablePersistingInGameSettings: false,
          },
        },
      }), { status: 200 });
    }

    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  const ipcMain = {
    handle(channel: string, handler: (...args: unknown[]) => Promise<unknown>): void {
      handlers.set(channel, handler);
    },
  } as unknown as IpcMain;

  registerSessionIpcHandlers({
    ipcMain,
    authService: {
      getSelectedProvider: () => ({ streamingServiceUrl: base }),
    } as never,
    settingsManager: {
      get: () => false,
    } as never,
    resolveJwt: async () => "token",
    setActivity: async () => {},
    clearActivity: async () => {},
    dialog: {
      showMessageBox: async () => ({ response: 2, checkboxChecked: false }),
    } as never,
    getMainWindow: () => null,
  });

  const createSessionHandler = handlers.get(IPC_CHANNELS.CREATE_SESSION);
  assert.ok(createSessionHandler);

  const request: SessionCreateRequest = {
    token: "renderer-token",
    streamingBaseUrl: base,
    appId: "1001",
    internalTitle: "Test Game",
    accountLinked: true,
    zone: "prod",
    settings: makeSettings(),
    enablePersistingInGameSettings: false,
    supportsInGameSettingsPersistence: true,
  };
  const result = await createSessionHandler(null, request);

  assert.deepEqual(stopUrls, ["https://203.0.113.10/v2/session/sess-stale"]);
  assert.equal(createBodies[0]?.sessionRequestData?.enablePersistingInGameSettings, false);
  assert.equal((result as { sessionId?: string }).sessionId, "sess-new");
});

test("CREATE_SESSION preserves a launching session when persistence is not echoed", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalLog = console.log;
  const originalWarn = console.warn;
  const handlers = new Map<string, (...args: unknown[]) => Promise<unknown>>();
  const createBodies: Array<{ sessionRequestData?: { enablePersistingInGameSettings?: boolean } }> = [];
  const stopUrls: string[] = [];
  const base = "https://unit.cloudmatchbeta.nvidiagrid.net";

  console.log = () => {};
  console.warn = () => {};
  t.after(() => {
    globalThis.fetch = originalFetch;
    console.log = originalLog;
    console.warn = originalWarn;
  });

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url === `${base}/v2/session`) {
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        sessions: [{
          sessionId: "sess-unknown",
          status: 1,
          sessionRequestData: {
            appId: "1001",
            appLaunchMode: 1,
          },
          sessionControlInfo: { ip: "203.0.113.10" },
          connectionInfo: [{ ip: "203.0.113.10", port: 443, usage: 14 }],
        }],
      }), { status: 200 });
    }

    if (url === "https://203.0.113.10/v2/session/sess-unknown" && init?.method === "GET") {
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        session: {
          sessionId: "sess-unknown",
          status: 1,
          sessionControlInfo: { ip: "203.0.113.10" },
          connectionInfo: [{ ip: "203.0.113.10", port: 443, usage: 14 }],
          sessionRequestData: {
            appId: "1001",
          },
        },
      }), { status: 200 });
    }

    if (url === "https://203.0.113.10/v2/session/sess-unknown" && init?.method === "DELETE") {
      stopUrls.push(url);
      return new Response("", { status: 204 });
    }

    if (url.startsWith(`${base}/v2/session?`) && init?.method === "POST") {
      createBodies.push(JSON.parse(String(init.body)));
      return new Response(JSON.stringify({
        requestStatus: { statusCode: 1, statusDescription: "SUCCESS_STATUS" },
        session: {
          sessionId: "sess-new",
          status: 1,
          sessionControlInfo: { ip: "203.0.113.20" },
          connectionInfo: [{ ip: "203.0.113.20", port: 443, usage: 14 }],
          sessionRequestData: {
            appId: "1001",
            enablePersistingInGameSettings: true,
          },
        },
      }), { status: 200 });
    }

    throw new Error(`Unexpected fetch: ${url}`);
  }) as typeof fetch;

  const ipcMain = {
    handle(channel: string, handler: (...args: unknown[]) => Promise<unknown>): void {
      handlers.set(channel, handler);
    },
  } as unknown as IpcMain;

  registerSessionIpcHandlers({
    ipcMain,
    authService: {
      getSelectedProvider: () => ({ streamingServiceUrl: base }),
    } as never,
    settingsManager: {
      get: () => false,
    } as never,
    resolveJwt: async () => "token",
    setActivity: async () => {},
    clearActivity: async () => {},
    dialog: {
      showMessageBox: async () => ({ response: 2, checkboxChecked: false }),
    } as never,
    getMainWindow: () => null,
  });

  const createSessionHandler = handlers.get(IPC_CHANNELS.CREATE_SESSION);
  assert.ok(createSessionHandler);

  const request: SessionCreateRequest = {
    token: "renderer-token",
    streamingBaseUrl: base,
    appId: "1001",
    internalTitle: "Test Game",
    accountLinked: true,
    zone: "prod",
    settings: makeSettings(),
    enablePersistingInGameSettings: true,
    supportsInGameSettingsPersistence: true,
  };
  const result = await createSessionHandler(null, request);

  assert.deepEqual(stopUrls, []);
  assert.deepEqual(createBodies, []);
  assert.equal((result as { sessionId?: string }).sessionId, "sess-unknown");
});
