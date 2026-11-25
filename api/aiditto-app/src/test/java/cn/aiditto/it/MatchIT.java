package cn.aiditto.it;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.domain.week.WeekState;
import cn.aiditto.domain.week.WeekStep;
import cn.aiditto.match.MatchFlow;
import cn.aiditto.match.People;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.match.SyntheticPhones;
import cn.aiditto.match.WeekRunner;
import cn.aiditto.infra.crypto.Crypto;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Match-now, cohort isolation, demo reset, batch resume and the deadline ticker. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatchIT extends ApiIT {
    @Autowired SyntheticLoader loader;
    @Autowired WeekRunner weeks;
    @Autowired MatchFlow matches;
    @Autowired People people;
    @Autowired Crypto crypto;

    @Test
    @Order(1)
    void matchNowIsFastAndClean() {
        Map<String, Integer> loaded = loader.load();
        assertThat(loaded.get("loaded")).isEqualTo(200);
        assertThat(loader.load().get("loaded")).isZero();
        TestConsents.grantAll(jdbc);

        Instant now = Instant.now();
        long t0 = System.nanoTime();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        Map<String, Object> bj;
        Map<String, Object> sh;
        try {
            var bjRun = pool.submit(() -> weeks.runManual("bj", now));
            var shRun = pool.submit(() -> weeks.runManual("sh", now));
            bj = bjRun.get();
            sh = shRun.get();
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            pool.shutdown();
        }
        long millis = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("match-now-ms " + millis);
        assertThat(millis).as("match-now millis").isLessThan(10_000);
        assertThat(bj.get("state")).isEqualTo("published");
        assertThat(sh.get("state")).isEqualTo("published");

        List<Map<String, Object>> pairs = jdbc.queryForList("""
                SELECT m.id, m.user_low, m.user_high, m.week_id, a.cohort AS low_cohort, b.cohort AS high_cohort,
                       a.phone_hash AS low_phone, b.phone_hash AS high_phone
                FROM "match" m
                JOIN "user" a ON a.id = m.user_low
                JOIN "user" b ON b.id = m.user_high
                """);
        assertThat(pairs).isNotEmpty();
        LocalDate week = WeekCalendar.weekStartFor(now);
        MatchingInput input = MatchingInput.of(week, List.of(), 1);
        for (Map<String, Object> pair : pairs) {
            String low = pair.get("low_cohort").toString();
            String high = pair.get("high_cohort").toString();
            assertThat(low.equals("synthetic") && high.equals("synthetic")).isFalse();
            assertThat(low.equals("real") && !high.equals("real")).isFalse();
            assertThat(high.equals("real") && !low.equals("real")).isFalse();
            long a = ((Number) pair.get("user_low")).longValue();
            long b = ((Number) pair.get("user_high")).longValue();
            Candidate ca = people.ungated(a, week, now);
            Candidate cb = people.ungated(b, week, now);
            assertThat(ca).isNotNull();
            assertThat(cb).isNotNull();
            assertThat(HardFilter.violations(ca, cb, input)).as("pair %s", pair.get("id")).isEmpty();
        }
        for (String phone : SyntheticPhones.DEMO) {
            Integer n = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM match_side s
                    JOIN "user" u ON u.id = s.user_id
                    JOIN "match" m ON m.id = s.match_id
                    WHERE u.phone_hash = ? AND m.state <> 'draft'
                    """, Integer.class, crypto.hash(phone));
            assertThat(n).as("demo %s matched", phone).isPositive();
        }
    }

    @Test
    @Order(2)
    void realUsersAreNotPairedWithSyntheticOnesAndResetLeavesThemAlone() {
        String manPhone = randomPhone();
        String womanPhone = randomPhone();
        onboard(manPhone, "man", List.of("women"), "real-man-" + manPhone + "@pku.edu.cn", "阿实");
        onboard(womanPhone, "woman", List.of("men"), "real-woman-" + womanPhone + "@pku.edu.cn", "阿真");
        long answersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_answer a JOIN \"user\" u ON u.id = a.user_id WHERE u.cohort = 'real'", Long.class);

        String admin = login("13800000001");
        Resp run = call("POST", "/api/admin/match-runs", Map.of("pool", "bj", "publish", true), admin);
        assertThat(run.status()).as("%s", run.body()).isEqualTo(200);
        assertThat(run.body().get("millis").asLong()).isLessThan(10_000);

        Integer mixed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM "match" m
                JOIN "user" a ON a.id = m.user_low JOIN "user" b ON b.id = m.user_high
                WHERE (a.cohort = 'real' AND b.cohort <> 'real') OR (b.cohort = 'real' AND a.cohort <> 'real')
                   OR (a.cohort = 'synthetic' AND b.cohort = 'synthetic')
                """, Integer.class);
        assertThat(mixed).isZero();

        String man = login(manPhone);
        JsonNode card = call("GET", "/api/matches/current", null, man).body();
        if (card.get("partner") != null && !card.get("partner").isNull()) {
            String handle = card.get("partner").get("id").asString();
            long asId = handle.startsWith("u") ? Long.parseLong(handle.substring(1)) : -1L;
            String cohort = jdbc.queryForObject("SELECT cohort FROM \"user\" WHERE handle = ? OR id = ?", String.class, handle, asId);
            assertThat(cohort).isEqualTo("real");
        }

        Resp reset = call("POST", "/api/admin/demo/reset", Map.of(), admin);
        assertThat(reset.status()).isEqualTo(200);
        long answersAfter = jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_answer a JOIN \"user\" u ON u.id = a.user_id WHERE u.cohort = 'real'", Long.class);
        assertThat(answersAfter).isEqualTo(answersBefore);
        assertThat(call("GET", "/api/onboarding/progress", null, man).body().get("answers").get("name").asString()).isEqualTo("阿实");
    }

    @Test
    @Order(3)
    void partnerActionRefusesARealPartnerAndStepsResume() {
        String demo = login("13800000002");
        Resp stage = call("POST", "/api/demo/match/stage", Map.of("stage", "awaitingPartner"), demo);
        assertThat(stage.status()).as("%s", stage.body()).isEqualTo(204);
        JsonNode card = call("GET", "/api/matches/current", null, demo).body();
        assertThat(card.get("match").get("stage").asString()).isEqualTo("awaitingPartner");
        long matchId = Long.parseLong(card.get("match").get("id").asString());
        long partnerId = partnerUser(matchId, userId(demo));
        jdbc.update("UPDATE \"user\" SET cohort = 'real' WHERE id = ?", partnerId);
        try {
            Resp refused = call("POST", "/api/demo/match/partner-action", Map.of("action", "confirm"), demo);
            assertThat(refused.status()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT state FROM \"match\" WHERE id = ?", String.class, matchId)).isEqualTo("published");
        } finally {
            jdbc.update("UPDATE \"user\" SET cohort = 'synthetic' WHERE id = ?", partnerId);
        }

        long weekId = jdbc.queryForObject("SELECT week_id FROM \"match\" WHERE id = ?", Long.class, matchId);
        int copies = jdbc.queryForObject("SELECT COUNT(*) FROM match_copy WHERE match_id = ?", Integer.class, matchId);
        assertThat(weeks.rerun(weekId, WeekStep.COPY, Instant.now()).state()).isEqualTo(WeekState.PUBLISHED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match_copy WHERE match_id = ?", Integer.class, matchId)).isEqualTo(copies);

        jdbc.update("UPDATE week SET state = 'failed', failed_step = 'copy' WHERE id = ?", weekId);
        assertThat(weeks.step(weekId, Instant.now()).state()).isEqualTo(WeekState.COPIED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match_copy WHERE match_id = ?", Integer.class, matchId)).isEqualTo(copies);
        assertThat(weeks.step(weekId, Instant.now()).state()).isEqualTo(WeekState.PUBLISHED);
    }

    @Test
    @Order(4)
    void deadlineTickIsIdempotentAndSyntheticPhonesGetNoCode() {
        Long matchId = jdbc.queryForObject("SELECT id FROM \"match\" WHERE state = 'published' ORDER BY id LIMIT 1", Long.class);
        assertThat(matchId).isNotNull();
        jdbc.update("UPDATE \"match\" SET respond_due = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), matchId);
        long low = jdbc.queryForObject("SELECT user_low FROM \"match\" WHERE id = ?", Long.class, matchId);
        int before = jdbc.queryForObject("SELECT no_response_streak FROM \"user\" WHERE id = ?", Integer.class, low);
        matches.tickDeadlines(Instant.now());
        assertThat(jdbc.queryForObject("SELECT state FROM \"match\" WHERE id = ?", String.class, matchId)).isEqualTo("expired");
        int after = jdbc.queryForObject("SELECT no_response_streak FROM \"user\" WHERE id = ?", Integer.class, low);
        assertThat(after).isEqualTo(before + 1);
        matches.tickDeadlines(Instant.now());
        assertThat(jdbc.queryForObject("SELECT no_response_streak FROM \"user\" WHERE id = ?", Integer.class, low)).isEqualTo(after);

        int codes = jdbc.queryForObject("SELECT COUNT(*) FROM auth_code", Integer.class);
        Resp sent = call("POST", "/api/auth/sms/send", Map.of("phone", SyntheticPhones.fictional(0)), null);
        assertThat(sent.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_code", Integer.class)).isEqualTo(codes);
    }

    private void onboard(String phone, String gender, List<String> wants, String email, String name) {
        String token = login(phone);
        answer(token, "isStudent", "no");
        answer(token, "name", name);
        answer(token, "gender", gender);
        answer(token, "expectedGender", wants);
        answer(token, "birthday", "1986-03-02");
        answer(token, "hobbies", List.of("music", "reading"));
        answer(token, "musicSound", List.of("indie"));
        answer(token, "readingGenres", List.of("scifi"));
        answer(token, "location", Map.of("placeId", "wudaokou", "mode", "transit", "minutes", 45));
        answer(token, "expectedAgeRange", List.of(35, 50));
        answer(token, "relationshipLooking", List.of("serious"));
        answer(token, "matchingStyle", "steady");
        answer(token, "occupation", "工程师");
        answer(token, "educationLevel", "college");
        answer(token, "educationImportance", "none");
        answer(token, "firstDateBudget", "20to50");
        answer(token, "personalEmail", email);
        answer(token, "religion", List.of("buddhist"));
        assertThat(call("POST", "/api/auth/email/verify", Map.of("email", email, "code", DEMO_CODE), token).body().get("verified").asBoolean()).isTrue();
        answer(token, "emailVerify", DEMO_CODE);
        uploadPhoto(token, "profile", 800, 1000);
        uploadPhoto(token, "profile", 900, 1100);
        assertThat(call("POST", "/api/onboarding/complete", null, token).status()).as("complete %s", phone).isEqualTo(204);
    }

    private long userId(String token) {
        return Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
    }

    private long partnerUser(long matchId, long userId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT user_low, user_high FROM \"match\" WHERE id = ?", matchId);
        long low = ((Number) row.get("user_low")).longValue();
        long high = ((Number) row.get("user_high")).longValue();
        return low == userId ? high : low;
    }
}
