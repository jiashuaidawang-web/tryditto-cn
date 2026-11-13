package cn.aiditto.match;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.User;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import cn.aiditto.onboarding.AnswerStore;
import cn.aiditto.profile.ProfileProjection;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code synthetic/users-v1.json}. Idempotent. Never overwrites a real user who already finished
 * onboarding, and never sends anything to the fictional phones or emails.
 */
@Service
public class SyntheticLoader {
    private final AccountService accounts;
    private final AnswerStore answers;
    private final ProfileProjection profiles;
    private final Crypto crypto;
    private final IdentifierGenerator ids;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public SyntheticLoader(AccountService accounts, AnswerStore answers, ProfileProjection profiles, Crypto crypto,
                           IdentifierGenerator ids, Clock clock, PlatformTransactionManager txm,
                           org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.answers = answers;
        this.profiles = profiles;
        this.crypto = crypto;
        this.ids = ids;
        this.clock = clock;
        this.tx = new TransactionTemplate(txm);
        this.jdbc = jdbc;
    }

    public Map<String, Integer> load() {
        JsonNode root = Json.readTree(readFixture());
        int index = 0;
        int loaded = 0;
        int skipped = 0;
        for (JsonNode node : root.get("users")) {
            int i = index++;
            boolean wrote = Boolean.TRUE.equals(tx.execute(s -> one(node, i)));
            if (wrote) loaded++;
            else skipped++;
        }
        return Map.of("loaded", loaded, "skipped", skipped);
    }

    private boolean one(JsonNode node, int index) {
        String phone = node.hasNonNull("phone") ? node.get("phone").asString() : SyntheticPhones.fictional(index);
        String kind = node.get("kind").asString();
        String cohort = "demo".equals(kind) ? "demo" : "synthetic";
        byte[] hash = crypto.hash(phone);
        List<Long> existing = jdbc.query("SELECT id FROM `user` WHERE phone_hash = ?", (rs, n) -> rs.getLong(1), hash);
        Long userId = existing.isEmpty() ? null : existing.get(0);
        if (userId != null) {
            String current = jdbc.queryForObject("SELECT cohort FROM `user` WHERE id = ?", String.class, userId);
            Instant done = jdbc.queryForObject("SELECT onboarding_completed_at FROM `user` WHERE id = ?",
                    (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), userId);
            boolean real = current == null || "real".equals(current);
            if (real && (!SyntheticPhones.isDemo(phone) || done != null)) return false;
            Integer profile = jdbc.queryForObject("SELECT COUNT(*) FROM profile WHERE user_id = ?", Integer.class, userId);
            if (!real && profile != null && profile > 0) return false;
        }
        Instant now = Times.millis(clock.instant());
        if (userId == null) {
            User created = accounts.loginUser(phone, null);
            userId = created.id;
        }
        String handle = node.get("handle").asString();
        jdbc.update("""
                UPDATE `user` SET cohort = ?, is_synthetic = ?, handle = ?, status = 'active',
                onboarding_completed_at = COALESCE(onboarding_completed_at, ?), updated_at = ? WHERE id = ?
                """, cohort, "synthetic".equals(cohort), handle, now, now, userId);
        Map<String, Object> values = new LinkedHashMap<>();
        node.get("answers").properties().forEach(e -> {
            Object v = Json.toPlain(e.getValue());
            if (v != null) values.put(e.getKey(), v);
        });
        long id = userId;
        answers.putAll(id, values);
        profiles.write(id, values, 2);
        email(id, values, now);
        card(id, node.get("card"), node.get("aiConstraints"));
        return true;
    }

    private void email(long userId, Map<String, Object> values, Instant now) {
        Object raw = values.getOrDefault("schoolEmail", values.get("personalEmail"));
        if (!(raw instanceof String email) || !email.contains("@")) return;
        String kind = values.containsKey("schoolEmail") ? "school" : "personal";
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM email_verification WHERE user_id = ? AND kind = ?", Integer.class, userId, kind);
        if (n != null && n > 0) return;
        String domain = email.substring(email.indexOf('@') + 1);
        jdbc.update("""
                INSERT INTO email_verification (id, user_id, kind, email_enc, email_hash, domain, verified_at)
                VALUES (?,?,?,?,?,?,?)
                """, newId(), userId, kind, crypto.encrypt(email), crypto.hash(email), domain, now);
    }

    private void card(long userId, JsonNode card, JsonNode ai) {
        if (card == null || card.isNull()) return;
        String persona = Json.write(Json.toPlain(card));
        String avatar = card.has("avatar") ? Json.write(Json.toPlain(card.get("avatar"))) : null;
        byte[] wechat = card.hasNonNull("wechat") ? crypto.encrypt(card.get("wechat").asString()) : null;
        String constraints = ai != null && !ai.isNull() ? Json.write(Json.toPlain(ai)) : null;
        jdbc.update("""
                UPDATE profile SET persona = ?, avatar_spec = ?, wechat_enc = ?, ai_constraints = ?, ai_status = ?
                WHERE user_id = ?
                """, persona, avatar, wechat, constraints, constraints == null ? "none" : "ok", userId);
    }

    private long newId() {
        return ((Number) ids.nextId(new Object())).longValue();
    }

    private static String readFixture() {
        try {
            return new ClassPathResource("synthetic/users-v1.json").getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("synthetic/users-v1.json", e);
        }
    }
}
