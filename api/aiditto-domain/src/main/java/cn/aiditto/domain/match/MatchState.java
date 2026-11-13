package cn.aiditto.domain.match;

/** {@code match.state} (技术方案 §5.1). {@link #dbValue()} is the column value. */
public enum MatchState {
    DRAFT,
    PUBLISHED,
    DECLINED,
    NO_OVERLAP,
    EXPIRED,
    UNSCHEDULED,
    CONFIRMED,
    CANCELLED,
    FEEDBACK_OPEN,
    CLOSED,
    HALTED;

    public boolean isTerminal() {
        return switch (this) {
            case DECLINED, EXPIRED, UNSCHEDULED, CANCELLED, CLOSED, HALTED -> true;
            default -> false;
        };
    }

    public String dbValue() {
        return name().toLowerCase();
    }

    public static MatchState fromDb(String value) {
        return valueOf(value.toUpperCase());
    }
}
