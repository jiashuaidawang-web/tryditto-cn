package cn.aiditto.domain.recall.v4;

/** Experimental routes. None of them is allowed to skip the shared eligibility screen. */
public enum RecallRoute {
    R1("R1"),
    R2("R2"),
    R3("R3"),
    R4("R4"),
    R5("R5"),
    R6("R6");

    private final String code;

    RecallRoute(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
