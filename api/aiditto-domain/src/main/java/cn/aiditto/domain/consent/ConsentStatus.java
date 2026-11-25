package cn.aiditto.domain.consent;

/** Whether this person has agreed to be put into weekly matching. Absent row means {@link #UNKNOWN}. */
public enum ConsentStatus {
    /** No recorded choice. Not the same as agreeing. */
    UNKNOWN,
    /** The person explicitly agreed to take part in matching. */
    GRANTED,
    /** The person explicitly withdrew matching permission. */
    REVOKED;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static ConsentStatus fromDb(String value) {
        return valueOf(value.toUpperCase());
    }
}
