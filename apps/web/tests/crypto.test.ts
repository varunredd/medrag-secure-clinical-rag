import { beforeEach, describe, expect, it, vi } from "vitest";

beforeEach(() => {
  vi.resetModules();
  process.env.KEYCLOAK_CLIENT_SECRET = "test-client-secret";
  process.env.NEXT_SESSION_KEY_BASE64 = Buffer.alloc(32, 7).toString("base64");
});

describe("encrypted server-side sessions", () => {
  it("round-trips authenticated session data", async () => {
    const { decryptJson, encryptJson } = await import("@/lib/crypto");
    const input = { accessToken: "not-visible-to-browser", tenantId: "clinic-a" };

    expect(decryptJson(encryptJson(input))).toEqual(input);
  });

  it("rejects ciphertext tampering", async () => {
    const { decryptJson, encryptJson } = await import("@/lib/crypto");
    const encrypted = Buffer.from(encryptJson({ role: "DOCTOR" }), "base64url");
    encrypted[encrypted.length - 1] ^= 1;

    expect(() => decryptJson(encrypted.toString("base64url"))).toThrow();
  });
});
