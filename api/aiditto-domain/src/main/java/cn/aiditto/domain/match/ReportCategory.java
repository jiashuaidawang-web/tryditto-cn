package cn.aiditto.domain.match;

/** Frontend {@code REPORT_CATEGORIES}. Urgent ones suspend the reported user pending review (技术方案 §4.7). */
public enum ReportCategory {
    HARASSMENT("harassment", true),
    SCAM("scam", true),
    FAKE("fake", false),
    OFFLINE("offline", true),
    MINOR("minor", true),
    OTHER("other", false);

    private final String id;
    private final boolean urgent;

    ReportCategory(String id, boolean urgent) {
        this.id = id;
        this.urgent = urgent;
    }

    public String id() {
        return id;
    }

    public boolean urgent() {
        return urgent;
    }

    public static ReportCategory fromId(String id) {
        for (ReportCategory c : values()) if (c.id.equals(id)) return c;
        throw new MatchRuleException(MatchError.CATEGORY_UNKNOWN, "unknown report category: " + id);
    }
}
