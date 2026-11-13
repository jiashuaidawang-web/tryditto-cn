package cn.aiditto.domain.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlotRulesTest {
    private static final Instant PUBLISH = Instant.parse("2026-11-18T11:00:00Z");
    private static final Instant DUE = Deadlines.respondDue(PUBLISH);

    @Test
    void proposesFriSatSunAfterTheResponseDeadline() {
        List<Slot> s = SlotRules.propose(DUE);
        assertThat(s).extracting(Slot::id).containsExactly("slot_0", "slot_1", "slot_2");
        assertThat(s).extracting(Slot::startsAt).containsExactly(
                Instant.parse("2026-11-20T10:30:00Z"),
                Instant.parse("2026-11-21T07:30:00Z"),
                Instant.parse("2026-11-22T04:00:00Z"));
    }

    @Test
    void skipsClosedTimesAndExtendsToMondayTuesday() {
        List<Slot> s = SlotRules.propose(DUE, t -> t.getDayOfWeek() != DayOfWeek.SATURDAY && t.getDayOfWeek() != DayOfWeek.SUNDAY);
        assertThat(s).hasSize(3);
        List<ZonedDateTime> local = s.stream().map(x -> x.startsAt().atZone(Deadlines.ZONE)).toList();
        assertThat(local.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(local.get(1).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(local.get(1).toLocalTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(local.get(2).toLocalTime()).isEqualTo(LocalTime.of(15, 30));
        assertThat(s).isSortedAccordingTo(Comparator.comparing(Slot::startsAt));
    }

    @Test
    void neverAfterEightPmAndAllAfterTheDeadline() {
        for (Slot s : SlotRules.propose(DUE, t -> t.getHour() != 18)) {
            assertThat(s.startsAt()).isAfter(DUE);
            assertThat(s.endsAt().atZone(Deadlines.ZONE).toLocalTime()).isBeforeOrEqualTo(LocalTime.of(20, 0));
        }
    }

    @Test
    void venueClosedAllWeekYieldsNothing() {
        assertThat(SlotRules.propose(DUE, t -> false)).isEmpty();
    }

    @Test
    void selectionChecks() {
        List<Slot> s = SlotRules.propose(DUE);
        assertThat(SlotRules.checkSelection(s, Set.of())).contains(SlotRules.SelectionProblem.EMPTY);
        assertThat(SlotRules.checkSelection(s, null)).contains(SlotRules.SelectionProblem.EMPTY);
        assertThat(SlotRules.checkSelection(s, Set.of("slot_0", "slot_7"))).contains(SlotRules.SelectionProblem.UNKNOWN_SLOT);
        assertThat(SlotRules.checkSelection(s, Set.of("slot_2"))).isEmpty();
    }

    @Test
    void pickConfirmedTakesEarliestCommonSlotSixHoursOut() {
        List<Slot> s = SlotRules.propose(DUE);
        Instant now = DUE;
        assertThat(SlotRules.pickConfirmed(s, Set.of("slot_0", "slot_2"), Set.of("slot_2", "slot_0"), now).map(Slot::id)).contains("slot_0");
        assertThat(SlotRules.pickConfirmed(s, Set.of("slot_0"), Set.of("slot_1"), now)).isEmpty();
        Instant fiveHoursBefore = s.get(0).startsAt().minusSeconds(5 * 3600);
        assertThat(SlotRules.pickConfirmed(s, Set.of("slot_0", "slot_1"), Set.of("slot_0", "slot_1"), fiveHoursBefore).map(Slot::id)).contains("slot_1");
        Instant sixHoursBefore = s.get(0).startsAt().minusSeconds(6 * 3600);
        assertThat(SlotRules.pickConfirmed(s, Set.of("slot_0"), Set.of("slot_0"), sixHoursBefore)).isPresent();
        assertThat(SlotRules.pickConfirmed(s, Set.of("slot_0"), Set.of("slot_0"), sixHoursBefore.plusMillis(1))).isEqualTo(Optional.empty());
    }
}
