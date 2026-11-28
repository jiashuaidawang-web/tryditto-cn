package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.Category;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal.Status;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipProfile;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.SnapshotAdmission;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns legal synthetic feedback into proposals and, after the user's own confirmation,
 * into one admitted profile snapshot. It does not call a model or the V3 taste rewriter.
 */
public final class ProfileLearningEngine {
    public static final String POLICY = "learn-1";
    public static final String SEED_VERSION = "seed-1";
    public static final int UNVERIFIED_REPEAT_COUNT = 3;
    public static final int UNVERIFIED_WINDOW_DAYS = 30;
    private static final Set<String> HARD = Set.of("partner.age", "partner.height", "partner.education", "partner.gender");
    private static final Set<String> HYPOTHESIS_FEATURES = Set.of("self.communication", "partner.communication");

    private final FeatureRegistry registry;

    public ProfileLearningEngine(FeatureRegistry registry) {
        this.registry = registry;
    }

    public static ProfileLearningEngine v1() {
        return new ProfileLearningEngine(FeatureRegistry.v1());
    }

    public List<LearningSignal> signals(FeedbackLedger ledger, long subject) {
        return FeedbackSignalClassifier.classify(ledger, subject);
    }

    public List<ProfileLearningProposal> hypotheses(List<SelfDirectionReport> reports, long subject, int profileVersion,
                                                    long generation, Instant now) {
        return proposalsFrom(FeedbackSignalClassifier.observe(reports, subject, now), reports, subject,
                profileVersion, generation, now);
    }

    /** Counts only reports that name a real event owned by the subject inside the window. */
    public List<ProfileLearningProposal> hypotheses(FeedbackLedger ledger, List<SelfDirectionReport> reports, long subject,
                                                    int profileVersion, long generation, Instant now) {
        List<SelfDirectionReport> accepted = FeedbackSignalClassifier.accepted(ledger, reports, subject, now);
        return proposalsFrom(FeedbackSignalClassifier.observe(accepted, subject, now), accepted, subject,
                profileVersion, generation, now);
    }

    private List<ProfileLearningProposal> proposalsFrom(List<LearningSignal> signals, List<SelfDirectionReport> reports,
                                                        long subject, int profileVersion, long generation, Instant now) {
        List<ProfileLearningProposal> proposals = new ArrayList<>();
        for (LearningSignal signal : signals) {
            if (signal.category() != Category.PREFERENCE_HYPOTHESIS) continue;
            String feature = signal.learnableTarget().substring(0, signal.learnableTarget().indexOf('='));
            String value = signal.learnableTarget().substring(signal.learnableTarget().indexOf('=') + 1);
            Set<String> ids = new LinkedHashSet<>();
            for (SelfDirectionReport report : reports) {
                if (report.userId() == subject && report.featureCode().equals(feature)
                        && value.equals(FeedbackSignalClassifier.valueOf(report.direction()))) {
                    ids.add(report.eventId());
                }
            }
            boolean contradicted = signal.evidenceState() != LearningSignal.EvidenceState.USABLE
                    || signals.stream().anyMatch(other -> other.learnableTarget() != null
                    && other.learnableTarget().startsWith(feature + "=")
                    && !other.learnableTarget().equals(signal.learnableTarget()));
            Section section = feature.startsWith("partner.") ? Section.PREFERENCE : Section.SELF;
            proposals.add(new ProfileLearningProposal(
                    "hyp." + subject + "." + feature.replace('.', '-') + "." + value,
                    subject, profileVersion, generation, feature, section, null, value,
                    Operator.EQ, Importance.KINDA, List.copyOf(ids), "PREFERENCE_HYPOTHESIS",
                    contradicted ? "CONTRADICTED" : "UNCONFIRMED",
                    "重复次数达到未验证阈值 " + UNVERIFIED_REPEAT_COUNT + "，时间窗口 " + UNVERIFIED_WINDOW_DAYS + " 天",
                    POLICY, Status.PENDING_CONFIRMATION, now, now.plus(Duration.ofDays(UNVERIFIED_WINDOW_DAYS))));
        }
        return List.copyOf(proposals);
    }

    public ProfileLearningProposal propose(ExplicitPreferenceCommand command) {
        FeatureDefinition definition = requireLegal(command.featureCode());
        Section section = sectionOf(definition);
        return new ProfileLearningProposal(
                "prop." + command.commandId(), command.userId(), command.profileVersion(), command.lifecycleGeneration(),
                command.featureCode(), section, command.oldValue(), command.newValue(),
                section == Section.PREFERENCE ? command.operator() : Operator.EQ,
                section == Section.PREFERENCE ? command.importance() : Importance.NONE,
                command.sourceEventIds(), "EXPLICIT_CONFIRMATION", "UNCONFIRMED",
                "用户明确选择", POLICY, Status.PENDING_CONFIRMATION, command.confirmedAt(),
                command.confirmedAt().plus(Duration.ofDays(UNVERIFIED_WINDOW_DAYS)));
    }

