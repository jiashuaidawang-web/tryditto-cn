package cn.aiditto.domain.match;

/** A rejected match event. The app maps {@link #code()} to an HTTP problem response. */
public class MatchRuleException extends RuntimeException {
    private final MatchError code;

    public MatchRuleException(MatchError code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public MatchError code() {
        return code;
    }
}
