package cn.aiditto.domain.feedback.v4.strategy;

import java.time.Instant;

/**
 * Split by publication time. A different matching-period label is not this split.
 */
public final class TimeSplit {
    private TimeSplit() {}

    public static boolean development(MatchingObservation row, Instant cutoff) {
        return !row.publishedAt().isAfter(cutoff);
    }
}
