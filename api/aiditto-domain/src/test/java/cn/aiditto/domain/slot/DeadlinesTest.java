package cn.aiditto.domain.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeadlinesTest {

    @Test
    void scheduledPublishRespondsByThursday22() {
        assertThat(Deadlines.respondDue(Instant.parse("2026-11-18T11:00:00Z"))).isEqualTo(Instant.parse("2026-11-19T14:00:00Z"));
    }

    @Test
    void lateManualPublishRollsToNextThursday() {
        Instant thursdayEvening = Instant.parse("2026-11-19T12:00:00Z");
        assertThat(Deadlines.respondDue(thursdayEvening)).isEqualTo(Instant.parse("2026-11-26T14:00:00Z"));
        Instant wednesdayMorning = Instant.parse("2026-11-18T01:00:00Z");
        assertThat(Deadlines.respondDue(wednesdayMorning)).isEqualTo(Instant.parse("2026-11-19T14:00:00Z"));
        assertThat(Duration.between(thursdayEvening, Deadlines.respondDue(thursdayEvening))).isGreaterThanOrEqualTo(Deadlines.MIN_RESPOND_WINDOW);
    }

    @Test
    void reselectDueIsFridayNoonOrEarliestSlotMinusSix() {
        Instant due = Instant.parse("2026-11-19T14:00:00Z");
        List<Slot> normal = SlotRules.propose(due);
        assertThat(Deadlines.reselectDue(due, normal)).isEqualTo(Instant.parse("2026-11-20T04:00:00Z"));
        List<Slot> early = List.of(new Slot("slot_0", Instant.parse("2026-11-20T08:00:00Z")));
        assertThat(Deadlines.reselectDue(due, early)).isEqualTo(Instant.parse("2026-11-20T02:00:00Z"));
        assertThat(Deadlines.reselectDue(due, List.of())).isEqualTo(Instant.parse("2026-11-20T04:00:00Z"));
    }

    @Test
    void windows() {
        Instant start = Instant.parse("2026-11-21T07:30:00Z");
        Instant end = start.plus(Deadlines.SLOT_LENGTH);
        assertThat(Deadlines.feedbackDue(end)).isEqualTo(end.plus(Duration.ofHours(72)));
        assertThat(Deadlines.dateDayOpens(start)).isEqualTo(start.minus(Duration.ofHours(2)));
        assertThat(Deadlines.isLateCancel(start, start.minus(Duration.ofHours(6)))).isTrue();
        assertThat(Deadlines.isLateCancel(start, start.minus(Duration.ofHours(6)).minusMillis(1))).isFalse();
        assertThat(Deadlines.inCheckInWindow(start, end, start.minus(Duration.ofHours(2)))).isTrue();
        assertThat(Deadlines.inCheckInWindow(start, end, end.plusMillis(1))).isFalse();
    }
}