    public LearningSignal explicitSignal(ExplicitPreferenceCommand command) {
        FeatureDefinition definition = requireLegal(command.featureCode());
        Section section = sectionOf(definition);
        Category category = section == Section.PREFERENCE
                ? Category.EXPLICIT_PARTNER_PREFERENCE : Category.EXPLICIT_SELF_CORRECTION;
        return new LearningSignal("confirm." + command.commandId(), command.userId(), category,
                LearningSignal.Direction.NONE, LearningSignal.SourceTrust.SELF, command.featureCode(),
                section == Section.PREFERENCE ? LearningSignal.Scope.PARTNER_PREFERENCE : LearningSignal.Scope.SELF_PROFILE,
                command.learningGranted() && command.feedbackProcessingGranted()
                        ? LearningSignal.Authorization.OPEN : LearningSignal.Authorization.WITHHELD,
                command.confirmedAt(), LearningSignal.EvidenceState.USABLE);
    }

    public ProfileEvidence confirmationEvidence(ExplicitPreferenceCommand command) {
        requireLegal(command.featureCode());
        Section section = sectionOf(registry.require(command.featureCode()));
        return ProfileEvidence.build(command.userId(), command.featureCode(), section,
                new FeatureValue.EnumOne(command.newValue()), EvidenceSource.SELF_REPORTED,
                "confirm." + command.commandId(), POLICY, 1.0, EvidenceStatus.CONFIRMED,
                section == Section.PREFERENCE ? command.operator() : null,
                section == Section.PREFERENCE ? command.importance() : null,
                section == Section.PREFERENCE ? ConstraintType.SOFT : null,
                null, null, registry);
    }

    public ProfileEvidence retractEvidence(ProfileEvidence evidence) {
        return retract(evidence);
    }

    public ProfileEvidence seed(long userId, String featureCode, String value) {
        FeatureDefinition definition = requireLegal(featureCode);
        Section section = sectionOf(definition);
        return ProfileEvidence.build(userId, featureCode, section, new FeatureValue.EnumOne(value),
                EvidenceSource.SELF_REPORTED, "seed." + featureCode, SEED_VERSION, 1.0, EvidenceStatus.CONFIRMED,
                section == Section.PREFERENCE ? Operator.EQ : null,
                section == Section.PREFERENCE ? Importance.KINDA : null,
                section == Section.PREFERENCE ? ConstraintType.SOFT : null,
                null, null, registry);
    }

    public ProfileUpdate apply(ExplicitPreferenceCommand command, ProfileLearningProposal proposal,
                               List<ProfileEvidence> existing, int currentVersion, long currentGeneration,
                               String currentHash, boolean accountActive, boolean erased, boolean sourceRetracted,
                               ConfirmationSources sources) {
        if (command.userId() != proposal.userId()) throw new FeedbackRejection("CROSS_USER");
        if (proposal.proposalId().startsWith("prop.")
                && !command.commandId().equals(proposal.proposalId().substring("prop.".length()))) {
            throw new FeedbackRejection("PROPOSAL_MISMATCH");
        }
        if (!proposal.proposalId().startsWith("prop.") && !proposal.proposalId().startsWith("hyp.")) {
            throw new FeedbackRejection("PROPOSAL_MISMATCH");
        }
        if (erased) throw new FeedbackRejection("ERASED");
        if (!accountActive) throw new FeedbackRejection("ACCOUNT_INACTIVE");
        if (!command.feedbackProcessingGranted()) throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        if (!command.learningGranted()) throw new FeedbackRejection("NO_LEARNING_CONSENT");
        if (command.action() == ExplicitPreferenceCommand.Action.REJECT) {
            return ProfileUpdate.withoutSnapshot(proposal.withStatus(Status.REJECTED, "REJECTED"));
        }
        if (command.confirmedAt().isAfter(proposal.expiresAt())) {
            return ProfileUpdate.withoutSnapshot(proposal.withStatus(Status.EXPIRED, "EXPIRED"));
        }
        if (sourceRetracted) {
            return ProfileUpdate.withoutSnapshot(proposal.withStatus(Status.INVALID_EVIDENCE, "RETRACTED_SOURCE"));
        }
        requireLegal(command.featureCode());
        String sourceRef = "confirm." + command.commandId();
        if (existing.stream().anyMatch(item -> item.sourceRef().equals(sourceRef) && item.status() == EvidenceStatus.CONFIRMED)) {
            RelationshipProfileSnapshot same = SnapshotAdmission.admit(
                    RelationshipProfile.of(command.userId(), existing, registry).snapshot(registry), registry, sources);
            return new ProfileUpdate(proposal.withStatus(Status.CONFIRMED, "CONFIRMED"), same,
                    new ProfileVersionPolicy.Decision(ProfileVersionPolicy.Kind.KEEP_CURRENT, currentVersion), existing);
        }
        if (currentVersion != proposal.profileVersion() || currentGeneration != proposal.lifecycleGeneration()) {
            return ProfileUpdate.withoutSnapshot(proposal.withStatus(Status.STALE_PROFILE, "STALE"));
        }
        sources.put(ConfirmationSources.explicit(command.userId(), sourceRef, false));
        List<ProfileEvidence> next = new ArrayList<>();
        for (ProfileEvidence evidence : existing) {
            if (evidence.featureCode().equals(command.featureCode()) && evidence.status() != EvidenceStatus.RETRACTED) {
                if (command.oldValue() != null && !command.oldValue().equals(text(evidence.claim()))) {
                    return ProfileUpdate.withoutSnapshot(proposal.withStatus(Status.STALE_PROFILE, "OLD_VALUE"));
                }
                sources.retract(evidence);
                next.add(retract(evidence));
            } else {
                next.add(evidence);
            }
        }
        ProfileEvidence created = confirmationEvidence(command);
        next.add(created);
        RelationshipProfileSnapshot admitted = SnapshotAdmission.admit(
                RelationshipProfile.of(command.userId(), next, registry).snapshot(registry), registry, sources);
        int nextSource = currentVersion + 1;
        ProfileVersionPolicy.Decision decision = ProfileVersionPolicy.decide(
                true, true, currentGeneration, currentGeneration, currentVersion, nextSource, nextSource,
                currentVersion > 0, currentHash, admitted.inputHash(), ProfileVersionPolicy.WriteMode.DETERMINISTIC);
        return new ProfileUpdate(proposal.withStatus(Status.CONFIRMED, "CONFIRMED"), admitted, decision, next);
    }

