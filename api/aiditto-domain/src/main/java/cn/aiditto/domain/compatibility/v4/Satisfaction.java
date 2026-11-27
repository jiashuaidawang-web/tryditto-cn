package cn.aiditto.domain.compatibility.v4;

/** How far one stated preference is met by the other person's own feature. Unknown is not a number. */
public enum Satisfaction {
    SATISFIED,
    PARTIALLY_SATISFIED,
    UNSATISFIED,
    UNKNOWN,
    NOT_APPLICABLE,
    CONTESTED;

    public Double value() {
        return switch (this) {
            case SATISFIED -> 1.0;
            case PARTIALLY_SATISFIED -> 0.5;
            case UNSATISFIED -> 0.0;
            case UNKNOWN, NOT_APPLICABLE, CONTESTED -> null;
        };
    }

    public boolean known() {
        return value() != null;
    }
}
