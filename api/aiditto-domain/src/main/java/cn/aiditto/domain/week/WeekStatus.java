package cn.aiditto.domain.week;

import java.util.Objects;

/**
 * Persisted batch status. {@code failedStep} is set only when {@code state == FAILED} and names the step
 * that the next run retries (the {@code week.error} column carries the message).
 */
public record WeekStatus(WeekState state, WeekStep failedStep) {

    public WeekStatus {
        Objects.requireNonNull(state, "state");
        if ((state == WeekState.FAILED) != (failedStep != null)) {
            throw new IllegalArgumentException("failedStep must be set exactly when state is FAILED");
        }
    }

    public static WeekStatus of(WeekState state) {
        return new WeekStatus(state, null);
    }

    public static WeekStatus failed(WeekStep step) {
        return new WeekStatus(WeekState.FAILED, step);
    }

    /** The state the batch is logically in: a failed step counts as "not done yet". */
    public WeekState effectiveState() {
        return state == WeekState.FAILED ? failedStep.from() : state;
    }
}
