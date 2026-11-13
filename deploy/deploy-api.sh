#!/usr/bin/env bash
# Build the api jar locally, ship it to /opt/aiditto/api/releases, flip current.jar, restart, roll back if unhealthy
# (技术方案 §10.8). Run from a dev machine; the server never builds anything.
#   SKIP_TESTS=1  skip `mvn verify` (integration tests need a local MySQL 8.4, see docs/backend-notes/M1.md)
#   HOST=user@ip  target server
set -euo pipefail

HOST=${HOST:-root@124.223.220.245}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
STAMP=$(date +%Y%m%d-%H%M%S)
JAR="aiditto-api-$STAMP.jar"

cd "$ROOT/api"
if [[ "${SKIP_TESTS:-0}" == 1 ]]; then
  mvn -q -pl aiditto-app -am -DskipTests package
else
  mvn -q -pl aiditto-app -am verify
fi
test -s aiditto-app/target/aiditto-api.jar

ssh "$HOST" 'mkdir -p /opt/aiditto/api/releases /opt/aiditto/data/photos /opt/aiditto/logs/api /opt/aiditto/backups'
rsync -az "$ROOT/deploy/docker-compose.yml" "$ROOT/deploy/nginx.conf" "$ROOT/deploy/proxy_params.inc" \
  "$ROOT/deploy/security_headers.inc" "$HOST:/opt/aiditto/"
rsync -az aiditto-app/target/aiditto-api.jar "$HOST:/opt/aiditto/api/releases/$JAR"

ssh "$HOST" JAR="$JAR" STAMP="$STAMP" 'bash -s' <<'REMOTE'
set -euo pipefail
cd /opt/aiditto

# The api reaches MySQL by container name over this network; idempotent, and needed again if ditto-mysql is recreated.
docker network inspect aiditto-db >/dev/null 2>&1 || docker network create aiditto-db
docker inspect -f '{{json .NetworkSettings.Networks}}' ditto-mysql | grep -q '"aiditto-db"' \
  || docker network connect --alias mysql aiditto-db ditto-mysql

# Snapshot the database before Flyway gets a chance to migrate it.
set -a; . ./.env; set +a
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" ditto-mysql \
  mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --routines "$MYSQL_DATABASE" \
  | gzip > "backups/db-predeploy-$STAMP.sql.gz"

OLD=$(readlink api/current.jar 2>/dev/null || true)
ln -sfn "releases/$JAR" api/current.jar
docker compose up -d api web
docker exec aiditto-web nginx -s reload

for i in $(seq 1 45); do
  if [ "$(docker inspect -f '{{.State.Health.Status}}' aiditto-api 2>/dev/null)" = healthy ]; then
    echo "api healthy on $JAR"
    ls -1t api/releases/aiditto-api-*.jar | tail -n +6 | xargs -r rm -f
    exit 0
  fi
  sleep 2
done

echo "api not healthy after 90s; last log lines:" >&2
docker logs --tail 80 aiditto-api >&2 || true
if [ -n "$OLD" ]; then
  ln -sfn "$OLD" api/current.jar
  docker compose restart api
  echo "rolled back to $OLD" >&2
fi
exit 1
REMOTE
