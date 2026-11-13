package cn.aiditto.domain.match;

import static cn.aiditto.domain.match.MatchFixtures.A;
import static cn.aiditto.domain.match.MatchFixtures.B;
import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.match.FeedbackAnswer.Again;
import cn.aiditto.domain.match.FeedbackAnswer.Met;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Random walks through the machine: whatever happens, the invariants of §5.1 hold. */
class MatchMachinePropertiesTest {

    record Step(MatchEvent event, long minutesLater) {}

    @Provide
    Arbitrary<List<Step>> walks() {
        Arbitrary<Long> user = Arbitraries.of(A, B);
        Arbitrary<Set<String>> slots = Arbitraries.of("slot_0", "slot_1", "slot_2").set().ofMinSize(0).ofMaxSize(3);
        Arbitrary<FeedbackAnswer> fb = Combinators.combine(Arbitraries.of(Met.values()), Arbitraries.of(Again.values()), Arbitraries.of(true, true, false))
                .as((m, a, safe) -> new FeedbackAnswer(m, null, m == Met.MET ? a : null, safe));
        Arbitrary<MatchEvent> event = Arbitraries.oneOf(
                Arbitraries.just(new MatchEvent.Publish(MatchFixtures.RESPOND_DUE)),
                user.map(MatchEvent.Decline::new),
                Combinators.combine(user, slots).as(MatchEvent.SubmitSlots::new),
                Combinators.combine(user, slots).as(MatchEvent.SubmitSlots::new),
                user.map(MatchEvent.CheckIn::new),
                user.map(MatchEvent.Cancel::new),
                Combinators.combine(user, fb).as(MatchEvent.SubmitFeedback::new),
                Combinators.combine(user, Arbitraries.of(ReportCategory.values())).as(MatchEvent.Report::new),
                Arbitraries.just(new MatchEvent.RespondDeadline()),
                Arbitraries.just(new MatchEvent.ReselectDeadline()),
                Arbitraries.just(new MatchEvent.DateEnded()),
                Arbitraries.just(new MatchEvent.FeedbackDeadline()));
        Arbitrary<Long> gap = Arbitraries.longs().between(0, 60 * 30);
        return Combinators.combine(event, gap).as(Step::new).list().ofMinSize(1).ofMaxSize(40);
    }

    @Property(tries = 2000)
    void invariantsHoldOnAnyWalk(@ForAll("walks") List<Step> walk) {
        MatchSnapshot m = MatchFixtures.draft();
        Instant now = MatchFixtures.PUBLISH_AT;
        for (Step s : walk) {
            now = now.plus(Duration.ofMinutes(s.minutesLater()));
            Transition t;
            try {
                t = MatchMachine.apply(m, s.event(), now);
            } catch (MatchRuleException rejected) {
                continue;
            }
            MatchSnapshot next = t.after();
            if (!t.changed()) {
                assertThat(next).isEqualTo(m);
                assertThat(t.effects()).isEmpty();
                continue;
            }
            if (m.state().isTerminal()) {
                assertThat(next.state()).as("terminal states never change").isEqualTo(m.state());
            } else {
                assertThat(MatchMachine.successors(m.state())).contains(next.state());
            }
            for (long u : List.of(A, B)) {
                Response before = m.side(u).response();
                Response after = next.side(u).response();
                if (before != Response.PENDING) assertThat(after).as("response changes once").isEqualTo(before);
                if (m.side(u).feedback() != null) assertThat(next.side(u).feedback()).isEqualTo(m.side(u).feedback());
            }
            if (next.state() == MatchState.CONFIRMED && m.state() != MatchState.CONFIRMED) {
                assertThat(next.low().slotIds()).contains(next.confirmedSlotId());
                assertThat(next.high().slotIds()).contains(next.confirmedSlotId());
                assertThat(Duration.between(now, next.slotStart())).isGreaterThanOrEqualTo(Duration.ofHours(6));
            }
            if (next.state().isTerminal()) assertThat(next.closedAt()).isNotNull();
            if (t.effects().stream().anyMatch(e -> e instanceof Effect.UnlockContacts)) {
                assertThat(next.outcome()).isEqualTo(Outcome.MUTUAL);
            }
            for (long viewer : List.of(A, B)) {
                StageProjector.project(new StageProjector.Input(viewer, next, true, ParticipationStatus.ACTIVE, now));
            }
            m = next;
        }
    }
}
