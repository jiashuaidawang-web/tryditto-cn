package cn.aiditto.domain.relationship;

/**
 * Decides whether a snapshot may be stored. Same canonical input keeps the current row.
 * An older source profile version cannot replace a newer one.
 */
public final class ProfileVersionPolicy {
    private ProfileVersionPolicy() {}

    public enum Kind { KEEP_CURRENT, INSERT, REJECT_INACTIVE, REJECT_STALE, REJECT_CONFLICT, REJECT_DUPLICATE }

    public record Decision(Kind kind) {}

    public static Decision decide(boolean accountCanBeProfiled, Integer currentVersion, Integer currentSource,
                                  String currentHash, boolean sameHashStoredButNotCurrent,
                                  int incomingSource, String incomingHash) {
        if (!accountCanBeProfiled) return new Decision(Kind.REJECT_INACTIVE);
        if (currentVersion != null && incomingHash.equals(currentHash)) return new Decision(Kind.KEEP_CURRENT);
        if (currentVersion != null && incomingSource < currentSource) return new Decision(Kind.REJECT_STALE);
        if (currentVersion != null && incomingSource == currentSource) return new Decision(Kind.REJECT_CONFLICT);
        if (sameHashStoredButNotCurrent) return new Decision(Kind.REJECT_DUPLICATE);
        return new Decision(Kind.INSERT);
    }
}
