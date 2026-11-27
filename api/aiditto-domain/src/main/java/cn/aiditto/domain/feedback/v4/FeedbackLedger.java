package cn.aiditto.domain.feedback.v4;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Append-only facts for one saved match. Repeating an idempotency key returns the original event.
 * Versions are copied from {@link MatchFeedbackContext}, never from the command.
 */
public final class FeedbackLedger {
    private final MatchFeedbackContext context;
    private final List<RelationshipFeedbackEvent> events = new ArrayList<>();
    private final Map<String, RelationshipFeedbackEvent> byKey = new LinkedHashMap<>();
    private final Set<String> consumed = new LinkedHashSet<>();
    private final Set<Long> erasedActors = new LinkedHashSet<>();
    private boolean learningBlocked;
    private boolean duplicateConsumption;

    public FeedbackLedger(MatchFeedbackContext context) {
        this.context = context;
    }

    public MatchFeedbackContext context() {
        return context;
    }

    public List<RelationshipFeedbackEvent> events() {
        return List.copyOf(events);
    }

    public boolean learningBlocked() {
        return learningBlocked;
    }

    public synchronized void blockLearning() {
        learningBlocked = true;
    }

    /** A deletion mark blocks later writes by that person. It does not revive an old event. */
    public synchronized void markErased(long userId) {
        erasedActors.add(userId);
        learningBlocked = true;
    }

    /** Reloads a claim that was already stored. It does not count as a second claim. */
    public synchronized void restoreConsumed(String eventId) {
        consumed.add(eventId);
    }

    /** The first claim may be learned later. A second claim is duplicate consumption. */
    public synchronized boolean markConsumed(String eventId) {
        if (events.stream().noneMatch(event -> event.eventId().equals(eventId))) throw new FeedbackRejection("UNKNOWN_EVENT");
        if (supersededIds().contains(eventId)) throw new FeedbackRejection("SUPERSEDED");
        if (!consumed.add(eventId)) {
            duplicateConsumption = true;
            return false;
        }
        return true;
    }

    public synchronized RelationshipFeedbackEvent append(FeedbackCommand command, Instant recordedAt) {
        RelationshipFeedbackEvent existing = byKey.get(command.idempotencyKey());
        if (existing != null) {
            long actor = command.type().system() ? 0 : command.actorUserId();
            if (existing.eventType() != command.type() || existing.actorUserId() != actor
                    || !java.util.Objects.equals(existing.reasonCode(), command.reasonCode())
                    || !java.util.Objects.equals(existing.supersedesEventId(), command.supersedesEventId())
                    || !existing.occurredAt().equals(command.occurredAt())) {
                throw new FeedbackRejection("IDEMPOTENCY_CONFLICT");
            }
            return existing;
        }
        validate(command);
        RelationshipFeedbackEvent event = materialize(command, recordedAt, null);
        events.add(event);
        byKey.put(event.idempotencyKey(), event);
        if (command.type() == FeedbackEventType.MATCH_ACCEPTED) deriveMutual(recordedAt);
        return event;
    }

    /** Rebuild from stored rows. The rows were validated when they were written. */
    public static FeedbackLedger replay(MatchFeedbackContext context, List<RelationshipFeedbackEvent> stored, boolean learningBlocked) {
        FeedbackLedger ledger = new FeedbackLedger(context);
        ledger.learningBlocked = learningBlocked;
        for (RelationshipFeedbackEvent event : stored) {
            if (ledger.byKey.put(event.idempotencyKey(), event) != null) throw new FeedbackRejection("DUPLICATE_STORED_KEY");
            ledger.events.add(event);
        }
        return ledger;
    }

