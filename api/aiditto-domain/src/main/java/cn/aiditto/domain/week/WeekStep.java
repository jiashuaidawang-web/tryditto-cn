package cn.aiditto.domain.week;

/** The five batch steps, in order. Each step moves the week from {@link #from()} to {@link #to()}. */
public enum WeekStep {
    LOCK(WeekState.OPEN, WeekState.LOCKED),
    SOLVE(WeekState.LOCKED, WeekState.SOLVED),
    COPY(WeekState.SOLVED, WeekState.COPIED),
    PUBLISH(WeekState.COPIED, WeekState.PUBLISHED),
    CLOSE(WeekState.PUBLISHED, WeekState.CLOSED);

    private final WeekState from;
    private final WeekState to;

    WeekStep(WeekState from, WeekState to) {
        this.from = from;
        this.to = to;
    }

    public WeekState from() {
        return from;
    }

    public WeekState to() {
        return to;
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
