# Security Design

## Controls implemented

- OIDC Authorization Code + PKCE
- opaque HttpOnly session cookie; encrypted access/refresh tokens remain in server-side Redis and never enter browser storage
- issuer, signature, expiry, audience, and tenant validation
- route and method RBAC
- least-privilege internal JWT exchange
- anti-token-confusion issuer/audience/algorithm checks in FastAPI
- file size, extension, declared MIME, and detected MIME validation
- ClamAV streaming scan before object storage
- private object buckets and non-guessable object keys
- AES-256-GCM application encryption for chunks and FAISS indexes
- append-only audit event model; login/logout, upload, retry, query-hash, delete, legal hold, classification, redrive, export intake, and tenant-setting changes
- no raw questions, document text, filenames, or answers in logs
- uploaded original filenames are not retained because filenames frequently contain PHI
- correlation IDs across services
- bounded inputs, timeouts, retries, request body limits, and Redis-backed API rate limits
- per-request nonce-based Content Security Policy for rendered pages
- production LLM host allowlisting to reduce accidental public PHI egress
- dependency and container scanning workflows
- reason-bound, maximum-15-minute break-glass claim validation with no role bypass and automatic audit tagging
- local-only client-credentials smoke identity restricted to the synthetic test workflow; it must not exist in non-local realms

## Required production controls outside this repository

- TLS everywhere, including service-to-service traffic; prefer mTLS on the private network
- managed KMS/HSM and envelope encryption; rotate data encryption keys
- OIDC MFA, conditional access, phishing-resistant authenticators for privileged roles
- immutable/WORM audit export and SIEM alerting
- backups, restore tests, disaster recovery, regional residency controls
- formal retention/deletion policy and legal hold process
- BAAs/DPAs and vendor risk reviews
- vulnerability management, penetration testing, SAST/DAST, incident drills
- validated clinical model and human review workflow
- data loss prevention and egress restrictions
- secrets manager rather than environment files
- tenant quota plans, anomaly detection, and adaptive abuse controls beyond the included baseline rate limits

## Threat notes

### Prompt injection

Clinical documents are untrusted. MedRAG never grants the LLM tools or network access. The system prompt states that document instructions are data and must be ignored. Answers must be grounded in retrieved evidence.

### Cross-tenant access

Tenant IDs are derived from verified tokens, never request parameters. Both Spring and FastAPI independently verify tenant binding. Storage keys and database queries are tenant scoped.

### Replay

Internal tokens expire after 60 seconds, include a unique `jti`, are bound to the request correlation ID, and are accepted only once through a Redis replay cache. Production deployments should additionally use workload identity or mTLS and automate signing-key rotation.

### Logging

Do not log PHI. The default logs include request ID, actor ID, tenant ID, action, resource ID, status, and latency only.

## Reporting vulnerabilities

Do not open public issues containing vulnerability details. Follow the private reporting process in `SECURITY.md` at repository root when publishing this project.


The maintained threat model is in [THREAT_MODEL.md](THREAT_MODEL.md). Internal JWT and data-key rotation guidance is in [KEY_ROTATION.md](KEY_ROTATION.md).

## Browser security-header review

Rendered routes receive per-request CSP nonces, `strict-dynamic`, `object-src 'none'`, `frame-ancestors 'none'`, a Keycloak-origin-limited `form-action`, and an HTTPS-only `upgrade-insecure-requests` directive. API routes use `default-src 'none'` and `Cache-Control: no-store`. Next.js also emits `nosniff`, no-referrer, restricted Permissions Policy, COOP, and CORP. Production ingress must add HSTS only after HTTPS is verified for every supported hostname.
