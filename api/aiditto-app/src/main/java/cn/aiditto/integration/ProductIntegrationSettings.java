package cn.aiditto.integration;

import cn.aiditto.domain.integration.ProductIntegrationGate;
import cn.aiditto.domain.integration.ProductIntegrationMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Binds the integration mode. A test mode fails startup outside an isolated environment. */
@Component
public class ProductIntegrationSettings {
    private final ProductIntegrationMode mode;

    @Autowired
    public ProductIntegrationSettings(
            @Value("${aiditto.integration.mode:LEGACY_ONLY}") String raw,
            @Value("${aiditto.integration.environment:}") String environment,
            @Value("${spring.datasource.url:}") String jdbcUrl,
            Environment spring) {
        this(raw, environment, String.join(",", spring.getActiveProfiles()), jdbcUrl);
    }

    public ProductIntegrationSettings(String raw, String environment, String activeProfiles, String jdbcUrl) {
        this.mode = ProductIntegrationGate.open(raw, environment, activeProfiles, jdbcUrl);
    }

    public ProductIntegrationMode mode() {
        return mode;
    }

    public boolean v4IntegrationTest() {
        return mode == ProductIntegrationMode.V4_INTEGRATION_TEST;
    }

    public boolean shadowTest() {
        return mode == ProductIntegrationMode.V4_SHADOW_TEST;
    }

    public boolean isolatedTest() {
        return mode.isolatedTest();
    }
}
