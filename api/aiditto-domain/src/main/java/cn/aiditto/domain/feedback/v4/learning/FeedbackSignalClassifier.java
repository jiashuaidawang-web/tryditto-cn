package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.Authorization;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.Category;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.Direction;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.EvidenceState;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.Scope;
import cn.aiditto.domain.feedback.v4.learning.LearningSignal.SourceTrust;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads feedback as signals. A decline, a timeout, or another person's report
 * does not become the subject's preference.
 */
public final class FeedbackSignalClassifier {
    private FeedbackSignalClassifier() {}

    public static List<LearningSignal> classify(FeedbackLedger ledger, long subject) {
        if (!ledger.context().participant(subject)) throw new FeedbackRejection("NOT_PARTICIPANT");
        Set<String> superseded = new LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.supersedesEventId() != null) superseded.add(event.supersedesEventId());
        }
        boolean subjectWithheld = ledger.learningBlocked()
                || !ledger.context().learningConsent(subject)
                || !ledger.context().feedbackConsent(subject)
                || ledger.events().stream().anyMatch(event -> event.actorUserId() == subject
                && (event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED
                || event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED));
        List<LearningSignal> signals = new ArrayList<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.eventType() == FeedbackEventType.MATCH_PUBLISHED
                    || event.eventType() == FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED) {
                continue;
            }
            if (event.actorUserId() == 0) {
                if (event.eventType() == FeedbackEventType.MATCH_EXPIRED) {
                    signals.add(signal(event.eventId(), subject, Category.NO_LEARNING_SIGNAL, Direction.NONE,
                            SourceTrust.SYSTEM, null, Scope.NONE, Authorization.OPEN, event.occurredAt(), EvidenceState.NOT_USABLE));
                }
                continue;
            }
            if (event.actorUserId() != subject) {
                signals.add(signal(event.eventId(), subject, Category.COUNTERPART_OPINION, Direction.NONE,
                        SourceTrust.COUNTERPART, null, Scope.NONE,
                        subjectWithheld ? Authorization.WITHHELD : Authorization.OPEN,
                        event.occurredAt(), EvidenceState.NOT_USABLE));
                continue;
            }
            Category category = subjectWithheld ? Category.WITHHELD : ownCategory(event);
            Scope scope = category == Category.RELATIONSHIP_OUTCOME ? Scope.RELATIONSHIP : Scope.NONE;
            Authorization authorization = subjectWithheld || category == Category.WITHHELD
                    ? Authorization.WITHHELD : Authorization.OPEN;
            EvidenceState state = EvidenceState.NOT_USABLE;
            if (superseded.contains(event.eventId())) authorization = Authorization.WITHHELD;
            signals.add(signal(event.eventId(), subject, category, Direction.NONE, SourceTrust.SELF, null, scope,
                    authorization, event.occurredAt(), state));
        }
        return List.copyOf(signals);
    }

    /**
     * Repeated self-reports can become an unconfirmed hypothesis.
     * The repeat count and the window are unverified parameters, not feature weights.
     */
    public static List<LearningSignal> observe(List<SelfDirectionReport> reports, long subject, Instant now) {
        Map<String, Set<String>> ids = new LinkedHashMap<>();
        Map<String, Instant> latest = new LinkedHashMap<>();
        for (SelfDirectionReport report : reports) {
            if (report.userId() != subject) continue;
            if (report.occurredAt().isBefore(now.minus(Duration.ofDays(ProfileLearningEngine.UNVERIFIED_WINDOW_DAYS)))) {
                continue;
            }
            if (!report.featureCode().equals("self.communication") && !report.featureCode().equals("partner.communication")) {
                continue;
            }
            String value = valueOf(report.direction());
            if (value == null) continue;
            String key = report.featureCode() + "|" + value;
            ids.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(report.eventId());
            latest.merge(key, report.occurredAt(), (left, right) -> left.isAfter(right) ? left : right);
        }
        Set<String> contradicted = new LinkedHashSet<>();
        Map<String, Set<String>> valuesByFeature = new LinkedHashMap<>();
        for (String key : ids.keySet()) {
            String feature = key.substring(0, key.indexOf('|'));
            String value = key.substring(key.indexOf('|') + 1);
            valuesByFeature.computeIfAbsent(feature, ignored -> new LinkedHashSet<>()).add(value);
        }
        for (var entry : valuesByFeature.entrySet()) {
            if (entry.getValue().size() > 1) contradicted.add(entry.getKey());
        }
        List<LearningSignal> signals = new ArrayList<>();
        for (var entry : ids.entrySet()) {
            String feature = entry.getKey().substring(0, entry.getKey().indexOf('|'));
            String value = entry.getKey().substring(entry.getKey().indexOf('|') + 1);
            Instant when = latest.get(entry.getKey());
            if (when.isBefore(now.minus(Duration.ofDays(ProfileLearningEngine.UNVERIFIED_WINDOW_DAYS)))) continue;
            int count = entry.getValue().size();
            Category category = count >= ProfileLearningEngine.UNVERIFIED_REPEAT_COUNT
                    ? Category.PREFERENCE_HYPOTHESIS : Category.BEHAVIORAL_OBSERVATION;
            Scope scope = feature.startsWith("partner.") ? Scope.PARTNER_PREFERENCE : Scope.SELF_PROFILE;
            EvidenceState state = contradicted.contains(feature) ? EvidenceState.USABLE : EvidenceState.USABLE;
            signals.add(new LearningSignal(feature + ":" + value, subject, category, Direction.NONE, SourceTrust.SELF,
                    feature + "=" + value, scope, Authorization.OPEN, when.plus(Duration.ofDays(ProfileLearningEngine.UNVERIFIED_WINDOW_DAYS)),
                    state));
        }
        return List.copyOf(signals);
    }

    private static Category ownCategory(RelationshipFeedbackEvent event) {
        return switch (event.eventType()) {
            case FEEDBACK_RETRACTED, FEEDBACK_PROCESSING_REVOKED -> Category.WITHHELD;
            case MATCH_DECLINED -> event.reasonCode() == null ? Category.NO_LEARNING_SIGNAL : Category.AMBIGUOUS_REASON;
            case PREFERENCE_CORRECTION_SUBMITTED, FEEDBACK_CORRECTED -> Category.AMBIGUOUS_REASON;
            case MATCH_VIEWED -> Category.NO_LEARNING_SIGNAL;
            case MATCH_ACCEPTED, DATE_SCHEDULED, DATE_CANCELLED, DATE_REPORTED_ATTENDED, DATE_REPORTED_NOT_ATTENDED,
                    CONTINUE_INTEREST_EXPRESSED, CONTINUE_INTEREST_DECLINED -> Category.RELATIONSHIP_OUTCOME;
            default -> Category.NO_LEARNING_SIGNAL;
        };
    }

    public static String valueOf(String direction) {
        return switch (direction) {
            case "quieter" -> "quiet";
            case "more_direct" -> "direct";
            case "more_frequent" -> "frequent";
            default -> null;
        };
    }

    private static LearningSignal signal(String eventId, long userId, Category category, Direction direction,
                                         SourceTrust trust, String target, Scope scope, Authorization authorization,
                                         Instant until, EvidenceState state) {
        return new LearningSignal(eventId, userId, category, direction, trust, target, scope, authorization, until, state);
    }
}
