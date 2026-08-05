import { createClient } from "redis";

import { config } from "@/lib/config";

type MedRagRedisClient = ReturnType<typeof createClient>;

const globalForRedis = globalThis as unknown as {
  medragRedis?: MedRagRedisClient;
};

export const redis =
  globalForRedis.medragRedis ?? createClient({ url: config.REDIS_URL });

if (!globalForRedis.medragRedis) {
  globalForRedis.medragRedis = redis;
}

redis.on("error", (error) =>
  console.error(
    "Redis connection error",
    error instanceof Error ? error.message : "unknown",
  ),
);

export async function ensureRedis(): Promise<MedRagRedisClient> {
  if (!redis.isOpen) {
    await redis.connect();
  }
  return redis;
}
