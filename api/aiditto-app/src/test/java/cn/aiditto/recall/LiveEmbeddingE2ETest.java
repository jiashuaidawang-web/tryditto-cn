package cn.aiditto.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 embedding 端点的小样本 E2E（4 条中文短文本）。
 * 默认跳过；用 AIDITTO_LLM_E2E=1 + EMBEDDING_* 环境变量开启。
 * 密钥只从环境变量读，绝不写入仓库/日志。
 *
 * 目的：在跑 200 用户全量同步之前，几秒钟内验证 URL 归一化、dimensions 参数、
 * 批量 index 归位、L2 归一化与中文语义区分度，避免长任务白跑。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_LLM_E2E", matches = "1")
class LiveEmbeddingE2ETest {

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    private OpenAiCompatEmbeddingService service() {
        return new OpenAiCompatEmbeddingService(
                env("EMBEDDING_MODEL", "text-embedding-v3") + "-d" + env("EMBEDDING_DIM", "1024"),
                env("EMBEDDING_BASE_URL", "https://api.vveai.com"),
                env("EMBEDDING_API_KEY", ""),
                env("EMBEDDING_MODEL", "text-embedding-v3"),
                Integer.parseInt(env("EMBEDDING_DIM", "1024")),
                Boolean.parseBoolean(env("EMBEDDING_SEND_DIMENSIONS", "true")),
                30_000L);
    }

    @Test
    void batchReturnsNormalizedVectorsInChineseWithSemanticGap() {
        List<String> texts = List.of(
                "喜欢攀岩、徒步和户外冒险，周末总往山里跑",
                "热爱登山露营，享受在野外挑战自己的感觉",
                "喜欢宅家看书写代码，周末更愿意一个人安静待着");
        List<float[]> vs = service().embedBatch(texts);
        assertThat(vs).hasSize(3);
        for (float[] v : vs) {
            assertThat(v).hasSize(Integer.parseInt(env("EMBEDDING_DIM", "1024")));
            double norm2 = 0;
            for (float x : v) norm2 += x * x;
            assertThat(norm2).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
        }
        double related = cos(vs.get(0), vs.get(1));
        double unrelated = cos(vs.get(0), vs.get(2));
        System.out.printf("[LIVE-EMB] dim=%d related=%.3f unrelated=%.3f gap=%+.3f%n",
                vs.get(0).length, related, unrelated, related - unrelated);
        // 真实语义模型必须能把「同为冒险型」排在「宅家型」之前；
        // 确定性 hash 向量在这个基准上做不到，正是引入真实 embedding 的意义。
        assertThat(related - unrelated).as("中文语义区分度").isGreaterThan(0.08);
    }

    @Test
    void singleAndBatchAreConsistent() {
        OpenAiCompatEmbeddingService svc = service();
        float[] single = svc.embed("喜欢攀岩和户外冒险");
        float[] fromBatch = svc.embedBatch(List.of("喜欢攀岩和户外冒险")).get(0);
        assertThat(cos(single, fromBatch)).isGreaterThan(0.999);
    }

    private static double cos(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            s += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return s / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
