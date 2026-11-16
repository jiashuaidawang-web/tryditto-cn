package cn.aiditto.recall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 确定性特征哈希 embedding（默认实现）。
 *
 * 用途不是替代语义模型，而是让**召回工程链路**（filterable HNSW、五路融合、RRF、
 * 分桶 Recall@K、注销删除、版本切流）在没有真实 embedding key 时也能完整跑通并量化验收。
 * 同一文本永远同一向量，仿真可复现；上线前换成 OpenAiCompatEmbeddingService 只改配置，
 * 并用同一套 E1–E14 回放重跑一遍（列入 Go/No-Go 闸门）。
 */
public final class HashingEmbeddingService implements EmbeddingService {

    public static final String VERSION = "hash-1024-v1";

    private final int dimension;

    public HashingEmbeddingService(int dimension) {
        this.dimension = dimension > 0 ? dimension : 1024;
    }

    @Override
    public String version() {
        return VERSION + "-d" + dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimension];
        v[0] += 1f; // 空文本也必须是非零向量，Cosine 下零向量不可用
        if (text == null || text.isBlank()) return normalize(v);

        String s = text.strip().toLowerCase(Locale.ROOT);
        List<String> features = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            features.add(String.valueOf(s.charAt(i)));
            if (i + 1 < s.length()) features.add(s.substring(i, i + 2));
        }
        for (String word : s.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) features.add("w:" + word);
        }
        for (String f : features) {
            long h = hash(f);
            int idx = (int) Math.floorMod(h, dimension);
            v[idx] += ((h >>> 32) & 1L) == 0L ? 1f : -1f;
        }
        return normalize(v);
    }

    private static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm <= 0) return v;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    private static long hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xffL);
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
