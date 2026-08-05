import { beforeEach, describe, expect, it, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  process.env.KEYCLOAK_CLIENT_SECRET = "test-client-secret";
  process.env.NEXT_SESSION_KEY_BASE64 = Buffer.alloc(32, 7).toString("base64");
});

describe("clinical access-token roles", () => {
  it("normalizes and filters realm_access roles", async () => {
    const { clinicalRolesFromAccessPayload } = await import("@/lib/oidc");

    expect(
      clinicalRolesFromAccessPayload({
        realm_access: {
          roles: ["doctor", "AUDITOR", "offline_access", "doctor"],
        },
      }),
    ).toEqual(["AUDITOR", "DOCTOR"]);
  });

  it("fails closed when a refreshed token omits clinical roles", async () => {
    const { clinicalRolesFromAccessPayload } = await import("@/lib/oidc");

    expect(
      clinicalRolesFromAccessPayload({ realm_access: { roles: [] } }),
    ).toEqual([]);
  });
});
