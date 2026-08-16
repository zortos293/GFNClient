/// <reference types="node" />

import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";

import { LogCapture, redactSensitiveData } from "./logger";

type ConsoleOutputStreams = NonNullable<ConstructorParameters<typeof LogCapture>[1]>;

class FakeOutputStream extends EventEmitter {
  destroyed = false;
  writable = true;
  writableEnded = false;
}

function writeError(code: string, message = "write failed"): NodeJS.ErrnoException {
  const error = new Error(message) as NodeJS.ErrnoException;
  error.code = code;
  return error;
}

function streams(): {
  outputStreams: ConsoleOutputStreams;
  stdout: FakeOutputStream;
  stderr: FakeOutputStream;
} {
  const stdout = new FakeOutputStream();
  const stderr = new FakeOutputStream();
  return {
    outputStreams: { stdout, stderr },
    stdout,
    stderr,
  };
}

test("captures logs when the original console method synchronously hits a broken pipe", () => {
  const originalLog = console.log;
  const { outputStreams } = streams();
  const capture = new LogCapture("test", outputStreams);
  console.log = () => {
    throw writeError("EPIPE");
  };

  try {
    capture.interceptConsole();
    assert.doesNotThrow(() => console.log("[PostHog] still captured"));
    assert.equal(capture.getCount(), 1);
    assert.equal(capture.getEntries()[0]?.message, "still captured");
  } finally {
    capture.restoreConsole();
    console.log = originalLog;
  }
});

test("captures logs when a console stream asynchronously reports an I/O write error", async () => {
  const originalLog = console.log;
  const { outputStreams, stdout } = streams();
  const capture = new LogCapture("test", outputStreams);
  console.log = () => {
    setImmediate(() => stdout.emit("error", writeError("EIO", "EIO: i/o error, write")));
  };

  try {
    capture.interceptConsole();
    console.log("[PostHog] async failure");
    await new Promise<void>((resolve) => setImmediate(resolve));
    assert.equal(capture.getCount(), 1);
    assert.equal(capture.getEntries()[0]?.message, "async failure");
  } finally {
    capture.restoreConsole();
    console.log = originalLog;
  }
});

test("does not suppress unrelated synchronous console failures", () => {
  const originalError = console.error;
  const { outputStreams } = streams();
  const capture = new LogCapture("test", outputStreams);
  const failure = new Error("console formatter failed");
  console.error = () => {
    throw failure;
  };

  try {
    capture.interceptConsole();
    assert.throws(() => console.error("bad log"), (error) => error === failure);
    assert.equal(capture.getCount(), 1);
  } finally {
    capture.restoreConsole();
    console.error = originalError;
  }
});

test("leaves unrelated asynchronous stream errors to existing reporters", async () => {
  const originalWarn = console.warn;
  const { outputStreams, stderr } = streams();
  const capture = new LogCapture("test", outputStreams);
  const failure = new Error("unexpected stream implementation failure");
  let reported: unknown;
  stderr.on("error", (error) => {
    reported = error;
  });
  console.warn = () => {
    setImmediate(() => stderr.emit("error", failure));
  };

  try {
    capture.interceptConsole();
    console.warn("bad log");
    await new Promise<void>((resolve) => setImmediate(resolve));
    assert.equal(reported, failure);
    assert.equal(capture.getCount(), 1);
  } finally {
    capture.restoreConsole();
    console.warn = originalWarn;
  }
});

test("exports retained diagnostic context before categorized events", () => {
  const capture = new LogCapture("renderer", {});
  capture.setContext("stream.latest", {
    streamKey: "0123...6789ab",
    phase: "streaming",
    codec: "H265",
  });
  capture.addEntry("warn", "NativeStreamer", "Decoder recovery started", []);

  const exported = capture.exportRedacted();

  assert.match(exported, /^OpenNOW Desktop diagnostics/m);
  assert.match(exported, /context\.stream\.latest\.streamKey=0123\.\.\.6789ab/);
  assert.match(exported, /events\.count=1 max=5000/);
  assert.match(exported, /WARN \[NativeStreamer\] Decoder recovery started/);
});

test("appends a bounded previous-run snapshot only to the full export", () => {
  const capture = new LogCapture("main", {});
  capture.setPreviousRunSnapshot({ capturedAt: 1234, text: "prior diagnostic evidence" });

  assert.doesNotMatch(capture.exportCurrentRedacted(), /previousAppRun/);
  assert.match(capture.exportRedacted(), /previousAppRun\.capturedAt=1970-01-01T00:00:01\.234Z/);
  assert.match(capture.exportRedacted(), /prior diagnostic evidence/);
});

test("redacts unlabelled UUIDs, IPv6 addresses, credentials, and user-home paths", () => {
  const raw = [
    "session 01234567-89ab-4cde-8fab-0123456789ab",
    "ipv6=2001:db8::1234",
    "a=ice-pwd:private-credential",
    "Bearer header.payload.signature",
    "/Users/example/OpenNOW/native-streamer",
  ].join("\n");
  const redacted = redactSensitiveData(raw);

  for (const sensitive of [
    "01234567-89ab-4cde-8fab-0123456789ab",
    "2001:db8::1234",
    "private-credential",
    "header.payload.signature",
    "/Users/example",
  ]) {
    assert.doesNotMatch(redacted, new RegExp(sensitive.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});
