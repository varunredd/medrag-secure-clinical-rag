import { NextRequest, NextResponse } from "next/server";

function configuredOrigin(value: string | undefined, fallback: string): string {
  try {
    return new URL(value || fallback).origin;
  } catch {
    return new URL(fallback).origin;
  }
}

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const development = process.env.NODE_ENV === "development";
  const appOrigin = configuredOrigin(process.env.APP_BASE_URL, request.nextUrl.origin);
  const keycloakOrigin = configuredOrigin(
    process.env.KEYCLOAK_ISSUER,
    "http://localhost:8081/realms/medrag",
  );
  const upgradeInsecure = !development && appOrigin.startsWith("https://");
  const policy = `
    default-src 'self';
    script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ""};
    style-src 'self' 'nonce-${nonce}';
    img-src 'self' blob: data:;
    font-src 'self';
    connect-src 'self';
    object-src 'none';
    base-uri 'self';
    form-action 'self' ${keycloakOrigin};
    frame-ancestors 'none';
    frame-src 'none';
    worker-src 'self' blob:;
    manifest-src 'self';
    ${upgradeInsecure ? "upgrade-insecure-requests;" : ""}
  `
    .replace(/\s{2,}/g, " ")
    .trim();

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", policy);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: [
    {
      source: "/((?!api|_next/static|_next/image|favicon.ico).*)",
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
