package cn.aiditto.domain.week;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.aiditto.domain.week.WeekMachine.Decision;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeekMachineTest {

    @Test
    void happyPathVisitsEveryState() {
        WeekStatus s = WeekStatus.of(WeekState.OPEN);
        for (WeekStep step : WeekStep.values()) {
            assertThat(WeekMachine.nextStep(s)).contains(step);
            assertThat(WeekMachine.begin(s, step)).isEqualTo(Decision.RUN);
            s = WeekMachine.succeed(s, step);
            assertThat(s.state()).isEqualTo(step.to());
        }
        assertThat(s.state()).isEqualTo(WeekState.CLOSED);
        assertThat(WeekMachine.nextStep(s)).isEmpty();
    }

    @Test
    void everyStepFromEveryState() {
        for (WeekState state : WeekState.values()) {
            if (state == WeekState.FAILED) continue;
            WeekStatus s = WeekStatus.of(state);
            for (WeekStep step : WeekStep.values()) {
                if (state == step.from()) {
                    assertThat(WeekMachine.begin(s, step)).isEqualTo(Decision.RUN);
                } else if (state.ordinal() > step.from().ordinal()) {
                    assertThat(WeekMachine.begin(s, step)).as(state + "/" + step).isEqualTo(Decision.ALREADY_DONE);
                    assertThat(WeekMachine.succeed(s, step)).as("re-running a done step changes nothing").isEqualTo(s);
                } else {
                    assertThatThrownBy(() -> WeekMachine.begin(s, step)).as(state + "/" + step).isInstanceOf(WeekRuleException.class);
                }
            }
        }
    }

    @Test
    void failedStepIsRetriedNext() {
        WeekStatus locked = WeekStatus.of(WeekState.LOCKED);
        WeekStatus failed = WeekMachine.fail(locked, WeekStep.SOLVE);
        assertThat(failed).isEqualTo(WeekStatus.failed(WeekStep.SOLVE));
        assertThat(failed.effectiveState()).isEqualTo(WeekState.LOCKED);
        assertThat(WeekMachine.nextStep(failed)).contains(WeekStep.SOLVE);
        assertThat(WeekMachine.begin(failed, WeekStep.LOCK)).isEqualTo(Decision.ALREADY_DONE);
        assertThat(WeekMachine.begin(failed, WeekStep.SOLVE)).isEqualTo(Decision.RUN);
        assertThatThrownBy(() -> WeekMachine.begin(failed, WeekStep.COPY)).isInstanceOf(WeekRuleException.class);
        assertThat(WeekMachine.succeed(failed, WeekStep.SOLVE)).isEqualTo(WeekStatus.of(WeekState.SOLVED));
        assertThat(WeekMachine.fail(failed, WeekStep.SOLVE)).as("failing again keeps it retryable").isEqualTo(failed);
    }

    @Test
    void cannotFailACompletedStep() {
        assertThatThrownBy(() -> WeekMachine.fail(WeekStatus.of(WeekState.SOLVED), WeekStep.SOLVE)).isInstanceOf(WeekRuleException.class);
    }

    @Test
    void closeRequiresAllMatchesTerminal() {
        WeekStatus published = WeekStatus.of(WeekState.PUBLISHED);
        assertThatThrownBy(() -> WeekMachine.succeed(published, WeekStep.CLOSE, false)).isInstanceOf(WeekRuleException.class);
        assertThat(WeekMachine.succeed(published, WeekStep.CLOSE, true).state()).isEqualTo(WeekState.CLOSED);
    }

    @Test
    void statusValidation() {
        assertThatThrownBy(() -> new WeekStatus(WeekState.FAILED, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeekStatus(WeekState.OPEN, WeekStep.LOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThat(WeekState.fromDb("copied")).isEqualTo(WeekState.COPIED);
        assertThat(WeekMachine.nextStep(WeekStatus.of(WeekState.COPIED))).isEqualTo(Optional.of(WeekStep.PUBLISH));
    }
}
