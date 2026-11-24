package cn.aiditto.it;

import cn.aiditto.infra.json.Json;
import cn.aiditto.voice.VoiceAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自适应情景采集引擎端到端（真 PG16）：start → 逐题作答（含命中校准）→ 主动停止 → 人格资产入库。
 * 集成测试环境 LLM 是 mock，出题会走固定兜底题库，但状态机/校准/落库全流程可验证。
 * 默认跳过；AIDITTO_VOICE_IT=1 开启。
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AIDITTO_VOICE_IT", matches = "1")
class VoiceSessionIT extends ApiIT {

    @Autowired VoiceAssetService voiceAssets;

    @Test
    void runsAdaptiveFlowAndPersistsVoiceProfile() {
        String token = login("13800000001");

        // 1) 开始：拿到第一题（含 questionId 供作答）
        JsonNode q = call("POST", "/api/voice/start", null, token).body();
        assertThat(q).isNotNull();
        String sessionId = q.get("sessionId").asString();
        String qid = q.get("questionId").asString();
        // 刷新重进：同一道未答题，不另出一题
        JsonNode again = call("POST", "/api/voice/start", null, token).body();
        assertThat(again.get("questionId").asString()).isEqualTo(qid);
        assertThat(q.get("scenario").asString()).isNotBlank();
        assertThat(q.get("options")).hasSizeGreaterThanOrEqualTo(2);
        // 前端不应看到预测排序（防刷）
        assertThat(q.get("options").get(0).has("predict_rank")).isFalse();

        // 2) 答第一题：选 A（兜底题库里 A 的 predict_rank=1，应命中）
        var a1resp = call("POST", "/api/voice/answer/" + qid, Map.of("chosenKey", "A"), token);
        assertThat(a1resp.status()).as("answer resp body=" + a1resp.body()).isEqualTo(200);
        JsonNode a1 = a1resp.body();
        assertThat(a1.get("step").asInt()).isEqualTo(1);
        assertThat(a1.get("predictHit").asBoolean()).isTrue();
        assertThat(a1.get("calibration").asDouble()).isEqualTo(1.0);
        assertThat(a1.get("done").asBoolean()).isFalse();

        // 3) 第二题用自定义输入（语气样本资产）
        JsonNode q2 = a1.get("question");
        String qid2 = q2.get("questionId").asString();
        JsonNode a2 = call("POST", "/api/voice/answer/" + qid2,
                Map.of("customText", "我一般会直接说，别绕弯子，我这人就这样"), token).body();
        assertThat(a2.get("predictHit").isNull()).isTrue();  // 自定义不算校准
        String session2 = a2.get("sessionId").asString();
        assertThat(session2).isEqualTo(sessionId);

        // 4) 主动停止 → 推断人格资产
        JsonNode stop = call("POST", "/api/voice/stop/" + sessionId, null, token).body();
        assertThat(stop.get("active").asBoolean()).isTrue();
        assertThat(stop.get("traits")).isNotNull();
        assertThat(stop.get("calibration")).isNotNull();
        System.out.println("[VOICE-IT] profile=" + Json.write(stop));

        // 5) 再次 start 应幂等续上（已有 active session 时结束态了会新建，这里只验证不报错）
        JsonNode profile = call("GET", "/api/voice/profile", null, token).body();
        assertThat(profile.get("active").asBoolean()).isTrue();

        // 6) 治本验证：语气资产能被分身服务读出（脱敏、受控长度）
        Long uid = jdbc.queryForObject("SELECT user_id FROM voice_profile WHERE is_active "
                + "ORDER BY created_at DESC LIMIT 1", Long.class);
        if (uid != null) {
            String hint = voiceAssets.voiceHint(uid);
            assertThat(hint).isNotBlank();
            assertThat(hint.length()).isLessThanOrEqualTo(320);
            assertThat(hint).doesNotContain("13800138000");
            System.out.println("[VOICE-IT] voiceHint=" + hint);
        }
        // 无资产用户返回空串（零回归）
        assertThat(voiceAssets.voiceHint(-999L)).isEmpty();
    }
}
