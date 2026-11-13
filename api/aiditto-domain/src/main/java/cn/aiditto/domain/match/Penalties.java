package cn.aiditto.domain.match;

import java.time.Duration;
import java.time.Instant;

/** No-show ladder and auto pause (PRD §6.6, 技术方案 §5.1). Counts reset each semester; the app owns that. */
public final class Penalties {
    public static final Duration FIRST_SUSPENSION = Duration.ofDays(7);
    public static final Duration SECOND_SUSPENSION = Duration.ofDays(28);
    public static final int NO_RESPONSE_WEEKS_BEFORE_PAUSE = 2;

    /** {@code suspendedUntil == null && stopped} means "stopped from matching until an appeal succeeds". */
    public record Penalty(int effectiveNoShows, Instant suspendedUntil, boolean stopped) {
        public static final Penalty NONE = new Penalty(0, null, false);
    }

    private Penalties() {}

    /** Two late cancels count as one no-show. */
    public static int effectiveNoShows(int noShows, int lateCancels) {
        return noShows + lateCancels / 2;
    }

    /** Penalty after the count just went up: 1 → one week, 2 → four weeks, 3+ → stopped. */
    public static Penalty forNoShows(int noShows, int lateCancels, Instant now) {
        int n = effectiveNoShows(noShows, lateCancels);
        if (n <= 0) return Penalty.NONE;
        if (n == 1) return new Penalty(n, now.plus(FIRST_SUSPENSION), false);
        if (n == 2) return new Penalty(n, now.plus(SECOND_SUSPENSION), false);
        return new Penalty(n, null, true);
    }

    /** Two consecutive weeks without responding pauses participation until the user comes back. */
    public static boolean shouldAutoPause(int consecutiveNoResponseWeeks) {
        return consecutiveNoResponseWeeks >= NO_RESPONSE_WEEKS_BEFORE_PAUSE;
    }
}
