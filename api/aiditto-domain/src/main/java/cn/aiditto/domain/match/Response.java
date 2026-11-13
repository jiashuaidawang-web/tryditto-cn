package cn.aiditto.domain.match;

/** {@code match_side.response}. Changes from {@link #PENDING} at most once. */
public enum Response {
    PENDING,
    ACCEPT,
    DECLINE;

    public String dbValue() {
        return name().toLowerCase();
    }
}
