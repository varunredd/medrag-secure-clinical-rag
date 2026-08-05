import { NextRequest, NextResponse } from "next/server";

import { recordSessionEvent } from "@/lib/backend";
import { config } from "@/lib/config";
import { logoutUrl } from "@/lib/oidc";
import { isAllowedMutationOrigin } from "@/lib/origins";
import { destroySession, readSession } from "@/lib/session";

export async function POST(request: NextRequest) {
  if (!isAllowedMutationOrigin(request)) {
    return NextResponse.json({ code: "INVALID_ORIGIN" }, { status: 403 });
  }

  const current = await readSession();
  if (current) {
    await recordSessionEvent(current.session.accessToken, "LOGOUT");
  }
  await destroySession();

  let redirectTo = config.APP_BASE_URL;
  try {
    redirectTo = logoutUrl(current?.session.idToken);
  } catch {
    // Local session is already gone; fall back to app home.
  }

  const accept = request.headers.get("accept") ?? "";
  const wantsJson =
    accept.includes("application/json") ||
    request.headers.get("x-medrag-client") === "1";

  if (wantsJson) {
    return NextResponse.json({ redirectTo });
  }
  return NextResponse.redirect(redirectTo, { status: 303 });
}
