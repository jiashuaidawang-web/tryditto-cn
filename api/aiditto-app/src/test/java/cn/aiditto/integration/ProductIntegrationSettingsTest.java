package cn.aiditto.integration;

import cn.aiditto.domain.integration.ProductIntegrationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductIntegrationSettingsTest {
    private static final String TEST_URL = "jdbc:postgresql://127.0.0.1:5432/aiditto_s01_test?stringtype=unspecified";
    private static final String LAB_URL = "jdbc:postgresql://127.0.0.1:5432/aiditto_lab_e2e";
    private static final String BUSINESS_URL = "jdbc:postgresql://127.0.0.1:5432/aiditto";

    @Test
    void legacyIsTheDefaultAndIntegrationTestDoesNotEnableProduction() {
        ProductIntegrationSettings legacy = open("LEGACY_ONLY", "", "", BUSINESS_URL);
        ProductIntegrationSettings isolated = open("V4_INTEGRATION_TEST", "ci", "", TEST_URL);
        assertThat(legacy.v4IntegrationTest()).isFalse();
        assertThat(legacy.isolatedTest()).isFalse();
        assertThat(legacy.mode()).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(isolated.v4IntegrationTest()).isTrue();
        assertThat(isolated.mode().allowsProductionV4()).isFalse();
        assertThat(isolated.mode().grantsFeedbackLearning()).isFalse();
    }

    @Test
    void defaultStaysLegacyWithoutAnEnvironment() {
        ProductIntegrationSettings settings = open(null, "", "prod", "jdbc:postgresql://db.internal/aiditto");
        assertThat(settings.mode()).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(settings.shadowTest()).isFalse();
    }

    @Test
    void illegalModeFailsClosed() {
        assertThatThrownBy(() -> open("V4_SHADOW", "ci", "", TEST_URL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> open("V4_PILOT", "isolated", "", TEST_URL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> open("PRODUCTION", "ci", "", TEST_URL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testModeWithoutEnvironmentFails() {
        assertThatThrownBy(() -> open("V4_INTEGRATION_TEST", "", "", TEST_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("environment");
        assertThatThrownBy(() -> open("V4_SHADOW_TEST", "staging", "", TEST_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("environment");
    }

    @Test
    void productionProfileRejectsTestMode() {
        assertThatThrownBy(() -> open("V4_INTEGRATION_TEST", "ci", "prod", TEST_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
        assertThatThrownBy(() -> open("V4_SHADOW_TEST", "production", "", LAB_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
    }

    @Test
    void isolatedEnvironmentAcceptsTestMode() {
        ProductIntegrationSettings integration = open("V4_INTEGRATION_TEST", "isolated", "local", TEST_URL);
        ProductIntegrationSettings shadow = open("V4_SHADOW_TEST", "local", "", LAB_URL);
        assertThat(integration.isolatedTest()).isTrue();
        assertThat(shadow.shadowTest()).isTrue();
        assertThat(shadow.v4IntegrationTest()).isFalse();
        assertThat(shadow.mode().grantsFeedbackLearning()).isFalse();
        assertThat(shadow.mode().allowsProductionV4()).isFalse();
    }

    @Test
    void isolatedDatasourceIsRequired() {
        assertThatThrownBy(() -> open("V4_INTEGRATION_TEST", "ci", "", BUSINESS_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database");
        assertThatThrownBy(() -> open("V4_SHADOW_TEST", "ci", "", "jdbc:postgresql://db.example.com/aiditto_s01_test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database");
        assertThatThrownBy(() -> open("V4_INTEGRATION_TEST", "ci", "", "jdbc:postgresql://aiditto:secret@127.0.0.1:5432/aiditto_s01_test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database");
    }

    private static ProductIntegrationSettings open(String mode, String environment, String profiles, String url) {
        return new ProductIntegrationSettings(mode, environment, profiles, url);
    }
}
