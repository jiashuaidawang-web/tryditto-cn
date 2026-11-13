package cn.aiditto.domain.week;

/** A batch step was requested out of order (a programming or scheduling error, never user-facing). */
public class WeekRuleException extends RuntimeException {
    public WeekRuleException(String message) {
        super(message);
    }
}
