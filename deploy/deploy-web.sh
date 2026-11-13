#!/usr/bin/env bash
# Build the frontend (real API by default; VITE_API=mock for the old browser-only demo) and publish it.
# The web build calls /api, so deploy the api first (deploy-api.sh) on a fresh server.
set -euo pipefail

HOST=${HOST:-root@124.223.220.245}
ROOT=$(cd "$(dirname "$0")/.." && pwd)

(cd "$ROOT/web" && npm run build)
ssh "$HOST" 'mkdir -p /opt/aiditto/web /opt/aiditto/data/photos'
rsync -az --delete "$ROOT/web/dist/" "$HOST:/opt/aiditto/web/"
rsync -az "$ROOT/deploy/nginx.conf" "$ROOT/deploy/proxy_params.inc" "$ROOT/deploy/security_headers.inc" \
  "$ROOT/deploy/docker-compose.yml" "$HOST:/opt/aiditto/"
ssh "$HOST" 'cd /opt/aiditto && docker compose up -d web && docker exec aiditto-web nginx -t && docker exec aiditto-web nginx -s reload'
