package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.HardRule;
import cn.aiditto.domain.matching.MatchingInput;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Offline bidirectional fit. Hard constraints stay in {@link HardFilter}.
 * This class does not call the production {@code Scorer} and does not call a model.
 */
public final class BidirectionalCompatibilityEngine {
    public static final LocalDate WEEK = LocalDate.of(2026, 11, 25);

    private final DirectionalCompatibilityScorer scorer;
    private final FeatureAlignmentRegistry alignments;

    public BidirectionalCompatibilityEngine(FeatureAlignmentRegistry alignments) {
        this.alignments = alignments;
        this.scorer = new DirectionalCompatibilityScorer(alignments);
    }

    public static BidirectionalCompatibilityEngine v1() {
        return new BidirectionalCompatibilityEngine(FeatureAlignmentRegistry.v1());
    }

    public PairCompatibilityResult score(CompatibilitySubject left, CompatibilitySubject right) {
        DirectionalCompatibilityResult ab = scorer.score(left, right, "AB");
        DirectionalCompatibilityResult ba = scorer.score(right, left, "BA");
        List<String> hard = new ArrayList<>();
        if (!left.current() || left.withdrawn() || !right.current() || right.withdrawn()) {
            return publish(left, right, hard, null, null, null, null, CompatibilityStatus.NOT_CURRENT, ab, ba);
        }
        MatchingInput input = MatchingInput.of(WEEK, List.of(left.candidate(), right.candidate()), 1);
        for (HardRule rule : HardFilter.violations(left.candidate(), right.candidate(), input)) hard.add(rule.name());
        if (!hard.isEmpty()) {
            return publish(left, right, hard, null, null, null, null, CompatibilityStatus.HARD_VETO, ab, ba);
        }
        CompatibilityStatus status = statusOf(ab, ba);
        Double scoreAB = status == CompatibilityStatus.SCORED || ab.score() != null ? ab.score() : null;
        Double scoreBA = status == CompatibilityStatus.SCORED || ba.score() != null ? ba.score() : null;
        if (status != CompatibilityStatus.SCORED && status != CompatibilityStatus.INSUFFICIENT_EVIDENCE) {
            scoreAB = ab.status() == CompatibilityStatus.SCORED ? ab.score() : null;
            scoreBA = ba.status() == CompatibilityStatus.SCORED ? ba.score() : null;
        }
        Double coverageAB = ab.coverage();
        Double coverageBA = ba.coverage();
        if (ab.status() == CompatibilityStatus.NOT_CURRENT) coverageAB = null;
        if (ba.status() == CompatibilityStatus.NOT_CURRENT) coverageBA = null;
        return publish(left, right, hard, scoreAB, scoreBA, coverageAB, coverageBA, status, ab, ba);
    }

    private PairCompatibilityResult publish(CompatibilitySubject left, CompatibilitySubject right, List<String> hard,
                                            Double scoreAB, Double scoreBA, Double coverageAB, Double coverageBA,
                                            CompatibilityStatus status, DirectionalCompatibilityResult ab,
                                            DirectionalCompatibilityResult ba) {
        List<FeatureMatchContribution> rows = new ArrayList<>();
        rows.addAll(ab.contributions());
        rows.addAll(ba.contributions());
        rows.sort(Comparator.comparing(FeatureMatchContribution::direction).thenComparing(FeatureMatchContribution::preferenceCode));
        List<String> conflicts = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (FeatureMatchContribution row : rows) {
            if (row.satisfaction() == Satisfaction.UNSATISFIED && "SUPER".equals(row.importance())) {
                conflicts.add(row.direction() + ":" + row.preferenceCode());
            }
            if (row.satisfaction() == Satisfaction.UNKNOWN) unknown.add(row.direction() + ":" + row.preferenceCode());
        }
        Double strongAB = ab.evidence().strongSatisfaction();
        Double strongBA = ba.evidence().strongSatisfaction();
        return new PairCompatibilityResult(
                left.userId(), right.userId(), left.profileVersion(), right.profileVersion(),
                left.generation(), right.generation(), hard,
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

    private static CompatibilityStatus statusOf(DirectionalCompatibilityResult ab, DirectionalCompatibilityResult ba) {
        if (ab.status() == CompatibilityStatus.NO_PREFERENCE && ba.status() == CompatibilityStatus.NO_PREFERENCE) {
            return CompatibilityStatus.NO_PREFERENCE;
        }
        if (ab.score() == null || ba.score() == null) return CompatibilityStatus.INSUFFICIENT_EVIDENCE;
        return CompatibilityStatus.SCORED;
    }
}
