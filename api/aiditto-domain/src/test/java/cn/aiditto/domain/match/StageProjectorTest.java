package cn.aiditto.domain.match;

import static cn.aiditto.domain.match.MatchFixtures.A;
import static cn.aiditto.domain.match.MatchFixtures.B;
import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.match.FeedbackAnswer.Again;
import cn.aiditto.domain.match.MatchEvent.CheckIn;
import cn.aiditto.domain.match.MatchEvent.Report;
import cn.aiditto.domain.match.MatchEvent.SubmitFeedback;
import cn.aiditto.domain.slot.Slot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageProjectorTest {

    private static Stage project(MatchSnapshot m, long viewer, Instant now) {
        return StageProjector.project(new StageProjector.Input(viewer, m, true, ParticipationStatus.ACTIVE, now));
    }

    @Test
    void noMatch() {
        Instant now = MatchFixtures.T_REVEALED;
        assertThat(StageProjector.project(new StageProjector.Input(A, null, false, ParticipationStatus.ACTIVE, now))).isEqualTo(Stage.WAITING);
        assertThat(StageProjector.project(new StageProjector.Input(A, null, true, ParticipationStatus.ACTIVE, now))).isEqualTo(Stage.EMPTY_WEEK);
        assertThat(StageProjector.project(new StageProjector.Input(A, MatchFixtures.draft(), false, ParticipationStatus.ACTIVE, now))).isEqualTo(Stage.WAITING);
    }

    @Test
    void pausedAndSuspendedOnlyShowWithoutAMatch() {
        Instant now = MatchFixtures.T_REVEALED;
        for (boolean published : List.of(true, false)) {
            assertThat(StageProjector.project(new StageProjector.Input(A, null, published, ParticipationStatus.PAUSED, now))).isEqualTo(Stage.PAUSED);
            assertThat(StageProjector.project(new StageProjector.Input(A, null, published, ParticipationStatus.SUSPENDED, now))).isEqualTo(Stage.SUSPENDED);
        }
        assertThat(StageProjector.project(new StageProjector.Input(A, MatchFixtures.published(), true, ParticipationStatus.SUSPENDED, now)))
                .isEqualTo(Stage.REVEALED);
        assertThat(Stage.PAUSED.legacyWire()).isEqualTo("waiting");
        assertThat(Stage.SUSPENDED.wire()).isEqualTo("suspended");
    }

    @Test
    void specRowsInOrder() {
        Instant t = MatchFixtures.T_REVEALED;
        assertThat(project(MatchFixtures.halted(), A, t)).isEqualTo(Stage.REPORTED);
        assertThat(project(MatchFixtures.halted(), B, t)).isEqualTo(Stage.CLOSED);
        assertThat(project(MatchFixtures.published(), A, t)).isEqualTo(Stage.REVEALED);
        assertThat(project(MatchFixtures.declined(), A, t)).isEqualTo(Stage.DECLINED);
        assertThat(project(MatchFixtures.declined(), B, t)).isEqualTo(Stage.CLOSED);
        assertThat(project(MatchFixtures.aAccepted("slot_0"), A, t)).isEqualTo(Stage.AWAITING_PARTNER);
        assertThat(project(MatchFixtures.aAccepted("slot_0"), B, t)).isEqualTo(Stage.REVEALED);
        assertThat(project(MatchFixtures.expired(), A, t)).isEqualTo(Stage.CLOSED);
        assertThat(project(MatchFixtures.unscheduled(), A, t)).isEqualTo(Stage.CLOSED);
        assertThat(project(MatchFixtures.cancelled(), B, t)).isEqualTo(Stage.CLOSED);
        assertThat(project(MatchFixtures.noOverlap(), A, t)).isEqualTo(Stage.NO_OVERLAP);
        assertThat(Stage.NO_OVERLAP.legacyWire()).isEqualTo("closed");

        MatchSnapshot c = MatchFixtures.confirmed();
        Instant opens = c.slotStart().minus(Duration.ofHours(2));
        assertThat(project(c, A, opens.minusMillis(1))).isEqualTo(Stage.CONFIRMED);
        assertThat(project(c, A, opens)).isEqualTo(Stage.DATE_DAY);
        MatchSnapshot checked = MatchMachine.apply(c, new CheckIn(A), opens).after();
        assertThat(project(checked, A, opens)).isEqualTo(Stage.CHECKED_IN);
        assertThat(project(checked, B, opens)).isEqualTo(Stage.DATE_DAY);

        MatchSnapshot f = MatchFixtures.feedbackOpen();
        assertThat(project(f, A, f.slotEnd())).isEqualTo(Stage.FEEDBACK);
        MatchSnapshot fa = MatchMachine.apply(f, new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), f.slotEnd()).after();
        assertThat(project(fa, A, f.slotEnd())).isEqualTo(Stage.AWAITING_FEEDBACK);
        assertThat(project(fa, B, f.slotEnd())).isEqualTo(Stage.FEEDBACK);

        MatchSnapshot mutual = MatchFixtures.closedMutual();
        assertThat(project(mutual, A, mutual.closedAt())).isEqualTo(Stage.MUTUAL);
        assertThat(StageProjector.contactVisible(Stage.MUTUAL)).isTrue();
        MatchSnapshot reportedAfter = MatchMachine.apply(mutual, new Report(B, ReportCategory.FAKE), mutual.closedAt()).after();
        assertThat(project(reportedAfter, B, mutual.closedAt())).isEqualTo(Stage.REPORTED);
        assertThat(project(reportedAfter, A, mutual.closedAt())).as("contact hidden once reported").isEqualTo(Stage.CLOSED);
    }

    /**
     * 技术方案 §5.2: every match state × my response × partner response × time bucket lands on exactly one
     * stage (the projector is a total function), and every reachable combination lands on the right one.
     */
    @Test
    void exhaustiveStateResponseTimeGrid() {
        Slot s = MatchFixtures.slot(1);
        Instant start = s.startsAt();
        List<Instant> buckets = List.of(start.minus(Duration.ofDays(1)), start.minus(Duration.ofHours(2)), start, s.endsAt(), s.endsAt().plus(Duration.ofDays(1)));
        Map<Stage, Integer> seen = new EnumMap<>(Stage.class);
        List<String> wrong = new ArrayList<>();
        int combos = 0;
        for (MatchState st : MatchState.values()) {
            for (Response mine : Response.values()) {
                for (Response theirs : Response.values()) {
                    for (boolean checkedIn : List.of(false, true)) {
                        for (boolean myFeedback : List.of(false, true)) {
                            for (Instant now : buckets) {
                                SideSnapshot me = new SideSnapshot(A, mine, mine == Response.ACCEPT ? Set.of("slot_1") : Set.of(), false,
                                        checkedIn ? start.minus(Duration.ofHours(1)) : null, myFeedback ? FeedbackAnswer.met(Again.YES) : null);
                                SideSnapshot them = new SideSnapshot(B, theirs, theirs == Response.ACCEPT ? Set.of("slot_1") : Set.of(), false, null, null);
                                MatchSnapshot m = new MatchSnapshot(st, me, them, MatchFixtures.SLOTS, MatchFixtures.PUBLISH_AT, MatchFixtures.RESPOND_DUE,
                                        null, "slot_1", start, s.endsAt(), s.endsAt().plus(Duration.ofHours(72)),
                                        st == MatchState.CLOSED ? Outcome.MUTUAL : null, st == MatchState.HALTED ? A : null, null, null);
                                Stage stage = project(m, A, now);
                                combos++;
                                seen.merge(stage, 1, Integer::sum);
                                Stage expected = expected(st, mine, theirs, checkedIn, myFeedback, now, start);
                                if (expected != null && expected != stage) wrong.add(st + "/" + mine + "/" + theirs + "/" + checkedIn + "/" + myFeedback + "/" + now + " → " + stage + " expected " + expected);
                            }
                        }
                    }
                }
            }
        }
        assertThat(wrong).isEmpty();
        assertThat(combos).isEqualTo(MatchState.values().length * 9 * 4 * buckets.size());
        assertThat(seen.keySet()).containsAll(EnumSet.complementOf(EnumSet.of(Stage.EMPTY_WEEK, Stage.PAUSED, Stage.SUSPENDED)));
    }

    /** Reference table from 技术方案 §5.2; {@code null} for combinations the machine cannot produce. */
    private static Stage expected(MatchState st, Response mine, Response theirs, boolean checkedIn, boolean myFeedback, Instant now, Instant start) {
        switch (st) {
            case DRAFT: return Stage.WAITING;
            case HALTED: return Stage.REPORTED;
            case PUBLISHED:
                if (mine == Response.PENDING) return Stage.REVEALED;
                if (mine == Response.DECLINE) return Stage.DECLINED;
                return theirs == Response.PENDING ? Stage.AWAITING_PARTNER : null;
            case DECLINED: return mine == Response.DECLINE ? Stage.DECLINED : Stage.CLOSED;
            case EXPIRED: case UNSCHEDULED: case CANCELLED: return Stage.CLOSED;
            case NO_OVERLAP: return Stage.NO_OVERLAP;
            case CONFIRMED:
                if (now.isBefore(start.minus(Duration.ofHours(2)))) return Stage.CONFIRMED;
                return checkedIn ? Stage.CHECKED_IN : Stage.DATE_DAY;
            case FEEDBACK_OPEN: return myFeedback ? Stage.AWAITING_FEEDBACK : Stage.FEEDBACK;
            case CLOSED: return Stage.MUTUAL;
            default: return null;
        }
    }
}
