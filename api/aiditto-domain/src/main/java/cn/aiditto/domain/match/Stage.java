package cn.aiditto.domain.match;

/**
 * What one person sees right now: the frontend {@code MatchStage}, plus three stages the frontend does
 * not have yet ({@link #NO_OVERLAP} = F7, {@link #PAUSED} and {@link #SUSPENDED} = PRD §6.2).
 */
public enum Stage {
    WAITING("waiting"),
    EMPTY_WEEK("emptyWeek"),
    PAUSED("paused"),
    SUSPENDED("suspended"),
    REVEALED("revealed"),
    DECLINED("declined"),
    AWAITING_PARTNER("awaitingPartner"),
    NO_OVERLAP("noOverlap"),
    CONFIRMED("confirmed"),
    DATE_DAY("dateDay"),
    CHECKED_IN("checkedIn"),
    FEEDBACK("feedback"),
    AWAITING_FEEDBACK("awaitingFeedback"),
    MUTUAL("mutual"),
    CLOSED("closed"),
    REPORTED("reported");

    private final String wire;

    Stage(String wire) {
        this.wire = wire;
    }

    /** Stage id for a frontend that knows {@code noOverlap}, {@code paused} and {@code suspended}. */
    public String wire() {
        return wire;
    }

    /** Stage id for today's frontend: {@code noOverlap → closed} (F7), paused/suspended → {@code waiting}. */
    public String legacyWire() {
        return switch (this) {
            case NO_OVERLAP -> CLOSED.wire;
            case PAUSED, SUSPENDED -> WAITING.wire;
            default -> wire;
        };
    }
}
