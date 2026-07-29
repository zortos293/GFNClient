/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { extractVideoPeer, parseRtspResponse } from "./rtspClient";

test("extractVideoPeer prefers SETUP X-GS-ServerPort", () => {
  assert.deepEqual(
    extractVideoPeer("unicast;X-GS-ServerPort=5004-5005;source=80.250.97.37"),
    { ip: "80.250.97.37", port: 5004 },
  );
});

test("parseRtspResponse preserves status, normalized headers, and SDP body", () => {
  const response = parseRtspResponse(
    "RTSP/1.0 200 OK\r\nCSeq: 2\r\nSession: session-id;timeout=60\r\nContent-Length: 5\r\n\r\nv=0\r\n",
  );
  assert.deepEqual(response, {
    statusCode: 200,
    statusText: "OK",
    headers: {
      cseq: "2",
      session: "session-id;timeout=60",
      "content-length": "5",
    },
    body: "v=0\n",
  });
});
