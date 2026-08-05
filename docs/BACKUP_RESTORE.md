# Backup and Restore Runbook

This runbook supports a compliance program; it is not evidence that backups are configured in a deployment.

## Scope

Back up PostgreSQL databases, private source-document buckets, encrypted index buckets, Keycloak configuration, audit exports, and the metadata needed to resolve KMS and internal-JWT key versions. Redis sessions and replay/rate-limit state are disposable; Redis job coordination state must be recreated carefully from PostgreSQL jobs.

## Required production design

- Managed PostgreSQL point-in-time recovery with encrypted cross-account copies.
- Versioned S3 buckets with KMS, access logging, and policy-controlled lifecycle.
- Immutable audit export to a separate security account.
- Key material in KMS/HSM or a secrets manager; never include plaintext keys in ordinary backups.
- Documented RPO/RTO per clinic contract and data-residency region.

## Restore exercise

1. Open a change/incident record and identify the approved restore point.
2. Restore databases and object buckets into an isolated environment.
3. Restore identity configuration without enabling outbound email or federation.
4. Bind the correct historical KMS/key versions.
5. Run Flyway/Alembic validation without destructive repair.
6. Reconcile `clinical_document`, ingestion jobs, encrypted chunks, and FAISS index object generations.
7. Run tenant-isolation, sample upload, query-with-citations, legal-hold, and purge tests using synthetic data.
8. Verify append-only audit continuity and export the exercise evidence.
9. Obtain security, privacy, and service-owner approval before traffic cutover.

Never test restore procedures with copied production PHI on developer laptops.
