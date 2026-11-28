package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.Mutual;

import java.util.ArrayList;
import java.util.List;

/**
 * Published observations can be counted. A strategy that was not published does not receive invented outcomes.
 * Real lift is not identified from a replay.
 */
public final class StrategyComparison {
    private StrategyComparison() {}

    public static final String[] PUBLISHED = {
            "E+G1", "E+G2", "E+G3", "E+G4", "F", "M02-B5"
    };

    public record Row(String strategy, int published, int bothAccepted, int bothDeclined, int notBinary,
                      boolean counterfactual, String realLift) {}

    public static List<Row> compare(List<MatchingObservation> rows) {
        List<Row> result = new ArrayList<>();
        for (String strategy : PUBLISHED) {
            List<MatchingObservation> matched = rows.stream()
                    .filter(row -> row.candidateStrategyVersion().equals(strategy)).toList();
            int accepted = 0;
            int declined = 0;
            int other = 0;
            for (MatchingObservation row : matched) {
                Mutual label = RelationshipLabels.of(row).y1();
                if (label == Mutual.BOTH_ACCEPTED) accepted++;
                else if (label == Mutual.BOTH_DECLINED) declined++;
                else other++;
            }
            result.add(new Row(strategy, matched.size(), accepted, declined, other, false,
                    StrategyLearningProposal.REAL_LIFT));
        }
        return List.copyOf(result);
    }
}
