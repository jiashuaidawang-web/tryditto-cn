#!/usr/bin/env bash
set -euo pipefail

HOST=${HOST:-root@124.223.220.245}
ROOT=$(cd "$(dirname "$0")/.." && pwd)

(cd "$ROOT/web" && npm run build)
ssh "$HOST" 'mkdir -p /opt/aiditto/web'
rsync -az --delete "$ROOT/web/dist/" "$HOST:/opt/aiditto/web/"
rsync -az "$ROOT/deploy/nginx.conf" "$ROOT/deploy/docker-compose.yml" "$HOST:/opt/aiditto/"
ssh "$HOST" 'cd /opt/aiditto && docker compose up -d && docker exec aiditto-web nginx -s reload'
