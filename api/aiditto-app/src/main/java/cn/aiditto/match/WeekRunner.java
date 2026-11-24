package cn.aiditto.match;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.MatchingResult;
import cn.aiditto.domain.matching.MatchingSolver;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.Weights;
import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.slot.SlotRules;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.domain.week.WeekMachine;
import cn.aiditto.domain.week.WeekRuleException;
import cn.aiditto.domain.week.WeekState;
import cn.aiditto.domain.week.WeekStatus;
import cn.aiditto.domain.week.WeekStep;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One batch step per transaction (技术方案 §5.4). A crash leaves the week at the last successful step;
 * the next call resumes there. Re-running a finished step is a no-op.
 */
@Service
@EnableConfigurationProperties(MatchAgentProperties.class)
public class WeekRunner {
    private final MatchDb db;
    private final People people;
    private final MatchCopyWriter copies;
    private final AgentRerankService agent;
    private final TransactionTemplate tx;
    private final WeekMetricsService metrics;

    /** 兼容旧构造（无 Agent 精排、无埋点，等价关闭）。 */
    public WeekRunner(MatchDb db, People people, MatchCopyWriter copies, PlatformTransactionManager txm) {
        this(db, people, copies, null, null, txm);
    }

    /** 兼容 5 参构造（有 Agent、无埋点）。 */
    public WeekRunner(MatchDb db, People people, MatchCopyWriter copies,
                      AgentRerankService agent, PlatformTransactionManager txm) {
        this(db, people, copies, agent, null, txm);
    }

    @Autowired
    public WeekRunner(MatchDb db, People people, MatchCopyWriter copies,
                      AgentRerankService agent, WeekMetricsService metrics, PlatformTransactionManager txm) {
        this.db = db;
        this.people = people;
        this.copies = copies;
        this.agent = agent;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(txm);
    }

    /** Admin "立即匹配": lock through publish. Does not wait for the week's close. */
    public Map<String, Object> runManual(String pool, Instant now) {
        return runManual(pool, now, true);
    }

    public Map<String, Object> runManual(String pool, Instant now, boolean publish) {
        if (!"bj".equals(pool) && !"sh".equals(pool)) throw DomainException.invalid("pool", "只能是 bj 或 sh");
        LocalDate week = WeekCalendar.weekStartFor(now);
        long weekId = tx.execute(s -> {
            Long stuck = incomplete(pool);
            if (stuck != null) throw new DomainException(ErrorCode.RUN_IN_PROGRESS);
            return db.insertWeek(pool, week, "manual", db.nextManualRun(pool, week), now, now);
        });
        drive(weekId, now, publish ? WeekStep.PUBLISH : WeekStep.COPY);
        return summary(weekId, now);
    }

    /** Lock and solve, then roll back. Used to preview a batch without writing matches. */
    public Map<String, Object> dryRun(String pool, Instant now) {
        if (!"bj".equals(pool) && !"sh".equals(pool)) throw DomainException.invalid("pool", "只能是 bj 或 sh");
        LocalDate week = WeekCalendar.weekStartFor(now);
        return tx.execute(status -> {
            if (incomplete(pool) != null) throw new DomainException(ErrorCode.RUN_IN_PROGRESS);
            long id = db.insertWeek(pool, week, "manual", db.nextManualRun(pool, week), now, now);
            Map<Long, List<People.Member>> members = new LinkedHashMap<>();
            runStep(id, WeekStep.LOCK, now, members);
            runStep(id, WeekStep.SOLVE, now, members);
            Map<String, Object> out = summary(id, now);
            status.setRollbackOnly();
            out.put("dryRun", true);
            return out;
        });
    }

    /** Scheduled tick for both cities. Each due step is its own transaction. */
    public void tick(Instant now) {
        for (String pool : List.of("bj", "sh")) {
            LocalDate week = WeekCalendar.weekStartFor(now);
            Long id = tx.execute(s -> {
                MatchDb.WeekRow existing = db.findScheduled(pool, week);
                if (existing != null) return existing.id();
                if (incomplete(pool) != null) return null;
                return db.insertWeek(pool, week, "scheduled", 0, WeekCalendar.publishAt(week), now);
            });
            if (id == null) continue;
            drive(id, now, WeekStep.CLOSE);
        }
    }

    /** Re-run one step. A step that already succeeded does nothing. */
    public WeekStatus rerun(long weekId, WeekStep step, Instant now) {
        return tx.execute(s -> {
            runStep(weekId, step, now, new LinkedHashMap<>());
            return db.lockWeek(weekId).status();
        });
    }

    /** Run the next step once. Tests use this for re-run and crash-resume. */
    public WeekStatus step(long weekId, Instant now) {
        return tx.execute(s -> {
            MatchDb.WeekRow fresh = db.lockWeek(weekId);
            if (fresh == null) throw new DomainException(ErrorCode.NOT_FOUND, "没有这个批次");
            Optional<WeekStep> next = WeekMachine.nextStep(fresh.status());
            if (next.isEmpty()) return fresh.status();
            if (next.get() == WeekStep.CLOSE && !db.allTerminal(weekId)) return fresh.status();
            runStep(weekId, next.get(), now, new LinkedHashMap<>());
            return db.lockWeek(weekId).status();
        });
    }

