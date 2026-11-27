package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Hand-labeled challenge relations. These labels were not produced by a model.
 * They sit beside the frozen 120-relation set and do not change its hash.
 */
public final class ReasoningChallenge {
    public static final String SOURCE = "synthetic-fixture:v4-m03-challenge";

    public enum Disposition { SUPPORT, HYPOTHESIS, INSUFFICIENT, CONTRADICTION, MUST_REJECT }

    public record Judgment(String relationId, String direction, String preferenceCode, String targetFeatureCode,
                           ReasoningCategory expectedCategory, Disposition disposition,
                           Set<ReasoningCategory> acceptableAlternatives) {}

    public record Case(String id, String split, int copy, String kind, int gain,
                       CompatibilitySubject seeker, CompatibilitySubject candidate, List<Judgment> judgments) {}

    private ReasoningChallenge() {}

    public static List<Case> all() {
        List<Case> out = new ArrayList<>();
        for (int copy = 0; copy < 6; copy++) {
            String split = copy >= 4 ? "holdout" : "dev";
            out.add(explain(split, copy));
            out.add(hypothesis(split, copy));
            out.add(insufficient(split, copy));
            out.add(contradiction(split, copy));
            out.add(falseNew(split, copy));
            out.add(oneWay(split, copy));
        }
        return List.copyOf(out);
    }

    public static String hash() {
        StringBuilder text = new StringBuilder();
        for (Case item : all()) {
            text.append(item.id()).append('|').append(item.split()).append('|').append(item.kind()).append('|').append(item.gain());
            for (Judgment judgment : item.judgments()) {
                text.append('|').append(judgment.direction()).append(':').append(judgment.preferenceCode())
                        .append(':').append(judgment.targetFeatureCode()).append(':').append(judgment.disposition());
            }
            text.append('\n');
        }
        return RelationshipReasoningContext.sha256(text.toString());
    }

    private static Case explain(String split, int copy) {
        long id = 73000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow"), self("self.communication", "quiet")),
                List.of(pref("partner.pace", "slow"), pref("partner.communication", "quiet")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.pace", "slow"), self("self.communication", "quiet")),
                List.of(pref("partner.pace", "slow"), pref("partner.communication", "quiet")));
        String relation = split + "-" + copy + "-EXPLAIN";
        return new Case(relation, split, copy, "EXPLAIN", 3, seeker, candidate, List.of(
                support(relation, "AB", "partner.pace", "self.pace"),
                support(relation, "AB", "partner.communication", "self.communication"),
                support(relation, "BA", "partner.pace", "self.pace"),
                support(relation, "BA", "partner.communication", "self.communication")));
    }

    private static Case hypothesis(String split, int copy) {
        long id = 74000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow")),
                List.of(pref("partner.support", "close")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.communication", "frequent"), self("self.pace", "slow")),
                List.of(pref("partner.pace", "slow")));
        String relation = split + "-" + copy + "-HYPOTHESIS";
        return new Case(relation, split, copy, "HYPOTHESIS", 1, seeker, candidate, List.of(
                new Judgment(relation, "AB", "partner.support", "self.communication", ReasoningCategory.POTENTIAL_COMPLEMENT,
                        Disposition.HYPOTHESIS, Set.of()),
                support(relation, "BA", "partner.pace", "self.pace")));
    }

    private static Case insufficient(String split, int copy) {
        long id = 75000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow")),
                List.of(pref("partner.pace", "slow"), pref("partner.communication", "quiet")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.pace", "slow")),
                List.of(pref("partner.pace", "slow")));
        String relation = split + "-" + copy + "-INSUFFICIENT";
        return new Case(relation, split, copy, "INSUFFICIENT", 0, seeker, candidate, List.of(
                support(relation, "AB", "partner.pace", "self.pace"),
                new Judgment(relation, "AB", "partner.communication", "self.communication",
                        ReasoningCategory.INSUFFICIENT_EVIDENCE, Disposition.INSUFFICIENT,
                        EnumSet.of(ReasoningCategory.UNSUPPORTED_INFERENCE)),
                support(relation, "BA", "partner.pace", "self.pace")));
    }

