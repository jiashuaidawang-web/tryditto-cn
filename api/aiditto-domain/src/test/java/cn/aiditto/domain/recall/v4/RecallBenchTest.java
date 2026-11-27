package cn.aiditto.domain.recall.v4;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecallBenchTest {
    @Test
    void benchHasOneHundredFixturePeopleAndIndependentLabels() {
        RecallBench bench = RecallBench.load();
        assertThat(bench.subjects()).hasSize(100);
        assertThat(bench.subjects()).allMatch(subject -> subject.fixtureSource().startsWith("synthetic-fixture:"));
        assertThat(bench.split("dev")).hasSize(70);
        assertThat(bench.split("holdout")).hasSize(30);
        long relevant = bench.labels().stream().filter(label -> "RELEVANT".equals(label.kind())).map(label -> label.seeker() + ":" + label.candidate()).distinct().count();
        long veto = bench.labels().stream().filter(label -> "HARD_VETO".equals(label.kind())).count();
        long unknown = bench.labels().stream().filter(label -> "UNKNOWN".equals(label.kind())).count();
        long irrelevant = bench.labels().stream().filter(label -> "IRRELEVANT".equals(label.kind())).count();
        assertThat(relevant).isGreaterThanOrEqualTo(100);
        assertThat(veto).isPositive();
        assertThat(unknown).isPositive();
        assertThat(irrelevant).isPositive();
        Set<String> keys = new HashSet<>();
        for (RecallMetrics.Label label : bench.labels()) {
            if (!"RELEVANT".equals(label.kind())) continue;
            assertThat(keys.add(label.seeker() + ":" + label.candidate() + ":veto")).isTrue();
        }
        for (RecallMetrics.Label label : bench.labels()) {
            if (!"HARD_VETO".equals(label.kind())) continue;
            assertThat(keys).doesNotContain(label.seeker() + ":" + label.candidate() + ":veto");
        }
    }
}
