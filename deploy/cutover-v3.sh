#!/usr/bin/env bash
# 切 80 端口到 v3（PG + Qdrant + DeepSeek + 埋点）。创始人 2026-11-17 拍板选 B（直接切 80）。
#
# 切法：nginx 上游是容器名 aiditto-api:8080，不改 nginx.conf。
#   1) 旧容器 stop + 改名为 aiditto-api-old（保留，不删）→ 释放 aiditto-api 这个名字
#   2) 起 v3 容器，命名 aiditto-api（加入 aiditto-db + aiditto_default 网络）→ nginx DNS 自动指向新容器
#   3) 健康检查 + 站点验证；失败则停 v3、把旧容器改回 aiditto-api 并 start（秒级确定性回滚）
#
# 安全边界：
#  - 执行前必须有 backups/cutover-*/ 备份（脚本校验）；
#  - 任何失败自动回滚；只动 api 容器，web/PG/Qdrant/MySQL 不动；
#  - 密钥只在 /opt/aiditto-v3/.env（挂载只读），脚本不打印。
#
# 用法（从开发机）：bash deploy/cutover-v3.sh
set -euo pipefail

HOST=${HOST:-root@124.223.220.245}
STAMP=$(date +%Y%m%d-%H%M%S)
V3_JAR=$(ssh "$HOST" "ls -1t /opt/aiditto-v3/releases/aiditto-api-v3-*.jar 2>/dev/null | head -1")

if [ -z "$V3_JAR" ]; then
  echo "服务器没有 v3 jar，请先上传" >&2
  exit 1
fi
echo "使用 jar: $V3_JAR"

ssh "$HOST" "export STAMP='$STAMP' V3_JAR='$V3_JAR'; bash -s" <<'REMOTE'
set -euo pipefail
cd /opt/aiditto

LATEST_BACKUP=$(ls -1dt backups/cutover-* 2>/dev/null | head -1)
if [ -z "$LATEST_BACKUP" ]; then
  echo "没有 cutover 备份，拒绝切流" >&2
  exit 1
fi
echo "使用备份: $LATEST_BACKUP"

rollback() {
  echo "=== 回滚开始 ===" >&2
  docker rm -f aiditto-api >/dev/null 2>&1 || true
  docker rename aiditto-api-old aiditto-api >/dev/null 2>&1 || true
  docker start aiditto-api >/dev/null 2>&1 || true
  docker exec aiditto-web nginx -s reload >/dev/null 2>&1 || true
  echo "=== 回滚完成：旧容器已恢复为 aiditto-api ===" >&2
}
trap 'rollback' ERR

# 1) 旧容器停 + 改名（保留不删）
docker inspect aiditto-api > "$LATEST_BACKUP/old-api-precutover.json"
docker stop aiditto-api
docker rename aiditto-api aiditto-api-old
echo "旧 api 已停并改名 aiditto-api-old（可秒级回滚）"

# 2) 起 v3 api（容器名 aiditto-api，nginx 上游名不变）
#    关键：docker run 用 --env-file 注入变量（挂载文件不会自动变成环境变量）；
#    DB_URL 用容器真名 aiditto-pg16（env 里的 pg16 在 aiditto-db 网络无别名，解析不了）；
#    PG 是 trust 认证，用户 aiditto、空密码。
docker run -d \
  --name aiditto-api \
  --restart unless-stopped \
  --network aiditto-db \
  --env-file /opt/aiditto-v3/.env \
  -v "$V3_JAR":/app/current.jar:ro \
  -v /opt/aiditto/data/photos:/data/photos \
  -v /opt/aiditto/logs/api:/logs \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e TZ=Asia/Shanghai \
  -e DB_URL='jdbc:postgresql://aiditto-pg16:5432/aiditto_pg?stringtype=unspecified' \
  -e DB_USERNAME=aiditto \
  -e DB_PASSWORD='' \
  -e QDRANT_URL='http://aiditto-qdrant:6333' \
  -e QDRANT_COLLECTION=match_user_live_v3 \
  -e EMBEDDING_MODE=openai \
  -e EMBEDDING_MODEL=text-embedding-v3 \
  -e EMBEDDING_DIM=1024 \
  -e EMBEDDING_SEND_DIMENSIONS=true \
  -e EMBEDDING_BATCH_SIZE=5 \
  -e EMBEDDING_MAX_BATCH_ITEMS=10 \
  -e RECALL_ENABLED=true \
  -e RECALL_ISO_ENABLED=true \
  -e MATCH_AGENT_ENABLED=true \
  -e MATCH_AGENT_TOP_PAIRS=15 \
  -e MATCH_AGENT_MIN_TOTAL=72 \
  -e MATCH_COPY_AI_ENABLED=true \
  -e MATCH_COPY_MAX_ATTEMPTS=2 \
  -e LEARNING_ENABLED=true \
  -e LEARNING_MIN_CALIBRATION_SAMPLES=30 \
  -e LEARNING_CALIBRATION_CAP=10 \
  -e MANAGEMENT_PORT=8081 \
  eclipse-temurin:17-jre \
  java -jar /app/current.jar
docker network connect aiditto_default aiditto-api >/dev/null 2>&1 || true
echo "v3 api 已启动，等待健康..."

# 3) 健康检查（用启动日志判断，最可靠）
HEALTHY=0
for i in $(seq 1 60); do
  if docker logs aiditto-api 2>&1 | grep -q 'Started AidittoApplication'; then
    HEALTHY=1; break
  fi
  ST=$(docker inspect -f '{{.State.Status}}' aiditto-api 2>/dev/null)
  [ "$ST" != "running" ] && break
  sleep 2
done
if [ "$HEALTHY" != "1" ]; then
  echo "v3 api 健康检查失败" >&2
  docker logs --tail 100 aiditto-api >&2 || true
  rollback
  exit 1
fi
echo "v3 api healthy"

# 4) nginx reload + 站点验证
docker exec aiditto-web nginx -s reload
sleep 2
SITE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1/)
echo "站点 HTTP=$SITE"
if [ "$SITE" != "200" ]; then
  echo "站点非 200，回滚" >&2
  rollback
  exit 1
fi

trap - ERR
echo "CUTOVER_OK stamp=$STAMP jar=$V3_JAR backup=$LATEST_BACKUP"
echo "回滚命令: docker rm -f aiditto-api && docker rename aiditto-api-old aiditto-api && docker start aiditto-api && docker exec aiditto-web nginx -s reload"
REMOTE
