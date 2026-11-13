package cn.aiditto.domain.week;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeekCalendarTest {
    private static final LocalDate WED = LocalDate.of(2026, 11, 18);

    @Test
    void scheduleInShanghaiTime() {
        assertThat(WeekCalendar.scheduledAt(WED, WeekStep.LOCK)).isEqualTo(Instant.parse("2026-11-17T15:59:00Z"));
        assertThat(WeekCalendar.scheduledAt(WED, WeekStep.SOLVE)).isEqualTo(Instant.parse("2026-11-17T16:10:00Z"));
        assertThat(WeekCalendar.scheduledAt(WED, WeekStep.COPY)).isEqualTo(Instant.parse("2026-11-17T16:30:00Z"));
        assertThat(WeekCalendar.publishAt(WED)).isEqualTo(Instant.parse("2026-11-18T11:00:00Z"));
        assertThat(WeekCalendar.scheduledAt(WED, WeekStep.CLOSE)).isEqualTo(Instant.parse("2026-11-24T15:00:00Z"));
    }

    @Test
    void weekStartFollowsTheLock() {
        assertThat(WeekCalendar.weekStartFor(Instant.parse("2026-11-17T15:58:59Z"))).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(WeekCalendar.weekStartFor(Instant.parse("2026-11-17T15:59:00Z"))).isEqualTo(WED);
        assertThat(WeekCalendar.weekStartFor(Instant.parse("2026-11-20T00:00:00Z"))).isEqualTo(WED);
        assertThat(WeekCalendar.weekStartFor(Instant.parse("2026-11-24T15:58:00Z"))).isEqualTo(WED);
    }

    @Test
    void dueOnlyWhenNextAndTimeReached() {
        WeekStatus open = WeekStatus.of(WeekState.OPEN);
        Instant lock = WeekCalendar.scheduledAt(WED, WeekStep.LOCK);
        assertThat(WeekCalendar.isDue(WED, open, WeekStep.LOCK, lock.minusSeconds(1))).isFalse();
        assertThat(WeekCalendar.isDue(WED, open, WeekStep.LOCK, lock)).isTrue();
        assertThat(WeekCalendar.isDue(WED, open, WeekStep.SOLVE, lock.plusSeconds(3600))).isFalse();
        assertThat(WeekCalendar.isDue(WED, WeekStatus.failed(WeekStep.COPY), WeekStep.COPY, lock.plusSeconds(3600))).isTrue();
    }

    @Test
    void ageOnDropDay() {
        assertThat(WeekCalendar.ageOn(LocalDate.of(2008, 11, 18), WED)).isEqualTo(18);
        assertThat(WeekCalendar.ageOn(LocalDate.of(2008, 11, 19), WED)).isEqualTo(17);
    }

    @Test
    void rejectsNonWednesday() {
        assertThatThrownBy(() -> WeekCalendar.publishAt(LocalDate.of(2026, 11, 19))).isInstanceOf(IllegalArgumentException.class);
    }
}
