package cn.aiditto.domain.taste;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 8 防过拟合与口味还原的可计算闸门。 */
class TasteWeightsTest {

    @Test
    void neutralIsHalfAndJsonRoundTrips() {
        TasteWeights n = TasteWeights.neutral();
        assertThat(n.asMap()).hasSize(10);
        for (String d : TasteWeights.DIMS) assertThat(n.get(d)).isEqualTo(0.5);
        TasteWeights back = TasteWeights.of(Jsonish.parse(n.toJson()));
        for (String d : TasteWeights.DIMS) assertThat(back.get(d)).isEqualTo(n.get(d));
        // 缺省维度补中性，非法值被夹到 0..1
        TasteWeights partial = TasteWeights.of(Map.of("humor", 9, "calm", -3));
        assertThat(partial.get("humor")).isEqualTo(1.0);
        assertThat(partial.get("calm")).isEqualTo(0.0);
        assertThat(partial.get("adventure")).isEqualTo(0.5);
    }

    @Test
    void applyChangesExactlyOneDimensionAndClampsDelta() {
        TasteWeights a = TasteWeights.neutral();
        TasteWeights b = a.apply("humor", 0.9);
        assertThat(b.get("humor")).isEqualTo(0.5 + TasteWeights.MAX_DELTA);
        for (String d : TasteWeights.DIMS) {
            if (!d.equals("humor")) assertThat(b.get(d)).isEqualTo(a.get(d));
        }
        assertThat(a.maxDeltaFrom(b)).isLessThanOrEqualTo(TasteWeights.MAX_DELTA + 1e-9);
        // 下界不越界
        TasteWeights low = TasteWeights.of(Map.of("humor", 0.02));
        assertThat(low.apply("humor", -1.0).get("humor")).isEqualTo(0.0);
    }

    @Test
    void applyIgnoresUnknownDimensionAndNaN() {
        TasteWeights a = TasteWeights.neutral();
        assertThat(a.apply("not_a_dim", 1.0).asMap()).isEqualTo(a.asMap());
        assertThat(a.apply("humor", Double.NaN).asMap()).isEqualTo(a.asMap());
    }

    @Test
    void spearmanPerfectInverseAndConstant() {
        TasteWeights a = TasteWeights.of(Map.of("humor", 0.9, "adventure", 0.8, "expressiveness", 0.7,
                "calm", 0.6, "schedule", 0.5, "ambition", 0.4, "spending", 0.3,
                "warmth", 0.2, "height_sensitivity", 0.1, "look_weight", 0.0));
        assertThat(TasteWeights.spearman(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        TasteWeights b = TasteWeights.of(Map.of("humor", 0.0, "adventure", 0.1, "expressiveness", 0.2,
                "calm", 0.3, "schedule", 0.4, "ambition", 0.5, "spending", 0.6,
                "warmth", 0.7, "height_sensitivity", 0.8, "look_weight", 0.9));
        assertThat(TasteWeights.spearman(a, b)).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
        // 全常量不产生 NaN，也不能虚高
        assertThat(TasteWeights.spearman(TasteWeights.neutral(), a)).isEqualTo(0.0);
    }

    @Test
    void twoFacedConvergenceRequiresLatentAboveSurface() {
        TasteWeights n = TasteWeights.neutral();
        assertThat(n.twoFacedConverged()).isFalse();
        TasteWeights learned = n.apply("humor", 0.15).apply("expressiveness", 0.15)
                .apply("height_sensitivity", -0.15);
        assertThat(learned.latentMean()).isGreaterThan(learned.surfaceMean());
        assertThat(learned.twoFacedConverged()).isTrue();
    }

    @Test
    void rankAgainstIsBoundedDirectionalAndNeutralSafe() {
        // 中性画像互相打分落在 (0,1) 内且确定
        double nn = TasteWeights.neutral().rankAgainst(TasteWeights.neutral());
        assertThat(nn).isBetween(0.0, 1.0);
        assertThat(TasteWeights.neutral().rankAgainst(TasteWeights.neutral())).isEqualTo(nn);

        // 偏好×属性是有向的：A 看重幽默、B 擅长表达 → A×B 高；反向低
        TasteWeights a = TasteWeights.of(Map.of("humor", 0.9, "expressiveness", 0.1,
                "adventure", 0.7, "calm", 0.2));
        TasteWeights b = TasteWeights.of(Map.of("humor", 0.1, "expressiveness", 0.9,
                "adventure", 0.2, "calm", 0.8));
        assertThat(a.rankAgainst(b)).isGreaterThan(b.rankAgainst(a));
        assertThat(a.rankAgainst(b)).isBetween(0.0, 1.0);

        // 越贴合偏好分越高：把对方幽默拉满应抬高 A 对它的分
        TasteWeights bMoreHumor = TasteWeights.of(Map.of("humor", 1.0, "expressiveness", 0.9,
                "adventure", 1.0, "calm", 0.0));
        assertThat(a.rankAgainst(bMoreHumor)).isGreaterThan(a.rankAgainst(b));
    }

    /** 极简 JSON 解析（domain 层不依赖 Jackson），只用于断言 toJson 可回读。 */
    static final class Jsonish {
        static Map<String, Object> parse(String s) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            String body = s.replace("{", "").replace("}", "");
            for (String part : body.split(",")) {
                String[] kv = part.split(":");
                m.put(kv[0].replace("\"", "").strip(), Double.parseDouble(kv[1].trim()));
            }
            return m;
        }
    }
}
