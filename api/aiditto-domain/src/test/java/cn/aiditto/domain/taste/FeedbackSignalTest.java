package cn.aiditto.domain.taste;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A01 / A02 / A03：单次反馈只调 1 维、幅度受控、矛盾与恶意信号不洗画像。 */
class FeedbackSignalTest {

    @Test
    void goodTagBoostsDeclaredDimension() {
        FeedbackSignal s = FeedbackSignal.derive("good:humor", 5, "yes", "笑点很合");
        assertThat(s.applicable()).isTrue();
        assertThat(s.dim()).isEqualTo("humor");
        assertThat(s.delta()).isPositive().isLessThanOrEqualTo(TasteWeights.MAX_DELTA);
        TasteWeights after = TasteWeights.neutral().apply(s.dim(), s.delta());
        assertThat(after.maxDeltaFrom(TasteWeights.neutral())).isLessThanOrEqualTo(TasteWeights.MAX_DELTA + 1e-9);
    }

    @Test
    void badTagLowersSurfaceDimensionForTwoFacedUsers() {
        FeedbackSignal s = FeedbackSignal.derive("bad:height_sensitivity", 2, "no", "条件都对但没感觉");
        assertThat(s.dim()).isEqualTo("height_sensitivity");
        assertThat(s.delta()).isNegative();
        TasteWeights after = TasteWeights.neutral().apply(s.dim(), s.delta());
        assertThat(after.get("height_sensitivity")).isLessThan(0.5);
    }

    @Test
    void textDerivesDimensionWhenNoTag() {
        assertThat(FeedbackSignal.derive(null, 5, "yes", "聊起来特别轻松，笑点很合").dim()).isEqualTo("humor");
        assertThat(FeedbackSignal.derive(null, 2, "no", "完全没话题，有点冷场").dim()).isEqualTo("expressiveness");
        assertThat(FeedbackSignal.derive(null, 4, "yes", "都爱尝试没做过的事").dim()).isEqualTo("adventure");
    }

    @Test
    void ambivalentFeedbackKeepsBothSignalsAndHalvesImpact() {
        FeedbackSignal calm = FeedbackSignal.derive("good:humor", 5, "yes", "聊得挺好");
        FeedbackSignal mixed = FeedbackSignal.derive("good:humor", 5, "yes", "人挺好的，但就是没啥感觉，说不上来哪不对");
        assertThat(mixed.ambivalent()).isTrue();
        assertThat(mixed.note()).contains(FeedbackSignal.OBSERVE);
        assertThat(Math.abs(mixed.delta())).isLessThan(Math.abs(calm.delta()));
    }

    @Test
    void maliciousOneStarRatingBarelyMovesProfile() {
        FeedbackSignal normal = FeedbackSignal.derive("bad:humor", 1, "no", "完全没话题");
        FeedbackSignal malicious = FeedbackSignal.derive("bad:humor", 1, "yes", "还行吧");
        assertThat(malicious.malicious()).isTrue();
        assertThat(Math.abs(malicious.delta())).isLessThan(Math.abs(normal.delta()));
        TasteWeights after = TasteWeights.neutral().apply(malicious.dim(), malicious.delta());
        assertThat(after.maxDeltaFrom(TasteWeights.neutral())).isLessThanOrEqualTo(TasteWeights.MAX_DELTA);
    }

    @Test
    void noUsableSignalLeavesProfileUntouched() {
        FeedbackSignal s = FeedbackSignal.derive(null, 3, "unsure", "还行吧");
        assertThat(s.applicable()).isFalse();
        assertThat(s.delta()).isZero();
    }

    @Test
    void unknownTagOrDimensionIsIgnored() {
        assertThat(FeedbackSignal.derive("good:not_a_dim", 5, "yes", "").applicable()).isFalse();
        assertThat(FeedbackSignal.derive("随便一句自然语言不是标签", 5, "yes", "").applicable()).isFalse();
    }
}
