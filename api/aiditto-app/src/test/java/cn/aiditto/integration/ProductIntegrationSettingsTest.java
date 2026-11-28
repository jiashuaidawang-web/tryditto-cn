package cn.aiditto.integration;

import cn.aiditto.domain.integration.ProductIntegrationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductIntegrationSettingsTest {
    @Test
    void legacyIsTheDefaultAndIntegrationTestDoesNotEnableProduction() {
        ProductIntegrationSettings legacy = new ProductIntegrationSettings("LEGACY_ONLY");
        ProductIntegrationSettings isolated = new ProductIntegrationSettings("V4_INTEGRATION_TEST");
        assertThat(legacy.v4IntegrationTest()).isFalse();
        assertThat(legacy.mode()).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(isolated.v4IntegrationTest()).isTrue();
        assertThat(isolated.mode().allowsProductionV4()).isFalse();
    }

    @Test
    void futureModesFailBeforeStartup() {
        assertThatThrownBy(() -> new ProductIntegrationSettings("V4_SHADOW"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductIntegrationSettings("V4_PILOT"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductIntegrationSettings("PRODUCTION"))
                .isInstanceOf(IllegalStateException.class);
    }
}
