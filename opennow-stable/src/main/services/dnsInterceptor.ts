import dns from "node:dns";

const originalLookup = dns.lookup;

const fallbackResolver = new dns.Resolver();
fallbackResolver.setServers(["1.1.1.1", "8.8.8.8"]);

function isNvidiaHostname(hostname: string): boolean {
  if (!hostname) return false;
  const lower = hostname.toLowerCase();
  return lower.endsWith(".nvidiagrid.net") || lower.endsWith(".nvidia.com");
}

export function initDnsInterceptor(): void {
  // @ts-ignore
  dns.lookup = function (
    hostname: string,
    options: any,
    callback: (err: NodeJS.ErrnoException | null, address: string | any[], family?: number) => void
  ) {
    let actualOptions = options;
    let actualCallback = callback;

    if (typeof options === "function") {
      actualCallback = options;
      actualOptions = {};
    }

    if (isNvidiaHostname(hostname)) {
      const family = actualOptions.family;
      if (family === 6) {
        // Fall back to original for IPv6 lookup
        originalLookup(hostname, actualOptions, actualCallback);
        return;
      }

      fallbackResolver.resolve4(hostname, (err, addresses) => {
        if (!err && addresses && addresses.length > 0) {
          const ip = addresses[0];
          console.log(`[DNS Interceptor] Intercepted lookup for ${hostname} -> resolved to ${ip}`);
          if (actualOptions.all) {
            actualCallback(null, [{ address: ip, family: 4 }], 4);
          } else {
            actualCallback(null, ip, 4);
          }
        } else {
          originalLookup(hostname, actualOptions, actualCallback);
        }
      });
      return;
    }

    originalLookup(hostname, actualOptions, actualCallback);
  };

  console.log("[DNS Interceptor] Interceptor initialized successfully.");
}
