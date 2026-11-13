package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.matching.PoolGate.Applicant;
import cn.aiditto.domain.matching.PoolGate.ExcludeReason;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PoolGateTest {
    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);
    static final Instant NOW = Instant.parse("2026-11-17T15:59:00Z");
    static final LocalDate ADULT = LocalDate.of(2004, 5, 1);
    static final Applicant OK = new Applicant(ADULT, true, true, false, 2, false, false, null, false);

    @Test
    void eligible() {
        assertThat(PoolGate.check(OK, WEEK, NOW)).isEmpty();
    }

    @Test
    void eachReason() {
        assertThat(PoolGate.check(new Applicant(WEEK.minusYears(18).plusDays(1), true, true, false, 2, false, false, null, false), WEEK, NOW))
                .contains(ExcludeReason.UNDER_18);
        assertThat(PoolGate.check(new Applicant(null, true, true, false, 2, false, false, null, false), WEEK, NOW)).contains(ExcludeReason.UNDER_18);
        assertThat(PoolGate.check(new Applicant(ADULT, false, true, false, 2, false, false, null, false), WEEK, NOW)).contains(ExcludeReason.NOT_ACTIVE);
        assertThat(PoolGate.check(new Applicant(ADULT, true, false, false, 2, false, false, null, false), WEEK, NOW)).contains(ExcludeReason.INCOMPLETE);
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 1, false, false, null, false), WEEK, NOW)).contains(ExcludeReason.PHOTOS);
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 3, true, false, null, false), WEEK, NOW)).contains(ExcludeReason.PHOTOS);
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 2, false, true, null, false), WEEK, NOW)).contains(ExcludeReason.PAUSED);
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 2, false, false, NOW.plusSeconds(1), false), WEEK, NOW)).contains(ExcludeReason.SUSPENDED);
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 2, false, false, null, true), WEEK, NOW)).contains(ExcludeReason.OPEN_MATCH);
    }

    @Test
    void suspensionThatEndedDoesNotExclude() {
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, false, 2, false, false, NOW, false), WEEK, NOW)).isEmpty();
    }

    @Test
    void syntheticUsersNeedNoPhotos() {
        assertThat(PoolGate.check(new Applicant(ADULT, true, true, true, 0, false, false, null, false), WEEK, NOW)).isEmpty();
    }

    @Test
    void firstReasonWins() {
        assertThat(PoolGate.check(new Applicant(WEEK.minusYears(17), false, false, false, 0, true, true, NOW.plusSeconds(60), true), WEEK, NOW))
                .contains(ExcludeReason.UNDER_18);
        assertThat(ExcludeReason.OPEN_MATCH.dbValue()).isEqualTo("open_match");
    }
}
