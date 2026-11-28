package cn.aiditto.shadow;

import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.integration.ProductIntegrationSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowRunnerClosedTest {
    @Test
    void legacyModeRefusesBeforeThePoolIsRead() {
        ProductIntegrationSettings legacy = new ProductIntegrationSettings("LEGACY_ONLY", "", "",
                "jdbc:postgresql://db.internal/aiditto");
        V4ShadowRunner runner = new V4ShadowRunner(legacy, null, null, null, null, null, null, "UNSPECIFIED");
        assertThatThrownBy(() -> runner.run(new ShadowRequest("shadow.closed", LocalDate.of(2026, 11, 23), "real", "bj",
                Instant.parse("2026-11-23T00:00:00Z"), V4ShadowRunner.STRATEGY, CompatibilityPolicies.VERSION,
                Objective.SELECTED_VERSION, null, 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