    public ProfileUpdate retractDerived(long userId, List<ProfileEvidence> existing, String revokedSourceRef,
                                        int currentVersion, long generation, ConfirmationSources sources) {
        List<ProfileEvidence> next = new ArrayList<>();
        for (ProfileEvidence evidence : existing) {
            if (evidence.subjectUserId() != userId) throw new FeedbackRejection("CROSS_USER");
            if (evidence.sourceRef().equals(revokedSourceRef) && evidence.status() != EvidenceStatus.RETRACTED) {
                sources.retract(evidence);
                next.add(retract(evidence));
            } else {
                next.add(evidence);
            }
        }
        RelationshipProfileSnapshot admitted = SnapshotAdmission.admit(
                RelationshipProfile.of(userId, next, registry).snapshot(registry), registry, sources);
        int nextSource = currentVersion + 1;
        return new ProfileUpdate(null, admitted, ProfileVersionPolicy.decide(
                true, true, generation, generation, currentVersion, nextSource, nextSource,
                true, "", admitted.inputHash(), ProfileVersionPolicy.WriteMode.DETERMINISTIC), next);
    }

    public static boolean matchingMayRead(ProfileLearningProposal proposal) {
        return proposal.status() == Status.CONFIRMED;
    }

    private FeatureDefinition requireLegal(String featureCode) {
        if (HARD.contains(featureCode)) throw new FeedbackRejection("ILLEGAL_HARD_CONSTRAINT");
        FeatureDefinition definition;
        try {
            definition = registry.require(featureCode);
        } catch (IllegalArgumentException ex) {
            throw new FeedbackRejection("ILLEGAL_FEATURE");
        }
        if (!definition.allows(sectionOf(definition), EvidenceSource.SELF_REPORTED)) {
            throw new FeedbackRejection("ILLEGAL_FEATURE");
        }
        return definition;
    }

    private static Section sectionOf(FeatureDefinition definition) {
        if (definition.sections().contains(Section.PREFERENCE)) return Section.PREFERENCE;
        if (definition.sections().contains(Section.SELF)) return Section.SELF;
        throw new FeedbackRejection("ILLEGAL_FEATURE");
    }

    private ProfileEvidence retract(ProfileEvidence evidence) {
        return ProfileEvidence.build(evidence.subjectUserId(), evidence.featureCode(), evidence.section(), evidence.claim(),
                evidence.sourceType(), evidence.sourceRef(), evidence.sourceVersion(), evidence.confidence(),
                EvidenceStatus.RETRACTED, evidence.operator(), evidence.importance(), evidence.constraintType(),
                evidence.behaviorKind(), evidence.polarity(), registry);
    }

    private static String text(FeatureValue value) {
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        return "";
    }

    public boolean hypothesisFeature(String featureCode) {
        return HYPOTHESIS_FEATURES.contains(featureCode);
    }

    public record ProfileUpdate(ProfileLearningProposal proposal, RelationshipProfileSnapshot snapshot,
                                ProfileVersionPolicy.Decision decision, List<ProfileEvidence> evidence) {
        public static ProfileUpdate withoutSnapshot(ProfileLearningProposal proposal) {
            return new ProfileUpdate(proposal, null, null, List.of());
        }
    }
}
