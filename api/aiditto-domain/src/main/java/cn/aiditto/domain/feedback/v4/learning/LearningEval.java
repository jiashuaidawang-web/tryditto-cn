package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal.Status;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores the frozen synthetic label table. Agreement with these labels is a contract check.
 * It is not a measurement of real users.
 */
public final class LearningEval {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");
    private static final String[] KINDS = {
            "explicit-self", "explicit-partner", "decline-no-reason", "timeout", "no-meeting",
            "counterpart", "one-observation", "repeated", "contradiction", "reject",
            "revoke", "delete", "stale", "duplicate", "no-consent", "hard-constraint",
            "cross-user", "withheld"
    };

    private LearningEval() {}

    public record Case(String id, String split, String kind) {}

    public record Report(int total, int dev, int holdout, int signalHits, int signalLabeled,
                         int correctProposals, int unsupportedProposals, int wrongDirection,
                         int counterpartWrites, int unconfirmedApplied, int explicitSuccess, int explicitAttempts,
                         int staleOverwrites, int duplicateApplications, int revokeLeaks, int hardConstraintEdits,
                         int traceableChanges, int appliedChanges, int personified, int safetyViolations) {}

    public static List<Case> frozen() {
        List<Case> cases = new ArrayList<>();
        int dev = 0;
        int holdout = 0;
        for (int n = 0; cases.size() < 72; n++) {
            String kind = KINDS[n % KINDS.length];
            boolean hold = n % 3 == 2;
            if (hold && holdout >= 24) hold = false;
            if (!hold && dev >= 48) hold = true;
            String split = hold ? "holdout" : "dev";
            if (hold) holdout++;
            else dev++;
            cases.add(new Case((hold ? "H" : "D") + String.format("%02d", cases.size() + 1), split, kind));
        }
        return List.copyOf(cases);
    }

    public static Report evaluate(List<Case> cases) {
        ProfileLearningEngine engine = ProfileLearningEngine.v1();
        int signalHits = 0;
        int signalLabeled = 0;
        int correct = 0;
        int unsupported = 0;
        int wrongDirection = 0;
        int counterpartWrites = 0;
        int unconfirmedApplied = 0;
        int explicitSuccess = 0;
        int explicitAttempts = 0;
        int staleOverwrites = 0;
        int duplicates = 0;
        int revokeLeaks = 0;
        int hardEdits = 0;
        int traceable = 0;
        int applied = 0;
        int personified = 0;
        int dev = 0;
        int holdout = 0;
        for (Case item : cases) {
            if (item.split().equals("dev")) dev++;
            else holdout++;
            Outcome outcome = run(engine, item);
            if (outcome.expectedSignal != null) {
                signalLabeled++;
                if (outcome.expectedSignal.equals(outcome.actualSignal)) signalHits++;
            }
            if (outcome.correctProposal) correct++;
            else unsupported++;
            wrongDirection += outcome.wrongDirection;
            counterpartWrites += outcome.counterpartWrite;
            unconfirmedApplied += outcome.unconfirmedApplied;
            if (outcome.explicitAttempt) {
                explicitAttempts++;
                if (outcome.explicitSuccess) explicitSuccess++;
            }
            staleOverwrites += outcome.staleOverwrite;
            duplicates += outcome.duplicate;
            revokeLeaks += outcome.revokeLeak;
            hardEdits += outcome.hardEdit;
            if (outcome.applied) {
                applied++;
                if (outcome.traceable) traceable++;
            }
            personified += outcome.personified;
        }
        int safety = counterpartWrites + unconfirmedApplied + staleOverwrites + duplicates + revokeLeaks + hardEdits + personified;
        return new Report(cases.size(), dev, holdout, signalHits, signalLabeled, correct, unsupported, wrongDirection,
                counterpartWrites, unconfirmedApplied, explicitSuccess, explicitAttempts, staleOverwrites, duplicates,
                revokeLeaks, hardEdits, traceable, applied, personified, safety);
    }

