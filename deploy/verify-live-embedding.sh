#!/usr/bin/env bash
# 有效 embedding key 到位后的一键全量验收（Step 5 偏离收尾 / Step 9 前置）。
#
# 安全边界：
#  - 密钥只从环境变量 EMBEDDING_API_KEY 读取，绝不写入任何文件、不打印、不进日志。
#  - 只操作 aiditto_test / 评测临时 collection，不动线上 aiditto_pg 与三个线上容器。
#  - 任一步失败立即退出，不带着半套配置继续。
#
# 用法：
#   export EMBEDDING_API_KEY='sk-...'
#   export EMBEDDING_BASE_URL='https://api.vveai.com'
#   bash deploy/verify-live-embedding.sh
set -euo pipefail

cd "$(dirname "$0")/.."
API="$(pwd)/api"
PY=/usr/bin/python3

: "${EMBEDDING_API_KEY:?请先 export EMBEDDING_API_KEY（不会写入任何文件）}"
export EMBEDDING_BASE_URL="${EMBEDDING_BASE_URL:-https://api.vveai.com}"
export QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:16333}"
export TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://127.0.0.1:15432/aiditto_test?stringtype=unspecified}"
export TEST_DB_USER="${TEST_DB_USER:-aiditto}"
export TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-}"

echo "===== 1/4 验证模型可用性与真实维度 ====="
# 选型固定为 2026-11-16 中文语义实测最优的 text-embedding-v3（1024 维）。
# 不采用"每次探测自动挑选"：模型漂移会破坏仿真可复现性。
# 允许显式覆盖：EMBEDDING_MODEL / EMBEDDING_DIM / EMBEDDING_SEND_DIMENSIONS
MODEL="${EMBEDDING_MODEL:-text-embedding-v3}"
DIM="${EMBEDDING_DIM:-1024}"
SEND_DIM="${EMBEDDING_SEND_DIMENSIONS:-true}"

"$PY" deploy/probe-embedding-models.py --probe | tee /tmp/aiditto_model_probe.txt >/dev/null
# 校验选定模型确实可用且维度与配置一致（防止换网关后静默用错维度）
if ! grep -qE "^  ${MODEL//./\\.}[[:space:]]+dim=${DIM}" /tmp/aiditto_model_probe.txt; then
  echo "WARN: 探测输出中未匹配到 ${MODEL} dim=${DIM}，请核对：" >&2
  grep -E "^  .*dim=|^  .*FAIL" /tmp/aiditto_model_probe.txt | head -20 >&2
  echo "如确认无误，可继续；如需改用其他模型请 export EMBEDDING_MODEL / EMBEDDING_DIM" >&2
fi
echo "选定 model=$MODEL dim=$DIM send_dimensions=$SEND_DIM"

export EMBEDDING_MODE=openai
export EMBEDDING_MODEL="$MODEL"
export EMBEDDING_DIM="$DIM"
export EMBEDDING_SEND_DIMENSIONS="$SEND_DIM"
# text-embedding-v3 单请求文本上限 10；每用户 2 条文本，故每批最多 5 用户
export EMBEDDING_BATCH_SIZE="${EMBEDDING_BATCH_SIZE:-5}"
export EMBEDDING_MAX_BATCH_ITEMS="${EMBEDDING_MAX_BATCH_ITEMS:-10}"
# 真实向量必须用独立 collection，禁止与 hash 向量混在同一语义空间
export QDRANT_COLLECTION="match_user_live_$(echo "$MODEL" | tr -c 'A-Za-z0-9' '_')_d${DIM}"
export AIDITTO_QDRANT_IT=1

echo "===== 2/4 全量同步 200 用户真实向量 + 五路召回 ====="
( cd "$API" && mvn -pl aiditto-app -am \
    -Dtest='VectorSyncIT' -Dsurefire.failIfNoSpecifiedTests=false test )

echo "===== 3/4 分桶 Recall@K + 生产规模实验矩阵 ====="
( cd "$API" && mvn -pl aiditto-app -am \
    -Dtest='RecallEvalIT' -Dsurefire.failIfNoSpecifiedTests=false test )
( cd "$API" && AIDITTO_RECALL_MATRIX=1 mvn -pl aiditto-app -am \
    -Dtest='RecallExperimentIT' -Dsurefire.failIfNoSpecifiedTests=false test )

echo "===== 4/4 全量回归（PG）====="
( cd "$API" && mvn verify )

echo
echo "DONE: 真实向量链路验收完成。上线前仍需："
echo "  1) 用真实向量重跑 12 周期合成仿真（Step 9 Go/No-Go 报告）"
echo "  2) 判定单路占比 ≤60% / 候选覆盖率 ≥98% 两个闸门"
echo "  3) 把 collection=$QDRANT_COLLECTION 与模型配置写入生产 .env 后再切流（Step 10）"
