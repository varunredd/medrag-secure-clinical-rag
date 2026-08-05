#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
KEYCLOAK_TOKEN_URL="${KEYCLOAK_TOKEN_URL:-http://localhost:8081/realms/medrag/protocol/openid-connect/token}"
SMOKE_CLIENT_ID="${SMOKE_CLIENT_ID:-medrag-smoke}"
SMOKE_CLIENT_SECRET="${SMOKE_CLIENT_SECRET:-dev-only-smoke-secret}"
SAMPLE_FILE="${SAMPLE_FILE:-samples/synthetic-clinical-note.txt}"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }
[[ -f "$SAMPLE_FILE" ]] || { echo "Synthetic sample not found: $SAMPLE_FILE" >&2; exit 1; }

if [[ -z "${ACCESS_TOKEN:-}" ]]; then
  echo "Obtaining a local-only service token for the synthetic smoke client"
  token_json="$(curl -fsS \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=$SMOKE_CLIENT_ID" \
    --data-urlencode "client_secret=$SMOKE_CLIENT_SECRET" \
    "$KEYCLOAK_TOKEN_URL")"
  ACCESS_TOKEN="$(jq -r '.access_token // empty' <<<"$token_json")"
  [[ -n "$ACCESS_TOKEN" ]] || { echo "Could not obtain smoke access token" >&2; exit 1; }
fi

request_id="smoke-$(date +%s)-$RANDOM"
echo "Uploading synthetic record (support code $request_id)"
upload_json="$({ curl -fsS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Request-ID: $request_id" \
  -F "file=@${SAMPLE_FILE};type=text/plain" \
  "$API_URL/api/v1/documents"; } || true)"
document_id="$(jq -r '.id // empty' <<<"$upload_json")"
[[ -n "$document_id" ]] || { echo "Upload failed: $upload_json" >&2; exit 1; }

status="QUEUED"
for _ in $(seq 1 90); do
  document_json="$(curl -fsS \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "X-Request-ID: ${request_id}-poll" \
    "$API_URL/api/v1/documents/$document_id")"
  status="$(jq -r '.status' <<<"$document_json")"
  case "$status" in
    READY) break ;;
    FAILED)
      echo "Ingestion failed: $(jq -r '.failureCode // "UNCLASSIFIED_FAILURE"' <<<"$document_json")" >&2
      exit 1
      ;;
  esac
  sleep 2
done
[[ "$status" == "READY" ]] || { echo "Timed out waiting for READY; last status=$status" >&2; exit 1; }

query_json="$(curl -fsS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Request-ID: ${request_id}-query" \
  -d "$(jq -n --arg id "$document_id" '{question:"What follow-up and urgent-care instructions are documented?",documentIds:[$id],topK:8}')" \
  "$API_URL/api/v1/queries")"

citation_count="$(jq '.citations | length' <<<"$query_json")"
[[ "$citation_count" -gt 0 ]] || { echo "Query returned no citations: $query_json" >&2; exit 1; }

echo "Authenticated upload/query smoke passed"
echo "Document: $document_id"
echo "Generation mode: $(jq -r '.generationModel' <<<"$query_json")"
echo "Citations: $citation_count"
