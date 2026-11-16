#!/usr/bin/env python3
"""列出 OpenAI 兼容网关的可用模型，并探测 embedding 模型的真实维度。

用途：换到有效 key 后，一条命令确定 EMBEDDING_MODEL / EMBEDDING_DIM /
EMBEDDING_SEND_DIMENSIONS 三个配置，避免猜测维度导致全批同步失败。

安全：密钥只从环境变量读取，绝不写入任何文件、不打印、不进日志。

用法：
  export EMBEDDING_API_KEY='sk-...'
  export EMBEDDING_BASE_URL='https://api.vveai.com'      # 或 .../v1
  python3 deploy/probe-embedding-models.py               # 只列模型
  python3 deploy/probe-embedding-models.py --probe       # 额外探测每个 embedding 模型的维度
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

EMBED_HINTS = ("embed", "bge", "m3e", "gte-", "e5-", "text-embedding", "jina-emb")
SKIP_HINTS = ("rerank", "tts", "whisper", "image", "vision", "audio", "moderation")

# 中文语义选型实测（2026-11-16，同 4 句「相关/无关」基准）：
#   model                     gap    批量延迟
#   Qwen3-Embedding-8B      +0.196   2236ms
#   text-embedding-v3       +0.184   1862ms   <- 综合最优（语义接近最优、快、支持 dimensions、国产合规）
#   bge-m3                  +0.162   2774ms
#   text-embedding-3-small  +0.146   1862ms
#   gemini-embedding-001    +0.138   1695ms（无关句相似度偏高）
# 推荐顺序 = 语义质量优先 + 延迟惩罚，不要按字母序取第一个（会选到最慢的 Qwen3-8B）。
PREFERRED = [
    "text-embedding-v3", "text-embedding-v4", "Qwen3-Embedding-8B",
    "bge-m3", "gemini-embedding-001", "text-embedding-3-small",
]


def base() -> str:
    s = (os.environ.get("EMBEDDING_BASE_URL") or "https://api.vveai.com").strip().rstrip("/")
    if s.endswith("/embeddings"):
        s = s[: -len("/embeddings")]
    return s


def key() -> str:
    k = (os.environ.get("EMBEDDING_API_KEY") or "").strip()
    if not k:
        print("ERROR: 请设置环境变量 EMBEDDING_API_KEY（不会写入任何文件）", file=sys.stderr)
        sys.exit(2)
    return k


def call(url: str, k: str, body=None, timeout=45):
    headers = {"Authorization": "Bearer " + k, "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers,
                                 method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:300]}
    except Exception as e:
        return -1, {"error": "%s: %s" % (type(e).__name__, str(e)[:160])}


def err_msg(d) -> str:
    if not isinstance(d, dict):
        return str(d)[:160]
    e = d.get("error")
    if isinstance(e, dict):
        return str(e.get("message") or e.get("code") or e)[:200]
    return str(d.get("message") or d.get("raw") or e or d)[:200]


def list_models(b: str, k: str):
    code, d = call(b + "/v1/models", k)
    if code != 200:
        print("GET /v1/models -> HTTP %s | %s" % (code, err_msg(d)))
        print()
        print("诊断：")
        print("  - 'This token has expired' = token 已过期，去控制台续期/重置后重发")
        print("  - 'Invalid API key'        = key 字符串有误")
        print("  - 'API key is missing'     = 未传认证头")
        print("  - HTTP 404                 = 网关模型清单路径不同，试 /v1/model 或 /models")
        return []
    items = d.get("data") if isinstance(d, dict) else d
    ids = []
    if isinstance(items, list):
        for x in items:
            if isinstance(x, dict):
                ids.append(str(x.get("id") or x.get("name") or ""))
            else:
                ids.append(str(x))
    return sorted({i for i in ids if i})


def probe(b: str, k: str, model: str, send_dim: bool):
    body = {"model": model, "input": ["维度探测"]}
    if send_dim:
        body["dimensions"] = 1024
    t0 = time.time()
    code, d = call(b + "/v1/embeddings", k, body)
    ms = int((time.time() - t0) * 1000)
    if code != 200:
        return {"model": model, "ok": False, "http": code, "msg": err_msg(d), "ms": ms,
                "dim": None, "send_dim": send_dim}
    data = d.get("data") if isinstance(d, dict) else None
    vec = (data[0].get("embedding") if isinstance(data, list) and data
           and isinstance(data[0], dict) else None)
    dim = len(vec) if isinstance(vec, list) else None
    return {"model": model, "ok": dim is not None, "http": code, "msg": "", "ms": ms,
            "dim": dim, "send_dim": send_dim}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--probe", action="store_true", help="探测 embedding 模型真实维度")
    ap.add_argument("--all", action="store_true", help="对所有模型（而非仅 embedding 类）尝试探测")
    args = ap.parse_args()

    b, k = base(), key()
    print("base_url = %s" % b)
    print("GET  %s/v1/models" % b)
    ids = list_models(b, k)
    if not ids:
        sys.exit(1)
    print("模型总数 = %d" % len(ids))
    print("-" * 78)
    for i in ids:
        tag = "  <= embedding?" if any(h in i.lower() for h in EMBED_HINTS) else ""
        print("  %s%s" % (i, tag))

    if not args.probe:
        print("-" * 78)
        print("加 --probe 参数可探测每个 embedding 模型的真实维度并生成配置建议")
        return

    candidates = [i for i in ids if any(h in i.lower() for h in EMBED_HINTS)
                  and not any(s in i.lower() for s in SKIP_HINTS)]
    if args.all:
        candidates = ids
    if not candidates:
        print("未找到名称含 embedding 特征的模型；可加 --all 对全部模型尝试")
        return

    print("-" * 78)
    print("embedding 探测（每次 1 条短文本）：")
    results = []
    for m in candidates[:20]:
        r = probe(b, k, m, send_dim=True)
        if not r["ok"] and r["http"] == 400:
            # 部分网关不支持 dimensions 参数，重试一次不传
            r2 = probe(b, k, m, send_dim=False)
            if r2["ok"]:
                r2["note"] = "需 EMBEDDING_SEND_DIMENSIONS=false"
                r = r2
        results.append(r)
        status = ("dim=%s %dms" % (r["dim"], r["ms"])) if r["ok"] else \
                 ("FAIL http=%s %s" % (r["http"], r["msg"][:70]))
        print("  %-45s %s%s" % (m[:45], status, (" | " + r["note"]) if r.get("note") else ""))

    ok = [r for r in results if r["ok"]]
    print("-" * 78)
    if ok:
        # 按实测语义质量优先的推荐序挑选，同序内再按延迟排序；未列入 PREFERRED 的排最后
        def rank(r):
            name = r["model"]
            pref = next((i for i, p in enumerate(PREFERRED) if p.lower() in name.lower()), 999)
            return (pref, r["ms"])
        best = sorted(ok, key=rank)[0]
        print("推荐配置（按中文语义质量 + 延迟选型，非字母序）：")
        print("  EMBEDDING_MODE=openai")
        print("  EMBEDDING_BASE_URL=%s" % b)
        print("  EMBEDDING_MODEL=%s" % best["model"])
        print("  EMBEDDING_DIM=%s" % best["dim"])
        if best.get("send_dim") is False or best.get("note"):
            print("  EMBEDDING_SEND_DIMENSIONS=false")
        print("  EMBEDDING_API_KEY=<只在运行时注入，不要写进文件>")
        print()
        print("提示：切换模型或维度后，必须换 QDRANT_COLLECTION 名（或清空旧 collection）")
        print("      重新全量同步，否则不同维度/不同语义空间的向量会被混算。")
    else:
        print("没有可用 embedding 模型；检查该 token 的分组权限或换模型名。")
        sys.exit(1)


if __name__ == "__main__":
    main()
