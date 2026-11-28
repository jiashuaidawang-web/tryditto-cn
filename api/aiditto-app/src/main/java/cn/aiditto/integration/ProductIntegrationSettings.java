package cn.aiditto.integration;

import cn.aiditto.domain.integration.ProductIntegrationMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Binds {@code aiditto.integration.mode}. An unknown or future mode fails startup. */
@Component
public class ProductIntegrationSettings {
    private final ProductIntegrationMode mode;

    public ProductIntegrationSettings(@Value("${aiditto.integration.mode:LEGACY_ONLY}") String raw) {
        this.mode = ProductIntegrationMode.parse(raw);
    }

    public ProductIntegrationMode mode() {
        return mode;
    }

    public boolean v4IntegrationTest() {
        return mode == ProductIntegrationMode.V4_INTEGRATION_TEST;
    }
}