    public static String json() {
        StringBuilder out = new StringBuilder("[\n");
        List<Case> cases = frozen();
        for (int i = 0; i < cases.size(); i++) {
            Case item = cases.get(i);
            out.append("  {\"id\":\"").append(item.id()).append("\",\"split\":\"").append(item.split())
                    .append("\",\"kind\":\"").append(item.kind()).append("\"}");
            out.append(i + 1 == cases.size() ? "\n" : ",\n");
        }
        return out.append("]\n").toString();
    }

    public static void main(String[] args) {
        System.out.print(json());
    }

    private static Outcome run(ProfileLearningEngine engine, Case item) {
        Outcome outcome = new Outcome();
        switch (item.kind()) {
            case "explicit-self" -> explicit(engine, outcome, "self.communication", "quiet");
            case "explicit-partner" -> explicit(engine, outcome, "partner.communication", "quiet");
            case "decline-no-reason" -> signal(engine, outcome, FeedbackEventType.MATCH_DECLINED, null, "NO_LEARNING_SIGNAL");
            case "timeout" -> signal(engine, outcome, FeedbackEventType.MATCH_EXPIRED, null, "NO_LEARNING_SIGNAL");
            case "no-meeting" -> signal(engine, outcome, FeedbackEventType.DATE_REPORTED_NOT_ATTENDED, null, "RELATIONSHIP_OUTCOME");
            case "counterpart" -> counterpart(engine, outcome);
            case "one-observation" -> observation(engine, outcome, 1);
            case "repeated" -> observation(engine, outcome, 3);
            case "contradiction" -> contradiction(engine, outcome);
            case "reject" -> reject(engine, outcome);
            case "revoke" -> revoke(engine, outcome);
            case "delete" -> erased(engine, outcome);
            case "stale" -> stale(engine, outcome);
            case "duplicate" -> duplicate(engine, outcome);
            case "no-consent" -> noConsent(engine, outcome);
            case "hard-constraint" -> hard(engine, outcome);
            case "cross-user" -> crossUser(engine, outcome);
            case "withheld" -> withheld(engine, outcome);
            default -> throw new IllegalStateException(item.kind());
        }
        return outcome;
    }

