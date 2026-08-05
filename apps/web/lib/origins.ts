import { config } from "@/lib/config";

/** Browser origins allowed for same-site form/API mutations in local and production. */
export function allowedBrowserOrigins(): string[] {
  const origins = new Set<string>([
    config.APP_BASE_URL.replace(/\/$/, ""),
    "http://localhost:3000",
    "http://127.0.0.1:3000",
  ]);
  try {
    const base = new URL(config.APP_BASE_URL);
    if (base.hostname === "localhost") {
      origins.add(`${base.protocol}//127.0.0.1${base.port ? `:${base.port}` : ""}`);
    }
  } catch {
    // ignore invalid APP_BASE_URL shape; zod already validates at boot
  }
  return [...origins];
}

/**
 * True when the mutation is a same-site browser request.
 * Allows missing Origin when Sec-Fetch-Site is same-origin (common for form POSTs).
 */
export function isAllowedMutationOrigin(request: {
  headers: Headers;
}): boolean {
  const origin = request.headers.get("origin");
  if (origin) {
    return allowedBrowserOrigins().includes(origin);
  }

  const site = request.headers.get("sec-fetch-site");
  if (site === "same-origin" || site === "none") {
    return true;
  }

  // Last resort: referer on the app base (older agents / odd proxies).
  const referer = request.headers.get("referer");
  if (referer) {
    try {
      const refOrigin = new URL(referer).origin;
      return allowedBrowserOrigins().includes(refOrigin);
    } catch {
      return false;
    }
  }
  return false;
}
