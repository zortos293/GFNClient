import assert from "node:assert/strict";
import test from "node:test";

import {
  negotiateCodecAnswer,
  negotiatePeerConnectionCodecAnswer,
  type CodecNegotiationPeerConnection,
} from "./codecNegotiation";

function answer(codec: "H264" | "H265" | "AV1", port = 9): string {
  return [
    "v=0",
    `m=video ${port} UDP/TLS/RTP/SAVPF 96`,
    `a=rtpmap:96 ${codec}/90000`,
  ].join("\r\n");
}

test("codec negotiation stops immediately after the first valid answer", async () => {
  const attempts: string[] = [];
  const result = await negotiateCodecAnswer(["AV1", "H264", "H265"], async (codec) => {
    attempts.push(codec);
    return { answer: codec, sdp: answer("AV1") };
  });

  assert.deepEqual(attempts, ["AV1"]);
  assert.equal(result.attemptedCodec, "AV1");
  assert.equal(result.negotiatedCodec, "AV1");
  assert.equal(result.attemptCount, 1);
});

test("codec negotiation retries rejected video once per candidate then terminates", async () => {
  const attempts: string[] = [];

  await assert.rejects(
    negotiateCodecAnswer(["AV1", "H264", "H265"], async (codec) => {
      attempts.push(codec);
      return { answer: codec, sdp: answer(codec, 0) };
    }),
    /after 3 attempt\(s\): AV1, H264, H265/,
  );

  assert.deepEqual(attempts, ["AV1", "H264", "H265"]);
});

test("codec negotiation accepts a fallback negotiated by an earlier attempt", async () => {
  const result = await negotiateCodecAnswer(["AV1", "H264"], async (codec) => ({
    answer: codec,
    sdp: answer("H264"),
  }));

  assert.equal(result.attemptedCodec, "AV1");
  assert.equal(result.negotiatedCodec, "H264");
  assert.equal(result.attemptCount, 1);
});

class FakePeerConnection implements CodecNegotiationPeerConnection {
  signalingState: RTCSignalingState = "stable";
  readonly descriptions: RTCSessionDescriptionInit[] = [];

  constructor(
    private readonly answers: RTCSessionDescriptionInit[],
    private readonly restoreStableOnRollback = true,
  ) {}

  async setRemoteDescription(description: RTCSessionDescriptionInit): Promise<void> {
    this.descriptions.push(description);
    if (description.type === "rollback") {
      assert.equal(this.signalingState, "have-remote-offer");
      if (this.restoreStableOnRollback) {
        this.signalingState = "stable";
      }
      return;
    }
    assert.equal(description.type, "offer");
    assert.equal(this.signalingState, "stable");
    this.signalingState = "have-remote-offer";
  }

  async createAnswer(): Promise<RTCSessionDescriptionInit> {
    assert.equal(this.signalingState, "have-remote-offer");
    const next = this.answers.shift();
    assert.ok(next);
    return next;
  }
}

test("peer connection rolls back the rejected remote offer before running the second codec", async () => {
  const pc = new FakePeerConnection([
    { type: "answer", sdp: answer("AV1", 0) },
    { type: "answer", sdp: answer("H264") },
  ]);
  const prepared: string[] = [];

  const result = await negotiatePeerConnectionCodecAnswer(
    pc,
    ["AV1", "H264"],
    (codec) => `offer-${codec}`,
    (codec) => {
      prepared.push(codec);
    },
  );

  assert.deepEqual(pc.descriptions, [
    { type: "offer", sdp: "offer-AV1" },
    { type: "rollback" },
    { type: "offer", sdp: "offer-H264" },
  ]);
  assert.deepEqual(prepared, ["AV1", "H264"]);
  assert.equal(result.attemptedCodec, "H264");
  assert.equal(result.negotiatedCodec, "H264");
  assert.equal(result.attemptCount, 2);
});

test("peer connection codec retry fails if remote rollback does not restore stable", async () => {
  const pc = new FakePeerConnection(
    [
      { type: "answer", sdp: answer("AV1", 0) },
      { type: "answer", sdp: answer("H264") },
    ],
    false,
  );

  await assert.rejects(
    negotiatePeerConnectionCodecAnswer(
      pc,
      ["AV1", "H264"],
      (codec) => `offer-${codec}`,
    ),
    /remote-offer rollback did not restore stable signaling state/,
  );
  assert.deepEqual(pc.descriptions, [
    { type: "offer", sdp: "offer-AV1" },
    { type: "rollback" },
  ]);
});
