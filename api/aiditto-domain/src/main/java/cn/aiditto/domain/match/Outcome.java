package cn.aiditto.domain.match;

import cn.aiditto.domain.match.FeedbackAnswer.Met;

/** {@code match.outcome} for terminal states, plus the feedback judgement table (PRD §6.5, 技术方案 §5.1). */
public enum Outcome {
    MUTUAL,
    NOT_MUTUAL,
    NO_SHOW,
    DISPUTED,
    NO_FEEDBACK,
    DECLINED,
    EXPIRED,
    UNSCHEDULED,
    CANCELLED,
    HALTED;

    public String dbValue() {
        return name().toLowerCase();
    }

    /** Result of judging two feedback forms; {@code noShowA/B} mark who gets a no-show recorded. */
    public record Judgement(Outcome outcome, boolean noShowA, boolean noShowB) {}

    /**
     * Judges the feedback of sides A and B; {@code null} means that side never submitted.
     * Symmetric: swapping A and B swaps the no-show flags and nothing else.
     *
     * <pre>
     * met+yes   / met+yes   → mutual
     * met       / met       → not_mutual (anyone not "yes")
     * met       / missing   → not_mutual
     * noShow    / iSkipped  → no_show, the skipper is penalised
     * noShow    / missing   → no_show, the silent side is penalised
     * iSkipped  / missing   → no_show, the self-admitted skipper is penalised
     * iSkipped  / iSkipped  → no_show, both penalised
     * noShow    / met|noShow, met / iSkipped → disputed, nobody penalised
     * missing   / missing   → no_feedback
     * </pre>
     */
    public static Judgement judge(FeedbackAnswer a, FeedbackAnswer b) {
        Met ma = a == null ? null : a.met();
        Met mb = b == null ? null : b.met();
        if (ma == null && mb == null) return new Judgement(NO_FEEDBACK, false, false);
        if (ma == null || (mb != null && rank(mb) < rank(ma))) {
            Judgement flipped = judgeOrdered(mb, b, ma, a);
            return new Judgement(flipped.outcome(), flipped.noShowB(), flipped.noShowA());
        }
        return judgeOrdered(ma, a, mb, b);
    }

    /** Orders cases so that {@code x} is the "stronger" claim and never null. */
    private static int rank(Met m) {
        return switch (m) {
            case MET -> 0;
            case NO_SHOW -> 1;
            case I_SKIPPED -> 2;
        };
    }

    private static Judgement judgeOrdered(Met x, FeedbackAnswer fx, Met y, FeedbackAnswer fy) {
        switch (x) {
            case MET -> {
                if (y == null) return new Judgement(NOT_MUTUAL, false, false);
                if (y == Met.MET) {
                    return new Judgement(fx.wantsAgain() && fy.wantsAgain() ? MUTUAL : NOT_MUTUAL, false, false);
                }
                return new Judgement(DISPUTED, false, false);
            }
            case NO_SHOW -> {
                if (y == null || y == Met.I_SKIPPED) return new Judgement(NO_SHOW, false, true);
                return new Judgement(DISPUTED, false, false);
            }
            case I_SKIPPED -> {
                return new Judgement(NO_SHOW, true, y == Met.I_SKIPPED);
            }
            default -> throw new IllegalStateException();
        }
    }
}
