# ADR 0001: Do not forward end-user OAuth tokens to the AI service

**Status:** Accepted

## Context

The clinical API must call a Python inference service after authenticating a clinic user. Forwarding the original OAuth access token would make the AI runtime a second consumer of a broad end-user credential and couple it to the external identity provider's claims and lifecycle.

## Decision

Spring Boot validates the external token and completes tenant and role authorization first. For each AI operation it then mints an RS256 internal JWT with:

- issuer `medrag-api`;
- audience `medrag-ai`;
- one operation scope such as `ai:query`, `ai:ingest`, or `ai:purge`;
- verified tenant and actor identifiers;
- request/correlation binding;
- unique `jti` with one-time replay enforcement;
- 60-second expiry and no refresh capability.

FastAPI trusts only the Spring JWKS and independently validates signature, issuer, audience, scope, tenant binding, expiry, request binding, and replay state.

## Consequences

The AI service cannot impersonate the user against the identity provider or other APIs. Key rotation and service availability become explicit operational responsibilities. This is intentional: a narrower, independently verifiable service credential is preferable to distributing a broad user credential.
