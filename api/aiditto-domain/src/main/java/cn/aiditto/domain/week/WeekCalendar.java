package cn.aiditto.domain.week;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Scheduled times of a weekly batch (Asia/Shanghai, 技术方案 §5.3). A week is identified by
 * {@code weekStart}, the Wednesday of the drop. Its cycle runs from the lock (Tuesday 23:59 before)
 * to the next lock a week later.
 */
public final class WeekCalendar {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalTime LOCK = LocalTime.of(23, 59);
    private static final LocalTime SOLVE = LocalTime.of(0, 10);
    private static final LocalTime COPY = LocalTime.of(0, 30);
    private static final LocalTime PUBLISH = LocalTime.of(19, 0);
    private static final LocalTime CLOSE = LocalTime.of(23, 0);

    private WeekCalendar() {}

    /** The Wednesday whose cycle contains {@code now}: the latest Wednesday on or before {@code now + 1 min}. */
    public static LocalDate weekStartFor(Instant now) {
        LocalDate d = now.plus(Duration.ofMinutes(1)).atZone(ZONE).toLocalDate();
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
    }

    /** Earliest time {@code step} may run in a scheduled week. Manual runs ignore this. */
    public static Instant scheduledAt(LocalDate weekStart, WeekStep step) {
        requireWednesday(weekStart);
        return switch (step) {
            case LOCK -> weekStart.minusDays(1).atTime(LOCK).atZone(ZONE).toInstant();
            case SOLVE -> weekStart.atTime(SOLVE).atZone(ZONE).toInstant();
            case COPY -> weekStart.atTime(COPY).atZone(ZONE).toInstant();
            case PUBLISH -> weekStart.atTime(PUBLISH).atZone(ZONE).toInstant();
            case CLOSE -> weekStart.plusDays(6).atTime(CLOSE).atZone(ZONE).toInstant();
        };
    }

    public static Instant publishAt(LocalDate weekStart) {
        return scheduledAt(weekStart, WeekStep.PUBLISH);
    }

    /** Whether the scheduler should attempt {@code step} now (it is due and is the next step). */
    public static boolean isDue(LocalDate weekStart, WeekStatus status, WeekStep step, Instant now) {
        return WeekMachine.nextStep(status).filter(s -> s == step).isPresent()
                && !now.isBefore(scheduledAt(weekStart, step));
    }

    /** Age is measured on the drop Wednesday (技术方案 §7.2 rule 3). */
    public static int ageOn(LocalDate birthday, LocalDate weekStart) {
        return Period.between(birthday, weekStart).getYears();
    }

    private static void requireWednesday(LocalDate d) {
        if (d.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            throw new IllegalArgumentException("weekStart must be a Wednesday: " + d);
        }
    }
}
