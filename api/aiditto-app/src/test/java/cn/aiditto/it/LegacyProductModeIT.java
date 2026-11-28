package cn.aiditto.it;

import cn.aiditto.domain.integration.ProductIntegrationMode;
import cn.aiditto.integration.ProductIntegrationSettings;
import cn.aiditto.taste.FeedbackLearningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** The default product path stays on V3. This class does not opt into V4. */
class LegacyProductModeIT extends ApiIT {
    @Autowired ProductIntegrationSettings integration;
    @Autowired FeedbackLearningProperties learning;

    @Test
    void legacyDeleteDoesNotOpenV4Erasure() {
        String phone = randomPhone();
        String token = login(phone);
        long userId = Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
        assertThat(call("DELETE", "/api/account", null, token).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_erasure_task WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId)).isEqualTo("deleting");
        assertThat(call("POST", "/api/auth/login", java.util.Map.of("phone", phone, "code", DEMO_CODE), null).status()).isEqualTo(403);
    }

    @Test
    void v3LearningRemainsEnabled() {
        assertThat(integration.mode()).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(integration.v4IntegrationTest()).isFalse();
        assertThat(learning.enabled()).isTrue();
    }
}
