import { randomBytes } from "node:crypto";

import { cookies } from "next/headers";

import { config } from "@/lib/config";
import { decryptJson, encryptJson } from "@/lib/crypto";
import { ensureRedis } from "@/lib/redis";

export const SESSION_COOKIE = "medrag_session";
export const STATE_COOKIE = "medrag_oidc_state";

export type TokenSession = {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresAt: number;
  refreshExpiresAt: number;
  user: {
    sub: string;
    email?: string;
    name?: string;
    tenantId?: string;
    roles: string[];
    breakGlass?: { expiresAt: number };
  };
};

export type LoginTransaction = {
  verifier: string;
  nonce: string;
  returnTo: string;
};

const cookieOptions = {
  httpOnly: true,
  secure: config.COOKIE_SECURE === "true",
  sameSite: "lax" as const,
  path: "/",
};

export async function createLoginTransaction(
  state: string,
  transaction: LoginTransaction,
): Promise<void> {
  const redis = await ensureRedis();
  await redis.set(`oidc:${state}`, encryptJson(transaction), { EX: 600 });
  const cookieStore = await cookies();
  cookieStore.set(STATE_COOKIE, state, { ...cookieOptions, maxAge: 600 });
}

export async function consumeLoginTransaction(
  state: string,
): Promise<LoginTransaction | null> {
  const cookieStore = await cookies();
  if (cookieStore.get(STATE_COOKIE)?.value !== state) {
    return null;
  }
  cookieStore.delete(STATE_COOKIE);

  const redis = await ensureRedis();
  const key = `oidc:${state}`;
  const encrypted = await redis.get(key);
  await redis.del(key);
  return encrypted ? decryptJson<LoginTransaction>(encrypted) : null;
}

export async function createSession(session: TokenSession): Promise<void> {
  const id = randomBytes(32).toString("base64url");
  const ttl = sessionTtl(session);
  const redis = await ensureRedis();
  const cookieStore = await cookies();
  const previousId = cookieStore.get(SESSION_COOKIE)?.value;

  await redis.set(`session:${id}`, encryptJson(session), { EX: ttl });
  if (previousId && previousId !== id) {
    await redis.del(`session:${previousId}`);
  }
  cookieStore.set(SESSION_COOKIE, id, { ...cookieOptions, maxAge: ttl });
}

export async function readSession(): Promise<{
  id: string;
  session: TokenSession;
} | null> {
  const id = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!id) {
    return null;
  }
  return readSessionById(id);
}

export async function readSessionById(id: string): Promise<{
  id: string;
  session: TokenSession;
} | null> {
  const encrypted = await (await ensureRedis()).get(`session:${id}`);
  if (!encrypted) {
    return null;
  }
  try {
    return { id, session: decryptJson<TokenSession>(encrypted) };
  } catch {
    await invalidateSessionById(id);
    return null;
  }
}

export async function updateSession(
  id: string,
  session: TokenSession,
): Promise<void> {
  await (await ensureRedis()).set(`session:${id}`, encryptJson(session), {
    EX: sessionTtl(session),
  });
}

export async function invalidateSessionById(id: string): Promise<void> {
  await (await ensureRedis()).del(`session:${id}`);
}

export async function destroySession(): Promise<void> {
  const cookieStore = await cookies();
  const id = cookieStore.get(SESSION_COOKIE)?.value;
  if (id) {
    await invalidateSessionById(id);
  }
  cookieStore.delete(SESSION_COOKIE);
}

function sessionTtl(session: TokenSession): number {
  return Math.max(
    60,
    Math.floor((session.refreshExpiresAt - Date.now()) / 1000),
  );
}
