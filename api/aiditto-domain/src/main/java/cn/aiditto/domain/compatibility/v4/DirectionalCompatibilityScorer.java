package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.MatchingFeatureGate;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.SemanticSupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scores chooser.preference against target.self. It does not look at the reverse direction. */
public final class DirectionalCompatibilityScorer {
    private final FeatureAlignmentRegistry alignments;

    public DirectionalCompatibilityScorer(FeatureAlignmentRegistry alignments) {
        this.alignments = alignments;
    }

    public DirectionalCompatibilityResult score(CompatibilitySubject chooser, CompatibilitySubject target, String direction) {
        if (!chooser.current() || chooser.withdrawn() || !target.current() || target.withdrawn()) {
            return empty(chooser, target, direction, CompatibilityStatus.NOT_CURRENT);
        }
        if (chooser.preference().targets().isEmpty()) {
            return empty(chooser, target, direction, CompatibilityStatus.NO_PREFERENCE);
        }
        List<FeatureMatchContribution> rows = new ArrayList<>();
        int withheld = 0;
        int dropped = 0;
        for (PreferenceTarget preference : chooser.preference().targets()) {
            Row row = admit(chooser, target, direction, preference);
            if (row.kind == Kind.WITHHELD) withheld++;
            else if (row.kind == Kind.DROPPED) dropped++;
            else rows.add(row.contribution);
        }
        rows.sort(Comparator.comparing(FeatureMatchContribution::preferenceCode));
        EvidenceCoverage evidence = cover(rows);
        CompatibilityStatus status = evidence.knownScore() == null
                ? CompatibilityStatus.INSUFFICIENT_EVIDENCE : CompatibilityStatus.SCORED;
        return new DirectionalCompatibilityResult(chooser.userId(), target.userId(), direction,
                evidence.knownScore(), evidence.coverage(), evidence, rows, withheld, dropped, status);
    }

