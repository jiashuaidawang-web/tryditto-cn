package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

/** Match-time scores. This object carries no accept, attendance, or continue label. */
public record MatchScoreSnapshot(int scoreAB, int scoreBA, double coverageAB, double coverageBA, int reciprocalScore,
                                 String formula) {
    public MatchScoreSnapshot {
        if (formula == null || formula.isBlank()) throw new FeedbackRejection("SCORE_FORMULA");
        if (scoreAB < 0 || scoreAB > 100 || scoreBA < 0 || scoreBA > 100 || reciprocalScore < 0 || reciprocalScore > 100) {
            throw new FeedbackRejection("SCORE");
        }
        if (!Double.isFinite(coverageAB) || !Double.isFinite(coverageBA) || coverageAB < 0 || coverageAB > 1
                || coverageBA < 0 || coverageBA > 1) {
            throw new FeedbackRejection("COVERAGE");
        }
    }
}
