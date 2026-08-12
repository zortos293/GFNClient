export function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  label: string,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timeout = setTimeout(
      () => reject(new Error(`${label} timed out after ${timeoutMs}ms`)),
      timeoutMs,
    );
    promise.then(
      (value) => {
        clearTimeout(timeout);
        resolve(value);
      },
      (error: unknown) => {
        clearTimeout(timeout);
        reject(error);
      },
    );
  });
}

export async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  label: string,
): Promise<Response> {
  return fetchAndConsumeWithTimeout(
    url,
    init,
    timeoutMs,
    label,
    (response) => Promise.resolve(response),
  );
}

export async function fetchAndConsumeWithTimeout<T>(
  url: string,
  init: RequestInit,
  timeoutMs: number,
  label: string,
  consume: (response: Response) => Promise<T>,
): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(
    () =>
      controller.abort(new Error(`${label} timed out after ${timeoutMs}ms`)),
    timeoutMs,
  );

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
    });
    return await consume(response);
  } catch (error) {
    if (
      (error instanceof Error && error.name === "AbortError") ||
      controller.signal.aborted
    ) {
      const reason = controller.signal.reason;
      const message =
        reason instanceof Error
          ? reason.message
          : `${label} timed out after ${timeoutMs}ms`;
      throw new Error(message);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export async function readResponseTextWithLimit(
  response: Response,
  maxBytes: number,
): Promise<string> {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 0) {
    throw new Error("Response byte limit must be a non-negative safe integer");
  }
  if (!response.body) return "";

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;

  try {
    while (totalBytes < maxBytes) {
      const { done, value } = await reader.read();
      if (done) break;

      const remainingBytes = maxBytes - totalBytes;
      const chunk = value.byteLength <= remainingBytes
        ? value
        : value.subarray(0, remainingBytes);
      chunks.push(chunk);
      totalBytes += chunk.byteLength;

      if (chunk.byteLength < value.byteLength) {
        break;
      }
    }

    if (totalBytes === maxBytes) {
      await reader.cancel();
    }
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(bytes);
}
