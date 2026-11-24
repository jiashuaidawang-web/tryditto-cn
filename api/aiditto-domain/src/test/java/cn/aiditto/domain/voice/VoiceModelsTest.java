package cn.aiditto.domain.voice;

import cn.aiditto.domain.voice.VoiceModels.AnswerOutcome;
import cn.aiditto.domain.voice.VoiceModels.Option;
import cn.aiditto.domain.voice.VoiceModels.Scenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 自适应情景引擎的纯逻辑闸门：预测-校准信号、命中率、收敛判断。 */
class VoiceModelsTest {

    private static final Scenario Q = new Scenario("咖啡馆对方迟到，你会？", List.of(
            new Option("A", "发消息开个玩笑", "humor", 1),
            new Option("B", "先等着看情况", "cautious", 2),
            new Option("C", "直接打电话", "direct", 3)));

    @Test
    void choosingPredictedFirstIsAHit() {
        AnswerOutcome hit = VoiceModels.evaluate(Q, "a");
        assertThat(hit.isCustom()).isFalse();
        assertThat(hit.predictHit()).isTrue();
        assertThat(hit.traitSignal()).isEqualTo("humor");
        AnswerOutcome miss = VoiceModels.evaluate(Q, "C");
        assertThat(miss.predictHit()).isFalse();
        assertThat(miss.traitSignal()).isEqualTo("direct");
    }

    @Test
    void customAnswerIsNotACalibrationSignal() {
        AnswerOutcome c = VoiceModels.evaluate(Q, null);
        assertThat(c.isCustom()).isTrue();
        assertThat(c.predictHit()).isFalse();
        assertThat(VoiceModels.calibrationRate(List.of(c))).isEqualTo(0.0);
    }

    @Test
    void calibrationRateCountsOnlyDecidedChoices() {
        List<AnswerOutcome> a = List.of(
                VoiceModels.evaluate(Q, "A"),   // hit
                VoiceModels.evaluate(Q, "B"),   // miss
                VoiceModels.evaluate(Q, "A"));  // hit
        assertThat(VoiceModels.calibrationRate(a)).isEqualTo(2.0 / 3);
    }

    @Test
    void convergesOnlyAfterMinStepsWithStreakOfHits() {
        List<AnswerOutcome> a = new ArrayList<>();
        // 2 题全中但不够 minSteps
        a.add(VoiceModels.evaluate(Q, "A"));
        a.add(VoiceModels.evaluate(Q, "A"));
        assertThat(VoiceModels.converged(a, 3, 3)).isFalse();
        // 第 3 题也中 → 收敛
        a.add(VoiceModels.evaluate(Q, "A"));
        assertThat(VoiceModels.converged(a, 3, 3)).isTrue();
        // 中途选错打断连中 → 不收敛
        a.set(2, VoiceModels.evaluate(Q, "C"));
        assertThat(VoiceModels.converged(a, 3, 3)).isFalse();
        // 自定义输入打断 → 不收敛
        a.set(2, AnswerOutcome.ofCustom());
        assertThat(VoiceModels.converged(a, 3, 3)).isFalse();
    }

    @Test
    void optionRanksAreNormalizedSafe() {
        Option o = new Option("A", "x", null, 0);
        assertThat(o.trait()).isEmpty();
        assertThat(o.predictRank()).isEqualTo(99); // 非法 rank 不会伪装成"最可能"
    }
}
