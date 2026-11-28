package cn.aiditto.shadow;

import cn.aiditto.domain.compatibility.v4.BusinessFeatureCoverage;
import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.v4.GlobalBudgets;
import cn.aiditto.domain.matching.v4.GlobalCandidateGraphBuilder;
import cn.aiditto.domain.matching.v4.GlobalMatchingGraph;
import cn.aiditto.domain.matching.v4.GlobalMatchingInput;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.matching.v4.V4GlobalMatchingEngine;
import cn.aiditto.domain.matching.v4.PreparedEdge;
import cn.aiditto.domain.matching.v4.ShadowResourceBudget;
import cn.aiditto.integration.ProductIntegrationSettings;
import cn.aiditto.match.MatchDb;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Isolated E + B5 + G2 run. It does not call the week runner, publish guard, notification, or match insert.
 * The advisory lock is class 13 and is taken before any user-row lock. Account deletion takes the user
 * row and then class 11, so the two orders do not form a cycle. Blossom is not cancelled from here.
 */
@Service
public class V4ShadowRunner {
    public static final String STRATEGY = "recall-rank-1";
    private static final Pattern RUN_ID = Pattern.compile("[a-z][a-z0-9.]{0,40}");

    private final ProductIntegrationSettings settings;
    private final ProductMatchingPoolReader reader;
    private final ProductCompatibilitySubjectAdapter adapter;
    private final ShadowRunStore store;
    private final MatchDb matches;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final cn.aiditto.consent.MatchingConsentService consents;
    private final String commitSha;
    private volatile Runnable beforeCommit;
    private volatile CountDownLatch overlapStarted;
    private volatile CountDownLatch overlapRelease;

    public V4ShadowRunner(ProductIntegrationSettings settings, ProductMatchingPoolReader reader,
                          ProductCompatibilitySubjectAdapter adapter, ShadowRunStore store, MatchDb matches,
                          JdbcTemplate jdbc, cn.aiditto.consent.MatchingConsentService consents,
                          @Value("${aiditto.match-lab.commit-sha:UNSPECIFIED}") String commitSha) {
        this.settings = settings;
        this.reader = reader;
        this.adapter = adapter;
        this.store = store;
        this.matches = matches;
        this.jdbc = jdbc;
        this.consents = consents;
        this.commitSha = commitSha == null || commitSha.isBlank() ? "UNSPECIFIED" : commitSha;
    }

    public void beforeCommitForTest(Runnable hook) {
        if (!settings.shadowTest()) throw new IllegalStateException("shadow runner is closed");
        this.beforeCommit = hook;
    }

    public void overlapForTest(CountDownLatch started, CountDownLatch release) {
        if (!settings.shadowTest()) throw new IllegalStateException("shadow runner is closed");
        this.overlapStarted = started;
        this.overlapRelease = release;
    }

    public ShadowView run(ShadowRequest request) {
        if (!settings.shadowTest()) throw new IllegalStateException("shadow runner is closed");
        validate(request);
        Instant started = Instant.now();
        ShadowView[] box = new ShadowView[1];
        Boolean ran = jdbc.execute((Connection connection) -> {
            if (!tryLock(connection)) return Boolean.FALSE;
            try {
                box[0] = execute(request, started);
                return Boolean.TRUE;
            } finally {
                unlock(connection);
            }
        });
        if (!Boolean.TRUE.equals(ran)) {
            return closed(request.matchingRunId(), "CONCURRENCY", "BUSY");
        }
        return box[0];
    }

