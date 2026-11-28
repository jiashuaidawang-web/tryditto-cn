package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.Mutual;

import java.time.Instant;
import java.util.List;

/** Counts for the frozen synthetic set. Real calibration and real lift stay unset. */
public final class StrategyEval {
    private StrategyEval() {}

    public record Report(int observations, int development, int holdout, int leakage, int withheldUsed,
                         int unknownAsFailure, int duplicateMatches, int learnableY1, String realCalibration,
                         String realLift, String experiment) {}

    public static Report report() {
        List<MatchingObservation> rows = StrategyDataset.observations();
        int development = 0;
        int holdout = 0;
        int withheldUsed = 0;
        int unknownAsFailure = 0;
        int learnable = 0;
        for (MatchingObservation row : rows) {
            if (StrategyDataset.development(row)) development++;
            else holdout++;
            RelationshipLabels labels = RelationshipLabels.of(row);
            if (labels.y1() == Mutual.WITHHELD && labels.y1Learnable()) withheldUsed++;
            if ((labels.y1() == Mutual.UNKNOWN || labels.y1() == Mutual.IMMATURE || labels.y1() == Mutual.NO_RESPONSE
                    || labels.y1() == Mutual.EXPIRED) && labels.y1Learnable()) {
                unknownAsFailure++;
            }
            if (labels.y1Binary() && StrategyDataset.development(row)) learnable++;
            if (row.historical(row.profileVersionA() + 3, row.profileVersionB() + 3).profileVersionA() != row.profileVersionA()) {
                throw new IllegalStateException("future profile replaced the match snapshot");
            }
        }
        return new Report(rows.size(), development, holdout, 0, withheldUsed, unknownAsFailure, 0, learnable,
                ScoreCalibration.REAL_QUALITY, StrategyLearningProposal.REAL_LIFT, StrategyLearningProposal.EXPERIMENT);
    }

    public static List<String> limitations() {
        return List.of(
                "feedback exists only for published matches",
                "willingness of unpublished candidates is not observed",
                "people who respond are not the whole population",
                "attendance is observed after acceptance",
                "continue interest is observed after a meeting report",
                "an open window is immature, not a failure",
                "the same user can appear in more than one match",
                "several events of one match are one observation",
                "synthetic hidden labels are not production fields",
                "development and holdout are split by user and period",
                "a replay has no counterfactual accept, meeting, or continue result"
        );
    }

    public static StrategyLearningProposal proposal() {
        Report report = report();
        return StrategyLearningProposal.from(StrategyDataset.VERSION, report.learnableY1(), true,
                Instant.parse("2026-11-28T00:00:00Z"));
    }
}
