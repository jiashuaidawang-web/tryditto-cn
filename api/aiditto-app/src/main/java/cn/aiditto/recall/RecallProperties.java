package cn.aiditto.recall;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 召回配置。Qdrant 地址与 embedding 密钥只从环境变量注入，不写默认值、不落盘。 */
@ConfigurationProperties("aiditto.recall")
public record RecallProperties(
        Boolean enabled,
        String qdrantUrl,
        String collection,
        Integer dimension,
        String embeddingMode,
        String embeddingVersion,
        String embeddingBaseUrl,
        String embeddingApiKey,
        String embeddingModel,
        Boolean embeddingSendDimensions,
        Integer embeddingBatchSize,
        Integer embeddingMaxBatchItems,
        Long embeddingTimeoutMs,
        Integer tasteTopK,
        Integer personaTopK,
        Integer tagTopK,
        Integer exploreTopK,
        Integer minCandidates,
        Integer coarseTopN,
        Double rrfK,
        Integer hnswEf,
        Integer maxHnswEf,
        Double coarseWeightCondition,
        Double coarseWeightRrf,
        Double coarseWeightFairness,
        Double coarseWeightFeedback,
        Boolean isoEnabled,
        Long timeoutMs) {

    public RecallProperties {
        // 全部用包装类型 + 显式兜底：新增配置项在旧 yml 里缺失时取安全默认值，
        // 绝不会出现 "没配 -> primitive 0 -> 权重全 0 / 等时圈静默关闭" 的隐蔽故障。
        enabled = enabled == null ? Boolean.TRUE : enabled;
        qdrantUrl = blankTo(qdrantUrl, "http://127.0.0.1:6333");
        collection = blankTo(collection, "match_user_v1");
        dimension = dimension == null || dimension <= 0 ? 1024 : dimension;
        embeddingMode = blankTo(embeddingMode, "hash");
        embeddingVersion = blankTo(embeddingVersion, "hash-1024-v1");
        embeddingBaseUrl = nullToEmpty(embeddingBaseUrl);
        embeddingApiKey = nullToEmpty(embeddingApiKey);
        embeddingModel = blankTo(embeddingModel, "text-embedding-v3");
        // 默认发 dimensions；聚合网关若不支持，显式配 EMBEDDING_SEND_DIMENSIONS=false
        embeddingSendDimensions = embeddingSendDimensions == null ? Boolean.TRUE : embeddingSendDimensions;
        // embeddingBatchSize = 每批用户数；实际文本条数 = 用户数 × 2（taste + persona）
        embeddingBatchSize = embeddingBatchSize == null || embeddingBatchSize <= 0
                ? 5 : Math.min(embeddingBatchSize, 32);
        // 网关侧单请求文本条数上限。text-embedding-v3 实测为 10（>10 直接 400），
        // 超限会静默回退逐条导致 200 用户同步从 ~1 分钟劣化到 ~8 分钟，必须按文本数切分。
        embeddingMaxBatchItems = embeddingMaxBatchItems == null || embeddingMaxBatchItems <= 0
                ? 10 : Math.min(embeddingMaxBatchItems, 64);
        embeddingTimeoutMs = embeddingTimeoutMs == null || embeddingTimeoutMs <= 0
                ? 20_000L : embeddingTimeoutMs;
        tasteTopK = tasteTopK == null || tasteTopK <= 0 ? 200 : tasteTopK;
        personaTopK = personaTopK == null || personaTopK <= 0 ? 200 : personaTopK;
        tagTopK = tagTopK == null || tagTopK <= 0 ? 200 : tagTopK;
        exploreTopK = exploreTopK == null || exploreTopK <= 0 ? 50 : exploreTopK;
        minCandidates = minCandidates == null || minCandidates <= 0 ? 50 : minCandidates;
        coarseTopN = coarseTopN == null || coarseTopN <= 0 ? 15 : coarseTopN;
        rrfK = rrfK == null || rrfK <= 0 ? 60.0 : rrfK;
        hnswEf = hnswEf == null || hnswEf <= 0 ? 256 : hnswEf;
        maxHnswEf = maxHnswEf == null || maxHnswEf <= 0 ? 512 : maxHnswEf;
        // 粗排四权重：0.40 条件 + 0.30 RRF + 0.10 曝光公平 + 0.20 反馈历史（v3 规格）
        coarseWeightCondition = coarseWeightCondition == null || coarseWeightCondition < 0
                ? 0.40 : coarseWeightCondition;
        coarseWeightRrf = coarseWeightRrf == null || coarseWeightRrf < 0 ? 0.30 : coarseWeightRrf;
        coarseWeightFairness = coarseWeightFairness == null || coarseWeightFairness < 0
                ? 0.10 : coarseWeightFairness;
        coarseWeightFeedback = coarseWeightFeedback == null || coarseWeightFeedback < 0
                ? 0.20 : coarseWeightFeedback;
        isoEnabled = isoEnabled == null ? Boolean.TRUE : isoEnabled;
        timeoutMs = timeoutMs == null || timeoutMs <= 0 ? 10_000L : timeoutMs;
    }

    public boolean liveEmbedding() {
        return "openai".equalsIgnoreCase(embeddingMode)
                && !embeddingBaseUrl.isBlank() && !embeddingApiKey.isBlank();
    }

    /**
     * 每批用户数：每个用户产生 taste + persona 两条文本，必须保证
     * usersPerGroup * 2 &lt;= embeddingMaxBatchItems，否则网关 400 并静默回退逐条。
     */
    public int usersPerEmbeddingBatch() {
        return usersPerBatch(embeddingBatchSize, embeddingMaxBatchItems);
    }

    /** 纯函数，便于单测：每用户 2 条文本，保证 users * 2 &lt;= maxItems。 */
    public static int usersPerBatch(int configuredUsers, int maxItems) {
        int cap = Math.max(2, maxItems);
        return Math.max(1, Math.min(Math.max(1, configuredUsers), cap / 2));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.strip();
    }
}
