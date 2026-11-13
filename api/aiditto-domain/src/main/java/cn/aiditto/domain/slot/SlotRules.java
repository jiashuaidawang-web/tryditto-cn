package cn.aiditto.domain.slot;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Which slots a pair is offered, whether a selection is valid, and which slot gets confirmed
 * (技术方案 §5.1, §7.6).
 */
public final class SlotRules {
    public static final int SLOTS_PER_MATCH = 3;

    /** Primary grid: Fri 18:30, Sat 15:30, Sun 12:00 (frontend {@code proposedSlots}). */
    static final List<DayTime> PRIMARY = List.of(
            new DayTime(DayOfWeek.FRIDAY, LocalTime.of(18, 30)),
            new DayTime(DayOfWeek.SATURDAY, LocalTime.of(15, 30)),
            new DayTime(DayOfWeek.SUNDAY, LocalTime.of(12, 0)));

    /** Extension when the venue is closed for some primary slots: Mon and Tue at 12:00, 15:30, 18:30. */
    static final List<DayTime> EXTENSION = List.of(
            new DayTime(DayOfWeek.MONDAY, LocalTime.of(12, 0)),
            new DayTime(DayOfWeek.MONDAY, LocalTime.of(15, 30)),
            new DayTime(DayOfWeek.MONDAY, LocalTime.of(18, 30)),
            new DayTime(DayOfWeek.TUESDAY, LocalTime.of(12, 0)),
            new DayTime(DayOfWeek.TUESDAY, LocalTime.of(15, 30)),
            new DayTime(DayOfWeek.TUESDAY, LocalTime.of(18, 30)));

    record DayTime(DayOfWeek day, LocalTime time) {}

    public enum SelectionProblem { EMPTY, UNKNOWN_SLOT }

    private SlotRules() {}

    /**
     * Up to 3 slots in the weekend following {@code respondDue}, skipping times the venue is closed
     * ({@code venueOpen} receives the slot start in Asia/Shanghai). Ids are {@code slot_0..slot_2}
     * in chronological order. May return fewer than 3 when the venue is closed nearly all week.
     */
    public static List<Slot> propose(Instant respondDue, Predicate<ZonedDateTime> venueOpen) {
        ZonedDateTime due = respondDue.atZone(Deadlines.ZONE);
        LocalDate friday = due.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        List<ZonedDateTime> picked = new ArrayList<>();
        for (List<DayTime> grid : List.of(PRIMARY, EXTENSION)) {
            for (DayTime dt : grid) {
                if (picked.size() == SLOTS_PER_MATCH) break;
                ZonedDateTime start = atOrAfter(friday, dt).atTime(dt.time()).atZone(Deadlines.ZONE);
                if (start.isAfter(due) && venueOpen.test(start)) {
                    picked.add(start);
                }
            }
        }
        picked.sort(Comparator.naturalOrder());
        List<Slot> slots = new ArrayList<>(picked.size());
        for (int i = 0; i < picked.size(); i++) {
            slots.add(new Slot("slot_" + i, picked.get(i).toInstant()));
        }
        return List.copyOf(slots);
    }

    public static List<Slot> propose(Instant respondDue) {
        return propose(respondDue, t -> true);
    }

    private static LocalDate atOrAfter(LocalDate friday, DayTime dt) {
        return friday.with(TemporalAdjusters.nextOrSame(dt.day()));
    }

    /** Empty when {@code chosen} is a non-empty subset of the proposed slot ids. */
    public static Optional<SelectionProblem> checkSelection(List<Slot> proposed, Collection<String> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return Optional.of(SelectionProblem.EMPTY);
        }
        Set<String> known = proposed.stream().map(Slot::id).collect(Collectors.toSet());
        return known.containsAll(chosen) ? Optional.empty() : Optional.of(SelectionProblem.UNKNOWN_SLOT);
    }

    /**
     * The earliest slot both people picked that starts at least {@link Deadlines#MIN_CONFIRM_LEAD}
     * after {@code now}. Empty means "no overlap" (an overlap that is too soon counts as none).
     */
    public static Optional<Slot> pickConfirmed(List<Slot> proposed, Set<String> a, Set<String> b, Instant now) {
        Instant earliest = now.plus(Deadlines.MIN_CONFIRM_LEAD);
        return proposed.stream()
                .filter(s -> a.contains(s.id()) && b.contains(s.id()))
                .filter(s -> !s.startsAt().isBefore(earliest))
                .min(Comparator.comparing(Slot::startsAt).thenComparing(Slot::id));
    }
}
