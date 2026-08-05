import { NextRequest, NextResponse } from "next/server";

import { recordSessionEvent } from "@/lib/backend";
import { config } from "@/lib/config";
import { exchangeCode } from "@/lib/oidc";
import { consumeLoginTransaction, createSession } from "@/lib/session";

export async function GET(request: NextRequest) {
  const code = request.nextUrl.searchParams.get("code");
  const state = request.nextUrl.searchParams.get("state");
  const error = request.nextUrl.searchParams.get("error");
  if (error || !code || !state) {
    return NextResponse.redirect(`${config.APP_BASE_URL}/?authError=oidc`);
  }
  const transaction = await consumeLoginTransaction(state);
  if (!transaction) {
    return NextResponse.redirect(`${config.APP_BASE_URL}/?authError=state`);
  }
  try {
    const session = await exchangeCode(
      code,
      transaction.verifier,
      transaction.nonce,
    );
    const audited = await recordSessionEvent(session.accessToken, "LOGIN");
    if (!audited) {
      throw new Error("Authenticated session could not be audited");
    }
    await createSession(session);
    return NextResponse.redirect(
      `${config.APP_BASE_URL}${transaction.returnTo}`,
    );
  } catch {
    return NextResponse.redirect(`${config.APP_BASE_URL}/?authError=callback`);
  }
}
