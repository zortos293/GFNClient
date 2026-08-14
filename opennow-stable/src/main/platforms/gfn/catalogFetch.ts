import { fetchWithOptionalProxy } from "./proxyFetch";
import { normalizeSessionProxyUrl } from "./proxyUrl";

export const CATALOG_PROXY_TIMEOUT_MS = 10_000;
export const CATALOG_PROXY_RETRY_AFTER_MS = 60_000;

const CATALOG_PROXY_FALLBACK_STATUSES = new Set([407, 408, 425, 429, 500, 502, 503, 504]);

type ProxyFetch = (
  input: string,
  init: RequestInit | undefined,
  proxyUrl?: string,
) => Promise<Response>;

interface CatalogFetcherDependencies {
  proxyFetch: ProxyFetch;
  directFetch: (input: string, init?: RequestInit) => Promise<Response>;
  now?: () => number;
  proxyTimeoutMs?: number;
  retryAfterMs?: number;
  warn?: (message: string) => void;
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

export function createCatalogFetcher({
  proxyFetch,
  directFetch,
  now = Date.now,
  proxyTimeoutMs = CATALOG_PROXY_TIMEOUT_MS,
  retryAfterMs = CATALOG_PROXY_RETRY_AFTER_MS,
  warn = (message) => console.warn(message),
}: CatalogFetcherDependencies): ProxyFetch {
  const retryDirectUntilByProxy = new Map<string, number>();

  return async (input, init, proxyUrl) => {
    const normalizedProxyUrl = normalizeSessionProxyUrl(proxyUrl);
    if (!normalizedProxyUrl) {
      return directFetch(input, init);
    }

    if ((retryDirectUntilByProxy.get(normalizedProxyUrl) ?? 0) > now()) {
      return directFetch(input, init);
    }

    const timeoutController = new AbortController();
    const callerSignal = init?.signal;
    const abortFromCaller = (): void => timeoutController.abort(callerSignal?.reason);
    if (callerSignal?.aborted) {
      abortFromCaller();
    } else {
      callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
    }

    const timeoutError = new Error(`Catalog proxy request timed out after ${proxyTimeoutMs}ms`);
    let timeout: ReturnType<typeof setTimeout> | undefined;
    const timeoutPromise = new Promise<never>((_resolve, reject) => {
      timeout = setTimeout(() => {
        timeoutController.abort(timeoutError);
        reject(timeoutError);
      }, proxyTimeoutMs);
    });

    try {
      const response = await Promise.race([
        proxyFetch(input, { ...init, signal: timeoutController.signal }, normalizedProxyUrl),
        timeoutPromise,
      ]);

      if (!CATALOG_PROXY_FALLBACK_STATUSES.has(response.status)) {
        retryDirectUntilByProxy.delete(normalizedProxyUrl);
        return response;
      }

      await response.body?.cancel().catch(() => undefined);
      retryDirectUntilByProxy.set(normalizedProxyUrl, now() + retryAfterMs);
      warn(`[Games] Catalog proxy returned HTTP ${response.status}; retrying directly.`);
    } catch (error) {
      if (callerSignal?.aborted) {
        throw error;
      }

      retryDirectUntilByProxy.set(normalizedProxyUrl, now() + retryAfterMs);
      warn(`[Games] Catalog proxy request failed; retrying directly: ${errorMessage(error)}`);
    } finally {
      if (timeout !== undefined) {
        clearTimeout(timeout);
      }
      callerSignal?.removeEventListener("abort", abortFromCaller);
    }

    return directFetch(input, init);
  };
}

export const fetchCatalogRequest = createCatalogFetcher({
  proxyFetch: fetchWithOptionalProxy,
  directFetch: (input, init) => fetch(input, init),
});
