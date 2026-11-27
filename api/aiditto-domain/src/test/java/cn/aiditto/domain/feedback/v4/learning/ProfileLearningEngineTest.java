package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal.Status;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileLearningEngineTest {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");
    private final ProfileLearningEngine engine = ProfileLearningEngine.v1();

    @Test
    void aDeclineWithoutAReasonIsNotAPreference() {
        FeedbackLedger ledger = published();
        ledger.append(person(FeedbackEventType.MATCH_DECLINED, 81001, "dec"), T0);
        List<LearningSignal> signals = engine.signals(ledger, 81001);
        assertThat(signals).anyMatch(signal -> signal.category() == LearningSignal.Category.NO_LEARNING_SIGNAL);
        assertThat(signals).noneMatch(LearningSignal::canPropose);
        assertThat(engine.signals(ledger, 81002)).allMatch(signal ->
                signal.category() == LearningSignal.Category.COUNTERPART_OPINION && !signal.canPropose());
    }

    @Test
    void timeoutAndAReasonCodeStayOutOfTheProfile() {
        FeedbackLedger expired = published();
        expired.append(system(FeedbackEventType.MATCH_EXPIRED, "exp"), T0);
        assertThat(engine.signals(expired, 81001))
                .anyMatch(signal -> signal.category() == LearningSignal.Category.NO_LEARNING_SIGNAL);
        FeedbackLedger reasoned = published();
        var decline = reasoned.append(new FeedbackCommand(FeedbackEventType.MATCH_DECLINED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "why", T0, null, "OTHER", null), T0);
        reasoned.append(new FeedbackCommand(FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "corr", T0, decline.eventId(), "COMMUNICATION_STYLE", null), T0);
        assertThat(engine.signals(reasoned, 81001)).allMatch(signal ->
                signal.category() == LearningSignal.Category.AMBIGUOUS_REASON && signal.learnableTarget() == null);
    }

    @Test
    void repeatedSelfReportsBecomeAnUnconfirmedHypothesis() {
        List<SelfDirectionReport> once = List.of(report("e1", "quieter", T0));
        assertThat(engine.hypotheses(once, 81001, 1, 1, T0.plusSeconds(1))).isEmpty();
        List<SelfDirectionReport> repeated = List.of(
                report("e1", "quieter", T0), report("e1", "quieter", T0.plusSeconds(1)),
                report("e2", "quieter", T0.plusSeconds(2)), report("e3", "quieter", T0.plusSeconds(3)));
        List<ProfileLearningProposal> proposals = engine.hypotheses(repeated, 81001, 1, 1, T0.plusSeconds(4));
        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).status()).isEqualTo(Status.PENDING_CONFIRMATION);
        assertThat(proposals.get(0).sourceEventIds()).containsExactly("e1", "e2", "e3");
        assertThat(proposals.get(0).proposedValue()).isEqualTo("quiet");
        assertThat(ProfileLearningEngine.matchingMayRead(proposals.get(0))).isFalse();
        List<SelfDirectionReport> both = new ArrayList<>(repeated);
        both.add(report("e4", "more_frequent", T0.plusSeconds(4)));
        both.add(report("e5", "more_frequent", T0.plusSeconds(5)));
        both.add(report("e6", "more_frequent", T0.plusSeconds(6)));
        List<ProfileLearningProposal> conflict = engine.hypotheses(both, 81001, 1, 1, T0.plusSeconds(7));
        assertThat(conflict).hasSize(2);
        assertThat(conflict).allMatch(proposal -> proposal.evidenceStatus().equals("CONTRADICTED"));
        assertThat(conflict).allMatch(proposal -> proposal.status() == Status.PENDING_CONFIRMATION);
    }

    @Test
    void anExplicitConfirmationEntersTheProfileAndARepeatDoesNot() {
        ProfileEvidence seed = engine.seed(81001, "self.communication", "frequent");
        ConfirmationSources sources = new ConfirmationSources();
        sources.put(ConfirmationSources.seed(81001, seed.sourceRef()));
        ExplicitPreferenceCommand command = command("quiet1", "self.communication", "frequent", "quiet", 1);
        ProfileLearningProposal proposal = engine.propose(command);
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, proposal, List.of(seed), 1, 1,
                "hash", true, false, false, sources);
        assertThat(update.proposal().status()).isEqualTo(Status.CONFIRMED);
        assertThat(update.decision().kind()).isEqualTo(ProfileVersionPolicy.Kind.INSERT);
        assertThat(update.snapshot().self().features()).anyMatch(feature ->
                feature.value() instanceof FeatureValue.EnumOne one && one.value().equals("quiet"));
        assertThat(update.evidence()).anyMatch(item -> item.status() == EvidenceStatus.RETRACTED);
        ProfileLearningEngine.ProfileUpdate again = engine.apply(command, proposal, update.evidence(), 2, 1,
                update.snapshot().inputHash(), true, false, false, sources);
        assertThat(again.decision().kind()).isEqualTo(ProfileVersionPolicy.Kind.KEEP_CURRENT);
    }

    @Test
    void staleRejectExpiredAndUnauthorizedUpdatesDoNotWrite() {
        ExplicitPreferenceCommand reject = new ExplicitPreferenceCommand("reject1", 81001, "self.communication",
                "frequent", "quiet", Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.REJECT,
                1, 1, 1, true, true, List.of(), T0);
        assertThat(engine.apply(reject, engine.propose(reject), List.of(), 1, 1, null, true, false, false,
                new ConfirmationSources()).proposal().status()).isEqualTo(Status.REJECTED);
        ExplicitPreferenceCommand stale = command("stale1", "self.communication", null, "quiet", 1);
        assertThat(engine.apply(stale, engine.propose(stale), List.of(), 2, 1, "hash", true, false, false,
                new ConfirmationSources()).proposal().status()).isEqualTo(Status.STALE_PROFILE);
        ExplicitPreferenceCommand expired = command("expire1", "partner.communication", null, "quiet", 0);
        ProfileLearningProposal old = engine.propose(expired).withStatus(Status.PENDING_CONFIRMATION, "UNCONFIRMED");
        ExplicitPreferenceCommand late = new ExplicitPreferenceCommand("expire1", 81001, "partner.communication",
                null, "quiet", Importance.KINDA, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                0, 1, 1, true, true, List.of(), T0.plus(java.time.Duration.ofDays(40)));
        assertThat(engine.apply(late, old, List.of(), 0, 1, null, true, false, false, new ConfirmationSources())
                .proposal().status()).isEqualTo(Status.EXPIRED);
        ExplicitPreferenceCommand closed = command("closed1", "self.communication", null, "quiet", 0);
        ExplicitPreferenceCommand noLearning = new ExplicitPreferenceCommand("closed1", 81001, "self.communication",
                null, "quiet", Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                0, 1, 1, true, false, List.of(), T0);
        assertThatThrownBy(() -> engine.apply(noLearning, engine.propose(closed), List.of(), 0, 1, null,
                true, false, false, new ConfirmationSources()))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_LEARNING_CONSENT");
        assertThatThrownBy(() -> engine.propose(command("hard1", "partner.age", null, "30", 0)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ILLEGAL_HARD_CONSTRAINT");
    }

    @Test
    void retractionDropsOnlyTheDerivedFeature() {
        ProfileEvidence kept = engine.seed(81001, "self.pace", "slow");
        ExplicitPreferenceCommand command = command("quiet2", "self.communication", null, "quiet", 1);
        ConfirmationSources sources = new ConfirmationSources();
        sources.put(ConfirmationSources.seed(81001, kept.sourceRef()));
        ProfileLearningEngine.ProfileUpdate written = engine.apply(command, engine.propose(command), List.of(kept), 1, 1,
                "hash", true, false, false, sources);
        ProfileLearningEngine.ProfileUpdate retracted = engine.retractDerived(81001, written.evidence(),
                "confirm.quiet2", 2, 1, sources);
        assertThat(retracted.snapshot().self().features()).anyMatch(feature -> feature.featureCode().equals("self.pace"));
        assertThat(retracted.snapshot().self().features()).noneMatch(feature -> feature.featureCode().equals("self.communication"));
        assertThat(retracted.evidence()).anyMatch(item -> item.sourceRef().equals("confirm.quiet2")
                && item.status() == EvidenceStatus.RETRACTED);
    }

    @Test
    void withheldFeedbackAndMissingConsentDoNotPropose() {
        MatchFeedbackContext context = MatchFeedbackContext.experiment(71041, "run-f02", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true, false, true);
        FeedbackLedger ledger = new FeedbackLedger(context);
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0);
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        assertThat(engine.signals(ledger, 81001)).allMatch(signal ->
                signal.category() == LearningSignal.Category.WITHHELD && EvidencePriority.rank(signal) == 0);
        FeedbackLedger revoked = published();
        revoked.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        revoked.append(person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "revoke"), T0.plusSeconds(1));
        assertThat(engine.signals(revoked, 81001)).allMatch(signal -> signal.authorization() == LearningSignal.Authorization.WITHHELD);
    }

    private static SelfDirectionReport report(String id, String direction, Instant when) {
        return new SelfDirectionReport(id, 81001, "self.communication", direction, when);
    }

    private static ExplicitPreferenceCommand command(String id, String feature, String oldValue, String value, int version) {
        Importance importance = feature.startsWith("partner.") ? Importance.KINDA : Importance.NONE;
        return new ExplicitPreferenceCommand(id, 81001, feature, oldValue, value, importance, Operator.EQ,
                ExplicitPreferenceCommand.Action.CONFIRM, version, 1, 1, true, true, List.of("evt." + id), T0);
    }

    private static FeedbackLedger published() {
        FeedbackLedger ledger = new FeedbackLedger(MatchFeedbackContext.experiment(71041, "run-f02", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true));
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0.minusSeconds(10));
        return ledger;
    }

    private static FeedbackCommand system(FeedbackEventType type, String key) {
        return new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, key, T0, null, null, null);
    }

    private static FeedbackCommand person(FeedbackEventType type, long actor, String key) {
        return new FeedbackCommand(type, actor, FeedbackFacts.SourceType.PARTICIPANT, key, T0, null, null, null);
    }
}
