import test from "node:test";
import assert from "node:assert/strict";

import { shouldReportRendererTermination } from "./rendererLifecycle";

test("suppresses renderer termination after app shutdown is requested", () => {
  for (const reason of ["clean-exit", "killed", "crashed", "oom"] as const) {
    assert.equal(shouldReportRendererTermination(reason, true), false);
  }
});

test("reports unexpected killed, crashed, and OOM renderers", () => {
  for (const reason of ["killed", "crashed", "oom"] as const) {
    assert.equal(shouldReportRendererTermination(reason, false), true);
  }
});

test("preserves reporting for renderer exits when shutdown was not requested", () => {
  assert.equal(shouldReportRendererTermination("clean-exit", false), true);
  assert.equal(shouldReportRendererTermination("abnormal-exit", false), true);
});
