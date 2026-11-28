package cn.aiditto.domain.matching.v4;

/**
 * Limits for one isolated shadow run.
 * Crossing a limit refuses the run. It does not drop edges and still call the remainder optimal.
 * Five thousand people and above stay closed. Blossom itself is not killed by this budget.
 */
public record ShadowResourceBudget(
        int maxUsers,
        int maxCandidateEdges,
        int maxQualifiedEdges,
        long maxWallMs,
        int maxConcurrentRuns,
        int maxQueued,
        long maxResultBytes) {

    public static final int UNPROTECTED_USER_FLOOR = 5_000;

    public ShadowResourceBudget {
        if (maxUsers < 2 || maxUsers >= UNPROTECTED_USER_FLOOR) throw new IllegalArgumentException("user cap");
        if (maxCandidateEdges < 1 || maxQualifiedEdges < 1 || maxQualifiedEdges > GlobalBudgets.MAX_SOLVER_EDGES) {
            throw new IllegalArgumentException("edge cap");
        }
        if (maxWallMs < 1 || maxWallMs > GlobalBudgets.WALL_MS) throw new IllegalArgumentException("wall budget");
        if (maxConcurrentRuns != 1 || maxQueued != 0) throw new IllegalArgumentException("one run, no queue");
        if (maxResultBytes < 1_024 || maxResultBytes > 2_000_000) throw new IllegalArgumentException("result bytes");
    }

    public static ShadowResourceBudget isolatedDefault() {
        return new ShadowResourceBudget(200, GlobalBudgets.MAX_SOLVER_EDGES, GlobalBudgets.MAX_SOLVER_EDGES,
                20_000L, 1, 0, 1_048_576L);
    }

    /** Full-pool candidate count. A larger graph is refused before the solver starts. */
    public String refuseUsers(int users) {
        if (users >= UNPROTECTED_USER_FLOOR) return "UNPROTECTED_SCALE";
        if (users > maxUsers) return "USER_CAP";
        if (users >= 2) {
            long pairs = (long) users * (users - 1) / 2;
            if (pairs > maxCandidateEdges) return "CANDIDATE_CAP";
        }
        return null;
    }
}
