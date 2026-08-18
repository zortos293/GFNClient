/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  collectRtspsEndpoints,
  negotiateNvstRtspSession,
  NvstRtspNegotiationError,
  resolveRtspControlUri,
  rtspsUrlToWssUrl,
  selectPrimaryRtspsEndpoint,
  type NvstRtspClient,
  type NvstRtspNegotiationDependencies,
} from "./probe";
import type { ParsedRtspResponse } from "./rtspClient";

function response(
  headers: Record<string, string> = {},
  body = "",
  statusCode = 200,
): ParsedRtspResponse {
  return {
    statusCode,
    statusText: statusCode === 200 ? "OK" : "Failed",
    headers,
    body,
  };
}

class FakeRtspClient implements NvstRtspClient {
  closed = false;
  readonly requests: Array<{
    method: string;
    uri: string;
    headers: Record<string, string>;
    body: string;
  }> = [];

  constructor(
    private readonly onRequest: (
      method: string,
      uri: string,
      headers: Record<string, string>,
      body: string,
    ) => ParsedRtspResponse | Promise<ParsedRtspResponse>,
    private readonly events: string[],
  ) {}

  async connect(sessionId?: string): Promise<void> {
    this.events.push(`connect:${sessionId}`);
  }

  async request(
    method: string,
    uri: string,
    headers: Record<string, string> = {},
    body = "",
  ): Promise<ParsedRtspResponse> {
    this.requests.push({ method, uri, headers, body });
    this.events.push(`request:${method}`);
    return this.onRequest(method, uri, headers, body);
  }

  close(): void {
    this.closed = true;
    this.events.push("client-close");
  }
}

const DESCRIBE_SDP = [
  "v=0",
  "a=x-nv-runtime.encryptionKey:AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
  "a=x-nv-runtime.encryptionKeyId:42",
  "m=video 0 RTP/AVP 96",
  "a=control:tracks/actual-video-track",
  "",
].join("\r\n");

function createNegotiationHarness(
  events: string[],
  override?: (method: string) => ParsedRtspResponse | undefined,
): {
  client: FakeRtspClient;
  dependencies: NvstRtspNegotiationDependencies;
} {
  const client = new FakeRtspClient((method) => {
    const overridden = override?.(method);
    if (overridden) {
      return overridden;
    }
    switch (method) {
      case "DESCRIBE":
        return response({ session: "rtsp-session;timeout=60" }, DESCRIBE_SDP);
      case "SETUP":
        return response({
          transport: "unicast;X-GS-ServerPort=5004-5005;source=192.0.2.4",
          "x-nv-ping-payload": "ping-data",
          "x-nv-ping": "1",
        });
      default:
        return response();
    }
  }, events);
  return {
    client,
    dependencies: {
      createClient: () => client,
      reserveUdpPort: async () => ({
        port: 45678,
        release: async () => {
          events.push("udp-release");
        },
      }),
    },
  };
}

test("selectPrimaryRtspsEndpoint preserves CloudMatch endpoint order", () => {
  const selected = selectPrimaryRtspsEndpoint([
    "rtsps://host.example:48322",
    "rtsps://host.example:322",
  ]);
  assert.equal(selected, "rtsps://host.example:48322");
});

test("rtspsUrlToWssUrl is host:port with no path (empty upgrade path is manual)", () => {
  assert.equal(
    rtspsUrlToWssUrl("rtsps://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322"),
    "wss://80-250-97-37.cloudmatchbeta.nvidiagrid.net:322",
  );
});

test("collectRtspsEndpoints keeps usage=16 RTSPS paths and ignores signaling", () => {
  const endpoints = collectRtspsEndpoints(
    [
      {
        usage: 16,
        port: 322,
        resourcePath: "rtsps://host.example:322",
      },
      {
        usage: 16,
        port: 48322,
        resourcePath: "rtsps://host.example:48322",
      },
      {
        usage: 14,
        port: 443,
        resourcePath: "rtsps://signal.example/nvst/",
      },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});

test("collectRtspsEndpoints synthesizes from port when resourcePath missing", () => {
  const endpoints = collectRtspsEndpoints(
    [
      { usage: 16, port: 322, resourcePath: null },
      { usage: 16, port: 48322, resourcePath: null },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
  ]);
});

test("resolveRtspControlUri preserves server-advertised absolute and relative controls", () => {
  assert.equal(
    resolveRtspControlUri("rtsps://host.example:322/session/base", "tracks/video-main"),
    "rtsps://host.example:322/session/base/tracks/video-main",
  );
  assert.equal(
    resolveRtspControlUri("rtsps://host.example:322/session/base", "/tracks/video-main"),
    "rtsps://host.example:322/tracks/video-main",
  );
  assert.equal(
    resolveRtspControlUri(
      "rtsps://host.example:322/session/base",
      "rtsps://media.example:322/selected/video",
    ),
    "rtsps://media.example:322/selected/video",
  );
});

test("negotiation retains RTSPS control and releases UDP immediately before native handoff", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events);
  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    codec: "H265",
  }, dependencies);
  events.push("native-start");

  assert.deepEqual(events.slice(-3), ["request:PLAY", "udp-release", "native-start"]);
  assert.equal(client.closed, false);
  assert.equal(negotiated.videoSession.clientUdpPort, 45678);
  assert.equal(negotiated.videoSession.videoPeerIp, "192.0.2.4");
  assert.equal(negotiated.videoSession.codec, "H265");
  assert.equal(negotiated.videoSession.srtpSaltHex, "00000000000000000000002A");
  assert.equal(negotiated.videoSession.srtpProfile, undefined);
  assert.equal(negotiated.srtp.saltHex, "00000000000000000000002A");
  assert.equal(negotiated.srtp.profile, undefined);
  assert.equal(
    client.requests.find(({ method }) => method === "SETUP")?.uri,
    "rtsps://host.example:322/session/base/tracks/actual-video-track",
  );

  await negotiated.release("test stop");
  await negotiated.release("duplicate stop");

  assert.equal(client.closed, true);
  assert.equal(client.requests.filter(({ method }) => method === "TEARDOWN").length, 1);
  assert.equal(
    client.requests.find(({ method }) => method === "TEARDOWN")?.headers.Session,
    "rtsp-session",
  );
  assert.deepEqual(events.slice(-2), ["request:TEARDOWN", "client-close"]);
});

