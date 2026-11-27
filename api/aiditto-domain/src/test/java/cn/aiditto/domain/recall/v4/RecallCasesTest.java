package cn.aiditto.domain.recall.v4;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecallCasesTest {
    @Test
    void sixStoriesExplainRouteFusionRejectionBudgetAndDirection() {
        List<RecallCases.Story> stories = RecallCases.six();
        assertThat(stories).hasSize(6);
        RecallCases.Story hobby = stories.get(0);
        assertThat(hobby.result().hits()).hasSize(1);
        assertThat(hobby.result().hits().get(0).recallReasonCode()).isEqualTo("R1");
        assertThat(stories.get(1).result().hits().get(0).recallReasonCode()).isEqualTo("R2");
        V4RecallEngine.Hit fused = stories.get(2).result().hits().get(0);
        assertThat(fused.recallReasonCode()).isEqualTo("FUSED");
        assertThat(fused.routes()).extracting(V4RecallEngine.RouteHit::route).contains("R1", "R3");
        assertThat(stories.get(3).result().hits()).isEmpty();
        assertThat(stories.get(3).result().rejected()).anyMatch(rejection -> rejection.reasonCodes().contains("HARD_AGE"));
        assertThat(stories.get(4).result().legalCount()).isEqualTo(5);
        assertThat(stories.get(4).result().hits()).hasSize(2);
        assertThat(stories.get(5).note()).contains("正向列表 1", "反向列表 0", "撤回后列表 0");
        assertThat(stories).allMatch(story -> story.result().hardViolations() == 0);
    }
}
