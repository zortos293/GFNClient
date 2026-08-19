/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildNvstStunBindingRequest,
  collectRtspsEndpoints,
  negotiateNvstRtspSession,
  NvstRtspNegotiationError,
  resolveRtspControlUri,
  rtspsUrlToWssUrl,
  selectPrimaryRtspsEndpoint,
  type NvstRtspClient,
  type NvstRtspNegotiationDependencies,
} from "./probe";

test("version 6 STUN request matches the official RFC 5389 packet shape", () => {
  assert.equal(
    buildNvstStunBindingRequest(
      "loc1",
      "remote01",
      "remote-password-with-36-byte-value-001",
      Buffer.from("000102030405060708090A0B", "hex"),
    ).toString("hex").toUpperCase(),
    "000100342112A442000102030405060708090A0B0006000D72656D6F746530313A6C6F633100000000080014B276DC1C7949494C7EF7EB226BE8BB5E0EE5AABD802800045A8349EF",
  );
});
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
  "m=audio 0 RTP/AVP 97",
  "a=control:tracks/actual-audio-track",
  "m=application 0 RTP/AVP 98",
  "a=control:streamid=control/0",
  "",
].join("\r\n");

function createNegotiationHarness(
  events: string[],
  override?: (method: string) => ParsedRtspResponse | undefined,
): {
  client: FakeRtspClient;
  dependencies: NvstRtspNegotiationDependencies;
} {
  let nextUdpPort = 45678;
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
      reserveUdpPort: async () => {
        const port = nextUdpPort;
        nextUdpPort += 2;
        return {
          port,
          release: async () => {
            events.push("udp-release");
          },
        };
      },
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

test("collectRtspsEndpoints keeps current and legacy RTSPS descriptors and ignores signaling paths", () => {
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
        appLevelProtocol: 6,
        port: 48323,
        resourcePath: "rtsps://legacy.example:48323",
      },
      {
        usage: 14,
        port: 443,
        resourcePath: "/nvst/",
      },
    ],
    "host.example",
  );
  assert.deepEqual(endpoints, [
    "rtsps://host.example:322",
    "rtsps://host.example:48322",
    "rtsps://legacy.example:48323",
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

test("negotiation retains RTSPS control and video UDP until native rebind", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events);
  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    codec: "H265",
  }, dependencies);
  events.push("native-start");
  await negotiated.handoffVideoUdp();

  assert.deepEqual(events.slice(-3), [
    "request:ANNOUNCE",
    "native-start",
    "udp-release",
  ]);
  assert.equal(client.closed, false);
  assert.equal(negotiated.videoSession.clientUdpPort, 45678);
  assert.equal(negotiated.videoSession.videoPeerIp, "192.0.2.4");
  assert.equal(negotiated.videoSession.codec, "H265");
  assert.equal(negotiated.videoSession.srtpSaltHex, "00000000000000000000002A");
  assert.equal(negotiated.videoSession.srtpProfile, undefined);
  assert.equal(negotiated.srtp.saltHex, "00000000000000000000002A");
  assert.equal(negotiated.srtp.profile, undefined);
  assert.equal(
    client.requests.find(({ method }) => method === "OPTIONS")?.uri,
    "rtsp://host.example:322",
  );
  assert.equal(
    client.requests.find(({ method }) => method === "DESCRIBE")?.headers["X-Nv-Abtesting"],
    "2",
  );
  assert.equal(
    client.requests.find(({ method, uri }) => method === "SETUP" && uri.includes("video"))?.uri,
    "tracks/actual-video-track",
  );
  assert.deepEqual(
    client.requests.filter(({ method }) => method === "SETUP").map(({ uri }) => uri),
    ["tracks/actual-video-track", "tracks/actual-audio-track", "streamid=control/0"],
  );
  assert.deepEqual(
    client.requests.filter(({ method }) => method === "SETUP").map(({ headers }) => headers.Session),
    ["rtsp-session", "rtsp-session", "rtsp-session"],
  );
  const announce = client.requests.find(({ method }) => method === "ANNOUNCE");
  assert.equal(announce?.uri, "/");
  assert.match(announce?.body ?? "", /m=video 5004/);
  assert.match(announce?.body ?? "", /a=x-nv-general\.clientPorts\.video:45678/);
  assert.equal(client.requests.some(({ method }) => method === "PLAY"), false);

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

