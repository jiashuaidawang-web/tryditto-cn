package cn.aiditto.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The research console stays off in every other IT. This class turns the switch on inside the disposable database. */
class MatchLabIT extends ApiIT {
    @DynamicPropertySource
    static void lab(DynamicPropertyRegistry registry) {
        registry.add("aiditto.match-lab.enabled", () -> "true");
        registry.add("aiditto.match-lab.environment", () -> "ci");
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void anonymousAndUserAreRejectedAndAdminCanScoreWithoutWritingProfiles() throws Exception {
        assertThat(call("GET", "/api/match-lab/overview", null, null).status()).isEqualTo(401);
        String user = login(randomPhone());
        assertThat(call("GET", "/api/match-lab/overview", null, user).status()).isEqualTo(403);
        String admin = login("13800000001");
        Resp overview = call("GET", "/api/match-lab/overview", null, admin);
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.body().get("production").asString()).isEqualTo("NO-GO");
        assertThat(overview.body().get("environment").asString()).isEqualTo("SYNTHETIC_SANDBOX");
        int profiles = count("relationship_profile_version");
        int observations = count("matching_observation");
        int events = count("relationship_feedback_event");
        int matches = jdbc.queryForObject("SELECT COUNT(*) FROM \"match\"", Integer.class);
        Resp score = call("POST", "/api/match-lab/score", Map.of("userA", 71001, "userB", 71002), admin);
        assertThat(score.status()).as(String.valueOf(score.body())).isEqualTo(200);
        assertThat(score.body().get("executionMode").asString()).isEqualTo("ENGINE_EXECUTED");
        assertThat(score.body().get("recommendationEligible").asBoolean()).isTrue();
        Resp refused = call("POST", "/api/match-lab/score", Map.of("userA", 71003, "userB", 71004), admin);
        assertThat(refused.status()).isEqualTo(200);
        assertThat(refused.body().get("recommendationEligible").asBoolean()).isFalse();
        Resp bad = call("POST", "/api/match-lab/profiles", Map.of(
                "name", "坏特征",
                "self", List.of(Map.of("code", "select * from app_user", "value", "slow", "status", "CONFIRMED")),
                "preferences", List.of()), admin);
        assertThat(bad.status()).isEqualTo(422);
        Resp recall = call("POST", "/api/match-lab/recall", Map.of(), admin);
        assertThat(recall.status()).as(String.valueOf(recall.body())).isEqualTo(200);
        assertThat(recall.body().get("executionMode").asString()).isEqualTo("ENGINE_EXECUTED");
        Resp reasoning = call("POST", "/api/match-lab/reasoning", Map.of(), admin);
        assertThat(reasoning.status()).isEqualTo(200);
        assertThat(reasoning.body().get("realModelStatus").asString()).isEqualTo("NOT_RUN");
        assertThat(reasoning.body().get("aiAdjustment").asBoolean()).isFalse();
        Resp global = call("POST", "/api/match-lab/global", Map.of("objective", "G2", "userIds", List.of()), admin);
        assertThat(global.status()).as(String.valueOf(global.body())).isEqualTo(200);
        assertThat(global.body().get("usablePlan").asBoolean()).isTrue();
        String runId = global.body().get("runId").asString();
        long low = global.body().get("pairs").get(0).get("lowId").asLong();
        long high = global.body().get("pairs").get(0).get("highId").asLong();
        Resp opened = call("POST", "/api/match-lab/feedback/open", Map.of("runId", runId, "userA", low, "userB", high), admin);
        assertThat(opened.status()).as(String.valueOf(opened.body())).isEqualTo(200);
        Resp math = call("POST", "/api/match-lab/global/compare", Map.of(), admin);
        assertThat(math.status()).isEqualTo(200);
        Resp blocked = call("POST", "/api/match-lab/feedback/open", Map.of(
                "runId", math.body().get("runId").asString(), "userA", 1, "userB", 3), admin);
        assertThat(blocked.status()).isEqualTo(422);
        Resp proposal = call("POST", "/api/match-lab/learning/propose", Map.of(
                "userId", high, "featureCode", "partner.pace", "newValue", "fast", "sourceEventId", ""), admin);
        assertThat(proposal.status()).as(String.valueOf(proposal.body())).isEqualTo(200);
        Resp confirmed = call("POST", "/api/match-lab/learning/decide", Map.of(
                "proposalId", proposal.body().get("proposal").get("proposalId").asString(),
                "action", "confirm", "actorUserId", high), admin);
        assertThat(confirmed.status()).as(String.valueOf(confirmed.body())).isEqualTo(200);
        assertThat(count("relationship_profile_version")).isEqualTo(profiles);
        assertThat(count("matching_observation")).isEqualTo(observations);
        assertThat(count("relationship_feedback_event")).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"match\"", Integer.class)).isEqualTo(matches);
        java.net.http.HttpRequest foreign = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/api/match-lab/overview"))
                .header("Authorization", "Bearer " + admin)
                .header("X-Match-Lab-Workspace", "ws-other-admin")
                .GET().build();
        java.net.http.HttpResponse<String> rejected = java.net.http.HttpClient.newHttpClient()
                .send(foreign, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(403);
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
