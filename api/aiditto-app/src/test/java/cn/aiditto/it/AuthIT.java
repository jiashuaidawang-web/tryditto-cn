package cn.aiditto.it;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIT extends ApiIT {

    @Test
    void schemaMigratesFromZeroAndSeedsPlaces() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE kind = 'area'", Integer.class)).isEqualTo(21);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4");
    }

    @Test
    void sendCodeIsRateLimited() {
        String phone = randomPhone();
        Resp first = call("POST", "/api/auth/sms/send", Map.of("phone", phone), null);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("cooldown").asInt()).isEqualTo(60);

        Resp again = call("POST", "/api/auth/sms/send", Map.of("phone", phone), null);
        assertThat(again.status()).isEqualTo(429);
        assertThat(again.code()).isEqualTo("SMS_TOO_FREQUENT");
        assertThat(again.body().get("retryAfter").asInt()).isBetween(1, 61);
        assertThat(again.headers().firstValue("Retry-After")).isPresent();
        assertThat(again.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    @Test
    void badInputMapsToTheFrontendErrorCodes() {
        assertThat(call("POST", "/api/auth/login", Map.of("phone", "12345", "code", DEMO_CODE), null).code()).isEqualTo("INVALID_PHONE");
        assertThat(call("POST", "/api/auth/login", Map.of("phone", randomPhone(), "code", "12"), null).code()).isEqualTo("INVALID_CODE_FORMAT");
        Resp wrong = call("POST", "/api/auth/login", Map.of("phone", randomPhone(), "code", "123456"), null);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("AUTH_CODE_WRONG");
    }

    @Test
    void fiveWrongCodesLockThePhone() {
        String phone = randomPhone();
        for (int i = 0; i < 4; i++) {
            assertThat(call("POST", "/api/auth/login", Map.of("phone", phone, "code", "000000"), null).code()).isEqualTo("AUTH_CODE_WRONG");
        }
        assertThat(call("POST", "/api/auth/login", Map.of("phone", phone, "code", "000000"), null).code()).isEqualTo("AUTH_CODE_LOCKED");
        Resp locked = call("POST", "/api/auth/login", Map.of("phone", phone, "code", DEMO_CODE), null);
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("AUTH_CODE_LOCKED");
    }

    @Test
    void loginCreatesTheUserAndLogoutRevokesAtOnce() {
        String phone = randomPhone();
        Resp r = call("POST", "/api/auth/login", Map.of("phone", phone, "code", DEMO_CODE), null);
        assertThat(r.status()).isEqualTo(200);
        String token = r.body().get("token").asString();
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(r.body().get("expiresAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(r.body().get("me").get("phone").asString()).isEqualTo(phone.substring(0, 3) + "****" + phone.substring(7));
        assertThat(r.body().get("me").get("onboarded").asBoolean()).isFalse();
        assertThat(r.body().get("me").get("demoMode").asBoolean()).isTrue();
        assertThat(r.body().get("me").get("id").isString()).isTrue();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE token_hash = UNHEX(SHA2(?, 256))", Integer.class, token)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE phone_enc LIKE CONCAT('%', ?, '%')", Integer.class, phone)).isZero();

        assertThat(call("GET", "/api/me", null, token).status()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/logout", null, token).status()).isEqualTo(204);
        Resp after = call("GET", "/api/me", null, token);
        assertThat(after.status()).isEqualTo(401);
        assertThat(after.code()).isEqualTo("UNAUTHENTICATED");

        String again = login(phone);
        assertThat(call("GET", "/api/me", null, again).body().get("id").asString()).isEqualTo(r.body().get("me").get("id").asString());
    }

    @Test
    void protectedEndpointsNeedAToken() {
        assertThat(call("GET", "/api/me", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/api/onboarding/progress", null, "not-a-token").status()).isEqualTo(401);
        assertThat(call("GET", "/api/places/search?q=pku", null, null).status()).isEqualTo(200);
    }
}
