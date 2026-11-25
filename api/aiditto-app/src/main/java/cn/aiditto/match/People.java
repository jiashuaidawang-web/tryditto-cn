package cn.aiditto.match;

import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.matching.AiConstraints;
import cn.aiditto.domain.matching.Budget;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.Cohort;
import cn.aiditto.domain.matching.EducationLevel;
import cn.aiditto.domain.matching.Gender;
import cn.aiditto.domain.matching.GenderPref;
import cn.aiditto.domain.matching.HeightPref;
import cn.aiditto.domain.matching.Importance;
import cn.aiditto.domain.matching.Location;
import cn.aiditto.domain.matching.Looking;
import cn.aiditto.domain.matching.MatchingStyle;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.matching.SchoolYear;
import cn.aiditto.domain.matching.TravelMode;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns profile rows into matcher candidates and copy facts. No phones or emails leave this class. */
@Component
public class People {
    private static final String OPEN = """
            SELECT s.user_id FROM match_side s JOIN "match" m ON m.id = s.match_id
            WHERE m.state NOT IN ('declined','expired','unscheduled','cancelled','closed','halted')
            """;

    private final JdbcTemplate jdbc;
    private final Crypto crypto;

    public People(JdbcTemplate jdbc, Crypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    /** One onboarded person in a city, whether or not they pass {@link PoolGate}. */
    public record Member(long userId, int profileVersion, PoolGate.ExcludeReason excluded, Candidate candidate,
                         ProfileFacts facts, String handle, String cohort, String personaJson, byte[] wechatEnc,
                         String placeNameZh, String placeNameEn, String occupation, int heightCm, List<String> hobbies,
                         PoolGate.Applicant applicant) {}

    public List<Member> city(String pool, LocalDate week, Instant now) {
        Set<Long> open = new HashSet<>(jdbc.query(OPEN, (rs, n) -> rs.getLong(1)));
        Map<Long, Set<String>> blocks = hashes("SELECT user_id, value_hash FROM block_contact");
        Map<Long, Set<String>> emails = hashes("SELECT user_id, email_hash FROM email_verification");
        List<Member> out = new ArrayList<>();
        jdbc.query("""
                SELECT u.id, u.cohort, u.status, u.handle, u.empty_week_streak, u.suspended_until, u.phone_hash,
                       u.onboarding_completed_at, u.photo_issue, u.is_synthetic,
                       p.version, p.name, p.gender, p.birthday, p.age_min, p.age_max, p.height_cm, p.height_any,
                       p.height_min, p.height_max, p.is_student, p.school_year, p.education_level, p.education_importance,
                       p.budget, p.matching_style, p.relationship_looking, p.hobbies, p.hobby_details, p.place_id,
                       p.travel_mode, p.travel_minutes, p.first_message, p.photo_count, p.expected_genders_enc,
                       p.occupation, p.ai_constraints, p.ai_status, p.persona, p.wechat_enc,
                       pl.lat, pl.lng, pl.name_zh, pl.name_en, pl.city
                FROM "user" u
                JOIN profile p ON p.user_id = u.id
                JOIN place pl ON pl.id = p.place_id
                WHERE pl.city = ? AND u.status IN ('active','paused','suspended')
                """, rs -> {
            while (rs.next()) out.add(member(rs, week, now, open, blocks, emails, true));
            return null;
        }, pool);
        return out;
    }

    public Member one(long userId) {
        List<Member> found = new ArrayList<>();
        jdbc.query("""
                SELECT u.id, u.cohort, u.status, u.handle, u.empty_week_streak, u.suspended_until, u.phone_hash,
                       u.onboarding_completed_at, u.photo_issue, u.is_synthetic,
                       p.version, p.name, p.gender, p.birthday, p.age_min, p.age_max, p.height_cm, p.height_any,
                       p.height_min, p.height_max, p.is_student, p.school_year, p.education_level, p.education_importance,
                       p.budget, p.matching_style, p.relationship_looking, p.hobbies, p.hobby_details, p.place_id,
                       p.travel_mode, p.travel_minutes, p.first_message, p.photo_count, p.expected_genders_enc,
                       p.occupation, p.ai_constraints, p.ai_status, p.persona, p.wechat_enc,
                       pl.lat, pl.lng, pl.name_zh, pl.name_en, pl.city
                FROM "user" u
                JOIN profile p ON p.user_id = u.id
                LEFT JOIN place pl ON pl.id = p.place_id
                WHERE u.id = ?
                """, rs -> {
            if (rs.next()) found.add(member(rs, LocalDate.now(), Instant.now(), Set.of(), Map.of(), Map.of(), true));
            return null;
        }, userId);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Candidate as the solver saw them, ignoring an open match created by this week's result. */
    public Candidate ungated(long userId, LocalDate week, Instant now) {
        Map<Long, Set<String>> blocks = hashes("SELECT user_id, value_hash FROM block_contact");
        Map<Long, Set<String>> emails = hashes("SELECT user_id, email_hash FROM email_verification");
        List<Member> found = new ArrayList<>();
        jdbc.query("""
                SELECT u.id, u.cohort, u.status, u.handle, u.empty_week_streak, u.suspended_until, u.phone_hash,
                       u.onboarding_completed_at, u.photo_issue, u.is_synthetic,
                       p.version, p.name, p.gender, p.birthday, p.age_min, p.age_max, p.height_cm, p.height_any,
                       p.height_min, p.height_max, p.is_student, p.school_year, p.education_level, p.education_importance,
                       p.budget, p.matching_style, p.relationship_looking, p.hobbies, p.hobby_details, p.place_id,
                       p.travel_mode, p.travel_minutes, p.first_message, p.photo_count, p.expected_genders_enc,
                       p.occupation, p.ai_constraints, p.ai_status, p.persona, p.wechat_enc,
                       pl.lat, pl.lng, pl.name_zh, pl.name_en, pl.city
                FROM "user" u
                JOIN profile p ON p.user_id = u.id
                LEFT JOIN place pl ON pl.id = p.place_id
                WHERE u.id = ?
                """, rs -> {
            if (rs.next()) found.add(member(rs, week, now, Set.of(), blocks, emails, false));
            return null;
        }, userId);
        return found.isEmpty() ? null : found.get(0).candidate();
    }

    /**
     * Profile, pool facts and block hashes for a republish check. Open matches other than
     * {@code exceptMatchId} still count; that draft must not exclude itself.
     */
    public Member snapshot(long userId, LocalDate week, Instant now, long exceptMatchId) {
        Map<Long, Set<String>> blocks = hashes("SELECT user_id, value_hash FROM block_contact");
        Map<Long, Set<String>> emails = hashes("SELECT user_id, email_hash FROM email_verification");
        Set<Long> open = openExcept(exceptMatchId);
        List<Member> found = new ArrayList<>();
        jdbc.query("""
                SELECT u.id, u.cohort, u.status, u.handle, u.empty_week_streak, u.suspended_until, u.phone_hash,
                       u.onboarding_completed_at, u.photo_issue, u.is_synthetic,
                       p.version, p.name, p.gender, p.birthday, p.age_min, p.age_max, p.height_cm, p.height_any,
                       p.height_min, p.height_max, p.is_student, p.school_year, p.education_level, p.education_importance,
                       p.budget, p.matching_style, p.relationship_looking, p.hobbies, p.hobby_details, p.place_id,
                       p.travel_mode, p.travel_minutes, p.first_message, p.photo_count, p.expected_genders_enc,
                       p.occupation, p.ai_constraints, p.ai_status, p.persona, p.wechat_enc,
                       pl.lat, pl.lng, pl.name_zh, pl.name_en, pl.city
                FROM "user" u
                JOIN profile p ON p.user_id = u.id
                LEFT JOIN place pl ON pl.id = p.place_id
                WHERE u.id = ?
                """, rs -> {
            if (rs.next()) found.add(member(rs, week, now, open, blocks, emails, false));
            return null;
        }, userId);
        return found.isEmpty() ? null : found.get(0);
    }

    public String cohortOf(long userId) {
        List<String> rows = jdbc.query("SELECT cohort FROM \"user\" WHERE id = ?", (rs, n) -> rs.getString(1), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String nameOf(long userId) {
        List<String> rows = jdbc.query("SELECT name FROM profile WHERE user_id = ?", (rs, n) -> rs.getString(1), userId);
        return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
    }

    private Set<Long> openExcept(long matchId) {
        return new HashSet<>(jdbc.query(OPEN + " AND m.id <> ?", (rs, n) -> rs.getLong(1), matchId));
    }

    private Member member(ResultSet rs, LocalDate week, Instant now, Set<Long> open,
                          Map<Long, Set<String>> blocks, Map<Long, Set<String>> emails, boolean gate) throws SQLException {
        long id = rs.getLong("id");
        String cohort = rs.getString("cohort");
        boolean synthetic = "synthetic".equals(cohort) || rs.getBoolean("is_synthetic");
        int photos = rs.getInt("photo_count");
        if ("demo".equals(cohort) || synthetic) photos = Math.max(photos, PoolGate.MIN_PHOTOS);
        Timestamp suspended = rs.getTimestamp("suspended_until");
        Instant suspendedUntil = suspended == null ? null : suspended.toInstant();
        String status = rs.getString("status");
        boolean paused = "paused".equals(status);
        if ("suspended".equals(status) && (suspendedUntil == null || !suspendedUntil.isAfter(now))) {
            suspendedUntil = now.plusSeconds(3600);
        }
        PoolGate.Applicant applicant = new PoolGate.Applicant(
                date(rs, "birthday"),
                "active".equals(status) || paused || "suspended".equals(status),
                rs.getTimestamp("onboarding_completed_at") != null,
                synthetic,
                photos,
                rs.getString("photo_issue") != null,
                paused,
                suspendedUntil,
                open.contains(id));
        PoolGate.ExcludeReason why = gate ? PoolGate.check(applicant, week, now).orElse(null) : null;
        String gender = rs.getString("gender");
        LocalDate birthday = date(rs, "birthday");
        Double lat = number(rs.getObject("lat"));
        Double lng = number(rs.getObject("lng"));
        Candidate candidate = null;
        if ((why == null || !gate) && gender != null && birthday != null && lat != null && lng != null) {
            candidate = candidate(rs, id, cohort, birthday, open.contains(id), blocks, emails);
        } else if (why == null) {
            why = PoolGate.ExcludeReason.INCOMPLETE;
        }
        return new Member(id, rs.getInt("version"), why, candidate, facts(rs, id), rs.getString("handle"),
                cohort == null ? "real" : cohort, rs.getString("persona"), rs.getBytes("wechat_enc"),
                rs.getString("name_zh"), rs.getString("name_en"), rs.getString("occupation"),
                rs.getObject("height_cm") == null ? 0 : rs.getInt("height_cm"), stringList(rs.getString("hobbies")),
                applicant);
    }

    private Candidate candidate(ResultSet rs, long id, String cohort, LocalDate birthday, boolean open,
                                Map<Long, Set<String>> blocks, Map<Long, Set<String>> emails) throws SQLException {
        Set<String> contacts = new LinkedHashSet<>();
        byte[] phone = rs.getBytes("phone_hash");
        if (phone != null) contacts.add(HexFormat.of().formatHex(phone));
        contacts.addAll(emails.getOrDefault(id, Set.of()));
        boolean student = rs.getBoolean("is_student");
        String year = rs.getString("school_year");
        int height = rs.getObject("height_cm") == null ? 170 : rs.getInt("height_cm");
        boolean any = rs.getBoolean("height_any");
        HeightPref pref = any ? HeightPref.ANY
                : HeightPref.between(rs.getObject("height_min") == null ? 150 : rs.getInt("height_min"),
                rs.getObject("height_max") == null ? 200 : rs.getInt("height_max"));
        int minutes = rs.getObject("travel_minutes") == null ? 45 : rs.getInt("travel_minutes");
        String mode = rs.getString("travel_mode");
        return Candidate.builder(id)
                .pool(rs.getString("city"))
                .gender(Gender.fromId(rs.getString("gender")))
                .wants(wants(rs.getBytes("expected_genders_enc")))
                .birthday(birthday)
                .ageRange(rs.getInt("age_min"), rs.getInt("age_max"))
                .heightCm(height)
                .heightPref(pref)
                .student(student)
                .schoolYear(student && year != null ? enumOrNull(SchoolYear.class, year) : null)
                .education(enumOrNull(EducationLevel.class, rs.getString("education_level")),
                        enumOr(Importance.class, rs.getString("education_importance"), Importance.NONE))
                .budget(enumOr(Budget.class, rs.getString("budget"), Budget.NO_PREF))
                .style(enumOr(MatchingStyle.class, rs.getString("matching_style"), MatchingStyle.STEADY))
                .looking(looking(rs.getString("relationship_looking")))
                .hobbies(new LinkedHashSet<>(stringList(rs.getString("hobbies"))))
                .hobbyDetails(hobbyOptions(rs.getString("hobby_details")))
                .location(new Location(rs.getString("place_id"), rs.getDouble("lat"), rs.getDouble("lng"),
                        enumOr(TravelMode.class, mode, TravelMode.TRANSIT), Math.max(1, minutes)))
                .emptyWeekStreak(rs.getInt("empty_week_streak"))
                .contactHashes(contacts)
                .blockedHashes(blocks.getOrDefault(id, Set.of()))
                .ai(ai(rs.getString("ai_status"), rs.getString("ai_constraints")))
                .hasOpenMatch(open)
                .cohort(Cohort.fromDb(cohort))
                .build();
    }

    private ProfileFacts facts(ResultSet rs, long id) throws SQLException {
        Map<String, Set<String>> options = new HashMap<>();
        Map<String, String> texts = new HashMap<>();
        splitHobbies(rs.getString("hobby_details"), options, texts);
        return new ProfileFacts(id, rs.getString("name"), rs.getBoolean("is_student"), rs.getString("school_year"),
                new LinkedHashSet<>(stringList(rs.getString("hobbies"))), options, texts,
                new LinkedHashSet<>(stringList(rs.getString("relationship_looking"))),
                rs.getString("matching_style"), rs.getString("budget"), rs.getString("first_message"));
    }

    private Set<GenderPref> wants(byte[] enc) {
        if (enc == null) return Set.of(GenderPref.EVERYONE);
        Set<GenderPref> out = new LinkedHashSet<>();
        for (String id : Json.readStringList(crypto.decrypt(enc))) {
            try {
                out.add(GenderPref.fromId(id));
            } catch (IllegalArgumentException ignored) {
                // skip an option the bank no longer has
            }
        }
        return out.isEmpty() ? Set.of(GenderPref.EVERYONE) : out;
    }

    private static Set<Looking> looking(String json) {
        Set<Looking> out = new LinkedHashSet<>();
        for (String id : stringList(json)) {
            Looking v = enumOrNull(Looking.class, id);
            if (v != null) out.add(v);
        }
        return out.isEmpty() ? Set.of(Looking.NOT_SURE) : out;
    }

    private static AiConstraints ai(String status, String json) {
        if (!"ok".equals(status) || json == null || json.isBlank()) return AiConstraints.NONE;
        Map<String, Object> m = Json.readMap(json);
        return new AiConstraints(intOrNull(m.get("minHeight")), intOrNull(m.get("maxHeight")),
                intOrNull(m.get("minAge")), intOrNull(m.get("maxAge")),
                Boolean.TRUE.equals(m.get("studentsOnly")), Boolean.TRUE.equals(m.get("nonStudentsOnly")));
    }

    private Map<Long, Set<String>> hashes(String sql) {
        Map<Long, Set<String>> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            byte[] hash = rs.getBytes(2);
            if (hash != null) out.computeIfAbsent(rs.getLong(1), k -> new LinkedHashSet<>()).add(HexFormat.of().formatHex(hash));
        });
        return out;
    }

    private static void splitHobbies(String json, Map<String, Set<String>> options, Map<String, String> texts) {
        if (json == null || json.isBlank()) return;
        Map<String, Object> raw = Json.readMap(json);
        raw.forEach((k, v) -> {
            if (v instanceof String s && !s.isBlank()) texts.put(k, s);
            else if (v instanceof List<?> list) {
                Set<String> ids = new LinkedHashSet<>();
                for (Object o : list) if (o != null) ids.add(o.toString());
                if (!ids.isEmpty()) options.put(k, ids);
            }
        });
    }

    private static Map<String, Set<String>> hobbyOptions(String json) {
        Map<String, Set<String>> options = new HashMap<>();
        splitHobbies(json, options, new HashMap<>());
        return options;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String id) {
        if (id == null || id.isBlank()) return null;
        for (E v : type.getEnumConstants()) {
            try {
                Object got = type.getMethod("id").invoke(v);
                if (id.equals(got)) return v;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String id, E fallback) {
        E v = enumOrNull(type, id);
        return v == null ? fallback : v;
    }

    private static List<String> stringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        return Json.readStringList(json);
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static Integer intOrNull(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    /** JDBC returns DECIMAL columns as BigDecimal. */
    private static Double number(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }
}
