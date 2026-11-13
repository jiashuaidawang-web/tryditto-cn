package cn.aiditto.domain.week;

import java.util.Optional;

/**
 * Batch step bookkeeping (技术方案 §5.3, §5.4). Each step is {@code step(weekId)}: ask {@link #begin},
 * do nothing on {@link Decision#ALREADY_DONE}, otherwise do the work and record {@link #succeed} or
 * {@link #fail}. A failed step is retried by the next run of that same step.
 */
public final class WeekMachine {

    public enum Decision {
        /** Run the step now. */
        RUN,
        /** The step already completed; return immediately (idempotent re-run). */
        ALREADY_DONE
    }

    private WeekMachine() {}

    /**
     * Whether {@code step} should run given the current status.
     *
     * @throws WeekRuleException when an earlier step has not completed yet, or a different step failed
     */
    public static Decision begin(WeekStatus status, WeekStep step) {
        if (status.state() == WeekState.FAILED) {
            if (status.failedStep() == step) return Decision.RUN;
            if (status.failedStep().ordinal() > step.ordinal()) return Decision.ALREADY_DONE;
            throw new WeekRuleException(step + " cannot run: " + status.failedStep() + " failed and must be retried first");
        }
        WeekState s = status.state();
        if (s == step.from()) return Decision.RUN;
        if (s.ordinal() > step.from().ordinal()) return Decision.ALREADY_DONE;
        throw new WeekRuleException(step + " cannot run from " + s.dbValue());
    }

    /**
     * Status after {@code step} finished. {@code allMatchesTerminal} is only consulted for
     * {@link WeekStep#CLOSE}: a week closes only once every match in it is terminal.
     */
    public static WeekStatus succeed(WeekStatus status, WeekStep step, boolean allMatchesTerminal) {
        if (begin(status, step) != Decision.RUN) {
            return status;
        }
        if (step == WeekStep.CLOSE && !allMatchesTerminal) {
            throw new WeekRuleException("week cannot close while a match is still open");
        }
        return WeekStatus.of(step.to());
    }

    public static WeekStatus succeed(WeekStatus status, WeekStep step) {
        return succeed(status, step, true);
    }

    public static WeekStatus fail(WeekStatus status, WeekStep step) {
        if (begin(status, step) != Decision.RUN) {
            throw new WeekRuleException(step + " already completed; cannot mark it failed");
        }
        return WeekStatus.failed(step);
    }

    /** The next step to run, or empty when the week is closed. */
    public static Optional<WeekStep> nextStep(WeekStatus status) {
        if (status.state() == WeekState.FAILED) return Optional.of(status.failedStep());
        for (WeekStep step : WeekStep.values()) {
            if (step.from() == status.state()) return Optional.of(step);
        }
        return Optional.empty();
    }
}
