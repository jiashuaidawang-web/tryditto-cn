package cn.aiditto.it;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountIT extends ApiIT {

    @Test
    void blocklistNormalizesAndEnforcesRules() {
        String token = login(randomPhone());
        Resp none = call("POST", "/api/blocks", Map.of("name", "前任", "emails", List.of(), "phones", List.of(), "note", ""), token);
        assertThat(none.status()).isEqualTo(422);
        assertThat(none.code()).isEqualTo("BLOCK_NEEDS_CONTACT");

        Resp added = call("POST", "/api/blocks", Map.of("name", " 前任 ", "emails", List.of("Ex@PKU.edu.cn"), "phones", List.of("+86 138-0000-0001"), "note", "别"), token);
        assertThat(added.status()).isEqualTo(200);
        JsonNode b = added.body();
        assertThat(b.get("name").asString()).isEqualTo("前任");
        assertThat(b.get("emails").get(0).asString()).isEqualTo("ex@pku.edu.cn");
        assertThat(b.get("phones").get(0).asString()).isEqualTo("13800000001");
        assertThat(b.get("id").isString()).isTrue();

        JsonNode list = call("GET", "/api/blocks", null, token).body();
        assertThat(list.size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM block_contact WHERE block_id = ?", Integer.class, Long.parseLong(b.get("id").asString()))).isEqualTo(2);

        for (int i = 1; i < 20; i++) {
            assertThat(call("POST", "/api/blocks", Map.of("name", "n" + i, "emails", List.of("p" + i + "@x.com"), "phones", List.of(), "note", ""), token).status()).isEqualTo(200);
        }
        Resp limit = call("POST", "/api/blocks", Map.of("name", "x", "emails", List.of("z@x.com"), "phones", List.of(), "note", ""), token);
        assertThat(limit.status()).isEqualTo(409);
        assertThat(limit.code()).isEqualTo("BLOCK_LIMIT");

        assertThat(call("DELETE", "/api/blocks/" + b.get("id").asString(), null, token).status()).isEqualTo(204);
        assertThat(call("GET", "/api/blocks", null, token).body().size()).isEqualTo(19);
        String other = login(randomPhone());
        assertThat(call("DELETE", "/api/blocks/" + list.get(0).get("id").asString(), null, other).status()).isEqualTo(404);
    }

    @Test
    void deactivateRevokesSessionsAndLoginReactivates() {
        String phone = randomPhone();
        String token = login(phone);
        String second = login(phone);
        assertThat(call("POST", "/api/account/deactivate", null, token).status()).isEqualTo(204);
        assertThat(call("GET", "/api/me", null, token).status()).isEqualTo(401);
        assertThat(call("GET", "/api/me", null, second).status()).isEqualTo(401);

        String back = login(phone);
        assertThat(call("GET", "/api/me", null, back).body().get("status").asString()).isEqualTo("onboarding");
    }

    @Test
    void deleteClosesTheAccount() {
        String phone = randomPhone();
        String token = login(phone);
        assertThat(call("GET", "/api/referrals/me", null, token).body().get("url").asString()).startsWith("jianyimian.cn/j/home-referral/u_");
        assertThat(call("DELETE", "/api/account", null, token).status()).isEqualTo(204);
        assertThat(call("GET", "/api/me", null, token).status()).isEqualTo(401);
        Resp again = call("POST", "/api/auth/login", Map.of("phone", phone, "code", DEMO_CODE), null);
        assertThat(again.status()).isEqualTo(403);
        assertThat(again.code()).isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    void placeSearchMatchesChineseEnglishAndPinyin() {
        JsonNode zh = call("GET", "/api/places/search?q=" + java.net.URLEncoder.encode("五道口", java.nio.charset.StandardCharsets.UTF_8), null, null).body();
        assertThat(zh.size()).isGreaterThan(0);
        assertThat(zh.get(0).get("id").asString()).isEqualTo("wudaokou");
        assertThat(zh.get(0).get("name").get("en").asString()).isNotBlank();
        assertThat(call("GET", "/api/places/search?q=wudaokou", null, null).body().size()).isGreaterThan(0);
        assertThat(call("GET", "/api/places/search?q=", null, null).body().size()).isZero();
        assertThat(call("GET", "/api/places/search?q=pku", null, null).body().size()).isLessThanOrEqualTo(8);
    }
}
