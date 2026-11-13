package cn.aiditto.domain.matching;

/**
 * Per-component scores for one pair (技术方案 §7.3). {@link #score()} (0–100) is the pair quality that is
 * stored in {@code match.score} and compared against the style threshold. {@link #fairness()} is added
 * only to the solver objective, so it decides who gets a partner but never lets a low-quality pair in.
 */
public record ScoreBreakdown(
        int hobbies,
        int looking,
        int style,
        int identity,
        int budget,
        int age,
        int distance,
        int fairness) {

    public int score() {
        int sum = hobbies + looking + style + identity + budget + age + distance;
        return Math.max(0, Math.min(100, sum));
    }

    /** Objective value before the tie-break: quality plus both people's fairness bonus. */
    public int objective() {
        return score() + fairness;
    }
}
