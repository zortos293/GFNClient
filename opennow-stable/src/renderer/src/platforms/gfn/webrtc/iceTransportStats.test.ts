import assert from "node:assert/strict";
import test from "node:test";

import { extractActiveIceTransportStats } from "./iceTransportStats";

test("extracts UDP and local candidate type from the nominated pair", () => {
  const result = extractActiveIceTransportStats([
    {
      id: "pair-1",
      type: "candidate-pair",
      state: "succeeded",
      nominated: true,
      localCandidateId: "local-1",
    },
    {
      id: "local-1",
      type: "local-candidate",
      protocol: "UDP",
      candidateType: "srflx",
    },
  ]);

  assert.equal(result.activePair?.id, "pair-1");
  assert.equal(result.transportType, "udp");
  assert.equal(result.localCandidateType, "srflx");
});

test("uses the transport-selected TCP pair when nomination is unavailable", () => {
  const result = extractActiveIceTransportStats([
    {
      id: "pair-tcp",
      type: "candidate-pair",
      state: "succeeded",
      localCandidateId: "local-tcp",
      transportId: "transport-1",
    },
    {
      id: "transport-1",
      type: "transport",
      selectedCandidatePairId: "pair-tcp",
      protocol: "tcp",
    },
    {
      id: "local-tcp",
      type: "local-candidate",
      protocol: "tcp",
      candidateType: "relay",
    },
  ]);

  assert.equal(result.activePair?.id, "pair-tcp");
  assert.equal(result.transportType, "tcp");
  assert.equal(result.localCandidateType, "relay");
});

test("returns unknown transport when the active pair has no candidate details", () => {
  assert.deepEqual(
    extractActiveIceTransportStats([
      {
        id: "pair-1",
        type: "candidate-pair",
        state: "succeeded",
        nominated: true,
      },
    ]),
    {
      activePair: {
        id: "pair-1",
        type: "candidate-pair",
        state: "succeeded",
        nominated: true,
      },
      transportType: "unknown",
      localCandidateType: "",
    },
  );
});
