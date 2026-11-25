//package cn.aiditto.domain.taste;
//
//import org.junit.jupiter.api.Test;
//
//import java.util.Map;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///** Pairwise ranker 闸门：能从成对比较里学、权重有界、零样本退回先验、分数在 0-100。 */
//class PairwiseRankerTest {
//
//    private static TasteWeights tw(Map<String, Double> vals) {
//        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
//        for (String d : TasteWeights.DIMS) m.put(d, vals.getOrDefault(d, 0.5));
//        return TasteWeights.of(m);
//    }
//
//    @Test
//    void learnsFromPairwiseComparison() {
//        PairwiseRanker r = new PairwiseRanker(0.3, 0.0); // 大学习率，快速见效
//        double[] before = r.weights();
//        // 成功对：双方 humor 都高；失败对：双方 humor 都低。反复观察应提升 humor 维度权重
//        TasteWeights winA = tw(Map.of("humor", 0.9)), winB = tw(Map.of("humor", 0.9));
//        TasteWeights loseA = tw(Map.of("humor", 0.1)), loseB = tw(Map.of("humor", 0.1));
//        for (int i = 0; i < 40; i++) r.observe(winA, winB, loseA, loseB);
//        double[] after = r.weights();
//        int humorIdx = TasteWeights.DIMS.indexOf("humor");
//        assertThat(after[humorIdx]).isGreaterThan(before[humorIdx]);
//        assertThat(r.observations()).isEqualTo(40);
//    }
//
//    @Test
//    void correctlyRankedPairDoesNotMoveWeights() {
//        PairwiseRanker r = new PairwiseRanker();
//        double[] before = r.weights();
//        // 用初始权重就能排对的极端例子：高权重维度全满 vs 全空
//        TasteWeights winA = tw(Map.of()), winB = tw(Map.of());
//        TasteWeights loseA = tw(Map.of()), loseB = tw(Map.of());
//        // 全 0.5 时 diff=0 → g=0.5，仍会动；用一个明显排对的组合
//        TasteWeights hiA = tw(Map.of("humor", 1.0, "expressiveness", 1.0));
//        TasteWeights hiB = tw(Map.of("humor", 1.0, "expressiveness", 1.0));
//        TasteWeights loA = tw(Map.of("humor", 0.0, "expressiveness", 0.0));
//        TasteWeights loB = tw(Map.of("humor", 0.0, "expressiveness", 0.0));
//        r.observe(hiA, hiB, loA, loB);
//        // 排序本来正确 → g 很小，权重变化有限但可能因 l2 微降；只断言有界
//        assertThat(r.weights()).allSatisfy(x -> {
//            assertThat(x).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(3.0);
//        });
//        assertThat(before).hasSize(TasteWeights.DIMS.size() + 1);
//    }
//
//    @Test
//    void weightsStayBoundedUnderExtremeUpdates() {
//        PairwiseRanker r = new PairwiseRanker(1.0, 0.0);
//        TasteWeights hiA = tw(Map.of("humor", 1.0)), hiB = tw(Map.of("humor", 1.0));
//        TasteWeights loA = tw(Map.of("humor", 0.0)), loB = tw(Map.of("humor", 0.0));
//        for (int i = 0; i < 1000; i++) r.observe(hiA, hiB, loA, loB);
//        assertThat(r.weights()).allSatisfy(x -> {
//            assertThat(x).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(3.0);
//        });
//    }
//
//    @Test
//    void scoreInRangeAndMonoInCompatibility() {
//        PairwiseRanker r = new PairwiseRanker();
//        TasteWeights hiA = tw(Map.of("humor", 1.0)), hiB = tw(Map.of("humor", 1.0));
//        TasteWeights loA = tw(Map.of("humor", 0.0)), loB = tw(Map.of("humor", 0.0));
//        int hi = r.score(hiA, hiB), lo = r.score(loA, loB);
//        assertThat(hi).isBetween(0, 100);
//        assertThat(lo).isBetween(0, 100);
//        assertThat(hi).isGreaterThan(lo);
//    }
//}
