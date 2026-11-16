package cn.aiditto.domain.sim;

import cn.aiditto.domain.taste.TasteWeights;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Oracle / 指标纯函数闸门：确定性、硬否决、跨表面共鸣、学习曲线可计算。 */
class SimDomainTest {

    private static HiddenTruth truth(long id, double humor, double adventure, Set<String> db, Set<String> attrs) {
        return new HiddenTruth(id, TasteWeights.of(Map.of("humor", humor, "adventure", adventure)),
                db == null ? Set.of() : db, attrs == null ? Set.of() : attrs, false, null);
    }

    @Test
    void dealbreakerGateIsHardZero() {
        HiddenTruth a = truth(1, 0.9, 0.9, Set.of("smoking"), Set.of());
        HiddenTruth b = truth(2, 0.9, 0.9, Set.of(), Set.of("smoking"));
        assertThat(TrueScore.hitsDealbreaker(a, b)).isTrue();
        assertThat(TrueScore.of(a, b, 7L)).isZero();
    }

    @Test
    void trueScoreIsDeterministicForSameSeed() {
        HiddenTruth a = truth(1, 0.8, 0.7, null, null);
        HiddenTruth b = truth(2, 0.6, 0.9, null, null);
        double x = TrueScore.of(a, b, 42L), y = TrueScore.of(a, b, 42L);
        assertThat(x).isEqualTo(y).isBetween(0.0, 100.0);
        assertThat(TrueScore.interaction(a.weights(), b.weights())).isGreaterThan(0.0);
    }

    @Test
    void oracleIsDeterministicAndTagsNeverLeakNumbers() {
        HiddenTruth viewer = truth(1, 0.95, 0.1, null, null);
        HiddenTruth partner = truth(2, 0.5, 0.5, null, null);
        Feedback f1 = FeedbackOracle.of(78.0, viewer, partner, 11L);
        Feedback f2 = FeedbackOracle.of(78.0, viewer, partner, 11L);
        assertThat(f1).isEqualTo(f2);
        assertThat(f1.reasonTag()).matches("(good|bad):[a-z_]+");
        assertThat(f1.freeText()).doesNotContain("0.95", "hidden", "true_score");
    }

    @Test
    void oracleInjectsDirtyDataFlags() {
        HiddenTruth a = truth(1, 0.7, 0.6, null, null), b = truth(2, 0.6, 0.7, null, null);
        int terse = 0, ambivalent = 0;
        for (int i = 0; i < 200; i++) {
            Feedback f = FeedbackOracle.of(70.0, a, b, i);
            if (f.flags().contains("terse")) terse++;
            if (f.flags().contains("ambivalent")) ambivalent++;
        }
        assertThat(terse).isGreaterThan(0);
        assertThat(ambivalent).isGreaterThan(0);
    }

    @Test
    void metricsComputeRegretHitSlopeAndRecovery() {
        assertThat(SimMetrics.regret(80, 100)).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
        List<Double> feasible = new ArrayList<>();
        for (int i = 100; i > 0; i--) feasible.add((double) i);
        assertThat(SimMetrics.hitTopDecile(95.0, feasible)).isTrue();
        assertThat(SimMetrics.hitTopDecile(20.0, feasible)).isFalse();
        List<Double> improving = List.of(0.50, 0.45, 0.41, 0.36, 0.30, 0.25, 0.22, 0.18, 0.16, 0.14, 0.12, 0.10);
        assertThat(SimMetrics.slope(improving)).isNegative();
        assertThat(SimMetrics.monotoneDecreaseCount(improving)).isEqualTo(11);

        TasteWeights t = TasteWeights.of(Map.of("humor", 0.9, "adventure", 0.8, "expressiveness", 0.7,
                "calm", 0.6, "schedule", 0.5, "ambition", 0.4, "spending", 0.3,
                "warmth", 0.2, "height_sensitivity", 0.1, "look_weight", 0.0));
        assertThat(SimMetrics.tasteRecovery(List.of(t, t), List.of(t, t))).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void rankCorrelationDetectsOrderingQuality() {
        List<Double> x = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(SimMetrics.rankCorrelation(x, List.of(10.0, 20.0, 30.0, 40.0, 50.0)))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SimMetrics.rankCorrelation(x, List.of(50.0, 40.0, 30.0, 20.0, 10.0)))
                .isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
        // 并列秩、常量序列、长度不符都不能产生 NaN 或虚高
        assertThat(SimMetrics.rankCorrelation(x, List.of(1.0, 1.0, 1.0, 1.0, 1.0))).isEqualTo(0.0);
        assertThat(SimMetrics.rankCorrelation(x, List.of(1.0, 2.0))).isEqualTo(0.0);
        assertThat(SimMetrics.rankCorrelation(List.of(1.0, 1.0, 2.0, 2.0),
                List.of(3.0, 3.0, 4.0, 4.0)))
                .isGreaterThan(0.9);
    }
}