    private ShadowView execute(ShadowRequest request, Instant started) {
        awaitOverlap();
        ShadowResourceBudget budget = request.budget() == null ? ShadowResourceBudget.isolatedDefault() : request.budget();
        ProductMatchingPoolReader.PoolRead pool = reader.read(request.city(), request.matchingPeriod(), request.sourceCutoff(), request.cohort());
        String document = ShadowText.document(request, pool, commitSha);
        String hash = ShadowText.hash(document);
        if (document.length() > budget.maxResultBytes()) return refuse(request, started, pool, document, hash, "REJECTED", "RESULT_CAP");
        ShadowView existing = store.find(request.matchingRunId());
        if (existing != null) {
            if (existing.inputHash().equals(hash)) return existing;
            return new ShadowView(request.matchingRunId(), "REJECTED", false, existing.inputHash(), existing.resultHash(),
                    existing.eligibleUsers(), existing.legalEdges(), existing.recommendedEdges(), existing.matchedPairs(),
                    existing.unmatchedUsers(), existing.safetyViolations(), "INPUT_CHANGED", existing.comparisonJson());
        }
        if (pool.city().size() >= ShadowResourceBudget.UNPROTECTED_USER_FLOOR) {
            return refuse(request, started, pool, document, hash, "REJECTED", "UNPROTECTED_SCALE");
        }
        String cap = budget.refuseUsers(pool.eligible().size());
        if (cap != null) return refuse(request, started, pool, document, hash, "REJECTED", cap);
        if (pool.eligible().size() < 2) {
            return refuse(request, started, pool, document, hash, "REJECTED", pool.eligible().isEmpty() ? "EMPTY_POOL" : "SINGLE_USER");
        }
        List<CompatibilitySubject> subjects = new ArrayList<>();
        Map<Long, Long> versions = new LinkedHashMap<>();
        Map<Long, Long> generations = new LinkedHashMap<>();
        Map<Long, String> consent = new LinkedHashMap<>();
        Map<Long, Integer> streaks = new LinkedHashMap<>();
        Map<Long, String> frozenHash = new LinkedHashMap<>();
        for (ProductMatchingPoolReader.Eligible row : pool.eligible()) {
            CompatibilitySubject subject = adapter.adapt(row.member(), row.snapshot(), row.version().version(), row.generation());
            subjects.add(subject);
            versions.put(subject.userId(), subject.profileVersion());
            generations.put(subject.userId(), subject.generation());
            consent.put(subject.userId(), "GRANTED");
            streaks.put(subject.userId(), subject.candidate().emptyWeekStreak());
            frozenHash.put(subject.userId(), row.version().inputHash());
        }
        Set<PairKey> past = pairKeys(matches.pastPairs());
        Set<PairKey> forbidden = pairKeys(matches.reportedPairs());
        forbidden.addAll(pairKeys(matches.forbiddenPairs()));
        GlobalMatchingInput input = new GlobalMatchingInput(request.matchingRunId(), request.matchingPeriod(), request.cohort(),
                subjects, versions, generations, consent, Set.of(), forbidden, past, STRATEGY, CompatibilityPolicies.VERSION,
                Objective.SELECTED_VERSION, streaks, request.seed());
        long deadline = System.nanoTime() + budget.maxWallMs() * 1_000_000L;
        GlobalMatchingGraph graph = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL,
                deadline, Math.min(budget.maxQualifiedEdges(), GlobalBudgets.MAX_SOLVER_EDGES));
        int legal = legalEdges(graph);
        int recommended = graph.solver().size();
        if (!graph.fullEligibleGraph()) {
            return refuse(request, started, pool, document, hash, "ABORTED", "GRAPH_" + graph.coverage().name(), legal, recommended);
        }
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, Objective.SELECTED, deadline);
        if (!result.optimalForFullEligibleGraph()) {
            return refuse(request, started, pool, document, hash, "ABORTED", "SOLVE_" + result.status(), legal, recommended);
        }
        Runnable hook = beforeCommit;
        beforeCommit = null;
        if (hook != null) hook.run();
        BusinessFeatureCoverage.Report covered = ShadowText.coverage(pool);
        String comparison = ShadowComparison.write(pool, subjects, result, past, forbidden, consents, request.matchingPeriod(),
                request.seed(), deadline, exclusionCounts(pool), ShadowText.coverageJson(covered, recommended, legal));
        String resultHash = resultHash(result.pairs());
        int matchesBefore = count("\"match\"");
        int weeksBefore = count("week");
        int copiesBefore = count("match_copy");
        ShadowRunStore.Draft draft = draft(request, started, document, hash, resultHash, "COMPLETE", pool.eligible().size(),
                legal, recommended, result.pairs().size(), result.unmatched().size(), graph.coverage().name(), null, comparison);
        return store.storePlan(draft, graph, result.pairs(), frozenHash, matchesBefore, weeksBefore, copiesBefore);
    }

    private ShadowView refuse(ShadowRequest request, Instant started, ProductMatchingPoolReader.PoolRead pool,
                              String document, String hash, String status, String reason) {
        return refuse(request, started, pool, document, hash, status, reason, 0, 0);
    }

    private ShadowView refuse(ShadowRequest request, Instant started, ProductMatchingPoolReader.PoolRead pool,
                              String document, String hash, String status, String reason, int legal, int recommended) {
        ShadowRunStore.Draft draft = draft(request, started, document, hash, ShadowText.hash(""), status, pool.eligible().size(),
                legal, recommended, 0, pool.eligible().size(), "NONE", reason, ShadowText.emptyComparison(reason));
        return store.storeRefused(draft);
    }

    private ShadowRunStore.Draft draft(ShadowRequest request, Instant started, String document, String hash, String resultHash,
                                       String status, int eligible, int legal, int recommended, int pairs, int unmatched,
                                       String coverage, String reason, String comparison) {
        long elapsed = java.time.Duration.between(started, Instant.now()).toMillis();
        return new ShadowRunStore.Draft(request.matchingRunId(), hash, resultHash, started, status, request.city(),
                request.cohort(), request.matchingPeriod(), request.sourceCutoff(), eligible, legal, recommended, pairs,
                unmatched, coverage, CompatibilityPolicies.VERSION, Objective.SELECTED_VERSION, STRATEGY, commitSha,
                elapsed, reason, document, comparison);
    }

    private void awaitOverlap() {
        CountDownLatch started = overlapStarted;
        CountDownLatch release = overlapRelease;
        overlapStarted = null;
        overlapRelease = null;
        if (started == null || release == null) return;
        started.countDown();
        try {
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("shadow overlap wait expired");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("shadow overlap interrupted");
        }
    }

    private static void validate(ShadowRequest request) {
        if (request == null || request.matchingRunId() == null || !RUN_ID.matcher(request.matchingRunId()).matches()) {
            throw new IllegalArgumentException("shadow run id");
        }
        if (request.matchingPeriod() == null || request.city() == null || request.city().isBlank()
                || request.cohort() == null || request.cohort().isBlank() || request.sourceCutoff() == null) {
            throw new IllegalArgumentException("shadow scope");
        }
        if (!STRATEGY.equals(request.candidateStrategyVersion())
                || !CompatibilityPolicies.VERSION.equals(request.compatibilityPolicyVersion())
                || !Objective.SELECTED_VERSION.equals(request.globalObjectiveVersion())) {
            throw new IllegalArgumentException("shadow version is frozen");
        }
    }

    private static int legalEdges(GlobalMatchingGraph graph) {
        int legal = 0;
        for (PreparedEdge edge : graph.diagnostic()) {
            if (edge.decision() == null) continue;
            boolean hard = edge.decision().pair().hardFilterResult().stream().anyMatch(code -> code.startsWith("HARD_"));
            if (!hard) legal++;
        }
        return legal;
    }

    private static String resultHash(List<GlobalMatchingResult.MatchedPair> pairs) {
        StringBuilder raw = new StringBuilder();
        for (GlobalMatchingResult.MatchedPair pair : pairs) {
            raw.append(pair.lowId()).append(',').append(pair.highId()).append(',').append(pair.optimizationWeight()).append(';');
        }
        return ShadowText.hash(raw.toString());
    }

    private static Set<PairKey> pairKeys(List<long[]> rows) {
        Set<PairKey> keys = new LinkedHashSet<>();
        for (long[] row : rows) {
            if (row[0] == row[1]) continue;
            keys.add(PairKey.of(row[0], row[1]));
        }
        return keys;
    }

    private static Map<String, Integer> exclusionCounts(ProductMatchingPoolReader.PoolRead pool) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ProductMatchingPoolReader.Exclusion exclusion : pool.excluded()) {
            counts.merge(exclusion.reason().name(), 1, Integer::sum);
        }
        return counts;
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static ShadowView closed(String runId, String status, String reason) {
        return new ShadowView(runId, status, false, "", "", 0, 0, 0, 0, 0, 0, reason, ShadowText.emptyComparison(reason));
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(13, 1)");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getBoolean(1);
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(13, 1)");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
        }
    }
}
