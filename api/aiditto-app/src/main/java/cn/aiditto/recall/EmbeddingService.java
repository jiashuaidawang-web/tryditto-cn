package cn.aiditto.recall;

/**
 * 画像文本 → 稠密向量。可插拔：生产接 OpenAI 兼容 embedding 端点，
 * 离线/仿真/无密钥时用确定性 HashingEmbeddingService。
 *
 * version() 必须写进 Qdrant payload 并作为硬过滤项，禁止跨版本混算相似度。
 */
public interface EmbeddingService {

    String version();

    int dimension();

    /** 返回已 L2 归一化的向量；长度恒等于 dimension()。 */
    float[] embed(String text);

    /**
     * 批量取向量。默认逐条串行（HashingEmbeddingService 等纯本地实现无需覆盖）；
     * 真实远端实现应覆盖为单次 HTTP 批量请求——200 用户 × 2 向量若串行要 400 次
     * 网络往返（约 10 分钟），生产不可接受。
     */
    default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        java.util.List<float[]> out = new java.util.ArrayList<>(texts.size());
        for (String t : texts) out.add(embed(t));
        return out;
    }
}