test("ping version 6 hands off remote and generated local ICE credentials", async () => {
  const events: string[] = [];
  const remoteUsername = "remote01";
  const remotePassword = "remote-password-with-36-byte-value-001";
  const { client, dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        DESCRIBE_SDP.replace(
          "m=video",
          `a=x-nv-general.iceUserNameFragmentV2:${remoteUsername}\r\na=x-nv-general.icePasswordV2:${remotePassword}\r\nm=video`,
        ),
      );
    }
    if (method === "SETUP") {
      return response({
        transport: "unicast;X-GS-ServerPort=5004-5005;source=192.0.2.4",
        "x-nv-ping": "6",
        "x-nv-ping-payload": "srv1",
      });
    }
    return undefined;
  });

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  assert.equal(negotiated.videoSession.pingVersion, 6);
  assert.equal(negotiated.videoSession.remoteIceUsernameFragment, remoteUsername);
  assert.equal(negotiated.videoSession.remoteIcePassword, remotePassword);
  assert.match(negotiated.videoSession.localIceUsernameFragment ?? "", /^[A-Za-z0-9+/]{4}$/);
  assert.match(negotiated.videoSession.localIcePassword ?? "", /^[A-Za-z0-9+/]{22}$/);
  const announceBody = client.requests.find(({ method }) => method === "ANNOUNCE")?.body ?? "";
  assert.ok(
    announceBody.includes(
      `a=x-nv-general.iceUsernameFragment:${negotiated.videoSession.localIceUsernameFragment}`,
    ),
  );
  assert.ok(
    announceBody.includes(`a=x-nv-general.iceUsernamePwd:${negotiated.videoSession.localIcePassword}`),
  );
  assert.ok(
    announceBody.includes(
      `a=x-nv-general.iceUserNameFragmentV2:${negotiated.videoSession.localIceUsernameFragment}`,
    ),
  );
  assert.ok(
    announceBody.includes(`a=x-nv-general.icePasswordV2:${negotiated.videoSession.localIcePassword}`),
  );
  assert.match(announceBody, /m=video 5004/);
  assert.match(announceBody, /a=x-nv-general\.clientPorts\.video:45678/);
  await negotiated.release("test complete");
});

test("negotiation plays when DESCRIBE leaves disablePlay at 0", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        `${DESCRIBE_SDP.replace("m=video", "a=x-nv-general.disablePlay:0\r\nm=video")}`,
      );
    }
    if (method === "PLAY") {
      return response({}, "", 455);
    }
    return undefined;
  });

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  assert.equal(client.requests.some(({ method, uri }) => method === "PLAY" && uri === "/"), true);
  assert.equal(negotiated.steps.includes("play-455"), true);
  await negotiated.release("test complete");
});

test("negotiation arms native receive after video SETUP and before ANNOUNCE", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events);
  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    onVideoReady: async (videoSession) => {
      events.push(`native-armed:${videoSession.clientUdpPort}`);
    },
  }, dependencies);

  const announceIndex = events.indexOf("request:ANNOUNCE");
  const armedIndex = events.indexOf("native-armed:45678");
  assert.ok(armedIndex >= 0);
  assert.ok(announceIndex > armedIndex);
  assert.equal(negotiated.steps.includes("native-receive-armed"), true);
  assert.equal(client.requests.some(({ method }) => method === "ANNOUNCE"), true);
  await negotiated.release("test complete");
});

test("negotiation starts native WebRtcTransport after ANNOUNCE and before PLAY", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        `${DESCRIBE_SDP.replace("m=video", "a=x-nv-general.disablePlay:0\r\nm=video")}`,
      );
    }
    if (method === "PLAY") {
      return response({}, "", 455);
    }
    return undefined;
  });
  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    onAnnounceReady: async (videoSession) => {
      events.push(`native-announce-armed:${videoSession.clientUdpPort}`);
    },
  }, dependencies);

  const announceIndex = events.indexOf("request:ANNOUNCE");
  const armedIndex = events.indexOf("native-announce-armed:45678");
  const playIndex = events.indexOf("request:PLAY");
  assert.ok(announceIndex >= 0);
  assert.ok(armedIndex > announceIndex);
  assert.ok(playIndex > armedIndex);
  assert.equal(negotiated.steps.includes("native-announce-armed"), true);
  await negotiated.release("test complete");
});

