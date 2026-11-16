package cn.aiditto.match;

import cn.aiditto.domain.match.FeedbackAnswer;
import cn.aiditto.domain.match.MatchSnapshot;
import cn.aiditto.domain.match.MatchState;
import cn.aiditto.domain.match.Outcome;
import cn.aiditto.domain.match.Response;
import cn.aiditto.domain.match.SideSnapshot;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.week.WeekState;
import cn.aiditto.domain.week.WeekStatus;
import cn.aiditto.domain.week.WeekStep;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC for the match tables. Callers own the transaction. */
@Component
public class MatchDb {
    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;

    public MatchDb(JdbcTemplate jdbc, IdentifierGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    public long newId() {
        return ((Number) ids.nextId(new Object())).longValue();
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public record WeekRow(long id, String pool, LocalDate weekStart, String trigger, int runNo, WeekStatus status,
                          Instant publishAt) {}

    public record LoadedMatch(long id, long weekId, int version, MatchSnapshot snapshot, String scoreJson,
                              Map<Long, List<Integer>> flagged, String venueJson, String activity, String extra) {}

    public long insertWeek(String pool, LocalDate weekStart, String trigger, int runNo, Instant publishAt, Instant now) {
        long id = newId();
        jdbc.update("""
                INSERT INTO week (id, pool, week_start, trigger_kind, run_no, state, publish_at, created_at, updated_at)
                VALUES (?,?,?,?,?,'open',?,?,?)
                """, id, pool, weekStart, trigger, runNo, ts(publishAt), ts(now), ts(now));
        return id;
    }

    public WeekRow lockWeek(long id) {
        List<WeekRow> rows = jdbc.query("SELECT * FROM week WHERE id = ? FOR UPDATE", (rs, n) -> week(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeekRow findScheduled(String pool, LocalDate weekStart) {
        List<WeekRow> rows = jdbc.query("""
                SELECT * FROM week WHERE pool = ? AND week_start = ? AND trigger_kind = 'scheduled' AND run_no = 0
                """, (rs, n) -> week(rs), pool, weekStart);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean runInProgress(String pool) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM week WHERE pool = ? AND state IN ('open','locked','solved','copied','failed')
                """, Integer.class, pool);
        return n != null && n > 0;
    }

    public int nextManualRun(String pool, LocalDate weekStart) {
        Integer n = jdbc.queryForObject("""
                SELECT COALESCE(MAX(run_no), 0) + 1 FROM week
                WHERE pool = ? AND week_start = ? AND trigger_kind = 'manual'
                """, Integer.class, pool, weekStart);
        return n == null ? 1 : n;
    }

    public void saveWeek(long id, WeekStatus status, WeekStep finished, String error, String stats, String hash, Instant now) {
        String stamp = switch (finished) {
            case LOCK -> "locked_at";
            case SOLVE -> "solved_at";
            case COPY -> "copied_at";
            case PUBLISH -> "published_at";
            case CLOSE -> "closed_at";
        };
        if (status.state() == WeekState.FAILED) stamp = null;
        String sql = "UPDATE week SET state = ?, failed_step = ?, error = ?, updated_at = ?"
                + (stats != null ? ", stats = ?" : "")
                + (hash != null ? ", snapshot_hash = ?" : "")
                + (stamp != null ? ", " + stamp + " = ?" : "")
                + " WHERE id = ?";
        List<Object> args = new ArrayList<>();
        args.add(status.state().dbValue());
        args.add(status.failedStep() == null ? null : status.failedStep().dbValue());
        args.add(error);
        args.add(ts(now));
        if (stats != null) args.add(stats);
        if (hash != null) args.add(hash);
        if (stamp != null) args.add(ts(now));
        args.add(id);
        jdbc.update(sql, args.toArray());
    }

    public void insertParticipant(long weekId, long userId, int version, boolean in, String reason) {
        insertParticipants(weekId, List.<Object[]>of(new Object[] {userId, version, in, reason}));
    }

    /** One round trip for the whole pool. Each row is {userId, profileVersion, inPool, excludeReason}. */
    public void insertParticipants(long weekId, List<Object[]> rows) {
        if (rows.isEmpty()) return;
        StringBuilder sql = new StringBuilder("""
                INSERT INTO week_participant (week_id, user_id, profile_version, status, exclude_reason) VALUES 
                """);
        List<Object> args = new ArrayList<>(rows.size() * 5);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append("(?,?,?,?,?)");
            Object[] r = rows.get(i);
            args.add(weekId);
            args.add(r[0]);
            args.add(r[1]);
            args.add(Boolean.TRUE.equals(r[2]) ? "in" : "excluded");
            args.add(r[3]);
        }
        jdbc.update(sql.toString(), args.toArray());
    }

    public int countParticipants(long weekId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM week_participant WHERE week_id = ?", Integer.class, weekId);
        return n == null ? 0 : n;
    }

    public List<long[]> pastPairs() {
        return jdbc.query("SELECT user_low, user_high FROM \"match\"", (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    public List<long[]> reportedPairs() {
        return jdbc.query("SELECT reporter_id, reported_id FROM report", (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    /** Step 8：双向拒绝 / 举报后永久 forbidden 的对，硬过滤第一道就排除（A04）。 */
    public List<long[]> forbiddenPairs() {
        return jdbc.query("SELECT user_lo, user_hi FROM ai_pair_memory WHERE forbidden",
                (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    public void insertDraft(long matchId, long weekId, long low, long high, int score, String scoreJson, Instant now) {
        jdbc.update("""
                INSERT INTO "match" (id, week_id, user_low, user_high, score, score_json, state, version, created_at, updated_at)
                VALUES (?,?,?,?,?,?,'draft',0,?,?)
                """, matchId, weekId, low, high, score, scoreJson, ts(now), ts(now));
    }

    public void insertSide(long matchId, long weekId, long userId) {
        jdbc.update("""
                INSERT INTO match_side (match_id, user_id, week_id, response, slot_ids, reselected, flagged_reasons)
                VALUES (?,?,?,'pending','[]',FALSE,'[]')
                """, matchId, userId, weekId);
    }

    public void insertPlan(long matchId, String slotsJson, String venueJson, String activity, String extra) {
        jdbc.update("""
                INSERT INTO date_plan (match_id, slots, venue_json, activity, extra) VALUES (?,?,?,?,?)
                """, matchId, slotsJson, venueJson, activity, extra);
    }

    public void insertCopy(long matchId, long viewerId, String lang, String headline, String blurb, String reasons, String openers) {
        insertCopy(matchId, viewerId, lang, headline, blurb, reasons, openers, "template", "template-v1");
    }

    /** Step 7：source='ai' 时同时落 prompt_version，便于按版本追溯与回滚。 */
    public void insertCopy(long matchId, long viewerId, String lang, String headline, String blurb,
                           String reasons, String openers, String source, String promptVersion) {
        jdbc.update("""
                INSERT INTO match_copy (match_id, viewer_id, lang, headline, blurb, reasons, openers, source, prompt_version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, matchId, viewerId, lang, headline, blurb, reasons, openers, source, promptVersion);
    }

    /** 合并写入周批统计，不覆盖 SOLVE 阶段已写的 agentMode / pairs 等字段。 */
    public void mergeWeekStats(long id, String stats) {
        jdbc.update("UPDATE week SET stats = COALESCE(stats, '{}'::JSONB) || CAST(? AS JSONB) WHERE id = ?",
                stats, id);
    }

    public boolean hasCopy(long matchId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM match_copy WHERE match_id = ?", Integer.class, matchId);
        return n != null && n > 0;
    }

    public int countCopies(long weekId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_copy c JOIN "match" m ON m.id = c.match_id WHERE m.week_id = ?
                """, Integer.class, weekId);
        return n == null ? 0 : n;
    }

    public int countMatches(long weekId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM \"match\" WHERE week_id = ?", Integer.class, weekId);
        return n == null ? 0 : n;
    }

    public List<Long> matchIds(long weekId) {
        return jdbc.query("SELECT id FROM \"match\" WHERE week_id = ? ORDER BY id", (rs, n) -> rs.getLong(1), weekId);
    }

    public List<Long> draftIds(long weekId) {
        return jdbc.query("SELECT id FROM \"match\" WHERE week_id = ? AND state = 'draft' ORDER BY id", (rs, n) -> rs.getLong(1), weekId);
    }

    public boolean allTerminal(long weekId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM "match" WHERE week_id = ? AND state NOT IN
                ('declined','expired','unscheduled','cancelled','closed','halted')
                """, Integer.class, weekId);
        return n == null || n == 0;
    }

    public void setStreak(long userId, int streak, Instant now) {
        setStreaks(Map.of(userId, streak), now);
    }

    /** One round trip. Keys are user ids. */
    public void setStreaks(Map<Long, Integer> streaks, Instant now) {
        if (streaks.isEmpty()) return;
        StringBuilder sql = new StringBuilder("UPDATE \"user\" SET empty_week_streak = CASE id ");
        List<Object> args = new ArrayList<>(streaks.size() * 3 + 1);
        for (Map.Entry<Long, Integer> e : streaks.entrySet()) {
            sql.append("WHEN ? THEN ? ");
            args.add(e.getKey());
            args.add(e.getValue());
        }
        sql.append("END, updated_at = ? WHERE id IN (");
        args.add(ts(now));
        int i = 0;
        for (Long id : streaks.keySet()) {
            if (i++ > 0) sql.append(',');
            sql.append('?');
            args.add(id);
        }
        sql.append(')');
        jdbc.update(sql.toString(), args.toArray());
    }

    public int streak(long userId) {
        Integer n = jdbc.queryForObject("SELECT empty_week_streak FROM \"user\" WHERE id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    public LoadedMatch lockMatch(long id) {
        List<Long> ids = jdbc.query("SELECT id FROM \"match\" WHERE id = ? FOR UPDATE", (rs, n) -> rs.getLong(1), id);
        if (ids.isEmpty()) return null;
        return load(id);
    }

    public LoadedMatch latestFor(long userId) {
        List<Long> ids = jdbc.query("""
                SELECT m.id FROM match_side s JOIN "match" m ON m.id = s.match_id
                WHERE s.user_id = ?
                ORDER BY (m.state NOT IN ('declined','expired','unscheduled','cancelled','closed','halted')) DESC, m.created_at DESC
                LIMIT 1
                """, (rs, n) -> rs.getLong(1), userId);
        return ids.isEmpty() ? null : load(ids.get(0));
    }

    public WeekRow weekOf(long weekId) {
        List<WeekRow> rows = jdbc.query("SELECT * FROM week WHERE id = ?", (rs, n) -> week(rs), weekId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeekRow demoWeek(long userId) {
        List<WeekRow> rows = jdbc.query("""
                SELECT * FROM week WHERE pool = ? AND trigger_kind = 'demo' ORDER BY id DESC LIMIT 1
                """, (rs, n) -> week(rs), "u" + userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeekRow latestCityWeek(long userId) {
        List<WeekRow> rows = jdbc.query("""
                SELECT w.* FROM week_participant p JOIN week w ON w.id = p.week_id
                WHERE p.user_id = ? AND w.trigger_kind <> 'demo'
                ORDER BY w.week_start DESC, w.run_no DESC LIMIT 1
                """, (rs, n) -> week(rs), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Publish every draft in the week. Same columns {@code MatchMachine} sets on {@code Publish}, and no effects. */
    public int publishDrafts(long weekId, Instant respondDue, Instant now) {
        return jdbc.update("""
                UPDATE "match" SET state = 'published', published_at = ?, respond_due = ?,
                version = version + 1, updated_at = ? WHERE week_id = ? AND state = 'draft'
                """, ts(now), ts(respondDue), ts(now), weekId);
    }

    public void saveSnapshot(long matchId, MatchSnapshot s, Instant now) {
        jdbc.update("""
                UPDATE "match" SET state = ?, outcome = ?, published_at = ?, respond_due = ?, reselect_due = ?,
                slot_start = ?, slot_end = ?, feedback_due = ?, closed_at = ?, reporter_id = ?, cancelled_by = ?,
                version = version + 1, updated_at = ?
                WHERE id = ?
                """, s.state().dbValue(), s.outcome() == null ? null : s.outcome().dbValue(),
                ts(s.publishedAt()), ts(s.respondDue()), ts(s.reselectDue()), ts(s.slotStart()), ts(s.slotEnd()),
                ts(s.feedbackDue()), ts(s.closedAt()), s.reporterId(), s.cancelledBy(), ts(now), matchId);
        if (s.confirmedSlotId() != null) {
            jdbc.update("UPDATE date_plan SET confirmed_slot_id = ? WHERE match_id = ?", s.confirmedSlotId(), matchId);
        }
        saveSide(matchId, s.low(), now);
        saveSide(matchId, s.high(), now);
        saveFeedback(matchId, s.low(), now);
        saveFeedback(matchId, s.high(), now);
    }

    public void markAccepted(long matchId, long userId, Instant now) {
        jdbc.update("UPDATE match_side SET accept_clicked_at = ? WHERE match_id = ? AND user_id = ? AND accept_clicked_at IS NULL",
                ts(now), matchId, userId);
    }

    public void saveDecline(long matchId, long userId, String reasonsJson, String note) {
        jdbc.update("UPDATE match_side SET decline_reasons = ?, decline_note = ? WHERE match_id = ? AND user_id = ?",
                reasonsJson, note, matchId, userId);
    }

    public void saveFlags(long matchId, long userId, String json) {
        jdbc.update("UPDATE match_side SET flagged_reasons = ? WHERE match_id = ? AND user_id = ?", json, matchId, userId);
    }

    public void reschedule(long matchId, Instant start, Instant end, Instant now) {
        jdbc.update("UPDATE \"match\" SET slot_start = ?, slot_end = ?, feedback_due = ?, updated_at = ? WHERE id = ?",
                ts(start), ts(end), ts(end.plus(cn.aiditto.domain.slot.Deadlines.FEEDBACK_WINDOW)), ts(now), matchId);
    }

    public void setWeekState(long weekId, WeekState state, Instant now) {
        jdbc.update("UPDATE week SET state = ?, failed_step = NULL, updated_at = ? WHERE id = ?", state.dbValue(), ts(now), weekId);
    }

    public void insertReport(long id, long reporter, long reported, long matchId, String category, byte[] note,
                             boolean urgent, Instant sla, Instant now) {
        jdbc.update("""
                INSERT INTO report (id, reporter_id, reported_id, match_id, category, note_enc, severity, state, sla_due_at, created_at)
                VALUES (?,?,?,?,?,?,?,'open',?,?)
                """, id, reporter, reported, matchId, category, note, urgent ? "urgent" : "normal", ts(sla), ts(now));
    }

    public void addNoShow(long userId) {
        jdbc.update("UPDATE \"user\" SET no_show_count = no_show_count + 1 WHERE id = ?", userId);
    }

    public void addLateCancel(long userId) {
        jdbc.update("UPDATE \"user\" SET late_cancel_count = late_cancel_count + 1 WHERE id = ?", userId);
    }

    public void addNoResponse(long userId) {
        jdbc.update("UPDATE \"user\" SET no_response_streak = no_response_streak + 1 WHERE id = ?", userId);
    }

    public void clearNoResponse(long userId) {
        jdbc.update("UPDATE \"user\" SET no_response_streak = 0 WHERE id = ?", userId);
    }

    public void addDispute(long userId) {
        jdbc.update("UPDATE \"user\" SET dispute_count = dispute_count + 1 WHERE id = ?", userId);
    }

    public int[] penaltyCounts(long userId) {
        return jdbc.queryForObject("SELECT no_show_count, late_cancel_count, no_response_streak, dispute_count FROM \"user\" WHERE id = ?",
                (rs, n) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)}, userId);
    }

    public void pause(long userId, Instant now) {
        jdbc.update("UPDATE \"user\" SET status = 'paused', updated_at = ? WHERE id = ?", ts(now), userId);
    }

    /** Suspend applies to whoever earned it, including real users. The SQL above is just "id = ?" twice; kept simple below. */
    public void suspendUser(long userId, Instant until, Instant now) {
        jdbc.update("UPDATE \"user\" SET status = 'suspended', suspended_until = ?, updated_at = ? WHERE id = ?",
                ts(until), ts(now), userId);
    }

    public List<Long> due(String column, String state, Instant now) {
        return jdbc.query("SELECT id FROM \"match\" WHERE state = ? AND " + column + " <= ? ORDER BY id",
                (rs, n) -> rs.getLong(1), state, ts(now));
    }

    public void deleteMatch(long matchId) {
        jdbc.update("DELETE FROM feedback WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM report WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM match_copy WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM match_demo WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM date_plan WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM match_side WHERE match_id = ?", matchId);
        jdbc.update("DELETE FROM \"match\" WHERE id = ?", matchId);
    }

    public void setWantsAgain(long matchId, boolean wants) {
        jdbc.update("""
                INSERT INTO match_demo (match_id, partner_wants_again) VALUES (?,?)
                ON CONFLICT (match_id) DO UPDATE SET partner_wants_again = EXCLUDED.partner_wants_again
                """, matchId, wants);
    }

    public boolean wantsAgain(long matchId) {
        List<Boolean> rows = jdbc.query("SELECT partner_wants_again FROM match_demo WHERE match_id = ?",
                (rs, n) -> rs.getBoolean(1), matchId);
        return rows.isEmpty() || rows.get(0);
    }

    public void audit(long adminId, String action, String type, String target, String payload, String ip, Instant now) {
        jdbc.update("""
                INSERT INTO admin_audit (id, admin_id, action, target_type, target_id, payload, ip, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, newId(), adminId, action, type, target, payload, ip, ts(now));
    }

    public List<Map<String, Object>> weeks() {
        return jdbc.query("""
                SELECT id, pool, week_start, trigger_kind, run_no, state, failed_step, error, stats, created_at
                FROM week ORDER BY id DESC LIMIT 50
                """, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", Long.toString(rs.getLong("id")));
            m.put("pool", rs.getString("pool"));
            m.put("weekStart", rs.getDate("week_start").toLocalDate().toString());
            m.put("trigger", rs.getString("trigger_kind"));
            m.put("runNo", rs.getInt("run_no"));
            m.put("state", rs.getString("state"));
            m.put("failedStep", rs.getString("failed_step"));
            m.put("error", rs.getString("error"));
            m.put("stats", rs.getString("stats"));
            return m;
        });
    }

    public List<Map<String, Object>> pairs(long weekId) {
        return jdbc.query("""
                SELECT m.id, m.user_low, m.user_high, m.score, m.state, lo.name AS low_name, hi.name AS high_name,
                       ulo.cohort AS low_cohort, uhi.cohort AS high_cohort, ulo.handle AS low_handle, uhi.handle AS high_handle,
                       (SELECT c.source FROM match_copy c WHERE c.match_id = m.id LIMIT 1) AS source
                FROM "match" m
                JOIN profile lo ON lo.user_id = m.user_low
                JOIN profile hi ON hi.user_id = m.user_high
                JOIN "user" ulo ON ulo.id = m.user_low
                JOIN "user" uhi ON uhi.id = m.user_high
                WHERE m.week_id = ?
                ORDER BY m.id
                """, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", Long.toString(rs.getLong("id")));
            m.put("lowId", Long.toString(rs.getLong("user_low")));
            m.put("highId", Long.toString(rs.getLong("user_high")));
            m.put("lowName", rs.getString("low_name"));
            m.put("highName", rs.getString("high_name"));
            m.put("lowCohort", rs.getString("low_cohort"));
            m.put("highCohort", rs.getString("high_cohort"));
            m.put("lowHandle", rs.getString("low_handle"));
            m.put("highHandle", rs.getString("high_handle"));
            m.put("score", rs.getInt("score"));
            m.put("state", rs.getString("state"));
            m.put("copySource", rs.getString("source"));
            return m;
        }, weekId);
    }

    private void saveSide(long matchId, SideSnapshot side, Instant now) {
        jdbc.update("""
                UPDATE match_side SET response = ?, slot_ids = ?, reselected = ?, checked_in_at = ?,
                responded_at = CASE WHEN ? <> 'pending' AND responded_at IS NULL THEN ? ELSE responded_at END
                WHERE match_id = ? AND user_id = ?
                """, side.response().dbValue(), Json.write(new ArrayList<>(side.slotIds())), side.reselected(),
                ts(side.checkedInAt()), side.response().dbValue(), ts(now), matchId, side.userId());
    }

    private void saveFeedback(long matchId, SideSnapshot side, Instant now) {
        FeedbackAnswer f = side.feedback();
        if (f == null) return;
        jdbc.update("DELETE FROM feedback WHERE match_id = ? AND user_id = ?", matchId, side.userId());
        jdbc.update("""
                INSERT INTO feedback (match_id, user_id, met, rating, again, felt_safe, created_at) VALUES (?,?,?,?,?,?,?)
                """, matchId, side.userId(), f.met().id(), f.rating(), f.again() == null ? null : f.again().id(),
                f.feltSafe(), ts(now));
    }

    private LoadedMatch load(long id) {
        return jdbc.queryForObject("SELECT * FROM \"match\" WHERE id = ?", (rs, n) -> {
            long low = rs.getLong("user_low");
            long high = rs.getLong("user_high");
            List<Slot> slots = slots(id);
            SideSnapshot lo = side(id, low);
            SideSnapshot hi = side(id, high);
            String outcome = rs.getString("outcome");
            MatchSnapshot snap = new MatchSnapshot(
                    MatchState.fromDb(rs.getString("state")), lo, hi, slots,
                    instant(rs, "published_at"), instant(rs, "respond_due"), instant(rs, "reselect_due"),
                    confirmed(id), instant(rs, "slot_start"), instant(rs, "slot_end"), instant(rs, "feedback_due"),
                    outcome == null ? null : Outcome.valueOf(outcome.toUpperCase()),
                    longOrNull(rs.getObject("reporter_id")), longOrNull(rs.getObject("cancelled_by")), instant(rs, "closed_at"));
            Map<Long, List<Integer>> flags = new LinkedHashMap<>();
            flags.put(low, flags(id, low));
            flags.put(high, flags(id, high));
            String venue = jdbc.queryForObject("SELECT venue_json FROM date_plan WHERE match_id = ?", String.class, id);
            String activity = jdbc.queryForObject("SELECT activity FROM date_plan WHERE match_id = ?", String.class, id);
            String extra = jdbc.queryForObject("SELECT extra FROM date_plan WHERE match_id = ?", String.class, id);
            return new LoadedMatch(id, rs.getLong("week_id"), rs.getInt("version"), snap, rs.getString("score_json"),
                    flags, venue, activity, extra);
        }, id);
    }

    private SideSnapshot side(long matchId, long userId) {
        return jdbc.queryForObject("SELECT * FROM match_side WHERE match_id = ? AND user_id = ?", (rs, n) -> {
            FeedbackAnswer feedback = feedback(matchId, userId);
            return new SideSnapshot(userId, Response.valueOf(rs.getString("response").toUpperCase()),
                    slotSet(rs.getString("slot_ids")), rs.getBoolean("reselected"),
                    instant(rs, "checked_in_at"), feedback);
        }, matchId, userId);
    }

    private FeedbackAnswer feedback(long matchId, long userId) {
        List<FeedbackAnswer> rows = jdbc.query("SELECT * FROM feedback WHERE match_id = ? AND user_id = ?", (rs, n) -> {
            String again = rs.getString("again");
            Object rating = rs.getObject("rating");
            Object felt = rs.getObject("felt_safe");
            return new FeedbackAnswer(FeedbackAnswer.Met.fromId(rs.getString("met")),
                    rating == null ? null : ((Number) rating).intValue(),
                    again == null ? null : FeedbackAnswer.Again.fromId(again),
                    felt == null ? null : "1".equals(felt.toString()) || Boolean.TRUE.equals(felt) || Integer.valueOf(1).equals(felt));
        }, matchId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Slot> slots(long matchId) {
        String json = jdbc.queryForObject("SELECT slots FROM date_plan WHERE match_id = ?", String.class, matchId);
        List<Slot> out = new ArrayList<>();
        for (Object item : Json.read(json, List.class)) {
            Map<?, ?> m = (Map<?, ?>) item;
            out.add(new Slot(m.get("id").toString(), Instant.parse(m.get("startsAt").toString())));
        }
        return out;
    }

    private String confirmed(long matchId) {
        return jdbc.queryForObject("SELECT confirmed_slot_id FROM date_plan WHERE match_id = ?", String.class, matchId);
    }

    private List<Integer> flags(long matchId, long userId) {
        String json = jdbc.queryForObject("SELECT flagged_reasons FROM match_side WHERE match_id = ? AND user_id = ?",
                String.class, matchId, userId);
        if (json == null || json.isBlank()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (Object o : Json.read(json, List.class)) if (o instanceof Number num) out.add(num.intValue());
        return out;
    }

    private static java.util.Set<String> slotSet(String json) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (json != null && !json.isBlank()) {
            for (Object o : Json.read(json, List.class)) if (o != null) out.add(o.toString());
        }
        return out;
    }

    private WeekRow week(ResultSet rs) throws SQLException {
        String failed = rs.getString("failed_step");
        WeekState state = WeekState.fromDb(rs.getString("state"));
        WeekStatus status = state == WeekState.FAILED
                ? WeekStatus.failed(WeekStep.valueOf(failed.toUpperCase()))
                : WeekStatus.of(state);
        return new WeekRow(rs.getLong("id"), rs.getString("pool"), rs.getDate("week_start").toLocalDate(),
                rs.getString("trigger_kind"), rs.getInt("run_no"), status, instant(rs, "publish_at"));
    }

    private static Long longOrNull(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static Timestamp ts(Instant t) {
        return t == null ? null : Timestamp.from(Times.millis(t));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
