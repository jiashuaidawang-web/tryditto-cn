-- V9: embedding_version 扩容（Step 5.2 实测发现）。
-- V6 定义为 VARCHAR(16)，但 HashingEmbeddingService.version() = "hash-1024-v1-d1024"(19)，
-- 真实模型名（如 text-embedding-v3-d1024）更长，回填 user_taste 会抛 DataIntegrityViolationException。
-- 现象是 Qdrant 已写入、PG 回填失败，全部标记 failed，必须扩到 64。

ALTER TABLE user_taste ALTER COLUMN embedding_version TYPE VARCHAR(64);

COMMENT ON COLUMN user_taste.embedding_version IS
  'embedding model/version tag; must match Qdrant payload filter, never mix versions in cosine similarity';