    private static Case contradiction(String split, int copy) {
        long id = 76000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow")),
                List.of(pref("partner.pace", "slow"), pref("partner.communication", "quiet")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.pace", "slow"),
                        CompatibilitySubject.feature("self.communication", null, FeatureStatus.CONTESTED)),
                List.of(pref("partner.pace", "slow")));
        String relation = split + "-" + copy + "-CONTRADICTION";
        return new Case(relation, split, copy, "CONTRADICTION", 0, seeker, candidate, List.of(
                support(relation, "AB", "partner.pace", "self.pace"),
                new Judgment(relation, "AB", "partner.communication", "self.communication",
                        ReasoningCategory.CONTRADICTION, Disposition.CONTRADICTION, Set.of()),
                support(relation, "BA", "partner.pace", "self.pace")));
    }

    private static Case falseNew(String split, int copy) {
        long id = 77000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow"), self("self.communication", "quiet")),
                List.of(pref("partner.pace", "slow")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.pace", "slow"), self("self.communication", "direct")),
                List.of(pref("partner.pace", "slow")));
        String relation = split + "-" + copy + "-FALSE_NEW";
        return new Case(relation, split, copy, "FALSE_NEW", 2, seeker, candidate, List.of(
                support(relation, "AB", "partner.pace", "self.pace"),
                support(relation, "BA", "partner.pace", "self.pace"),
                new Judgment(relation, "AB", "partner.communication", "self.communication",
                        ReasoningCategory.UNSUPPORTED_INFERENCE, Disposition.MUST_REJECT, Set.of()),
                new Judgment(relation, "BA", "partner.communication", "self.communication",
                        ReasoningCategory.UNSUPPORTED_INFERENCE, Disposition.MUST_REJECT, Set.of())));
    }

    private static Case oneWay(String split, int copy) {
        long id = 78000L + copy;
        CompatibilitySubject seeker = person(id, List.of(self("self.pace", "slow"), self("self.communication", "quiet")),
                List.of(pref("partner.pace", "slow"), pref("partner.communication", "quiet")));
        CompatibilitySubject candidate = person(id + 1, List.of(self("self.pace", "slow"), self("self.communication", "quiet")),
                List.of(pref("partner.pace", "fast"), pref("partner.communication", "frequent")));
        String relation = split + "-" + copy + "-ONE_WAY";
        return new Case(relation, split, copy, "ONE_WAY", 1, seeker, candidate, List.of(
                support(relation, "AB", "partner.pace", "self.pace"),
                support(relation, "AB", "partner.communication", "self.communication"),
                new Judgment(relation, "BA", "partner.pace", "self.pace", ReasoningCategory.POTENTIAL_CONFLICT,
                        Disposition.SUPPORT, Set.of()),
                new Judgment(relation, "BA", "partner.communication", "self.communication",
                        ReasoningCategory.POTENTIAL_CONFLICT, Disposition.SUPPORT, Set.of())));
    }

    private static Judgment support(String relation, String direction, String preference, String target) {
        return new Judgment(relation, direction, preference, target, ReasoningCategory.SUPPORTED_COMPATIBILITY,
                Disposition.SUPPORT, Set.of());
    }

    private static CompatibilitySubject person(long id, List<ProfileFeature> self, List<PreferenceTarget> preferences) {
        return CompatibilitySubject.synthetic(id, id, 1, true, false, SOURCE, self, preferences,
                Candidate.builder(id).hobbies("reading").build());
    }

    private static ProfileFeature self(String code, String value) {
        return CompatibilitySubject.feature(code, new FeatureValue.EnumOne(value), FeatureStatus.CONFIRMED);
    }

    private static PreferenceTarget pref(String code, String value) {
        return CompatibilitySubject.preference(code, value, Importance.SUPER);
    }
}