    private Row admit(CompatibilitySubject chooser, CompatibilitySubject target, String direction, PreferenceTarget preference) {
        SemanticSupport support = chooser.preferenceSupport().get(preference.featureCode());
        if (preference.status() == FeatureStatus.PROVISIONAL || !MatchingFeatureGate.eligibleForOfficialScore(support)) {
            return Row.dropped();
        }
        if (preference.targetValue() instanceof FeatureValue.Withheld) return Row.withheld();
        if (preference.status() == FeatureStatus.CONTESTED) {
            return Row.kept(row(direction, preference, null, null, Satisfaction.CONTESTED, "CONTESTED", preference.featureCode()));
        }
        if (preference.constraintType() == ConstraintType.UNBOUNDED || preference.targetValue() instanceof FeatureValue.Unbounded) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "UNBOUNDED", preference.featureCode()));
        }
        if (preference.constraintType() == ConstraintType.DOCUMENTED_EXISTING_RULE) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "DOCUMENTED_EXISTING_RULE", preference.featureCode()));
        }
        if (preference.operator() == Operator.ANY) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "ANY_IS_NOT_A_MATCH", preference.featureCode()));
        }
        if (preference.operator() == Operator.CONTROLLED_REF || preference.targetValue() instanceof FeatureValue.ControlledRef) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "CONTROLLED_REF", preference.featureCode()));
        }
        FeatureAlignment alignment = alignments.find(preference.featureCode());
        if (alignment == null) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "NO_ALIGNMENT", preference.featureCode()));
        }
        if (!legalPreference(preference)) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE", alignment.selfCode()));
        }
        ProfileFeature actual = target.self().features().stream()
                .filter(feature -> alignment.selfCode().equals(feature.featureCode())).findFirst().orElse(null);
        if (actual == null) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.UNKNOWN, "TARGET_MISSING", alignment.selfCode()));
        }
        SemanticSupport actualSupport = target.selfSupport().get(actual.featureCode());
        if (actual.value() instanceof FeatureValue.Withheld) return Row.withheld();
        if (actual.status() == FeatureStatus.PROVISIONAL || !MatchingFeatureGate.eligibleForOfficialScore(actualSupport)) {
            return Row.dropped();
        }
        if (actual.status() == FeatureStatus.CONTESTED) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), null, Satisfaction.CONTESTED, "CONTESTED", alignment.selfCode()));
        }
        if (!legal(actual.featureCode(), actual.value())) {
            return Row.kept(row(direction, preference, text(preference.targetValue()), text(actual.value()), Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE", alignment.selfCode()));
        }
        Compared compared = compare(preference.operator(), preference.targetValue(), actual.value(), alignment);
        return Row.kept(row(direction, preference, text(preference.targetValue()), text(actual.value()), compared.satisfaction, compared.reason, alignment.selfCode()));
    }

    private boolean legal(String code, FeatureValue value) {
        try {
            FeatureRegistry registry = alignments.features();
            registry.checkValue(registry.require(code), value);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** A preference may name several acceptable enum values. That set is not itself one enum value. */
    private boolean legalPreference(PreferenceTarget preference) {
        FeatureValue value = preference.targetValue();
        if (preference.operator() == Operator.IN && value instanceof FeatureValue.EnumMany many) {
            return alignments.features().require(preference.featureCode()).allowedValues().containsAll(many.values());
        }
        return legal(preference.featureCode(), value);
    }

    private static Compared compare(Operator operator, FeatureValue expected, FeatureValue actual, FeatureAlignment alignment) {
        return switch (operator) {
            case EQ -> exact(expected, actual, alignment);
            case IN -> membership(expected, actual);
            case BETWEEN -> between(expected, actual);
            case AT_LEAST -> atLeast(expected, actual);
            default -> new Compared(Satisfaction.NOT_APPLICABLE, "OPERATOR_NOT_SCORED");
        };
    }

    private static Compared exact(FeatureValue expected, FeatureValue actual, FeatureAlignment alignment) {
        if (!(expected instanceof FeatureValue.EnumOne want) || !(actual instanceof FeatureValue.EnumOne got)) {
            return new Compared(Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE");
        }
        if (want.value().equals(got.value())) return new Compared(Satisfaction.SATISFIED, "EXACT");
        if (!alignment.allowsPartial()) return new Compared(Satisfaction.UNSATISFIED, "MISMATCH");
        int left = alignment.ordinal().indexOf(want.value());
        int right = alignment.ordinal().indexOf(got.value());
        if (left < 0 || right < 0) return new Compared(Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE");
        if (Math.abs(left - right) == 1) return new Compared(Satisfaction.PARTIALLY_SATISFIED, "ADJACENT");
        return new Compared(Satisfaction.UNSATISFIED, "MISMATCH");
    }

    private static Compared membership(FeatureValue expected, FeatureValue actual) {
        if (!(expected instanceof FeatureValue.EnumMany allowed) || !(actual instanceof FeatureValue.EnumOne got)) {
            return new Compared(Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE");
        }
        return allowed.values().contains(got.value())
                ? new Compared(Satisfaction.SATISFIED, "IN_SET")
                : new Compared(Satisfaction.UNSATISFIED, "MISMATCH");
    }

    private static Compared between(FeatureValue expected, FeatureValue actual) {
        if (!(expected instanceof FeatureValue.Span span) || !(actual instanceof FeatureValue.Num number)) {
            return new Compared(Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE");
        }
        return number.value() >= span.min() && number.value() <= span.max()
                ? new Compared(Satisfaction.SATISFIED, "IN_RANGE")
                : new Compared(Satisfaction.UNSATISFIED, "MISMATCH");
    }

    private static Compared atLeast(FeatureValue expected, FeatureValue actual) {
        if (!(expected instanceof FeatureValue.Num want) || !(actual instanceof FeatureValue.Num got)) {
            return new Compared(Satisfaction.NOT_APPLICABLE, "ILLEGAL_VALUE");
        }
        return got.value() >= want.value()
                ? new Compared(Satisfaction.SATISFIED, "AT_LEAST")
                : new Compared(Satisfaction.UNSATISFIED, "MISMATCH");
    }

    private static FeatureMatchContribution row(String direction, PreferenceTarget preference, String expected, String actual,
                                                 Satisfaction satisfaction, String reason, String targetCode) {
        Importance importance = preference.importance();
        int weight = importance == null ? 1 : PreferenceWeights.of(importance);
        return new FeatureMatchContribution(direction, preference.featureCode(), targetCode, expected, actual,
                importance == null ? null : importance.name(),
                preference.operator() == null ? null : preference.operator().name(),
                preference.status().name(), satisfaction, weight, satisfaction.value(), reason);
    }

    private static EvidenceCoverage cover(List<FeatureMatchContribution> rows) {
        double knownWeight = 0;
        double knownSatisfaction = 0;
        double unknownWeight = 0;
        double conflictWeight = 0;
        double contestedWeight = 0;
        double strongWeight = 0;
        double strongSatisfaction = 0;
        boolean strongSeen = false;
        int compared = 0;
        for (FeatureMatchContribution row : rows) {
            if (row.satisfaction().known()) {
                knownWeight += row.weight();
                knownSatisfaction += row.satisfactionValue() * row.weight();
                compared++;
                if (row.satisfaction() == Satisfaction.UNSATISFIED) conflictWeight += row.weight();
                if ("SUPER".equals(row.importance())) {
                    strongSeen = true;
                    strongWeight += row.weight();
                    strongSatisfaction += row.satisfactionValue() * row.weight();
                }
            } else if (row.satisfaction() == Satisfaction.UNKNOWN) {
                unknownWeight += row.weight();
            } else if (row.satisfaction() == Satisfaction.CONTESTED) {
                contestedWeight += row.weight();
            }
        }
        Double score = knownWeight == 0 ? null : knownSatisfaction * 100.0 / knownWeight;
        double evaluable = knownWeight + unknownWeight;
        Double coverage = evaluable == 0 ? null : knownWeight / evaluable;
        Double strong = strongSeen ? strongSatisfaction / strongWeight : null;
        return new EvidenceCoverage(knownWeight, unknownWeight, conflictWeight, contestedWeight, score, coverage, strong, compared);
    }

    private static DirectionalCompatibilityResult empty(CompatibilitySubject chooser, CompatibilitySubject target,
                                                        String direction, CompatibilityStatus status) {
        EvidenceCoverage evidence = new EvidenceCoverage(0, 0, 0, 0, null, null, null, 0);
        return new DirectionalCompatibilityResult(chooser.userId(), target.userId(), direction, null, null,
                evidence, List.of(), 0, 0, status);
    }

    private static String text(FeatureValue value) {
        if (value == null) return null;
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        if (value instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        if (value instanceof FeatureValue.Num number) return Double.toString(number.value());
        if (value instanceof FeatureValue.Span span) return span.min() + ".." + span.max();
        if (value instanceof FeatureValue.Bool bool) return Boolean.toString(bool.value());
        if (value instanceof FeatureValue.Text text) return text.value();
        if (value instanceof FeatureValue.Unbounded) return "UNBOUNDED";
        if (value instanceof FeatureValue.Withheld) return "WITHHELD";
        if (value instanceof FeatureValue.ControlledRef ref) return ref.ref();
        return null;
    }

    private enum Kind { KEPT, WITHHELD, DROPPED }

    private record Row(Kind kind, FeatureMatchContribution contribution) {
        static Row kept(FeatureMatchContribution contribution) { return new Row(Kind.KEPT, contribution); }
        static Row withheld() { return new Row(Kind.WITHHELD, null); }
        static Row dropped() { return new Row(Kind.DROPPED, null); }
    }

    private record Compared(Satisfaction satisfaction, String reason) {}
}
