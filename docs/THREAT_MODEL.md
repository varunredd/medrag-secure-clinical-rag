# MedRAG Threat Model

## Protected assets

Clinical source documents, extracted chunks, vector indexes, generated answers, tenant configuration, audit records, identity tokens, encryption/signing keys, and privacy-request metadata.

## Trust boundaries

1. Browser to Next.js BFF: opaque HttpOnly session only.
2. BFF to Spring API: external access token over a private server-side channel.
3. Spring to FastAPI: one-time, audience-restricted internal JWT.
4. Services to PostgreSQL, Redis, object storage, and the private model endpoint.
5. Operator/admin actions to retention, legal hold, redrive, and model-routing policy.

## Priority abuse cases and controls

| Threat | Engineering controls | Deployment obligations |
|---|---|---|
| Stolen browser token | OAuth tokens never enter browser storage; opaque sessions, SameSite, origin checks, CSP | TLS, secure cookies, managed Redis, session monitoring |
| Cross-tenant retrieval | Tenant claim from verified token; tenant in every repository query; AI tenant/request comparison | Add PostgreSQL RLS and tenant-isolation tests in release gates |
| User-token confused deputy | Spring-minted 60-second internal JWT with narrow scope/audience and replay cache | Rotate signing keys; prefer mTLS/workload identity |
| Malware in uploaded file | Type/size checks and ClamAV before object persistence | Keep signatures current; alert on scanner unavailability |
| PHI leakage through logs | Pseudonymous stored filenames; no raw text/questions/answers; question hash only in audit | Central log redaction tests and restricted SIEM access |
| Prompt injection | Documents treated as untrusted evidence; no tools/network for model; citations required | Clinical evaluation, egress controls, approved model images |
| Destructive retention error | Retention opt-in; legal hold override; soft-delete plus purge job; append-only audit | Approved policy, dual control, backup/restore and legal review |
| Public-model egress | Private-host allowlist; extractive fallback when model is unset; vault references only in tenant policy | Network deny-by-default and approved secret resolver |
| Dead-letter accumulation | Durable jobs, leases, heartbeats, operator visibility, admin redrive | Paging thresholds and failure runbooks |
| Emergency privilege abuse | Break-glass never expands roles; 15-minute maximum token, reason identifier, visible banner, audit hash on every action | Strong re-authentication, dual control, IdP revocation, post-event review |

## Defensible audit default

Query audit stores a SHA-256 digest of the normalized question, document count, citation count, model identifiers, actor, tenant, request ID, and outcome. It does **not** store the raw question because clinical questions can contain PHI. Deployments needing replayable prompts must create a separately approved encrypted evidence store with explicit retention and access policy rather than weakening the audit log.
