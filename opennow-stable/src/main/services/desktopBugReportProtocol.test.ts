import assert from "node:assert/strict";
import test from "node:test";
import {
  buildDesktopBugReportFormData,
  desktopBugReportReporterId,
  parseDesktopBugReportResponse,
  parseDesktopBugReportServerError,
} from "./desktopBugReportProtocol";

const VALID_DESCRIPTION =
  "Video stopped after reconnecting, but audio continued and I expected playback to recover without restarting the stream.";

test("desktopBugReportReporterId is stable, namespaced, and pseudonymous", () => {
  const first = desktopBugReportReporterId("installation-a");
  const repeated = desktopBugReportReporterId("installation-a");
  const second = desktopBugReportReporterId("installation-b");

  assert.match(first, /^br1_[0-9a-f]{64}$/);
  assert.equal(first, repeated);
  assert.notEqual(first, second);
  assert.equal(first.includes("installation-a"), false);
});

test("multipart request repeats the files field and carries the desktop contract", async () => {
  const form = buildDesktopBugReportFormData({
    title: "Stream froze",
    description: VALID_DESCRIPTION,
    versionName: "0.9.0",
    versionCode: "45",
    reporterId: desktopBugReportReporterId("installation-a"),
    metadata: { source: "test", sessionReportIncluded: true },
    files: [
      {
        fileName: "opennow.log",
        contentType: "text/plain",
        bytes: Buffer.from("redacted log"),
      },
      {
        fileName: "opennow-session-report.json",
        contentType: "application/json",
        bytes: Buffer.from("{}"),
      },
    ],
  });

  assert.equal(form.get("title"), "Stream froze");
  assert.equal(form.get("description"), VALID_DESCRIPTION);
  assert.equal(form.get("versionName"), "0.9.0");
  assert.equal(form.get("versionCode"), "45");
  assert.equal(form.get("platform"), "desktop");
  assert.match(String(form.get("reporterId")), /^br1_[0-9a-f]{64}$/);
  assert.deepEqual(JSON.parse(String(form.get("metadata"))), {
    source: "test",
    sessionReportIncluded: true,
  });
  const files = form.getAll("files");
  assert.equal(files.length, 2);
  assert.equal((files[0] as File).name, "opennow.log");
  assert.equal(await (files[1] as File).text(), "{}");
});

test("server responses expose bounded public errors and accepted references", () => {
  assert.deepEqual(
    parseDesktopBugReportResponse('{"id":"report-123"}', 201, true),
    { reference: "report-123" },
  );
  assert.deepEqual(
    parseDesktopBugReportServerError(
      '{"error":{"code":"blocked","message":"Bug reporting is disabled for this installation.","retryable":false}}',
      403,
    ),
    {
      code: "blocked",
      message: "Bug reporting is disabled for this installation.",
      retryable: false,
    },
  );
  assert.throws(
    () => parseDesktopBugReportResponse('{"ok":false,"message":"Try again later"}', 200, true),
    /Try again later/,
  );
});

test("multipart validation rejects low-detail reports and oversized attachments", () => {
  const base = {
    title: "Stream froze",
    description: VALID_DESCRIPTION,
    versionName: "0.9.0",
    versionCode: "45",
    reporterId: desktopBugReportReporterId("installation-a"),
    metadata: {},
  };
  assert.throws(
    () => buildDesktopBugReportFormData({ ...base, description: "froze", files: [] }),
    /at least 50 letters or numbers/,
  );
  assert.throws(
    () => buildDesktopBugReportFormData({
      ...base,
      files: [{
        fileName: "large.log",
        contentType: "text/plain",
        bytes: new Uint8Array(10 * 1024 * 1024 + 1),
      }],
    }),
    /larger than 10 MiB/,
  );
});
