#!/usr/bin/env bash
set -euo pipefail
curl -fsS http://localhost:9091/actuator/health | grep -q '"status":"UP"'
curl -fsS http://localhost:8000/health | grep -q '"status":"ok"'
curl -fsS http://localhost:3000/api/health | grep -q '"status":"ok"'
echo "MedRAG smoke test passed"
