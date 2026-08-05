import { z } from "zod";
const schema=z.object({
  APP_BASE_URL:z.string().url().default("http://localhost:3000"),
  KEYCLOAK_ISSUER:z.string().url().default("http://localhost:8081/realms/medrag"),
  KEYCLOAK_INTERNAL_ISSUER:z.string().url().default("http://localhost:8081/realms/medrag"),
  KEYCLOAK_CLIENT_ID:z.string().min(1).default("medrag-web"),
  KEYCLOAK_CLIENT_SECRET:z.string().min(8),
  KEYCLOAK_API_AUDIENCE:z.string().min(1).default("medrag-api"),
  NEXT_SESSION_KEY_BASE64:z.string().min(40),
  API_INTERNAL_BASE_URL:z.string().url().default("http://localhost:8080"),
  API_HEALTH_URL:z.string().url().default("http://localhost:9091/actuator/health/readiness"),
  AI_INTERNAL_BASE_URL:z.string().url().default("http://localhost:8000"),
  KEYCLOAK_HEALTH_URL:z.string().url().default("http://localhost:9000/health/ready"),
  STORAGE_HEALTH_URL:z.string().url().default("http://localhost:9000/minio/health/ready"),
  REDIS_URL:z.string().default("redis://localhost:6379/1"),
  COOKIE_SECURE:z.enum(["true","false"]).default("true")
});
export const config=schema.parse(process.env);
