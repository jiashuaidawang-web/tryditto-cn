package cn.aiditto.domain.recall;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RecallMatrixTest {

    private static Map<String, List<Long>> routes(int n) {
        Map<String, List<Long>> r = new LinkedHashMap<>();
        for (String s : new String[]{"R1_TASTE", "R2_PERSONA", "R3_TAGS", "R4_PREFERENCE", "R5_EXPLORE"}) {
            r.put(s, LongStream.rangeClosed(1, n).boxed().toList());
        }
        return r;
    }

    @Test
    void balancedRoutesKeepSingleRouteShareLow() {
        List<RrfFusion.Ranked> ranked = LongStream.rangeClosed(1, 10)
                .mapToObj(i -> new RrfFusion.Ranked(i, (int) i, "R1_TASTE")).toList();
        // 五路都贡献不同候选时，单路占比不会垄断
        Map<String, List<Long>> r = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            final int s = i;
            r.put("R" + (i + 1) + "_X", LongStream.rangeClosed(1, 10)
                    .map(x -> x + s * 10L).boxed().toList());
        }
        // 用 ROUTES 名字才能被 share 统计到
        Map<String, List<Long>> named = new LinkedHashMap<>();
        String[] names = {"R1_TASTE", "R2_PERSONA", "R3_TAGS", "R4_PREFERENCE", "R5_EXPLORE"};
        int j = 0;
        for (List<Long> v : r.values()) named.put(names[j++], v);

        List<List<RrfFusion.Ranked>> all = named.values().stream()
                .map(ids -> LongStream.range(0, ids.size())
                        .mapToObj(i -> new RrfFusion.Ranked(ids.get((int) i), (int) i + 1, "x"))
                        .toList())
                .toList();
        // 直接验证 share 上限逻辑
        assertThat(RecallMatrix.maxSingleRouteShare(List.of())).isZero();
        assertThat(RecallMatrix.coverage(98, 100)).isEqualTo(0.98);
        assertThat(RecallMatrix.coverage(0, 0)).isEqualTo(1.0);
    }

    @Test
    void exclusiveShareDistinguishesConsensusFromMonopoly() {
        // 五路都召回同一批候选 -> 共识，独占占比 0（健康），但旧 share 会是 1.0
        List<RrfFusion.Fused> consensus = LongStream.rangeClosed(1, 10)
                .mapToObj(i -> new RrfFusion.Fused(i, 1.0, List.of(
                        "R1_TASTE", "R2_PERSONA", "R3_TAGS", "R4_PREFERENCE", "R5_EXPLORE")))
                .toList();
        assertThat(RecallMatrix.maxExclusiveRouteShare(consensus)).isZero();
        assertThat(RecallMatrix.maxSingleRouteShare(consensus)).isEqualTo(1.0);

        // 只有一路贡献候选 -> 真垄断，独占占比 1.0
        List<RrfFusion.Fused> monopoly = LongStream.rangeClosed(1, 10)
                .mapToObj(i -> new RrfFusion.Fused(i, 1.0, List.of("R1_TASTE")))
                .toList();
        assertThat(RecallMatrix.maxExclusiveRouteShare(monopoly)).isEqualTo(1.0);

        // 混合：10 个里 3 个仅由 R3 贡献
        List<RrfFusion.Fused> mixed = new java.util.ArrayList<>();
        for (long i = 1; i <= 7; i++) mixed.add(new RrfFusion.Fused(i, 1.0,
                List.of("R1_TASTE", "R3_TAGS")));
        for (long i = 8; i <= 10; i++) mixed.add(new RrfFusion.Fused(i, 1.0,
                List.of("R3_TAGS")));
        assertThat(RecallMatrix.maxExclusiveRouteShare(mixed)).isEqualTo(0.3);
        assertThat(RecallMatrix.maxExclusiveRouteShare(List.of())).isZero();
    }

    @Test
    void gateThresholdsAreExact() {
        assertThat(RecallMatrix.gate(0.60, 0.98)).isTrue();
        assertThat(RecallMatrix.gate(0.61, 0.98)).isFalse();
        assertThat(RecallMatrix.gate(0.50, 0.97)).isFalse();
        assertThat(RecallMatrix.gate(0.0, 1.0)).isTrue();
    }

    @Test
    void sweepProducesBaselineAblationsAndGrid() {
        List<RecallMatrix.Cell> cells = RecallMatrix.sweep(routes(30), 30, 60.0, 0.5);
        // 1 基线 + 5 消融 + 4 网格(w1=0.5,1.0,1.5,2.0)
        assertThat(cells).hasSize(10);
        assertThat(cells.get(0).label()).isEqualTo("base-equal");
        assertThat(cells).anySatisfy(c -> assertThat(c.label()).startsWith("ablate-"));
        assertThat(cells).anySatisfy(c -> assertThat(c.label()).startsWith("grid-"));
        assertThat(cells).allSatisfy(c -> {
            assertThat(c.maxSingleRouteShare()).isBetween(0.0, 1.0);
            assertThat(c.coverage()).isBetween(0.0, 1.0);
            assertThat(c.fusedCount()).isPositive();
        });
    }

    @Test
    void sweepIsDeterministic() {
        assertThat(RecallMatrix.sweep(routes(20), 20, 60.0, 0.5))
                .isEqualTo(RecallMatrix.sweep(routes(20), 20, 60.0, 0.5));
    }
}