test("negotiation announces reserved WebRtcTransport ICE and DTLS fingerprint", async () => {
  const events: string[] = [];
  const fingerprint = "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF";
  const { client, dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        DESCRIBE_SDP
          .replace("a=x-nv-runtime.encryptionKey:AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899\r\n", "")
          .replace("a=x-nv-runtime.encryptionKeyId:42\r\n", "")
          .replace(
            "m=video",
            [
              "a=x-nv-general.nativeRtcOnBundlePort:1",
              "a=x-nv-general.dtlsFingerprintV2:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99",
              "a=x-nv-general.iceUserNameFragmentV2:srvUfrag",
              "a=x-nv-general.icePasswordV2:srv-password-with-22b-value",
              "m=video",
            ].join("\r\n"),
          ),
      );
    }
    return undefined;
  });
  const originalReserve = dependencies.reserveUdpPort;
  dependencies.reserveUdpPort = async () => {
    const reservation = await originalReserve();
    return {
      ...reservation,
      iceUsernameFragment: "locU",
      icePassword: "local-password-22-chars!",
      dtlsFingerprint: fingerprint,
    };
  };

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  const announceBody = client.requests.find(({ method }) => method === "ANNOUNCE")?.body ?? "";
  assert.ok(announceBody.includes(`a=x-nv-general.dtlsFingerprint:${fingerprint}`));
  assert.ok(announceBody.includes(`a=x-nv-general.dtlsFingerprintV2:${fingerprint}`));
  assert.ok(announceBody.includes("a=x-nv-general.iceUsernameFragment:locU"));
  assert.equal(negotiated.videoSession.localDtlsFingerprint, fingerprint);
  assert.equal(negotiated.videoSession.remoteDtlsFingerprint?.length, 95);
  assert.equal(negotiated.videoSession.remoteIceUsernameFragment, "srvUfrag");
  assert.equal(announceBody.includes("encryptionKey"), false);
  await negotiated.release("test complete");
});

test("negotiation bundles every SETUP onto the video client port when asked", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) => {
    if (method === "DESCRIBE") {
      return response(
        { session: "rtsp-session;timeout=60" },
        `${DESCRIBE_SDP.replace("m=video", "a=x-nv-general.nativeRtcOnBundlePort:1\r\nm=video")}`,
      );
    }
    return undefined;
  });

  const negotiated = await negotiateNvstRtspSession({
    sessionId: "gfn-session",
    rtspsEndpoints: ["rtsps://host.example:322/session/base"],
  }, dependencies);

  const setupPorts = client.requests
    .filter(({ method }) => method === "SETUP")
    .map(({ headers }) => headers.Transport);
  assert.deepEqual(setupPorts, [
    "unicast;X-GS-ClientPort=45678-45679",
    "unicast;X-GS-ClientPort=45678-45679",
    "unicast;X-GS-ClientPort=45678-45679",
  ]);
  assert.match(client.requests.find(({ method }) => method === "ANNOUNCE")?.body ?? "", /nativeRtcOnBundlePort:1/);
  assert.match(client.requests.find(({ method }) => method === "ANNOUNCE")?.body ?? "", /clientPorts\.bundle:45678/);
  assert.equal(negotiated.videoSession.clientUdpPort, 45678);
  await negotiated.release("test complete");
});

test("ping version 6 fails closed without DESCRIBE ICE credentials", async () => {
  const events: string[] = [];
  const { dependencies } = createNegotiationHarness(events, (method) =>
    method === "SETUP"
      ? response({
        transport: "unicast;X-GS-ServerPort=5004-5005;source=192.0.2.4",
        "x-nv-ping": "6",
      })
      : undefined);

  await assert.rejects(
    negotiateNvstRtspSession({
      sessionId: "gfn-session",
      rtspsEndpoints: ["rtsps://host.example:322/session/base"],
    }, dependencies),
    (error: unknown) =>
      error instanceof NvstRtspNegotiationError
      && error.code === "missing-ice-credentials",
  );
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

test("failed ANNOUNCE releases the UDP reservation and closes RTSPS control", async () => {
  const events: string[] = [];
  const { client, dependencies } = createNegotiationHarness(events, (method) =>
    method === "ANNOUNCE" ? response({}, "", 500) : undefined);

  await assert.rejects(
    negotiateNvstRtspSession({
      sessionId: "gfn-session",
      rtspsEndpoints: ["rtsps://host.example:322"],
    }, dependencies),
    (error: unknown) =>
      error instanceof NvstRtspNegotiationError
      && error.code === "negotiation-failed"
      && /ANNOUNCE failed/.test(error.message),
  );

  assert.equal(events.includes("udp-release"), true);
  assert.equal(client.closed, true);
  assert.deepEqual(events.slice(-3), ["udp-release", "request:TEARDOWN", "client-close"]);
});
