package cn.aiditto.match;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Deadline ticker and the weekly batch. Tests turn this off and call the runners directly. */
@Component
@ConditionalOnProperty(name = "aiditto.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MatchScheduler {
    private final WeekRunner weeks;
    private final MatchFlow matches;
    private final Clock clock;

    public MatchScheduler(WeekRunner weeks, MatchFlow matches, Clock clock) {
        this.weeks = weeks;
        this.matches = matches;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aiditto.scheduler.delay-ms:30000}", initialDelayString = "${aiditto.scheduler.delay-ms:30000}")
    public void tick() {
        weeks.tick(clock.instant());
        matches.tickDeadlines(clock.instant());
    }
}
