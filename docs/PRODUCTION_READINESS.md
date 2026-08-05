# Production Readiness Gate

The project is intentionally production-oriented, but deployment is not production-approved until every item below has evidence.

## Identity and access

- [ ] Production OIDC provider configured with MFA
- [ ] role and tenant claim mappings tested
- [ ] privileged access review completed
- [ ] token lifetimes and revocation tested
- [ ] break-glass accounts controlled and audited
- [ ] local demo users and the `medrag-smoke` client removed from the production realm

## Data protection

- [ ] managed KMS/envelope encryption and workload identity replace static data keys and object-store credentials
- [ ] S3 versioning, object lock, access logging, and lifecycle policies configured
- [ ] database encryption and backups enabled
- [ ] restore test passed
- [ ] data residency and retention approved

## Application security

- [ ] SAST, SCA, container, IaC, and secret scans clean
- [ ] penetration test findings closed
- [ ] included rate limits tuned from load tests; tenant quota plans configured
- [ ] mTLS or workload identity between Spring and FastAPI
- [ ] internal token signing key rotation tested
- [ ] prompt-injection and data-exfiltration evaluation passed

## Clinical safety

- [ ] intended use and prohibited use documented
- [ ] approved clinical embedding and generation models locked
- [ ] representative evaluation dataset approved
- [ ] citation accuracy, hallucination, omission, and bias metrics meet thresholds
- [ ] human-review and escalation workflow implemented
- [ ] model/version lineage retained for each answer

## Operations

- [ ] SLOs, dashboards, paging, and runbooks approved
- [ ] audit events exported to immutable storage/SIEM
- [ ] incident response and breach notification drills completed
- [ ] capacity, load, soak, and failure tests passed
- [ ] blue/green or canary rollback tested


## Build and model supply chain

- [ ] Java, Python, and Node dependency lock/update policy approved
- [ ] embedding model artifact is pinned by immutable revision and baked into the image
- [ ] production AI runtime starts with model-network downloads disabled
- [ ] base images and GitHub Actions are digest/SHA pinned by the release pipeline
- [ ] signed SBOM and provenance attestations are generated for every release


## Repository controls now available for validation

- tenant-scoped operational overview and dead-letter redrive;
- opt-in retention worker and legal-hold override;
- vault-reference-only tenant model policy;
- subject-reference-hash privacy request intake;
- append-only usage metering events;
- operator readiness aggregation and support codes;
- explicit `dev` and `prod-like` Compose profiles, with source-mounted development services and immutable production-like images;
- synthetic authenticated upload/query smoke and k6 evidence-query load script.

These controls support a compliance program but do not replace organizational policies, deployment evidence, vendor agreements, model validation, or certification.
