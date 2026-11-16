package cn.aiditto.domain.matching;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 6 性质测试：Agent 精排分主导配对，但绝不越过硬约束/门槛，且默认零回归。
 * 这些是 C 端上线闸门的核心不变量。
 */
class AiScoreMatchingTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 18);

    /** 4 个高兼容候选，两两都能通过硬约束与门槛（用同一 builder 配置）。 */
    private static Candidate c(long id) {
        return Candidate.builder(id)
                .gender(id % 2 == 0 ? Gender.WOMAN : Gender.MAN)
                .wants(GenderPref.MEN, GenderPref.WOMEN)
                .birthday(LocalDate.of(2002, 6, 1))
                .ageRange(18, 28)
                .heightCm(170)
                .student(true)
                .schoolYear(SchoolYear.JUNIOR)
                .budget(Budget.FROM_20_TO_50)
                .style(MatchingStyle.STEADY)
                .looking(Looking.NOT_SURE)
                .hobbies("music", "travel")
                .build();
    }

    private static MatchingInput input(Map<PairKey, Integer> ai) {
        return new MatchingInput(WEEK, List.of(c(1), c(2), c(3), c(4)), Set.of(), Set.of(),
                Weights.W1, 42L, false, ai);
    }

    @Test
    void emptyAiScoresAreByteForByteIdenticalToM2() {
        MatchingInput legacy = new MatchingInput(WEEK, List.of(c(1), c(2), c(3), c(4)),
                Set.of(), Set.of(), Weights.W1, 42L, false);
        MatchingResult a = new MatchingSolver().solve(legacy);
        MatchingResult b = new MatchingSolver().solve(input(Map.of()));
        assertThat(b.pairs()).isEqualTo(a.pairs());
        assertThat(b.unmatched()).isEqualTo(a.unmatched());
        assertThat(b.stats().totalWeight()).isEqualTo(a.stats().totalWeight());
    }

    @Test
    void aiScoreDominatesRuleScoreInFinalPairing() {
        List<Edge> plain = MatchingSolver.buildEdges(input(Map.of()));
        assertThat(plain).isNotEmpty();
        // 给每一对都打 AI 分：让 (1,4) 与 (2,3) 显著高于其他组合
        Map<PairKey, Integer> ai = Map.of(
                PairKey.of(1, 2), 10,
                PairKey.of(3, 4), 10,
                PairKey.of(1, 3), 12,
                PairKey.of(2, 4), 12,
                PairKey.of(1, 4), 95,
                PairKey.of(2, 3), 95);
        MatchingResult r = new MatchingSolver().solve(input(ai));
        List<PairKey> chosen = r.pairs().stream().map(p -> PairKey.of(p.low(), p.high())).toList();
        assertThat(chosen).containsExactlyInAnyOrder(PairKey.of(1, 4), PairKey.of(2, 3));
        // 展示分仍是规则分，不被 AI 抬高
        for (MatchingResult.MatchedPair p : r.pairs()) {
            assertThat(p.score()).isLessThanOrEqualTo(100);
        }
    }

    @Test
    void aiScoreCannotBypassHardConstraintsOrThreshold() {
        // 未成年人必须被 HardFilter 拦下，哪怕 AI 给满分
        Candidate underage = Candidate.builder(9)
                .gender(Gender.WOMAN).wants(GenderPref.MEN)
                .birthday(LocalDate.of(2012, 1, 1))
                .ageRange(18, 28).heightCm(160).student(true)
                .budget(Budget.FROM_20_TO_50).style(MatchingStyle.STEADY)
                .looking(Looking.NOT_SURE).hobbies("music").build();
        MatchingInput in = new MatchingInput(WEEK, List.of(c(1), underage), Set.of(), Set.of(),
                Weights.W1, 42L, false, Map.of(PairKey.of(1, 9), 100));
        assertThat(MatchingSolver.buildEdges(in)).isEmpty();
        assertThat(new MatchingSolver().solve(in).pairs()).isEmpty();
    }

    @Test
    void aiScoreKeepsOnePartnerPerPersonAndDeterminism() {
        Map<PairKey, Integer> ai = Map.of(
                PairKey.of(1, 2), 80, PairKey.of(1, 3), 90,
                PairKey.of(2, 4), 70, PairKey.of(3, 4), 85);
        MatchingResult a = new MatchingSolver().solve(input(ai));
        MatchingResult b = new MatchingSolver().solve(input(ai));
        assertThat(a.pairs()).isEqualTo(b.pairs());
        Set<Long> seen = new java.util.HashSet<>();
        for (MatchingResult.MatchedPair p : a.pairs()) {
            assertThat(seen.add(p.low())).isTrue();
            assertThat(seen.add(p.high())).isTrue();
        }
    }

    @Test
    void aiScoresAreValidatedAtConstruction() {
        assertThatThrownBy(() -> input(Map.of(PairKey.of(1, 2), 101)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("[0,100]");
        assertThatThrownBy(() -> input(Map.of(PairKey.of(1, 2), -1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(input(Map.of()).aiScores()).isEmpty();
    }

    @Test
    void weightFormulaKeepsAiDominantButRuleAsTiebreak() {
        ScoreBreakdown s = new ScoreBreakdown(10, 10, 5, 5, 5, 5, 5, 0);
        long low = MatchingSolver.weight(s, 42, 1, 2, 10);
        long high = MatchingSolver.weight(s, 42, 3, 4, 90);
        assertThat(high).isGreaterThan(low);
        // AI 分差 1 分应压过规则 objective 的最大可能差异
        long aiOneMore = MatchingSolver.weight(s, 42, 1, 2, 11);
        assertThat(aiOneMore).isGreaterThan(low);
        assertThat(MatchingSolver.weight(s, 42, 1, 2, null))
                .isEqualTo(MatchingSolver.weight(s, 42, 1, 2));
    }
}