test("negotiation hands off an explicitly advertised DESCRIBE SRTP profile", async () => {
  const events: string[] = [];
  const { dependencies } = createNegotiationHarness(events, (method) =>
    method === "DESCRIBE"
      ? response(
        { session: "rtsp-session;timeout=60" },
        DESCRIBE_SDP.replace(
          "m=video",
          "a=crypto:1 AEAD_AES_256_GCM inline:ignored\r\nm=video",
        ),
      )
      : undefined);

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  assert.equal(negotiated.srtp.profile, "AEAD_AES_256_GCM");
  assert.equal(negotiated.videoSession.srtpProfile, "AEAD_AES_256_GCM");
  assert.equal(negotiated.videoSession.srtpSaltHex, negotiated.srtp.saltHex);
  await negotiated.release("test complete");
});

test("negotiation hands off an explicitly advertised SETUP SRTP profile", async () => {
  const events: string[] = [];
  const { dependencies } = createNegotiationHarness(events, (method) =>
    method === "SETUP"
      ? response({
        transport: "unicast;X-GS-ServerPort=5004-5005;source=192.0.2.4;profile=AES_CM_128_HMAC_SHA1_80",
      })
      : undefined);

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  assert.equal(negotiated.srtp.profile, "AES_CM_128_HMAC_SHA1_80");
  assert.equal(negotiated.videoSession.srtpProfile, "AES_CM_128_HMAC_SHA1_80");
  await negotiated.release("test complete");
});

test("negotiation fails closed on conflicting explicit SRTP profiles", async () => {
  const events: string[] = [];
  const { dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        DESCRIBE_SDP.replace(
          "m=video",
          "a=crypto:1 AEAD_AES_256_GCM inline:ignored\r\nm=video",
        ),
      );
    }
    if (method === "SETUP") {
      return response({
        transport: "unicast;X-GS-ServerPort=5004-5005;source=192.0.2.4;profile=AEAD_AES_128_GCM",
      });
    }
    return undefined;
  });

  await assert.rejects(
    negotiateNvstRtspSession({
      sessionId: "gfn-session",
      rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    }, dependencies),
    (error: unknown) =>
      error instanceof NvstRtspNegotiationError
      && error.code === "conflicting-srtp-profile",
  );

  assert.equal(events.includes("udp-release"), true);
  assert.deepEqual(events.slice(-2), ["request:TEARDOWN", "client-close"]);
});

test("negotiation fails closed when DESCRIBE omits video control and tears down", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) =>
    method === "DESCRIBE"
      ? response({ session: "rtsp-session" }, "v=0\r\nm=video 0 RTP/AVP 96\r\n")
      : undefined);

  await assert.rejects(
    negotiateNvstRtspSession({
      sessionId: "gfn-session",
      rtspsEndpoints: ["rtsps://host.example:322"],
    }, dependencies),
    (error: unknown) =>
      error instanceof NvstRtspNegotiationError
      && error.code === "missing-video-control",
  );

  assert.equal(client.requests.some(({ method }) => method === "SETUP"), false);
  assert.deepEqual(events.slice(-2), ["request:TEARDOWN", "client-close"]);
});

test("failed startup releases the UDP reservation and closes RTSPS control", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) =>
    method === "PLAY" ? response({}, "", 500) : undefined);

  await assert.rejects(
    negotiateNvstRtspSession({
      sessionId: "gfn-session",
      rtspsEndpoints: ["rtsps://host.example:322"],
    }, dependencies),
    (error: unknown) =>
      error instanceof NvstRtspNegotiationError
      && error.code === "negotiation-failed"
      && /PLAY failed/.test(error.message),
  );

  assert.equal(events.includes("udp-release"), true);
  assert.equal(client.closed, true);
  assert.deepEqual(events.slice(-3), ["udp-release", "request:TEARDOWN", "client-close"]);
});
