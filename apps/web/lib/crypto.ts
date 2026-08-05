import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

import { config } from "@/lib/config";

const key = Buffer.from(config.NEXT_SESSION_KEY_BASE64, "base64");
if (key.length !== 32) {
  throw new Error("NEXT_SESSION_KEY_BASE64 must decode to 32 bytes");
}

export function encryptJson(value: unknown): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const plaintext = Buffer.from(JSON.stringify(value));
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ciphertext]).toString("base64url");
}

export function decryptJson<T>(value: string): T {
  const payload = Buffer.from(value, "base64url");
  if (payload.length < 29) {
    throw new Error("Invalid encrypted payload");
  }
  const iv = payload.subarray(0, 12);
  const tag = payload.subarray(12, 28);
  const ciphertext = payload.subarray(28);
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([
    decipher.update(ciphertext),
    decipher.final(),
  ]).toString("utf8");
  return JSON.parse(plaintext) as T;
}
