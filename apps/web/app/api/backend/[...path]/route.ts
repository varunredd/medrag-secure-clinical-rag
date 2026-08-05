import { NextRequest } from "next/server";

import { backendFetch } from "@/lib/backend";
import { isAllowedMutationOrigin } from "@/lib/origins";

async function proxy(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  if (!["GET", "HEAD", "OPTIONS"].includes(request.method)) {
    if (!isAllowedMutationOrigin(request)) {
      return new Response(JSON.stringify({ code: "INVALID_ORIGIN" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      });
    }
  }

  const { path } = await params;
  if (
    path.length < 2 ||
    path[0] !== "api" ||
    path[1] !== "v1" ||
    path.some((segment) => !segment.match(/^[A-Za-z0-9._-]+$/))
  ) {
    return new Response(JSON.stringify({ code: "INVALID_PROXY_PATH" }), {
      status: 400,
      headers: { "content-type": "application/json" },
    });
  }

  const target = `/${path.join("/")}${request.nextUrl.search}`;
  const headers = new Headers();
  const contentType = request.headers.get("content-type");
  if (contentType) {
    headers.set("content-type", contentType);
  }
  const accept = request.headers.get("accept");
  if (accept) {
    headers.set("accept", accept);
  }

  const body = ["GET", "HEAD"].includes(request.method)
    ? undefined
    : await request.arrayBuffer();

  const response = await backendFetch(target, {
    method: request.method,
    headers,
    body,
    redirect: "manual",
  });

  const outHeaders = new Headers();
  for (const key of [
    "content-type",
    "x-request-id",
    "x-medrag-auth-state",
    "location",
    "retry-after",
  ]) {
    const value = response.headers.get(key);
    if (value) {
      outHeaders.set(key, value);
    }
  }

  return new Response(response.body, {
    status: response.status,
    headers: outHeaders,
  });
}

export const GET = proxy;
export const POST = proxy;
export const DELETE = proxy;
export const PUT = proxy;
export const PATCH = proxy;
