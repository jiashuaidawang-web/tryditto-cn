package cn.aiditto.it;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.UserLock;
import cn.aiditto.block.BlockService;
import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.domain.week.WeekStep;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.match.MatchDb;
import cn.aiditto.match.People;
import cn.aiditto.match.PublishGuard;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.match.WeekRunner;
import cn.aiditto.profile.ProfileProjection;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publish safety on PostgreSQL. The default configuration is V4 compliance mode: only GRANTED
 * may be published. This class does not turn on the legacy UNKNOWN hatch.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublishSafetyIT extends ApiIT {
    @Autowired SyntheticLoader loader;
    @Autowired WeekRunner weeks;
    @Autowired People people;
    @Autowired Crypto crypto;
    @Autowired MatchingConsentService consents;
    @Autowired PublishGuard guard;
    @Autowired MatchDb db;
    @Autowired BlockService blocks;
    @Autowired AccountService accounts;
    @Autowired UserLock userLock;
    @Autowired PlatformTransactionManager txm;
    @Autowired DataSource dataSource;
    @Autowired ProfileProjection profiles;

    private PublishSafetyFixtures fixtures;

    private PublishSafetyFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PublishSafetyFixtures(jdbc, accounts, profiles, consents, db, people);
        }
        return fixtures;
    }

    @Test
    @Order(1)
    void unknownCannotPublishAndAdminOrDemoCannotBypass() {
        assertThat(consents.legacyAllowUnknown()).isFalse();
        System.out.println("pg " + jdbc.queryForObject("SELECT version()", String.class));
        assertThat(loader.load().get("loaded")).isEqualTo(200);
        String demo = login("13800000002");
        Resp before = call("GET", "/api/me/matching-consent", null, demo);
        assertThat(before.status()).isEqualTo(200);
        assertThat(before.body().get("consentStatus").asString()).isEqualTo("UNKNOWN");
        assertThat(before.body().get("consentVersion").asInt()).isZero();

        Resp granted = call("POST", "/api/me/matching-consent", Map.of(), demo);
        assertThat(granted.status()).isEqualTo(200);
        assertThat(granted.body().get("consentStatus").asString()).isEqualTo("GRANTED");
        Resp revoked = call("DELETE", "/api/me/matching-consent", null, demo);
        assertThat(revoked.body().get("consentStatus").asString()).isEqualTo("REVOKED");
        assertThat(revoked.body().toString()).doesNotContain("phone", "wechat");

        String admin = login("13800000001");
        Resp run = call("POST", "/api/admin/match-runs", Map.of("pool", "sh", "publish", true), admin);
        assertThat(run.status()).as("%s", run.body()).isEqualTo(200);
        assertNoPrivateLeak(run.body().toString());
        assertThat(publishedInPool("sh")).isZero();

        Resp stage = call("POST", "/api/demo/match/stage", Map.of("stage", "revealed"), demo);
        assertThat(stage.status()).isEqualTo(409);
        assertThat(stage.code()).isEqualTo("MATCH_STATE");
        assertNoPrivateLeak(new String(stage.raw()));
        Integer demoPublished = jdbc.queryForObject("""
                SELECT COUNT(*) FROM "match" m
                JOIN match_side s ON s.match_id = m.id
                JOIN "user" u ON u.id = s.user_id
                WHERE u.phone_hash = ? AND m.state = 'published'
                """, Integer.class, crypto.hash("13800000002"));
        assertThat(demoPublished).isZero();

        long[] pair = twoUsersWithoutConsent();
        long weekId = insertOpenWeek("bj", 980);
        try {
            long matchId = insertDraft(weekId, pair[0], pair[1]);
            PublishGuard.Outcome outcome = guard.publishDraft(matchId, Instant.now());
            assertThat(outcome.published()).isFalse();
            assertThat(outcome.reasonCodes()).contains("CONSENT_UNKNOWN");
            assertDraftHeld(matchId, "CONSENT_UNKNOWN");
        } finally {
            closeWeek(weekId);
        }
    }

    @Test
    @Order(2)
    void grantedDraftPublishes() throws Exception {
        assertDistinctConnections();
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            committed(() -> {
                PublishGuard.Outcome outcome = guard.publishDraft(scene.matchId(), Instant.now());
                assertThat(outcome.published()).isTrue();
                assertThat(outcome.reasonCodes()).isEmpty();
            });
            assertThat(stateOf(scene.matchId())).isEqualTo("published");
            assertThat(versionOf(scene.matchId())).isEqualTo(1);
            assertThat(publishedAt(scene.matchId())).isNotNull();
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(3)
    void publishRollsBackWithTheTransaction() {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            int before = reports(scene.matchId());
            rollbackAfterPublish(scene.matchId());
            assertThat(stateOf(scene.matchId())).isEqualTo("draft");
            assertThat(publishedAt(scene.matchId())).isNull();
            assertThat(versionOf(scene.matchId())).isZero();
            assertThat(reports(scene.matchId())).isEqualTo(before);
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(4)
    void revokeBeforePublishHoldsTheDraft() {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            committed(() -> consents.revoke(scene.low()));
            committed(() -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertDraftHeld(scene.matchId(), "CONSENT_REVOKED");
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(5)
    void revokeAfterPublishHoldsWithoutPenalty() {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            committed(() -> assertThat(guard.publishDraft(scene.matchId(), Instant.now()).published()).isTrue());
            int beforeReport = reports(scene.matchId());
            int beforeLow = streak(scene.low());
            int beforeHigh = streak(scene.high());
            committed(() -> consents.revoke(scene.low()));
            assertThat(stateOf(scene.matchId())).isEqualTo("cancelled");
            assertThat(holdReason(scene.matchId())).contains("CONSENT_REVOKED");
            assertThat(reports(scene.matchId())).isEqualTo(beforeReport);
            assertThat(streak(scene.low())).isEqualTo(beforeLow);
            assertThat(streak(scene.high())).isEqualTo(beforeHigh);
            assertNoNotificationTable();
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(6)
    void revokedMatchHidesContact() {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            committed(() -> assertThat(guard.publishDraft(scene.matchId(), Instant.now()).published()).isTrue());
            committed(() -> consents.revoke(scene.low()));
            assertHidden(scene.matchId(), scene.lowPhone());
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(7)
    void revokeRacesPublishAndDoesNotPublish() throws Exception {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            race(() -> consents.revoke(scene.low()),
                    () -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertThat(stateOf(scene.matchId())).isNotEqualTo("published");
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(8)
    void blockRacesPublishAndDoesNotPublish() throws Exception {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            race(() -> blocks.add(scene.low(), new BlockService.NewBlock("n", List.of(), List.of(scene.highPhone()), "")),
                    () -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertThat(stateOf(scene.matchId())).isNotEqualTo("published");
        } finally {
            jdbc.update("DELETE FROM block_contact WHERE user_id = ?", scene.low());
            jdbc.update("DELETE FROM \"block\" WHERE user_id = ?", scene.low());
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(9)
    void deleteRacesPublishAndDoesNotPublish() throws Exception {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            race(() -> accounts.requestDelete(scene.low()),
                    () -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertThat(stateOf(scene.matchId())).isNotEqualTo("published");
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(10)
    void hardConstraintRacesPublishAndDoesNotPublish() throws Exception {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        int ageMin = jdbc.queryForObject("SELECT age_min FROM profile WHERE user_id = ?", Integer.class, scene.low());
        int ageMax = jdbc.queryForObject("SELECT age_max FROM profile WHERE user_id = ?", Integer.class, scene.low());
        try {
            race(() -> committed(() -> {
                        userLock.lock(scene.low());
                        jdbc.update("UPDATE profile SET age_min = 40, age_max = 50 WHERE user_id = ?", scene.low());
                        guard.releaseUser(scene.low(), Instant.now());
                    }),
                    () -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertThat(stateOf(scene.matchId())).isNotEqualTo("published");
        } finally {
            jdbc.update("UPDATE profile SET age_min = ?, age_max = ? WHERE user_id = ?", ageMin, ageMax, scene.low());
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(11)
    void duplicatePublishIsIdempotent() throws Exception {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            race(() -> guard.publishDraft(scene.matchId(), Instant.now()),
                    () -> guard.publishDraft(scene.matchId(), Instant.now()));
            assertThat(stateOf(scene.matchId())).isEqualTo("published");
            assertThat(versionOf(scene.matchId())).isEqualTo(1);
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(12)
    void sharedUsersLockInUserIdOrder() throws Exception {
        List<PublishSafetyFixtures.Adult> adults = fixtures().threeAdults();
        long a = adults.get(0).id();
        long b = adults.get(1).id();
        long c = adults.get(2).id();
        LocalDate week = WeekCalendar.weekStartFor(Instant.now());
        long leftWeek = insertOpenWeek("bj", db.nextManualRun("bj", week));
        long rightWeek = insertOpenWeek("bj", db.nextManualRun("bj", week));
        try {
            long left = insertDraft(leftWeek, a, b);
            long right = insertDraft(rightWeek, b, c);
            race(() -> guard.publishDraft(left, Instant.now()), () -> guard.publishDraft(right, Instant.now()));
            assertThat(stateOf(left)).isIn("published", "cancelled");
            assertThat(stateOf(right)).isIn("published", "cancelled");
        } finally {
            closeWeek(leftWeek);
            closeWeek(rightWeek);
        }
    }

    @Test
    @Order(13)
    void holdDoesNotAddReportOrStreak() {
        PublishSafetyFixtures.Scene scene = fixtures().grantedDraft();
        try {
            committed(() -> assertThat(guard.publishDraft(scene.matchId(), Instant.now()).published()).isTrue());
            int beforeReport = reports(scene.matchId());
            int beforeLow = streak(scene.low());
            int beforeHigh = streak(scene.high());
            committed(() -> consents.revoke(scene.low()));
            assertThat(stateOf(scene.matchId())).isEqualTo("cancelled");
            assertThat(reports(scene.matchId())).isEqualTo(beforeReport);
            assertThat(streak(scene.low())).isEqualTo(beforeLow);
            assertThat(streak(scene.high())).isEqualTo(beforeHigh);
        } finally {
            fixtures().close(scene.weekId());
        }
    }

    @Test
    @Order(14)
    void weekRunnerPublishesAndRepeatIsIdempotent() {
        Map<String, Integer> census = loader.load();
        assertThat(census.get("loaded") + census.get("skipped")).isEqualTo(200);
        TestConsents.grantAll(jdbc);
        long weekId = copiedWeek("bj");
        try {
            int drafts = drafts(weekId).size();
            System.out.println("bj solver drafts " + drafts);
            assertThat(drafts).as("real solver produced drafts").isPositive();
            weeks.rerun(weekId, WeekStep.PUBLISH, Instant.now());
            Integer published = publishedCount(weekId);
            assertThat(published).isPositive();
            assertPublishedPairsPassHardFilter(weekId);
            Integer versions = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(version), 0) FROM \"match\" WHERE week_id = ?", Integer.class, weekId);
            weeks.rerun(weekId, WeekStep.PUBLISH, Instant.now());
            assertThat(publishedCount(weekId)).isEqualTo(published);
            assertThat(jdbc.queryForObject(
                    "SELECT COALESCE(SUM(version), 0) FROM \"match\" WHERE week_id = ?", Integer.class, weekId))
                    .isEqualTo(versions);
        } finally {
            closeWeek(weekId);
        }
    }

    private void assertHidden(long matchId, String phone) {
        String token = login(phone);
        Resp card = call("GET", "/api/matches/current", null, token);
        assertThat(card.status()).isEqualTo(200);
        assertThat(card.body().get("partner").isNull()).isTrue();
        assertNoPrivateLeak(card.body().toString());
        Resp contact = call("GET", "/api/matches/" + matchId + "/contact", null, token);
        if (contact.status() == 200 && contact.body() != null && contact.body().get("wechat") != null) {
            assertThat(contact.body().get("wechat").isNull()).isTrue();
        }
        assertNoPrivateLeak(new String(contact.raw()));
    }

    private void rollbackAfterPublish(long matchId) {
        TransactionTemplate tx = committedTemplate();
        tx.executeWithoutResult(status -> {
            PublishGuard.Outcome outcome = guard.publishDraft(matchId, Instant.now());
            assertThat(outcome.published()).isTrue();
            status.setRollbackOnly();
        });
    }

    private void committed(Runnable work) {
        committedTemplate().executeWithoutResult(status -> work.run());
    }

    private TransactionTemplate committedTemplate() {
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private void race(Runnable left, Runnable right) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Thread first = new Thread(() -> runAfter(barrier, left, errors, done), "publish-race-a");
        Thread second = new Thread(() -> runAfter(barrier, right, errors, done), "publish-race-b");
        first.start();
        second.start();
        assertThat(done.await(60, TimeUnit.SECONDS)).as("deadlock or hang").isTrue();
        assertThat(errors).isEmpty();
    }

    private static void runAfter(CyclicBarrier barrier, Runnable work, List<Throwable> errors, CountDownLatch done) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
            work.run();
        } catch (Throwable t) {
            errors.add(t);
        } finally {
            done.countDown();
        }
    }

    private void assertDistinctConnections() throws Exception {
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            assertThat(backendPid(a)).isNotEqualTo(backendPid(b));
        }
    }

    private static int backendPid(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private long copiedWeek(String pool) {
        Map<String, Object> run = weeks.runManual(pool, Instant.now(), false);
        long weekId = Long.parseLong(run.get("weekId").toString());
        assertThat(drafts(weekId)).isNotEmpty();
        return weekId;
    }

    private List<Draft> drafts(long weekId) {
        return jdbc.query("""
                SELECT id, user_low, user_high FROM "match"
                WHERE week_id = ? AND state = 'draft' ORDER BY id
                """, (rs, n) -> new Draft(rs.getLong(1), rs.getLong(2), rs.getLong(3)), weekId);
    }

    private long insertOpenWeek(String pool, int runNo) {
        return db.insertWeek(pool, WeekCalendar.weekStartFor(Instant.now()), "manual", runNo, Instant.now(), Instant.now());
    }

    private long insertDraft(long weekId, long low, long high) {
        long matchId = db.newId();
        Instant now = Instant.now();
        db.insertDraft(matchId, weekId, Math.min(low, high), Math.max(low, high), 1, "{}", now);
        db.insertSide(matchId, weekId, low);
        db.insertSide(matchId, weekId, high);
        db.insertPlan(matchId, "[]", "{}", "coffee", "");
        return matchId;
    }

    private void closeWeek(long weekId) {
        jdbc.update("""
                UPDATE week SET state = 'closed'
                WHERE id = ? AND state IN ('open','locked','solved','copied','failed')
                """, weekId);
    }

    private long[] twoUsersWithoutConsent() {
        List<Long> ids = jdbc.queryForList("""
                SELECT u.id FROM "user" u
                JOIN profile p ON p.user_id = u.id
                LEFT JOIN matching_consent c ON c.user_id = u.id
                WHERE c.user_id IS NULL AND u.status = 'active'
                ORDER BY u.id
                LIMIT 2
                """, Long.class);
        assertThat(ids).hasSize(2);
        return new long[] {ids.get(0), ids.get(1)};
    }

    private void assertDraftHeld(long matchId, String code) {
        assertThat(stateOf(matchId)).isEqualTo("cancelled");
        assertThat(publishedAt(matchId)).isNull();
        assertThat(holdReason(matchId)).contains(code);
        assertThat(holdReason(matchId)).doesNotContain("phone", "wechat");
    }

    private String stateOf(long matchId) {
        return jdbc.queryForObject("SELECT state FROM \"match\" WHERE id = ?", String.class, matchId);
    }

    private Object publishedAt(long matchId) {
        return jdbc.queryForObject("SELECT published_at FROM \"match\" WHERE id = ?", Object.class, matchId);
    }

    private int versionOf(long matchId) {
        return jdbc.queryForObject("SELECT version FROM \"match\" WHERE id = ?", Integer.class, matchId);
    }

    private String holdReason(long matchId) {
        return String.valueOf(jdbc.queryForObject("SELECT hold_reason FROM \"match\" WHERE id = ?", String.class, matchId));
    }

    private int reports(long matchId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE match_id = ?", Integer.class, matchId);
    }

    private int streak(long userId) {
        return jdbc.queryForObject("SELECT no_response_streak FROM \"user\" WHERE id = ?", Integer.class, userId);
    }

    private void assertNoNotificationTable() {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name ILIKE '%notif%'
                """, Integer.class);
        assertThat(n).isZero();
    }

    private Integer publishedInPool(String pool) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM "match" m JOIN week w ON w.id = m.week_id
                WHERE w.pool = ? AND m.state = 'published'
                """, Integer.class, pool);
    }

    private Integer publishedCount(long weekId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"match\" WHERE week_id = ? AND state = 'published'", Integer.class, weekId);
    }

    private void assertPublishedPairsPassHardFilter(long weekId) {
        LocalDate week = WeekCalendar.weekStartFor(Instant.now());
        MatchingInput input = MatchingInput.of(week, List.of(), 1);
        List<Map<String, Object>> pairs = jdbc.queryForList("""
                SELECT user_low, user_high FROM "match" WHERE week_id = ? AND state = 'published'
                """, weekId);
        assertThat(pairs).isNotEmpty();
        for (Map<String, Object> pair : pairs) {
            long a = ((Number) pair.get("user_low")).longValue();
            long b = ((Number) pair.get("user_high")).longValue();
            Candidate ca = people.ungated(a, week, Instant.now());
            Candidate cb = people.ungated(b, week, Instant.now());
            assertThat(ca).isNotNull();
            assertThat(cb).isNotNull();
            assertThat(HardFilter.violations(ca, cb, input)).isEmpty();
        }
    }

    private static void assertNoPrivateLeak(String body) {
        assertThat(body).doesNotContain("CONSENT_REVOKED", "CONSENT_UNKNOWN", "HARD_BLOCKLIST", "hold_reason", "deleting");
    }

    private record Draft(long matchId, long low, long high) {}
}
