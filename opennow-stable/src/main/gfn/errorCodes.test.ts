/// <reference types="node" />

import test from "node:test";
import assert from "node:assert/strict";

import { GfnErrorCode, SessionError } from "./errorCodes";

test("SessionError explains insufficient playability as a membership upgrade requirement", () => {
  const error = SessionError.fromResponse(200, JSON.stringify({
    requestStatus: {
      statusCode: 86,
      statusDescription: "INSUFFICIENT_PLAYABILITY_LEVEL",
    },
  }));

  assert.equal(error.gfnErrorCode, GfnErrorCode.SessionInsufficientPlayabilityLevel);
  assert.equal(error.title, "Membership Upgrade Required");
  assert.match(error.message, /GeForce NOW membership is not high enough/i);
  assert.equal(error.isSessionConflict(), false);
});

test("SessionError maps CloudMatch status 91 to app streaming denied", () => {
  const error = SessionError.fromResponse(200, JSON.stringify({
    requestStatus: {
      statusCode: 91,
      statusDescription: "APP_NOT_ALLOWED_TO_STREAM",
    },
  }));

  assert.equal(error.gfnErrorCode, GfnErrorCode.AppNotAllowedToStream);
  assert.equal(error.title, "Streaming Not Allowed");
  assert.match(error.message, /not allowed to stream/i);
});
