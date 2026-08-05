import { createHash, randomBytes } from "node:crypto";

import { createRemoteJWKSet, jwtVerify, type JWTPayload } from "jose";

import { config } from "@/lib/config";
import type { TokenSession } from "@/lib/session";

const MEDRAG_ROLES = new Set(["DOCTOR", "NURSE", "CLINIC_ADMIN", "AUDITOR"]);

const jwks = createRemoteJWKSet(
  new URL(
    `${config.KEYCLOAK_INTERNAL_ISSUER}/protocol/openid-connect/certs`,
  ),
);

export function randomUrlSafe(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

export function pkceChallenge(verifier: string): string {
  return createHash("sha256").update(verifier).digest("base64url");
}

export function authorizationUrl(
  state: string,
  nonce: string,
  verifier: string,
  forceLogin = false,
): string {
  const url = new URL(
    `${config.KEYCLOAK_ISSUER}/protocol/openid-connect/auth`,
  );
  const parameters: Record<string, string> = {
    client_id: config.KEYCLOAK_CLIENT_ID,
    response_type: "code",
    scope: "openid profile email",
    redirect_uri: `${config.APP_BASE_URL}/api/auth/callback`,
    state,
    nonce,
    code_challenge: pkceChallenge(verifier),
    code_challenge_method: "S256",
  };
  if (forceLogin) {
    parameters.prompt = "login";
    parameters.max_age = "0";
  }
  url.search = new URLSearchParams(parameters).toString();
  return url.toString();
}

async function tokenRequest(
  body: URLSearchParams,
): Promise<Record<string, unknown>> {
  const response = await fetch(
    `${config.KEYCLOAK_INTERNAL_ISSUER}/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body,
      cache: "no-store",
    },
  );
  if (!response.ok) {
    throw new Error(`OIDC token endpoint failed: ${response.status}`);
  }
  return (await response.json()) as Record<string, unknown>;
}

export async function exchangeCode(
  code: string,
  verifier: string,
  expectedNonce: string,
): Promise<TokenSession> {
  const raw = await tokenRequest(
    new URLSearchParams({
      grant_type: "authorization_code",
      client_id: config.KEYCLOAK_CLIENT_ID,
      client_secret: config.KEYCLOAK_CLIENT_SECRET,
      redirect_uri: `${config.APP_BASE_URL}/api/auth/callback`,
      code,
      code_verifier: verifier,
    }),
  );
  return toSession(raw, expectedNonce);
}

export async function refreshSession(
  refreshToken: string,
  current: TokenSession,
): Promise<TokenSession> {
  const raw = await tokenRequest(
    new URLSearchParams({
      grant_type: "refresh_token",
      client_id: config.KEYCLOAK_CLIENT_ID,
      client_secret: config.KEYCLOAK_CLIENT_SECRET,
      refresh_token: refreshToken,
    }),
  );
  return toSession(raw, undefined, current);
}

async function verifyAccessToken(token: string): Promise<JWTPayload> {
  const verified = await jwtVerify(token, jwks, {
    issuer: config.KEYCLOAK_ISSUER,
    audience: config.KEYCLOAK_API_AUDIENCE,
    algorithms: ["RS256"],
  });
  return verified.payload;
}

export function clinicalRolesFromAccessPayload(payload: JWTPayload): string[] {
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
  const rawRoles: unknown[] = Array.isArray(realmAccess?.roles)
    ? realmAccess.roles
    : [];
  const tokenRoles: string[] = rawRoles
    .filter((role): role is string => typeof role === "string")
    .map((role) => role.trim().toUpperCase())
    .filter((role) => MEDRAG_ROLES.has(role));

  // Every new or refreshed access token must carry the current realm roles.
  // Never retain roles from an older token: role revocation must take effect on refresh.
  return [...new Set(tokenRoles)].sort();
}


export function breakGlassFromAccessPayload(
  payload: JWTPayload,
): { expiresAt: number } | undefined {
  const active = payload.break_glass === true || payload.break_glass === "true";
  if (!active) {
    return undefined;
  }
  const reasonId =
    typeof payload.break_glass_reason_id === "string"
      ? payload.break_glass_reason_id
      : "";
  const issuedAt = typeof payload.iat === "number" ? payload.iat : 0;
  const expiresAt = typeof payload.exp === "number" ? payload.exp : 0;
  if (!reasonId.match(/^[A-Za-z0-9._-]{3,120}$/)) {
    throw new Error("Break-glass token is missing a valid reason identifier");
  }
  if (!issuedAt || !expiresAt || expiresAt - issuedAt > 15 * 60) {
    throw new Error("Break-glass token exceeds the 15-minute lifetime policy");
  }
  return { expiresAt: expiresAt * 1000 };
}

async function toSession(
  raw: Record<string, unknown>,
  expectedNonce?: string,
  existing?: TokenSession,
): Promise<TokenSession> {
  const accessToken = String(raw.access_token ?? "");
  const idToken = raw.id_token ? String(raw.id_token) : existing?.idToken;
  const refreshToken = raw.refresh_token
    ? String(raw.refresh_token)
    : existing?.refreshToken;
  if (!accessToken) {
    throw new Error("Missing access token");
  }

  const access = await verifyAccessToken(accessToken);
  const roles = clinicalRolesFromAccessPayload(access);
  if (roles.length === 0) {
    throw new Error("No MedRAG realm role in access token");
  }

  let profile: JWTPayload | undefined;
  if (idToken) {
    const verifiedId = await jwtVerify(idToken, jwks, {
      issuer: config.KEYCLOAK_ISSUER,
      audience: config.KEYCLOAK_CLIENT_ID,
      algorithms: ["RS256"],
    });
    profile = verifiedId.payload;
    if (expectedNonce && profile.nonce !== expectedNonce) {
      throw new Error("OIDC nonce mismatch");
    }
  }

  const sub = String(access.sub ?? profile?.sub ?? "");
  const tenantId =
    typeof access.tenant_id === "string" ? access.tenant_id : undefined;
  if (!sub || !tenantId) {
    throw new Error("Access token is missing subject or tenant binding");
  }

  const now = Date.now();
  return {
    accessToken,
    refreshToken,
    idToken,
    expiresAt: now + Number(raw.expires_in ?? 300) * 1000,
    refreshExpiresAt: raw.refresh_expires_in
      ? now + Number(raw.refresh_expires_in) * 1000
      : existing?.refreshExpiresAt ?? now + 1_800_000,
    user: {
      sub,
      email:
        typeof profile?.email === "string"
          ? profile.email
          : typeof access.email === "string"
            ? access.email
            : existing?.user.email,
      name:
        typeof profile?.name === "string"
          ? profile.name
          : typeof access.name === "string"
            ? access.name
            : existing?.user.name,
      tenantId,
      roles,
      breakGlass: breakGlassFromAccessPayload(access),
    },
  };
}

export function logoutUrl(idToken?: string): string {
  const url = new URL(
    `${config.KEYCLOAK_ISSUER}/protocol/openid-connect/logout`,
  );
  url.searchParams.set(
    "post_logout_redirect_uri",
    config.APP_BASE_URL.replace(/\/$/, ""),
  );
  url.searchParams.set("client_id", config.KEYCLOAK_CLIENT_ID);
  if (idToken) {
    url.searchParams.set("id_token_hint", idToken);
  }
  return url.toString();
}
