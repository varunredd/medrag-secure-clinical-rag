#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p secrets
umask 077

if [[ ! -f secrets/internal-private.pem ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/internal-private.pem
  openssl rsa -pubout -in secrets/internal-private.pem -out secrets/internal-public.pem
fi

random_b64() { openssl rand -base64 32 | tr -d '\n'; }
random_hex() { openssl rand -hex 24; }

if [[ ! -f .env ]]; then
  cat > .env <<EOF
POSTGRES_SUPER_PASSWORD=$(random_hex)
CORE_DB_PASSWORD=$(random_hex)
AI_DB_PASSWORD=$(random_hex)
KEYCLOAK_ADMIN_PASSWORD=$(random_hex)
KEYCLOAK_DB_PASSWORD=$(random_hex)
KEYCLOAK_WEB_CLIENT_SECRET=dev-only-change-me
MINIO_ROOT_USER=medrag
MINIO_ROOT_PASSWORD=$(random_hex)
AI_ENCRYPTION_KEY_BASE64=$(random_b64)
NEXT_SESSION_KEY_BASE64=$(random_b64)
INTERNAL_JWT_KEY_ID=medrag-internal-dev-1
LLM_BASE_URL=
LLM_API_KEY=local-only
LLM_MODEL=
EOF
  chmod 600 .env
fi

echo "Generated local .env and RSA keys. These files are gitignored and development-only."
