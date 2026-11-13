package cn.aiditto.domain.matching;

/**
 * Who a person is allowed to be paired with. Real accounts never see synthetic users. Synthetic users
 * exist only so the four demo accounts have partners.
 */
public enum Cohort {
    REAL,
    DEMO,
    SYNTHETIC;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static Cohort fromDb(String value) {
        if (value == null || value.isBlank()) return REAL;
        return valueOf(value.toUpperCase());
    }
}
