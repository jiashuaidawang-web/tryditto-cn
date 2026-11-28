package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.strategy.ExplicitOutcome.Mutual;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration.Method;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration.Target;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ObservationCloseoutTest {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void oneAcceptDoesNotBecomeAMutualOutcome() {
        Built built = oneAccept();
        MatchingObservation row = built.observation();
        ExplicitOutcome outcome = ExplicitOutcome.of(row);
        assertThat(row.responseA()).isEqualTo(Response.ACCEPTED);
        assertThat(row.responseB()).isEqualTo(Response.UNKNOWN);
        assertThat(outcome.y1()).isEqualTo(Mutual.MISSING);
        assertThat(outcome.y2()).isNotEqualTo(ExplicitOutcome.Meeting.BOTH_ATTENDED);
        assertThat(outcome.y3()).isNotEqualTo(ExplicitOutcome.Followup.BOTH_EXPRESSED);
        assertThat(row.features().toString()).doesNotContain("ACCEPTED");
    }

    @Test
    void missingConsentAndRetractedOrUnknownSourcesAreRefused() {
        assertThatThrownBy(() -> ObservationBuilder.fromLedger(context(false), ledger(false), scores(), window(),
                "obs", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), List.of("missing")))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_FEEDBACK_CONSENT");
        Built built = oneAccept();
        List<String> wrong = new ArrayList<>(built.sources());
        wrong.set(0, "00000000-0000-0000-0000-000000000000");
        assertThatThrownBy(() -> ObservationBuilder.fromLedger(built.context(), built.ledger(), scores(), window(),
                "obs-2", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), wrong))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("SOURCE_MISMATCH");
        built.ledger().append(new FeedbackCommand(FeedbackEventType.FEEDBACK_RETRACTED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "undo", T0, built.sources().get(1), null, null), T0.plusSeconds(3));
        List<String> after = built.ledger().events().stream().map(event -> event.eventId()).toList();
        assertThatThrownBy(() -> ObservationBuilder.fromLedger(built.context(), built.ledger(), scores(), window(),
                "obs-3", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), after))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("RETRACTED_SOURCE");
    }

    @Test
    void newLabelContractKeepsTheFrozenRowsAndCountsDeclineApartFromUnknown() {
        ExplicitOutcome.Census census = ExplicitOutcome.census(StrategyDataset.observations());
        assertThat(census.published()).isEqualTo(128);
        assertThat(census.oldBinaryNegative()).isEqualTo(24);
        assertThat(census.newNotMutual()).isEqualTo(44);
        assertThat(census.eitherDecline()).isEqualTo(44);
        assertThat(census.bothExplicit()).isEqualTo(96);
        assertThat(census.expired()).isEqualTo(8);
        assertThat(census.immature()).isEqualTo(8);
        assertThat(census.noResponse()).isEqualTo(8);
        assertThat(census.missing()).isEqualTo(8);
        assertThat(census.unauthorized()).isEqualTo(8);
        assertThat(ExplicitOutcome.VERSION).isEqualTo("f03-labels-2");
    }

    @Test
    void isotonicIgnoresInputOrderAndStaysMonotone() {
        List<MatchingObservation> forward = List.of(sample("a", 1, 40, Response.DECLINED), sample("b", 2, 40, Response.ACCEPTED));
        List<MatchingObservation> reverse = List.of(forward.get(1), forward.get(0));
        double[] left = ScoreCalibration.probabilities(Method.ISOTONIC, Target.SCORE_AB_ACCEPT_A, forward, forward);
        double[] right = ScoreCalibration.probabilities(Method.ISOTONIC, Target.SCORE_AB_ACCEPT_A, reverse, forward);
        assertThat(left).containsExactly(right);
        assertThat(left[0]).isCloseTo(0.5, within(1e-9));
        List<MatchingObservation> ladder = List.of(
                sample("c", 3, 0, Response.DECLINED),
                sample("d", 4, 20, Response.DECLINED),
                sample("e", 5, 40, Response.ACCEPTED),
                sample("f", 6, 100, Response.ACCEPTED));
        double[] monotone = ScoreCalibration.probabilities(Method.ISOTONIC, Target.SCORE_AB_ACCEPT_A, ladder, ladder);
        for (int i = 1; i < monotone.length; i++) assertThat(monotone[i]).isGreaterThanOrEqualTo(monotone[i - 1]);
        double[] logistic = ScoreCalibration.probabilities(Method.LOGISTIC, Target.SCORE_AB_ACCEPT_A, ladder, ladder);
        for (double value : logistic) assertThat(value).isFinite();
        List<MatchingObservation> gap = List.of(sample("g", 7, 0, Response.DECLINED), sample("h", 8, 100, Response.ACCEPTED));
        List<MatchingObservation> emptyBin = List.of(sample("i", 9, 50, Response.ACCEPTED), sample("j", 10, 50, Response.DECLINED));
        double[] binned = ScoreCalibration.probabilities(Method.BINNING, Target.SCORE_AB_ACCEPT_A, gap, emptyBin);
        assertThat(binned).containsExactly(0.5, 0.5);
        assertThat(ScoreCalibration.run(ladder).stream().filter(result -> result.method() == Method.CONSTANT_RATE).findFirst().orElseThrow().brier())
                .isNotNull();
        assertThat(ScoreCalibration.run(List.of(sample("k", 11, 10, Response.ACCEPTED))).get(0).status()).isEqualTo("INSUFFICIENT");
    }

    @Test
    void aDocumentRoundTripKeepsLabelsAndADifferentWindowChangesTheDigest() {
        MatchingObservation row = StrategyDataset.observations().get(0);
        ObservationDocument document = new ObservationDocument(ObservationDocument.VERSION, ObservationDocument.Entry.FIXTURE,
                StrategyDataset.VERSION, row);
        ObservationDocument parsed = ObservationDocument.parse(document.serialize());
        assertThat(parsed.observation().canonical()).isEqualTo(row.canonical());
        assertThat(ExplicitOutcome.of(parsed.observation())).isEqualTo(ExplicitOutcome.of(row));
        assertThat(GroupFunnel.matchStages(List.of(parsed.observation())).get(0).matches()).isEqualTo(1);
        String shifted = document.serialize().replace("responseDeadline=" + row.responseDeadline(),
                "responseDeadline=" + row.responseDeadline().plusSeconds(60));
        assertThat(ObservationDocument.parse(shifted).digest()).isNotEqualTo(document.digest());
    }

    @Test
    void publicationTimeSplitsRowsThatShareAPeriod() {
        Instant cutoff = Instant.parse("2026-11-10T00:00:00Z");
        MatchingObservation early = sampleAt("early", 21, Instant.parse("2026-11-01T00:00:00Z"), LocalDate.of(2026, 11, 1));
        MatchingObservation lateSamePeriod = sampleAt("late", 22, Instant.parse("2026-11-20T00:00:00Z"), LocalDate.of(2026, 11, 1));
        MatchingObservation otherPeriod = sampleAt("other", 23, Instant.parse("2026-11-02T00:00:00Z"), LocalDate.of(2026, 12, 1));
        assertThat(TimeSplit.development(early, cutoff)).isTrue();
        assertThat(TimeSplit.development(lateSamePeriod, cutoff)).isFalse();
        assertThat(early.matchingPeriod()).isEqualTo(lateSamePeriod.matchingPeriod());
        assertThat(TimeSplit.development(otherPeriod, cutoff)).isTrue();
        assertThat(otherPeriod.matchingPeriod()).isNotEqualTo(early.matchingPeriod());
    }

    private static Built oneAccept() {
        MatchFeedbackContext context = context(true);
        FeedbackLedger ledger = new FeedbackLedger(context);
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub", T0, null, null, null), T0);
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "acc", T0, null, null, null), T0.plusSeconds(1));
        List<String> sources = ledger.events().stream().map(event -> event.eventId()).toList();
        MatchingObservation observation = ObservationBuilder.fromLedger(context, ledger, scores(), window(), "obs-one",
                T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), sources);
        return new Built(context, ledger, sources, observation);
    }

    private static MatchFeedbackContext context(boolean consentB) {
        return MatchFeedbackContext.experiment(71011, "run-f03", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "m02-b5", "global-objective-g2-1", "E+G2", true, consentB);
    }

    private static FeedbackLedger ledger(boolean consentB) {
        return new FeedbackLedger(context(consentB));
    }

    private static MatchScoreSnapshot scores() {
        return new MatchScoreSnapshot(80, 20, 0.8, 0.4, 20, "B5");
    }

    private static ObservationWindow window() {
        return new ObservationWindow(T0.plusSeconds(7 * 86400L), T0.plusSeconds(14 * 86400L), T0.plusSeconds(21 * 86400L));
    }

    private static MatchingObservation sample(String id, long match, int score, Response response) {
        return sampleAt(id, match, T0, LocalDate.of(2026, 11, 1), score, response);
    }

    private static MatchingObservation sampleAt(String id, long match, Instant published, LocalDate period) {
        return sampleAt(id, match, published, period, 40, Response.ACCEPTED);
    }

    private static MatchingObservation sampleAt(String id, long match, Instant published, LocalDate period, int score, Response response) {
        return new MatchingObservation(id, match, "boundary", period, 91001, 91040 + match, 2, 3, 1, 1,
                "E+G2", "m02-b5", "global-objective-g2-1", "B5", score, score, 1, 1, score, published,
                published.plusSeconds(86400), published.plusSeconds(172800), published.plusSeconds(259200),
                response, response, true, false, Attendance.NOT_APPLICABLE, Continue.NOT_APPLICABLE,
                Continue.NOT_APPLICABLE, Access.OPEN, false, List.of("e-" + match), published.plusSeconds(864000));
    }

    private record Built(MatchFeedbackContext context, FeedbackLedger ledger, List<String> sources, MatchingObservation observation) {}
}
