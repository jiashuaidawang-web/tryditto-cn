package cn.aiditto.it;

import cn.aiditto.relationship.RelationshipProfileService;
import cn.aiditto.shadow.ProductMatchingPoolReader;
import cn.aiditto.shadow.ShadowRequest;
import cn.aiditto.shadow.ShadowRunStore;
import cn.aiditto.shadow.V4ShadowRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ShadowCase extends ApiIT {
    static final Instant CUTOFF = Instant.parse("2026-11-23T12:00:00Z");
    static final LocalDate PERIOD = LocalDate.of(2026, 11, 23);

    @Autowired V4ShadowRunner runner;
    @Autowired ProductMatchingPoolReader pools;
    @Autowired RelationshipProfileService profiles;
    @Autowired ShadowRunStore shadowRuns;

    @DynamicPropertySource
    static void shadowMode(DynamicPropertyRegistry registry) {
        registry.add("aiditto.integration.mode", () -> "V4_SHADOW_TEST");
        registry.add("aiditto.integration.environment", () -> "ci");
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
    }

    Ready enroll(String gender, List<String> wants, boolean profile, boolean consent) {
        String phone = randomPhone();
        String token = login(phone);
        String email = "i02" + phone.substring(3) + "@pku.edu.cn";
        answer(token, "isStudent", "yes");
        answer(token, "name", "小明");
        answer(token, "gender", gender);
        answer(token, "expectedGender", wants);
        answer(token, "birthday", "2004-05-01");
        answer(token, "hobbies", List.of("music", "reading"));
        answer(token, "musicSound", List.of("indie"));
        answer(token, "readingGenres", List.of("scifi"));
        answer(token, "location", Map.of("placeId", "wudaokou", "mode", "transit", "minutes", 45));
        answer(token, "relationshipLooking", List.of("serious"));
        answer(token, "matchingStyle", "steady");
        answer(token, "schoolYear", "junior");
        answer(token, "schoolEmail", email);
        answer(token, "religion", List.of("buddhist"));
        answer(token, "expectedAgeRange", List.of(22, 30));
        answer(token, "heightRange", Map.of("any", true, "min", 150, "max", 190));
        answer(token, "educationLevel", "preferNot");
        answer(token, "educationImportance", "none");
        answer(token, "occupation", "工程师");
        answer(token, "firstMessage", "周末有空的话可以见面");
        assertThat(call("POST", "/api/auth/email/verify", Map.of("email", email, "code", DEMO_CODE), token).body().get("verified").asBoolean()).isTrue();
        answer(token, "emailVerify", DEMO_CODE);
        uploadPhoto(token, "profile", 800, 1000);
        uploadPhoto(token, "profile", 1000, 800);
        assertThat(call("POST", "/api/onboarding/complete", null, token).status()).isEqualTo(204);
        long userId = Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
        if (profile) {
            int source = jdbc.queryForObject("SELECT version FROM profile WHERE user_id = ?", Integer.class, userId);
            long generation = profiles.grantProcessing(userId);
            profiles.save(userId, source, generation, profiles.projectAuthoritative(userId));
        }
        if (consent) assertThat(call("POST", "/api/me/matching-consent", null, token).status()).isEqualTo(200);
        return new Ready(token, phone, email, userId);
    }

    ShadowRequest request(String runId) {
        return new ShadowRequest(runId, PERIOD, "real", "bj", CUTOFF, V4ShadowRunner.STRATEGY,
                "compat-decision-1", "global-objective-g2-1", null, 7L);
    }

    int table(String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + name, Integer.class);
        return count == null ? 0 : count;
    }

    static final class Ready {
        final String token;
        final String phone;
        final String email;
        final long userId;

        Ready(String token, String phone, String email, long userId) {
            this.token = token;
            this.phone = phone;
            this.email = email;
            this.userId = userId;
        }
    }
}
