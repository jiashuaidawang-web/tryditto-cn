package cn.aiditto.domain.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PenaltiesTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void ladder() {
        assertThat(Penalties.forNoShows(0, 0, NOW)).isEqualTo(Penalties.Penalty.NONE);
        assertThat(Penalties.forNoShows(1, 0, NOW).suspendedUntil()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(Penalties.forNoShows(2, 0, NOW).suspendedUntil()).isEqualTo(NOW.plus(Duration.ofDays(28)));
        Penalties.Penalty third = Penalties.forNoShows(3, 0, NOW);
        assertThat(third.stopped()).isTrue();
        assertThat(third.suspendedUntil()).isNull();
    }

    @Test
    void twoLateCancelsCountAsOneNoShow() {
        assertThat(Penalties.effectiveNoShows(0, 1)).isZero();
        assertThat(Penalties.effectiveNoShows(0, 2)).isEqualTo(1);
        assertThat(Penalties.forNoShows(1, 2, NOW).effectiveNoShows()).isEqualTo(2);
    }

    @Test
    void autoPauseAfterTwoSilentWeeks() {
        assertThat(Penalties.shouldAutoPause(1)).isFalse();
        assertThat(Penalties.shouldAutoPause(2)).isTrue();
    }
}
