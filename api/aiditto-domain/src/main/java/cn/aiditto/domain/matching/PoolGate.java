package cn.aiditto.domain.matching;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

/**
 * Who may enter a week's pool at the lock step (技术方案 §5.5, PRD §5.2). The app gathers the facts;
 * this decides and returns the {@code week_participant.exclude_reason}.
 */
public final class PoolGate {
    public static final int MIN_PHOTOS = 2;

    public enum ExcludeReason {
        UNDER_18("under18"),
        NOT_ACTIVE("not_active"),
        INCOMPLETE("incomplete"),
        PHOTOS("photos"),
        PAUSED("paused"),
        SUSPENDED("suspended"),
        OPEN_MATCH("open_match");

        private final String dbValue;

        ExcludeReason(String dbValue) {
            this.dbValue = dbValue;
        }

        public String dbValue() {
            return dbValue;
        }
    }

    /**
     * @param active {@code user.status = active}
     * @param onboardingComplete all required onboarding fields are present
     * @param photoCount active profile photos; synthetic users are exempt
     * @param photoIssue {@code user.photo_issue} is set
     * @param pausedThisWeek the user paused this week
     * @param suspendedUntil {@code user.suspended_until}, or null
     * @param hasOpenMatch a non-terminal match exists from an earlier run
     */
    public record Applicant(LocalDate birthday, boolean active, boolean onboardingComplete, boolean synthetic,
                            int photoCount, boolean photoIssue, boolean pausedThisWeek, Instant suspendedUntil,
                            boolean hasOpenMatch) {}

    private PoolGate() {}

    public static Optional<ExcludeReason> check(Applicant a, LocalDate weekStart, Instant now) {
        if (a.birthday() == null || Period.between(a.birthday(), weekStart).getYears() < HardFilter.ADULT_AGE) {
            return Optional.of(ExcludeReason.UNDER_18);
        }
        if (!a.active()) return Optional.of(ExcludeReason.NOT_ACTIVE);
        if (!a.onboardingComplete()) return Optional.of(ExcludeReason.INCOMPLETE);
        if (!a.synthetic() && (a.photoCount() < MIN_PHOTOS || a.photoIssue())) return Optional.of(ExcludeReason.PHOTOS);
        if (a.pausedThisWeek()) return Optional.of(ExcludeReason.PAUSED);
        if (a.suspendedUntil() != null && a.suspendedUntil().isAfter(now)) return Optional.of(ExcludeReason.SUSPENDED);
        if (a.hasOpenMatch()) return Optional.of(ExcludeReason.OPEN_MATCH);
        return Optional.empty();
    }
}
