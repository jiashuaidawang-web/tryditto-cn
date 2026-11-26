package cn.aiditto.domain.relationship;

import java.time.Instant;

/** One stored snapshot. Rows are not edited in place. Withdrawal sets {@code retractedAt} and clears current. */
public record ProfileVersion(
        long userId,
        int version,
        int schemaVersion,
        String featureRegistryVersion,
        int sourceProfileVersion,
        String inputHash,
        Instant createdAt,
        boolean current,
        Instant retractedAt) {

    public ProfileVersion {
        if (userId <= 0 || version < 1 || schemaVersion < 1) throw new IllegalArgumentException("version");
        if (featureRegistryVersion == null || inputHash == null || inputHash.length() != 64) throw new IllegalArgumentException("version identity");
        if (sourceProfileVersion < 0) throw new IllegalArgumentException("source");
    }
}
