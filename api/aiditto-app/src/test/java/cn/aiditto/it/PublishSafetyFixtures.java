package cn.aiditto.it;

import cn.aiditto.account.AccountService;
import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.HardRule;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.matching.Weights;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.match.MatchDb;
import cn.aiditto.match.People;
import cn.aiditto.profile.ProfileProjection;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disposable adults for publish-safety tests. Each scene has its own users, week and draft.
 *
 * <p>Two {@code synthetic}-cohort users cannot pass {@link HardFilter#cohortsCompatible}: a synthetic
 * user may only be paired with a demo account. These fixtures are therefore {@code demo} cohort, which
 * is allowed to match another demo, and they still go through {@code ProfileProjection} and
 * {@code PublishGuard}. They are not marked published in advance.
 */
final class PublishSafetyFixtures {
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final ProfileProjection profiles;
    private final MatchingConsentService consents;
    private final MatchDb db;
    private final People people;

    PublishSafetyFixtures(JdbcTemplate jdbc, AccountService accounts, ProfileProjection profiles,
                          MatchingConsentService consents, MatchDb db, People people) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.profiles = profiles;
        this.consents = consents;
        this.db = db;
        this.people = people;
    }

    /** One granted draft whose two adults pass the real eligibility gate. */
    Scene grantedDraft() {
        Adult low = adult("man", List.of("women"));
        Adult high = adult("woman", List.of("men"));
        return draft(low, high);
    }

    /** Three granted adults. Callers pair them; this method does not insert a match. */
    List<Adult> threeAdults() {
        return List.of(
                adult("man", List.of("everyone")),
                adult("woman", List.of("everyone")),
                adult("man", List.of("everyone")));
    }

    void close(long weekId) {
        jdbc.update("""
                UPDATE week SET state = 'closed'
                WHERE id = ? AND state IN ('open','locked','solved','copied','failed')
                """, weekId);
    }

    private Scene draft(Adult first, Adult second) {
        long low = Math.min(first.id(), second.id());
        long high = Math.max(first.id(), second.id());
        String lowPhone = low == first.id() ? first.phone() : second.phone();
        String highPhone = high == first.id() ? first.phone() : second.phone();
        LocalDate weekStart = WeekCalendar.weekStartFor(Instant.now());
        int runNo = db.nextManualRun("bj", weekStart);
        long weekId = db.insertWeek("bj", weekStart, "manual", runNo, Instant.now(), Instant.now());
        long matchId = db.newId();
        Instant now = Instant.now();
        db.insertDraft(matchId, weekId, low, high, 1, "{}", now);
        db.insertSide(matchId, weekId, low);
        db.insertSide(matchId, weekId, high);
        db.insertPlan(matchId, "[]", "{}", "coffee", "");
        requireEligible(matchId, low, high, weekStart);
        return new Scene(weekId, matchId, low, high, lowPhone, highPhone);
    }

    private Adult adult(String gender, List<String> wants) {
        int n = SEQ.getAndIncrement();
        String phone = "136" + String.format("%08d", n);
        long id = accounts.loginUser(phone, null).id;
        profiles.write(id, answers(gender, wants), 2);
        jdbc.update("""
                UPDATE "user" SET cohort = 'demo', is_synthetic = TRUE, status = 'active', handle = ?,
                onboarding_completed_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """, "psf" + n, id);
        consents.grant(id);
        return new Adult(id, phone);
    }

    private static Map<String, Object> answers(String gender, List<String> wants) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("name", "夹具");
        answers.put("gender", gender);
        answers.put("expectedGender", wants);
        answers.put("birthday", "1998-06-01");
        answers.put("expectedAgeRange", List.of(25, 40));
        answers.put("height", Map.of("cm", 170));
        answers.put("heightRange", Map.of("any", true));
        answers.put("isStudent", "no");
        answers.put("occupation", "设计");
        answers.put("educationLevel", "college");
        answers.put("educationImportance", "none");
        answers.put("firstDateBudget", "noPref");
        answers.put("matchingStyle", "steady");
        answers.put("relationshipLooking", List.of("serious"));
        answers.put("hobbies", List.of("music"));
        answers.put("location", Map.of("placeId", "wudaokou", "mode", "transit", "minutes", 60));
        answers.put("firstMessage", "你好");
        return answers;
    }

    /** Setup check only. The test still publishes through {@code PublishGuard}. */
    private void requireEligible(long matchId, long low, long high, LocalDate weekStart) {
        Instant now = Instant.now();
        People.Member a = people.snapshot(low, weekStart, now, matchId);
        People.Member b = people.snapshot(high, weekStart, now, matchId);
        if (a == null || b == null || a.candidate() == null || b.candidate() == null) {
            throw new IllegalStateException("fixture profile is incomplete");
        }
        refuse(a.applicant(), weekStart, now);
        refuse(b.applicant(), weekStart, now);
        if (consents.current(low).consentStatus() != ConsentStatus.GRANTED
                || consents.current(high).consentStatus() != ConsentStatus.GRANTED) {
            throw new IllegalStateException("fixture consent is not GRANTED");
        }
        Candidate left = a.candidate();
        Candidate right = b.candidate();
        MatchingInput input = new MatchingInput(weekStart, List.of(left, right), Set.of(), Set.of(),
                Weights.W1, weekStart.toEpochDay(), true);
        List<HardRule> rules = HardFilter.violations(left, right, input);
        if (!rules.isEmpty()) throw new IllegalStateException("fixture fails hard filter: " + rules);
    }

    private static void refuse(PoolGate.Applicant applicant, LocalDate weekStart, Instant now) {
        PoolGate.check(applicant, weekStart, now).ifPresent(reason -> {
            throw new IllegalStateException("fixture fails pool gate: " + reason);
        });
    }

    record Adult(long id, String phone) {}

    record Scene(long weekId, long matchId, long low, long high, String lowPhone, String highPhone) {}
}
