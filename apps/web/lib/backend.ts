import { randomBytes, randomUUID } from "node:crypto";

import { config } from "@/lib/config";
import { refreshSession } from "@/lib/oidc";
import { ensureRedis } from "@/lib/redis";
import {
  destroySession,
  invalidateSessionById,
  readSession,
  readSessionById,
  updateSession,
} from "@/lib/session";

const RATE_LIMIT_SCRIPT = `
local current = redis.call('INCR', KEYS[1])
if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return current
`;

const RELEASE_LOCK_SCRIPT = `
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
else
  return 0
end
`;

export async function authenticatedSession() {
  const current = await readSession();
  if (!current) {
    return null;
  }
  if (current.session.expiresAt > Date.now() + 60_000) {
    return current;
  }
  if (!current.session.refreshToken) {
    await invalidateSessionById(current.id);
    return null;
  }

  const redis = await ensureRedis();
  const lockKey = `session-refresh:${current.id}`;
  const lockToken = randomBytes(24).toString("base64url");
  const acquired = await redis.set(lockKey, lockToken, { NX: true, EX: 10 });

  if (!acquired) {
    for (let attempt = 0; attempt < 20; attempt += 1) {
      await new Promise((resolve) => setTimeout(resolve, 100));
      const updated = await readSessionById(current.id);
      if (!updated) {
        return null;
      }
      if (updated.session.expiresAt > Date.now() + 30_000) {
        return updated;
      }
    }
    return null;
  }

  try {
    const latest = await readSessionById(current.id);
    if (!latest) {
      return null;
    }
    if (latest.session.expiresAt > Date.now() + 60_000) {
      return latest;
    }
    if (!latest.session.refreshToken) {
      await invalidateSessionById(latest.id);
      return null;
    }
    const refreshed = await refreshSession(
      latest.session.refreshToken,
      latest.session,
    );
    await updateSession(latest.id, refreshed);
    return { id: latest.id, session: refreshed };
  } catch {
    await invalidateSessionById(current.id);
    return null;
  } finally {
    await redis
      .eval(RELEASE_LOCK_SCRIPT, {
        keys: [lockKey],
        arguments: [lockToken],
      })
      .catch(() => undefined);
  }
}

async function allowed(
  sessionId: string,
  path: string,
  method: string,
): Promise<boolean> {
  const normalizedMethod = method.toUpperCase();
  const query = normalizedMethod === "POST" && path.includes("/queries");
  const upload = normalizedMethod === "POST" && path.split("?")[0].endsWith("/documents");
  const limit = query ? 30 : upload ? 20 : 120;
  const windowSeconds = 60;
  const bucket = Math.floor(Date.now() / (windowSeconds * 1000));
  const value = await (await ensureRedis()).eval(RATE_LIMIT_SCRIPT, {
    keys: [
      `rate:${sessionId}:${query ? "query" : upload ? "upload" : "api"}:${bucket}`,
    ],
    arguments: [String(windowSeconds)],
  });
  return Number(value) <= limit;
}

export async function backendFetch(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const current = await authenticatedSession();
  if (!current) {
    await destroySession().catch(() => undefined);
    return unauthenticatedResponse();
  }
  if (!(await allowed(current.id, path, init.method ?? "GET"))) {
    return new Response(
      JSON.stringify({ code: "RATE_LIMITED", detail: "Too many requests" }),
      {
        status: 429,
        headers: {
          "content-type": "application/json",
          "retry-after": "60",
        },
      },
    );
  }

  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${current.session.accessToken}`);
  headers.set("X-Request-ID", randomUUID());
  const response = await fetch(`${config.API_INTERNAL_BASE_URL}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });

  if (response.status === 401) {
    await invalidateSessionById(current.id).catch(() => undefined);
    await destroySession().catch(() => undefined);
    return unauthenticatedResponse(response.headers.get("x-request-id"));
  }
  return response;
}

function unauthenticatedResponse(requestId?: string | null): Response {
  const headers: Record<string, string> = {
    "content-type": "application/json",
    "x-medrag-auth-state": "expired",
  };
  if (requestId) {
    headers["x-request-id"] = requestId;
  }
  return new Response(
    JSON.stringify({
      code: "UNAUTHENTICATED",
      detail: "Your secure session expired. Sign in again to continue.",
      requestId: requestId ?? undefined,
    }),
    { status: 401, headers },
  );
}

export async function recordSessionEvent(
  accessToken: string,
  event: "LOGIN" | "LOGOUT",
): Promise<boolean> {
  try {
    const response = await fetch(
      `${config.API_INTERNAL_BASE_URL}/api/v1/session-events`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "X-Request-ID": randomUUID(),
        },
        body: JSON.stringify({ event }),
        cache: "no-store",
      },
    );
    return response.ok;
  } catch {
    return false;
  }
}
