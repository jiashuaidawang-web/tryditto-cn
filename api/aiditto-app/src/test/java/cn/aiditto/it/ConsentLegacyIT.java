package cn.aiditto.it;

import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.match.WeekRunner;
import cn.aiditto.domain.week.WeekStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The legacy hatch is not V4 compliance mode. It may let UNKNOWN through.
 * REVOKED must still be refused while the hatch is open.
 */
class ConsentLegacyIT extends ApiIT {
    @DynamicPropertySource
    static void legacyHatch(DynamicPropertyRegistry registry) {
        registry.add("aiditto.match.consent.legacy-allow-unknown", () -> "true");
    }

    @Autowired SyntheticLoader loader;
    @Autowired WeekRunner weeks;
    @Autowired MatchingConsentService consents;

    @Test
    void revokedStillCannotPublishWhenUnknownIsAllowed() {
        assertThat(consents.legacyAllowUnknown()).isTrue();
        assertThat(loader.load().get("loaded")).isEqualTo(200);
        Map<String, Object> copied = weeks.runManual("bj", Instant.now(), false);
        long weekId = Long.parseLong(copied.get("weekId").toString());
        Map<String, Object> draft = jdbc.queryForMap("""
                SELECT id, user_low FROM "match" WHERE week_id = ? AND state = 'draft' ORDER BY id LIMIT 1
                """, weekId);
        long matchId = ((Number) draft.get("id")).longValue();
        long userId = ((Number) draft.get("user_low")).longValue();
        consents.revoke(userId);
        weeks.rerun(weekId, WeekStep.PUBLISH, Instant.now());

        Map<String, Object> held = jdbc.queryForMap(
                "SELECT state, published_at, hold_reason FROM \"match\" WHERE id = ?", matchId);
        assertThat(held.get("state")).isEqualTo("cancelled");
        assertThat(held.get("published_at")).isNull();
        assertThat(String.valueOf(held.get("hold_reason"))).contains("CONSENT_REVOKED");

        Integer published = jdbc.queryForObject("""
                SELECT COUNT(*) FROM "match" WHERE week_id = ? AND state = 'published'
                """, Integer.class, weekId);
        assertThat(published).as("legacy hatch is on, so other UNKNOWN pairs still publish").isPositive();
    }
}
