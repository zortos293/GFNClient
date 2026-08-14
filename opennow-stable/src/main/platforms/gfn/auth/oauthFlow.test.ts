import test from "node:test";
import assert from "node:assert/strict";
import net from "node:net";

import {
  openAuthorizationUrlAndWaitForCode,
  waitForAuthorizationCode,
} from "./oauthFlow";

async function getAvailablePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (!address || typeof address === "string") {
        reject(new Error("Unable to allocate test port"));
        return;
      }
      server.close((error) => {
        if (error) reject(error);
        else resolve(address.port);
      });
    });
  });
}

async function assertPortCanBeReused(port: number): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(port, "127.0.0.1", () => {
      server.close((error) => {
        if (error) reject(error);
        else resolve();
      });
    });
  });
}

test("browser launch failure cancels and closes the OAuth callback waiter", async () => {
  const port = await getAvailablePort();
  const browserError = new Error("No default browser is available");

  await assert.rejects(
    openAuthorizationUrlAndWaitForCode(
      "https://login.example/authorize",
      port,
      10_000,
      async () => {
        throw browserError;
      },
    ),
    (error) => error === browserError,
  );

  await assertPortCanBeReused(port);
});

test("OAuth callback timeout closes the server and reports a useful error", async () => {
  const port = await getAvailablePort();

  await assert.rejects(
    waitForAuthorizationCode(port, 20),
    /Timed out waiting for OAuth callback/,
  );

  await assertPortCanBeReused(port);
});

test("aborting OAuth callback waiting closes the server", async () => {
  const port = await getAvailablePort();
  const abortController = new AbortController();
  const codePromise = waitForAuthorizationCode(
    port,
    10_000,
    abortController.signal,
  );
  abortController.abort();

  await assert.rejects(codePromise, /OAuth login was cancelled/);
  await assertPortCanBeReused(port);
});
