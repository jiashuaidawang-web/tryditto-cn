package cn.aiditto.domain.recall;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    private static List<RrfFusion.Ranked> route(String source, long... ids) {
        var out = new java.util.ArrayList<RrfFusion.Ranked>();
        for (int i = 0; i < ids.length; i++) out.add(new RrfFusion.Ranked(ids[i], i + 1, source));
        return out;
    }

    @Test
    void mergesRoutesWithReciprocalRankAndStableTiebreak() {
        var fused = RrfFusion.merge(
                List.of(route("R1", 10L, 20L), route("R2", 20L, 30L)),
                Map.of("R1", 1.0, "R2", 1.0), 60);
        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).userId()).isEqualTo(20L); // 两路都命中
        assertThat(fused.get(0).sources()).containsExactlyInAnyOrder("R1", "R2");
        assertThat(fused.get(0).score()).isGreaterThan(fused.get(1).score());
        assertThat(fused.get(1).userId()).isEqualTo(10L);
    }

    @Test
    void weightsAndKChangeOrderingDeterministically() {
        var routes = List.of(route("R1", 1L, 2L), route("R2", 2L, 1L));
        var equal = RrfFusion.merge(routes, Map.of("R1", 1.0, "R2", 1.0), 60);
        var heavyR1 = RrfFusion.merge(routes, Map.of("R1", 3.0, "R2", 1.0), 60);
        assertThat(heavyR1.get(0).userId()).isEqualTo(1L);
        assertThat(RrfFusion.merge(routes, Map.of("R1", 1.0, "R2", 1.0), 60))
                .isEqualTo(equal); // 确定性
        assertThat(RrfFusion.merge(routes, Map.of("R1", 1.0, "R2", 1.0), 20).get(0).score())
                .isGreaterThan(equal.get(0).score());
    }

    @Test
    void shareDetectsSingleRouteDominance() {
        var only = RrfFusion.merge(List.of(route("R1", 1L, 2L, 3L)), Map.of(), 60);
        assertThat(RrfFusion.share(only, "R1")).isEqualTo(1.0);
        assertThat(RrfFusion.share(only, "R2")).isZero();
        var mixed = RrfFusion.merge(
                List.of(route("R1", 1L), route("R2", 2L)), Map.of(), 60);
        assertThat(RrfFusion.share(mixed, "R1")).isEqualTo(0.5);
    }

    @Test
    void emptyAndInvalidRanksAreIgnored() {
        assertThat(RrfFusion.merge(List.of(), Map.of(), 60)).isEmpty();
        var bad = List.of(new RrfFusion.Ranked(7L, 0, "R1"));
        assertThat(RrfFusion.merge(List.of(bad), Map.of(), 60)).isEmpty();
    }
}
