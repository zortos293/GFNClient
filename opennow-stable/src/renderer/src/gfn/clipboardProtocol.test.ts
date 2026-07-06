/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import {
  buildClipboardControlMessage,
  CLIPBOARD_CLIENT_DATA_RESPONSE,
  CLIPBOARD_SERVER_DATA_REQUEST,
  GFN_CLIPBOARD_MESSAGE_TYPE,
  GFN_CLIPBOARD_SERVER_RECIPIENT,
  isClipboardServerDataRequest,
  parseClipboardControlMessage,
  validateClipboardText,
} from "./clipboardProtocol";

test("buildClipboardControlMessage matches official PASTE custom-message envelope", () => {
  const message = buildClipboardControlMessage(CLIPBOARD_CLIENT_DATA_RESPONSE, {
    text: "hello",
    tracingData: {
      requestId: "req-1",
      traceId: "trace-1",
      traceContext: [{ key: "k", value: "v" }],
    },
  });

  const customMessage = JSON.parse(message.customMessage);
  assert.equal(customMessage.messageType, "PASTE");
  assert.equal(customMessage.messageRecipient, "UIPlugin");

  const payload = JSON.parse(customMessage.data);
  assert.equal(payload.messageType, "PASTE");
  assert.equal(payload.pasteData.type, CLIPBOARD_CLIENT_DATA_RESPONSE);
  assert.equal(payload.pasteData.data, "hello");
  assert.equal(payload.tracingData.requestId, "req-1");
  assert.equal(payload.tracingData.traceId, "trace-1");
  assert.deepEqual(payload.tracingData.traceContext, [{ key: "k", value: "v" }]);
});

test("parseClipboardControlMessage detects official server data requests", () => {
  const inner = {
    messageType: GFN_CLIPBOARD_MESSAGE_TYPE,
    pasteData: { type: CLIPBOARD_SERVER_DATA_REQUEST },
    tracingData: { requestId: "req-2" },
  };
  const outer = {
    customMessage: JSON.stringify({
      messageType: GFN_CLIPBOARD_MESSAGE_TYPE,
      messageRecipient: GFN_CLIPBOARD_SERVER_RECIPIENT,
      data: JSON.stringify(inner),
    }),
  };

  const payload = parseClipboardControlMessage(outer);
  assert.equal(isClipboardServerDataRequest(payload), true);
  assert.equal(payload?.tracingData?.requestId, "req-2");
});

test("validateClipboardText enforces UTF-8 byte limit", () => {
  assert.equal(validateClipboardText("abc", 3), "abc");
  assert.equal(validateClipboardText("é", 1), null);
  assert.equal(validateClipboardText("", 10), null);
});
