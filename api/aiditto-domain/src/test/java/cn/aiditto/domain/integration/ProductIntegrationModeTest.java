package cn.aiditto.domain.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductIntegrationModeTest {
    @Test
    void blankStaysLegacyAndDoesNotEnableProduction() {
        assertThat(ProductIntegrationMode.parse(null)).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(ProductIntegrationMode.parse("  ")).isEqualTo(ProductIntegrationMode.LEGACY_ONLY);
        assertThat(ProductIntegrationMode.parse("LEGACY_ONLY").allowsProductionV4()).isFalse();
        assertThat(ProductIntegrationMode.parse("V4_INTEGRATION_TEST").allowsProductionV4()).isFalse();
        assertThat(LegacyLearningPolicy.mayWriteRelationshipProfile(ProductIntegrationMode.LEGACY_ONLY)).isFalse();
        assertThat(LegacyLearningPolicy.mayWriteRelationshipProfile(ProductIntegrationMode.V4_INTEGRATION_TEST)).isFalse();
        assertThat(LegacyLearningPolicy.mayWriteRelationshipProfile(null)).isFalse();
    }

    @Test
    void shadowPilotAndUnknownFailClosed() {
        assertThatThrownBy(() -> ProductIntegrationMode.parse("V4_SHADOW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
        assertThatThrownBy(() -> ProductIntegrationMode.parse("V4_PILOT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
        assertThatThrownBy(() -> ProductIntegrationMode.parse("LIVE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }
}
