package cn.aiditto.domain.slot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

/**
 * Every match deadline, computed from instants passed in by the caller (技术方案 §5.1, PRD §6.1).
 * Business times are Asia/Shanghai; results are UTC instants.
 */
public final class Deadlines {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static final Duration SLOT_LENGTH = Duration.ofHours(1);
    /** The confirmed slot must start at least this long after the confirming submit. */
    public static final Duration MIN_CONFIRM_LEAD = Duration.ofHours(6);
    /** Cancelling closer than this to the start is a late cancel. */
    public static final Duration LATE_CANCEL_WINDOW = Duration.ofHours(6);
    /** Date-day page (and check-in) opens this long before the start. */
    public static final Duration DATE_DAY_OPENS = Duration.ofHours(2);
    public static final Duration FEEDBACK_WINDOW = Duration.ofHours(72);
    /** Reports are still accepted this long after a match closed (技术方案 §6.6). */
    public static final Duration REPORT_AFTER_CLOSE = Duration.ofDays(7);
    /** A publish this close to a Thursday 22:00 rolls the response deadline to the next week. */
    public static final Duration MIN_RESPOND_WINDOW = Duration.ofHours(12);

    static final LocalTime RESPOND_TIME = LocalTime.of(22, 0);
    static final LocalTime RESELECT_TIME = LocalTime.of(12, 0);

    private Deadlines() {}

    /** First Thursday 22:00 (Shanghai) that is at least {@link #MIN_RESPOND_WINDOW} after publishing. */
    public static Instant respondDue(Instant publishedAt) {
        ZonedDateTime earliest = publishedAt.plus(MIN_RESPOND_WINDOW).atZone(ZONE);
        ZonedDateTime due = earliest.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY)).with(RESPOND_TIME);
        if (due.isBefore(earliest)) {
            due = due.plusWeeks(1);
        }
        return due.toInstant();
    }

    /** {@code min(the Friday 12:00 right after respondDue, earliest proposed slot − 6h)}. */
    public static Instant reselectDue(Instant respondDue, List<Slot> proposed) {
        ZonedDateTime r = respondDue.atZone(ZONE);
        ZonedDateTime friday = r.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).with(RESELECT_TIME);
        if (!friday.isAfter(r)) {
            friday = friday.plusWeeks(1);
        }
        Instant cap = friday.toInstant();
        return proposed.stream()
                .map(Slot::startsAt)
                .min(Comparator.naturalOrder())
                .map(first -> first.minus(MIN_CONFIRM_LEAD))
                .filter(t -> t.isBefore(cap))
                .orElse(cap);
    }

    public static Instant feedbackDue(Instant slotEnd) {
        return slotEnd.plus(FEEDBACK_WINDOW);
    }

    public static Instant dateDayOpens(Instant slotStart) {
        return slotStart.minus(DATE_DAY_OPENS);
    }

    public static boolean isLateCancel(Instant slotStart, Instant now) {
        return !now.isBefore(slotStart.minus(LATE_CANCEL_WINDOW));
    }

    /** Check-in is allowed from 2 hours before the start until the slot ends (inclusive). */
    public static boolean inCheckInWindow(Instant slotStart, Instant slotEnd, Instant now) {
        return !now.isBefore(dateDayOpens(slotStart)) && !now.isAfter(slotEnd);
    }
}
