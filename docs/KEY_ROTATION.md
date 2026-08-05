# Internal JWT and Data-Key Rotation

## Internal Spring → FastAPI signing keys

Expose multiple public JWKs during overlap. Spring mints with the active `kid`; FastAPI accepts active and retiring public keys. Deploy in this order: publish new public key, deploy FastAPI/JWKS cache refresh, switch Spring signer, wait beyond maximum token TTL plus clock skew, then remove the retired public key. Record every transition and test old/new tokens at the boundary.

The local single PEM pair is for development only. Production should use a KMS/HSM-backed signer or workload identity and must not mount long-lived private PEM files into general-purpose containers.

## Chunk/index encryption keys

Use envelope encryption and version every ciphertext with a key identifier. Rotate by writing new data with the new key and running a resumable, audited re-encryption job for existing objects. Legal hold and retention state must be preserved. Deleting a key is a cryptographic destruction event and requires privacy/legal approval and verified backup implications.
