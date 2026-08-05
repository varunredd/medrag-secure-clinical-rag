import { NextRequest, NextResponse } from "next/server";

import { authorizationUrl, randomUrlSafe } from "@/lib/oidc";
import { createLoginTransaction } from "@/lib/session";

export async function GET(request: NextRequest) {
  const returnTo = request.nextUrl.searchParams.get("returnTo") ?? "/dashboard";
  const safeReturn =
    returnTo.startsWith("/") && !returnTo.startsWith("//")
      ? returnTo
      : "/dashboard";
  const forceLogin = request.nextUrl.searchParams.get("reason") === "session_expired";
  const state = randomUrlSafe();
  const nonce = randomUrlSafe();
  const verifier = randomUrlSafe(64);
  await createLoginTransaction(state, {
    nonce,
    verifier,
    returnTo: safeReturn,
  });
  return NextResponse.redirect(
    authorizationUrl(state, nonce, verifier, forceLogin),
  );
}