    private static void explicit(ProfileLearningEngine engine, Outcome outcome, String feature, String value) {
        outcome.expectedSignal = feature.startsWith("partner.") ? "EXPLICIT_PARTNER_PREFERENCE" : "EXPLICIT_SELF_CORRECTION";
        outcome.explicitAttempt = true;
        ExplicitPreferenceCommand command = command("c" + feature.charAt(0) + value.charAt(0), feature, value, true);
        outcome.actualSignal = engine.explicitSignal(command).category().name();
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, engine.propose(command), List.of(), 0, 1,
                null, true, false, false, new ConfirmationSources());
        outcome.explicitSuccess = update.proposal().status() == Status.CONFIRMED
                && update.decision().kind() == ProfileVersionPolicy.Kind.INSERT;
        outcome.applied = outcome.explicitSuccess;
        outcome.traceable = update.proposal().sourceEventIds().size() == 1;
        outcome.correctProposal = outcome.explicitSuccess && update.proposal().proposedValue().equals(value);
        outcome.wrongDirection = outcome.correctProposal ? 0 : 1;
    }

    private static void signal(ProfileLearningEngine engine, Outcome outcome, FeedbackEventType type, String reason, String expected) {
        outcome.expectedSignal = expected;
        FeedbackLedger ledger = ledger(true, true);
        if (type == FeedbackEventType.MATCH_EXPIRED) {
            ledger.append(new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, "sys", T0, null, null, null), T0);
        } else if (type == FeedbackEventType.DATE_REPORTED_NOT_ATTENDED) {
            ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001, FeedbackFacts.SourceType.PARTICIPANT, "a", T0, null, null, null), T0);
            ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81002, FeedbackFacts.SourceType.PARTICIPANT, "b", T0, null, null, null), T0);
            ledger.append(new FeedbackCommand(FeedbackEventType.DATE_SCHEDULED, 81001, FeedbackFacts.SourceType.PARTICIPANT, "s", T0, null, null, null), T0);
            ledger.append(new FeedbackCommand(type, 81001, FeedbackFacts.SourceType.PARTICIPANT, "r", T0, null, reason, null), T0);
        } else {
            ledger.append(new FeedbackCommand(type, 81001, FeedbackFacts.SourceType.PARTICIPANT, "d", T0, null, reason, null), T0);
        }
        outcome.actualSignal = engine.signals(ledger, 81001).stream()
                .filter(signal -> signal.category().name().equals(expected))
                .findFirst().map(signal -> signal.category().name()).orElse("MISSING");
        outcome.correctProposal = "MISSING".equals(outcome.actualSignal) == false && engine.signals(ledger, 81001).stream().noneMatch(LearningSignal::canPropose);
        outcome.personified = outcome.correctProposal ? 0 : 1;
    }

    private static void counterpart(ProfileLearningEngine engine, Outcome outcome) {
        outcome.expectedSignal = "COUNTERPART_OPINION";
        FeedbackLedger ledger = ledger(true, true);
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_DECLINED, 81002, FeedbackFacts.SourceType.PARTICIPANT,
                "other", T0, null, "OTHER", null), T0);
        outcome.actualSignal = engine.signals(ledger, 81001).get(0).category().name();
        outcome.correctProposal = !engine.signals(ledger, 81001).get(0).canPropose();
        outcome.counterpartWrite = outcome.correctProposal ? 0 : 1;
    }

    private static void observation(ProfileLearningEngine engine, Outcome outcome, int count) {
        outcome.expectedSignal = count >= 3 ? "PREFERENCE_HYPOTHESIS" : "BEHAVIORAL_OBSERVATION";
        List<SelfDirectionReport> reports = new ArrayList<>();
        for (int i = 0; i < count; i++) reports.add(new SelfDirectionReport("r" + i, 81001, "self.communication", "quieter", T0.plusSeconds(i)));
        List<LearningSignal> signals = FeedbackSignalClassifier.observe(reports, 81001, T0.plusSeconds(10));
        outcome.actualSignal = signals.isEmpty() ? "MISSING" : signals.get(0).category().name();
        List<ProfileLearningProposal> proposals = engine.hypotheses(reports, 81001, 1, 1, T0.plusSeconds(10));
        boolean pendingOnly = proposals.stream().allMatch(proposal -> proposal.status() == Status.PENDING_CONFIRMATION
                && !ProfileLearningEngine.matchingMayRead(proposal));
        outcome.correctProposal = outcome.expectedSignal.equals(outcome.actualSignal)
                && (count < 3 ? proposals.isEmpty() : proposals.size() == 1 && pendingOnly);
        outcome.unconfirmedApplied = pendingOnly || proposals.isEmpty() ? 0 : 1;
    }

    private static void contradiction(ProfileLearningEngine engine, Outcome outcome) {
        outcome.expectedSignal = "PREFERENCE_HYPOTHESIS";
        List<SelfDirectionReport> reports = List.of(
                new SelfDirectionReport("a1", 81001, "self.communication", "quieter", T0),
                new SelfDirectionReport("a2", 81001, "self.communication", "quieter", T0.plusSeconds(1)),
                new SelfDirectionReport("a3", 81001, "self.communication", "quieter", T0.plusSeconds(2)),
                new SelfDirectionReport("b1", 81001, "self.communication", "more_frequent", T0.plusSeconds(3)),
                new SelfDirectionReport("b2", 81001, "self.communication", "more_frequent", T0.plusSeconds(4)),
                new SelfDirectionReport("b3", 81001, "self.communication", "more_frequent", T0.plusSeconds(5)));
        outcome.actualSignal = FeedbackSignalClassifier.observe(reports, 81001, T0.plusSeconds(6)).get(0).category().name();
        List<ProfileLearningProposal> proposals = engine.hypotheses(reports, 81001, 1, 1, T0.plusSeconds(6));
        outcome.correctProposal = proposals.size() == 2 && proposals.stream().allMatch(proposal ->
                proposal.evidenceStatus().equals("CONTRADICTED") && !ProfileLearningEngine.matchingMayRead(proposal));
    }

    private static void reject(ProfileLearningEngine engine, Outcome outcome) {
        outcome.expectedSignal = "EXPLICIT_SELF_CORRECTION";
        ExplicitPreferenceCommand command = new ExplicitPreferenceCommand("rej", 81001, "self.communication", "frequent",
                "quiet", cn.aiditto.domain.relationship.RelationshipTypes.Importance.NONE,
                cn.aiditto.domain.relationship.RelationshipTypes.Operator.EQ, ExplicitPreferenceCommand.Action.REJECT,
                0, 1, 1, true, true, List.of("evt.rej"), T0);
        outcome.actualSignal = engine.explicitSignal(command).category().name();
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, engine.propose(command), List.of(), 0, 1,
                null, true, false, false, new ConfirmationSources());
        outcome.correctProposal = update.proposal().status() == Status.REJECTED && update.snapshot() == null;
        outcome.unconfirmedApplied = update.snapshot() == null ? 0 : 1;
    }

    private static void revoke(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("rev", "self.communication", "quiet", true);
        ConfirmationSources sources = new ConfirmationSources();
        ProfileEvidence kept = engine.seed(81001, "self.pace", "slow");
        sources.put(ConfirmationSources.seed(81001, kept.sourceRef()));
        ProfileLearningEngine.ProfileUpdate written = engine.apply(command, engine.propose(command), List.of(kept), 0, 1,
                null, true, false, false, sources);
        ProfileLearningEngine.ProfileUpdate retracted = engine.retractDerived(81001, written.evidence(), "confirm.rev", 1, 1, sources);
        boolean quietGone = retracted.snapshot().self().features().stream().noneMatch(feature -> feature.featureCode().equals("self.communication"));
        boolean paceKept = retracted.snapshot().self().features().stream().anyMatch(feature -> feature.featureCode().equals("self.pace"));
        outcome.correctProposal = quietGone && paceKept;
        outcome.revokeLeak = outcome.correctProposal ? 0 : 1;
        outcome.applied = true;
        outcome.traceable = true;
    }

    private static void erased(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("era", "self.communication", "quiet", true);
        try {
            engine.apply(command, engine.propose(command), List.of(), 0, 1, null, true, true, false, new ConfirmationSources());
            outcome.correctProposal = false;
        } catch (FeedbackRejection rejection) {
            outcome.correctProposal = "ERASED".equals(rejection.code());
        }
    }

    private static void stale(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("old", "self.communication", "quiet", true);
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, engine.propose(command), List.of(), 4, 1,
                "hash", true, false, false, new ConfirmationSources());
        outcome.correctProposal = update.proposal().status() == Status.STALE_PROFILE && update.snapshot() == null;
        outcome.staleOverwrite = update.snapshot() == null ? 0 : 1;
    }

    private static void duplicate(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("dup", "self.communication", "quiet", true);
        ConfirmationSources sources = new ConfirmationSources();
        ProfileLearningEngine.ProfileUpdate first = engine.apply(command, engine.propose(command), List.of(), 0, 1,
                null, true, false, false, sources);
        ProfileLearningEngine.ProfileUpdate second = engine.apply(command, engine.propose(command), first.evidence(), 1, 1,
                first.snapshot().inputHash(), true, false, false, sources);
        outcome.correctProposal = second.decision().kind() == ProfileVersionPolicy.Kind.KEEP_CURRENT;
        outcome.duplicate = outcome.correctProposal ? 0 : 1;
        outcome.applied = true;
        outcome.traceable = true;
        outcome.explicitAttempt = true;
        outcome.explicitSuccess = true;
    }

    private static void noConsent(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("noc", "self.communication", "quiet", false);
        outcome.expectedSignal = "EXPLICIT_SELF_CORRECTION";
        outcome.actualSignal = engine.explicitSignal(command).category().name();
        try {
            engine.apply(command, engine.propose(command), List.of(), 0, 1, null, true, false, false, new ConfirmationSources());
            outcome.correctProposal = false;
        } catch (FeedbackRejection rejection) {
            outcome.correctProposal = "NO_LEARNING_CONSENT".equals(rejection.code());
        }
    }

    private static void hard(ProfileLearningEngine engine, Outcome outcome) {
        try {
            engine.propose(command("age", "partner.age", "30", true));
            outcome.hardEdit = 1;
            outcome.correctProposal = false;
        } catch (FeedbackRejection rejection) {
            outcome.correctProposal = "ILLEGAL_HARD_CONSTRAINT".equals(rejection.code());
            outcome.hardEdit = outcome.correctProposal ? 0 : 1;
        }
    }

    private static void crossUser(ProfileLearningEngine engine, Outcome outcome) {
        ExplicitPreferenceCommand command = command("x", "self.communication", "quiet", true);
        ProfileLearningProposal proposal = engine.propose(command);
        ExplicitPreferenceCommand other = new ExplicitPreferenceCommand("x", 81002, "self.communication", null, "quiet",
                cn.aiditto.domain.relationship.RelationshipTypes.Importance.NONE,
                cn.aiditto.domain.relationship.RelationshipTypes.Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                0, 1, 1, true, true, List.of("evt.x"), T0);
        try {
            engine.apply(other, proposal, List.of(), 0, 1, null, true, false, false, new ConfirmationSources());
            outcome.correctProposal = false;
        } catch (FeedbackRejection rejection) {
            outcome.correctProposal = "CROSS_USER".equals(rejection.code());
        }
    }

    private static void withheld(ProfileLearningEngine engine, Outcome outcome) {
        outcome.expectedSignal = "WITHHELD";
        FeedbackLedger ledger = ledger(true, false);
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "acc", T0, null, null, null), T0);
        outcome.actualSignal = engine.signals(ledger, 81001).get(0).category().name();
        outcome.correctProposal = !engine.signals(ledger, 81001).get(0).canPropose();
    }

    private static ExplicitPreferenceCommand command(String id, String feature, String value, boolean learning) {
        boolean preference = feature.startsWith("partner.");
        return new ExplicitPreferenceCommand(id, 81001, feature, null, value,
                preference ? cn.aiditto.domain.relationship.RelationshipTypes.Importance.KINDA
                        : cn.aiditto.domain.relationship.RelationshipTypes.Importance.NONE,
                cn.aiditto.domain.relationship.RelationshipTypes.Operator.EQ,
                ExplicitPreferenceCommand.Action.CONFIRM, 0, 1, 1, true, learning, List.of("evt." + id), T0);
    }

    private static FeedbackLedger ledger(boolean feedback, boolean learning) {
        FeedbackLedger ledger = new FeedbackLedger(MatchFeedbackContext.experiment(71051, "run-f02", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", feedback, feedback, learning, learning));
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM, "pub", T0, null, null, null), T0);
        return ledger;
    }

    private static final class Outcome {
        String expectedSignal;
        String actualSignal = "";
        boolean correctProposal;
        int wrongDirection;
        int counterpartWrite;
        int unconfirmedApplied;
        boolean explicitAttempt;
        boolean explicitSuccess;
        int staleOverwrite;
        int duplicate;
        int revokeLeak;
        int hardEdit;
        boolean applied;
        boolean traceable;
        int personified;
    }
}
