/// <reference types="node" />

import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import test from "node:test";

import { LogCapture } from "./logger";

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
