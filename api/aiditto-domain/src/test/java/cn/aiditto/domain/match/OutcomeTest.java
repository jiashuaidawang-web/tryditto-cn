package cn.aiditto.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.match.FeedbackAnswer.Again;
import cn.aiditto.domain.match.FeedbackAnswer.Met;
import cn.aiditto.domain.match.Outcome.Judgement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutcomeTest {
    private static final FeedbackAnswer YES = FeedbackAnswer.met(Again.YES);
    private static final FeedbackAnswer NO = FeedbackAnswer.met(Again.NO);
    private static final FeedbackAnswer UNSURE = FeedbackAnswer.met(Again.UNSURE);
    private static final FeedbackAnswer NO_SHOW = FeedbackAnswer.of(Met.NO_SHOW);
    private static final FeedbackAnswer SKIPPED = FeedbackAnswer.of(Met.I_SKIPPED);

    @Test
    void specTable() {
        assertThat(Outcome.judge(YES, YES)).isEqualTo(new Judgement(Outcome.MUTUAL, false, false));
        assertThat(Outcome.judge(YES, NO)).isEqualTo(new Judgement(Outcome.NOT_MUTUAL, false, false));
        assertThat(Outcome.judge(UNSURE, YES)).isEqualTo(new Judgement(Outcome.NOT_MUTUAL, false, false));
        assertThat(Outcome.judge(NO_SHOW, SKIPPED)).isEqualTo(new Judgement(Outcome.NO_SHOW, false, true));
        assertThat(Outcome.judge(NO_SHOW, null)).isEqualTo(new Judgement(Outcome.NO_SHOW, false, true));
        assertThat(Outcome.judge(NO_SHOW, YES)).isEqualTo(new Judgement(Outcome.DISPUTED, false, false));
        assertThat(Outcome.judge(NO_SHOW, NO_SHOW)).isEqualTo(new Judgement(Outcome.DISPUTED, false, false));
        assertThat(Outcome.judge(null, null)).isEqualTo(new Judgement(Outcome.NO_FEEDBACK, false, false));
    }

    @Test
    void casesTheSpecLeavesOpen() {
        assertThat(Outcome.judge(YES, null)).isEqualTo(new Judgement(Outcome.NOT_MUTUAL, false, false));
        assertThat(Outcome.judge(YES, SKIPPED)).isEqualTo(new Judgement(Outcome.DISPUTED, false, false));
        assertThat(Outcome.judge(SKIPPED, null)).isEqualTo(new Judgement(Outcome.NO_SHOW, true, false));
        assertThat(Outcome.judge(SKIPPED, SKIPPED)).isEqualTo(new Judgement(Outcome.NO_SHOW, true, true));
    }

    @Test
    void symmetricOverEveryCombination() {
        List<FeedbackAnswer> all = new ArrayList<>(Arrays.asList(YES, NO, UNSURE, NO_SHOW, SKIPPED, null));
        for (FeedbackAnswer a : all) {
            for (FeedbackAnswer b : all) {
                Judgement ab = Outcome.judge(a, b);
                Judgement ba = Outcome.judge(b, a);
                assertThat(ba).as(a + " vs " + b).isEqualTo(new Judgement(ab.outcome(), ab.noShowB(), ab.noShowA()));
                assertThat(ab.outcome()).isIn(Outcome.MUTUAL, Outcome.NOT_MUTUAL, Outcome.NO_SHOW, Outcome.DISPUTED, Outcome.NO_FEEDBACK);
                if (ab.outcome() != Outcome.NO_SHOW) assertThat(ab.noShowA() || ab.noShowB()).isFalse();
                if (ab.outcome() == Outcome.MUTUAL) assertThat(a.wantsAgain() && b.wantsAgain()).isTrue();
            }
        }
    }
}
