import { NextResponse } from "next/server";

import { authenticatedSession } from "@/lib/backend";

export async function GET() {
  const current = await authenticatedSession();
  return NextResponse.json(
    current
      ? {
          authenticated: true,
          user: current.session.user,
          accessExpiresAt: current.session.expiresAt,
          sessionExpiresAt: current.session.refreshExpiresAt,
        }
      : { authenticated: false },
    { headers: { "cache-control": "no-store" } },
  );
}