    public synchronized FeedbackProjection project() {
        Set<String> superseded = supersededIds();
        boolean published = false;
        boolean viewedA = false;
        boolean viewedB = false;
        boolean acceptA = false;
        boolean acceptB = false;
        boolean declineA = false;
        boolean declineB = false;
        boolean expired = false;
        boolean mutual = false;
        boolean scheduled = false;
        boolean cancelled = false;
        Boolean attendedA = null;
        Boolean attendedB = null;
        Boolean continueA = null;
        Boolean continueB = null;
        boolean retracted = false;
        boolean revoked = false;
        Instant last = null;
        List<String> mutualCauses = new ArrayList<>();
        for (RelationshipFeedbackEvent event : events) {
            if (superseded.contains(event.eventId())) continue;
            last = event.recordedAt();
            long actor = event.actorUserId();
            switch (event.eventType()) {
                case MATCH_PUBLISHED -> published = true;
                case MATCH_VIEWED -> {
                    if (actor == context.userA()) viewedA = true;
                    else viewedB = true;
                }
                case MATCH_ACCEPTED -> {
                    if (actor == context.userA()) acceptA = true;
                    else acceptB = true;
                }
                case MATCH_DECLINED -> {
                    if (actor == context.userA()) declineA = true;
                    else declineB = true;
                }
                case MATCH_EXPIRED -> expired = true;
                case MUTUAL_ACCEPTANCE_REACHED -> {
                    if (event.causationEventId() != null) {
                        for (String cause : event.causationEventId().split(",")) {
                            if (!cause.isBlank()) mutualCauses.add(cause);
                        }
                    }
                }
                case DATE_SCHEDULED -> scheduled = true;
                case DATE_CANCELLED -> cancelled = true;
                case DATE_REPORTED_ATTENDED -> {
                    if (actor == context.userA()) attendedA = true;
                    else attendedB = true;
                }
                case DATE_REPORTED_NOT_ATTENDED -> {
                    if (actor == context.userA()) attendedA = false;
                    else attendedB = false;
                }
                case CONTINUE_INTEREST_EXPRESSED -> {
                    if (actor == context.userA()) continueA = true;
                    else continueB = true;
                }
                case CONTINUE_INTEREST_DECLINED -> {
                    if (actor == context.userA()) continueA = false;
                    else continueB = false;
                }
                case FEEDBACK_RETRACTED -> retracted = true;
                case FEEDBACK_PROCESSING_REVOKED -> revoked = true;
                case PREFERENCE_CORRECTION_SUBMITTED, FEEDBACK_CORRECTED -> {
                    // The correction is the event that superseded the old one. It carries no extra fact.
                }
            }
        }
        java.util.Set<String> effectiveAccepts = new java.util.LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : events) {
            if (superseded.contains(event.eventId())) continue;
            if (event.eventType() == FeedbackEventType.MATCH_ACCEPTED) effectiveAccepts.add(event.eventId());
        }
        mutual = false;
        for (RelationshipFeedbackEvent event : events) {
            if (superseded.contains(event.eventId()) || event.eventType() != FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED) {
                continue;
            }
            java.util.Set<String> causes = new java.util.LinkedHashSet<>();
            if (event.causationEventId() != null) {
                for (String cause : event.causationEventId().split(",")) {
                    if (!cause.isBlank()) causes.add(cause);
                }
            }
            if (causes.size() == 2 && causes.equals(effectiveAccepts)) mutual = true;
        }
        boolean contested = attendedA != null && attendedB != null && !attendedA.equals(attendedB);
        List<String> unknown = new ArrayList<>();
        if (!viewedA) unknown.add("VIEW_A");
        if (!viewedB) unknown.add("VIEW_B");
        if (!acceptA && !declineA && !expired) unknown.add("RESPONSE_A");
        if (!acceptB && !declineB && !expired) unknown.add("RESPONSE_B");
        if (attendedA == null) unknown.add("ATTENDANCE_A");
        if (attendedB == null) unknown.add("ATTENDANCE_B");
        if (continueA == null) unknown.add("CONTINUE_A");
        if (continueB == null) unknown.add("CONTINUE_B");
        FeedbackFacts.ScheduleState scheduleState = cancelled ? FeedbackFacts.ScheduleState.CANCELLED
                : scheduled ? FeedbackFacts.ScheduleState.SCHEDULED
                : expired ? FeedbackFacts.ScheduleState.NOT_OCCURRED
                : FeedbackFacts.ScheduleState.UNKNOWN;
        if (scheduleState == FeedbackFacts.ScheduleState.UNKNOWN) unknown.add("SCHEDULE");
        FeedbackLearningEligibility eligibility = eligibility();
        return new FeedbackProjection(context.matchId(), published, viewedA, viewedB,
                response(acceptA, declineA, expired, retracted), response(acceptB, declineB, expired, retracted),
                mutual && acceptA && acceptB && !retracted,
                scheduleState,
                attendance(attendedA, attendedB, contested, retracted),
                continueState(continueA, retracted), continueState(continueB, retracted),
                Boolean.TRUE.equals(continueA) && Boolean.TRUE.equals(continueB) && !retracted,
                contested, retracted, revoked, eligibility.matchFactEligible(), last, unknown);
    }

    public synchronized FeedbackLearningEligibility eligibility() {
        Set<String> reasons = new LinkedHashSet<>();
        boolean published = events.stream().anyMatch(event -> event.eventType() == FeedbackEventType.MATCH_PUBLISHED);
        if (!context.published() || !published) reasons.add("NOT_PUBLISHED");
        if (learningBlocked) reasons.add("ERASED");
        if (!context.feedbackConsentA() || !context.feedbackConsentB()) reasons.add("NO_FEEDBACK_CONSENT");
        if (!context.learningConsentA() || !context.learningConsentB()) reasons.add("NO_LEARNING_CONSENT");
        if (!consumed.isEmpty()) reasons.add("ALREADY_CONSUMED");
        if (duplicateConsumption) reasons.add("DUPLICATE_CONSUMPTION");
        boolean revoked = events.stream().anyMatch(event -> event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED);
        boolean retracted = events.stream().anyMatch(event -> event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED);
        if (revoked) reasons.add("PROCESSING_REVOKED");
        if (retracted) reasons.add("RETRACTED");
        Set<String> superseded = supersededIds();
        if (!superseded.isEmpty()) reasons.add("CORRECTED");
        Boolean attendedA = null;
        Boolean attendedB = null;
        for (RelationshipFeedbackEvent event : events) {
            if (event.actorUserId() == 0) {
                if (event.actorProfileVersionAtMatch() != context.profileVersionA()
                        || event.counterpartProfileVersionAtMatch() != context.profileVersionB()) {
                    reasons.add("SNAPSHOT_MISMATCH");
                }
            } else if (event.counterpartUserId() == 0
                    || event.actorProfileVersionAtMatch() != context.profileVersion(event.actorUserId())
                    || event.counterpartProfileVersionAtMatch() != context.profileVersion(event.counterpartUserId())) {
                reasons.add("SNAPSHOT_MISMATCH");
            }
            if (!event.compatibilityPolicyVersion().equals(context.compatibilityPolicyVersion())
                    || !event.matchingObjectiveVersion().equals(context.matchingObjectiveVersion())
                    || !event.candidateStrategyVersion().equals(context.candidateStrategyVersion())) {
                reasons.add("STRATEGY_MISMATCH");
            }
            if (superseded.contains(event.eventId())) continue;
            if (event.eventType() == FeedbackEventType.DATE_REPORTED_ATTENDED) {
                if (event.actorUserId() == context.userA()) attendedA = true;
                else if (event.actorUserId() == context.userB()) attendedB = true;
            }
            if (event.eventType() == FeedbackEventType.DATE_REPORTED_NOT_ATTENDED) {
                if (event.actorUserId() == context.userA()) attendedA = false;
                else if (event.actorUserId() == context.userB()) attendedB = false;
            }
        }
        if (attendedA != null && attendedB != null && !attendedA.equals(attendedB)) reasons.add("CONTESTED");
        return new FeedbackLearningEligibility(context.matchId(), reasons.isEmpty(), false, false, List.copyOf(reasons));
    }

    private static FeedbackFacts.ResponseState response(boolean accept, boolean decline, boolean expired, boolean retracted) {
        if (retracted) return FeedbackFacts.ResponseState.WITHHELD;
        if (decline) return FeedbackFacts.ResponseState.DECLINED;
        if (accept) return FeedbackFacts.ResponseState.ACCEPTED;
        if (expired) return FeedbackFacts.ResponseState.EXPIRED;
        return FeedbackFacts.ResponseState.UNKNOWN;
    }

    private static FeedbackFacts.AttendanceState attendance(Boolean a, Boolean b, boolean contested, boolean retracted) {
        if (retracted) return FeedbackFacts.AttendanceState.WITHHELD;
        if (contested) return FeedbackFacts.AttendanceState.CONTESTED;
        if (Boolean.TRUE.equals(a) && Boolean.TRUE.equals(b)) return FeedbackFacts.AttendanceState.BOTH_REPORTED_ATTENDED;
        if (Boolean.FALSE.equals(a) && Boolean.FALSE.equals(b)) return FeedbackFacts.AttendanceState.BOTH_REPORTED_NOT_ATTENDED;
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return FeedbackFacts.AttendanceState.ONE_REPORTED_ATTENDED;
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) return FeedbackFacts.AttendanceState.ONE_REPORTED_NOT_ATTENDED;
        return FeedbackFacts.AttendanceState.UNKNOWN;
    }

    private static FeedbackFacts.ContinueState continueState(Boolean value, boolean retracted) {
        if (retracted) return FeedbackFacts.ContinueState.WITHHELD;
        if (value == null) return FeedbackFacts.ContinueState.UNKNOWN;
        return value ? FeedbackFacts.ContinueState.EXPRESSED : FeedbackFacts.ContinueState.DECLINED;
    }

    private void validate(FeedbackCommand command) {
        if (!context.published()) throw new FeedbackRejection("NOT_AUTHORIZED");
        if (command.type().system() != (command.source() == FeedbackFacts.SourceType.SYSTEM)) {
            throw new FeedbackRejection(command.source() == FeedbackFacts.SourceType.PARTICIPANT ? "FORGED_SYSTEM_EVENT" : "SYSTEM_ACTOR");
        }
        if (command.type().system()) {
            if (command.actorUserId() != 0) throw new FeedbackRejection("SYSTEM_ACTOR");
        } else if (!context.participant(command.actorUserId())) {
            throw new FeedbackRejection("NOT_PARTICIPANT");
        }
        if (command.claimedCounterpartUserId() != null && !command.type().system()) {
            if (command.claimedCounterpartUserId() != context.counterpart(command.actorUserId())) {
                throw new FeedbackRejection("FORGED_COUNTERPART");
            }
        }
        if (erasedActors.contains(context.userA()) || erasedActors.contains(context.userB())) {
            throw new FeedbackRejection("ERASED");
        }
        if (command.supersedesEventId() != null) checkSupersedes(command);
        if ((command.type() == FeedbackEventType.FEEDBACK_CORRECTED
                || command.type() == FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED)
                && command.supersedesEventId() == null) {
            throw new FeedbackRejection("MISSING_SUPERSEDES");
        }
        boolean published = events.stream().anyMatch(event -> event.eventType() == FeedbackEventType.MATCH_PUBLISHED);
        if (command.type() != FeedbackEventType.MATCH_PUBLISHED && !published) throw new FeedbackRejection("NOT_PUBLISHED");
        boolean expired = events.stream().anyMatch(event -> event.eventType() == FeedbackEventType.MATCH_EXPIRED
                && !supersededIds().contains(event.eventId()));
        if (expired && command.supersedesEventId() == null
                && (command.type() == FeedbackEventType.MATCH_ACCEPTED
                || command.type() == FeedbackEventType.MATCH_DECLINED
                || command.type() == FeedbackEventType.DATE_SCHEDULED
                || command.type() == FeedbackEventType.DATE_CANCELLED)) {
            throw new FeedbackRejection("EXPIRED");
        }
        if (command.type() == FeedbackEventType.DATE_SCHEDULED
                && !active(context.userA(), FeedbackEventType.MATCH_ACCEPTED)
                && !active(context.userB(), FeedbackEventType.MATCH_ACCEPTED)) {
            throw new FeedbackRejection("ILLEGAL_SCHEDULE");
        }
        if (command.type() == FeedbackEventType.DATE_SCHEDULED
                && !(active(context.userA(), FeedbackEventType.MATCH_ACCEPTED)
                && active(context.userB(), FeedbackEventType.MATCH_ACCEPTED))) {
            throw new FeedbackRejection("ILLEGAL_SCHEDULE");
        }
        if (command.type() == FeedbackEventType.DATE_CANCELLED
                && !active(context.userA(), FeedbackEventType.DATE_SCHEDULED)
                && !active(context.userB(), FeedbackEventType.DATE_SCHEDULED)) {
            throw new FeedbackRejection("ILLEGAL_SCHEDULE");
        }
        if (!command.type().system()
                && command.type() != FeedbackEventType.MATCH_VIEWED
                && command.type() != FeedbackEventType.DATE_SCHEDULED
                && command.type() != FeedbackEventType.DATE_CANCELLED
                && command.type() != FeedbackEventType.FEEDBACK_RETRACTED
                && command.type() != FeedbackEventType.FEEDBACK_PROCESSING_REVOKED
                && !context.feedbackConsent(command.actorUserId())) {
            throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        }
        if (command.supersedesEventId() == null && conflicts(command)) throw new FeedbackRejection("CONFLICTING_RESPONSE");
        if (active(command.actorUserId(), command.type()) && command.supersedesEventId() == null
                && command.type() != FeedbackEventType.FEEDBACK_CORRECTED
                && command.type() != FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED) {
            throw new FeedbackRejection("DUPLICATE_EFFECT");
        }
    }

    private void checkSupersedes(FeedbackCommand command) {
        RelationshipFeedbackEvent target = events.stream()
                .filter(event -> event.eventId().equals(command.supersedesEventId()))
                .findFirst()
                .orElseThrow(() -> new FeedbackRejection("UNKNOWN_SUPERSEDED"));
        if (target.eventType() == FeedbackEventType.FEEDBACK_RETRACTED
                || target.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED) {
            throw new FeedbackRejection("REVOCATION_STANDS");
        }
        if (target.eventType().system()) throw new FeedbackRejection("CANNOT_CORRECT_SYSTEM");
        if (command.type().system()) throw new FeedbackRejection("FORGED_SYSTEM_EVENT");
        if (target.actorUserId() != command.actorUserId()) throw new FeedbackRejection("NOT_EVENT_OWNER");
        if (supersededIds().contains(target.eventId())) throw new FeedbackRejection("NOT_EFFECTIVE");
        boolean response = target.eventType() == FeedbackEventType.MATCH_ACCEPTED
                || target.eventType() == FeedbackEventType.MATCH_DECLINED
                || target.eventType() == FeedbackEventType.DATE_REPORTED_ATTENDED
                || target.eventType() == FeedbackEventType.DATE_REPORTED_NOT_ATTENDED
                || target.eventType() == FeedbackEventType.CONTINUE_INTEREST_EXPRESSED
                || target.eventType() == FeedbackEventType.CONTINUE_INTEREST_DECLINED
                || target.eventType() == FeedbackEventType.DATE_SCHEDULED
                || target.eventType() == FeedbackEventType.DATE_CANCELLED
                || target.eventType() == FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED
                || target.eventType() == FeedbackEventType.FEEDBACK_CORRECTED;
        if (command.type() == FeedbackEventType.FEEDBACK_CORRECTED && !response) {
            throw new FeedbackRejection("ILLEGAL_REPLACEMENT");
        }
        if (command.type() == FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED
                && target.eventType() != FeedbackEventType.MATCH_DECLINED
                && target.eventType() != FeedbackEventType.CONTINUE_INTEREST_DECLINED
                && target.eventType() != FeedbackEventType.CONTINUE_INTEREST_EXPRESSED
                && target.eventType() != FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED) {
            throw new FeedbackRejection("ILLEGAL_REPLACEMENT");
        }
        if (command.type() == FeedbackEventType.MATCH_DECLINED
                && target.eventType() != FeedbackEventType.MATCH_ACCEPTED
                && target.eventType() != FeedbackEventType.FEEDBACK_CORRECTED) {
            throw new FeedbackRejection("ILLEGAL_REPLACEMENT");
        }
        if (command.type() == FeedbackEventType.MATCH_ACCEPTED
                && target.eventType() != FeedbackEventType.MATCH_DECLINED
                && target.eventType() != FeedbackEventType.FEEDBACK_CORRECTED) {
            throw new FeedbackRejection("ILLEGAL_REPLACEMENT");
        }
    }

    private boolean conflicts(FeedbackCommand command) {
        long actor = command.actorUserId();
        return switch (command.type()) {
            case MATCH_ACCEPTED -> active(actor, FeedbackEventType.MATCH_DECLINED);
            case MATCH_DECLINED -> active(actor, FeedbackEventType.MATCH_ACCEPTED);
            case DATE_REPORTED_ATTENDED -> active(actor, FeedbackEventType.DATE_REPORTED_NOT_ATTENDED);
            case DATE_REPORTED_NOT_ATTENDED -> active(actor, FeedbackEventType.DATE_REPORTED_ATTENDED);
            case CONTINUE_INTEREST_EXPRESSED -> active(actor, FeedbackEventType.CONTINUE_INTEREST_DECLINED);
            case CONTINUE_INTEREST_DECLINED -> active(actor, FeedbackEventType.CONTINUE_INTEREST_EXPRESSED);
            default -> false;
        };
    }

    private boolean active(long actor, FeedbackEventType type) {
        Set<String> superseded = supersededIds();
        for (RelationshipFeedbackEvent event : events) {
            if (superseded.contains(event.eventId())) continue;
            if (event.actorUserId() == actor && event.eventType() == type) return true;
        }
        return false;
    }

    private Set<String> supersededIds() {
        Set<String> superseded = new LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : events) {
            if (event.supersedesEventId() == null) continue;
            RelationshipFeedbackEvent target = events.stream()
                    .filter(candidate -> candidate.eventId().equals(event.supersedesEventId()))
                    .findFirst()
                    .orElse(null);
            if (target == null) continue;
            if (target.eventType() == FeedbackEventType.FEEDBACK_RETRACTED
                    || target.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED) {
                continue;
            }
            superseded.add(event.supersedesEventId());
        }
        return superseded;
    }

    private void deriveMutual(Instant recordedAt) {
        boolean acceptA = active(context.userA(), FeedbackEventType.MATCH_ACCEPTED);
        boolean acceptB = active(context.userB(), FeedbackEventType.MATCH_ACCEPTED);
        if (!acceptA || !acceptB) return;
        Set<String> superseded = supersededIds();
        String causes = events.stream()
                .filter(event -> event.eventType() == FeedbackEventType.MATCH_ACCEPTED)
                .filter(event -> !superseded.contains(event.eventId()))
                .map(RelationshipFeedbackEvent::eventId)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (causes.isBlank() || !causes.contains(",")) return;
        String key = "match:" + context.matchId() + ":mutual-acceptance:" + causes;
        if (byKey.containsKey(key)) return;
        RelationshipFeedbackEvent derived = materialize(new FeedbackCommand(FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED, 0,
                FeedbackFacts.SourceType.SYSTEM, key, recordedAt, null, null, null), recordedAt, causes);
        events.add(derived);
        byKey.put(key, derived);
    }

    private RelationshipFeedbackEvent materialize(FeedbackCommand command, Instant recordedAt, String causation) {
        boolean system = command.type().system();
        long actor = system ? 0 : command.actorUserId();
        long counterpart = system ? 0 : context.counterpart(actor);
        FeedbackFacts.Direction direction = system ? FeedbackFacts.Direction.JOINT
                : actor == context.userA() ? FeedbackFacts.Direction.A_TO_B : FeedbackFacts.Direction.B_TO_A;
        long actorVersion = system ? context.profileVersionA() : context.profileVersion(actor);
        long counterpartVersion = system ? context.profileVersionB() : context.profileVersion(counterpart);
        return new RelationshipFeedbackEvent(UUID.randomUUID().toString(), FeedbackFacts.SCHEMA, command.type(),
                context.matchId(), context.matchingRunId(), context.matchingPeriod(), actor, counterpart, direction,
                command.source(), command.occurredAt(), recordedAt, command.idempotencyKey(), causation,
                command.supersedesEventId(), system ? "SYSTEM" : "FEEDBACK_PROCESSING",
                actorVersion, counterpartVersion, context.compatibilityPolicyVersion(), context.matchingObjectiveVersion(),
                context.candidateStrategyVersion(), command.reasonCode(), command.type().evidence());
    }
}
