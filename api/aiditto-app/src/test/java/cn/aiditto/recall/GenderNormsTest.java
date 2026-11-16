package cn.aiditto.recall;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenderNormsTest {

    @Test
    void normalizesPluralAndEverybodyValues() {
        assertThat(GenderNorms.norm("men")).isEqualTo("man");
        assertThat(GenderNorms.norm("women")).isEqualTo("woman");
        assertThat(GenderNorms.norm("WOMEN")).isEqualTo("woman");
        assertThat(GenderNorms.norm("nonbinary")).isEqualTo("nonbinary");
        assertThat(GenderNorms.norm(" ")).isEmpty();
    }

    @Test
    void emptyMeansNoPreferenceAndEveryoneExpandsToAll() {
        assertThat(GenderNorms.normList(null)).isEqualTo(GenderNorms.ALL);
        assertThat(GenderNorms.normList(List.of())).isEqualTo(GenderNorms.ALL);
        assertThat(GenderNorms.normList(List.of("everyone"))).isEqualTo(GenderNorms.ALL);
        assertThat(GenderNorms.normList(List.of("men", "women")))
                .containsExactly("man", "woman");
        assertThat(GenderNorms.normList(List.of("women", "nonbinary")))
                .containsExactly("woman", "nonbinary");
    }

    @Test
    void bidirectionalPreferenceNowMatchesForSyntheticFixture() {
        // 合成用户 A(man, 期望 women) 与 B(woman, 期望 men) 必须双向可配
        List<String> aWants = GenderNorms.normList(List.of("women"));
        List<String> bWants = GenderNorms.normList(List.of("men"));
        assertThat(aWants).contains(GenderNorms.norm("woman"));
        assertThat(bWants).contains(GenderNorms.norm("man"));
    }
}
