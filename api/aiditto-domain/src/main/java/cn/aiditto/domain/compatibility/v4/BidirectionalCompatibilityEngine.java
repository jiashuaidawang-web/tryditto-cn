package cn.aiditto.domain.compatibility.v4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Offline bidirectional fit. Authorization and hard constraints run before any profile row is produced.
 * This class does not call the production scorer or a model, and it does not invent a matching history.
 */
public final class BidirectionalCompatibilityEngine {
    public static final java.time.LocalDate WEEK = java.time.LocalDate.of(2026, 11, 25);
    public static final java.time.Instant NOW = java.time.Instant.parse("2026-11-27T06:00:00Z");

    private final DirectionalCompatibilityScorer scorer;
    private final FeatureAlignmentRegistry alignments;

    public BidirectionalCompatibilityEngine(FeatureAlignmentRegistry alignments) {
        this.alignments = alignments;
        this.scorer = new DirectionalCompatibilityScorer(alignments);
    }

    public static BidirectionalCompatibilityEngine v1() {
        return new BidirectionalCompatibilityEngine(FeatureAlignmentRegistry.v1());
    }

    public CompatibilityDecision decide(CompatibilitySubject left, CompatibilitySubject right, CompatibilityContext context) {
        return CompatibilityDecision.from(score(left, right, context));
    }

    public PairCompatibilityResult score(CompatibilitySubject left, CompatibilitySubject right, CompatibilityContext context) {
        List<String> blocked = context.blockReasons(left, right);
        if (!blocked.isEmpty()) {
            CompatibilityStatus status = statusForBlock(blocked);
            return publish(left, right, blocked, null, null, null, null, status,
                    empty(left, right, "AB", status), empty(right, left, "BA", status));
        }
        DirectionalCompatibilityResult ab = scorer.score(left, right, "AB");
        DirectionalCompatibilityResult ba = scorer.score(right, left, "BA");
        return publish(left, right, List.of(), ab.score(), ba.score(), ab.coverage(), ba.coverage(), statusOf(ab, ba), ab, ba);
    }

    private static CompatibilityStatus statusForBlock(List<String> blocked) {
        if (blocked.stream().anyMatch(code -> code.equals("PROFILE_STALE") || code.equals("PROFILE_WITHDRAWN"))) {
            return CompatibilityStatus.NOT_CURRENT;
        }
        if (blocked.stream().anyMatch(code -> code.startsWith("HARD_"))) return CompatibilityStatus.HARD_VETO;
        return CompatibilityStatus.NOT_ELIGIBLE;
    }

    private PairCompatibilityResult publish(CompatibilitySubject left, CompatibilitySubject right, List<String> blocked,
                                            Double scoreAB, Double scoreBA, Double coverageAB, Double coverageBA,
                                            CompatibilityStatus status, DirectionalCompatibilityResult ab,
                                            DirectionalCompatibilityResult ba) {
        boolean sealed = status == CompatibilityStatus.HARD_VETO
                || status == CompatibilityStatus.NOT_CURRENT
                || status == CompatibilityStatus.NOT_ELIGIBLE;
        List<FeatureMatchContribution> rows = new ArrayList<>();
        if (!sealed) {
            rows.addAll(ab.contributions());
            rows.addAll(ba.contributions());
            rows.sort(Comparator.comparing(FeatureMatchContribution::direction).thenComparing(FeatureMatchContribution::preferenceCode));
        }
        List<String> conflicts = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (FeatureMatchContribution row : rows) {
            if (row.satisfaction() == Satisfaction.UNSATISFIED && "SUPER".equals(row.importance())) {
                conflicts.add(row.direction() + ":" + row.preferenceCode());
            }
            if (row.satisfaction() == Satisfaction.UNKNOWN) unknown.add(row.direction() + ":" + row.preferenceCode());
        }
        Double strongAB = sealed ? null : ab.evidence().strongSatisfaction();
        Double strongBA = sealed ? null : ba.evidence().strongSatisfaction();
        if (sealed) {
            scoreAB = null;
            scoreBA = null;
            coverageAB = null;
            coverageBA = null;
            ab = empty(left, right, "AB", status);
            ba = empty(right, left, "BA", status);
        }
        return new PairCompatibilityResult(
                left.userId(), right.userId(), left.profileVersion(), right.profileVersion(),
                left.generation(), right.generation(), blocked,
                scoreAB, scoreBA, coverageAB, coverageBA,
                CompatibilityAggregator.arithmetic(scoreAB, scoreBA),
                CompatibilityAggregator.harmonic(scoreAB, scoreBA),
                CompatibilityAggregator.lowest(scoreAB, scoreBA),
                CompatibilityAggregator.geometric(scoreAB, scoreBA),
                CompatibilityAggregator.harmonicWithCoverage(scoreAB, scoreBA, coverageAB, coverageBA),
                CompatibilityAggregator.harmonicWithCoverageAndStrength(scoreAB, scoreBA, coverageAB, coverageBA, strongAB, strongBA),
                conflicts, unknown, rows,
                CompatibilityFormula.VERSION, alignments.featureRegistryVersion(), alignments.version(),
                status, ab, ba);
    }

    private static DirectionalCompatibilityResult empty(CompatibilitySubject chooser, CompatibilitySubject target,
                                                        String direction, CompatibilityStatus status) {
        EvidenceCoverage evidence = new EvidenceCoverage(0, 0, 0, 0, null, null, null, 0);
        return new DirectionalCompatibilityResult(chooser.userId(), target.userId(), direction, null, null,
                evidence, List.of(), 0, 0, status);
    }

    private static CompatibilityStatus statusOf(DirectionalCompatibilityResult ab, DirectionalCompatibilityResult ba) {
        if (ab.status() == CompatibilityStatus.NO_PREFERENCE && ba.status() == CompatibilityStatus.NO_PREFERENCE) {
            return CompatibilityStatus.NO_PREFERENCE;
        }
        if (ab.score() == null || ba.score() == null) return CompatibilityStatus.INSUFFICIENT_EVIDENCE;
        return CompatibilityStatus.SCORED;
    }
}
