#!/usr/bin/env bash
# AI 核心端到端关卡的一键验收（Agent 精排 / AI 文案 / 反馈学习 / 向量同步 / 召回质量）。
#
# 背景：这 6 个 IT 默认被开关关闭，日常 `mvn verify` 显示全绿时不包含它们，
# 等于最关键的 AI 功能平时没人盯着。本脚本一键全开，改完 AI 相关代码后可复现验收。
#
# 安全边界：
#  - 只连 aiditto_test 与评测用 Qdrant collection，不动线上库与线上容器；
#  - 密钥/连接串只从环境变量读取，不写入文件、不打印；
#  - 任一步失败立即退出，不带着半套配置继续。
#
# 前置：本机 SSH 隧道 15432=PG / 16333=Qdrant（脚本会自检，断了会提示重建命令）。
# 用法：
#   bash deploy/verify-ai-core.sh                 # 全档：含最慢的 E1-E14 实验矩阵
#   SKIP_RECALL_MATRIX=1 bash deploy/verify-ai-core.sh   # 快速档：跳过实验矩阵
set -euo pipefail

cd "$(dirname "$0")/.."
API="$(pwd)/api"

export TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://127.0.0.1:15432/aiditto_test?stringtype=unspecified}"
export TEST_DB_USER="${TEST_DB_USER:-aiditto}"
export TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-}"
export QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:16333}"

echo "===== 0/3 前置自检：PG / Qdrant 隧道 ====="
if ! nc -z 127.0.0.1 15432 2>/dev/null; then
  echo "PG 隧道 15432 不通。请先重建：" >&2
  echo "  ssh -f -N -o ServerAliveInterval=30 -L 15432:127.0.0.1:5432 -L 16333:127.0.0.1:6333 root@124.223.220.245" >&2
  exit 1
fi
if ! nc -z 127.0.0.1 16333 2>/dev/null; then
  echo "Qdrant 隧道 16333 不通（向量同步/召回关卡会失败）。请重建上面的隧道。" >&2
  exit 1
fi
echo "隧道 OK：PG=up Qdrant=up"

# 打开全部 AI 核心开关
export AIDITTO_STEP6_IT=1
export AIDITTO_STEP7_IT=1
export AIDITTO_STEP8_IT=1
export AIDITTO_QDRANT_IT=1
export AIDITTO_RECALL_MATRIX="${AIDITTO_RECALL_MATRIX:-1}"

MVN="mvn -o -pl aiditto-app -am -Dsurefire.failIfNoSpecifiedTests=false test"

echo "===== 1/3 AI 功能关卡：精排 / 文案 / 学习 / 向量同步 / 召回准确性 ====="
# 这 5 个不依赖真实 embedding key（Mock LLM + 真 PG/Qdrant），可稳定复现。
(cd "$API" && $MVN -Dtest=AgentRerankIT,AiCopyIT,FeedbackLearningIT,VectorSyncIT,RecallEvalIT)

if [ "${SKIP_RECALL_MATRIX:-0}" = "1" ]; then
  echo "===== 2/3 跳过 E1-E14 实验矩阵（SKIP_RECALL_MATRIX=1）======"
else
  echo "===== 2/3 E1-E14 召回实验矩阵（最慢，生产规模 O(n²)）======"
  (cd "$API" && $MVN -Dtest=RecallExperimentIT)
fi

echo "===== 3/3 汇总 ====="
echo "AI 核心关卡全部通过。日常 mvn verify 不开这些开关，改动 AI 代码后请重跑本脚本。"
