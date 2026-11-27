package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileAblationRunnerTest {
    @Test
    void preflightNamesTheStageWithoutPrintingTheKey() {
        Map<String, String> env = Map.of(
                "LLM_MODE", "live",
                "LLM_PRIMARY_BASE_URL", "https://example.test/v1",
                "LLM_PRIMARY_API_KEY", "super-secret-value",
                "LLM_PRIMARY_MODEL", "LongCat-2.5");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = ProfileAblationRunner.run(new String[] {"e1", "preflight", "v1"}, env,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertThat(code).isZero();
        assertThat(text).contains("stage=e1", "prompt=profile-extract-v2", "fallback=disabled",
                "database=not-connected", "samplesSent=0",
                ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERSION_V2));
        assertThat(text).doesNotContain("super-secret-value");
        assertThat(ProfileExtractionPrompts.digest()).isNotEqualTo(
                ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERSION_V2));
    }
}