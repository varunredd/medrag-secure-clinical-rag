#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE USER medrag_core WITH PASSWORD '${CORE_DB_PASSWORD}';
  CREATE USER medrag_ai WITH PASSWORD '${AI_DB_PASSWORD}';
  CREATE USER medrag_keycloak WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
  CREATE DATABASE medrag_core OWNER medrag_core;
  CREATE DATABASE medrag_ai OWNER medrag_ai;
  CREATE DATABASE medrag_keycloak OWNER medrag_keycloak;
EOSQL
