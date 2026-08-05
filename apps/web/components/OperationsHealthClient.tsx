"use client";

import { useCallback, useEffect, useState } from "react";

import { AlertIcon } from "@/components/Icons";

type Check = { status: "UP" | "DOWN"; latencyMs: number; code?: string };
type Health = {
  status: "UP" | "DEGRADED";
  components: Record<string, Check>;
  generatedAt: string;
  supportCode: string;
};

const LABELS: Record<string, string> = {
  webBff: "Next.js BFF",
  clinicalApi: "Spring clinical API",
  aiService: "Private AI service",
  identity: "Keycloak identity",
  objectStorage: "Private object storage",
  sessionStore: "Redis session store",
};

export function OperationsHealthClient() {
  const [health, setHealth] = useState<Health | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const response = await fetch("/api/operator/health", { cache: "no-store" });
      if (response.status === 401) {
        window.location.assign(
          new URL(
            `/api/auth/login?reason=session_expired&returnTo=${encodeURIComponent(window.location.pathname)}`,
            window.location.origin,
          ).toString(),
        );
        return;
      }
      if (!response.ok) throw new Error(`HTTP_${response.status}`);
      setHealth((await response.json()) as Health);
      setError("");
    } catch {
      setError("Operator health aggregation is unavailable. Use the support code from the failing clinical action and inspect container health directly.");
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    const timer = window.setInterval(() => void load(), 15_000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(timer);
    };
  }, [load]);

  return (
    <>
      {error && <div className="notice error"><AlertIcon /> {error}</div>}
      <section className="panel operatorSummary">
        <div>
          <p className="sectionKicker">AGGREGATED READINESS</p>
          <h2>{health?.status === "DEGRADED" ? "Dependency attention required" : "Clinical path ready"}</h2>
          <p>
            Synthetic health checks expose availability and latency only. They never return credentials,
            PHI, tenant data, filenames, or model prompts.
          </p>
        </div>
        <span className={health?.status === "DEGRADED" ? "healthPill warning" : "healthPill"}>
          <span /> {health?.status ?? "CHECKING"}
        </span>
      </section>
      <section className="healthGrid" aria-live="polite">
        {Object.entries(health?.components ?? {}).map(([name, check]) => (
          <article className={check.status === "DOWN" ? "healthCard down" : "healthCard"} key={name}>
            <div className="healthCardHeader">
              <strong>{LABELS[name] ?? name}</strong>
              <span>{check.status}</span>
            </div>
            <b>{check.latencyMs} ms</b>
            <small>{check.code ? `Failure code ${check.code}` : "Readiness response received"}</small>
          </article>
        ))}
      </section>
      {health && (
        <section className="supportCode panel">
          <div>
            <strong>Operator support code</strong>
            <span>{health.supportCode}</span>
          </div>
          <small>Generated {new Date(health.generatedAt).toLocaleString()} · refreshes every 15 seconds</small>
        </section>
      )}
    </>
  );
}
