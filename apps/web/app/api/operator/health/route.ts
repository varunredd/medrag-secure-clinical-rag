import { randomUUID } from "node:crypto";

import { NextResponse } from "next/server";

import { authenticatedSession } from "@/lib/backend";
import { config } from "@/lib/config";
import { ensureRedis } from "@/lib/redis";

type Check = {
  status: "UP" | "DOWN";
  latencyMs: number;
  code?: string;
};

async function httpCheck(url: string): Promise<Check> {
  const started = performance.now();
  try {
    const response = await fetch(url, {
      cache: "no-store",
      signal: AbortSignal.timeout(2500),
    });
    return {
      status: response.ok ? "UP" : "DOWN",
      latencyMs: Math.round(performance.now() - started),
      code: response.ok ? undefined : `HTTP_${response.status}`,
    };
  } catch (error) {
    return {
      status: "DOWN",
      latencyMs: Math.round(performance.now() - started),
      code: error instanceof Error ? error.name : "UNREACHABLE",
    };
  }
}

async function redisCheck(): Promise<Check> {
  const started = performance.now();
  try {
    await Promise.race([
      ensureRedis().then((client) => client.ping()),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error("TimeoutError")), 2500),
      ),
    ]);
    return { status: "UP", latencyMs: Math.round(performance.now() - started) };
  } catch (error) {
    return {
      status: "DOWN",
      latencyMs: Math.round(performance.now() - started),
      code: error instanceof Error ? error.name : "UNREACHABLE",
    };
  }
}

export async function GET() {
  const current = await authenticatedSession();
  if (!current) {
    return NextResponse.json({ code: "UNAUTHENTICATED" }, { status: 401 });
  }
  if (
    !current.session.user.roles.some((role) =>
      ["CLINIC_ADMIN", "AUDITOR"].includes(role),
    )
  ) {
    return NextResponse.json({ code: "FORBIDDEN" }, { status: 403 });
  }

  const supportCode = randomUUID();
  const [clinicalApi, aiService, identity, objectStorage, sessionStore] =
    await Promise.all([
      httpCheck(config.API_HEALTH_URL),
      httpCheck(`${config.AI_INTERNAL_BASE_URL}/health`),
      httpCheck(config.KEYCLOAK_HEALTH_URL),
      httpCheck(config.STORAGE_HEALTH_URL),
      redisCheck(),
    ]);

  const components = {
    clinicalApi,
    aiService,
    identity,
    objectStorage,
    sessionStore,
    webBff: { status: "UP" as const, latencyMs: 0 },
  };
  const degraded = Object.values(components).some((check) => check.status === "DOWN");

  return NextResponse.json(
    {
      status: degraded ? "DEGRADED" : "UP",
      components,
      generatedAt: new Date().toISOString(),
      supportCode,
    },
    { headers: { "cache-control": "no-store", "x-request-id": supportCode } },
  );
}
