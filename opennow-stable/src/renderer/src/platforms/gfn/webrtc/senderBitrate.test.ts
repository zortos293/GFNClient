/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { updateVideoSenderBitrate } from "./senderBitrate";

function makePeerConnection(options: {
  senders?: RTCRtpSender[];
  transceivers?: RTCRtpTransceiver[];
}): RTCPeerConnection {
  return {
    getSenders: () => options.senders ?? [],
    getTransceivers: () => options.transceivers ?? [],
  } as unknown as RTCPeerConnection;
}

function makeSender(options: {
  kind?: "audio" | "video" | null;
  encodings?: RTCRtpEncodingParameters[];
  setParameters?: (parameters: RTCRtpSendParameters) => Promise<void>;
}): RTCRtpSender {
  return {
    track: options.kind === null
      ? null
      : { kind: options.kind ?? "video" } as MediaStreamTrack,
    getParameters: () => ({
      codecs: [],
      headerExtensions: [],
      rtcp: {},
      encodings: options.encodings ?? [],
      transactionId: "test",
    }),
    setParameters: options.setParameters ?? (async () => {}),
  } as unknown as RTCRtpSender;
}

test("live bitrate update sets maxBitrate on a sending video encoding", async () => {
  const applied: RTCRtpSendParameters[] = [];
  const sender = makeSender({
    encodings: [{ active: true }],
    setParameters: async (parameters) => {
      applied.push(parameters);
    },
  });

  const result = await updateVideoSenderBitrate(
    makePeerConnection({ senders: [sender] }),
    75000,
  );

  assert.deepEqual(result, { status: "updated" });
  assert.equal(applied[0]?.encodings[0]?.maxBitrate, 75000000);
  assert.equal(applied[0]?.encodings[0]?.active, true);
});

test("live bitrate update treats a recv-only video transceiver as unavailable", async () => {
  let setParametersCalled = false;
  const sender = makeSender({
    kind: null,
    setParameters: async () => {
      setParametersCalled = true;
    },
  });
  const transceiver = {
    receiver: { track: { kind: "video" } },
    sender,
  } as unknown as RTCRtpTransceiver;

  const result = await updateVideoSenderBitrate(
    makePeerConnection({ senders: [sender], transceivers: [transceiver] }),
    4000,
  );

  assert.deepEqual(result, { status: "unavailable" });
  assert.equal(setParametersCalled, false);
});

test("live bitrate update reports unavailable and rejected fallbacks", async () => {
  assert.deepEqual(
    await updateVideoSenderBitrate(makePeerConnection({}), 75000),
    { status: "unavailable" },
  );

  const error = new Error("setParameters unsupported");
  const sender = makeSender({
    setParameters: async () => {
      throw error;
    },
  });
  assert.deepEqual(
    await updateVideoSenderBitrate(makePeerConnection({ senders: [sender] }), 75000),
    { status: "unsupported", error },
  );
});
