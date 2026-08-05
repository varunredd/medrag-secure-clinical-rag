import { beforeEach, describe, expect, it, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  process.env.KEYCLOAK_CLIENT_SECRET = "test-client-secret";
  process.env.NEXT_SESSION_KEY_BASE64 = Buffer.alloc(32, 7).toString("base64");
});

describe("break-glass access token claims", () => {
  it("accepts a reason-bound token with a short lifetime", async () => {
    const { breakGlassFromAccessPayload } = await import("@/lib/oidc");
    expect(
      breakGlassFromAccessPayload({
        break_glass: true,
        break_glass_reason_id: "INC-2048",
        iat: 1000,
        exp: 1300,
      }),
    ).toEqual({ expiresAt: 1_300_000 });
  });

  it("rejects an unreasoned or long-lived emergency token", async () => {
    const { breakGlassFromAccessPayload } = await import("@/lib/oidc");
    expect(() =>
      breakGlassFromAccessPayload({ break_glass: true, iat: 1000, exp: 1300 }),
    ).toThrow();
    expect(() =>
      breakGlassFromAccessPayload({
        break_glass: true,
        break_glass_reason_id: "INC-2048",
        iat: 1000,
        exp: 2000,
      }),
    ).toThrow();
  });
});
