# MedRAG — Secure Clinical Data Summarizer

MedRAG is a production-oriented reference architecture for private clinical document ingestion, retrieval, and grounded summarization. It demonstrates a secure Spring Boot → Python AI trust boundary without forwarding end-user OAuth tokens to the AI service.

> **Important:** This repository is an engineering starting point, not a certified medical device and not automatically HIPAA/GDPR compliant. Compliance depends on deployment, contracts, policies, risk assessment, validation, incident response, retention, and operational controls.

## Architecture

```text
Browser
  │ opaque HttpOnly session ID (tokens encrypted server-side in Redis)
  ▼
Next.js BFF ── Keycloak OIDC Authorization Code + PKCE
  │ external access token (server-to-server only)
  ▼
Spring Boot Clinical API
  ├─ validates issuer/audience/signature/roles
  ├─ enforces tenant isolation and RBAC
  ├─ scans uploads with ClamAV
  ├─ stores private objects with configurable S3/KMS encryption
  ├─ records append-only audit events
  ├─ persists ingestion jobs with bounded parallel workers, leases, heartbeats, and retry
  └─ mints 60-second internal JWT (aud=medrag-ai, least-privilege scope)
         │
         ▼
FastAPI AI Service
  ├─ independently validates internal JWT through Spring JWKS
  ├─ verifies tenant binding and scopes
  ├─ extracts/chunks clinical documents
  ├─ encrypts chunk text with AES-256-GCM
  ├─ stores encrypted FAISS tenant indexes in S3/MinIO
  └─ answers with evidence citations and a local/private LLM provider
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/SECURITY.md](docs/SECURITY.md), [docs/IDENTITY_FEDERATION.md](docs/IDENTITY_FEDERATION.md), [docs/BREAK_GLASS.md](docs/BREAK_GLASS.md), and [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md).

## Included

- Spring Boot 4.1 / Java 21 clinical API
- OAuth 2.0 resource server with Keycloak
- strict `DOCTOR`, `NURSE`, `CLINIC_ADMIN`, `AUDITOR` RBAC
- Redis-backed rate limits at both the Next.js BFF and Spring authorization boundary
- claim-based tenant enforcement at every repository operation
- audience-restricted internal JWT exchange for FastAPI
- FastAPI + Sentence Transformers + FAISS RAG service
- encrypted chunk text and encrypted serialized FAISS indexes
- local/private OpenAI-compatible LLM interface; no public LLM dependency
- Next.js 16.3 BFF with opaque HttpOnly cookies, encrypted Redis token sessions, refresh locking, PKCE, origin checks, and per-request CSP nonces
- PostgreSQL, MinIO, Keycloak, Redis, ClamAV, Prometheus local stack
- Flyway migrations, health checks, tests, CI, dependency scanning
- PHI-safe logging defaults, pseudonymous stored filenames, request correlation IDs, tenant governance, legal holds, and retention foundations

## Quick start

Requirements: Docker Compose, OpenSSL, and at least 8 GB RAM. The first AI start downloads the configured embedding model.

```bash
./scripts/bootstrap-dev.sh
make up-dev       # source-mounted hot reload for web/API/AI
# or: make up     # immutable production-like images
```

Open:

- Web: http://localhost:3000
- Keycloak: http://localhost:8081
- Spring API health: http://localhost:9091/actuator/health
- FastAPI health: http://localhost:8000/health
- MinIO console: http://localhost:9001
- Prometheus: http://localhost:9090

Development users (local realm import only):

| User | Password | Role |
|---|---|---|
| doctor@demo.medrag.local | `ChangeMe123!` | DOCTOR |
| nurse@demo.medrag.local | `ChangeMe123!` | NURSE |
| admin@demo.medrag.local | `ChangeMe123!` | CLINIC_ADMIN |
| auditor@demo.medrag.local | `ChangeMe123!` | AUDITOR |

All demo users belong to tenant `clinic-demo`.

> **Existing local Keycloak volume:** realm imports run only when the realm is first created. If this repository is replacing an older local MedRAG stack, apply the new `realm_access.roles`, audience, tenant, and post-logout client mappers manually, or reset local volumes with `docker compose down -v` before bootstrapping again. That reset permanently deletes all local databases and objects.

## Core workflows

1. Login through Keycloak using Authorization Code + PKCE.
2. Upload a PDF, DOCX, or TXT clinical record.
3. Spring checks role, tenant, size, MIME, and malware status.
4. Spring stores the object and creates a durable ingestion job.
5. The dispatcher mints a short-lived internal token and calls FastAPI.
6. FastAPI extracts and chunks the file, embeds it, updates an encrypted FAISS index, and stores encrypted chunk text.
7. Ask a question; the answer includes document/page citations.

## Commands

```bash
make bootstrap       # create local secrets and RSA keys
make up              # start immutable production-like stack
make up-dev          # source-mounted local development with hot reload
make rebuild-web     # rebuild/recreate immutable web image after UI changes
make down            # stop stack
make test            # run service tests (host tools required)
make smoke           # call health endpoints
make clean            # remove generated local secrets and caches
```

## Product and governance capabilities

- operational overview with live document/pipeline counts and dead-letter redrive;
- evidence-first query workspace with explicit 1–20 document scope, citations, confidence, and watermarked export;
- tenant admin controls for clinic name, allowed MIME types, upload size, opt-in retention, and three explicit generation modes: forced extractive, deployment-wide private model, or Vault-resolved tenant-private model;
- document classification and legal hold controls;
- privacy export/DSAR intake storing only a normalized subject-reference SHA-256;
- operator readiness aggregation that returns availability, latency, and a support code without PHI;
- usage events for future billing without a fabricated payment integration.


## Private-model routing

MedRAG never accepts model credentials from the browser. A Clinic Admin chooses one of three tenant policies:

- `EXTRACTIVE`: return retrieved evidence and citations without calling a generator.
- `PLATFORM_PRIVATE`: use the deployment-wide `LLM_BASE_URL` / `LLM_MODEL` configuration; show a visible extractive fallback when unavailable.
- `PRIVATE_OPENAI_COMPATIBLE`: persist only `vault://path#field` references and a model identifier. FastAPI resolves the endpoint and API key directly from Vault at query time, validates the endpoint against the private-host/allowlist policy, and never returns the secret to Spring.

See [docs/VAULT_INTEGRATION.md](docs/VAULT_INTEGRATION.md) before enabling tenant-private routing.

## Rebuilding the production web image

The default Compose stack runs immutable production images and does not hot-reload. After meaningful web changes run:

```bash
docker compose build web
docker compose up -d --force-recreate web
```

For active development, use `make up-dev` and `docker-compose.dev.yml` instead.

## Authenticated clinical-path smoke test

Run the local synthetic end-to-end smoke after the stack is healthy:

```bash
./scripts/authenticated-smoke.sh
```

The script uses the realm's **local-only** `medrag-smoke` service account, uploads only `samples/synthetic-clinical-note.txt`, waits for `READY`, executes an evidence-scoped query, and requires at least one citation. The smoke client and its development secret must be removed or disabled in every non-local realm. You may instead provide `ACCESS_TOKEN` explicitly.

## Security model in one sentence

The browser never receives OAuth tokens, Spring is the only public clinical API, and FastAPI accepts only short-lived, one-time Spring-issued tokens with explicit audience, tenant, scope, and request binding.

## License

Apache-2.0. See [LICENSE](LICENSE).
