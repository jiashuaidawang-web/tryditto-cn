package cn.aiditto.domain.week;

/** {@code week.state}: {@code open → locked → solved → copied → published → closed}, any step may fail (技术方案 §5.3). */
public enum WeekState {
    OPEN,
    LOCKED,
    SOLVED,
    COPIED,
    PUBLISHED,
    CLOSED,
    FAILED;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static WeekState fromDb(String value) {
        return valueOf(value.toUpperCase());
    }
}
