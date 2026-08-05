# Validation evidence

Validation performed in the artifact build environment on 2026-08-05.

## Passed locally

- 12 FastAPI/AI security and behavior tests, including AES-GCM integrity, internal-token policy, grounded-output validation, private-model egress policy, explicit extractive behavior, and Vault reference validation.
- Python bytecode compilation for all 27 application, migration, and test files.
- TypeScript/TSX parser validation for all 42 web source and test files.
- Java syntax parsing for all 62 main and test source files; no Java syntax diagnostics were found. External Spring/Jakarta symbols were unavailable without Maven dependencies.
- JSON, YAML, XML, and shell syntax validation.
- Keycloak structural assertions for `realm_access.roles`, `medrag-api` audience, post-logout redirects, tenant claims, and the local-only synthetic smoke client.
- Git whitespace/error validation with `git diff --check`.

## Environment-limited checks

The build environment could not resolve Maven Central (`repo.maven.apache.org`) and its npm registry mirror did not contain `@types/node@24.3.0`. Docker was not installed. Therefore, the following could not be executed here:

- dependency-resolved Maven tests and Spring context/Flyway validation;
- npm lint, Vitest, typecheck, and Next.js production build;
- Docker image builds, Compose startup, and the authenticated upload → READY → citation query smoke.

The repository includes GitHub Actions and `scripts/authenticated-smoke.sh` to run those checks in a networked environment. A release must not be promoted until those dependency-resolved and runtime checks pass.

This validation is engineering evidence, not clinical validation, regulatory certification, a penetration test, or a compliance attestation.
