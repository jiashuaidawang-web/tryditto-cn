package cn.aiditto.recall;

import java.util.List;
import java.util.Map;

/** 召回层数据契约。所有 record 不可变，方便纯函数单测与影子对照复用同一 filter。 */
public final class RecallModel {

    private RecallModel() {}

    public enum Source { R1_TASTE, R2_PERSONA, R3_TAGS, R4_PREFERENCE, R5_EXPLORE }

    /** 一个待写入 Qdrant 的用户点。payload 全部是可索引的过滤字段，不含任何 PII。 */
    public record Point(long userId, int tasteVersion, float[] taste, float[] persona,
                        Map<String, Object> payload) {}

    /**
     * 硬过滤条件。同一实例必须复用于五路召回与 FLAT 精确对照，否则 Recall 指标无意义。
     * isoCells 本轮用 place_id 近似（真实 isochrone 服务尚未接入，已在验收记录标注）。
     */
    public record Filter(
            long selfId,
            String schoolCode,
            List<String> expectedGenders,
            String selfGender,
            Integer ageMin,
            Integer ageMax,
            Integer heightMin,
            Integer heightMax,
            List<String> isoCells,
            List<Long> forbidden,
            String embeddingVersion,
            boolean includeSynthetic,
            String selectivityBucket) {

        public Filter {
            expectedGenders = expectedGenders == null ? List.of() : List.copyOf(expectedGenders);
            isoCells = isoCells == null ? List.of() : List.copyOf(isoCells);
            forbidden = forbidden == null ? List.of() : List.copyOf(forbidden);
        }
    }

    /** 单路召回命中；score 是该路原始分，source 用于 RRF 归因与单路占比监控。 */
    public record Hit(long userId, double score, Source source) {}

    /** ANN 查询结果。engine 记录真实引擎，degraded=true 表示走了结构化兜底。 */
    public record Result(List<Hit> hits, String engine, boolean degraded) {
        public Result {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }

    /** 融合后的候选：RRF 分、粗排分、来源集合。 */
    public record Candidate(long userId, double rrfScore, double coarseScore, List<Source> sources) {
        public Candidate {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    /** 分桶 Recall 评测结果。 */
    public record RecallSample(String bucket, int k, double recall, int annCount, int exactCount,
                               long latencyMs, int hnswEf) {}
}
