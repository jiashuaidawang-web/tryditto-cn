package cn.aiditto.shadow;

import cn.aiditto.account.UserLock;
import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.v4.GlobalMatchingGraph;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;
import cn.aiditto.domain.matching.v4.ShadowPlanAudit;
import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.relationship.RelationshipProfileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Writes shadow rows only. It has no method that inserts into {@code match}. */
@Service
public class ShadowRunStore {
    private final JdbcTemplate jdbc;
    private final UserLock userLock;
    private final MatchingConsentService consents;
    private final RelationshipProfileService profiles;

    public ShadowRunStore(JdbcTemplate jdbc, UserLock userLock, MatchingConsentService consents,
                          RelationshipProfileService profiles) {
        this.jdbc = jdbc;
        this.userLock = userLock;
        this.consents = consents;
        this.profiles = profiles;
    }

    public ShadowView find(String runId) {
        List<ShadowView> rows = jdbc.query("""
                SELECT run_id, status, executable, input_hash, result_hash, eligible_user_count, legal_edge_count,
                       recommended_edge_count, matched_pair_count, unmatched_user_count, safety_violation_count,
                       failure_reason, comparison_document::text
                FROM shadow_run WHERE run_id = ?
                """, (rs, n) -> new ShadowView(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11),
                rs.getString(12), rs.getString(13)), runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public void deleteRun(String runId) {
        jdbc.update("DELETE FROM shadow_pair WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM shadow_run WHERE run_id = ?", runId);
    }

    @Transactional
    public ShadowView storeRefused(Draft draft) {
        ShadowView existing = find(draft.runId());
        if (existing != null) return existing;
        insertRun(draft, draft.status(), false, 0, draft.failureReason(), draft.comparisonJson());
        return find(draft.runId());
    }

    @Transactional
    public ShadowView storePlan(Draft draft, GlobalMatchingGraph graph, List<GlobalMatchingResult.MatchedPair> pairs,
                                Map<Long, String> frozenHash, int matchesBefore, int weeksBefore, int copiesBefore) {
        ShadowView existing = find(draft.runId());
        if (existing != null) return existing;
        List<Long> ids = new ArrayList<>(frozenHash.keySet());
        ids.sort(Long::compare);
        for (long id : ids) userLock.lock(id);
        if (count("\"match\"") != matchesBefore || count("week") != weeksBefore || count("match_copy") != copiesBefore) {
            insertRun(draft, "SAFETY_REJECTED", false, 1, "PRODUCTION_WRITE", ShadowText.emptyComparison("PRODUCTION_WRITE"));
            return find(draft.runId());
        }
        Map<Long, ShadowPlanAudit.Live> live = new LinkedHashMap<>();
        for (long id : ids) live.put(id, reread(id));
        Map<String, Integer> safety = ShadowPlanAudit.audit(graph, pairs, live, frozenHash);
        int violations = ShadowPlanAudit.total(safety);
        if (violations > 0) {
            insertRun(draft, "SAFETY_REJECTED", false, violations, "SAFETY", ShadowText.emptyComparison("SAFETY"));
            return find(draft.runId());
        }
        for (ShadowPlanAudit.Live row : live.values()) {
            if (!row.active() || !"GRANTED".equals(row.consent()) || !frozenHash.get(row.userId()).equals(row.inputHash())) {
                insertRun(draft, "EXPIRED", false, 0, "LIFECYCLE", ShadowText.emptyComparison("LIFECYCLE"));
                return find(draft.runId());
            }
        }
        insertRun(draft, "COMPLETE", true, 0, null, draft.comparisonJson());
        for (GlobalMatchingResult.MatchedPair pair : pairs) {
            var edge = graph.solverByKey().get(pair.key());
            jdbc.update("""
                    INSERT INTO shadow_pair
                        (run_id, low_user_id, high_user_id, low_profile_version, high_profile_version,
                         low_generation, high_generation, score_low_to_high, score_high_to_low,
                         coverage_low_to_high, coverage_high_to_low, recommendation_eligible,
                         optimization_weight, safety_status, pair_status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,TRUE,?,'PASS','SHADOW')
                    """, draft.runId(), pair.lowId(), pair.highId(), edge.versionLow(), edge.versionHigh(),
                    edge.generationLow(), edge.generationHigh(), pair.scoreLowToHigh(), pair.scoreHighToLow(),
                    pair.coverageLowToHigh(), pair.coverageHighToLow(), pair.optimizationWeight());
        }
        return find(draft.runId());
    }

    private ShadowPlanAudit.Live reread(long userId) {
        try {
            String status = jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId);
            MatchingConsent consent = consents.current(userId);
            if (!"active".equals(status) || !profiles.processingOpen(userId)) {
                return new ShadowPlanAudit.Live(userId, false, consent.consentStatus().name(), 0, 0, "");
            }
            Optional<ProfileVersion> current = profiles.current(userId);
            if (current.isEmpty()) return new ShadowPlanAudit.Live(userId, false, consent.consentStatus().name(), 0, 0, "");
            return new ShadowPlanAudit.Live(userId, true, consent.consentStatus().name(), profiles.storedGeneration(userId),
                    current.get().version(), current.get().inputHash());
        } catch (DomainException ex) {
            return new ShadowPlanAudit.Live(userId, false, "CLOSED", 0, 0, "");
        }
    }

    private void insertRun(Draft draft, String status, boolean executable, int violations, String failure, String comparison) {
        Instant completed = Instant.now();
        jdbc.update("""
                INSERT INTO shadow_run
                    (run_id, input_hash, result_hash, started_at, completed_at, status, executable, city, cohort,
                     matching_period, source_cutoff, eligible_user_count, legal_edge_count, recommended_edge_count,
                     matched_pair_count, unmatched_user_count, safety_violation_count, candidate_graph_coverage,
                     scoring_version, objective_version, candidate_strategy_version, commit_sha, execution_ms,
                     failure_reason, input_document, comparison_document)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                """, draft.runId(), draft.inputHash(), executable ? draft.resultHash() : ShadowText.hash(""),
                Timestamp.from(draft.startedAt()),
                Timestamp.from(completed), status, executable, draft.city(), draft.cohort(),
                java.sql.Date.valueOf(draft.period()), Timestamp.from(draft.cutoff()), draft.eligibleUsers(),
                draft.legalEdges(), draft.recommendedEdges(), executable ? draft.matchedPairs() : 0,
                draft.unmatchedUsers(), violations, draft.coverage(), draft.scoringVersion(), draft.objectiveVersion(),
                draft.strategyVersion(), draft.commitSha(), draft.executionMs(), trim(failure),
                draft.inputDocument(), comparison);
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String trim(String reason) {
        if (reason == null) return null;
        return reason.length() <= 200 ? reason : reason.substring(0, 200);
    }

    public record Draft(
            String runId,
            String inputHash,
            String resultHash,
            Instant startedAt,
            String status,
            String city,
            String cohort,
            java.time.LocalDate period,
            Instant cutoff,
            int eligibleUsers,
            int legalEdges,
            int recommendedEdges,
            int matchedPairs,
            int unmatchedUsers,
            String coverage,
            String scoringVersion,
            String objectiveVersion,
            String strategyVersion,
            String commitSha,
            long executionMs,
            String failureReason,
            String inputDocument,
            String comparisonJson) {
    }
}
