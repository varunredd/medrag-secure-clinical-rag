# API Summary

All `/api/v1/**` endpoints require a valid Keycloak access token with audience `medrag-api` and a `tenant_id` claim.

## Documents

- `POST /api/v1/documents` — multipart field `file`; roles DOCTOR, NURSE, CLINIC_ADMIN; returns 202.
- `GET /api/v1/documents` — roles DOCTOR, NURSE, CLINIC_ADMIN, AUDITOR.
- `GET /api/v1/documents/{id}` — tenant-scoped.
- `POST /api/v1/documents/{id}/retry` — DOCTOR or CLINIC_ADMIN.
- `DELETE /api/v1/documents/{id}` — CLINIC_ADMIN; soft deletion and AI purge job.

## Query

- `POST /api/v1/queries` — DOCTOR or NURSE.

Example:

```json
{
  "question": "Summarize the medication changes and supporting dates.",
  "documentIds": ["required-ready-document-uuid"],
  "topK": 8
}
```

Callers must explicitly select 1–20 tenant-owned records in `READY` state. This prevents accidental tenant-wide or cross-patient context. The response includes an answer, evidence citations, confidence, model metadata, and a clinical-use disclaimer.

## Audit

- `GET /api/v1/audit-events` — CLINIC_ADMIN or AUDITOR; paginated and tenant-scoped.

## Internal

- `GET /.well-known/internal-jwks.json` — public keys only, used by FastAPI.
- `/internal/v1/**` on FastAPI — accepts only Spring-issued internal JWTs.


## Operations

- `GET /api/v1/operations/overview` — all clinical roles; tenant-scoped document and pipeline status.
- `POST /api/v1/operations/jobs/{jobId}/redrive` — CLINIC_ADMIN; dead jobs only and rejected when another job for the same document/operation is active.
- `GET /api/v1/upload-policy` — all clinical roles; effective tenant upload size and MIME policy.

## Tenant governance

- `GET /api/v1/tenant-settings` — CLINIC_ADMIN.
- `PUT /api/v1/tenant-settings` — CLINIC_ADMIN; retention, MIME/size policy, clinic display name, and vault-reference private model routing.
- `PATCH /api/v1/documents/{id}/legal-hold` — CLINIC_ADMIN.
- `PATCH /api/v1/documents/{id}/classification` — CLINIC_ADMIN.

Retention is disabled by default. Legal hold blocks user deletion and automated expiry.

## Privacy requests

- `POST /api/v1/export-requests` — CLINIC_ADMIN; creates a `REVIEW_REQUIRED` DSAR/data-export intake record. Only a normalized subject-reference SHA-256 is stored.
- `GET /api/v1/export-requests` — CLINIC_ADMIN or AUDITOR; tenant-scoped review queue.

This is an intake workflow, not automatic disclosure or deletion. Identity verification, approval, redaction, delivery, and legal decisions remain controlled procedures.

## Session audit

- `POST /api/v1/session-events` — called server-to-server by the authenticated BFF to append login/logout evidence.