    private void drive(long weekId, Instant now, WeekStep last) {
        Map<Long, List<People.Member>> members = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            MatchDb.WeekRow row = db.lockWeek(weekId);
            Optional<WeekStep> next = WeekMachine.nextStep(row.status());
            if (next.isEmpty()) return;
            WeekStep step = next.get();
            if (step.ordinal() > last.ordinal()) return;
            boolean scheduled = "scheduled".equals(row.trigger());
            if (scheduled && !WeekCalendar.isDue(row.weekStart(), row.status(), step, now)) return;
            if (step == WeekStep.CLOSE && !db.allTerminal(weekId)) return;
            try {
                tx.executeWithoutResult(s -> runStep(weekId, step, now, members));
            } catch (RuntimeException e) {
                if (e instanceof WeekRuleException && step == WeekStep.CLOSE) return;
                tx.executeWithoutResult(s -> markFailed(weekId, step, e, now));
                throw e;
            }
        }
        // 效果埋点：步骤全部提交后拍一次结果快照。埋点失败绝不影响配对主流程。
        if (metrics != null) {
            try {
                metrics.capture(weekId);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(WeekRunner.class)
                        .warn("week metric capture failed week={} err={}", weekId, e.getClass().getSimpleName());
            }
        }
    }

    private void runStep(long weekId, WeekStep step, Instant now, Map<Long, List<People.Member>> members) {
        MatchDb.WeekRow week = db.lockWeek(weekId);
        WeekMachine.Decision decision = WeekMachine.begin(week.status(), step);
        if (decision == WeekMachine.Decision.ALREADY_DONE) return;
        switch (step) {
            case LOCK -> lock(week, now, members);
            case SOLVE -> solve(week, now, members);
            case COPY -> copy(week, now, members);
            case PUBLISH -> publish(week, now);
            case CLOSE -> close(week, now);
        }
    }

    private List<People.Member> pool(MatchDb.WeekRow week, Instant now, Map<Long, List<People.Member>> cache) {
        return cache.computeIfAbsent(week.id(), id -> people.city(week.pool(), week.weekStart(), now));
    }

    private void lock(MatchDb.WeekRow week, Instant now, Map<Long, List<People.Member>> cache) {
        List<People.Member> members = pool(week, now, cache);
        List<Long> in = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>(members.size());
        for (People.Member m : members) {
            rows.add(new Object[] {m.userId(), m.profileVersion(), m.candidate() != null,
                    m.excluded() == null ? null : m.excluded().name().toLowerCase()});
            if (m.candidate() != null) in.add(m.candidate().id());
        }
        db.insertParticipants(week.id(), rows);
        in.sort(Long::compare);
        String hash = sha(week.weekStart().toEpochDay() + ":" + in);
        db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.LOCK), WeekStep.LOCK, null, null, hash, now);
    }

    private void solve(MatchDb.WeekRow week, Instant now, Map<Long, List<People.Member>> cache) {
        if (db.countMatches(week.id()) > 0) {
            db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.SOLVE), WeekStep.SOLVE, null, null, null, now);
            return;
        }
        List<Candidate> candidates = new ArrayList<>();
        for (People.Member m : pool(week, now, cache)) {
            if (m.candidate() != null) candidates.add(m.candidate());
        }
        boolean manual = "manual".equals(week.trigger());
        // Step 8：双向拒绝/举报沉淀的永久 forbidden 对与 report 一起进硬过滤，绝不复推（A04）
        List<long[]> excluded = new ArrayList<>(db.reportedPairs());
        excluded.addAll(db.forbiddenPairs());
        MatchingInput input = new MatchingInput(week.weekStart(), candidates, pairs(db.pastPairs()), pairs(excluded),
                Weights.W1, week.weekStart().toEpochDay(), manual);
        // Step 6：先用同一份 input 建边（硬约束/门槛完全一致），只对 Top-N 跑 Agent 精排，
        // 再把 AI 分注入 input 重新求解；开关关闭或整批降级时 input 不变，逐位等同 M2。
        Map<String, Object> agentStats = new LinkedHashMap<>();
        if (agent != null) {
            AgentRerankService.Outcome o = agent.rerank(week.id(),
                    MatchingSolver.buildEdges(input));
            if (!o.aiScores().isEmpty()) input = input.withAiScores(o.aiScores());
            // D6 方案 A：学习排序分（user_taste.weights 直接算的确定性契合分）并入边权。
            if (!o.learnedScores().isEmpty()) input = input.withLearnedScores(o.learnedScores());
            agentStats.put("agentMode", o.mode());
            agentStats.put("agentJudged", o.judged());
            agentStats.put("agentSimulated", o.simulated());
            agentStats.put("agentDegraded", o.degradedPairs());
            agentStats.put("agentAiPairs", o.aiScores().size());
            agentStats.put("agentLearnedPairs", o.learnedScores().size());
        }
        MatchingResult result = new MatchingSolver().solve(input);
        Instant respondDue = Deadlines.respondDue(manual ? now : week.publishAt());
        List<Slot> slots = SlotRules.propose(respondDue);
        String slotsJson = slotsJson(slots);
        Set<Long> matched = new LinkedHashSet<>();
        for (MatchingResult.MatchedPair pair : result.pairs()) {
            long id = db.newId();
            db.insertDraft(id, week.id(), pair.low(), pair.high(), pair.score(), Json.write(pair.breakdown()), now);
            db.insertSide(id, week.id(), pair.low());
            db.insertSide(id, week.id(), pair.high());
            db.insertPlan(id, slotsJson, "{}", null, null);
            matched.add(pair.low());
            matched.add(pair.high());
        }
        Map<Long, Integer> streaks = new LinkedHashMap<>();
        for (Candidate c : candidates) streaks.put(c.id(), matched.contains(c.id()) ? 0 : c.emptyWeekStreak() + 1);
        db.setStreaks(streaks, now);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pairs", result.pairs().size());
        stats.put("unmatched", result.unmatched().size());
        stats.put("participants", result.stats().participants());
        stats.put("edges", result.stats().edges());
        stats.put("solver", result.stats().solver().name());
        stats.put("algo", result.stats().algoVersion());
        stats.put("buildMillis", result.stats().buildMillis());
        stats.put("solveMillis", result.stats().solveMillis());
        stats.put("belowThreshold", result.stats().belowThreshold());
        stats.putAll(agentStats);
        db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.SOLVE), WeekStep.SOLVE, null, Json.write(stats), null, now);
    }

    private void copy(MatchDb.WeekRow week, Instant now, Map<Long, List<People.Member>> cache) {
        copies.resetStats();
        Map<Long, People.Member> byId = new LinkedHashMap<>();
        for (People.Member m : pool(week, now, cache)) byId.put(m.userId(), m);
        for (long matchId : db.matchIds(week.id())) {
            if (db.hasCopy(matchId)) continue;
            long[] pair = pairOf(matchId);
            People.Member low = byId.get(pair[0]);
            People.Member high = byId.get(pair[1]);
            if (low == null || high == null) throw new IllegalStateException("pair left the city pool: " + matchId);
            copies.write(matchId, low, high);
        }
        db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.COPY), WeekStep.COPY, null, null, null, now);
        // Step 7：合并写入（不覆盖 SOLVE 的 agent 统计），供「模板兜底率 ≤5%」闸门按批统计
        db.mergeWeekStats(week.id(), Json.write(copies.stats()));
    }

    private void publish(MatchDb.WeekRow week, Instant now) {
        // Every row is still a draft, which is the only state MatchMachine publishes, and publish has no effects.
        // One update is the same transition; a retry touches only the rows that are still drafts.
        db.publishDrafts(week.id(), Deadlines.respondDue(now), now);
        db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.PUBLISH), WeekStep.PUBLISH, null, null, null, now);
    }

    private void close(MatchDb.WeekRow week, Instant now) {
        WeekStatus next = WeekMachine.succeed(week.status(), WeekStep.CLOSE, db.allTerminal(week.id()));
        db.saveWeek(week.id(), next, WeekStep.CLOSE, null, null, null, now);
    }

    private void markFailed(long weekId, WeekStep step, Exception e, Instant now) {
        MatchDb.WeekRow week = db.lockWeek(weekId);
        try {
            WeekStatus failed = WeekMachine.fail(week.status(), step);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (message.length() > 1800) message = message.substring(0, 1800);
            db.saveWeek(weekId, failed, step, message, null, null, now);
        } catch (WeekRuleException ignored) {
            // the step already completed; nothing to mark
        }
    }

    private Long incomplete(String pool) {
        List<Long> ids = db.jdbc().query("""
                SELECT id FROM week WHERE pool = ? AND state IN ('open','locked','solved','copied','failed')
                ORDER BY id DESC LIMIT 1
                """, (rs, n) -> rs.getLong(1), pool);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long[] pairOf(long matchId) {
        return db.jdbc().queryForObject("SELECT user_low, user_high FROM \"match\" WHERE id = ?",
                (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)}, matchId);
    }

    private Map<String, Object> summary(long weekId, Instant started) {
        MatchDb.WeekRow week = db.weekOf(weekId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weekId", Long.toString(weekId));
        out.put("pool", week.pool());
        out.put("weekStart", week.weekStart().toString());
        out.put("state", week.status().state().dbValue());
        out.put("pairs", db.countMatches(weekId));
        out.put("participants", db.countParticipants(weekId));
        return out;
    }

    private static Set<PairKey> pairs(List<long[]> rows) {
        Set<PairKey> out = new LinkedHashSet<>();
        for (long[] r : rows) out.add(PairKey.of(r[0], r[1]));
        return out;
    }

    private static String slotsJson(List<Slot> slots) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Slot s : slots) out.add(Map.of("id", s.id(), "startsAt", Times.iso(s.startsAt())));
        return Json.write(out);
    }

    private static String sha(String raw) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
