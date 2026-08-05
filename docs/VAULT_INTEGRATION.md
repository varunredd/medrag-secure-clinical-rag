# Vault integration for tenant-private model routing

MedRAG supports tenant-specific OpenAI-compatible private endpoints without storing endpoint credentials in PostgreSQL or exposing them to the browser. Tenant settings contain only references such as:

```text
vault://medrag/tenants/clinic-demo/llm#endpoint
vault://medrag/tenants/clinic-demo/llm#api-key
```

## Runtime trust boundary

1. A Clinic Admin saves vault references and an approved model identifier through Spring.
2. Spring persists only those references and includes them in the tenant-bound internal request to FastAPI.
3. FastAPI authenticates to Vault with a workload token mounted at `VAULT_TOKEN_FILE`.
4. FastAPI resolves the referenced values in memory, caches them briefly, and calls the endpoint only after private-host or explicit allowlist validation.
5. Secret values are never returned to Spring, written to PostgreSQL, or logged.

If Vault or the model endpoint is unavailable, the query returns grounded extractive evidence with a visible fallback reason. It never attempts a public provider.

## Required configuration

```bash
VAULT_ADDR=https://vault.internal.example
VAULT_TOKEN_FILE=/vault/secrets/token
VAULT_NAMESPACE=
VAULT_TIMEOUT_SECONDS=3
VAULT_CACHE_SECONDS=60
LLM_ALLOWED_HOSTS=llm.internal.example
```

Use Kubernetes auth, AWS IAM auth, or another workload identity mechanism to place a short-lived Vault token in the configured file. Do not put a long-lived Vault token in `.env`, Compose files, images, or source control.

## Minimum Vault policy

Issue a tenant-aware or narrowly scoped identity to the AI service. A baseline KV v2 policy should grant read-only access to the approved model-secret prefix and nothing else:

```hcl
path "medrag/data/tenants/*/llm" {
  capabilities = ["read"]
}
```

For stronger tenant isolation, use per-tenant Vault namespaces or dynamically issue a scoped child token based on the authenticated tenant. The shared wildcard policy above is a reference starting point, not the strongest production posture.

## Rotation

- Rotate model API keys in Vault without changing the stored reference.
- Keep `VAULT_CACHE_SECONDS` short enough to meet the credential-revocation objective.
- Revoke the AI workload token immediately during an incident.
- Record Vault audit-device logs in the security logging pipeline without copying secret values.

## Network controls

Vault resolution is not a substitute for egress control. Production should enforce deny-by-default network policy from FastAPI, allowing only Vault, object storage, Spring JWKS, and explicitly approved private model hosts. DNS rebinding protection should also be enforced at the network/proxy layer in addition to application host validation.
